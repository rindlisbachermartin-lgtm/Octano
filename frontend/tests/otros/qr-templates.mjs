import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { stripTypeScriptTypes } from 'node:module'
import { computed, ref } from 'vue'

const databaseSource = readFileSync('app/composables/useDatabase.ts', 'utf8')
const resetSource = databaseSource.match(/function resetLegacyQrData\([\s\S]*?\n}/)[0]
const reset = new Function(`${stripTypeScriptTypes(resetSource)}; return resetLegacyQrData`)()
const legacy = { qrCodes: [{ code: 'OCT-2041' }], vehicles: [{ id: 1, qrCode: 'OCT-2041', km: 1234 }], clients: [{ id: 1 }] }
reset(legacy)
assert.deepEqual(legacy.qrCodes, [])
assert.equal(legacy.vehicles[0].qrCode, null)
assert.equal(legacy.vehicles[0].km, 1234)
assert.deepEqual(legacy.clients, [{ id: 1 }])
legacy.qrCodes.push({ code: 'OCT-NUEVO' })
legacy.vehicles[0].qrCode = 'OCT-NUEVO'
reset(legacy)
assert.equal(legacy.qrCodes.length, 1)
assert.equal(legacy.vehicles[0].qrCode, 'OCT-NUEVO')
const generation = databaseSource.match(/  function generateQrBatch\([\s\S]*?\n  }/)[0]
const db = { value: { qrCodes: [{ code: 'OCT-2053', status: 'asignado' }], vehicles: [
  { id: 1, plate: 'AC 284 FN' }, { id: 2, plate: 'ABC 123' },
] } }
const generate = new Function('db', `${stripTypeScriptTypes(generation)}; return generateQrBatch`)(db)
const first = generate()
const second = generate()
assert.equal(first.length, 16)
assert.equal(second.length, 16)
assert.equal(new Set(db.value.qrCodes.map(q => q.code)).size, 33)
assert.ok([...first, ...second].every(q => q.status === 'disponible' && q.vehicleId === null))

// La consulta pública sin asignación se verifica en qr-plate.test.mjs.
const templateSource = readFileSync('app/pages/codigos-qr.vue', 'utf8').replace(/\r\n/g, '\n')
const selectedExpression = templateSource.match(/const selectedQrs = .*\n/)[0]
const readyExpression = templateSource.match(/const canDownload = .*\n/)[0]
const displayedQrs = ref(first)
const selectedCodes = ref([first[0].code, second[0].code])
const isGenerating = ref(false)
const isDownloading = ref(false)
const printable = new Function('computed', 'displayedQrs', 'selectedCodes', 'isGenerating', 'isDownloading', `${stripTypeScriptTypes(selectedExpression + readyExpression)}; return {selectedQrs, canDownload}`)(computed, displayedQrs, selectedCodes, isGenerating, isDownloading)
assert.equal(printable.selectedQrs.value.length, 1)
assert.equal(printable.canDownload.value, false)
selectedCodes.value = first.map(q => q.code)
assert.equal(printable.canDownload.value, true)
first[0].printedAt = '2026-10-06T12:00:00Z'
displayedQrs.value = [...first]
assert.equal(printable.selectedQrs.value.length, 15)
assert.equal(printable.canDownload.value, false)
delete first[0].printedAt
displayedQrs.value = [...first]
assert.equal(printable.canDownload.value, true)
displayedQrs.value = [...first, second[0]]
selectedCodes.value = [...first, second[0]].map(q => q.code)
assert.equal(printable.canDownload.value, false)
displayedQrs.value = [...first]
selectedCodes.value = first.map(q => q.code)
isGenerating.value = true
assert.equal(printable.canDownload.value, false)
isGenerating.value = false
isDownloading.value = true
assert.equal(printable.canDownload.value, false)
isDownloading.value = false
selectedCodes.value = []
assert.equal(printable.canDownload.value, false)
assert.ok(!templateSource.includes('window.print('))
console.log('Lotes únicos y selección lista para descargar: OK')
