export default defineNuxtRouteMiddleware((to) => {
  // This is navigation for the local demo, not a production security boundary.
  if (import.meta.server) return
  if (/^\/(qr|ficha|demo)(\/|$)/.test(to.path)) return
  const auth = useOwnerAccount()
  auth.initialize()
  if (to.path === '/login' || (to.path === '/registro' && !auth.isMechanic.value)) return
  if (!auth.session.value) return navigateTo('/login')
  if (auth.isMechanic.value && to.path !== auth.homePath.value) return navigateTo(auth.homePath.value)
  if (!auth.isMechanic.value && to.path.startsWith('/mecanico')) return navigateTo('/login')
  if (to.path === '/configurar-arca' && !auth.owner.value) return navigateTo('/registro')
})
