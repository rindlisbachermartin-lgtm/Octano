import assert from 'node:assert/strict'
import { saveOrderCompletionData } from '../app/utils/orderWorkflow.ts'

function fixture(status = 'En proceso') {
  const order = { status, vehicle: 1, km: 70000, serviceTypes: ['Service de mantenimiento'], oilSpec: '5W-30', notes: 'Indicaciones del administrador', mechanicNotes: '', replacedFilters: [] }
  const vehicle = { id: 1, km: 70000 }
  const database = { vehicles: [vehicle] }
  const completion = { km: 75200, mechanicNotes: 'Revisado', oilSpec: '  Castrol Edge 5W-40  ', replacedFilters: ['Filtro de aceite'] }
  return { order, vehicle, database, completion }
}

for (const km of [0, 75200]) {
  const { order, vehicle, database, completion } = fixture()
  assert.equal(saveOrderCompletionData(database, order, { ...completion, km }), '')
  assert.equal(order.km, km)
  assert.equal(vehicle.km, km)
  assert.equal(order.oilSpec, 'Castrol Edge 5W-40')
  assert.equal(order.mechanicNotes, 'Revisado')
  assert.equal(order.notes, 'Indicaciones del administrador')
  assert.deepEqual(order.replacedFilters, ['Filtro de aceite'])
  completion.replacedFilters.push('Filtro de aire')
  assert.deepEqual(order.replacedFilters, ['Filtro de aceite'])
}

for (const km of ['', null, -1, 12.5, NaN, Infinity]) {
  const { order, vehicle, database, completion } = fixture()
  const before = structuredClone({ order, vehicle })
  assert.notEqual(saveOrderCompletionData(database, order, { ...completion, km }), '')
  assert.deepEqual({ order, vehicle }, before)
}

for (const status of ['Pendiente de ingreso', 'En espera', 'Finalizado', 'Cancelado']) {
  const { order, vehicle, database, completion } = fixture(status)
  const before = structuredClone({ order, vehicle })
  assert.notEqual(saveOrderCompletionData(database, order, completion), '')
  assert.deepEqual({ order, vehicle }, before)
}

console.log('Cierre del mecánico: kilómetros, aceite, filtros y observaciones separados; estados inválidos protegidos: OK')
