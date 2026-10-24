import type { Budget } from '~/types'

export function budgetAmounts(budget: Budget) {
  const materials = budget.items?.length
    ? budget.items.reduce((sum, item) => sum + Number(item.total ?? item.quantity * item.unitPrice), 0)
    : Number(budget.materials || 0)
  const subtotal = budget.subtotal ?? Number(budget.labor || 0) + materials
  const tax = budget.tax ?? Math.round(subtotal * 0.21)
  const total = budget.total ?? subtotal + tax
  return { subtotal, tax, total }
}
