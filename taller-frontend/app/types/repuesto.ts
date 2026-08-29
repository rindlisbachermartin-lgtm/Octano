export interface CompatibilidadVehiculo {
  id?: number
  marca: string
  modelo: string
  anioDesde: number
  anioHasta: number
  motorizacion: string
}

export interface Repuesto {
  id: number
  codigoOem: string
  descripcion: string
  marca: string
  categoria: string
  costoCompra: number
  precioVenta: number
  stockActual: number
  stockMinimo: number
  ubicacionDeposito?: string
  compatibilidades: CompatibilidadVehiculo[]
  estaActivo: boolean
}

export interface FormularioRepuesto {
  codigoOem: string
  descripcion: string
  marca: string
  categoria: string
  costoCompra: number
  precioVenta: number
  stockActual: number
  stockMinimo: number
  ubicacionDeposito?: string
  compatibilidades: CompatibilidadVehiculo[]
}
