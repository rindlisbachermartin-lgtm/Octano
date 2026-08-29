export interface User {
  id: number
  username: string
  name: string
  email: string
  role: 'ROLE_ADMIN' | 'ROLE_RECEPCIONISTA' | 'ROLE_MECANICO' | 'ROLE_CLIENTE'
}

export const useAuth = () => {
  const token = useCookie<string | null>('auth_token', {
    default: () => null,
    maxAge: 60 * 60 * 24 * 7, // 7 days
    sameSite: 'lax'
  })

  const user = useCookie<User | null>('auth_user', {
    default: () => ({
      id: 1,
      username: 'admin',
      name: 'Martín Rindlisbacher',
      email: 'admin@taller.com',
      role: 'ROLE_ADMIN'
    }),
    maxAge: 60 * 60 * 24 * 7,
    sameSite: 'lax'
  })

  const isAuthenticated = computed(() => !!token.value || !!user.value)

  const setAuth = (newToken: string, newUser: User) => {
    token.value = newToken
    user.value = newUser
  }

  const logout = () => {
    token.value = null
    user.value = null
    navigateTo('/login')
  }

  const hasRole = (roles: string[]) => {
    if (!user.value) return false
    return roles.includes(user.value.role)
  }

  return {
    token,
    user,
    isAuthenticated,
    setAuth,
    logout,
    hasRole
  }
}
