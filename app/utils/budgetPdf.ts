import { jsPDF } from 'jspdf'
import { autoTable, type UserOptions } from 'jspdf-autotable'

interface BudgetPdfData {
  id: number
  date: string
  client: { name: string; phone: string; email: string; address?: string } | null
  vehicle: { brand: string; model: string; engine: string; plate: string; vin?: string } | null
  description: string
  clientNotes?: string
  rows: { description: string; quantity: string | number; unitPrice: number; total: number }[]
  total: number
}

export function createBudgetPdf(data: BudgetPdfData) {
  const doc = new jsPDF({ unit: 'mm', format: 'a4' })
  const money = (value: number) => `$ ${Number(value || 0).toLocaleString('es-AR', {
    minimumFractionDigits: 2, maximumFractionDigits: 2,
  })}`
  const margin = 12
  const width = 186
  let endY = 0
  const table = (options: UserOptions) => {
    autoTable(doc, {
      theme: 'grid', margin: { top: 14, bottom: 18, left: margin, right: margin },
      styles: { font: 'helvetica', fontSize: 8, textColor: 0, fillColor: 255,
        lineColor: 0, lineWidth: 0.2, cellPadding: 2, overflow: 'linebreak' },
      headStyles: { fillColor: 245, textColor: 0, fontStyle: 'bold' },
      rowPageBreak: 'avoid',
      ...options,
      didDrawPage: (hook) => { endY = hook.cursor?.y ?? endY },
    })
    return endY
  }

  doc.setProperties({ title: `Presupuesto #${data.id}`, author: 'Taller Central' })
  doc.setFont('helvetica', 'bold')
  doc.setFontSize(14)
  doc.text('PRESUPUESTO - ESTIMACION REPARACION', 105, 19, { align: 'center' })
  table({ startY: 25, body: [[`N° Presupuesto: ${data.id}`, `Fecha: ${data.date}`]],
    styles: { fontSize: 9, fontStyle: 'bold', cellPadding: 2.5, lineColor: 0, lineWidth: 0.2 },
    columnStyles: { 0: { cellWidth: width / 2 }, 1: { cellWidth: width / 2 } },
  })

  const headerY = endY + 4
  const clientBottom = table({ startY: headerY, margin: { left: 106, right: margin }, tableWidth: 92,
    body: [
      ['Cliente:', data.client?.name.toUpperCase() || ''],
      ['Teléfono:', data.client?.phone || ''],
      ['Dirección:', data.client?.address || ''],
      ['Mail:', data.client?.email || ''],
      ['Observación:', data.clientNotes || ''],
    ], columnStyles: { 0: { cellWidth: 24 }, 1: { cellWidth: 68 } },
  })
  doc.rect(margin, headerY, 90, clientBottom - headerY)
  doc.setFontSize(13)
  doc.text('TALLER CENTRAL', margin + 4, headerY + 9)
  doc.setFont('helvetica', 'normal')
  doc.setFontSize(8)
  doc.text(['Av. San Martín 1420 - Centro', 'Tel: (011) 4567-8900',
    'Mail: administracion@tallercentral.com'], margin + 4, headerY + 15, { lineHeightFactor: 1.5 })

  table({ startY: clientBottom + 4, body: [
    ['MARCA:', data.vehicle?.brand.toUpperCase() || ''],
    ['MODELO:', `${data.vehicle?.model || ''} ${data.vehicle?.engine || ''}`.trim().toUpperCase()],
    ['MATRICULA:', data.vehicle?.plate || ''],
    ['VIN:', data.vehicle?.vin || data.vehicle?.engine || ''],
    ['OBSERVACION:', data.description.toUpperCase()],
  ], columnStyles: { 0: { cellWidth: 30, fontStyle: 'bold' }, 1: { cellWidth: 156 } } })

  const rows = data.rows.map(row => [row.description, String(row.quantity), money(row.unitPrice), money(row.total)])
  while (rows.length < 14) rows.push(['', '', '', ''])
  table({ startY: endY + 4,
    head: [['REFERENCIA / DETALLE PIEZAS', 'CANTIDAD', 'VALOR UNITARIO', 'TOTAL']],
    body: rows,
    columnStyles: { 0: { cellWidth: 100 }, 1: { cellWidth: 20, halign: 'center' },
      2: { cellWidth: 33, halign: 'right' }, 3: { cellWidth: 33, halign: 'right' } },
  })
  table({ startY: endY, pageBreak: 'avoid', body: [[
    'Presupuesto o estimación, bajo reserva del desmontaje.\nLos valores son expresados en pesos argentinos.\nValidez del presupuesto: 15 días.',
    `TOTAL\n${money(data.total)}`,
  ]], columnStyles: { 0: { cellWidth: 120, fontSize: 8 },
    1: { cellWidth: 66, fontStyle: 'bold', fontSize: 11, halign: 'right', valign: 'middle' } } })

  const pages = doc.getNumberOfPages()
  for (let page = 1; page <= pages; page++) {
    doc.setPage(page)
    doc.setFont('helvetica', 'normal')
    doc.setFontSize(8)
    doc.setTextColor(100)
    doc.text(`Presupuesto #${data.id} | Página ${page} de ${pages}`, 105, 288, { align: 'center' })
  }
  return doc
}
