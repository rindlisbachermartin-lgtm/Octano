import { jsPDF } from 'jspdf'
import { autoTable } from 'jspdf-autotable'
import type { InvoiceItem, Invoice } from '../types'

interface InvoicePdfData {
  issuer?: Invoice['issuer']
  type: string
  issuerVat?: string
  taxLegend?: string
  code: string
  number: string
  date: string
  client: { name: string; doc: string; address: string; city: string; province: string; phone: string; vat: string }
  paymentMethod: string
  items: (InvoiceItem & { code: string })[]
  total: number
  net: number
  vat: number
  budget: string
  vehicle: string
  cae?: string | null
  caeDate: string
  qrImage?: string
}

// Draw the document directly: screen themes, scrolling and canvas text metrics
// must not change the downloaded invoice.
export function createInvoicePdf(data: InvoicePdfData) {
  const doc = new jsPDF({ unit: 'mm', format: 'a4', compress: true })
  const margin = 3
  const width = 204
  const amount = (value: number) => value.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  const text = (value: string | string[], x: number, y: number, size = 9, bold = false, align: 'left' | 'right' | 'center' = 'left') => {
    doc.setFont('helvetica', bold ? 'bold' : 'normal')
    doc.setFontSize(size)
    doc.setTextColor(0)
    doc.text(value, x, y, { align })
  }
  const wrap = (value: string, maxWidth: number, size = 9) => {
    doc.setFont('helvetica', 'normal')
    doc.setFontSize(size)
    return doc.splitTextToSize(value, maxWidth) as string[]
  }
  const clientRows = [
    [`Nombre: ${data.client.name}`, `CUIT / DNI: ${data.client.doc}`],
    [`Domicilio: ${data.client.address}`, `Localidad: ${data.client.city}`],
    [`Cond. IVA: ${data.client.vat}`, `Provincia: ${data.client.province}`],
    [`Cond. Venta: ${data.paymentMethod}`, `Teléfono: ${data.client.phone}`],
  ].map(row => row.map(value => wrap(value, 93, 8)))
  const clientHeight = clientRows.reduce((height, row) => height + Math.max(row[0]!.length, row[1]!.length) * 3.8, 4)
  const tableTop = 59 + clientHeight + 2

  function drawHeader() {
    doc.setDrawColor(0)
    doc.setLineWidth(0.25)
    doc.rect(margin, 3, width, 53)
    doc.line(105, 3, 105, 56)
    text(wrap(data.issuer?.name || 'TALLER CENTRAL S.R.L.', 84, 14), 54, 19, 14, true, 'center')
    text(['Servicios Mecánicos y Reparación Integral', data.issuer?.address || 'Av. García Salinas 1450', data.issuer ? `${data.issuer.city} - ${data.issuer.province}` : 'Trenque Lauquen - Buenos Aires', data.issuer?.phone || '(02392) 45-6789'], 54, 32, 8, false, 'center')
    text(data.issuerVat || (data.type === 'C' ? 'Responsable Monotributo' : 'IVA Responsable Inscripto'), 54, 53, 8, true, 'center')
    doc.setFillColor(255, 255, 255)
    doc.rect(97, 3, 16, 16, 'FD')
    text(data.type, 105, 13, 25, true, 'center')
    text(`CÓD. ${data.code}`, 105, 17.5, 7, false, 'center')
    doc.setFillColor(232, 232, 232)
    doc.rect(97, 19, 16, 4, 'F')
    text('ORIGINAL', 105, 22, 7, false, 'center')
    text(data.type.startsWith('Nota') ? data.type.toUpperCase() : 'FACTURA', 121, 12, 20, true)
    text(data.number, 121, 21, 12, true)
    text(`Fecha de Emisión: ${data.date}`, 121, 28, 9)
    text([`CUIT: ${data.issuer?.cuit || '30-71829401-8'}`, `Ingresos Brutos: ${data.issuer ? '—' : '30-71829401-8'}`, `Inicio de Actividades: ${data.issuer?.activityStartDate ? data.issuer.activityStartDate.split('-').reverse().join('/') : data.issuer ? '—' : '01/03/2018'}`], 121, 43, 8)
    doc.rect(margin, 59, width, clientHeight)
    let rowY = 63
    for (const row of clientRows) {
      text(row[0]!, 8, rowY, 8)
      text(row[1]!, 109, rowY, 8)
      rowY += Math.max(row[0]!.length, row[1]!.length) * 3.8
    }
  }

  doc.setProperties({ title: `Factura ${data.type} ${data.number}`, author: 'Taller Central' })
  let lastY = tableTop
  autoTable(doc, {
    startY: tableTop,
    margin: { top: tableTop, bottom: 16, left: margin, right: margin },
    tableWidth: width,
    theme: 'plain',
    head: [['Código', 'Descripción', 'Cantidad', 'P. Unitario', 'Importe']],
    body: data.items.map(item => [item.code, item.description, item.quantity.toLocaleString('es-AR'), amount(item.unitPrice), amount(item.total)]),
    styles: { font: 'helvetica', fontSize: 8, textColor: 0, cellPadding: 1.8, overflow: 'linebreak', lineWidth: 0 },
    headStyles: { fillColor: 232, textColor: 0, fontStyle: 'bold', lineColor: 0, lineWidth: 0.25 },
    columnStyles: { 0: { cellWidth: 22 }, 1: { cellWidth: 95 }, 2: { cellWidth: 23, halign: 'right' }, 3: { cellWidth: 32, halign: 'right' }, 4: { cellWidth: 32, halign: 'right' } },
    rowPageBreak: 'avoid',
    willDrawPage: () => drawHeader(),
    didDrawPage: hook => { lastY = hook.cursor?.y ?? tableTop },
  })

  const observations = [
    ...(data.taxLegend ? wrap(data.taxLegend, 196, 7) : []),
    ...wrap(`Presupuesto de origen: ${data.budget}`, 196, 7),
    ...wrap(`Vehículo: ${data.vehicle}`, 196, 7),
  ]
  const observationHeight = observations.length * 3.5 + 5
  const totalsHeight = data.type === 'A' ? 33 : 28
  const footerHeight = totalsHeight + 3 + observationHeight + 3 + 26 + 7
  let footerY = Math.max(216, lastY + 6)
  if (footerY + footerHeight > 294) {
    if (lastY + 6 + footerHeight <= 294) footerY = 294 - footerHeight
    else {
      doc.addPage()
      drawHeader()
      footerY = Math.max(tableTop + 6, 294 - footerHeight)
    }
  }
  doc.rect(margin, footerY, width, totalsHeight)
  const totalRows: [string, number][] = [['Subtotal: $', data.type === 'A' ? data.net : data.total], ['Dto./Recargo: $', 0]]
  if (data.type === 'A') totalRows.push(['IVA 21%: $', data.vat])
  totalRows.push(['Total: $', data.total])
  let totalY = footerY + totalsHeight - totalRows.length * 5.5 + 2
  for (const [label, value] of totalRows) {
    const isTotal = label === 'Total: $'
    text(label, 167, totalY, isTotal ? 10 : 8, true, 'right')
    text(amount(value), 202, totalY, isTotal ? 10 : 8, isTotal, 'right')
    totalY += 5.5
  }
  const observationY = footerY + totalsHeight + 3
  doc.rect(margin, observationY, width, observationHeight)
  text(observations, 7, observationY + 4, 7)
  const arcaY = observationY + observationHeight + 3
  const logoX = data.qrImage ? 33 : 7
  if (data.qrImage) doc.addImage(data.qrImage, 'PNG', 6, arcaY, 24, 24)
  text('ARCA', logoX, arcaY + 8, 20, true)
  text(['AGENCIA DE RECAUDACIÓN', 'Y CONTROL ADUANERO'], logoX, arcaY + 10, 4)
  text(data.cae ? 'Comprobante Autorizado' : 'Comprobante sin autorización fiscal', logoX, arcaY + 18, 8, true)
  if (data.cae) text(wrap('Esta Administración Federal no se responsabiliza por los datos ingresados en el detalle de la operación.', 115, 5), logoX, arcaY + 22, 5)
  text(`CAE N°: ${data.cae || '—'}`, 203, arcaY + 5, 8, false, 'right')
  text(`Fecha de Vto. de CAE: ${data.caeDate}`, 203, arcaY + 11, 8, false, 'right')
  text('Comprobante generado con Octano', 203, arcaY + 30, 8, false, 'right')
  const pages = doc.getNumberOfPages()
  if (pages > 1) {
    for (let page = 1; page <= pages; page++) {
      doc.setPage(page)
      text(`Página ${page} de ${pages}`, 7, 293, 7)
    }
  }
  return doc
}
