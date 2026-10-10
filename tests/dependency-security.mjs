import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { createHash, generateKeyPairSync, verify as verifySignature } from 'node:crypto'
import { readFileSync, writeFileSync, cpSync, mkdirSync, mkdtempSync, rmSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const require = createRequire(import.meta.url)
// Resolve from the actual consumers, so an unused local copy cannot pass tests.
const micromatchRequire = createRequire(require.resolve('micromatch'))
const listhenRequire = createRequire(require.resolve('listhen'))
const braces = micromatchRequire('braces')
const forge = listhenRequire('node-forge')

test('simple-git 4 preserves Nuxt DevTools factory imports in ESM and CommonJS', async () => {
  const modern = await import('simple-git')
  const common = require('simple-git')
  assert.equal(typeof modern.default, 'function')
  assert.equal(modern.default, modern.simpleGit)
  assert.equal(typeof common, 'function')
  assert.equal(common, common.simpleGit)
  assert.equal(typeof modern.default().status, 'function')
})

test('Nitro consumers resolve the maintained local security patches', () => {
  for (const [resolver, name] of [[micromatchRequire, 'braces'], [listhenRequire, 'node-forge']]) {
    const metadata = JSON.parse(readFileSync(resolver.resolve(`${name}/package.json`), 'utf8'))
    assert.equal(metadata.octanoPatch.revision, 1)
    assert.equal(metadata.private, true)
  }
})

test('braces rejects excessively nested braces, parentheses and mixed patterns', () => {
  for (const [open, close] of [['{', '}'], ['(', ')'], ['({', '})']]) {
    for (const depth of [101, 4000]) {
      const pattern = open.repeat(depth) + 'a' + close.repeat(depth)
      // All payloads stay below the pre-existing 10,000-character limit.
      if (pattern.length > 10000) continue
      for (const operation of [braces, braces.parse, braces.compile, braces.expand, braces.stringify]) {
        assert.throws(() => operation(pattern), {
          name: 'SyntaxError', message: 'Brace nesting exceeds maximum depth (100)'
        })
      }
    }
  }
})

test('braces guards externally supplied ASTs and cyclic AST nodes', () => {
  const makeTree = () => {
    const root = { type: 'root', nodes: [] }
    let node = root
    for (let i = 0; i < 4000; i++) {
      const child = { type: 'brace', nodes: [], parent: node, commas: 1, open: true, close: true }
      node.nodes.push(child)
      node = child
    }
    return root
  }
  for (const operation of [braces.compile, braces.expand, braces.stringify]) {
    assert.throws(() => operation(makeTree()), /nesting exceeds maximum depth/)
    const cycle = { type: 'root', nodes: [] }
    cycle.nodes.push(cycle)
    assert.throws(() => operation(cycle), /nesting exceeds maximum depth/)
  }
})

test('normal brace expansion, escaping, globs and depth boundary remain supported', () => {
  assert.deepEqual(braces.expand('app/{pages,components}/*.{vue,ts}'), [
    'app/pages/*.vue', 'app/pages/*.ts', 'app/components/*.vue', 'app/components/*.ts'
  ])
  assert.deepEqual(braces.expand('file-{01..03}.js'), ['file-01.js', 'file-02.js', 'file-03.js'])
  assert.equal(braces.stringify(String.raw`\{a,b\}`), '{a,b}')
  const pattern = '{'.repeat(100) + 'a' + '}'.repeat(100)
  assert.equal(braces.stringify(pattern), pattern)
  assert.doesNotThrow(() => braces.compile(pattern))
  assert.doesNotThrow(() => braces.expand(pattern))
  assert.deepEqual(micromatchRequire('micromatch')(['a.vue', 'b.ts', 'c.css'], '*.{vue,ts}'), ['a.vue', 'b.ts'])
})

const { privateKey: privatePem, publicKey: publicPem } = generateKeyPairSync('rsa', {
  modulusLength: 1024, publicExponent: 3,
  privateKeyEncoding: { type: 'pkcs1', format: 'pem' },
  publicKeyEncoding: { type: 'spki', format: 'pem' }
})
const privateKey = forge.pki.privateKeyFromPem(privatePem)
const publicKey = forge.pki.publicKeyFromPem(publicPem)
const digest = forge.md.sha256.create().update('Octano dependency regression').digest().getBytes()
const { asn1 } = forge
const sequence = children => asn1.create(asn1.Class.UNIVERSAL, asn1.Type.SEQUENCE, true, children)
const octets = bytes => asn1.create(asn1.Class.UNIVERSAL, asn1.Type.OCTETSTRING, false, bytes)
const oid = () => asn1.create(asn1.Class.UNIVERSAL, asn1.Type.OID, false, asn1.oidToDer(forge.oids.sha256).getBytes())
const nullParameter = () => asn1.create(asn1.Class.UNIVERSAL, asn1.Type.NULL, false, '')
const signDigestInfo = info => privateKey.sign(asn1.toDer(info).getBytes(), 'NONE')

test('RSA rejects unconsumed children inside DigestAlgorithm, with and without NULL', () => {
  for (const parameters of [[oid(), nullParameter()], [oid()]]) {
    const extras = [[octets('garbage')], [nullParameter(), nullParameter()], [octets('a'), octets('b')]]
    for (const extra of extras) {
      const info = sequence([sequence([...parameters, ...extra]), octets(digest)])
      assert.throws(() => publicKey.verify(digest, signDigestInfo(info)), /valid RSASSA-PKCS1-v1_5 DigestInfo/)
    }
  }
})

test('RSA preserves outer DigestInfo validation and rejects digest mismatches', () => {
  const algorithm = sequence([oid(), nullParameter()])
  assert.throws(() => publicKey.verify(digest, signDigestInfo(sequence([
    algorithm, octets(digest), octets('extra outer child')
  ]))), /valid RSASSA-PKCS1-v1_5 DigestInfo/)
  assert.equal(publicKey.verify(digest, signDigestInfo(sequence([algorithm, octets('different digest')]))), false)
})

test('RSA accepts valid signatures with optional NULL and interoperates with Node crypto', () => {
  for (const parameters of [[oid()], [oid(), nullParameter()]]) {
    const signature = signDigestInfo(sequence([sequence(parameters), octets(digest)]))
    assert.equal(publicKey.verify(digest, signature), true)
  }
  for (const algorithm of ['sha1', 'sha256', 'sha384', 'sha512']) {
    const md = forge.md[algorithm].create().update('Octano valid signature')
    const signature = privateKey.sign(md)
    assert.equal(publicKey.verify(md.digest().getBytes(), signature), true)
    assert.equal(verifySignature(algorithm, Buffer.from('Octano valid signature'), publicPem, Buffer.from(signature, 'binary')), true)
  }
})

test('forge can still generate and verify the certificates used by listhen HTTPS', () => {
  const certificate = forge.pki.createCertificate()
  certificate.publicKey = publicKey
  certificate.serialNumber = '01'
  certificate.validity.notBefore = new Date('2026-01-01')
  certificate.validity.notAfter = new Date('2027-01-01')
  const attributes = [{ name: 'commonName', value: 'localhost' }]
  certificate.setSubject(attributes)
  certificate.setIssuer(attributes)
  certificate.sign(privateKey, forge.md.sha256.create())
  const decoded = forge.pki.certificateFromPem(forge.pki.certificateToPem(certificate))
  assert.equal(decoded.verify(decoded), true)
})

test('negative controls reproduce both vulnerabilities in the exact upstream sources', () => {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
  const temporaryRoot = join(root, 'tmp')
  mkdirSync(temporaryRoot, { recursive: true })
  const directory = mkdtempSync(join(temporaryRoot, 'security-reference-'))
  const manifest = JSON.parse(readFileSync(join(root, 'vendor/upstream-sha256.json'), 'utf8'))
  try {
    for (const name of ['braces', 'node-forge']) {
      const target = join(directory, name)
      cpSync(join(root, 'vendor', name), target, { recursive: true })
      // Reverse our reviewable unified diff, then verify every upstream byte.
      const patch = readFileSync(join(root, 'vendor/patches', `${name}.patch`), 'utf8').replace(/\r\n/g, '\n')
      let path
      for (const block of patch.split(/(?=^--- a\/)/m).filter(Boolean)) {
        const filename = block.match(/^--- a\/(.+)$/m)[1]
        path = join(target, filename)
        let content = readFileSync(path, 'utf8').replace(/\r\n/g, '\n')
        for (const hunk of block.split(/^@@.*@@.*\n/m).slice(1)) {
          const lines = hunk.split('\n').filter(line => /^[ +\-]/.test(line))
          const patched = lines.filter(line => line[0] !== '-').map(line => line.slice(1)).join('\n') + '\n'
          const original = lines.filter(line => line[0] !== '+').map(line => line.slice(1)).join('\n') + '\n'
          assert.ok(content.includes(patched), `Cannot reverse patch for ${filename}`)
          content = content.replace(patched, () => original)
        }
        const expectedHash = manifest[name].files[filename]
        if (createHash('sha256').update(content).digest('hex') !== expectedHash) {
          // npm's Windows distribution can use CRLF; patches normalize to LF.
          const crlf = content.replace(/\r?\n/g, '\r\n')
          assert.equal(createHash('sha256').update(crlf).digest('hex'), expectedHash)
          content = crlf
        }
        writeFileSync(path, content)
      }
      for (const [filename, expected] of Object.entries(manifest[name].files)) {
        assert.equal(createHash('sha256').update(readFileSync(join(target, filename))).digest('hex'), expected, `${name}/${filename}`)
      }
    }
    // Stack capacity varies by OS/Node. Use the same 512 KB limit for both copies.
    for (const [modulePath, expectedError] of [
      [join(directory, 'braces'), 'RangeError'],
      [micromatchRequire.resolve('braces'), 'SyntaxError']
    ]) {
      const probe = spawnSync(process.execPath, ['--stack-size=512', '-e',
        "const assert=require('node:assert/strict'); const braces=require(process.argv[1]); " +
        "assert.throws(()=>braces('{'.repeat(4999)+'a'+'}'.repeat(4999)), {name:process.argv[2]});",
        modulePath, expectedError
      ], { encoding: 'utf8', timeout: 10000 })
      assert.equal(probe.status, 0, probe.stderr || probe.error?.message)
    }
    const originalForge = require(join(directory, 'node-forge'))
    const info = sequence([sequence([oid(), nullParameter(), octets('unconsumed garbage')]), octets(digest)])
    assert.equal(originalForge.pki.publicKeyFromPem(publicPem).verify(digest, signDigestInfo(info)), true,
      'The original verifier incorrectly accepts the malformed nested algorithm')
  } finally {
    assert.equal(dirname(directory), temporaryRoot)
    rmSync(directory, { recursive: true, force: true })
  }
})
