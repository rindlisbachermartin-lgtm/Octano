import type { Vehicle, Client } from '~/types'

export interface VehiculoApi {
  id: number
  clienteId: number
  clienteNombre: string
  marcaId: number
  modeloId: number
  versionId: number | null
  marca: string
  modelo: string
  version: string | null
  patente: string
  anio: number
  kilometraje: number | null
  activo: boolean
  codigoQr: string | null
}

export interface ClienteTitularApi {
  id: number
  nombre: string
  documento: string
  telefono: string
  email: string | null
  activo: boolean
}

export function adaptarVehiculo(v: VehiculoApi): Vehicle {
  return {
    id: v.id, client: v.clienteId, brand: v.marca, model: v.modelo,
    version: v.version ?? '', engine: '', plate: v.patente, year: v.anio,
    km: v.kilometraje ?? 0, color: '#bbc5b7', qrCode: v.codigoQr,
    registro: {
      marcaId: v.marcaId, modeloId: v.modeloId, versionId: v.versionId,
      titular: v.clienteNombre, activo: v.activo, kilometraje: v.kilometraje,
    },
  }
}

export function adaptarTitular(c: ClienteTitularApi): Client {
  return { id: c.id, name: c.nombre, doc: c.documento, phone: c.telefono, email: c.email ?? '', active: c.activo }
}

export function kilometrajeVisible(v: Vehicle) {
  return v.registro?.kilometraje === null ? 'Sin registrar' : `${v.km.toLocaleString('es-AR')} km`
}
