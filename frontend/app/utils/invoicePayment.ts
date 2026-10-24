import type { Invoice } from '~/types'

export function recordInvoicePayment(invoice: Invoice, method: string, date: string, note = ''): boolean {
  if (invoice.status === 'Cobrada' || !Number.isFinite(invoice.total) || invoice.total <= 0 || !method.trim()) return false
  if (!invoice.isFiscal && !invoice.cae && invoice.status === 'Para armar') invoice.type = 'X'
  invoice.status = 'Cobrada'
  invoice.paymentMethod = method
  invoice.paymentDate = date
  invoice.paymentNote = note.trim()
  return true
}
