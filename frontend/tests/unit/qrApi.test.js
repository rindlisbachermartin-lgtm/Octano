import { ref, watch } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { normalizarCodigoQr, seleccionQr } from '../../app/utils/qrApi'

jest.mock('qrcode', () => ({ toDataURL: jest.fn().mockResolvedValue('data:image/png;base64,qr') }))
jest.mock('../../app/utils/qrTemplatePdf', () => ({ createQrTemplatePdf: jest.fn(() => ({ save: jest.fn() })) }))
const Panel = require('../../app/components/qr/PanelApi.vue').default
const Vincular = require('../../app/components/qr/VincularApi.vue').default
const Ficha = require('../../app/components/qr/FichaPublica.vue').default
const Resolucion = require('../../app/pages/qr/[code].vue').default
const Layout = require('../../app/layouts/default.vue').default
const DetalleVehiculo = require('../../app/components/vehiculos/ModalDetalleVehiculo.vue').default
const ConsultaOrden = require('../../app/components/ordenes/ConsultaApi.vue').default
const Vehiculos = require('../../app/pages/vehiculos/index.vue').default
const { adaptarVehiculo } = require('../../app/utils/vehiculoApi')
const { createQrTemplatePdf } = require('../../app/utils/qrTemplatePdf')
const caja = { template: '<div><slot /></div>' }
const boton = (w, label) => w.findAll('button').find(b => b.text() === label)
const lote = Array.from({ length: 16 }, (_, i) => ({ codigo: `OCT-${1000 + i}`, estado: 'DISPONIBLE', vehiculoId: null, impresoEn: null, urlPublica: `https://taller.example/qr/OCT-${1000 + i}` }))
const pagina = { contenido: lote, totalElementos: 32, totalPaginas: 2 }
let auth
beforeEach(() => {
  globalThis.useFetch = (url, options) => {
    const data = ref(null), status = ref('pending'), error = ref(null)
    const refresh = async () => {
      data.value = null
      status.value = 'pending'
      error.value = null
      try { data.value = await $fetch(url(), options); status.value = 'success' }
      catch (err) { error.value = err; status.value = 'error' }
    }
    watch(url, refresh, { immediate: true })
    return { data, status, error, refresh }
  }
  auth = { authorization: ref({ Authorization: 'Bearer prueba' }), user: ref({ rol: 'ADMIN', email: 'admin@example.test' }), puedeAdministrar: ref(true), workshopName: ref('Taller'), homePath: ref('/') }
  Object.assign(globalThis, { useAuthApi: () => auth, useDebouncedValue: value => value, useTheme: () => ({ isDark: ref(false) }), $fetch: jest.fn(), fetch: jest.fn().mockResolvedValue({ ok: true, arrayBuffer: async () => new ArrayBuffer(1) }), useRoute: () => ({ path: '/codigos-qr', params: { code: 'OCT-1000' }, query: {} }), useRouter: () => ({ replace: jest.fn() }) })
})

test('normaliza códigos y exige 16 etiquetas distintas para imprimir', () => {
  expect(normalizarCodigoQr(' oct-1000 ')).toBe('OCT-1000')
  expect(() => normalizarCodigoQr('../privado')).toThrow()
  expect(() => seleccionQr(lote.slice(1).map(c => c.codigo))).toThrow('16')
  expect(() => seleccionQr([...lote.slice(1).map(c => c.codigo), ' oct-1001 '])).toThrow('distintos')
})

test('consulta el listado con JWT, busca en servidor y limpia selección al paginar', async () => {
  $fetch.mockResolvedValue(pagina)
  const w = mount(Panel)
  await flushPromises()
  expect($fetch).toHaveBeenLastCalledWith('/api/codigos-qr', expect.objectContaining({ headers: auth.authorization.value, query: expect.objectContaining({ estado: 'DISPONIBLE', tamano: 16 }) }))
  await boton(w, 'Seleccionar esta página').trigger('click')
  expect(w.text()).toContain('16 / 16')
  await boton(w, 'Siguiente').trigger('click')
  await flushPromises()
  expect(w.text()).toContain('0 / 16')
  expect($fetch.mock.calls.at(-1)[1].query.pagina).toBe(1)
  await w.get('[aria-label="Buscar códigos QR"]').setValue('ABC123')
  await flushPromises()
  expect($fetch.mock.calls.at(-1)[1].query).toMatchObject({ q: 'ABC123', pagina: 0 })
  expect(useDatabase).not.toHaveBeenCalled()
})

