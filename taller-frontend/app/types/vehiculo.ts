export interface Vehiculo {
  id: number
  clienteId: number
  clienteNombre: string
  patente: string
  marca: string
  modelo: string
  anio: number
  motorizacion: string
  kilometrajeActual: number
  numeroChasis?: string
  observaciones?: string
  fechaUltimoIngreso?: string
  estaActivo: boolean
}

export interface FormularioVehiculo {
  clienteId: number
  patente: string
  marca: string
  modelo: string
  anio: number
  motorizacion: string
  kilometrajeActual: number
  numeroChasis?: string
  observaciones?: string
}
