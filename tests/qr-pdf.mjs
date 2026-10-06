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
for (const count of [1, 15, 17, 32]) {
  assert.throws(() => createQrTemplatePdf(Array.from({ length: count }, () => urls[0]), templateImage), /exactamente 16/)
}
mkdirSync('tmp/pdfs', { recursive: true })
writeFileSync('tmp/pdfs/plantilla-qr-prueba.pdf', Buffer.from(doc.output('arraybuffer')))
console.log('PDF A4: exactamente 16 QR en una hoja; otras cantidades bloqueadas: OK')
