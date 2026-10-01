import type { Budget, Invoice } from '~/types'

export function isBudgetArchived(budget: Budget, invoices: Invoice[]): boolean {
  return budget.orderId != null && invoices.some((invoice) =>
    invoice.orderId === budget.orderId && invoice.status === 'Cobrado'
  )
}
