export type CondicionFiscal = 
  | 'Consumidor Final'
  | 'Responsable Inscripto'
  | 'Monotributo'
  | 'Exento'

export interface Cliente {
  id: number
  nombre: string
  cuit: string
  condicionFiscal: CondicionFiscal
  email: string
  telefono: string
  direccion?: string
  vehiculoPrincipal?: string
  patentePrincipal?: string
  ultimaVisita?: string
  totalGastado: number
  estaActivo: boolean
}

export interface FormularioCliente {
  nombre: string
  cuit: string
  condicionFiscal: CondicionFiscal
  email: string
  telefono: string
  direccion?: string
  patente?: string
  vehiculo?: string
  anio?: number
  kilometraje?: number
}
