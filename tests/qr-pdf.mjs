import assert from 'node:assert/strict'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createQrTemplatePdf, QR_LABEL } from '../app/utils/qrTemplatePdf.ts'

const urls = Array.from({ length: 16 }, (_, index) => `https://octano.example/qr/OCT-${2053 + index}`)
const templateImage = readFileSync('public/graphics/qr-sticker-template.png')
assert.throws(() => createQrTemplatePdf([], templateImage), /Seleccioná/)
const doc = createQrTemplatePdf(urls, templateImage)
assert.equal(doc.getNumberOfPages(), 1)
assert.equal(doc.internal.pageSize.getWidth(), 210)
assert.equal(doc.internal.pageSize.getHeight(), 297)
assert.equal(QR_LABEL.width * QR_LABEL.columns, 210)
assert.equal(QR_LABEL.height * QR_LABEL.rows, 296.8)
assert.equal(createQrTemplatePdf([...urls, urls[0]], templateImage).getNumberOfPages(), 2)
assert.equal(createQrTemplatePdf([...urls, ...urls], templateImage).getNumberOfPages(), 2)
mkdirSync('tmp/pdfs', { recursive: true })
writeFileSync('tmp/pdfs/plantilla-qr-prueba.pdf', Buffer.from(doc.output('arraybuffer')))
console.log('PDF A4: 16 QR por hoja, etiquetas de 52,5 × 74,2 mm y paginación: OK')
