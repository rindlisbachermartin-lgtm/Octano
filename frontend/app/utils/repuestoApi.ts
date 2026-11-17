import type { Part } from '~/types'

export interface RepuestoApi {
  id: number
  codigoOem: string
  descripcion: string
  marca: string
  precioCosto: number
  margen: number
  precioPublico: number
  stock: number
  stockMinimo: number
  activo: boolean
}

export interface PaginaRepuestos<T> {
  contenido: T[]
  pagina: number
  totalPaginas: number
  totalElementos: number
}

export function adaptarRepuesto(r: RepuestoApi): Part {
  return {
    id: r.id, name: r.descripcion, brand: r.marca, oem: r.codigoOem,
    cost: r.precioCosto, margin: r.margen, price: r.precioPublico,
    stock: r.stock, min: r.stockMinimo, archived: !r.activo, compatible: [],
  }
}
