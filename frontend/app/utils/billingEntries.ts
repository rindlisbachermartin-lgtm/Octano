import type { Budget, Invoice, Order } from '~/types'

export type BillingSection = 'para-cobrar' | 'cobradas' | 'sin-presupuesto'
export interface BillingEntry {
  key: string
  section: BillingSection
  order: Order | null
  invoice: Invoice | null
  budget: Budget | null
}

export function billingEntryBudget(order: Order | null | undefined, budgets: Budget[], orderId?: number | null): Budget | null {
  return budgets.find((budget) => (order?.budgetId != null && budget.id === order.budgetId)
    || (budget.orderId != null && budget.orderId === (order?.id ?? orderId))) || null
}

export function billingDateMatches(date: string | undefined, period: 'month' | 'week' | 'day', value: string): boolean {
  if (!value) return true
  if (!date) return false
  return billingPeriodValue(date, period) === value
}

export function billingPeriodValue(date: string, period: 'month' | 'week' | 'day'): string {
  if (period === 'day') return date.slice(0, 10)
  if (period === 'month') return date.slice(0, 7)
  const day = new Date(`${date.slice(0, 10)}T00:00:00Z`)
  if (Number.isNaN(day.getTime())) return ''
  day.setUTCDate(day.getUTCDate() + 4 - (day.getUTCDay() || 7))
  const year = day.getUTCFullYear()
  const start = new Date(Date.UTC(year, 0, 1))
  const week = Math.ceil(((day.getTime() - start.getTime()) / 86400000 + 1) / 7)
  return `${year}-W${String(week).padStart(2, '0')}`
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
      budget: billingEntryBudget(orders.find((order) => order.id === invoice.orderId), budgets, invoice.orderId),
    }))

  for (const order of orders) {
    if (order.status !== 'Finalizado' || invoices.some((invoice) => invoice.orderId === order.id)) continue
    const budget = billingEntryBudget(order, budgets)
    entries.push({
      key: `order-${order.id}`, section: 'sin-presupuesto',
      order, invoice: null, budget,
    })
  }
  return entries
}
