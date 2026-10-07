import type { Database, Order, OrderPart, OrderPartSelection, Budget, InvoiceItem } from '../types/index'

export const canEditIssuedOrder = (order: Order) => !order.startedAt && ['Pendiente de ingreso', 'En espera'].includes(order.status)
export const canEditOrderWork = (order: Order) => canEditIssuedOrder(order) || order.status === 'En proceso'

export function saveOrderWork(database: Database, order: Order, changes: Pick<Order, 'notes' | 'diagnosis' | 'km' | 'oilSpec' | 'oilProvidedByCustomer' | 'replacedFilters' | 'photos'>): string {
  if (!canEditOrderWork(order)) return 'La orden está cerrada. No se pueden modificar sus datos.'
  if (changes.km != null && (!Number.isInteger(changes.km) || changes.km < 0)) return 'Ingresá un kilometraje válido, sin decimales y mayor o igual a cero.'
  Object.assign(order, changes, { photos: changes.photos.map(photo => ({ ...photo })), replacedFilters: [...(changes.replacedFilters || [])] })
  const vehicle = database.vehicles.find(vehicle => vehicle.id === order.vehicle)
  if (vehicle && changes.km != null) vehicle.km = changes.km
  return ''
}

export function orderWorkKinds(order: Pick<Order, 'service' | 'serviceTypes'>): { service: boolean; timing: boolean } {
  const source = order.serviceTypes?.length ? order.serviceTypes.join(' ') : order.service
  const text = source.toLowerCase()
  return { service: /service|mantenimiento|aceite|filtro/.test(text), timing: /distribuci|correa/.test(text) }
}

export function orderWorkTypes(order: Pick<Order, 'service' | 'serviceTypes'>): string[] {
  const kinds = orderWorkKinds(order)
  return [kinds.service && 'Service de mantenimiento', kinds.timing && 'Cambio de distribución'].filter(Boolean) as string[]
}

export function editIssuedOrder(database: Database, order: Order, changes: Pick<Order, 'vehicle' | 'service' | 'mechanic' | 'notes' | 'diagnosis' | 'oilSpec' | 'km' | 'tasks' | 'serviceTypes'>): string {
  if (!canEditIssuedOrder(order)) return 'La orden ya se inició o está cerrada. No se pueden modificar sus datos ni cambiar el mecánico.'
  if (!database.vehicles.some((v) => v.id === changes.vehicle)) return 'Seleccioná un vehículo válido.'
  if ((order.appointmentId || order.budgetId || database.quotes.some((q) => q.orderId === order.id)) && changes.vehicle !== order.vehicle) return 'El vehículo está vinculado al turno o presupuesto de esta orden.'
  if (!changes.service.trim() || !['Nicolás', 'Santiago'].includes(changes.mechanic)) return 'Completá el trabajo y seleccioná un mecánico.'
  if (changes.km != null && (!Number.isInteger(changes.km) || changes.km < 0)) return 'Ingresá un kilometraje válido.'
  Object.assign(order, changes, { service: changes.service.trim() })
  const appointment = database.appointments.find((a) => a.id === order.appointmentId || a.orderId === order.id)
  if (appointment) appointment.reason = order.service
  return ''
}

export function addOrderPart(database: Database, order: Order, partId: number, quantity: number): string {
  return assignOrderParts(database, order, [{ partId, quantity }])
}

export function validateOrderParts(database: Database, vehicleId: number, selections: OrderPartSelection[]): string {
  const quantities = new Map<number, number>()
  for (const { partId, quantity, name, unitPrice } of selections) {
    if (!Number.isInteger(quantity) || quantity <= 0) return 'Ingresá una cantidad entera mayor a cero.'
    if (unitPrice !== undefined && (!Number.isFinite(unitPrice) || unitPrice < 0)) return 'Ingresá un precio unitario válido.'
    if (partId === 0) {
      if (!name?.trim() || unitPrice === undefined) return 'Completá el nombre y el precio del repuesto personalizado.'
      continue
    }
    const part = database.parts.find((p) => p.id === partId)
    if (!part) return 'Seleccioná un repuesto del inventario.'
    if (part.compatible.length && !part.compatible.includes(vehicleId)) return 'El repuesto no es compatible con este vehículo.'
    const total = (quantities.get(partId) || 0) + quantity
    if (part.stock < total) return 'No hay stock suficiente para esa cantidad.'
    quantities.set(partId, total)
  }
  return ''
}

export function assignOrderParts(database: Database, order: Order, selections: OrderPartSelection[]): string {
  if (!canEditIssuedOrder(order) && order.status !== 'En proceso') return 'La orden está cerrada. No se pueden agregar repuestos.'
  const error = validateOrderParts(database, order.vehicle, selections)
  if (error) return error
  for (const selection of selections) {
    const { partId, quantity } = selection
    const part = database.parts.find((p) => p.id === partId)
    const unitPrice = selection.unitPrice ?? part!.price
    order.parts.push({ id: partId, name: part?.name || selection.name!.trim(), quantity, unitPrice, price: Math.round(unitPrice * quantity * 100) / 100, additional: true })
    if (part) part.stock -= quantity
  }
  return ''
}

export function releaseUnstartedOrderParts(database: Database, order: Order) {
  if (!canEditIssuedOrder(order)) return
  for (const assigned of order.parts.filter((part) => part.additional)) {
    const part = database.parts.find((p) => p.id === assigned.id)
    if (part) part.stock += assigned.quantity || 1
  }
}

export const orderPartInvoiceItem = (part: OrderPart): InvoiceItem => ({ description: part.name, quantity: part.quantity || 1, unitPrice: part.unitPrice ?? part.price, total: part.price })

export function orderBillingParts(order: Order, budget?: Budget | null): InvoiceItem[] {
  if (!budget) return order.parts.map(orderPartInvoiceItem)
  const base = budget.items?.length
    ? budget.items.map((item) => ({ description: item.name, quantity: item.quantity, unitPrice: item.unitPrice, total: item.total ?? item.quantity * item.unitPrice }))
    : budget.materials > 0 ? [{ description: 'Repuestos e insumos presupuestados', quantity: 1, unitPrice: budget.materials, total: budget.materials }] : []
  return [...base, ...order.parts.filter((part) => part.additional).map(orderPartInvoiceItem)]
}
