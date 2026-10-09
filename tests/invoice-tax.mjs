import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { stripTypeScriptTypes } from 'node:module'
import { ref, computed, watch } from 'vue'
import * as tax from '../app/utils/invoiceTax.ts'
import { billingEntryBudget } from '../app/utils/billingEntries.ts'
import { orderBillingParts } from '../app/utils/orderWorkflow.ts'

for (const issuer of tax.ISSUER_VAT_CONDITIONS) {
  for (const receiver of tax.VAT_CONDITIONS) {
    const expected = issuer !== 'IVA Responsable Inscripto' ? 'C'
      : ['IVA Responsable Inscripto', 'Responsable Monotributo', 'Monotributista Social', 'Monotributo Trabajador Independiente Promovido'].includes(receiver) ? 'A' : 'B'
    assert.equal(tax.invoiceTypeForVat(issuer, receiver), expected)
  }
}
assert.deepEqual(tax.invoiceTaxAmounts('C', 12100), { net: 12100, vat: 0 })
assert.deepEqual(tax.invoiceTaxAmounts('A', 12100), { net: 10000, vat: 2100 })
assert.equal(tax.invoiceTaxLegend('A', 'Responsable Monotributo'), tax.MONOTRIBUTO_INVOICE_LEGEND)
assert.equal(tax.invoiceTaxLegend('A', 'IVA Responsable Inscripto'), '')
const normalized = tax.fiscalItems([
  { description: 'Mano de obra', quantity: 1, unitPrice: 5000, total: 5000 },
  { description: 'Repuesto', quantity: 2, unitPrice: 2500, total: 5000 },
], 12100)
assert.equal(normalized.reduce((sum, item) => sum + item.total, 0), 12100)

// Exercise the actual emission handler, including validation and the stored snapshot.
const source = readFileSync('app/components/facturacion/ModalCobroYFactura.vue', 'utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const script = stripTypeScriptTypes(source.replace(/import[\s\S]*?from ['"][^'"]+['"]\s*/g, ''))
async function emitInvoice(issuer, receiver, doc, fiscalStatus = null) {
  const draft = { id: 127, orderId: 1, vehicle: 1, description: 'Service', type: 'B', status: 'Para armar', total: 12100, items: [{description: 'Service', quantity: 1, unitPrice: 10000, total: 10000}] }
  const db = ref({ issuerVatCondition: issuer, invoices: [draft], orders: [{id: 1}], quotes: [] })
  const owner = { name: 'Cliente fiscal', doc, vatCondition: receiver }
  let completed = false
  const args = { ref, computed, watch, billingEntryBudget, orderBillingParts, useWorkshopDay: () => ({ today: ref('2026-10-08') }), defineProps: () => ({open: true, order: {id: 1, vehicle: 1, service: 'Service', parts: []}, invoice: draft, initialTab: 'arca'}),
    defineEmits: () => () => {completed = true}, useDatabase: () => ({db, vehicle: () => ({client: 1}), client: () => owner}),
    useHelpers: () => ({money: String}), useWorkshopToast: () => ({notify: () => {}}), useModalEscape: () => {},
    useOwnerAccount: () => ({owner: ref(fiscalStatus ? {arcaStatus: fiscalStatus, pointOfSale: 7} : null), fiscalIssuer: ref(undefined)}),
    setTimeout: callback => callback(), ...tax }
  const handler = new Function(...Object.keys(args), `${script}; return {submitArcaInvoice, fiscalError, invoiceType};`)(...Object.values(args))
  await handler.submitArcaInvoice()
  return { draft: db.value.invoices[0], completed, error: handler.fiscalError.value, type: handler.invoiceType.value }
}
const mono = await emitInvoice('IVA Responsable Inscripto', 'Responsable Monotributo', '20-12345678-6')
assert.equal(mono.draft.type, 'A')
assert.equal(mono.draft.clientDoc, '20-12345678-6')
assert.equal(mono.draft.clientVatCondition, 'Responsable Monotributo')
assert.equal(mono.draft.status, 'Emitida')
const exempt = await emitInvoice('IVA Sujeto Exento', 'Consumidor Final', '32.456.789')
assert.equal(exempt.draft.type, 'C')
assert.equal(exempt.draft.vatAmount, 0)
assert.equal(exempt.draft.netAmount, 12100)
assert.equal(exempt.draft.items[0].total, 12100)
assert.equal(exempt.draft.issuerVatCondition, 'IVA Sujeto Exento')
const invalid = await emitInvoice('IVA Responsable Inscripto', 'IVA Responsable Inscripto', '32.456.789')
assert.equal(invalid.completed, false)
assert.equal(invalid.draft.status, 'Para armar')
assert.ok(invalid.error.includes('CUIT'))
const pending = await emitInvoice('IVA Responsable Inscripto', 'Consumidor Final', '32.456.789', 'pending')
assert.equal(pending.completed, false)
assert.equal(pending.draft.status, 'Para armar')
assert.ok(pending.error.includes('verificación'))
const verified = await emitInvoice('IVA Responsable Inscripto', 'Consumidor Final', '32.456.789', 'demo-verified')
assert.equal(verified.draft.ptoVta, 7)
console.log('24 combinaciones fiscales, importes, leyendas, emisión A/C y validación de CUIT: OK')