test('descarga la plantilla con los enlaces del servidor sin marcar impresiones', async () => {
  $fetch.mockImplementation((url) => Promise.resolve(url.endsWith('/plantillas') ? lote : pagina))
  const w = mount(Panel)
  await flushPromises()
  await boton(w, 'Seleccionar esta página').trigger('click')
  await boton(w, 'Descargar PDF').trigger('click')
  await flushPromises()
  expect(createQrTemplatePdf).toHaveBeenCalledWith(lote.map(c => c.urlPublica), expect.any(Uint8Array))
  expect($fetch.mock.calls.some(([url]) => url.endsWith('/impresiones'))).toBe(false)
})

test('registra una impresión solo al confirmar y conserva la selección ante rechazo', async () => {
  $fetch.mockImplementation((url) => url.endsWith('/impresiones') ? Promise.reject({ statusCode: 400, data: { detail: 'Uno ya está impreso.' } }) : Promise.resolve(pagina))
  const w = mount(Panel, { global: { stubs: { CommonModalDialog: caja } } })
  await flushPromises()
  await boton(w, 'Seleccionar esta página').trigger('click')
  await boton(w, 'Marcar como impresos').trigger('click')
  expect($fetch.mock.calls.some(([url]) => url.endsWith('/impresiones'))).toBe(false)
  await boton(w, 'Confirmar').trigger('click')
  await flushPromises()
  expect($fetch).toHaveBeenLastCalledWith('/api/codigos-qr/impresiones', expect.objectContaining({ method: 'POST', body: { codigos: lote.map(c => c.codigo) } }))
  expect(w.text()).toContain('Uno ya está impreso')
  expect(w.text()).toContain('16 / 16')
})

test('recepción consulta sin generar, vincular, desvincular ni imprimir', async () => {
  auth.puedeAdministrar.value = false
  auth.user.value.rol = 'RECEPCION'
  $fetch.mockResolvedValue(pagina)
  const w = mount(Panel)
  await flushPromises()
  expect(w.text()).not.toMatch(/Generar 16 QR|Marcar como impresos|Asignar vehículo|Desvincular/)
  expect(w.find('input[type=checkbox]').exists()).toBe(false)
})

test('vincula una etiqueta existente al vehículo real sin crear códigos', async () => {
  $fetch.mockImplementation((url, options = {}) => Promise.resolve(options.method === 'PUT' ? lote[0] : { id: 4, patente: 'ABC123', marca: 'Toyota', modelo: 'Hilux', clienteNombre: 'Ana', codigoQr: 'OCT-ANTERIOR' }))
  const w = mount(Vincular, { props: { codigo: 'oct-1000', vehiculoId: 4 } })
  await flushPromises()
  expect(w.get('.sticker-code .sticker-value').text()).toBe('OCT-ANTERIOR')
  expect(w.get('.replacement-warning').text()).not.toContain('OCT-ANTERIOR')
  expect(w.find('input[readonly]').exists()).toBe(false)
  expect(w.text()).toContain('inutilizado definitivamente')
  await w.get('form').trigger('submit')
  await flushPromises()
  expect($fetch).toHaveBeenLastCalledWith('/api/codigos-qr/OCT-1000/vehiculo', expect.objectContaining({ method: 'PUT', headers: auth.authorization.value, body: { vehiculoId: 4 } }))
  expect(w.emitted('saved')).toHaveLength(1)
})

