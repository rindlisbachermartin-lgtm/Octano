import assert from 'node:assert/strict'
import { test } from 'node:test'
import { isPartCompatible, matchesPartCompatibility, migratePartCompatibility } from '../app/utils/partCompatibility.ts'
import { addOrderPart } from '../app/utils/orderWorkflow.ts'

test('compatibility applies to another vehicle of the same brand and model', () => {
  const part = { id: 1, name: 'Filtro', price: 10, stock: 3, compatible: [{ brand: 'Volkswagen', model: 'Golf' }] }
  const vehicle = { id: 99, brand: ' volkswagen ', model: 'GOLF' }
  const order = { vehicle: 99, status: 'En proceso', parts: [] }
  assert.equal(addOrderPart({ vehicles: [vehicle], parts: [part] }, order, 1, 1), '')
  assert.equal(part.stock, 2)
  assert.equal(isPartCompatible(part, { brand: 'Volkswagen', model: 'Polo' }), false)
  assert.equal(isPartCompatible(part, { brand: 'Toyota', model: 'Golf' }), false)
  assert.equal(isPartCompatible(part, { brand: 'Volkswagen', model: '' }), false)
})

test('brand and model filtering works without registered vehicles', () => {
  const entry = { brand: 'Fiat', model: 'Cronos' }
  assert.equal(matchesPartCompatibility(entry, ' fiat ', 'CRONOS'), true)
  assert.equal(matchesPartCompatibility(entry, 'Fiat'), true)
  assert.equal(matchesPartCompatibility(entry, 'Ford', 'Cronos'), false)
  assert.equal(isPartCompatible({ compatible: [entry] }, entry), true)
  assert.equal(isPartCompatible({ compatible: [] }, entry), true)
})

test('legacy IDs migrate to deduplicated independent brand/model pairs', () => {
  const vehicles = [
    { id: 1, brand: 'Volkswagen', model: 'Golf' },
    { id: 2, brand: ' volkswagen ', model: 'GOLF' },
    { id: 3, brand: 'Ford', model: 'Focus' },
  ]
  const migrated = migratePartCompatibility([1, 2, 3], vehicles)
  assert.deepEqual(migrated, [{ brand: 'Volkswagen', model: 'Golf' }, { brand: 'Ford', model: 'Focus' }])
  assert.deepEqual(migratePartCompatibility(migrated, []), migrated)
  assert.equal(isPartCompatible({ compatible: migrated }, { brand: 'Volkswagen', model: 'Golf' }), true)
})

test('a missing legacy vehicle cannot turn restricted compatibility into unrestricted compatibility', () => {
  const compatible = migratePartCompatibility([999], [])
  assert.equal(compatible.length, 1)
  assert.equal(isPartCompatible({ compatible }, { brand: 'Fiat', model: 'Cronos' }), false)
  assert.deepEqual(migratePartCompatibility([], []), [])
})


test('previous year ranges are removed and no longer restrict brand/model compatibility', () => {
  const entries = [
    { brand: 'Fiat', model: 'Cronos', yearFrom: 2015, yearTo: 2020 },
    { brand: 'Fiat', model: 'Cronos', yearFrom: 2022 },
  ]
  assert.deepEqual(migratePartCompatibility(entries, []), [{ brand: 'Fiat', model: 'Cronos' }])
  for (const year of [2010, 2021, 2025]) {
    assert.equal(isPartCompatible({ compatible: entries }, { brand: 'Fiat', model: 'Cronos', year }), true)
  }
})

test('specific versions match independently from engine and reject another or unknown version', () => {
  const entry = { brand: 'Volkswagen', model: 'Golf', version: 'Highline' }
  const part = { id: 1, name: 'Filtro', stock: 2, price: 10, compatible: [entry] }
  assert.equal(isPartCompatible(part, { brand: 'Volkswagen', model: 'Golf', version: ' highline ', engine: '1.4 TSI' }), true)
  for (const version of ['Comfortline', '', undefined]) {
    const database = { parts: [part], vehicles: [{ id: 1, brand: 'Volkswagen', model: 'Golf', version, engine: '1.4 TSI' }] }
    const order = { vehicle: 1, status: 'En proceso', parts: [] }
    assert.notEqual(addOrderPart(database, order, 1, 1), '')
    assert.equal(part.stock, 2)
  }
  assert.equal(matchesPartCompatibility(entry, 'Volkswagen', 'Golf', 'Highline'), true)
  assert.equal(matchesPartCompatibility(entry, 'Volkswagen', 'Golf', 'Comfortline'), false)
  assert.equal(isPartCompatible({ compatible: [{ brand: 'Volkswagen', model: 'Golf' }] }, { brand: 'Volkswagen', model: 'Golf', version: 'Comfortline' }), true)
})

test('stored versions survive migration and remain separate for the same model', () => {
  const entries = [
    { brand: 'Volkswagen', model: 'Golf', version: 'Highline' },
    { brand: 'Volkswagen', model: 'Golf', version: 'Comfortline' },
  ]
  assert.deepEqual(migratePartCompatibility(entries, []), entries)
  assert.deepEqual(migratePartCompatibility([1], [{ id: 1, brand: 'Volkswagen', model: 'Golf', version: 'Highline' }]), [entries[0]])
})
