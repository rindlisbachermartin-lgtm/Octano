import type { VehicleCatalogEntry } from '~/types'

// Example catalog for exploring the prototype's dependent selectors.
export const DEMO_VEHICLE_CATALOG: VehicleCatalogEntry[] = [
  { brand: 'Volkswagen', model: 'Golf', engine: '1.4 TSI', versions: ['Comfortline', 'Highline', 'GTI'] },
  { brand: 'Toyota', model: 'Hilux', engine: '2.8 TDI', versions: ['SR', 'SRV', 'SRX'] },
  { brand: 'Peugeot', model: '208', engine: '1.6 VTi', versions: ['Active', 'Allure', 'Feline'] },
  { brand: 'Ford', model: 'Focus', engine: '2.0', versions: ['S', 'SE', 'Titanium'] },
  { brand: 'Renault', model: 'Sandero', engine: '1.6', versions: ['Life', 'Zen', 'Intens'] },
  { brand: 'Volkswagen', model: 'Amarok 2,0 140CV', engine: '2.0 TDI 140CV', versions: ['Startline', 'Trendline', 'Highline'] },
  { brand: 'Fiat', model: 'Cronos', engine: '1.3 Firefly', versions: ['Like', 'Drive', 'Precision'] },
  { brand: 'Chevrolet', model: 'Onix', engine: '1.0 Turbo', versions: ['LT', 'LTZ', 'Premier'] },
].flatMap(({ versions, ...entry }) => versions.map(version => ({ ...entry, version })))

export function catalogKey(value: string) {
  return value.trim().replace(/\s+/g, ' ').toLocaleLowerCase('es-AR')
}

export function vehicleCatalogOptions(
  entries: VehicleCatalogEntry[],
  field: 'brand' | 'model' | 'version' | 'engine',
  brand = '',
  model = ''
) {
  const options = new Map<string, string>()
  for (const entry of entries) {
    if (field !== 'brand' && catalogKey(entry.brand) !== catalogKey(brand)) continue
    if ((field === 'engine' || field === 'version') && catalogKey(entry.model) !== catalogKey(model)) continue
    const value = (entry[field] || '').trim().replace(/\s+/g, ' ')
    if (value && !options.has(catalogKey(value))) options.set(catalogKey(value), value)
  }
  return [...options.values()].sort((a, b) => a.localeCompare(b, 'es-AR', { numeric: true }))
}
