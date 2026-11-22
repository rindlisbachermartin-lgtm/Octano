export default defineNuxtRouteMiddleware(async (to) => {
  if (import.meta.server) return
  if (/^\/(qr|ficha)(\/|$)/.test(to.path)) return
  const api = useAuthApi()
  await api.restore()
  const role = api.user.value?.rol
  if (/^\/personal(\/|$)/.test(to.path) && !api.puedeAdministrar.value) {
    return navigateTo(role ? api.homePath.value : '/login')
  }
  if (role) {
    if (to.path === '/login' || to.path === '/registro') return navigateTo(api.homePath.value)
    if (role === 'MECANICO' && to.path !== '/mecanico') return navigateTo('/mecanico')
    if (role !== 'MECANICO' && /^\/mecanico(\/|$)/.test(to.path)) return navigateTo('/')
    if (role === 'RECEPCION' && /^\/(ajustes|configurar-arca|reportes|vehiculos\/plantilla-qr)(\/|$)/.test(to.path)) return navigateTo('/')
    return
  }

  if (to.path === '/login' || to.path === '/registro') return
  return navigateTo('/login')
})
