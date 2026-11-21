export type EstadoComprobante = 'BORRADOR' | 'EMITIDO' | 'PAGADO_PARCIAL' | 'COBRADO'

export type MedioPago = 'EFECTIVO' | 'TRANSFERENCIA' | 'TARJETA_DEBITO' | 'TARJETA_CREDITO' | 'MERCADO_PAGO'

export const mediosPago: { value: MedioPago; label: string }[] = [
  { value: 'EFECTIVO', label: 'Efectivo' },
  { value: 'TRANSFERENCIA', label: 'Transferencia bancaria' },
  { value: 'TARJETA_DEBITO', label: 'Tarjeta de débito' },
  { value: 'TARJETA_CREDITO', label: 'Tarjeta de crédito' },
  { value: 'MERCADO_PAGO', label: 'Mercado Pago / billetera virtual' },
]

export const estadosComprobante: Record<EstadoComprobante, { label: string; badge: string }> = {
  BORRADOR: { label: 'Borrador', badge: 'neutral' },
  EMITIDO: { label: 'Emitido', badge: 'amber' },
  PAGADO_PARCIAL: { label: 'Pago parcial', badge: 'blue' },
  COBRADO: { label: 'Cobrado', badge: 'green' },
}

export interface ComprobanteItemApi {
  renglon: number
  repuestoId: number | null
  codigoOem: string | null
  descripcion: string
  cantidad: number
  precioUnitario: number
  subtotal: number
}

export interface CobroApi {
  id: number
  comprobanteId: number
  fecha: string
  importe: number
  medioPago: MedioPago
  observaciones: string
  usuarioId: number
}

export interface ComprobanteParteApi {
  nombre: string
  documento: string | null
  calle: string | null
  altura: number | null
  condicionIva: string | null
  telefono: string | null
}

export interface ComprobanteVehiculoApi {
  patente: string
  marca: string
  modelo: string
  version: string | null
  anio: number
}

export interface ComprobanteResumenApi {
  id: number
  ordenId: number | null
  clienteId: number
  vehiculoId: number
  fecha: string
  descripcion: string
  estado: EstadoComprobante
  numero: number | null
  tipo: 'X' | 'A' | 'B' | 'C'
  puntoVenta: number
  total: number
  pagado: number
  saldo: number
  clienteNombre: string
  patente: string
  marca: string
  modelo: string
}

export interface ComprobanteApi extends ComprobanteResumenApi {
  esFiscal: false
  caeSimulado: string | null
  manoObra: number
  porcentajeIva: number
  totalRepuestos: number
  subtotal: number
  montoIva: number
  emisor: ComprobanteParteApi
  receptor: ComprobanteParteApi
  vehiculo: ComprobanteVehiculoApi
  creadoEn: string
  creadoPor: number
  emitidoEn: string | null
  emitidoPor: number | null
  fechaModif: string
  usuarioModificacionId: number
  items: ComprobanteItemApi[]
  cobro: CobroApi | null
  cobros: CobroApi[]
}

export interface OrdenPendienteApi {
  ordenId: number
  clienteId: number
  vehiculoId: number
  clienteNombre: string
  patente: string
  marca: string
  modelo: string
  descripcion: string
  presupuestoId: number | null
  finalizadaEn: string | null
  total: number
}

export interface PaginaApi<T> {
  contenido: T[]
  pagina: number
  tamano: number
  totalElementos: number
  totalPaginas: number
}

export function numeroComprobante(c: Pick<ComprobanteResumenApi, 'numero' | 'id'> & { puntoVenta?: number }) {
  return c.numero ? `Nº ${String(c.puntoVenta ?? 3).padStart(4, '0')}-${String(c.numero).padStart(8, '0')}` : `Borrador #${c.id}`
}

export function vehiculoLabel(v: Pick<ComprobanteVehiculoApi, 'marca' | 'modelo' | 'version' | 'anio'>) {
  return [v.marca, v.modelo, v.version].filter(Boolean).join(' ') + ` · ${v.anio}`
}

export function medioPagoLabel(medio: MedioPago) {
  return mediosPago.find((item) => item.value === medio)?.label ?? medio
}

export function fechaLabel(fecha?: string | null) {
  if (!fecha) return '—'
  if (fecha.includes('T')) {
    const instante = new Date(fecha)
    if (!Number.isNaN(instante.getTime())) return instante.toLocaleDateString('es-AR', {
      timeZone: 'America/Argentina/Buenos_Aires', day: '2-digit', month: '2-digit', year: 'numeric',
    })
  }
  const soloFecha = fecha.slice(0, 10)
  const [anio, mes, dia] = soloFecha.split('-')
  return dia && mes && anio ? `${dia}/${mes}/${anio}` : soloFecha
}

export async function todasLasPaginas<T>(ruta: string, headers: Record<string, string>, query: Record<string, string | number | boolean> = {}) {
  const contenido: T[] = []
  let pagina = 0
  let totalPaginas = 1
  do {
    const resultado = await $fetch<PaginaApi<T>>(ruta, { headers, query: { ...query, pagina, tamano: 100 } })
    contenido.push(...resultado.contenido)
    totalPaginas = resultado.totalPaginas
    pagina++
  } while (pagina < totalPaginas)
  return contenido
}
