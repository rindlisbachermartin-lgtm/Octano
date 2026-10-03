export default defineNuxtRouteMiddleware((to) => {
  // This is navigation for the local demo, not a production security boundary.
  if (import.meta.server) return
  if (/^\/(qr|ficha|demo)(\/|$)/.test(to.path)) return
  const auth = useOwnerAccount()
  auth.initialize()
  if (to.path === '/login' || to.path === '/registro') return
  if (!auth.session.value) return navigateTo('/login')
  if (to.path === '/configurar-arca' && !auth.owner.value) return navigateTo('/registro')
})
