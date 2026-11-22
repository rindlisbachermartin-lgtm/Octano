// Ejecutar después de npm run build: node --test tests/otros/qr-ssr.test.mjs
import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { spawn } from 'node:child_process'
import { once } from 'node:events'
import { test } from 'node:test'

test('la ficha y sus estados llegan en el HTML inicial, sin JavaScript ni sesión', async () => {
  const backend = createServer((req, res) => {
    const code = req.url.split('/').at(-1)
    res.setHeader('Content-Type', 'application/json')
    if (code === 'NO-EXISTE' || code === 'ERROR') {
      res.statusCode = code === 'ERROR' ? 503 : 404
      res.end(JSON.stringify({ statusCode: res.statusCode }))
      return
    }
    res.end(JSON.stringify({ codigo: code, estado: code, tallerNombre: 'Taller SSR',
      vehiculo: code === 'ASIGNADO' ? { patente: 'SSR123', marca: 'Toyota', modelo: 'Hilux', version: null, anio: 2020, kilometraje: 0 } : null,
      ultimoService: code === 'ASIGNADO' ? { fecha: '2026-11-20', kilometraje: 0, aceite: '5W30 SSR', filtros: [], repuestos: [] } : null,
      ultimaDistribucion: null }))
  })
  backend.listen(0, '127.0.0.1')
  await once(backend, 'listening')
  const portProbe = createServer().listen(0, '127.0.0.1')
  await once(portProbe, 'listening')
  const port = portProbe.address().port
  await new Promise(resolve => portProbe.close(resolve))
  const app = spawn(process.execPath, ['.output/server/index.mjs'], {
    env: { ...process.env, NITRO_HOST: '127.0.0.1', NITRO_PORT: String(port), NUXT_BACKEND_URL: `http://127.0.0.1:${backend.address().port}` },
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  let logs = ''
  app.stdout.on('data', chunk => { logs += chunk })
  app.stderr.on('data', chunk => { logs += chunk })
  try {
    let ready = false
    for (let attempt = 0; attempt < 100; attempt++) {
      try { await fetch(`http://127.0.0.1:${port}/qr/DISPONIBLE`); ready = true; break } catch {}
      await new Promise(resolve => setTimeout(resolve, 100))
    }
    assert.ok(ready, logs)
    for (const [code, expected] of [
      ['ASIGNADO', ['SSR123', 'Taller SSR', '0 km', '5W30 SSR']],
      ['DISPONIBLE', ['QR pendiente de asignación']],
      ['INUTILIZADO', ['Código QR inutilizado']],
      ['NO-EXISTE', ['Código QR no registrado']],
      ['ERROR', ['No pudimos consultar la ficha', 'Reintentar']],
    ]) {
      const response = await fetch(`http://127.0.0.1:${port}/qr/${code}`)
      assert.equal(response.status, 200)
      assert.match(response.headers.get('cache-control'), /no-store/)
      const html = await response.text()
      const markup = html.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '')
      for (const value of expected) assert.ok(markup.includes(value), `${code}: falta ${value}`)
      assert.ok(!markup.includes('Consultando ficha'))
      assert.ok(!html.includes('databasePersistenceReady'), 'no debe cargar la base demo en la ficha pública')
    }
  } finally {
    if (app.exitCode === null && app.signalCode === null) {
      const exited = once(app, 'exit')
      app.kill()
      await exited
    }
    await new Promise(resolve => backend.close(resolve))
  }
})
