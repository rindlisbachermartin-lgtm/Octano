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
  if (!method || getMethod(event) !== method) throw createError({ statusCode: 404 })

  const backend = new URL(useRuntimeConfig(event).backendUrl)
  backend.pathname = path.pathname
  backend.search = path.search
  setResponseHeader(event, 'Cache-Control', 'no-store')
  return proxyRequest(event, backend.href, { fetchOptions: { redirect: 'manual' } })
})
