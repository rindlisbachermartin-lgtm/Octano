import { isPartCompatible } from './partCompatibility.ts'
import { billingEntryBudget } from './billingEntries.ts'
import { budgetAmounts } from './budgetAmounts.ts'
import type { Database, Order, OrderPart, OrderPartSelection, Budget, InvoiceItem } from '../types/index'

export const canEditIssuedOrder = (order: Order) => !order.startedAt && ['Pendiente de ingreso', 'En espera'].includes(order.status)
export const canEditOrderWork = (order: Order) => canEditIssuedOrder(order) || order.status === 'En proceso'
export const canEditOrderDetails = (order: Order) => canEditOrderWork(order) || order.status === 'Finalizado'

export interface OrderCompletionData {
  km: number | null | ''
  mechanicNotes: string
  oilSpec: string
  replacedFilters: string[]
}

export function saveOrderCompletionData(database: Database, order: Order, data: OrderCompletionData): string {
  if (order.status !== 'En proceso') return 'Solo se puede finalizar una orden en proceso.'
  if (typeof data.km !== 'number' || !Number.isInteger(data.km) || data.km < 0) return 'Ingresá un kilometraje válido, sin decimales y mayor o igual a cero.'
  const isService = orderWorkKinds(order).service
  if (isService && !data.oilSpec.trim()) return 'Indicá el aceite utilizado en el service.'
  order.km = data.km
  order.mechanicNotes = data.mechanicNotes.trim()
  if (isService) {
    order.oilSpec = data.oilSpec.trim()
    order.replacedFilters = [...data.replacedFilters]
  }
  const vehicle = database.vehicles.find(vehicle => vehicle.id === order.vehicle)
  if (vehicle) vehicle.km = data.km
  return ''
}

