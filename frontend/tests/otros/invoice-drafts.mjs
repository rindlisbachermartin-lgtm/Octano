import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { stripTypeScriptTypes } from 'node:module'

function vueFunction(path, name) {
  const script = readFileSync(path, 'utf8').match(/<script setup[^>]*>([\s\S]*?)<\/script>/)[1]
  return stripTypeScriptTypes(script).match(new RegExp(`function ${name}\\([^]*?\\n\\}`))[0]
}

const events = []
const budgetViewerOpen = { value: false }
const selectedBudgetForViewer = { value: null }
const openDetail = new Function('budgetViewerOpen', 'selectedBudgetForViewer', 'openInvoiceForm', 'openArcaViewer', `${vueFunction('app/pages/facturacion/index.vue', 'openEntryDetail')}; return openEntryDetail`)(
  budgetViewerOpen, selectedBudgetForViewer,
  (...args) => events.push(['editor', ...args]),
  (invoice) => events.push(['viewer', invoice]),
)
const draft = { id: 126, vehicle: 1, orderId: 42, description: 'Service', status: 'Para armar', date: '2026-10-01', isFiscal: false, total: 100 }
openDetail({ section: 'sin-presupuesto', invoice: draft, order: null, budget: null })
assert.deepEqual(events.pop(), ['editor', null, 'arca', draft])
const budget = { id: 5 }
openDetail({ section: 'sin-presupuesto', invoice: draft, order: null, budget })
assert.deepEqual(events.pop(), ['editor', null, 'arca', draft])
assert.equal(budgetViewerOpen.value, false)
openDetail({ section: 'sin-presupuesto', invoice: null, order: { id: 42 }, budget })
assert.equal(budgetViewerOpen.value, true)
assert.equal(selectedBudgetForViewer.value, budget)
assert.equal(events.length, 0, 'Una tarjeta sin emitir nunca abre el visor')
openDetail({ section: 'para-cobrar', invoice: { ...draft, isFiscal: true }, order: null, budget: null })
assert.equal(events.pop()[0], 'viewer')

function editor(invoice = draft) {
  const db = { value: { issuerVatCondition: 'IVA Responsable Inscripto', invoices: [{ ...invoice }] } }
  const props = { invoice, order: { id: 42 } }
  const form = { value: { vehicle: 1, description: 'Service corregido', labor: 10, items: [{ key: 1, partId: 'custom', customName: 'Filtro', searchQuery: 'Personalizado', quantity: 2, unitPrice: 25 }] } }
  const formError = { value: '' }
  const context = { props, db, form, formError, totalAmount: { value: 72.6 }, netAmount: { value: 60 }, vatAmount: { value: 12.6 }, partsAmount: { value: 50 }, invoiceType: { value: 'B' }, selectedVehicle: { value: { client: 1 } }, client: () => ({ vatCondition: 'Consumidor Final' }), emit: (...args) => events.push(args) }
  const submit = new Function(...Object.keys(context), `${vueFunction('app/components/facturacion/ModalFactura.vue', 'submit')}; return submit`)(...Object.values(context))
  return { submit, db, formError }
}
const edited = editor()
edited.submit()
assert.equal(edited.formError.value, '')
assert.equal(edited.db.value.invoices.length, 1, 'Editar conserva el borrador sin duplicarlo')
assert.equal(edited.db.value.invoices[0].id, 126)
assert.equal(edited.db.value.invoices[0].orderId, 42)
assert.equal(edited.db.value.invoices[0].date, '2026-10-01')
assert.equal(edited.db.value.invoices[0].items[1].total, 50)
assert.equal(edited.db.value.invoices[0].status, 'Para armar')

for (const invoice of [{ ...draft, isFiscal: true }, { ...draft, cae: '123' }, { ...draft, status: 'Cobrada' }]) {
  const protectedEditor = editor(invoice)
  const before = JSON.stringify(protectedEditor.db.value)
  protectedEditor.submit()
  assert.ok(protectedEditor.formError.value)
  assert.equal(JSON.stringify(protectedEditor.db.value), before)
}

const entriesSource = stripTypeScriptTypes(readFileSync('app/utils/billingEntries.ts', 'utf8')).replace(/export /g, '')
const billingEntries = new Function(`${entriesSource}; return billingEntries`)()
assert.equal(billingEntries([], [draft], [])[0].section, 'sin-presupuesto')
assert.equal(billingEntries([], [{ ...draft, cae: '123' }], [])[0].section, 'para-cobrar')
console.log('Borradores: editor correcto, presupuesto, actualización sin duplicados y protección de emitidas/cobradas: OK')
