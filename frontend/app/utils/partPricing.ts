export function partSalePrice(cost: number, margin: number): number {
  if (![cost, margin].every(value => Number.isFinite(value) && value >= 0)) return NaN
  return Math.round((cost + cost * margin / 100 + Number.EPSILON) * 100) / 100
}

export function partMargin(cost: number, price: number): number {
  return cost > 0 ? (price / cost - 1) * 100 : 0
}
