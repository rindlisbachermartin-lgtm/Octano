import type { VehicleCatalogEntry } from '~/types'

export function catalogKey(value: string) {
  return value.trim().replace(/\s+/g, ' ').toLocaleLowerCase('es-AR')
}

export function vehicleCatalogOptions(
  entries: VehicleCatalogEntry[],
  field: 'brand' | 'model' | 'engine',
  brand = '',
  model = ''
) {
  const options = new Map<string, string>()
  for (const entry of entries) {
    if (field !== 'brand' && catalogKey(entry.brand) !== catalogKey(brand)) continue
    if (field === 'engine' && catalogKey(entry.model) !== catalogKey(model)) continue
    const value = entry[field].trim().replace(/\s+/g, ' ')
    if (value && !options.has(catalogKey(value))) options.set(catalogKey(value), value)
  }
  return [...options.values()].sort((a, b) => a.localeCompare(b, 'es-AR', { numeric: true }))
}
