import { authError } from './useAuthApi'
import {
  todasLasPaginas,
  type ComprobanteApi,
  type ComprobanteResumenApi,
  type MedioPago,
  type OrdenPendienteApi,
} from '~/utils/comprobanteApi'

export interface SolicitudComprobante {
  ordenId: number | null
  vehiculoId: number
  descripcion: string
  manoObra: number
  porcentajeIva: number
  items: {
    repuestoId?: number | null
    descripcion: string
    cantidad: number
    precioUnitario: number
  }[]
}

export interface DatosCobro {
  medioPago: MedioPago
  importe: number
  observaciones: string
}

export function useComprobantes() {
  const auth = useAuthApi()
  const headers = computed(() => auth.authorization.value)
  const cargando = ref(false)
  const error = ref('')
  const todos = ref<ComprobanteResumenApi[]>([])
  const ordenesPendientes = ref<OrdenPendienteApi[]>([])
  const cargandoOrdenes = ref(false)
  const errorOrdenes = ref('')
  const revision = ref(0)
  const ultimosFiltros = ref<{ q?: string; desde?: string; hasta?: string }>({})
  const ultimosFiltrosOrdenes = ref<{ q?: string; conPresupuesto?: boolean | null }>({})

  function escapar(filtros: { q?: string; desde?: string; hasta?: string }) {
    return {
      ...(filtros.q?.trim() ? { q: filtros.q.trim() } : {}),
      ...(filtros.desde ? { desde: filtros.desde } : {}),
      ...(filtros.hasta ? { hasta: filtros.hasta } : {}),
    }
  }

  async function cargar(filtros: { q?: string; desde?: string; hasta?: string } = {}) {
    if (!headers.value) return
    ultimosFiltros.value = filtros
    cargando.value = true
    error.value = ''
    try {
      todos.value = await todasLasPaginas<ComprobanteResumenApi>('/api/comprobantes', headers.value, escapar(filtros))
    } catch (err) {
      error.value = authError(err, 'No pudimos cargar los comprobantes.')
    } finally {
      cargando.value = false
    }
  }

  async function cargarOrdenes(filtros: { q?: string; conPresupuesto?: boolean | null } = {}) {
    if (!headers.value) return
    ultimosFiltrosOrdenes.value = filtros
    cargandoOrdenes.value = true
    errorOrdenes.value = ''
    try {
      const query = {
        ...(filtros.q?.trim() ? { q: filtros.q.trim() } : {}),
        ...(filtros.conPresupuesto == null ? {} : { conPresupuesto: filtros.conPresupuesto }),
      }
      ordenesPendientes.value = await todasLasPaginas<OrdenPendienteApi>('/api/comprobantes/ordenes-pendientes', headers.value, query)
    } catch (err) {
      errorOrdenes.value = authError(err, 'No pudimos cargar las órdenes finalizadas.')
    } finally {
      cargandoOrdenes.value = false
    }
  }

  async function recargar(
    filtros: { q?: string; desde?: string; hasta?: string } = ultimosFiltros.value,
    filtrosOrdenes: { q?: string; conPresupuesto?: boolean | null } = ultimosFiltrosOrdenes.value,
  ) {
    revision.value++
    await Promise.all([cargar(filtros), cargarOrdenes(filtrosOrdenes)])
  }

  function obtener(id: number) {
    return $fetch<ComprobanteApi>(`/api/comprobantes/${id}`, { headers: headers.value ?? undefined })
  }

  async function crearDesdeOrden(ordenId: number) {
    const respuesta = await $fetch<ComprobanteApi>(`/api/comprobantes/desde-orden/${ordenId}`, {
      method: 'POST', headers: headers.value ?? undefined,
    })
    await recargar()
    return respuesta
  }

  async function guardar(solicitud: SolicitudComprobante, id?: number) {
    const respuesta = await $fetch<ComprobanteApi>(id ? `/api/comprobantes/${id}` : '/api/comprobantes', {
      method: id ? 'PUT' : 'POST', headers: headers.value ?? undefined, body: solicitud,
    })
    await recargar()
    return respuesta
  }

  async function emitir(id: number) {
    const respuesta = await $fetch<ComprobanteApi>(`/api/comprobantes/${id}/emision`, {
      method: 'POST', headers: headers.value ?? undefined,
    })
    await recargar()
    return respuesta
  }

  async function cobrar(id: number, datos: DatosCobro) {
    const respuesta = await $fetch<ComprobanteApi>(`/api/comprobantes/${id}/cobro`, {
      method: 'POST', headers: headers.value ?? undefined, body: datos,
    })
    await recargar()
    return respuesta
  }

  async function cobrarOrden(ordenId: number, datos: DatosCobro) {
    const respuesta = await $fetch<ComprobanteApi>(`/api/comprobantes/desde-orden/${ordenId}/cobro`, {
      method: 'POST', headers: headers.value ?? undefined, body: datos,
    })
    await recargar()
    return respuesta
  }

  return {
    cargando, error, todos, ordenesPendientes, cargandoOrdenes, errorOrdenes, revision,
    cargar, cargarOrdenes, recargar, obtener, crearDesdeOrden, guardar, emitir, cobrar, cobrarOrden,
  }
}