test('el selector reúne todas las páginas de etiquetas sin mostrar paginación', async () => {
  $fetch.mockResolvedValueOnce({ contenido: lote.slice(0, 8), totalPaginas: 2 })
    .mockResolvedValueOnce({ contenido: lote.slice(8), totalPaginas: 2 })
  const w = mount(Vincular)
  await flushPromises()
  expect(w.get('[aria-label="Elegir etiqueta disponible"]').findAll('option')).toHaveLength(17)
  expect(w.get('option[value=""]').element.disabled).toBe(true)
  expect(w.get('option[value=""]').element.hidden).toBe(true)
  expect(w.find('input[maxlength="40"]').exists()).toBe(false)
  expect(w.get('.sticker-value').text()).toBe('Sin QR asignado')
  await w.get('[aria-label="Elegir etiqueta disponible"]').setValue('OCT-1000')
  expect(w.find('[aria-label="Elegir etiqueta disponible"]').exists()).toBe(false)
  expect(w.get('.selected-label').text()).toBe('OCT-1000')
  expect(w.get('.sticker-value').text()).toBe('Sin QR asignado')
  await w.get('[aria-label="Quitar etiqueta seleccionada"]').trigger('click')
  expect(w.get('[aria-label="Elegir etiqueta disponible"]').element.value).toBe('')
  expect(w.find('.selected-label').exists()).toBe(false)
  expect(w.find('[aria-label="Quitar etiqueta seleccionada"]').exists()).toBe(false)
  expect(w.text()).not.toMatch(/Anterior|Siguiente/)
  expect($fetch.mock.calls.map(([, options]) => options.query.pagina)).toEqual([0, 1])
})

test('un rechazo al vincular mantiene el formulario y el QR anterior', async () => {
  $fetch.mockImplementation((url, options = {}) => options.method === 'PUT' ? Promise.reject({ statusCode: 400, data: { detail: 'Ese QR ya fue utilizado.' } }) : Promise.resolve({ id: 4, patente: 'ABC123', codigoQr: 'OCT-ANTERIOR' }))
  const w = mount(Vincular, { props: { codigo: 'OCT-1000', vehiculoId: 4 } })
  await flushPromises()
  await w.get('form').trigger('submit')
  await flushPromises()
  expect(w.text()).toContain('Ese QR ya fue utilizado')
  expect(w.text()).toContain('OCT-ANTERIOR')
  expect(w.emitted('saved')).toBeUndefined()
})

test('la ficha pública consulta sin JWT, conserva cero km y filtros vacíos', async () => {
  $fetch.mockResolvedValue({ estado: 'ASIGNADO', tallerNombre: 'Taller Real', vehiculo: { patente: 'ABC123', marca: 'Toyota', modelo: 'Hilux', anio: 2020, kilometraje: 0 }, ultimoService: { fecha: '2026-11-20', kilometraje: 0, aceite: '5W30', filtros: [], repuestos: [] }, ultimaDistribucion: null, diagnostico: 'DATO PRIVADO' })
  const w = mount(Ficha, { props: { codigo: 'OCT-1000' } })
  await flushPromises()
  expect($fetch).toHaveBeenCalledWith('/api/publico/qr/OCT-1000', expect.not.objectContaining({ headers: expect.anything() }))
  expect(w.text()).toContain('0 km')
  expect(w.text()).toContain('No se informaron filtros')
  expect(w.text()).not.toContain('DATO PRIVADO')
  expect(w.find('a[href*="/ficha/"]').exists()).toBe(false)
})

test.each(['DISPONIBLE', 'INUTILIZADO'])('una etiqueta %s no expone datos antiguos de un vehículo', async estado => {
  $fetch.mockResolvedValue({ estado, tallerNombre: 'Taller', vehiculo: { patente: 'PRIVADO' }, ultimoService: null, ultimaDistribucion: null })
  const w = mount(Ficha, { props: { codigo: 'OCT-1000' } })
  await flushPromises()
  expect(w.text()).not.toContain('PRIVADO')
  expect(w.text()).toContain(estado === 'DISPONIBLE' ? 'pendiente de asignación' : 'inutilizado')
})

test('distingue un QR inexistente de una caída de servicio y permite reintentar', async () => {
  $fetch.mockRejectedValueOnce({ statusCode: 503 }).mockRejectedValueOnce({ statusCode: 404 })
  const w = mount(Ficha, { props: { codigo: 'OCT-1000' } })
  await flushPromises()
  expect(w.text()).toContain('No pudimos consultar')
  await boton(w, 'Reintentar').trigger('click')
  await flushPromises()
  expect(w.text()).toContain('Código QR no registrado')
})

