export type VatCondition = 'Consumidor Final' | 'IVA Responsable Inscripto' | 'Responsable Monotributo' | 'IVA Sujeto Exento' | 'IVA No Alcanzado' | 'Monotributista Social' | 'Monotributo Trabajador Independiente Promovido' | 'Sujeto No Categorizado'

export interface Client {
  id: number
  name: string
  doc: string
  phone: string
  email: string
  address?: string
  streetNumber?: number | null
  city?: string
  province?: string
  vatCondition?: VatCondition
  active: boolean
}

export interface Vehicle {
  registro?: {
    marcaId: number
    modeloId: number
    versionId: number | null
    titular: string
    activo: boolean
    kilometraje: number | null
  }
  id: number
  client: number
  brand: string
  model: string
  version?: string
  year: number
  engine: string
  plate: string
  km: number
  color: string
  qrCode?: string | null
}

export interface Appointment {
  id: number
  orderId?: number | null
  vehicle: number
  date: string
  time: string
  endTime?: string
  reason: string
  status: string
  budgetId?: number | null
  registro?: { vehiculo: string; patente: string; titular: string }
}

export interface PartCompatibility {
  brand: string
  model: string
  version?: string
}

export interface Part {
  archived?: boolean
  id: number
  name: string
  brand: string
  oem: string
  stock: number
  min: number
  cost: number
  margin?: number
  price: number
  compatible: PartCompatibility[]
}

export interface BudgetItem {
  id: number | string
  partId?: number | null
  name: string
  quantity: number
  unitPrice: number
  total: number
  isCustom?: boolean
}

export interface Quote {
  id: number
  vehicle: number
  description: string
  labor: number
  materials: number
  items?: BudgetItem[]
  subtotal?: number
  tax?: number
  total?: number
  status: string
  appointmentId?: number | null
  orderId?: number | null
  date?: string
  serviceTypes?: string[]
  oilSpec?: string
}
export type Budget = Quote