export function saveOrderWork(database: Database, order: Order, changes: Pick<Order, 'notes' | 'diagnosis' | 'km' | 'oilSpec' | 'oilProvidedByCustomer' | 'replacedFilters' | 'photos'>): string {
  if (!canEditOrderDetails(order)) return 'La orden está cancelada. No se pueden modificar sus datos.'
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
  return [kinds.service && 'Service de mantenimiento', kinds.timing && 'Cambio de distribución', order.serviceTypes?.includes('Otro trabajo') && 'Otro trabajo'].filter(Boolean) as string[]
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

export function budgetOrderParts(budget?: Budget | null): OrderPart[] {
  return (budget?.items || []).map(item => ({ id: item.isCustom ? 0 : Number(item.partId) || 0, name: item.name,
    quantity: item.quantity, unitPrice: item.unitPrice, price: Math.round(item.unitPrice * item.quantity * 100) / 100,
    stockBaseQuantity: item.quantity }))
}

export function orderPartIds(order?: Order | null, budget?: Budget | null): number[] {
  return [...(order?.parts || []).map(part => part.id), ...budgetOrderParts(budget).map(part => part.id)].filter(id => id > 0)
}

export function budgetConflictsWithOrder(order: Order | undefined, budget: Budget | undefined): boolean {
  const ids = new Set(budgetOrderParts(budget).map(part => part.id).filter(id => id > 0))
  return !!order?.parts.some(part => part.additional && part.id > 0 && ids.has(part.id))
}

export function validateOrderParts(database: Database, vehicleId: number, selections: OrderPartSelection[], existingIds: number[] = []): string {
  const used = new Set(existingIds)
  for (const { partId, quantity, name, unitPrice } of selections) {
    if (!Number.isInteger(quantity) || quantity <= 0) return 'Ingresá una cantidad entera mayor a cero.'
    if (unitPrice !== undefined && (!Number.isFinite(unitPrice) || unitPrice < 0)) return 'Ingresá un precio unitario válido.'
    if (partId === 0) {
      if (!name?.trim() || unitPrice === undefined) return 'Completá el nombre y el precio del repuesto personalizado.'
      continue
    }
    const part = database.parts.find((p) => p.id === partId)
    if (!part || part.archived) return 'Seleccioná un repuesto del inventario.'
    if (used.has(partId)) return 'El repuesto ya está en la orden. Modificá la cantidad del renglón existente.'
    used.add(partId)
    if (!isPartCompatible(part, database.vehicles.find(vehicle => vehicle.id === vehicleId))) return 'El repuesto no es compatible con este vehículo.'
    if (part.stock < quantity) return 'No hay stock suficiente para esa cantidad.'
  }
  return ''
}

export function assignOrderParts(database: Database, order: Order, selections: OrderPartSelection[]): string {
  if (!canEditOrderWork(order)) return 'La orden está cerrada. No se pueden agregar repuestos.'
  const budget = billingEntryBudget(order, database.quotes || [])
  const error = validateOrderParts(database, order.vehicle, selections, orderPartIds(order, budget))
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

export function setOrderPartQuantity(database: Database, order: Order, assigned: OrderPart, quantity: number, allowReturn = true): string {
  if (!canEditOrderWork(order)) return 'Solo se pueden cambiar cantidades de una orden activa.'
  if (!order.parts.includes(assigned)) return 'Seleccioná un repuesto de esta orden.'
  if (!Number.isInteger(quantity) || quantity <= 0) return 'Ingresá una cantidad entera mayor a cero.'
  const budget = billingEntryBudget(order, database.quotes || [])
  const baseItem = !assigned.additional ? budget?.items?.[order.parts.filter(part => !part.additional).indexOf(assigned)] : undefined
  const previous = assigned.quantity ?? baseItem?.quantity ?? 1
  if (!allowReturn && quantity < previous) return 'Solo administración puede reducir cantidades y devolver materiales.'
  const unitPrice = assigned.unitPrice ?? baseItem?.unitPrice ?? assigned.price / previous
  const base = assigned.stockBaseQuantity ?? (assigned.additional ? 0 : previous)
  const difference = Math.max(0, quantity - base) - Math.max(0, previous - base)
  const part = assigned.id > 0 ? database.parts.find(part => part.id === assigned.id) : undefined
  if (assigned.id > 0 && !part) return 'No se encontró el repuesto en inventario.'
  if (part && difference > 0) {
    if (part.archived || !isPartCompatible(part, database.vehicles.find(vehicle => vehicle.id === order.vehicle))) return 'Seleccioná un repuesto activo y compatible.'
    if (part.stock < difference) return 'No hay stock suficiente para esa cantidad.'
  }
  const total = Math.round(unitPrice * quantity * 100) / 100
  if (!Number.isFinite(total)) return 'El importe no es válido.'
  if (part) part.stock -= difference
  assigned.quantity = quantity
  assigned.unitPrice = unitPrice
  assigned.price = total
  assigned.stockBaseQuantity = base
  return ''
}

export function releaseUnstartedOrderParts(database: Database, order: Order) {
  if (!canEditIssuedOrder(order)) return
  for (const assigned of order.parts) {
    const quantity = assigned.quantity ?? 1
    const consumed = Math.max(0, quantity - (assigned.stockBaseQuantity ?? (assigned.additional ? 0 : quantity)))
    if (!consumed) continue
    const part = database.parts.find((p) => p.id === assigned.id)
    if (part) part.stock += consumed
  }
}

export function removeOrderPart(database: Database, order: Order, assigned: OrderPart): string {
  if (!canEditOrderWork(order)) return 'Solo se pueden eliminar repuestos de una orden activa.'
  const index = order.parts.indexOf(assigned)
  if (index < 0 || !assigned.additional) return 'Seleccioná un repuesto agregado a esta orden.'
  const quantity = assigned.quantity ?? 1
  if (!Number.isInteger(quantity) || quantity <= 0) return 'La cantidad del repuesto no es válida.'
  const part = assigned.id > 0 ? database.parts.find(part => part.id === assigned.id) : undefined
  if (assigned.id > 0 && !part) return 'No se encontró el repuesto en inventario para devolver el stock.'
  order.parts.splice(index, 1)
  if (part) part.stock += Math.max(0, quantity - (assigned.stockBaseQuantity ?? 0))
  return ''
}

export const orderPartInvoiceItem = (part: OrderPart): InvoiceItem => ({ description: part.name, quantity: part.quantity || 1, unitPrice: part.unitPrice ?? part.price, total: part.price })

export function orderBillingParts(order: Order, budget?: Budget | null): InvoiceItem[] {
  if (!budget) return order.parts.map(orderPartInvoiceItem)
  const base = budget.items?.length
    ? budget.items.map((item, index) => {
      const assigned = order.parts.filter(part => !part.additional)[index]
      return { description: item.name, quantity: assigned?.quantity ?? item.quantity, unitPrice: assigned?.unitPrice ?? item.unitPrice,
        total: assigned?.price ?? item.total ?? item.quantity * item.unitPrice }
    })
    : budget.materials > 0 ? (order.parts.some(part => !part.additional) ? order.parts.filter(part => !part.additional).map(orderPartInvoiceItem)
      : [{ description: 'Repuestos e insumos presupuestados', quantity: 1, unitPrice: budget.materials, total: budget.materials }]) : []
  return [...base, ...order.parts.filter((part) => part.additional).map(orderPartInvoiceItem)]
}

export function orderBillingTotal(order: Order, budget: Budget): number {
  const materials = orderBillingParts(order, budget).reduce((sum, item) => sum + item.total, 0)
  const change = materials - budget.materials + (order.laborAmount ?? budget.labor) - budget.labor
  return Math.round((budgetAmounts(budget).total + change * 1.21) * 100) / 100
}

export function orderDisplayParts(order: Order, budget: Budget | null | undefined, inventory: Database['parts']): (InvoiceItem & { brand?: string; code?: string; assignment?: OrderPart })[] {
  const baseCount = budget ? (budget.items?.length || (budget.materials > 0 ? 1 : 0)) : 0
  const orderParts = budget ? order.parts.filter(part => part.additional) : order.parts
  return orderBillingParts(order, budget).map((row, index) => {
    let part: Database['parts'][number] | undefined
    let assignment: OrderPart | undefined
    if (index < baseCount) {
      assignment = order.parts.filter(part => !part.additional)[index]
      const item = budget?.items?.[index]
      if (item && !item.isCustom && item.partId !== 0) {
        if (item.partId != null) part = inventory.find(part => part.id === Number(item.partId))
        else {
          // Older budgets may lack IDs. Only use an unambiguous name match.
          const matches = inventory.filter(part => part.name === item.name)
          if (matches.length === 1) part = matches[0]
        }
      }
    } else {
      const assigned = orderParts[index - baseCount]
      if (assigned?.additional) assignment = assigned
      if (assigned && assigned.id > 0) part = inventory.find(part => part.id === assigned.id && part.name === assigned.name)
    }
    return { ...row, brand: part?.brand, code: part?.oem, ...(assignment ? { assignment } : {}) }
  })
}
