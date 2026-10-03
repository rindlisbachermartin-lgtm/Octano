import type { InvoiceItem, IssuerVatCondition, VatCondition } from '../types'

export const VAT_CONDITIONS: VatCondition[] = [
  'Consumidor Final', 'IVA Responsable Inscripto', 'Responsable Monotributo',
  'IVA Sujeto Exento', 'IVA No Alcanzado', 'Monotributista Social',
  'Monotributo Trabajador Independiente Promovido', 'Sujeto No Categorizado',
]
export const ISSUER_VAT_CONDITIONS: IssuerVatCondition[] = [
  'IVA Responsable Inscripto', 'Responsable Monotributo', 'IVA Sujeto Exento',
]
export const MONOTRIBUTO_INVOICE_LEGEND = 'El crédito fiscal discriminado en el presente comprobante, solo podrá ser computado a efectos del Régimen de Sostenimiento e Inclusión Fiscal para Pequeños Contribuyentes de la Ley Nº 27.618'

export function requiresCuit(condition: string) {
  return ['IVA Responsable Inscripto', 'Responsable Monotributo', 'Monotributista Social', 'Monotributo Trabajador Independiente Promovido', 'IVA Sujeto Exento'].includes(condition)
}

export function invoiceTypeForVat(issuer: IssuerVatCondition, receiver: string = 'Consumidor Final'): 'A' | 'B' | 'C' {
  if (issuer !== 'IVA Responsable Inscripto') return 'C'
  return ['IVA Responsable Inscripto', 'Responsable Monotributo', 'Monotributista Social', 'Monotributo Trabajador Independiente Promovido'].includes(receiver) ? 'A' : 'B'
}

export function invoiceTaxAmounts(type: string, total: number) {
  const net = type === 'C' ? total : Math.round(total / 1.21 * 100) / 100
  return { net, vat: type === 'C' ? 0 : Math.round((total - net) * 100) / 100 }
}

export function invoiceTaxLegend(type: string, receiver: string) {
  return type === 'A' && receiver !== 'IVA Responsable Inscripto' ? MONOTRIBUTO_INVOICE_LEGEND : ''
}

// Preserve the agreed total when switching the issuer's tax regime.
export function fiscalItems(items: InvoiceItem[], net: number): InvoiceItem[] {
  const subtotal = items.reduce((sum, item) => sum + item.total, 0)
  if (subtotal <= 0) return items
  let assigned = 0
  return items.map((item, index) => {
    const total = index === items.length - 1 ? Math.round((net - assigned) * 100) / 100 : Math.round(item.total / subtotal * net * 100) / 100
    assigned += total
    return { ...item, total, unitPrice: total / item.quantity }
  })
}
