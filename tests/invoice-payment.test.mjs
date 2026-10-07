import assert from 'node:assert/strict'
import { test } from 'node:test'
import { recordInvoicePayment } from '../app/utils/invoicePayment.ts'
import { billingDateMatches, billingEntries } from '../app/utils/billingEntries.ts'

test('manual payment preserves issued fiscal invoice and cannot be recorded twice', () => {
  const invoice = { id: 12, type: 'A', status: 'Emitida', date: '2026-09-30', total: 121,
    isFiscal: true, cae: '123456', ptoVta: 3, nroCmp: 42, items: [{ description: 'Trabajo', total: 100 }] }
  const original = structuredClone(invoice)
  assert.equal(recordInvoicePayment(invoice, 'Transferencia bancaria', '2026-10-07', ' Recibido '), true)
  assert.deepEqual(invoice, { ...original, status: 'Cobrada', paymentMethod: 'Transferencia bancaria', paymentDate: '2026-10-07', paymentNote: 'Recibido' })
  const paid = structuredClone(invoice)
  assert.equal(recordInvoicePayment(invoice, 'Efectivo', '2026-10-08'), false)
  assert.deepEqual(invoice, paid)
})

test('draft payment creates receipt X and rejects zero amounts', () => {
  const invoice = { status: 'Para armar', type: 'B', total: 100 }
  assert.equal(recordInvoicePayment(invoice, 'Efectivo', '2026-10-07'), true)
  assert.equal(invoice.type, 'X')
  assert.equal(recordInvoicePayment({ status: 'Emitida', total: 0 }, 'Efectivo', '2026-10-07'), false)
})

test('billing filters match day, month and ISO week across year boundary', () => {
  assert.equal(billingDateMatches('2026-10-07', 'month', '2026-10'), true)
  assert.equal(billingDateMatches('2026-09-07', 'month', '2026-10'), false)
  assert.equal(billingDateMatches('2026-10-07', 'day', '2026-10-07'), true)
  assert.equal(billingDateMatches('2026-10-08', 'day', '2026-10-07'), false)
  assert.equal(billingDateMatches('2026-10-07', 'week', '2026-W41'), true)
  assert.equal(billingDateMatches('2027-01-01', 'week', '2026-W53'), true)
  assert.equal(billingDateMatches(undefined, 'day', ''), true)
})

test('unissued orders and drafts find budget by order budgetId or budget orderId', () => {
  const order = { id: 10, budgetId: 4, status: 'Finalizado' }
  const budget = { id: 4 }
  assert.equal(billingEntries([order], [], [budget])[0].budget, budget)
  assert.equal(billingEntries([order], [{ id: 2, orderId: 10, status: 'Para armar' }], [budget])[0].budget, budget)
  assert.equal(billingEntries([order], [], [{ id: 5, orderId: 10 }])[0].budget.id, 5)
})
