import { jsPDF } from 'jspdf'
import QRCode from 'qrcode'

// A4: 4 columnas de 52,5 mm y 4 filas de 74,2 mm, sin separación.
export const QR_LABEL = { width: 52.5, height: 74.2, columns: 4, rows: 4, qrSize: 29, qrTop: 14.5 } as const

export function createQrTemplatePdf(qrUrls: string[], templateImage: string | Uint8Array) {
  if (qrUrls.length !== 16) throw new Error('Seleccioná exactamente 16 QR para imprimir una hoja completa.')

  const doc = new jsPDF({ unit: 'mm', format: [210, 297], orientation: 'portrait', compress: true })
  doc.setProperties({ title: 'Plantilla de códigos QR', creator: 'Octano' })
  const perPage = QR_LABEL.columns * QR_LABEL.rows
  const top = (297 - QR_LABEL.rows * QR_LABEL.height) / 2

  for (let offset = 0; offset < qrUrls.length; offset += perPage) {
    if (offset > 0) doc.addPage()

    qrUrls.slice(offset, offset + perPage).forEach((url, index) => {
      const labelX = (index % QR_LABEL.columns) * QR_LABEL.width
      const labelY = top + Math.floor(index / QR_LABEL.columns) * QR_LABEL.height
      // Diseño original, sin su QR de ejemplo. Solo cambia el código de cada etiqueta.
      doc.addImage(templateImage, 'PNG', labelX, labelY, QR_LABEL.width, QR_LABEL.height, 'octano-sticker-template')
      const { modules } = QRCode.create(url, { errorCorrectionLevel: 'M' })
      const quietZone = 4
      const moduleSize = QR_LABEL.qrSize / (modules.size + quietZone * 2)
      const left = labelX + (QR_LABEL.width - QR_LABEL.qrSize) / 2
      const yTop = labelY + QR_LABEL.qrTop
      doc.setFillColor(255)
      doc.rect(left, yTop, QR_LABEL.qrSize, QR_LABEL.qrSize, 'F')
      doc.setFillColor(0)
      // Vectorial: conserva bordes nítidos al imprimir, incluida la zona blanca de seguridad.
      for (let row = 0; row < modules.size; row++) {
        for (let column = 0; column < modules.size;) {
          if (!modules.get(row, column)) { column++; continue }
          const start = column
          while (column < modules.size && modules.get(row, column)) column++
          doc.rect(left + (start + quietZone) * moduleSize, yTop + (row + quietZone) * moduleSize, (column - start) * moduleSize, moduleSize, 'F')
        }
      }
    })

    // Líneas compartidas de corte; no hay separación entre las etiquetas.
    doc.setDrawColor(110)
    doc.setLineWidth(0.1)
    for (let column = 0; column <= QR_LABEL.columns; column++) {
      const x = column * QR_LABEL.width
      doc.line(x, top, x, top + QR_LABEL.rows * QR_LABEL.height)
    }
    for (let row = 0; row <= QR_LABEL.rows; row++) {
      const y = top + row * QR_LABEL.height
      doc.line(0, y, 210, y)
    }
  }

  return doc
}
