export interface Cliente {
  id: number
  nombre: string
  cuit: string
  email: string
  telefono: string
  direccion?: string
  cantidadVehiculos: number
  vehiculoPrincipal?: string
  patentePrincipal?: string
  ultimaVisita?: string
  estaActivo: boolean
}

export interface FormularioCliente {
  nombre: string
  cuit: string
  email: string
  telefono: string
  direccion?: string
  patente?: string
  vehiculo?: string
  anio?: number
  kilometraje?: number
}
