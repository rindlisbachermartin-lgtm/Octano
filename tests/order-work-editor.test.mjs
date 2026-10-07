import assert from 'node:assert/strict'
import { test } from 'node:test'
import { saveOrderWork, canEditOrderWork, orderBillingParts } from '../app/utils/orderWorkflow.ts'
import { billingEntryBudget } from '../app/utils/billingEntries.ts'

const details = () => ({ notes: 'Controlado', diagnosis: 'Cambio de filtro', km: 40000, oilSpec: '5W-30', oilProvidedByCustomer: false, replacedFilters: ['Filtro de aceite'], photos: [{ sector: 'Frente', data: 'foto' }] })

test('shared editor saves technical work without changing assigned vehicle, mechanic or service', () => {
  const order = { id: 10, vehicle: 1, mechanic: 'Nicolás', service: 'Service', status: 'En proceso', startedAt: '2026-10-07T12:00:00Z' }
  const database = { vehicles: [{ id: 1, km: 30000 }] }
  const draft = details()
  assert.equal(saveOrderWork(database, order, draft), '')
  assert.equal(order.mechanic, 'Nicolás')
  assert.equal(order.service, 'Service')
  assert.equal(order.status, 'En proceso')
  assert.equal(database.vehicles[0].km, 40000)
  draft.photos.push({ sector: 'Trasera', data: 'otra' })
  draft.replacedFilters.push('Filtro de aire')
  assert.equal(order.photos.length, 1)
  assert.equal(order.replacedFilters.length, 1)
})

test('closed orders and invalid mileage cannot mutate order or vehicle', () => {
  for (const status of ['Finalizado', 'Cancelado']) {
    const order = { id: 10, vehicle: 1, status }
    const database = { vehicles: [{ id: 1, km: 30000 }] }
    const before = structuredClone({ order, database })
    assert.equal(canEditOrderWork(order), false)
    assert.notEqual(saveOrderWork(database, order, details()), '')
    assert.deepEqual({ order, database }, before)
  }
  for (const km of [-1, 1.5, NaN]) {
    const order = { vehicle: 1, status: 'En proceso' }
    assert.notEqual(saveOrderWork({ vehicles: [] }, order, { ...details(), km }), '')
    assert.equal(order.diagnosis, undefined)
  }
})

test('viewer includes budget parts and extra parts once without mutating the order', () => {
  const order = { id: 10, budgetId: 4, parts: [{ id: 1, name: 'Filtro', quantity: 2, unitPrice: 10, price: 20 }, { id: 2, name: 'Aceite adicional', quantity: 1, price: 30, additional: true }] }
  const budgets = [{ id: 4, items: [{ name: 'Filtro', quantity: 2, unitPrice: 10, total: 20 }] }]
  const before = structuredClone(order)
  assert.deepEqual(orderBillingParts(order, billingEntryBudget(order, budgets)), [{ description: 'Filtro', quantity: 2, unitPrice: 10, total: 20 }, { description: 'Aceite adicional', quantity: 1, unitPrice: 30, total: 30 }])
  assert.deepEqual(order, before)
  assert.equal(orderBillingParts({ id: 11, budgetId: 4, parts: [] }, billingEntryBudget({ id: 11, budgetId: 4 }, budgets)).length, 1)
})
