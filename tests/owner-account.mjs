import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { stripTypeScriptTypes } from 'node:module'
import { webcrypto } from 'node:crypto'
import { ref, computed } from 'vue'
import { isValidCuit } from '../app/utils/ownerValidation.ts'

function storage() {
  const values = new Map()
  return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key), values }
}
const localStorage = storage()
const sessionStorage = storage()
let states = new Map()
let navigation = ''
const source = stripTypeScriptTypes(readFileSync('app/composables/useOwnerAccount.ts', 'utf8')).replace(/import\.meta\.client/g, 'true').replace(/export /g, '')
const args = { crypto: webcrypto, localStorage, sessionStorage, computed,
  useState: (key, initial) => { if (!states.has(key)) states.set(key, ref(initial())); return states.get(key) },
  navigateTo: path => { navigation = path },
}
const useOwnerAccount = new Function(...Object.keys(args), `${source}; return useOwnerAccount;`)(...Object.values(args))
assert.equal(isValidCuit('20-12345678-6'), true)
assert.equal(isValidCuit('20-12345678-7'), false)
const data = { name: 'Dueño de prueba', email: 'OWNER@example.com', phone: '1123456789', workshop: 'Taller de prueba', legalName: 'Taller de prueba SRL', cuit: '20-12345678-6', address: 'Calle 123', city: 'Ciudad', province: 'Buenos Aires', vat: 'IVA Responsable Inscripto', pointOfSale: 7 }
const auth = useOwnerAccount()
await auth.register(data, 'contraseña-de-prueba')
assert.equal(auth.owner.value.email, 'owner@example.com')
assert.equal(auth.owner.value.arcaStatus, 'pending')
assert.equal(auth.fiscalIssuer.value.cuit, data.cuit)
assert.ok(!localStorage.getItem('octano-owner-demo-v1').includes('contraseña-de-prueba'))
await assert.rejects(() => auth.register(data, 'otra-clave'), /registrada/)
auth.logout()
assert.equal(auth.session.value, null)
assert.equal(navigation, '/login')
await assert.rejects(() => auth.login(data.email, 'incorrecta', false), /no coinciden/)
assert.equal(auth.session.value, null)
await auth.login('owner@example.com', 'contraseña-de-prueba', true)
auth.updateFiscal({arcaStatus: 'demo-verified', pointOfSale: 9, arcaModel: 'certificates', activityStartDate: '2020-03-01'})
assert.equal(auth.owner.value.arcaStatus, 'demo-verified')
assert.equal(auth.fiscalIssuer.value.activityStartDate, '2020-03-01')
states = new Map()
const reloaded = useOwnerAccount()
reloaded.initialize()
assert.equal(reloaded.owner.value.pointOfSale, 9)
assert.equal(reloaded.fiscalProfile.value.arcaModel, 'certificates')
assert.equal(reloaded.fiscalProfile.value.activityStartDate, '2020-03-01')
assert.equal(reloaded.session.value, 'owner@example.com')
reloaded.logout()
assert.equal(localStorage.getItem('octano-owner-session-demo-v1'), null)
reloaded.enterDemo()
assert.equal(reloaded.owner.value, null)
assert.equal(reloaded.session.value, 'demo')
reloaded.updateFiscal({ pointOfSale: 12, vat: 'Responsable Monotributo', arcaModel: 'delegation', activityStartDate: '2021-04-01', arcaStatus: 'demo-verified' })
assert.equal(reloaded.fiscalProfile.value.pointOfSale, 12)
assert.equal(reloaded.account.value.pointOfSale, 9, 'La configuración de demostración no modifica la cuenta del dueño')
states = new Map()
const demoReloaded = useOwnerAccount()
demoReloaded.initialize()
assert.equal(demoReloaded.fiscalProfile.value.pointOfSale, 12)
assert.equal(demoReloaded.fiscalProfile.value.activityStartDate, '2021-04-01')
console.log('Registro, hash sin contraseña almacenada, login, sesión, salida, CUIT y estado fiscal: OK')
