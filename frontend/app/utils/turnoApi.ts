import type { Appointment } from '~/types'

const estados = {
  PROGRAMADO: 'Programado',
  CONFIRMADO: 'Confirmado',
  EN_TALLER: 'En Taller',
  FINALIZADO: 'Finalizado',
  CANCELADO: 'Cancelado',
  NO_ASISTIO: 'No asistió',
}

export interface TurnoApi {
  id: number
  vehiculoId: number
  presupuestoId: number | null
  ordenId: number | null
  fecha: string
  horaInicio: string
  horaFin: string | null
  motivoVisita: string
  estado: keyof typeof estados
  marca: string
  modelo: string
  patente: string
  clienteNombre: string
}

export interface PaginaTurnos<T> {
  contenido: T[]
  totalPaginas: number
}

export interface ConflictoTurno {
  id: number
  patente: string
  horaInicio: string
}

export interface DisponibilidadTurno {
  vehiculoConTurno: boolean
  conflictos: ConflictoTurno[]
}

export function adaptarTurno(t: TurnoApi): Appointment {
  return {
    id: t.id,
    vehicle: t.vehiculoId,
    budgetId: t.presupuestoId,
    orderId: t.ordenId,
    date: t.fecha,
    time: t.horaInicio.slice(0, 5),
    endTime: t.horaFin?.slice(0, 5),
    reason: t.motivoVisita,
    status: estados[t.estado],
    registro: { vehiculo: `${t.marca} ${t.modelo}`, patente: t.patente, titular: t.clienteNombre },
  }
}
