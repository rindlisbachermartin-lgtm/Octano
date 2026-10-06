import assert from 'node:assert/strict'
import { test } from 'node:test'
import { canEditIssuedOrder, editIssuedOrder, addOrderPart, assignOrderParts, validateOrderParts, releaseUnstartedOrderParts, orderBillingParts, orderWorkKinds, orderWorkTypes } from '../app/utils/orderWorkflow.ts'

function fixture(status = 'En espera') {
  const order = { id: 10, vehicle: 1, service: 'Revisión', mechanic: 'Nicolás', status, parts: [], tasks: [{ name: 'Revisión', done: false }], notes: '', diagnosis: '', oilSpec: '', km: null }
  const part = { id: 2, name: 'Filtro', price: 120, stock: 4, compatible: [1] }
  const database = { orders: [order], vehicles: [{ id: 1 }, { id: 2 }], quotes: [], appointments: [], parts: [part] }
  return { order, part, database }
}

test('before starting, order details and mechanic are editable and linked turn stays synchronized', () => {
  const { database, order } = fixture('Pendiente de ingreso')
  order.appointmentId = 30
  const appointment = { id: 30, orderId: 10, reason: 'Revisión' }
  database.appointments.push(appointment)
  assert.equal(editIssuedOrder(database, order, { ...order, service: 'Revisión de frenos', mechanic: 'Santiago', notes: 'Avisar al cliente', km: 50000 }), '')
  assert.equal(order.mechanic, 'Santiago')
  assert.equal(order.service, 'Revisión de frenos')
  assert.equal(appointment.reason, order.service)
})

test('active, finished, cancelled and previously started orders cannot be reassigned or edited', () => {
  for (const status of ['En proceso', 'Finalizado', 'Cancelado']) {
    const { database, order } = fixture(status)
    assert.equal(canEditIssuedOrder(order), false)
    assert.notEqual(editIssuedOrder(database, order, { ...order, mechanic: 'Santiago' }), '')
    assert.equal(order.mechanic, 'Nicolás')
  }
  const { database, order } = fixture()
  order.startedAt = '2026-10-06T12:00:00Z'
  assert.notEqual(editIssuedOrder(database, order, { ...order, mechanic: 'Santiago' }), '')
})

test('linked vehicle and invalid data cannot be changed before starting', () => {
  const { database, order } = fixture()
  order.budgetId = 4
  assert.notEqual(editIssuedOrder(database, order, { ...order, vehicle: 2 }), '')
  assert.notEqual(editIssuedOrder(database, order, { ...order, mechanic: 'Otro' }), '')
  assert.notEqual(editIssuedOrder(database, order, { ...order, km: -1 }), '')
  assert.equal(order.vehicle, 1)
})

test('adding parts during work consumes stock exactly once and preserves billing quantities', () => {
  const { database, order, part } = fixture('En proceso')
  assert.equal(addOrderPart(database, order, part.id, 2), '')
  assert.equal(part.stock, 2)
  assert.equal(order.parts[0].price, 240)
  assert.deepEqual(orderBillingParts(order), [{ description: 'Filtro', quantity: 2, unitPrice: 120, total: 240 }])
})

test('invalid quantities, insufficient stock, incompatible parts and closed orders cannot consume stock', () => {
  const { database, order, part } = fixture('En proceso')
  for (const quantity of [-1, 0, 0.5, 5, NaN]) assert.notEqual(addOrderPart(database, order, part.id, quantity), '')
  assert.equal(part.stock, 4)
  order.vehicle = 2
  assert.notEqual(addOrderPart(database, order, part.id, 1), '')
  order.vehicle = 1
  for (const status of ['Finalizado', 'Cancelado']) {
    order.status = status
    assert.notEqual(addOrderPart(database, order, part.id, 1), '')
  }
  assert.equal(part.stock, 4)
  assert.equal(order.parts.length, 0)
})

test('orders without a budget can receive parts before starting and bill them later', () => {
  for (const status of ['Pendiente de ingreso', 'En espera']) {
    const { database, order, part } = fixture(status)
    assert.equal(addOrderPart(database, order, part.id, 2), '')
    assert.equal(part.stock, 2)
    assert.deepEqual(orderBillingParts(order), [{ description: 'Filtro', quantity: 2, unitPrice: 120, total: 240 }])
  }
})

test('draft parts do not consume stock and batch assignment validates combined quantities atomically', () => {
  const { database, order, part } = fixture()
  const selections = [{ partId: part.id, quantity: 2 }, { partId: part.id, quantity: 3 }]
  assert.notEqual(validateOrderParts(database, order.vehicle, selections), '')
  assert.notEqual(assignOrderParts(database, order, selections), '')
  assert.equal(part.stock, 4)
  assert.equal(order.parts.length, 0)
  selections[1].quantity = 1
  assert.equal(validateOrderParts(database, order.vehicle, selections), '')
  assert.equal(part.stock, 4)
  assert.equal(assignOrderParts(database, order, selections), '')
  assert.equal(part.stock, 1)
  assert.equal(order.parts.length, 2)
})

