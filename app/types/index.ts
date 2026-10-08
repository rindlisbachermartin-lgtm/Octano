export type VatCondition = 'Consumidor Final' | 'IVA Responsable Inscripto' | 'Responsable Monotributo' | 'IVA Sujeto Exento' | 'IVA No Alcanzado' | 'Monotributista Social' | 'Monotributo Trabajador Independiente Promovido' | 'Sujeto No Categorizado'
export type IssuerVatCondition = 'IVA Responsable Inscripto' | 'Responsable Monotributo' | 'IVA Sujeto Exento'

export interface Client {
  id: number
  name: string
  doc: string
  phone: string
  email: string
  address?: string
  city?: string
  province?: string
  vatCondition?: VatCondition
  active: boolean
}

export interface ServiceRecord {
  date: string
  km: number
  oil: string
  filters?: string[]
  notes?: string
}

export interface TimingBeltRecord {
  date: string
  km: number
  parts?: string[]
  notes?: string
}

export interface Vehicle {
  ownershipHistory?: { from: number; to: number; date: string }[]
  ownershipHistory?: { from: number; to: number; date: string }[]
  id: number
  client: number
  brand: string
  model: string
  year: number
  engine: string
  plate: string
  km: number
  color: string
  qrCode?: string | null
  lastService?: ServiceRecord | null
  lastTimingBelt?: TimingBeltRecord | null
}

export interface QrItem {
  code: string
  status: 'disponible' | 'asignado'
  vehicleId: number | null
  createdAt: string
  assignedAt?: string | null
  printedAt?: string | null
}

export interface Task {
  name: string
  done: boolean
}

export interface OrderPart {
  name: string
  price: number
  id: number
  quantity?: number
  unitPrice?: number
  additional?: boolean
}

export interface OrderPartSelection {
  partId: number
  quantity: number
  name?: string
  unitPrice?: number
}

export interface Photo {
  sector: string
  data: string
}

export interface Order {
  id: number
  appointmentId?: number | null
  budgetId?: number | null
  cancellationReason?: string
  startedAt?: string
  vehicle: number
  service: string
  status: string
  mechanic: string
  bay: number | null
  date: string
  time: string
  progress?: number
  exitDate?: string
  exitTime?: string
  km?: number | null
  diagnosis: string
  mechanicNotes?: string
  tasks: Task[]
  parts: OrderPart[]
  notes: string
  photos: Photo[]
  serviceTypes?: string[]
  oilSpec?: string
  oilProvidedByCustomer?: boolean
  replacedFilters?: string[]
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
}

export interface Part {
  id: number
  name: string
  brand: string
  oem: string
  stock: number
  min: number
  cost: number
  price: number
  compatible: number[]
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



export interface InvoiceItem {
  description: string
  quantity: number
  unitPrice: number
  total: number
}

export interface Invoice {
  issuer?: { name: string; cuit: string; address: string; city: string; province: string; phone: string; activityStartDate?: string }
  id: number
  vehicle: number
  orderId?: number | null
  description: string
  total: number
  type: string
  status: string
  date: string
  isFiscal?: boolean
  cae?: string | null
  caeVto?: string | null
  ptoVta?: number
  nroCmp?: number
  paymentMethod?: string | null
  paymentDate?: string
  paymentNote?: string
  laborAmount?: number
  partsAmount?: number
  netAmount?: number
  vatAmount?: number
  items?: InvoiceItem[]
  clientName?: string
  clientDoc?: string
  clientVatCondition?: string
  issuerVatCondition?: IssuerVatCondition
}

export interface AppNotification {
  id: number
  title: string
  detail: string
  read: boolean
}

export interface VehicleCatalogEntry {
  brand: string
  model: string
  engine: string
}

export interface Database {
  issuerVatCondition: IssuerVatCondition
  clients: Client[]
  vehicles: Vehicle[]
  vehicleCatalog: VehicleCatalogEntry[]
  orders: Order[]
  appointments: Appointment[]
  parts: Part[]
  quotes: Quote[]
  invoices: Invoice[]
  notifications: AppNotification[]
  qrCodes: QrItem[]
  qrResetVersion?: number
}
