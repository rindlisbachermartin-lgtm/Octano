export const estadosOrden = {
  PENDIENTE_INGRESO: 'Pendiente de ingreso', EN_ESPERA: 'En espera',
  EN_PROCESO: 'En proceso', FINALIZADO: 'Finalizado', CANCELADO: 'Cancelado',
} as const

export const tiposTrabajoOrden = { SERVICE: 'Service', DISTRIBUCION: 'Distribución', OTRO: 'Otro trabajo' } as const
export const filtrosOrden = { ACEITE: 'Filtro de aceite', AIRE: 'Filtro de aire', HABITACULO: 'Filtro de habitáculo', COMBUSTIBLE: 'Filtro de combustible' } as const
export type EstadoOrden = keyof typeof estadosOrden
export type TipoTrabajoOrden = keyof typeof tiposTrabajoOrden
export type FiltroOrden = keyof typeof filtrosOrden

export interface OrdenApi {
  id: number
  clienteId: number
  vehiculoId: number
  turnoId: number | null
  presupuestoId: number | null
  mecanicoId: number | null
  descripcion: string
  tiposTrabajo: TipoTrabajoOrden[]
  indicaciones: string
  diagnostico: string
  observacionesMecanico: string
  kilometraje: number | null
  aceite: string
  filtros: FiltroOrden[]
  manoObra: number
  porcentajeIva: number
  estado: EstadoOrden
  motivoCancelacion: string
  fechaCreacion: string
  ingresoFecha: string | null
  inicioFecha: string | null
  finFecha: string | null
  clienteNombre: string
  patente: string
  marca: string
  modelo: string
  version: string | null
  mecanicoNombre: string | null
  fechaTurno: string | null
  horaInicioTurno: string | null
}

export interface DetalleOrdenApi {
  orden: OrdenApi
  tareas: { id: number; descripcion: string; completada: boolean }[]
  items: { id: number; repuestoId: number | null; codigoOem: string | null; descripcion: string; cantidad: number; precioUnitario: number; subtotal: number; presupuestado: boolean; consumido: boolean }[]
  fotos: { id: number; sector: string; tipoContenido: string; fecha: string }[]
  progreso: number
  totalRepuestos: number
  subtotal: number
  montoIva: number
  total: number
}

export interface PaginaOrdenes<T> {
  contenido: T[]
  totalElementos: number
  totalPaginas: number
}

export function fechaOrden(fecha: string | null) {
  return fecha ? new Date(fecha).toLocaleString('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' }) : 'Sin registrar'
}

export function cierreOrden(kilometraje: number | null | '', observacionesMecanico: string, aceite: string, filtros: FiltroOrden[], service: boolean) {
  if (typeof kilometraje !== 'number' || !Number.isInteger(kilometraje) || kilometraje < 0 || kilometraje > 9999999) {
    throw new Error('Ingresá un kilometraje entero entre 0 y 9999999.')
  }
  if (service && !aceite.trim()) throw new Error('Indicá el aceite utilizado en el service.')
  return { kilometraje, observacionesMecanico: observacionesMecanico.trim(), aceite: service ? aceite.trim() : '', filtros: service ? [...filtros] : [] }
}
