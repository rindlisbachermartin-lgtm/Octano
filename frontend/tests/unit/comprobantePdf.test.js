jest.mock('jspdf', () => ({ jsPDF: jest.fn() }))
jest.mock('jspdf-autotable', () => ({ autoTable: (pdf, opciones) => opciones.willDrawPage() }))
const { crearPdfComprobante } = require('../../app/utils/comprobantePdf')
const { jsPDF } = require('jspdf')
test.each(['BORRADOR', 'EMITIDO'])('identifica %s en el PDF y marca cada pagina del borrador', (estado) => {
  const pdf = Object.fromEntries(['saveGraphicsState','restoreGraphicsState','setGState','setFont','setFontSize','setTextColor','text','setDrawColor','setLineWidth','rect','line','setFillColor','setPage','setProperties'].map(k => [k, jest.fn()]))
  pdf.GState = parametros => parametros
  pdf.splitTextToSize = texto => [texto]
  pdf.getNumberOfPages = () => 2
  jsPDF.mockImplementation(() => pdf)
  const parte = { nombre: 'Taller', condicionIva: null }
  crearPdfComprobante({ estado, id: 1, numero: null, tipo: 'X', emisor: parte, receptor: parte, vehiculo: { marca: 'Ford', modelo: 'Ka', anio: 2020 }, items: [], cobros: [], fecha: '2026-11-25' })
  const textos = pdf.text.mock.calls.map(([texto]) => texto)
  expect(textos).toContain(estado === 'BORRADOR' ? 'BORRADOR' : 'FACTURA')
  expect(pdf.text.mock.calls.filter(([, , , opciones]) => opciones?.angle === 0)).toHaveLength(estado === 'BORRADOR' ? 2 : 0)
  if (estado === 'BORRADOR') {
    expect(textos).not.toContain('FACTURA')
    expect(pdf.setGState).toHaveBeenCalledWith({ opacity: 0.1 })
    expect(pdf.restoreGraphicsState).toHaveBeenCalledTimes(2)
  } else expect(pdf.setGState).not.toHaveBeenCalled()
})
