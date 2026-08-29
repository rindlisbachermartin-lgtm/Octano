import type { NitroFetchOptions, NitroFetchRequest } from 'nitropack'

export const useApi = () => {
  const config = useRuntimeConfig()
  const { token, logout } = useAuth()

  const apiFetch = $fetch.create({
    baseURL: config.public.apiBase,
    
    onRequest({ options }) {
      // Inyectar JWT en cabeceras de autorización
      options.headers = options.headers || {}
      if (token.value) {
        const headers = new Headers(options.headers)
        headers.set('Authorization', `Bearer ${token.value}`)
        options.headers = headers
      }
    },

    onResponse({ response }) {
      // Interceptor de respuesta exitosa
      return response._data
    },

    onResponseError({ response }) {
      // Capturar y procesar errores HTTP comunes
      const status = response.status
      const errorData = response._data

      console.error(`[HTTP Error ${status}]:`, errorData)

      if (status === 401) {
        // Token expirado o inválido -> cerrar sesión
        console.warn('Sesión expirada o no autorizada. Redirigiendo a login...')
        logout()
      } else if (status === 403) {
        console.warn('Acceso denegado. No posee los permisos necesarios.')
      } else if (status === 500) {
        console.error('Error interno del servidor. Por favor intente más tarde.')
      }

      throw errorData || new Error(`Error en la solicitud HTTP ${status}`)
    }
  })

  // Helper methods
  const get = <T>(url: string, opts?: NitroFetchOptions<NitroFetchRequest>) => 
    apiFetch<T>(url, { method: 'GET', ...opts })

  const post = <T>(url: string, body?: any, opts?: NitroFetchOptions<NitroFetchRequest>) => 
    apiFetch<T>(url, { method: 'POST', body, ...opts })

  const put = <T>(url: string, body?: any, opts?: NitroFetchOptions<NitroFetchRequest>) => 
    apiFetch<T>(url, { method: 'PUT', body, ...opts })

  const del = <T>(url: string, opts?: NitroFetchOptions<NitroFetchRequest>) => 
    apiFetch<T>(url, { method: 'DELETE', ...opts })

  return {
    apiFetch,
    get,
    post,
    put,
    del
  }
}
