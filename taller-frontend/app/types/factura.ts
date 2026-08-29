export type TipoComprobanteFiscal = 'Factura A' | 'Factura B' | 'Factura C' | 'Nota de Credito A' | 'Nota de Credito B' | 'Nota de Credito C'

export interface FacturaFiscal {
  id: number
  puntoDeVenta: number
  numeroComprobante: number
  tipoComprobante: TipoComprobanteFiscal
  clienteId: number
  clienteNombre: string
  clienteCuit: string
  clienteCondicionFiscal: string
  ordenTrabajoId?: number
  fechaEmision: string
  importeNetoGravado: number
  importeIva: number
  importeTotal: number
  caeNumero: string
  caeVencimiento: string
  codigoQrFiscalUrl?: string
  estaAnulada: boolean
}