test('cancelling an unstarted order returns only assigned inventory parts once', () => {
  const { database, order, part } = fixture()
  order.parts.push({ id: part.id, name: 'Presupuestado', quantity: 1, price: 120 })
  addOrderPart(database, order, part.id, 2)
  releaseUnstartedOrderParts(database, order)
  order.status = 'Cancelado'
  releaseUnstartedOrderParts(database, order)
  assert.equal(part.stock, 4)
  const active = fixture('En proceso')
  addOrderPart(active.database, active.order, active.part.id, 1)
  releaseUnstartedOrderParts(active.database, active.order)
  assert.equal(active.part.stock, 3)
})

test('custom parts and editable prices reach billing without consuming unrelated inventory', () => {
  const { database, order, part } = fixture()
  assert.equal(assignOrderParts(database, order, [
    { partId: 0, name: 'Repuesto especial', quantity: 2, unitPrice: 300 },
    { partId: part.id, quantity: 1, unitPrice: 150 },
  ]), '')
  assert.equal(part.stock, 3)
  assert.deepEqual(orderBillingParts(order), [
    { description: 'Repuesto especial', quantity: 2, unitPrice: 300, total: 600 },
    { description: 'Filtro', quantity: 1, unitPrice: 150, total: 150 },
  ])
  releaseUnstartedOrderParts(database, order)
  assert.equal(part.stock, 4)
})

test('incomplete editor rows or invalid custom prices cannot partially assign stock', () => {
  const { database, order, part } = fixture()
  for (const invalid of [
    { partId: -1, quantity: 1, unitPrice: 0 },
    { partId: 0, name: ' ', quantity: 1, unitPrice: 100 },
    { partId: 0, name: 'Otro', quantity: 1, unitPrice: -1 },
    { partId: part.id, quantity: 1, unitPrice: NaN },
  ]) {
    assert.notEqual(assignOrderParts(database, order, [{ partId: part.id, quantity: 1 }, invalid]), '')
    assert.equal(part.stock, 4)
    assert.equal(order.parts.length, 0)
  }
})

test('budget billing adds extra parts without duplicating the original budget items', () => {
  const { database, order, part } = fixture('En proceso')
  order.parts.push({ id: 5, name: 'Kit presupuestado', price: 200 })
  const budget = { items: [{ name: 'Kit presupuestado', quantity: 2, unitPrice: 100, total: 200 }] }
  assert.equal(addOrderPart(database, order, part.id, 1), '')
  const items = orderBillingParts(order, budget)
  assert.equal(items.length, 2)
  assert.equal(items.reduce((sum, item) => sum + item.total, 0), 320)
})

test('legacy order parts remain billable without a budget', () => {
  const { order } = fixture('Finalizado')
  order.parts.push({ id: 5, name: 'Repuesto anterior', price: 200 })
  assert.deepEqual(orderBillingParts(order), [{ description: 'Repuesto anterior', quantity: 1, unitPrice: 200, total: 200 }])
})

test('explicit work type controls the digital service record instead of incidental description words', () => {
  assert.deepEqual(orderWorkKinds({ service: 'Revisión de correa y pérdida de aceite', serviceTypes: ['Otro trabajo'] }), { service: false, timing: false })
  assert.deepEqual(orderWorkKinds({ service: 'Trabajo programado', serviceTypes: ['Service de mantenimiento'] }), { service: true, timing: false })
  assert.deepEqual(orderWorkKinds({ service: 'Trabajo programado', serviceTypes: ['Cambio de distribución'] }), { service: false, timing: true })
  assert.deepEqual(orderWorkTypes({ service: 'Trabajo', serviceTypes: ['Service de mantenimiento', 'Cambio de distribución'] }), ['Service de mantenimiento', 'Cambio de distribución'])
  assert.deepEqual(orderWorkKinds({ service: 'Cambio de aceite y filtros' }), { service: true, timing: false })
})

test('editing a work type saves the category without clearing the budget oil or technical data', () => {
  const { database, order } = fixture()
  order.oilSpec = '5W-40'
  order.diagnosis = 'Diagnóstico previo'
  assert.equal(editIssuedOrder(database, order, { ...order, serviceTypes: ['Cambio de distribución'], service: 'Cambio de distribución', notes: 'Revisar bomba' }), '')
  assert.deepEqual(orderWorkKinds(order), { service: false, timing: true })
  assert.equal(order.oilSpec, '5W-40')
  assert.equal(order.diagnosis, 'Diagnóstico previo')
})
