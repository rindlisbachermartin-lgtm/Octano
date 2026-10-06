import type { Appointment, Database, Order } from '../types/index'
import { releaseUnstartedOrderParts } from './orderWorkflow.ts'

export const isScheduledAppointment = (a: Appointment) => ['Programado', 'Confirmado'].includes(a.status)
export const isWorkshopOrder = (o: Order) => ['En espera', 'En proceso'].includes(o.status)

function unlinkBudget(database: Database, a: Appointment) {
  const budget = database.quotes.find((q) => q.id === a.budgetId)
  if (budget?.appointmentId === a.id) {
    budget.appointmentId = null
    if (budget.orderId === a.orderId) budget.orderId = null
    if (budget.status === 'Con turno') budget.status = 'Pendiente'
  }
}

export function issueAppointmentOrder(database: Database, a: Appointment): Order {
  const existing = database.orders.find((o) => o.id === a.orderId || o.appointmentId === a.id)
  if (existing) {
    a.orderId = existing.id
    return existing
  }
  const budget = database.quotes.find((q) => q.id === a.budgetId)
  const order: Order = {
    id: Math.max(1048, ...database.orders.map((o) => o.id)) + 1,
    appointmentId: a.id, budgetId: a.budgetId || null,
    vehicle: a.vehicle, service: a.reason, status: 'Pendiente de ingreso',
    mechanic: 'Nicolás', bay: null, date: a.date, time: a.time,
    progress: 0, diagnosis: '', tasks: [{ name: a.reason, done: false }],
    parts: (budget?.items || []).map((item, i) => ({
      id: item.partId || i + 1,
      name: item.quantity > 1 ? `${item.name} (x${item.quantity})` : item.name,
      price: item.unitPrice * item.quantity,
    })),
    notes: '', photos: [], serviceTypes: budget?.serviceTypes || [], oilSpec: budget?.oilSpec || '',
  }
  database.orders.unshift(order)
  a.orderId = order.id
  if (budget) {
    budget.status = 'Con turno'
    budget.appointmentId = a.id
    budget.orderId = order.id
  }
  return order
}

export function syncAppointmentOrder(database: Database, a: Appointment, previousBudgetId?: number | null) {
  if (previousBudgetId && previousBudgetId !== a.budgetId) {
    unlinkBudget(database, { ...a, budgetId: previousBudgetId })
  }
  const order = issueAppointmentOrder(database, a)
  if (order.status !== 'Pendiente de ingreso') return
  const budget = database.quotes.find((q) => q.id === a.budgetId)
  const previousService = order.service
  const budgetChanged = (order.budgetId || null) !== (a.budgetId || null)
  order.vehicle = a.vehicle
  order.service = a.reason
  order.date = a.date
  order.time = a.time
  order.budgetId = a.budgetId || null
  order.tasks = order.tasks.map((task) => task.name === previousService ? { ...task, name: a.reason } : task)
  if (budgetChanged) {
    order.parts = [...(budget?.items || []).map((item, i) => ({ id: item.partId || i + 1, name: item.quantity > 1 ? `${item.name} (x${item.quantity})` : item.name, price: item.unitPrice * item.quantity })), ...order.parts.filter((part) => part.additional)]
    order.serviceTypes = budget?.serviceTypes || []
    order.oilSpec = budget?.oilSpec || ''
  }
  if (budget) {
    budget.status = 'Con turno'
    budget.appointmentId = a.id
    budget.orderId = order.id
  }
}

export function cancelAppointmentOrder(database: Database, a: Appointment, noShow = false) {
  if (!isScheduledAppointment(a)) return
  a.status = noShow ? 'No asistió' : 'Cancelado'
  const order = database.orders.find((o) => o.id === a.orderId || o.appointmentId === a.id)
  if (order?.status === 'Pendiente de ingreso') {
    releaseUnstartedOrderParts(database, order)
    order.status = 'Cancelado'
    order.cancellationReason = noShow ? 'El vehículo no ingresó antes del fin del turno.' : 'Turno cancelado.'
    order.bay = null
  }
  unlinkBudget(database, a)
}

export function expireAppointmentOrders(database: Database, now = new Date()) {
  for (const a of database.appointments) {
    if (!isScheduledAppointment(a)) continue
    // Older records without an estimated finish expire at the end of their day.
    const deadline = new Date(`${a.date}T${a.endTime || '23:59'}:00-03:00`)
    if (now.getTime() > deadline.getTime()) cancelAppointmentOrder(database, a, true)
  }
}

export function activateAppointmentOrder(database: Database, a: Appointment, now = new Date()): Order | null {
  expireAppointmentOrders(database, now)
  if (!isScheduledAppointment(a)) return null
  const order = issueAppointmentOrder(database, a)
  if (order.status !== 'Pendiente de ingreso') return null
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Argentina/Buenos_Aires', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(now)
  const part = (type: string) => parts.find((p) => p.type === type)?.value
  order.date = `${part('year')}-${part('month')}-${part('day')}`
  order.time = `${part('hour')}:${part('minute')}`
  order.status = 'En espera'
  a.status = 'En Taller'
  const budget = database.quotes.find((q) => q.id === a.budgetId)
  if (budget) budget.status = 'En taller'
  return order
}
