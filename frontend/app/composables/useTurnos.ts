import { authError } from '~/composables/useAuthApi'
import type { Appointment } from '~/types'
import { adaptarTurno, type PaginaTurnos, type TurnoApi } from '~/utils/turnoApi'

export function useTurnos(rango: () => { desde: string; hasta: string }) {
  const { authorization } = useAuthApi()
  const { db } = useDatabase()
  const turnos = ref<Appointment[]>([])
  const cargando = ref(false)
  const error = ref('')
  const revision = ref(0)

  const fechas = computed(rango)
  watch([authorization, () => fechas.value.desde, () => fechas.value.hasta, revision],
    async ([headers, desde, hasta], _, onCleanup) => {
      const controller = new AbortController()
      onCleanup(() => controller.abort())
      turnos.value = []
      error.value = ''
      cargando.value = !!headers
      if (!headers) return

      try {
        const contenido: TurnoApi[] = []
        let pagina = 0
        let totalPaginas: number
        do {
          const result = await $fetch<PaginaTurnos<TurnoApi>>('/api/turnos', {
            headers, signal: controller.signal, query: { desde, hasta, pagina, tamano: 100 },
          })
          contenido.push(...result.contenido)
          totalPaginas = result.totalPaginas
          pagina++
        } while (pagina < totalPaginas && !controller.signal.aborted)
        if (!controller.signal.aborted) turnos.value = contenido.map(adaptarTurno)
      } catch (err) {
        if (!controller.signal.aborted) error.value = authError(err, 'No se pudieron cargar los turnos.')
      } finally {
        if (!controller.signal.aborted) cargando.value = false
      }
    }, { immediate: true })

  return {
    turnos: computed(() => authorization.value ? turnos.value : db.value.appointments),
    cargando,
    error,
    recargar: () => { revision.value++ },
  }
}
