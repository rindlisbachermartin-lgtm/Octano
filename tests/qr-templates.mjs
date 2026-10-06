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
const first = generate(12)
const second = generate(24)
assert.equal(first.length, 12)
assert.equal(second.length, 24)
assert.equal(new Set(db.value.qrCodes.map(q => q.code)).size, 37)
assert.ok([...first, ...second].every(q => q.status === 'disponible' && q.vehicleId === null))

const scannerSource = readFileSync('app/pages/qr/[code].vue', 'utf8')
const normalize = scannerSource.match(/const normalizePlate = .*\n/)[0]
const candidates = scannerSource.match(/const candidateVehicles = computed\([\s\S]*?\n}\)/)[0]
const debouncedSearch = ref('ac-284-fn')
const find = new Function('db', 'computed', 'debouncedSearch', `${stripTypeScriptTypes(normalize + candidates)}; return candidateVehicles`)(db, computed, debouncedSearch)
assert.deepEqual(find.value.map(v => v.id), [1])
debouncedSearch.value = 'abc123'
assert.deepEqual(find.value.map(v => v.id), [2])
debouncedSearch.value = 'ZZZ999'
assert.deepEqual(find.value, [])
debouncedSearch.value = ' - '
assert.deepEqual(find.value, [])

const templateSource = readFileSync('app/pages/codigos-qr.vue', 'utf8')
const selectedExpression = templateSource.match(/const selectedQrs = .*\n/)[0]
const readyExpression = templateSource.match(/const canDownload = .*\n/)[0]
const displayedQrs = ref(first)
const selectedCodes = ref([first[0].code, second[0].code])
const isGenerating = ref(false)
const isDownloading = ref(false)
const printable = new Function('computed', 'displayedQrs', 'selectedCodes', 'isGenerating', 'isDownloading', `${stripTypeScriptTypes(selectedExpression + readyExpression)}; return {selectedQrs, canDownload}`)(computed, displayedQrs, selectedCodes, isGenerating, isDownloading)
assert.equal(printable.selectedQrs.value.length, 1)
assert.equal(printable.canDownload.value, true)
isGenerating.value = true
assert.equal(printable.canDownload.value, false)
isGenerating.value = false
isDownloading.value = true
assert.equal(printable.canDownload.value, false)
isDownloading.value = false
selectedCodes.value = []
assert.equal(printable.canDownload.value, false)
assert.ok(!templateSource.includes('window.print('))
console.log('Lotes únicos, búsqueda por patente y selección lista para descargar: OK')
