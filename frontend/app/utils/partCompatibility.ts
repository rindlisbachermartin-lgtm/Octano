import type { Part, PartCompatibility, Vehicle } from '../types/index'
import { catalogKey } from './vehicleCatalog.ts'

export function matchesPartCompatibility(entry: PartCompatibility, brand: string, model = '', version = '') {
  return !!entry.brand && !!entry.model &&
    catalogKey(entry.brand) === catalogKey(brand) &&
    (!model || catalogKey(entry.model) === catalogKey(model)) &&
    (!version || !entry.version || catalogKey(entry.version) === catalogKey(version))
}

export function isPartCompatible(part: Pick<Part, 'compatible'>, vehicle?: Pick<Vehicle, 'brand' | 'model' | 'version'>) {
  return !part.compatible.length || !!vehicle?.brand?.trim() && !!vehicle?.model?.trim() &&
    part.compatible.some(entry => (!entry.version || !!vehicle.version?.trim()) && matchesPartCompatibility(entry, vehicle.brand, vehicle.model, vehicle.version))
}

// Convert stored vehicle IDs once, retaining a blocked entry if the vehicle no longer exists.
export function migratePartCompatibility(entries: (number | PartCompatibility)[], vehicles: Vehicle[]): PartCompatibility[] {
  const result: PartCompatibility[] = []
  for (const entry of entries) {
    const source = typeof entry === 'number' ? vehicles.find(vehicle => vehicle.id === entry) : entry
    const pair: PartCompatibility = { brand: source?.brand?.trim().replace(/\s+/g, ' ') || '', model: source?.model?.trim().replace(/\s+/g, ' ') || '' }
    if (source?.version?.trim()) pair.version = source.version.trim().replace(/\s+/g, ' ')
    if (!result.some(other => catalogKey(other.brand) === catalogKey(pair.brand) && catalogKey(other.model) === catalogKey(pair.model) && catalogKey(other.version || '') === catalogKey(pair.version || ''))) result.push(pair)
  }
  return result
}
