// ponytail: el catálogo incluye combustible en el nombre de versión; usar un campo propio cuando exista.
const fuelPattern = /\b(di[eé]sel|gasoil|nafta|gasolina|gnc|glp|el[eé]ctrico|h[ií]brido(?:\s+enchufable)?)\b/gi

export function vehicleVersion(version?: string | null) {
  return (version || '').replace(fuelPattern, '').replace(/\s*[-/|·]\s*$/, '').replace(/\s{2,}/g, ' ').trim()
}

export function vehicleFuel(version?: string | null) {
  return [...new Set((version?.match(fuelPattern) || []).map(f => f.toLocaleLowerCase('es-AR')))]
    .map(f => /^(gnc|glp)$/.test(f) ? f.toUpperCase() : f.charAt(0).toUpperCase() + f.slice(1)).join(' / ')
}

export function vehicleLabel(brand?: string | null, model?: string | null, version?: string | null) {
  return [brand, model, vehicleVersion(version)].filter(Boolean).join(' ')
}
