import { authError } from '~/composables/useAuthApi'
import { useCambioDashboard } from '~/composables/useDashboardCambios'
import type { OrdenApi, PaginaOrdenes } from '~/utils/ordenApi'
import type { TurnoApi } from '~/utils/turnoApi'
import type { RepuestoApi, PaginaRepuestos } from '~/utils/repuestoApi'

interface OrdenesDashboard {
  proceso: PaginaOrdenes<OrdenApi>
  espera: PaginaOrdenes<OrdenApi>
  porIngresar: number
}
interface TurnosDashboard { contenido: TurnoApi[]; total: number }
interface ResumenDashboard {
  periodo: { desde: string; hasta: string }
  cantidadCobros: number
  ingresos: number
  comprobantesPorCobrar: { cantidad: number; importe: number }
}

export function useDashboard() {
  const { authorization, puedeAdministrar } = useAuthApi()
  const { today, label } = useWorkshopDay()
  const cambioDashboard = useCambioDashboard()
  const ordenes = ref<OrdenesDashboard | null>(null)
  const turnos = ref<TurnosDashboard | null>(null)
  const stock = ref<PaginaRepuestos<RepuestoApi> | null>(null)
  const resumen = ref<ResumenDashboard | null>(null)
  const errores = reactive({ ordenes: '', turnos: '', stock: '', resumen: '' })
  const cargando = ref(false)
  const revision = ref(0)
  const actualizado = ref<Date | null>(null)
  const recargar = () => { revision.value++ }

  watch([authorization, puedeAdministrar, today, revision, cambioDashboard], async ([headers, admin, hoy], _, onCleanup) => {
    const controller = new AbortController()
    const { signal } = controller
    onCleanup(() => controller.abort())
    ordenes.value = null
    turnos.value = null
    stock.value = null
    resumen.value = null
    actualizado.value = null
    Object.keys(errores).forEach(key => { errores[key as keyof typeof errores] = '' })
    cargando.value = !!headers
    if (!headers) return
    const options = { headers, signal }
    const consultas: [keyof typeof errores, () => Promise<void>][] = [
      ['ordenes', async () => {
        const [proceso, espera, ingreso] = await Promise.all(
          ['EN_PROCESO', 'EN_ESPERA', 'PENDIENTE_INGRESO'].map(estado =>
            $fetch<PaginaOrdenes<OrdenApi>>('/api/ordenes-trabajo', { ...options, query: { estado, pagina: 0, tamano: estado === 'PENDIENTE_INGRESO' ? 1 : 5 } })),
        )
        if (!signal.aborted) ordenes.value = { proceso, espera, porIngresar: ingreso.totalElementos }
      }],
      ['turnos', async () => {
        const paginas = await Promise.all(['PROGRAMADO', 'CONFIRMADO'].map(estado =>
          $fetch<{ contenido: TurnoApi[]; totalElementos: number }>('/api/turnos', { ...options, query: { desde: hoy, hasta: hoy, estado, pagina: 0, tamano: 5 } })))
        if (!signal.aborted) turnos.value = {
          total: paginas.reduce((sum, page) => sum + page.totalElementos, 0),
          contenido: paginas.flatMap(page => page.contenido).sort((a, b) => a.horaInicio.localeCompare(b.horaInicio) || a.id - b.id).slice(0, 5),
        }
      }],
      ['stock', async () => {
        const result = await $fetch<PaginaRepuestos<RepuestoApi>>('/api/repuestos', { ...options, query: { activo: true, stockBajo: true, pagina: 0, tamano: 5 } })
        if (!signal.aborted) stock.value = result
      }],
    ]
    if (admin) consultas.push(['resumen', async () => {
      const result = await $fetch<ResumenDashboard>('/api/reportes/resumen', { ...options, query: { desde: `${hoy.slice(0, 7)}-01`, hasta: hoy } })
      if (!signal.aborted) resumen.value = result
    }])
    await Promise.all(consultas.map(async ([key, consultar]) => {
      try { await consultar() }
      catch (err) { if (!signal.aborted) errores[key] = authError(err, 'No pudimos cargar esta información. Intentá nuevamente.') }
    }))
    if (!signal.aborted) {
      cargando.value = false
      actualizado.value = new Date()
    }
  }, { immediate: true })

  onMounted(() => window.addEventListener('focus', recargar))
  onBeforeUnmount(() => window.removeEventListener('focus', recargar))
  return { ordenes, turnos, stock, resumen, errores, cargando, actualizado, recargar, today, label }
}
