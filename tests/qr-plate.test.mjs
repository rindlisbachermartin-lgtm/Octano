import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { stripTypeScriptTypes } from 'node:module'
import { test } from 'node:test'

const source = readFileSync('app/pages/qr/[code].vue', 'utf8').replace(/\r\n/g, '\n')
const functions = stripTypeScriptTypes(source.slice(source.indexOf('function bindVehicle('), source.indexOf('\nonMounted(')))
const normalize = stripTypeScriptTypes(source.match(/const normalizePlate = .*\n/)[0])

function fixture(plate, success = true, qr = 'OCT-9999') {
  const vehicles = [{ id: 1, plate: 'AC 284 FN' }, { id: 2, plate: 'ABC-123' }]
  const context = {
    db: { value: { vehicles } }, code: { value: qr }, searchVehicle: { value: plate },
    assignmentError: { value: '' }, registerVehicleOpen: { value: false }, pendingPlate: { value: '' },
    replacementVehicle: { value: null },
    assignQrToVehicle: (...args) => { assignments.push(args); return success },
    notify: () => {}, router: { replace: path => routes.push(path) },
  }
  const assignments = [], routes = []
  const handlers = new Function(...Object.keys(context), `${normalize}\n${functions}; return { handleAssign, bindVehicle, confirmReplacement };`)(...Object.values(context))
  return { ...context, ...handlers, assignments, routes }
}

test('complete plate resolves exactly with case and separator normalization', () => {
  for (const plate of ['ac284fn', 'AC-284-FN', ' AC 284 FN ']) {
    const state = fixture(plate)
    state.handleAssign()
    assert.deepEqual(state.assignments, [['OCT-9999', 1]])
    assert.deepEqual(state.routes, ['/ficha/1'])
    assert.equal(state.registerVehicleOpen.value, false)
  }
})

test('unknown plate opens registration without assigning or inventing a vehicle', () => {
  const state = fixture('ag123tt')
  state.handleAssign()
  assert.equal(state.registerVehicleOpen.value, true)
  assert.equal(state.pendingPlate.value, 'AG123TT')
  assert.equal(state.db.value.vehicles.length, 2)
  assert.deepEqual(state.assignments, [])
  state.bindVehicle({ id: 3, plate: 'AG-123-TT' })
  assert.deepEqual(state.assignments, [['OCT-9999', 3]])
  assert.equal(state.registerVehicleOpen.value, false)
})

test('partial and invalid plates never assign a QR or start registration', () => {
  for (const plate of ['AC', '284', 'kkkkkkkk', '00000000000', 'AC@284FN']) {
    const state = fixture(plate)
    state.handleAssign()
    assert.ok(state.assignmentError.value)
    assert.deepEqual(state.assignments, [])
    assert.equal(state.registerVehicleOpen.value, false)
  }
})

test('invalid QR and rejected assignment do not navigate to a vehicle', () => {
  const invalid = fixture('AG123TT', true, 'bad/code')
  invalid.handleAssign()
  assert.ok(invalid.assignmentError.value)
  assert.equal(invalid.registerVehicleOpen.value, false)
  const rejected = fixture('AC284FN', false)
  rejected.handleAssign()
  assert.ok(rejected.assignmentError.value)
  assert.deepEqual(rejected.routes, [])
})

test('existing QR requires confirmation before replacement and cancellation changes nothing', () => {
  const state = fixture('AC284FN')
  state.db.value.vehicles[0].qrCode = 'OCT-1111'
  state.handleAssign()
  assert.equal(state.replacementVehicle.value.id, 1)
  assert.deepEqual(state.assignments, [])
  state.replacementVehicle.value = null
  state.confirmReplacement()
  assert.deepEqual(state.assignments, [])
  assert.equal(state.db.value.vehicles[0].qrCode, 'OCT-1111')
  state.handleAssign()
  state.confirmReplacement()
  assert.deepEqual(state.assignments, [['OCT-9999', 1]])
  assert.equal(state.replacementVehicle.value, null)
})
