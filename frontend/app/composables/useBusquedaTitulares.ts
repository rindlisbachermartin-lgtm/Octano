import type { Client } from '~/types'
import type { ClienteTitularApi } from '~/utils/vehiculoApi'

export function useBusquedaTitulares(busqueda: Ref<string>, habilitado: () => boolean) {
  const auth = useAuthApi()
  const resultados = ref<Client[]>([])
  const error = ref('')
  const cargando = ref(false)
  watch([busqueda, habilitado], async ([texto, activo], _, onCleanup) => {
    resultados.value = []
    error.value = ''
    cargando.value = false
    const q = texto.trim()
    const headers = auth.authorization.value
    if (!activo || !q || !headers) return
    const consulta = new AbortController()
    onCleanup(() => consulta.abort())
    cargando.value = true
    try {
      const respuesta = await $fetch<{ contenido: ClienteTitularApi[] }>('/api/clientes', {
        headers, query: { q, activo: true, tamano: 20 }, signal: consulta.signal,
      })
      if (!consulta.signal.aborted) resultados.value = respuesta.contenido.map(adaptarTitular)
    } catch (err) {
      if (!consulta.signal.aborted) error.value = authError(err, 'No pudimos buscar titulares.')
    } finally {
      if (!consulta.signal.aborted) cargando.value = false
    }
  })
  return { resultados, error, cargando }
}