test('la ruta pública abre la ficha API aunque haya datos demo con el mismo código', () => {
  useDatabase.mockReturnValue({ db: ref({ qrCodes: [{ code: 'OCT-1000', status: 'asignado', vehicleId: 1 }], vehicles: [{ id: 1, qrCode: 'OCT-1000' }] }) })
  const replace = jest.fn()
  globalThis.useRouter = () => ({ replace })
  const w = mount(Resolucion, { global: { stubs: { QrFichaPublica: true } } })
  expect(w.find('qr-ficha-publica-stub').exists()).toBe(true)
  expect(replace).not.toHaveBeenCalled()
  expect(useDatabase).not.toHaveBeenCalled()
})

test('el layout permite abrir Códigos QR con sesión real', () => {
  useDatabase.mockReturnValue({ db: ref({ notifications: [] }), expireAppointmentOrders: jest.fn(), activeOrders: ref([]), lowStock: ref([]) })
  useOwnerAccount.mockReturnValue({ owner: ref(null), isMechanic: ref(false), mechanic: ref(null) })
  const w = mount(Layout, { slots: { default: '<div data-testid="qr">Códigos QR conectados</div>' }, global: { mocks: { ownerInitials: () => 'AD' }, stubs: { ClientOnly: caja, CommonThemeToggle: true } } })
  expect(w.find('[data-testid=qr]').exists()).toBe(true)
  expect(w.text()).not.toContain('cuando se conecte el módulo')
})

test('el proxy permite solo las operaciones previstas del contrato QR', () => {
  Object.assign(globalThis, { defineEventHandler: fn => fn, getRequestURL: event => new URL(`http://localhost${event.path}`), getMethod: event => event.method, createError: data => Object.assign(new Error('No permitido'), data), useRuntimeConfig: () => ({ backendUrl: 'http://127.0.0.1:8080' }), setResponseHeader: jest.fn(), getRequestIP: () => '203.0.113.4', proxyRequest: jest.fn(() => 'ok') })
  const handler = require('../../server/api/[...path].ts').default
  expect(handler({ method: 'GET', path: '/api/ordenes-trabajo?vehiculoId=4' })).toBe('ok')
  for (const [method, path] of [['GET', '/api/codigos-qr'], ['POST', '/api/codigos-qr/lotes'], ['POST', '/api/codigos-qr/plantillas'], ['POST', '/api/codigos-qr/impresiones'], ['GET', '/api/codigos-qr/OCT-1000'], ['GET', '/api/codigos-qr/OCT-1000/imagen'], ['PUT', '/api/codigos-qr/OCT-1000/vehiculo'], ['DELETE', '/api/codigos-qr/OCT-1000/vehiculo'], ['GET', '/api/publico/qr/OCT-1000']]) expect(handler({ method, path })).toBe('ok')
  for (const [method, path] of [['POST', '/api/publico/qr/OCT-1000'], ['DELETE', '/api/codigos-qr/OCT-1000'], ['GET', '/api/publico/qr/OCT-1000/vehiculo']]) expect(() => handler({ method, path })).toThrow('No permitido')
})

test('el detalle prioriza trabajos por vehículo y mantiene titulares en un desplegable cerrado', async () => {
  const vehiculo = { id: 4, client: 2, brand: 'Toyota', model: 'Hilux', plate: 'ABC123', registro: { titular: 'Titular actual', activo: true } }
  useDatabase.mockReturnValue({ db: ref({ orders: [] }), client: jest.fn() })
  $fetch.mockImplementation(url => {
    if (url === '/api/ordenes-trabajo') return Promise.resolve({ contenido: [{ id: 8, clienteId: 1, descripcion: 'Service con titular anterior', estado: 'FINALIZADO', fechaCreacion: '2026-01-10T12:00:00Z', finFecha: '2026-01-11T12:00:00Z', kilometraje: 0, diagnostico: 'Revisión completa', observacionesMecanico: '' }], totalPaginas: 1 })
    return Promise.resolve([])
  })
  const w = mount(DetalleVehiculo, { props: { open: true, vehicleId: 4, vehicle: vehiculo }, global: { stubs: { CommonModalDialog: caja, OrdenesConsultaApi: true }, mocks: { kilometrajeVisible: () => 'Sin registrar' } } })
  await flushPromises()
  expect(w.get('.work-history').text()).toContain('Service con titular anterior')
  expect(w.get('.work-history').text()).not.toContain('Finalizado')
  expect(w.get('.ownership-history').element.open).toBe(false)
  expect(w.findAll('summary').at(-1).text()).toBe('Historial de titulares')
  const options = $fetch.mock.calls.find(([url]) => url === '/api/ordenes-trabajo')[1]
  expect(options.headers).toEqual(auth.authorization.value)
  expect(options.query).toEqual({ vehiculoId: 4, estado: 'FINALIZADO', pagina: 0, tamano: 20 })
  await w.setProps({ vehicle: { ...vehiculo, client: 3, registro: { titular: 'Otro titular', activo: true } } })
  await flushPromises()
  expect(w.get('.work-history').text()).toContain('Service con titular anterior')
  await boton(w, 'Ver OT').trigger('click')
  expect(w.get('ordenes-consulta-api-stub').attributes('id')).toBe('8')
})

