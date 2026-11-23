import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { stripTypeScriptTypes } from 'node:module'

function vueFunction(path, name) {
  const script = readFileSync(path, 'utf8').match(/<script setup[^>]*>([\s\S]*?)<\/script>/)[1]
  return stripTypeScriptTypes(script).match(new RegExp(`function ${name}\\([^]*?\\n\\}`))[0]
}

function ejecutar(path, nombre, contexto) {
  return new Function(...Object.keys(contexto), `${vueFunction(path, nombre)}; return ${nombre}`)(...Object.values(contexto))
}

function tallerManual() {
  const eventos = []
  const formError = { value: '' }
  const total = { value: 60 }
  const vehiculoFijo = { value: 7 }
  const form = {
    value: {
      descripcion: 'Service corregido',
      manoObra: 10,
      porcentajeIva: 21,
      items: [{ partId: 9, searchQuery: 'Filtro', quantity: 2, unitPrice: 25, precioUnitario: 25 }],
    },
  }
  const submit = ejecutar('app/components/facturacion/ModalComprobanteManual.vue', 'submit', {
    props: { open: true, orden: { ordenId: 42, vehiculoId: 7, descripcion: 'Service' }, comprobante: null },
    form,
    formError,
    vehiculoFijo,
    vehiculoSeleccionado: { value: null },
    titularCompleto: { value: true },
    cargandoRepuestos: { value: false },
    errorRepuestos: { value: '' },
    repuestosArchivados: { value: [] },
    condicionIvaReceptorId: { value: 1 },
    puntoVenta: { value: 1 },
    parts: { value: [{ id: 9, name: 'Filtro' }] },
    total,
    emit: (...args) => eventos.push(args),
  })
  return { submit, eventos, formError, total, form, vehiculoFijo }
}

const Manual = tallerManual()
Manual.submit()
assert.equal(Manual.formError.value, '')
assert.equal(Manual.eventos.length, 1)
assert.equal(Manual.eventos[0][0], 'guardar')
const solicitud = Manual.eventos[0][1]
assert.equal(solicitud.ordenId, 42)
assert.equal(solicitud.vehiculoId, 7)
assert.equal(solicitud.descripcion, 'Service corregido')
assert.equal(solicitud.manoObra, 10)
assert.equal(solicitud.items[0].repuestoId, 9)
assert.equal(solicitud.items[0].cantidad, 2)
assert.equal(Manual.eventos[0][2], undefined, 'Un alta no envía id')

Manual.form.value = { ...Manual.form.value, descripcion: '' }
Manual.submit()
assert.ok(Manual.formError.value)
assert.equal(Manual.eventos.length, 1, 'Un formulario inválido no emite')

const sinVehiculo = tallerManual()
sinVehiculo.vehiculoFijo.value = null
sinVehiculo.submit()
assert.ok(sinVehiculo.formError.value)
assert.equal(sinVehiculo.eventos.length, 0)

const sinTotal = tallerManual()
sinTotal.total.value = 0
sinTotal.submit()
assert.ok(sinTotal.formError.value)
assert.equal(sinTotal.eventos.length, 0)

function confirmarPago({ importe, saldo }) {
  const eventos = []
  const error = { value: '' }
  const confirmar = ejecutar('app/components/facturacion/ModalComprobantePago.vue', 'confirmar', {
    importe: { value: importe },
    saldo: { value: saldo },
    error,
    medioPago: { value: 'EFECTIVO' },
    observaciones: { value: 'En mostrador' },
    emit: (...args) => eventos.push(args),
  })
  confirmar()
  return { error, eventos }
}

const parcial = confirmarPago({ importe: 25.5, saldo: 100 })
assert.equal(parcial.error.value, '')
assert.equal(parcial.eventos[0][0], 'confirm')
assert.equal(parcial.eventos[0][1].importe, 25.5)
assert.equal(parcial.eventos[0][1].medioPago, 'EFECTIVO')
assert.equal(parcial.eventos[0][1].observaciones, 'En mostrador')

for (const importe of [0, -5, 120]) {
  const invalido = confirmarPago({ importe, saldo: 100 })
  assert.ok(invalido.error.value, `El importe ${importe} debe ser rechazado`)
  assert.equal(invalido.eventos.length, 0)
}

const api = stripTypeScriptTypes(readFileSync('app/utils/comprobanteApi.ts', 'utf8')).replace(/export /g, '')
const helpers = new Function(`${api}; return { numeroComprobante, medioPagoLabel }`)()
assert.equal(helpers.numeroComprobante({ numero: 7, id: 9, puntoVenta: 1 }), 'Nº 0001-00000007')
assert.equal(helpers.numeroComprobante({ numero: null, id: 9 }), 'Borrador #9')
assert.equal(helpers.medioPagoLabel('TARJETA_DEBITO'), 'Tarjeta de débito')

console.log('Facturación interna: solicitudes, pagos parciales y comprobantes sin ARCA: OK')
