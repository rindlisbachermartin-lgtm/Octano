export type EstadoQr = 'DISPONIBLE' | 'ASIGNADO' | 'INUTILIZADO'
export interface CodigoQrApi {
  codigo: string
  estado: EstadoQr
  vehiculoId: number | null
  patente: string | null
  marca: string | null
  modelo: string | null
  version: string | null
  creadoEn: string
  asignadoEn: string | null
  impresoEn: string | null
  urlPublica: string
}
export interface PaginaQr<T> { contenido: T[]; totalElementos: number; totalPaginas: number }
export interface MantenimientoQr {
  fecha: string
  kilometraje: number | null
  aceite: string | null
  filtros: string[]
  repuestos: string[]
}
export interface FichaQrApi {
  codigo: string
  estado: EstadoQr
  tallerNombre: string
  vehiculo: { patente: string; marca: string; modelo: string; version: string | null; anio: number; kilometraje: number | null } | null
  ultimoService: MantenimientoQr | null
  ultimaDistribucion: MantenimientoQr | null
}
export const estadosQr = { DISPONIBLE: 'Disponible', ASIGNADO: 'Asignado', INUTILIZADO: 'Inutilizado' }
export function normalizarCodigoQr(codigo: string) {
  const valor = codigo.trim().toUpperCase()
  if (!/^[A-Z0-9-]{3,40}$/.test(valor)) throw new Error('Usá entre 3 y 40 caracteres: letras, números o guiones.')
  return valor
}
export function seleccionQr(codigos: string[]) {
  const normalizados = codigos.map(normalizarCodigoQr)
  if (normalizados.length !== 16 || new Set(normalizados).size !== 16) throw new Error('Seleccioná exactamente 16 códigos distintos sin imprimir.')
  return { codigos: normalizados }
}
