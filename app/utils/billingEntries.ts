import type { Budget, Invoice, Order } from '~/types'

export type BillingSection = 'para-cobrar' | 'cobradas' | 'sin-presupuesto'
export interface BillingEntry {
  key: string
  section: BillingSection
  order: Order | null
  invoice: Invoice | null
  budget: Budget | null
}

export function billingEntries(orders: Order[], invoices: Invoice[], budgets: Budget[]): BillingEntry[] {
  const entries: BillingEntry[] = invoices
    .filter((invoice) => ['Para armar', 'Emitida', 'Cobrada'].includes(invoice.status))
    .map((invoice): BillingEntry => ({
      key: `invoice-${invoice.id}`,
      section: invoice.status === 'Cobrada' ? 'cobradas'
        : invoice.isFiscal || invoice.cae ? 'para-cobrar' : 'sin-presupuesto',
      invoice,
      order: orders.find((order) => order.id === invoice.orderId) || null,
      budget: budgets.find((budget) => budget.orderId != null && budget.orderId === invoice.orderId) || null,
    }))

  for (const order of orders) {
    if (order.status !== 'Finalizado' || invoices.some((invoice) => invoice.orderId === order.id)) continue
    const budget = budgets.find((budget) => budget.orderId === order.id) || null
    entries.push({
      key: `order-${order.id}`, section: 'sin-presupuesto',
      order, invoice: null, budget,
    })
  }
  return entries
}
