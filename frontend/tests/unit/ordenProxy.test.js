// Verifica la lista permitida del proxy real sin iniciar Nuxt ni enviar peticiones.
let handler
beforeAll(() => {
  Object.assign(globalThis, {
    defineEventHandler: fn => fn,
    getRequestURL: event => new URL(`http://localhost${event.path}`),
    getMethod: event => event.method,
    createError: data => Object.assign(new Error('Ruta no permitida'), data),
    useRuntimeConfig: () => ({ backendUrl: 'http://127.0.0.1:8080' }),
    setResponseHeader: jest.fn(),
    proxyRequest: jest.fn(() => 'respuesta'),
  })
  handler = require('../../server/api/[...path].ts').default
})

test('reenvía todas las operaciones de órdenes, fotos y consulta de presupuesto', () => {
  const rutas = [
    ['GET', ''], ['POST', ''], ['GET', '/90'], ['PUT', '/90'],
    ...['ingreso', 'inicio', 'finalizacion', 'cancelacion', 'tareas', 'repuestos', 'fotos'].map(r => ['POST', `/90/${r}`]),
    ['PUT', '/90/trabajo'], ['PATCH', '/90/tareas/51'], ['DELETE', '/90/tareas/51'],
    ['PATCH', '/90/repuestos/810'], ['DELETE', '/90/repuestos/810'],
    ['GET', '/90/fotos/12'], ['DELETE', '/90/fotos/12'],
  ]
  for (const [method, ruta] of rutas) expect(handler({ method, path: `/api/ordenes-trabajo${ruta}` })).toBe('respuesta')
  const event = { method: 'GET', path: '/api/ordenes-trabajo?q=ABC123&pagina=2' }
  handler(event)
  expect(proxyRequest).toHaveBeenLastCalledWith(event, 'http://127.0.0.1:8080/api/ordenes-trabajo?q=ABC123&pagina=2', expect.any(Object))
  expect(handler({ method: 'GET', path: '/api/presupuestos/63' })).toBe('respuesta')
  expect(handler({ method: 'PUT', path: '/api/presupuestos/63' })).toBe('respuesta')
  expect(handler({ method: 'GET', path: '/api/reportes/resumen' })).toBe('respuesta')
})

test('rechaza métodos y rutas ajenos al contrato', () => {
  for (const [method, path] of [['DELETE', '/api/ordenes-trabajo/90'], ['POST', '/api/ordenes-trabajo/90/fotos/12'], ['GET', '/api/ordenes-trabajo/90/inicio'], ['GET', '/api/ordenes-trabajo/otro'], ['POST', '/api/reportes/resumen']]) {
    expect(() => handler({ method, path })).toThrow('Ruta no permitida')
  }
})
