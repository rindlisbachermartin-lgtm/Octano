import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { stripTypeScriptTypes } from 'node:module'
import { computed } from 'vue'
import { parse } from '@vue/compiler-sfc'
import { test } from 'node:test'

const source = readFileSync('app/pages/qr/[code].vue', 'utf8').replace(/\r\n/g, '\n')
const resolution = stripTypeScriptTypes(source.slice(source.indexOf('const code = computed'), source.indexOf('\nonMounted(')))
function fixture(code, qrCodes = []) {
  const db = { value: { qrCodes, vehicles: [{ id: 1, qrCode: 'OCT-1000', plate: 'ABC-123' }] } }
  const refs = new Function('computed', 'db', 'route', `${resolution}; return { qrRecord, assignedVehicle };`)(computed, db, { params: { code } })
  return { db, ...refs }
}

test('public QR resolves only an existing assigned code and its matching vehicle', () => {
  const state = fixture(' oct-1000 ', [{ code: 'OCT-1000', status: 'asignado', vehicleId: 1 }])
  assert.equal(state.assignedVehicle.value.id, 1)
})

test('unknown public QR remains unknown even if a vehicle contains that code', () => {
  const state = fixture('OCT-1000')
  const before = structuredClone(state.db.value)
  assert.equal(state.qrRecord.value, undefined)
  assert.equal(state.assignedVehicle.value, undefined)
  assert.deepEqual(state.db.value, before)
})

test('available QR never exposes a vehicle or changes its assignment publicly', () => {
  const state = fixture('OCT-1000', [{ code: 'OCT-1000', status: 'disponible', vehicleId: null }])
  const before = structuredClone(state.db.value)
  assert.ok(state.qrRecord.value)
  assert.equal(state.assignedVehicle.value, undefined)
  assert.deepEqual(state.db.value, before)
})

test('inconsistent assigned QR does not resolve to another vehicle', () => {
  for (const record of [
    { code: 'OCT-1000', status: 'asignado', vehicleId: 2 },
    { code: 'OCT-2000', status: 'asignado', vehicleId: 1 }
  ]) {
    const state = fixture(record.code, [record])
    assert.equal(state.assignedVehicle.value, undefined)
  }
})

test('public QR has no assignment form, input or vehicle registration component', () => {
  const { descriptor, errors } = parse(source)
  assert.deepEqual(errors, [])
  const nodes = [descriptor.template.ast]
  while (nodes.length) {
    const node = nodes.pop()
    assert.ok(!['form', 'input', 'VehiculosModalFormularioVehiculo', 'VehiculosModalVincularQr'].includes(node.tag))
    nodes.push(...(node.children || []))
  }
  assert.ok(!descriptor.scriptSetup.content.includes('assignQrToVehicle'))
})

const admin = readFileSync('app/components/vehiculos/ModalVincularQr.vue', 'utf8').replace(/\r\n/g, '\n')
const confirm = stripTypeScriptTypes(admin.match(/function handleConfirm\([\s\S]*?\n}/)[0])
function adminFixture(qrCodes) {
  const assignments = [], events = []
  const context = {
    db: { value: { qrCodes, vehicles: [{ id: 1, plate: 'ABC-123', qrCode: null }] } },
    formError: { value: '' }, activeCode: { value: 'OCT-1000' },
    selectedVehicle: { value: { id: 1, plate: 'ABC-123', qrCode: null } },
    selectedVehicleId: { value: 1 },
    assignQrToVehicle: (...args) => { assignments.push(args); return true },
    notify: () => {}, emit: (...args) => events.push(args),
  }
  const handleConfirm = new Function(...Object.keys(context), `${confirm}; return handleConfirm`)(...Object.values(context))
  return { ...context, handleConfirm, assignments, events }
}

test('admin rejects manually entered codes that were never generated or are no longer available', () => {
  for (const records of [[], [{ code: 'OCT-1000', status: 'asignado', vehicleId: 2 }]]) {
    const state = adminFixture(records)
    state.handleConfirm()
    assert.ok(state.formError.value)
    assert.deepEqual(state.assignments, [])
    assert.deepEqual(state.events, [])
  }
})

test('admin can assign a generated available code and closes the form after saving', () => {
  const state = adminFixture([{ code: 'OCT-1000', status: 'disponible', vehicleId: null }])
  state.handleConfirm()
  assert.equal(state.formError.value, '')
  assert.deepEqual(state.assignments, [['OCT-1000', 1]])
  assert.deepEqual(state.events, [['assigned', { qrCode: 'OCT-1000', vehicleId: 1 }], ['close']])
})