test('el historial de trabajos permite reintentar sin mostrar órdenes locales ante un error', async () => {
  useDatabase.mockReturnValue({ db: ref({ orders: [{ id: 99, service: 'Dato demo' }] }), client: jest.fn() })
  let fallo = true
  $fetch.mockImplementation(url => url === '/api/ordenes-trabajo' && fallo ? Promise.reject({ statusCode: 503 }) : Promise.resolve(url === '/api/ordenes-trabajo' ? { contenido: [], totalPaginas: 0 } : []))
  const w = mount(DetalleVehiculo, { props: { open: true, vehicleId: 4, vehicle: { id: 4, registro: { titular: 'Ana' } } }, global: { stubs: { CommonModalDialog: caja }, mocks: { kilometrajeVisible: () => '' } } })
  await flushPromises()
  expect(w.get('.work-history').text()).toContain('No se pudo cargar')
  expect(w.text()).not.toContain('Dato demo')
  fallo = false
  await boton(w, 'Reintentar').trigger('click')
  await flushPromises()
  expect(w.get('.work-history').text()).toContain('todavía no tiene trabajos finalizados')
})

test('la consulta de la OT abre datos reales, con versión y kilometraje cero', async () => {
  $fetch.mockResolvedValue({ orden: { id: 8, marca: 'Citroen', modelo: 'C3', version: '1.4 HDi DIESEL', patente: 'ABC123', descripcion: 'Service', kilometraje: 0, filtros: [], manoObra: 100, porcentajeIva: 21, finFecha: '2026-11-20T12:00:00Z' }, tareas: [], items: [], fotos: [], totalRepuestos: 0, montoIva: 21, total: 121 })
  const w = mount(ConsultaOrden, { props: { id: 8 }, global: { stubs: { CommonModalDialog: caja } } })
  await flushPromises()
  expect($fetch).toHaveBeenCalledWith('/api/ordenes-trabajo/8', expect.objectContaining({ headers: auth.authorization.value }))
  expect(w.text()).toContain('Citroen C3 1.4 HDi')
  expect(w.text()).toContain('0 km')
  await boton(w, 'Volver al vehículo').trigger('click')
  expect(w.emitted('close')).toHaveLength(1)
})

test('la tabla separa marca, modelo, versión, combustible, año, QR y acciones', async () => {
  Object.assign(globalThis, { useListView: () => ref('table'), adaptarVehiculo })
  useDatabase.mockReturnValue({ db: ref({ vehicles: [], clients: [] }), client: jest.fn() })
  $fetch.mockResolvedValue({ contenido: [{ id: 4, clienteId: 1, marca: 'Citroen', modelo: 'C3', version: '1.4 HDi DIESEL', patente: 'ABC123', anio: 2010, activo: true, kilometraje: 1234 }], pagina: 0, totalPaginas: 1, totalElementos: 1 })
  const w = mount(Vehiculos)
  await flushPromises()
  expect(w.findAll('th').map(h => h.text())).toEqual(['PATENTE', 'MARCA', 'MODELO', 'VERSIÓN', 'COMBUSTIBLE', 'AÑO', 'QR', 'ACCIONES'])
  const cells = w.findAll('tbody tr')[0].findAll('td')
  expect(cells.slice(0, 7).map(c => c.text())).toEqual(['ABC123', 'Citroen', 'C3', '1.4 HDi', 'Diesel', '2010', 'Sin QR'])
})
