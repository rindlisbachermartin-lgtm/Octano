import type { VatCondition } from '../types'

export const VAT_CONDITIONS: VatCondition[] = [
  'Consumidor Final', 'IVA Responsable Inscripto', 'Responsable Monotributo',
  'IVA Sujeto Exento', 'IVA No Alcanzado', 'Monotributista Social',
  'Monotributo Trabajador Independiente Promovido', 'Sujeto No Categorizado',
]

export function requiresCuit(condition: string) {
  return ['IVA Responsable Inscripto', 'Responsable Monotributo', 'Monotributista Social', 'Monotributo Trabajador Independiente Promovido', 'IVA Sujeto Exento'].includes(condition)
}
