import assert from 'node:assert/strict'
import { test } from 'node:test'
import { issueAppointmentOrder, syncAppointmentOrder, activateAppointmentOrder, expireAppointmentOrders, cancelAppointmentOrder, isWorkshopOrder } from '../app/utils/appointmentLifecycle.ts'

function fixture(withBudget = false) {
  const appointment = { id: 10, vehicle: 2, date: '2026-10-07', time: '09:00', endTime: '11:00', reason: 'Revisión', status: 'Programado', budgetId: withBudget ? 20 : null }
  const budget = { id: 20, vehicle: 2, description: 'Revisión', status: 'Pendiente', serviceTypes: ['Frenos'], items: [{ partId: 3, name: 'Pastilla', quantity: 2, unitPrice: 100 }] }
  const database = { appointments: [appointment], orders: [], quotes: [budget] }
  return { appointment, budget, database }
}

test('issuing a turn without a budget creates exactly one order hidden from workshop work', () => {
  const { database, appointment } = fixture()
  const order = issueAppointmentOrder(database, appointment)
  assert.equal(order.status, 'Pendiente de ingreso')
  assert.equal(isWorkshopOrder(order), false)
  assert.equal(appointment.orderId, order.id)
  assert.equal(order.appointmentId, appointment.id)
  assert.equal(issueAppointmentOrder(database, appointment).id, order.id)
  assert.equal(database.orders.length, 1)
})

test('budget items and references carry over, activation reuses the issued order', () => {
  const { database, appointment, budget } = fixture(true)
  const issued = issueAppointmentOrder(database, appointment)
  assert.equal(budget.status, 'Con turno')
  assert.equal(budget.orderId, issued.id)
  assert.deepEqual(issued.parts, [{ id: 3, name: 'Pastilla (x2)', price: 200 }])
  const active = activateAppointmentOrder(database, appointment, new Date('2026-10-07T12:30:00Z'))
  assert.equal(active.id, issued.id)
  assert.equal(active.status, 'En espera')
  assert.equal(active.time, '09:30')
  assert.equal(isWorkshopOrder(active), true)
  assert.equal(appointment.status, 'En Taller')
  assert.equal(budget.status, 'En taller')
  assert.equal(activateAppointmentOrder(database, appointment, new Date('2026-10-07T12:40:00Z')), null)
  assert.equal(database.orders.length, 1)
  expireAppointmentOrders(database, new Date('2026-10-08T12:00:00Z'))
  assert.equal(active.status, 'En espera')
})

test('no-show expires only after the estimated end, cancelling the pending order and releasing budget', () => {
  const { database, appointment, budget } = fixture(true)
  const order = issueAppointmentOrder(database, appointment)
  expireAppointmentOrders(database, new Date('2026-10-07T14:00:00Z'))
  assert.equal(appointment.status, 'Programado')
  expireAppointmentOrders(database, new Date('2026-10-07T14:01:00Z'))
  assert.equal(appointment.status, 'No asistió')
  assert.equal(order.status, 'Cancelado')
  assert.match(order.cancellationReason, /no ingresó/)
  assert.equal(budget.status, 'Pendiente')
  assert.equal(budget.appointmentId, null)
  assert.equal(budget.orderId, null)
  assert.equal(activateAppointmentOrder(database, appointment, new Date('2026-10-07T15:00:00Z')), null)
})

test('late check-in expires the order even before the periodic review runs', () => {
  const { database, appointment } = fixture()
  issueAppointmentOrder(database, appointment)
  assert.equal(activateAppointmentOrder(database, appointment, new Date('2026-10-07T15:00:00Z')), null)
  assert.equal(database.orders[0].status, 'Cancelado')
})

test('rescheduling synchronizes the same pending order and budget removal releases references', () => {
  const { database, appointment, budget } = fixture(true)
  const order = issueAppointmentOrder(database, appointment)
  appointment.date = '2026-10-08'
  appointment.time = '14:00'
  appointment.endTime = '16:00'
  appointment.vehicle = 3
  appointment.reason = 'Otro trabajo'
  appointment.budgetId = null
  syncAppointmentOrder(database, appointment, budget.id)
  assert.equal(database.orders.length, 1)
  assert.equal(order.vehicle, 3)
  assert.equal(order.service, 'Otro trabajo')
  assert.equal(order.date, '2026-10-08')
  assert.equal(order.time, '14:00')
  assert.equal(order.budgetId, null)
  assert.equal(order.parts.length, 0)
  assert.equal(budget.orderId, null)
  expireAppointmentOrders(database, new Date('2026-10-07T15:00:00Z'))
  assert.equal(order.status, 'Pendiente de ingreso')
})

test('manual cancellation gives pending order a reason and releases budget', () => {
  const { database, appointment, budget } = fixture(true)
  const order = issueAppointmentOrder(database, appointment)
  cancelAppointmentOrder(database, appointment)
  assert.equal(order.status, 'Cancelado')
  assert.equal(order.cancellationReason, 'Turno cancelado.')
  assert.equal(appointment.status, 'Cancelado')
  assert.equal(budget.orderId, null)
})

test('legacy turns without an estimated finish expire at the end of the scheduled day', () => {
  const { database, appointment } = fixture()
  delete appointment.endTime
  const order = issueAppointmentOrder(database, appointment)
  expireAppointmentOrders(database, new Date('2026-10-07T21:00:00Z'))
  assert.equal(order.status, 'Pendiente de ingreso')
  expireAppointmentOrders(database, new Date('2026-10-08T03:00:00Z'))
  assert.equal(order.status, 'Cancelado')
})
