import { jsPDF } from 'jspdf'
import { autoTable } from 'jspdf-autotable'
import { fechaLabel, medioPagoLabel, numeroComprobante, vehiculoLabel, type ComprobanteApi } from './comprobanteApi'

const importe = (valor: number) => Number(valor || 0).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const domicilio = (parte: ComprobanteApi['emisor']) => [parte.calle, parte.altura].filter((dato) => dato != null && dato !== '').join(' ') || '—'

export function crearPdfComprobante(c: ComprobanteApi) {
  const pdf = new jsPDF({ unit: 'mm', format: 'a4', compress: true })
  const numero = c.numero ? `${String(c.puntoVenta).padStart(4, '0')}-${String(c.numero).padStart(8, '0')}` : numeroComprobante(c)
  const escribir = (texto: string | string[], x: number, y: number, tam = 9, negrita = false, align: 'left' | 'right' | 'center' = 'left') => {
    pdf.setFont('helvetica', negrita ? 'bold' : 'normal')
    pdf.setFontSize(tam)
    pdf.setTextColor(0)
    pdf.text(texto, x, y, { align })
  }
  const partir = (texto: string, ancho: number, tam = 8) => {
    pdf.setFontSize(tam)
    return pdf.splitTextToSize(texto, ancho) as string[]
  }

  function cabecera() {
    pdf.setDrawColor(0)
    pdf.setLineWidth(0.25)
    pdf.rect(3, 3, 204, 53)
    pdf.line(105, 3, 105, 56)
    escribir(partir(c.emisor.nombre, 82, 14), 54, 19, 14, true, 'center')
    escribir(['Servicios Mecánicos y Reparación Integral', domicilio(c.emisor), c.emisor.telefono || '—', c.emisor.condicionIva || '—'], 54, 32, 8, false, 'center')
    pdf.setFillColor(255, 255, 255)
    pdf.rect(97, 3, 16, 16, 'FD')
    escribir(c.tipo, 105, 13, 25, true, 'center')
    escribir('SIMULADO', 105, 17.5, 5, false, 'center')
    pdf.setFillColor(232, 232, 232)
    pdf.rect(97, 19, 16, 4, 'F')
    escribir(c.estado === 'BORRADOR' ? 'BORRADOR' : 'ORIGINAL', 105, 22, 7, false, 'center')
    escribir(c.estado === 'BORRADOR' ? 'BORRADOR' : 'FACTURA', 121, 12, 20, true)
    escribir(numero, 121, 21, 12, true)
    escribir(`Fecha: ${fechaLabel(c.fecha)}`, 121, 28, 9)
    escribir([`CUIT: ${c.emisor.documento || '—'}`, `Cond. IVA: ${c.emisor.condicionIva || '—'}`, `Orden: ${c.ordenId ? `#${c.ordenId}` : 'Sin orden'}`], 121, 43, 8)
    pdf.rect(3, 59, 204, 22)
    escribir(partir(`Nombre: ${c.receptor.nombre}`, 90), 8, 64, 8)
    escribir(`CUIT / DNI: ${c.receptor.documento || '—'}`, 109, 64, 8)
    escribir(partir(`Domicilio: ${domicilio(c.receptor)}`, 90), 8, 70, 8)
    escribir(`Teléfono: ${c.receptor.telefono || '—'}`, 109, 70, 8)
    escribir(partir(`Cond. IVA: ${c.receptor.condicionIva || '—'}`, 90), 8, 76, 8)
    escribir(partir(`Concepto: ${c.descripcion}`, 92).slice(0, 2), 109, 76, 8)
  }

  const filas = c.items.map((item) => [item.codigoOem || String(item.renglon).padStart(4, '0'), item.descripcion,
    Number(item.cantidad).toLocaleString('es-AR'), importe(item.precioUnitario), importe(item.subtotal)])
  if (Number(c.manoObra) > 0) filas.push(['—', 'Mano de obra', '1', importe(c.manoObra), importe(c.manoObra)])
  let finTabla = 87
  autoTable(pdf, {
    startY: 84, margin: { top: 84, bottom: 16, left: 3, right: 3 }, tableWidth: 204, theme: 'plain',
    head: [['Código', 'Descripción', 'Cantidad', 'P. unitario', 'Importe']], body: filas,
    styles: { font: 'helvetica', fontSize: 8, textColor: 0, cellPadding: 1.8, overflow: 'linebreak', lineWidth: 0 },
    headStyles: { fillColor: 232, textColor: 0, fontStyle: 'bold', lineColor: 0, lineWidth: 0.25 },
    columnStyles: { 0: { cellWidth: 22 }, 1: { cellWidth: 95 }, 2: { cellWidth: 23, halign: 'right' }, 3: { cellWidth: 32, halign: 'right' }, 4: { cellWidth: 32, halign: 'right' } },
    rowPageBreak: 'avoid', willDrawPage: () => cabecera(), didDrawPage: (datos) => { finTabla = datos.cursor?.y ?? 87 },
  })

  const pagos = c.cobros?.length ? c.cobros : c.cobro ? [c.cobro] : []
  const notas = [
    ...partir(`Vehículo: ${vehiculoLabel(c.vehiculo)} · Patente: ${c.vehiculo.patente}`, 196, 7),
    `Pagado: $ ${importe(c.pagado)} · Saldo pendiente: $ ${importe(c.saldo)}`,
    ...pagos.flatMap((pago) => partir(`${fechaLabel(pago.fecha)} · ${medioPagoLabel(pago.medioPago)} · $ ${importe(pago.importe)}${pago.observaciones ? ` · ${pago.observaciones}` : ''}`, 196, 7)),
  ]
  let y = Math.max(210, finTabla + 6)
  if (y + 29 + 16 + 23 > 289) {
    pdf.addPage()
    cabecera()
    y = 87
  }
  pdf.rect(3, y, 204, 29)
  escribir('Subtotal: $', 167, y + 8, 8, true, 'right')
  escribir(importe(c.subtotal), 202, y + 8, 8, false, 'right')
  escribir('Dto./Recargo: $', 167, y + 13, 8, true, 'right')
  escribir(importe(0), 202, y + 13, 8, false, 'right')
  escribir(`IVA ${Number(c.porcentajeIva).toLocaleString('es-AR')}%: $`, 167, y + 18, 8, true, 'right')
  escribir(importe(c.montoIva), 202, y + 18, 8, false, 'right')
  escribir('Total: $', 167, y + 23, 10, true, 'right')
  escribir(importe(c.total), 202, y + 23, 10, true, 'right')
  y += 32
  const pendientes = [...notas]
  do {
    const maximo = Math.max(1, Math.floor((289 - y - 27) / 3.5))
    const bloque = pendientes.splice(0, maximo)
    const alto = Math.max(16, bloque.length * 3.5 + 5)
    pdf.rect(3, y, 204, alto)
    escribir(bloque, 7, y + 4, 7)
    y += alto + 4
    if (pendientes.length) {
      pdf.addPage()
      cabecera()
      y = 87
    }
  } while (pendientes.length)
  escribir('CAE SIMULADO', 7, y + 5, 10, true)
  escribir(c.caeSimulado || 'Pendiente de emisión', 7, y + 12, 12, true)
  escribir('Sin validez fiscal. No autorizado por ARCA.', 203, y + 6, 8, false, 'right')
  escribir('Comprobante generado con Octano', 203, y + 16, 8, false, 'right')
  const paginas = pdf.getNumberOfPages()
  for (let pagina = 1; pagina <= paginas; pagina++) {
    pdf.setPage(pagina)
    if (c.estado === 'BORRADOR') {
      pdf.saveGraphicsState()
      pdf.setGState(pdf.GState({ opacity: 0.1 }))
      pdf.setFont('helvetica', 'bold')
      pdf.setFontSize(42)
      pdf.setTextColor(0)
      pdf.text('BORRADOR', 105, 150, { align: 'center', angle: 0 })
      pdf.restoreGraphicsState()
    }
    if (paginas > 1) escribir(`Página ${pagina} de ${paginas}`, 7, 293, 7)
  }
  pdf.setProperties({ title: `${c.estado === 'BORRADOR' ? 'Borrador' : 'Factura simulada'} ${c.tipo} ${numero}`, author: c.emisor.nombre,
    subject: 'Documento simulado sin validez fiscal' })
  return pdf
}
