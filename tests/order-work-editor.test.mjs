import assert from 'node:assert/strict'
import { test } from 'node:test'
import { saveOrderWork, canEditOrderWork, canEditOrderDetails, orderBillingParts, orderDisplayParts, saveOrderCompletionData } from '../app/utils/orderWorkflow.ts'
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

test('cancelled orders and invalid mileage cannot mutate order or vehicle', () => {
  for (const status of ['Cancelado']) {
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


test('OT details resolve brand and code by inventory ID, including budget and extra parts', () => {
  const inventory = [{ id: 1, name: 'Filtro de aceite', brand: 'MANN', oem: 'W 712/95' }, { id: 2, name: 'Filtro de aceite', brand: 'FRAM', oem: 'PH123' }]
  const order = { parts: [{ id: 2, name: 'Filtro de aceite', quantity: 1, unitPrice: 12, price: 12, additional: true }] }
  const budget = { items: [{ partId: 1, name: 'Filtro de aceite', quantity: 2, unitPrice: 10, total: 20 }], materials: 20 }
  const before = structuredClone({ inventory, order, budget })
  const rows = orderDisplayParts(order, budget, inventory)
  assert.deepEqual(rows.map(row => [row.brand, row.code, row.total]), [['MANN', 'W 712/95', 20], ['FRAM', 'PH123', 12]])
  assert.deepEqual({ inventory, order, budget }, before)
})

test('custom parts and ambiguous legacy names do not inherit unrelated brand or code', () => {
  const inventory = [{ id: 1, name: 'Filtro', brand: 'MANN', oem: 'W1' }, { id: 2, name: 'Filtro', brand: 'FRAM', oem: 'PH2' }]
  const order = { parts: [{ id: 0, name: 'Filtro', price: 15 }] }
  const custom = orderDisplayParts(order, null, inventory)[0]
  assert.equal(custom.code, undefined)
  const rows = orderDisplayParts({ parts: [] }, { items: [{ name: 'Filtro', quantity: 1, unitPrice: 10, total: 10 }, { partId: 1, name: 'Filtro', isCustom: true, quantity: 1, unitPrice: 15, total: 15 }] }, inventory)
  assert.equal(rows[0].brand, undefined)
  assert.equal(rows[1].code, undefined)
})


test('completion validates mileage and service oil before changing any data', () => {
  for (const km of ['', null, -1, 1.5, NaN]) {
    const order = { status: 'En proceso', vehicle: 1, serviceTypes: ['Service de mantenimiento'], parts: [], notes: 'Indicaciones del administrador' }
    const database = { vehicles: [{ id: 1, km: 40000 }] }
    const before = structuredClone({ order, database })
    assert.notEqual(saveOrderCompletionData(database, order, { km, mechanicNotes: 'No debe guardarse', oilSpec: '5W-30', replacedFilters: [] }), '')
    assert.deepEqual({ order, database }, before)
  }
  const order = { status: 'En proceso', vehicle: 1, serviceTypes: ['Service de mantenimiento'], parts: [], notes: 'Indicaciones del administrador' }
  const database = { vehicles: [{ id: 1, km: 40000 }] }
  assert.notEqual(saveOrderCompletionData(database, order, { km: 41000, mechanicNotes: 'No debe guardarse', oilSpec: '  ', replacedFilters: [] }), '')
  assert.equal(database.vehicles[0].km, 40000)
  const filters = ['Filtro de aceite']
  assert.equal(saveOrderCompletionData(database, order, { km: 41000, mechanicNotes: ' Service completo ', oilSpec: ' Castrol 5W-30 ', replacedFilters: filters }), '')
  filters.push('Filtro de aire')
  assert.equal(order.oilSpec, 'Castrol 5W-30')
  assert.equal(order.mechanicNotes, 'Service completo')
  assert.equal(order.notes, 'Indicaciones del administrador')
  assert.deepEqual(order.replacedFilters, ['Filtro de aceite'])
  assert.equal(database.vehicles[0].km, 41000)
})

test('non-service completion saves mileage and observations and closed orders cannot be changed', () => {
  const order = { status: 'En proceso', vehicle: 1, serviceTypes: ['Otro trabajo'], service: 'Revisión de aceite', oilSpec: 'Anterior' }
  const database = { vehicles: [{ id: 1, km: 40000 }] }
  assert.equal(saveOrderCompletionData(database, order, { km: 42000, mechanicNotes: ' Pastillas reemplazadas ', oilSpec: '', replacedFilters: [] }), '')
  assert.equal(order.oilSpec, 'Anterior')
  assert.equal(order.mechanicNotes, 'Pastillas reemplazadas')
  order.status = 'Finalizado'
  assert.notEqual(saveOrderCompletionData(database, order, { km: 43000, mechanicNotes: 'No debe guardarse', oilSpec: '', replacedFilters: [] }), '')
  assert.equal(database.vehicles[0].km, 42000)
  assert.equal(order.mechanicNotes, 'Pastillas reemplazadas')
})

test('finished order details remain editable without reopening the order', () => {
  const order = { id: 10, vehicle: 1, status: 'Finalizado', exitDate: '2026-10-08', exitTime: '12:00' }
  const database = { vehicles: [{ id: 1, km: 30000 }] }
  assert.equal(canEditOrderWork(order), false)
  assert.equal(canEditOrderDetails(order), true)
  assert.equal(saveOrderWork(database, order, details()), '')
  assert.equal(order.notes, 'Controlado')
  assert.equal(order.status, 'Finalizado')
  assert.equal(order.exitDate, '2026-10-08')
  assert.equal(order.exitTime, '12:00')
})
