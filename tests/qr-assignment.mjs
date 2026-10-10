import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { stripTypeScriptTypes } from 'node:module'

const source = readFileSync('app/composables/useDatabase.ts', 'utf8')
const assignment = source.match(/  function assignQrToVehicle\([\s\S]*?\n  }/)[0]
const db = { value: {
  vehicles: [{ id: 1, qrCode: 'OCT-2041' }, { id: 2, qrCode: 'OCT-2042' }, { id: 3, qrCode: null }],
  qrCodes: [
    { code: 'OCT-2041', status: 'asignado', vehicleId: 1, assignedAt: '2026-09-01' },
    { code: 'OCT-2042', status: 'asignado', vehicleId: 2, assignedAt: '2026-09-01' },
    { code: 'OCT-2045', status: 'disponible', vehicleId: null },
    { code: 'OCT-2046', status: 'disponible', vehicleId: null },
  ],
} }
const auth = { initialize() {}, owner: { value: {} }, session: { value: 'admin@example.com' } }
const assign = new Function('db', 'useOwnerAccount', `${stripTypeScriptTypes(assignment)}; return assignQrToVehicle`)(db, () => auth)

assert.equal(assign(' oct-2045 ', 3), true)
assert.equal(db.value.vehicles[2].qrCode, 'OCT-2045')
assert.equal(db.value.qrCodes[2].vehicleId, 3)
assert.equal(db.value.qrCodes[2].status, 'asignado')
assert.ok(db.value.qrCodes[2].assignedAt)

let before = structuredClone(db.value)
assert.equal(assign('oct-2045', 2), false)
assert.deepEqual(db.value, before)
assert.equal(assign('OCT-2046', 1), true)
assert.equal(db.value.vehicles[0].qrCode, 'OCT-2046')
assert.equal(db.value.qrCodes[0].status, 'disponible')
assert.equal(db.value.qrCodes[0].vehicleId, null)
assert.equal(db.value.qrCodes[0].assignedAt, null)
assert.equal(db.value.qrCodes[3].vehicleId, 1)

before = structuredClone(db.value)
assert.equal(assign('OCT-2046', 1), true)
assert.equal(db.value.qrCodes.length, before.qrCodes.length)
assert.equal(assign('', 3), false)
assert.equal(assign('a', 3), false)
assert.equal(assign('../qr', 3), false)
assert.equal(assign('OCT-9999', 999), false)
assert.deepEqual(db.value, before)

before = structuredClone(db.value)
assert.equal(assign('oct-nuevo', 3), false)
assert.deepEqual(db.value, before, 'Un QR inexistente no debe crearse ni liberar el anterior')
assert.equal(assign('OCT-NUEVO', 1), false)
assert.deepEqual(db.value, before)
for (const session of [null, 'mechanic:Nicolás', 'mechanic-preview:Santiago']) {
  auth.owner.value = null
  auth.session.value = session
  assert.equal(assign('OCT-2041', 3), false)
  assert.deepEqual(db.value, before, 'Solo administración puede asignar QR')
}
auth.session.value = 'demo'
assert.equal(assign('OCT-2041', 3), true)
assert.equal(db.value.qrCodes.length, before.qrCodes.length)
console.log('Asignación administrativa de QR existentes, reemplazo y rechazo sin mutaciones: OK')
