import { jsPDF } from 'jspdf'
import { autoTable } from 'jspdf-autotable'
import { fechaLabel, medioPagoLabel, numeroComprobante, vehiculoLabel, type ComprobanteApi } from './comprobanteApi'

const amount = (value: number) =>
  Number(value || 0).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

export function crearPdfComprobante(comprobante: ComprobanteApi) {
  const cobros = comprobante.cobros?.length
    ? comprobante.cobros
    : comprobante.cobro ? [comprobante.cobro] : []
  const doc = new jsPDF({ unit: 'mm', format: 'a4', compress: true })
  const margen = 12
  const ancho = 210 - margen * 2

  const escribir = (texto: string, x: number, y: number, tam = 9, negrita = false, align: 'left' | 'right' | 'center' = 'left') => {
    doc.setFont('helvetica', negrita ? 'bold' : 'normal')
    doc.setFontSize(tam)
    doc.setTextColor(0)
    doc.text(texto, x, y, { align })
  }

  doc.setDrawColor(0)
  doc.setLineWidth(0.25)
  doc.rect(margen, 10, ancho, 30)
  escribir('OCTANO', margen + 4, 18, 16, true)
  escribir(comprobante.emisor?.nombre || 'Taller', margen + 4, 25, 10, true)
  escribir('COMPROBANTE INTERNO', 210 - margen - 4, 18, 15, true, 'right')
  escribir('Sin validez fiscal', 210 - margen - 4, 25, 10, false, 'right')

  doc.line(margen, 40, 210 - margen, 40)
  escribir(numeroComprobante(comprobante), margen, 47, 12, true)
  escribir(`Fecha: ${fechaLabel(comprobante.fecha)}`, margen, 54, 9)
  escribir(comprobante.descripcion, 210 - margen, 47, 10, false, 'right')
  escribir(comprobante.ordenId ? `Orden de trabajo #${comprobante.ordenId}` : 'Sin orden asociada', 210 - margen, 54, 9, false, 'right')

  doc.rect(margen, 60, ancho / 2 - 2, 32)
  doc.rect(margen + ancho / 2 + 2, 60, ancho / 2 - 2, 32)
  escribir('EMISOR', margen + 4, 67, 8, true)
  escribir(comprobante.emisor?.nombre || '—', margen + 4, 74, 9)
  escribir(`Documento: ${comprobante.emisor?.documento || '—'}`, margen + 4, 80, 8)
  escribir(`IVA: ${comprobante.emisor?.condicionIva || '—'}`, margen + 4, 86, 8)
  escribir('RECEPTOR', margen + ancho / 2 + 6, 67, 8, true)
  escribir(comprobante.receptor?.nombre || '—', margen + ancho / 2 + 6, 74, 9)
  escribir(`Documento: ${comprobante.receptor?.documento || '—'}`, margen + ancho / 2 + 6, 80, 8)
  escribir(`IVA: ${comprobante.receptor?.condicionIva || '—'}`, margen + ancho / 2 + 6, 86, 8)

  escribir(`Vehículo: ${vehiculoLabel(comprobante.vehiculo)} · ${comprobante.vehiculo.patente}`, margen, 103, 9)

  autoTable(doc, {
    startY: 109,
    margin: { left: margen, right: margen, bottom: 18 },
    tableWidth: ancho,
    theme: 'plain',
    head: [['Cant.', 'Descripción', 'P. unitario', 'Subtotal']],
    body: comprobante.items.map((item) => [
      Number(item.cantidad).toLocaleString('es-AR'),
      item.descripcion,
      amount(item.precioUnitario),
      amount(item.subtotal),
    ]),
    styles: { font: 'helvetica', fontSize: 8, textColor: 0, cellPadding: 2, overflow: 'linebreak', lineWidth: 0 },
    headStyles: { fillColor: 232, textColor: 0, fontStyle: 'bold', lineWidth: 0.2, lineColor: 0 },
    columnStyles: { 0: { cellWidth: 18, halign: 'right' }, 2: { cellWidth: 32, halign: 'right' }, 3: { cellWidth: 34, halign: 'right' } },
    rowPageBreak: 'avoid',
  })

  let y = ((doc as unknown as { lastAutoTable?: { finalY?: number } }).lastAutoTable?.finalY ?? 150) + 8
  if (y > 235) {
    doc.addPage()
    y = 20
  }

  const totales = [
    ['Repuestos e insumos', amount(comprobante.totalRepuestos)],
    ['Mano de obra', amount(comprobante.manoObra)],
    [`IVA ${Number(comprobante.porcentajeIva).toLocaleString('es-AR')}%`, amount(comprobante.montoIva)],
  ]
  totales.forEach(([label, valor]) => {
    escribir(label, 210 - margen - 70, y, 9)
    escribir(valor, 210 - margen, y, 9, false, 'right')
    y += 6
  })
  doc.line(210 - margen - 70, y - 3, 210 - margen, y - 3)
  escribir('TOTAL', 210 - margen - 70, y + 2, 11, true)
  escribir(amount(comprobante.total), 210 - margen, y + 2, 11, true, 'right')
  y += 12

  escribir('PAGOS REGISTRADOS', margen, y, 9, true)
  y += 6
  if (!cobros.length) {
    escribir('Sin pagos registrados.', margen, y, 8)
    y += 6
  } else {
    cobros.forEach((cobro) => {
      escribir(`${fechaLabel(cobro.fecha)} · ${medioPagoLabel(cobro.medioPago)}`, margen, y, 8)
      escribir(amount(cobro.importe), 210 - margen, y, 8, false, 'right')
      y += 5
    })
  }
  escribir(`Pagado: ${amount(comprobante.pagado)}`, margen, y + 2, 9, true)
  escribir(`Saldo pendiente: ${amount(comprobante.saldo)}`, 210 - margen, y + 2, 9, true, 'right')

  doc.setFontSize(8)
  doc.setTextColor(90)
  doc.text('Documento interno generado con Octano. No tiene validez fiscal.', 210 / 2, 287, { align: 'center' })

  doc.setProperties({
    title: `Comprobante interno ${numeroComprobante(comprobante)}`,
    author: comprobante.emisor?.nombre || 'Octano',
    subject: 'Comprobante interno sin validez fiscal',
  })
  return doc
}
