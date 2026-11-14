export type UserRole = 'DUENO' | 'ADMIN' | 'RECEPCION' | 'MECANICO'

export interface AuthUser {
  id: number
  email: string
  rol: UserRole
  tallerId: number
  mecanicoId: number | null
  activo: boolean
}

interface AuthResponse {
  tokenAcceso: string
  expiraEnSegundos: number
  usuario: AuthUser
}

export interface WorkshopRegistration {
  nombre: string
  calle: string
  altura: number
  telefono: string
}

interface StoredSession {
  token: string
  expiresAt: number
}

const SESSION_KEY = 'octano-api-session'
let restoration: Promise<void> | undefined
let expiryTimer: ReturnType<typeof setTimeout> | undefined

export function authError(error: unknown, fallback: string) {
  const response = error as {
    status?: number
    statusCode?: number
    data?: { detail?: string; errores?: Record<string, string> }
  } | null
  const status = response?.statusCode ?? response?.status
  if (status === 401) return 'El correo o la contraseña no coinciden, o la sesión venció.'
  if (!status || status >= 500) return fallback
  return Object.values(response?.data?.errores ?? {})[0] || response?.data?.detail || fallback
}

export function useAuthApi() {
  const nuxt = useNuxtApp()
  const demo = useOwnerAccount()
  const user = useState<AuthUser | null>('api-auth-user', () => null)
  const session = useState<StoredSession | null>('api-auth-session', () => null)
  const workshopName = useState<string | null>('api-auth-workshop', () => null)

  function clear() {
    if (expiryTimer) clearTimeout(expiryTimer)
    session.value = null
    user.value = null
    workshopName.value = null
    if (import.meta.client) sessionStorage.removeItem(SESSION_KEY)
  }

  function scheduleExpiry(current: StoredSession) {
    if (expiryTimer) clearTimeout(expiryTimer)
    expiryTimer = setTimeout(() => {
      clear()
      if (!/^\/(login|registro|ficha|qr|demo)(\/|$)/.test(nuxt.$router.currentRoute.value.path)) {
        void nuxt.runWithContext(() => navigateTo('/login', { replace: true }))
      }
    }, Math.max(0, current.expiresAt - Date.now()))
  }

  async function loadWorkshop(current: StoredSession, profile: AuthUser) {
    if (profile.rol === 'MECANICO') return
    try {
      const workshop = await $fetch<{ nombre: string }>(`/api/talleres/${profile.tallerId}`, {
        headers: { Authorization: `Bearer ${current.token}` },
      })
      if (session.value?.token === current.token) workshopName.value = workshop.nombre
    } catch { /* The workshop name is optional in the account menu. */ }
  }

  function accept(response: AuthResponse) {
    demo.clearDemoSession()
    const current = {
      token: response.tokenAcceso,
      expiresAt: Date.now() + response.expiraEnSegundos * 1000,
    }
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(current))
    session.value = current
    user.value = response.usuario
    workshopName.value = null
    scheduleExpiry(current)
    void loadWorkshop(current, response.usuario)
  }

  async function restore() {
    if (import.meta.server) return
    if (user.value && session.value && session.value.expiresAt > Date.now()) return
    if (!restoration) {
      restoration = (async () => {
        try {
          const current = JSON.parse(sessionStorage.getItem(SESSION_KEY) ?? 'null') as StoredSession | null
          if (!current?.token || !Number.isFinite(current.expiresAt) || current.expiresAt <= Date.now()) {
            clear()
            return
          }
          const profile = await $fetch<AuthUser>('/api/auth/me', {
            headers: { Authorization: `Bearer ${current.token}` },
          })
          if (!profile.activo || current.expiresAt <= Date.now()) {
            clear()
            return
          }
          session.value = current
          user.value = profile
          demo.clearDemoSession()
          scheduleExpiry(current)
          void loadWorkshop(current, profile)
        } catch {
          clear()
        }
      })().finally(() => { restoration = undefined })
    }
    await restoration
  }

  async function login(email: string, password: string) {
    accept(await $fetch<AuthResponse>('/api/auth/login', {
      method: 'POST', body: { email: email.trim(), password },
    }))
  }

  async function register(email: string, password: string, taller: WorkshopRegistration) {
    accept(await $fetch<AuthResponse>('/api/talleres', {
      method: 'POST', body: { email: email.trim(), password, taller },
    }))
  }

  async function logout() {
    if (session.value) {
      try {
        await $fetch('/api/auth/logout', {
          method: 'POST', headers: { Authorization: `Bearer ${session.value.token}` },
        })
      } catch (error) {
        const response = error as { statusCode?: number; status?: number }
        if ((response.statusCode ?? response.status) !== 401) throw error
      }
    }
    clear()
  }

  const homePath = computed(() => user.value?.rol === 'MECANICO' ? '/mecanico' : '/')
  const authorization = computed(() => session.value ? { Authorization: `Bearer ${session.value.token}` } : undefined)
  const puedeAdministrar = computed(() => user.value?.rol === 'DUENO' || user.value?.rol === 'ADMIN')
  return { user, workshopName, homePath, authorization, puedeAdministrar, restore, login, register, logout }
}
