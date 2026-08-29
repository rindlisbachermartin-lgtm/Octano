export type EstadoOrdenTrabajo = 
  | 'Ingresado'
  | 'Peritaje Realizado'
  | 'En Reparacion'
  | 'Esperando Repuestos'
  | 'Control de Calidad'
  | 'Finalizado'
  | 'Entregado'

export interface ItemRepuestoOT {
  repuestoId: number
  codigoOem: string
  descripcion: string
  cantidad: number
  precioUnitario: number
  subtotal: number
}

export interface ItemManoObraOT {
  descripcion: string
  horas: number
  precioPorHora: number
  subtotal: number
}

export interface FotoPeritaje {
  id?: number
  sector: 'Frente' | 'Lateral Izquierdo' | 'Lateral Derecho' | 'Trasera' | 'Tablero y Kilometraje' | 'Detalles y Danos'
  url: string
  fechaCaptura: string
  observacion?: string
}

export interface OrdenTrabajo {
  id: number
  numeroOt: string
  clienteId: number
  clienteNombre: string
  clienteTelefono: string
  vehiculoId: number
  vehiculoInfo: string
  patente: string
  kilometrajeIngreso: number
  mecanicoAsignado: string
  diagnosticoInicial: string
  trabajosRealizados?: string
  estado: EstadoOrdenTrabajo
  fechaIngreso: string
  fechaFinalizacionEstimada?: string
  fechaEgreso?: string
  itemsRepuestos: ItemRepuestoOT[]
  itemsManoObra: ItemManoObraOT[]
  fotosPeritaje: FotoPeritaje[]
  totalRepuestos: number
  totalManoObra: number
  totalGeneral: number
  fueFacturado: boolean
  mensajeWhatsappEnviado: boolean
}
