const authRoutes = new Map([
  ['/api/auth/login', 'POST'],
  ['/api/auth/logout', 'POST'],
  ['/api/auth/me', 'GET'],
  ['/api/talleres', 'POST'],
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
  if (!personal && (!method || requestMethod !== method)) throw createError({ statusCode: 404 })

  const backend = new URL(useRuntimeConfig(event).backendUrl)
  backend.pathname = path.pathname
  backend.search = path.search
  setResponseHeader(event, 'Cache-Control', 'no-store')
  return proxyRequest(event, backend.href, { fetchOptions: { redirect: 'manual' } })
})
