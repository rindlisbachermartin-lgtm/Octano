export type EstadoTurno = 'Pendiente' | 'Confirmado' | 'En Taller' | 'Cancelado'

export interface Turno {
  id: number
  clienteId: number
  clienteNombre: string
  vehiculoId: number
  vehiculoInfo: string
  patente: string
  fecha: string
  horaInicio: string
  horaFin: string
  motivoVisita: string
  estado: EstadoTurno
  mecanicoAsignado?: string
  observaciones?: string
}

export interface FormularioTurno {
  clienteId: number | null
  vehiculoId: number | null
  fecha: string
  horaInicio: string
  horaFin: string
  motivoVisita: string
  estado: EstadoTurno
  mecanicoAsignado?: string
  observaciones?: string
}
