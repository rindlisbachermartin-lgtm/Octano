export interface AliquotaIva {
  id: number
  porcentaje: number
}

export const etiquetaIva = (porcentaje: number) =>
  `${Number(porcentaje).toLocaleString('es-AR', { maximumFractionDigits: 2 })}%`

// Conserva un valor histórico que ya no esté en el catálogo sin permitir cargar otro libre.
export function valoresIva(aliquotas: AliquotaIva[], actual: number | string): number[] {
  const valor = Number(actual)
  const opciones = aliquotas.map(item => Number(item.porcentaje))
  return valor && !opciones.includes(valor) ? [valor, ...opciones] : opciones
}
