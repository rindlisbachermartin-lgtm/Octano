import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('../app/components/mecanico/PanelMecanico.vue', import.meta.url), 'utf8')
const saveSource = source.match(/function saveOrderChanges\(showToast = true\) \{[\s\S]*?\n\}(?=\n\nfunction startOrder)/)?.[0]
assert.ok(saveSource, 'La función de guardado debe estar disponible')
const names = ['selectedOrder', 'canEditOrder', 'editKm', 'editNotes', 'editDiagnosis', 'selectedFilters', 'editOilSpec', 'oilProvidedByCustomer', 'db', 'notify', 'isEditingKm']

function fixture(status, km) {
  const order = { status, vehicle: 1, km: 70000, oilSpec: '5W-30', notes: '', diagnosis: '', replacedFilters: [] }
  const vehicle = { id: 1, km: 70000 }
  const messages = []
  const isEditingKm = { value: true }
  const save = new Function(...names, `${saveSource}\nreturn saveOrderChanges`)(
    { value: order }, { value: ['En espera', 'En proceso'].includes(status) },
    { value: km }, { value: 'Revisado' }, { value: '' }, { value: ['Filtro de aceite'] },
    { value: '  Castrol Edge 5W-40  ' }, { value: true },
    { value: { vehicles: [vehicle] } }, (message) => messages.push(message), isEditingKm,
  )
  return { order, vehicle, messages, save, isEditingKm }
}

for (const status of ['En espera', 'En proceso']) {
  const { order, vehicle, save, isEditingKm } = fixture(status, 75200)
  assert.equal(save(false), true)
  assert.equal(order.km, 75200)
  assert.equal(vehicle.km, 75200)
  assert.equal(isEditingKm.value, false, 'El campo vuelve a bloquearse después de guardar')
  assert.equal(order.oilSpec, 'Castrol Edge 5W-40')
  assert.equal(order.oilProvidedByCustomer, true)
  assert.deepEqual(order.replacedFilters, ['Filtro de aceite'])
}

const zero = fixture('En proceso', 0)
assert.equal(zero.save(false), true)
assert.equal(zero.vehicle.km, 0)

for (const km of [-1, 12.5, NaN, Infinity]) {
  const { order, vehicle, messages, save } = fixture('En proceso', km)
  const before = JSON.stringify({ order, vehicle })
  assert.equal(save(), false)
  assert.equal(JSON.stringify({ order, vehicle }), before)
  assert.equal(messages.length, 1)
}

for (const status of ['Finalizado', 'Cancelado']) {
  const { order, vehicle, save } = fixture(status, 75200)
  const before = JSON.stringify({ order, vehicle })
  assert.equal(save(), false)
  assert.equal(JSON.stringify({ order, vehicle }), before)
}

console.log('Kilómetros y aceite: guardado, validación y bloqueo de órdenes verificados.')
