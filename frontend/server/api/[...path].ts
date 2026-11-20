const authRoutes = new Map([
  ['/api/auth/login', 'POST'],
  ['/api/auth/logout', 'POST'],
  ['/api/auth/me', 'GET'],
  ['/api/talleres', 'POST'],
  ['/api/catalogo/marcas', 'GET'],
  ['/api/catalogo/modelos', 'GET'],
  ['/api/catalogo/versiones', 'GET'],
])

export default defineEventHandler((event) => {
  const path = getRequestURL(event)
  const method = authRoutes.get(path.pathname)
    ?? (/^\/api\/talleres\/[0-9]+$/.test(path.pathname) ? 'GET' : undefined)
  const requestMethod = getMethod(event)
  const personal =
    (path.pathname === '/api/usuarios' && ['GET', 'POST'].includes(requestMethod))
    || (path.pathname === '/api/mecanicos' && requestMethod === 'GET')
    || (path.pathname === '/api/mecanicos/con-cuenta' && requestMethod === 'POST')
    || (/^\/api\/usuarios\/[0-9]+$/.test(path.pathname) && requestMethod === 'PUT')
    || (/^\/api\/(usuarios|mecanicos)\/[0-9]+\/estado$/.test(path.pathname) && requestMethod === 'PATCH')
  const clientes =
    (path.pathname === '/api/clientes' && ['GET', 'POST'].includes(requestMethod))
    || (/^\/api\/clientes\/[0-9]+$/.test(path.pathname) && ['GET', 'PUT'].includes(requestMethod))
    || (/^\/api\/clientes\/[0-9]+\/estado$/.test(path.pathname) && requestMethod === 'PATCH')
    || (path.pathname === '/api/condiciones-iva' && requestMethod === 'GET')
  const vehiculos =
    (path.pathname === '/api/vehiculos' && ['GET', 'POST'].includes(requestMethod))
    || (/^\/api\/vehiculos\/[0-9]+$/.test(path.pathname) && ['GET', 'PUT'].includes(requestMethod))
    || (/^\/api\/vehiculos\/[0-9]+\/titularidad$/.test(path.pathname) && requestMethod === 'GET')
    || (/^\/api\/vehiculos\/[0-9]+\/(titular|estado)$/.test(path.pathname) && requestMethod === 'PATCH')
  const turnos =
    (path.pathname === '/api/turnos' && ['GET', 'POST'].includes(requestMethod))
    || (path.pathname === '/api/turnos/disponibilidad' && requestMethod === 'GET')
    || (/^\/api\/turnos\/[0-9]+$/.test(path.pathname) && ['GET', 'PUT'].includes(requestMethod))
    || (/^\/api\/turnos\/[0-9]+\/estado$/.test(path.pathname) && requestMethod === 'PATCH')
    || (/^\/api\/ordenes-trabajo\/[0-9]+\/ingreso$/.test(path.pathname) && requestMethod === 'POST')
    || (path.pathname === '/api/presupuestos' && requestMethod === 'GET')
  const repuestos =
    (path.pathname === '/api/repuestos' && ['GET', 'POST'].includes(requestMethod))
    || (/^\/api\/repuestos\/[0-9]+$/.test(path.pathname) && ['GET', 'PUT'].includes(requestMethod))
    || (/^\/api\/repuestos\/[0-9]+\/estado$/.test(path.pathname) && requestMethod === 'PATCH')
    || (/^\/api\/repuestos\/[0-9]+\/(compatibilidades|movimientos)$/.test(path.pathname) && ['GET', 'POST'].includes(requestMethod))
    || (/^\/api\/repuestos\/[0-9]+\/compatibilidades\/[0-9]+$/.test(path.pathname) && requestMethod === 'DELETE')
  const ordenes =
    (path.pathname === '/api/ordenes-trabajo' && ['GET', 'POST'].includes(requestMethod))
    || (/^\/api\/ordenes-trabajo\/[0-9]+$/.test(path.pathname) && ['GET', 'PUT'].includes(requestMethod))
    || (/^\/api\/ordenes-trabajo\/[0-9]+\/(ingreso|inicio|finalizacion|cancelacion|tareas|repuestos|fotos)$/.test(path.pathname) && requestMethod === 'POST')
    || (/^\/api\/ordenes-trabajo\/[0-9]+\/trabajo$/.test(path.pathname) && requestMethod === 'PUT')
    || (/^\/api\/ordenes-trabajo\/[0-9]+\/(tareas|repuestos)\/[0-9]+$/.test(path.pathname) && ['PATCH', 'DELETE'].includes(requestMethod))
    || (/^\/api\/ordenes-trabajo\/[0-9]+\/fotos\/[0-9]+$/.test(path.pathname) && ['GET', 'DELETE'].includes(requestMethod))
    || (/^\/api\/presupuestos\/[0-9]+$/.test(path.pathname) && requestMethod === 'GET')
  if (!personal && !clientes && !vehiculos && !turnos && !repuestos && !ordenes && (!method || requestMethod !== method)) throw createError({ statusCode: 404 })

  const backend = new URL(useRuntimeConfig(event).backendUrl)
  backend.pathname = path.pathname
  backend.search = path.search
  setResponseHeader(event, 'Cache-Control', 'no-store')
  return proxyRequest(event, backend.href, { fetchOptions: { redirect: 'manual' } })
})
