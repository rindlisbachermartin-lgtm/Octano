const { parse, compileScript } = require('@vue/compiler-sfc')
const { transformSync } = require('esbuild')
const { existsSync, readFileSync } = require('node:fs')
const { createHash } = require('node:crypto')

// Convierte Vue y TypeScript para Jest usando el mismo compilador de Vue de la aplicación.
// El adaptador vue3-jest solo admite Jest 29; así podemos utilizar Jest actualizado.
module.exports = {
  process(source, filename) {
    let code = source
    if (filename.endsWith('.vue')) {
      const { descriptor, errors } = parse(source, { filename })
      if (errors.length) throw errors[0]
      code = compileScript(descriptor, {
        id: createHash('sha256').update(filename).digest('hex').slice(0, 8),
        inlineTemplate: true,
        fs: {
          fileExists: existsSync,
          readFile: path => readFileSync(path, 'utf8'),
        },
      }).content
    }
    return {
      code: transformSync(code, {
        loader: filename.endsWith('.js') ? 'js' : 'ts',
        format: 'cjs',
        target: 'node24',
        sourcemap: 'inline',
        sourcefile: filename,
      }).code,
    }
  },
}
