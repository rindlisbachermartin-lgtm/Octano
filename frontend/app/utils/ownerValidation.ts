export function isValidCuit(value: string) {
  const digits = value.replace(/\D/g, '')
  if (!/^\d{11}$/.test(digits)) return false
  const weights = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2]
  const remainder = 11 - weights.reduce((sum, weight, index) => sum + Number(digits[index]) * weight, 0) % 11
  const check = remainder === 11 ? 0 : remainder === 10 ? 9 : remainder
  return check === Number(digits[10])
}

export function ownerInitials(name: string) {
  return name.trim().split(/\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase()
}
