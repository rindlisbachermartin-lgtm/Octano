import type { Budget, Client, Vehicle } from '~/types'
import { vehicleLabel } from './vehicleDisplay'

export type EstadoPresupuesto = 'PENDIENTE' | 'ACEPTADO' | 'RECHAZADO' | 'ANULADO'
export type TipoTrabajoPresupuesto = 'SERVICE' | 'DISTRIBUCION' | 'OTRO'
export const tiposTrabajoPresupuesto = {
  SERVICE: 'Service de mantenimiento',
  DISTRIBUCION: 'Cambio de distribución',
  OTRO: 'Otro trabajo',
}

export interface PresupuestoResumenApi {
  id: number
  clienteId: number
  vehiculoId: number
  fecha: string
  descripcion: string
  estado: EstadoPresupuesto
  total: number
  clienteNombre: string
  marca: string
  modelo: string
  version?: string | null
  patente: string
  ordenId: number | null
  turnoId: number | null
  turnoFecha: string | null
  turnoHora: string | null
  manoObra: number
  porcentajeIva: number
  totalRepuestos: number
  subtotal: number
  montoIva: number
}

export interface PresupuestoApi extends PresupuestoResumenApi {
  manoObra: number
  porcentajeIva: number
  totalRepuestos: number
  subtotal: number
  montoIva: number
  tiposTrabajo: TipoTrabajoPresupuesto[]
  turnoId: number | null
  turnoFecha: string | null
  turnoHora: string | null
  items: {
    renglon: number
    repuestoId: number | null
    descripcion: string
    cantidad: number
    precioUnitario: number
    subtotal: number
  }[]
}

export interface TallerPresupuesto {
  nombre: string
  calle: string
  altura: number | null
  telefono: string
}

export interface PresupuestoConDatos extends Budget {
  registro: {
    estado: EstadoPresupuesto
    porcentajeIva: number
    clienteId: number
    vehiculo: string
    patente: string
    turnoFecha?: string | null
    turnoHora?: string | null
    titular: string
  }
  cliente?: Client
  vehiculo?: Vehicle
  taller?: TallerPresupuesto
}

export function adaptarPresupuesto(p: PresupuestoApi): PresupuestoConDatos {
  const estados = { PENDIENTE: 'Pendiente', ACEPTADO: 'Aceptado', RECHAZADO: 'Rechazado', ANULADO: 'Anulado' }
  return {
    id: p.id, vehicle: p.vehiculoId, date: p.fecha, description: p.descripcion,
    labor: p.manoObra, materials: p.totalRepuestos, subtotal: p.subtotal, tax: p.montoIva, total: p.total,
    status: estados[p.estado], appointmentId: p.turnoId, orderId: p.ordenId,
    serviceTypes: p.tiposTrabajo.map(tipo => tiposTrabajoPresupuesto[tipo]),
    items: p.items.map(item => ({
      id: item.renglon, partId: item.repuestoId, name: item.descripcion,
      quantity: item.cantidad, unitPrice: item.precioUnitario, total: item.subtotal,
      isCustom: item.repuestoId === null,
    })),
    registro: {
      turnoFecha: p.turnoFecha, turnoHora: p.turnoHora,
      estado: p.estado, porcentajeIva: p.porcentajeIva, clienteId: p.clienteId,
      vehiculo: vehicleLabel(p.marca, p.modelo, p.version), patente: p.patente, titular: p.clienteNombre,
    },
  }
}

export function adaptarResumenPresupuesto(p: PresupuestoResumenApi): PresupuestoConDatos {
  return adaptarPresupuesto({ ...p, tiposTrabajo: [], items: [] })
}

export async function paginasPresupuestos<T>(ruta: string, headers: Record<string, string>, signal: AbortSignal, query: Record<string, string | number | boolean> = {}) {
  const contenido: T[] = []
  let pagina = 0
  let totalPaginas: number
  do {
    const resultado = await $fetch<{ contenido: T[]; totalPaginas: number }>(ruta, {
      headers, signal, query: { ...query, pagina, tamano: 100 },
    })
    contenido.push(...resultado.contenido)
    totalPaginas = resultado.totalPaginas
    pagina++
  } while (pagina < totalPaginas && !signal.aborted)
  return contenido
}
