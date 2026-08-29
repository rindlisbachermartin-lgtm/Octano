export type EstadoPresupuesto = 'Borrador' | 'Enviado' | 'Aprobado' | 'Rechazado' | 'Convertido a OT'

export interface ItemPresupuesto {
  tipo: 'REPUESTO' | 'MANO_OBRA'
  referenciaId?: number
  descripcion: string
  cantidad: number
  precioUnitario: number
  subtotal: number
}

export interface Presupuesto {
  id: number
  numeroPresupuesto: string
  clienteId: number
  clienteNombre: string
  clienteCuit: string
  vehiculoId: number
  vehiculoInfo: string
  patente: string
  fechaEmision: string
  fechaVencimiento: string
  items: ItemPresupuesto[]
  subtotalNeto: number
  montoIva: number
  totalPresupuesto: number
  estado: EstadoPresupuesto
  ordenTrabajoGeneradaId?: number
  observaciones?: string
}
