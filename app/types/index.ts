export interface Client {
  id: number
  name: string
  doc: string
  phone: string
  email: string
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
}

export interface Task {
  name: string
  done: boolean
}

export interface OrderPart {
  name: string
  price: number
  id: number
}

export interface Photo {
  sector: string
  data: string
}

export interface Order {
  id: number
  vehicle: number
  service: string
  status: string
  mechanic: string
  bay: number | null
  date: string
  time: string
  progress?: number
  km?: number | null
  diagnosis: string
  tasks: Task[]
  parts: OrderPart[]
  notes: string
  photos: Photo[]
  serviceTypes?: string[]
  oilSpec?: string
  replacedFilters?: string[]
}

export interface Appointment {
  id: number
  vehicle: number
  date: string
  time: string
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
  laborAmount?: number
  partsAmount?: number
  netAmount?: number
  vatAmount?: number
  items?: InvoiceItem[]
  clientName?: string
  clientDoc?: string
  clientVatCondition?: string
}

export interface AppNotification {
  id: number
  title: string
  detail: string
  read: boolean
}

export interface Database {
  clients: Client[]
  vehicles: Vehicle[]
  orders: Order[]
  appointments: Appointment[]
  parts: Part[]
  quotes: Quote[]
  invoices: Invoice[]
  notifications: AppNotification[]
  qrCodes: QrItem[]
}
