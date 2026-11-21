import { ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import ModalPresupuesto from '../../app/components/presupuestos/ModalPresupuesto.vue'
import ModalCompartirPresupuesto from '../../app/components/presupuestos/ModalCompartirPresupuesto.vue'
import Presupuestos from '../../app/pages/presupuestos/index.vue'
import ModalTurno from '../../app/components/agenda/ModalTurno.vue'
import { adaptarPresupuesto } from '../../app/utils/presupuestoApi'
import { inicialMayuscula } from '../../app/utils/inicialMayuscula'
import { budgetAmounts } from '../../app/utils/budgetAmounts'
import { prepararBaseDePrueba, campo } from './helpers'

const vehiculo = { id: 10, clienteId: 1, clienteNombre: 'Ana Pérez', marca: 'Toyota', modelo: 'Hilux', patente: 'ABC123', anio: 2020, activo: true, kilometraje: 5000 }
const cliente = { id: 1, nombre: 'Ana Pérez', documento: '30123456', telefono: '1123456789', email: '', activo: true }
const presupuesto = {
  id: 20, clienteId: 1, vehiculoId: 10, fecha: '2026-11-17', descripcion: 'Revisar frenos',
  estado: 'PENDIENTE', manoObra: 100, porcentajeIva: 10.5, totalRepuestos: 25,
  subtotal: 125, montoIva: 13.13, total: 138.13, tiposTrabajo: ['OTRO'],
  turnoId: null, ordenId: null, clienteNombre: 'Ana Pérez', marca: 'Toyota', modelo: 'Hilux', patente: 'ABC123',
  items: [{ renglon: 1, repuestoId: null, descripcion: 'Insumo', cantidad: 0.5, precioUnitario: 50, subtotal: 25 }],
}

let db
beforeEach(() => {
  db = prepararBaseDePrueba().db
  Object.assign(globalThis, {
    inicialMayuscula, budgetAmounts,
    useAuthApi: () => ({
      authorization: ref({ Authorization: 'Bearer prueba' }),
      user: ref({ id: 1, tallerId: 1 }), workshopName: ref('Taller real'),
      puedeAdministrar: ref(true),
    }),
    useWorkshopDay: () => ({ today: ref('2026-11-17') }),
    useRoute: () => ({ query: {} }),
    useListView: () => ref('cards'),
    $fetch: jest.fn(async (url, options = {}) => {
      if (url === '/api/vehiculos') return { contenido: [vehiculo], totalPaginas: 1 }
      if (url === '/api/vehiculos/10') return vehiculo
      if (url === '/api/clientes') return { contenido: [cliente], totalPaginas: 1 }
      if (url === '/api/clientes/1') return { ...cliente, nombre: 'Cliente API real', calle: 'San Martín', altura: 123 }
      if (url === '/api/talleres/1') return { nombre: 'Taller API', calle: 'Roca', altura: 456, telefono: '1123456789' }
      if (url === '/api/repuestos') return { contenido: [], totalPaginas: 1 }
      if (url === '/api/presupuestos' && options.method === 'POST') return presupuesto
      if (url === '/api/presupuestos') return { contenido: [presupuesto], totalPaginas: 1 }
      if (url === '/api/presupuestos/20/estado') return { ...presupuesto, estado: options.body.estado }
      if (url === '/api/presupuestos/20') return presupuesto
      if (url === '/api/turnos/disponibilidad') return { vehiculoConTurno: false, conflictos: [] }
      if (url === '/api/turnos' && options.method === 'POST') return {
        id: 30, vehiculoId: 10, presupuestoId: 20, ordenId: 40,
        fecha: options.body.fecha, horaInicio: options.body.horaInicio, horaFin: null,
        motivoVisita: options.body.motivoVisita, estado: 'PROGRAMADO',
        marca: 'Toyota', modelo: 'Hilux', patente: 'ABC123', clienteNombre: 'Ana Pérez',
      }
      throw new Error('Ruta inesperada ' + url)
    }),
  })
})

async function formulario(budget) {
  const wrapper = mount(ModalPresupuesto, {
    props: { open: false, budget },
    global: { stubs: { CommonEditorRepuestos: true, CommonFormatoInvalido: true } },
  })
  await wrapper.setProps({ open: true })
  await flushPromises()
  return wrapper
}

test('crea por API con un vehículo real y permite presupuesto solo de mano de obra', async () => {
  const wrapper = await formulario()
  await wrapper.get('select[aria-label="Vehículo"]').setValue('10')
  await wrapper.findAll('button').find(button => button.text() === 'Otro trabajo').trigger('click')
  await campo(wrapper, 'Detalle del trabajo').setValue('Revisar frenos')
  await campo(wrapper, 'Mano de obra').setValue('100')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect($fetch).toHaveBeenCalledWith('/api/presupuestos', expect.objectContaining({
    method: 'POST', body: {
      clienteId: 1, vehiculoId: 10, descripcion: 'Revisar frenos', manoObra: 100,
      porcentajeIva: 21, tiposTrabajo: ['OTRO'], items: [],
    },
  }))
  expect(db.value.quotes).toHaveLength(0)
  expect(wrapper.emitted('created')[0][0].total).toBe(138.13)
})

test('edita por PUT preservando el IVA y las cantidades decimales del presupuesto', async () => {
  const wrapper = await formulario(adaptarPresupuesto(presupuesto))
  await campo(wrapper, 'Detalle del trabajo').setValue('Revisar frenos y luces')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect($fetch).toHaveBeenCalledWith('/api/presupuestos/20', expect.objectContaining({
    method: 'PUT', body: expect.objectContaining({
      porcentajeIva: 10.5,
      items: [{ repuestoId: null, descripcion: 'Insumo', cantidad: 0.5, precioUnitario: 50 }],
    }),
  }))
  expect(wrapper.emitted('updated')).toHaveLength(1)
})

test('al editar muestra y conserva el titular histórico aunque el vehículo cambió de dueño', async () => {
  const titularActual = { id: 2, nombre: 'Nuevo titular', documento: '23456789', telefono: '2994444555', email: '', activo: true }
  const vehiculoTransferido = { ...vehiculo, clienteId: 2, clienteNombre: 'Nuevo titular' }
  const fetchOriginal = $fetch
  globalThis.$fetch = jest.fn((url, options) => {
    if (url === '/api/vehiculos') return Promise.resolve({ contenido: [vehiculoTransferido], totalPaginas: 1 })
    if (url === '/api/clientes') return Promise.resolve({ contenido: [cliente, titularActual], totalPaginas: 1 })
    return fetchOriginal(url, options)
  })
  const wrapper = await formulario(adaptarPresupuesto(presupuesto))
  expect(wrapper.get('.target-owner-name').text()).toBe('Ana Pérez')
  expect(wrapper.get('.target-sub').text()).toContain('Ana Pérez')
  expect(wrapper.get('.target-sub').text()).not.toContain('Nuevo titular')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect($fetch).toHaveBeenCalledWith('/api/presupuestos/20', expect.objectContaining({
    method: 'PUT', body: expect.objectContaining({ clienteId: 1, vehiculoId: 10 }),
  }))
  expect(wrapper.emitted('updated')).toHaveLength(1)
})

test('al cambiar el vehículo durante la edición usa el titular del vehículo elegido', async () => {
  const otroVehiculo = { ...vehiculo, id: 11, clienteId: 2, clienteNombre: 'Nuevo titular', patente: 'XYZ789' }
  const titularActual = { id: 2, nombre: 'Nuevo titular', documento: '23456789', telefono: '2994444555', email: '', activo: true }
  const fetchOriginal = $fetch
  globalThis.$fetch = jest.fn((url, options) => {
    if (url === '/api/vehiculos') return Promise.resolve({ contenido: [vehiculo, otroVehiculo], totalPaginas: 1 })
    if (url === '/api/clientes') return Promise.resolve({ contenido: [cliente, titularActual], totalPaginas: 1 })
    return fetchOriginal(url, options)
  })
  const wrapper = await formulario(adaptarPresupuesto(presupuesto))
  await wrapper.get('select[aria-label="Vehículo"]').setValue('11')
  expect(wrapper.get('.target-owner-name').text()).toBe('Nuevo titular')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect($fetch).toHaveBeenCalledWith('/api/presupuestos/20', expect.objectContaining({
    method: 'PUT', body: expect.objectContaining({ clienteId: 2, vehiculoId: 11 }),
  }))
})

test('rechaza importes negativos antes de enviar el presupuesto', async () => {
  const wrapper = await formulario(adaptarPresupuesto(presupuesto))
  await campo(wrapper, 'Mano de obra').setValue('-1')
  await wrapper.get('form').trigger('submit')
  expect(wrapper.text()).toContain('Formato inválido')
  expect($fetch.mock.calls.some(([, options]) => options?.method === 'PUT')).toBe(false)
})

test.each([[99999999.99, true], [100000000, false]])('mano de obra de %s respeta el límite de ocho dígitos', async (importe, valido) => {
  const wrapper = await formulario(adaptarPresupuesto(presupuesto))
  await campo(wrapper, 'Mano de obra').setValue(String(importe))
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect($fetch.mock.calls.some(([, options]) => options?.method === 'PUT')).toBe(valido)
  expect(campo(wrapper, 'Mano de obra').attributes('aria-invalid')).toBe(String(!valido))
})

test('la pantalla lista datos remotos y anula mediante PATCH conservando el historial', async () => {
  // "Anular" sólo se ofrece para presupuestos aceptados; el fixture base es PENDIENTE.
  const fetchOriginal = $fetch
  globalThis.$fetch = jest.fn((url, options) => url === '/api/presupuestos'
    ? Promise.resolve({ contenido: [{ ...presupuesto, estado: 'ACEPTADO' }], totalPaginas: 1 })
    : fetchOriginal(url, options))
  const wrapper = mount(Presupuestos, {
    global: { stubs: {
      PresupuestosModalPresupuesto: true, PresupuestosModalCompartirPresupuesto: true, AgendaModalTurno: true,
      CommonModalDialog: { template: '<section><slot /></section>' },
    } },
  })
  await flushPromises()
  expect(wrapper.text()).toContain('Toyota Hilux')
  expect(wrapper.text()).toContain('Ana Pérez')
  expect($fetch.mock.calls.filter(([url, options]) => url === '/api/presupuestos' && options?.query?.tamano === 20)).toHaveLength(1)
  expect($fetch.mock.calls.some(([url]) => /^\/api\/presupuestos\/\d+$/.test(url))).toBe(false)
  await wrapper.findAll('button').find(button => button.text() === 'Anular').trigger('click')
  await wrapper.findAll('button').find(button => button.text() === 'Confirmar').trigger('click')
  await flushPromises()
  expect($fetch).toHaveBeenCalledWith('/api/presupuestos/20/estado', expect.objectContaining({
    method: 'PATCH', body: { estado: 'ANULADO' },
  }))
})

test('pagina resúmenes sin descargar detalles y muestra el titular propio de cada presupuesto', async () => {
  const fetchOriginal = $fetch
  globalThis.$fetch = jest.fn((url, options) => url === '/api/presupuestos' ? Promise.resolve({ contenido: [{ ...presupuesto, clienteNombre: 'Titular anterior' }, { ...presupuesto, id: 21, clienteId: 2, clienteNombre: 'Titular actual' }], totalPaginas: 2, totalElementos: 22 }) : fetchOriginal(url, options))
  const wrapper = mount(Presupuestos, { global: { stubs: { PresupuestosModalPresupuesto: true, PresupuestosModalCompartirPresupuesto: true, AgendaModalTurno: true } } })
  await flushPromises()
  const cards = wrapper.findAll('.quote-card')
  expect(cards[0].text()).toContain('Titular anterior')
  expect(cards[0].text()).not.toContain('Titular actual')
  expect(cards[1].text()).toContain('Titular actual')
  expect($fetch.mock.calls.filter(([url, options]) => url === '/api/presupuestos' && options?.query?.tamano === 20)).toHaveLength(1)
  await wrapper.findAll('button').find(b => b.text() === 'Siguiente').trigger('click')
  await flushPromises()
  expect($fetch).toHaveBeenLastCalledWith('/api/presupuestos', expect.objectContaining({ query: expect.objectContaining({ pagina: 1, tamano: 20 }) }))
})

test('avisa sobre repuestos archivados y no permite guardar la edición', async () => {
  const fetchOriginal = $fetch
  globalThis.$fetch = jest.fn((url, options) => url === '/api/repuestos/7' ? Promise.resolve({ id: 7, descripcion: 'Filtro archivado', activo: false }) : fetchOriginal(url, options))
  const budget = adaptarPresupuesto({ ...presupuesto, items: [{ renglon: 1, repuestoId: 7, descripcion: 'Filtro archivado', cantidad: 1.5, precioUnitario: 100, subtotal: 150 }] })
  const wrapper = await formulario(budget)
  expect(wrapper.text()).toContain('incluye repuestos archivados: Filtro archivado')
  await campo(wrapper, 'Detalle del trabajo').setValue('Descripción corregida')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(wrapper.text()).toContain('incluye repuestos archivados. Quitá o reemplazá esos renglones para poder guardar')
  expect($fetch).not.toHaveBeenCalledWith('/api/presupuestos/20', expect.objectContaining({ method: 'PUT' }))
  expect(wrapper.emitted('updated')).toBeUndefined()
})

test('agendar desde un presupuesto vincula su ID y permite dejar el fin vacío', async () => {
  const wrapper = mount(ModalTurno, {
    props: { open: false, budget: adaptarPresupuesto(presupuesto), defaultDate: '2099-01-01' },
    global: { stubs: { CommonDatePicker: true } },
  })
  await wrapper.setProps({ open: true })
  await flushPromises()
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect($fetch).toHaveBeenCalledWith('/api/turnos', expect.objectContaining({
    method: 'POST', body: expect.objectContaining({
      clienteId: 1, vehiculoId: 10, presupuestoId: 20, horaFin: null, motivoVisita: 'Revisar frenos',
    }),
  }))
  expect(wrapper.emitted('created')[0][0].budgetId).toBe(20)
})

test('el detalle recibe los datos reales del cliente, del taller y el IVA guardado para imprimir', async () => {
  const wrapper = mount(Presupuestos, {
    global: { stubs: {
      PresupuestosModalPresupuesto: true, AgendaModalTurno: true,
      PresupuestosModalCompartirPresupuesto: {
        name: 'DetallePresupuesto',
        props: ['open', 'budget'],
        template: '<section />',
      },
    } },
  })
  await flushPromises()
  await wrapper.findAll('button').find(button => button.text() === 'Ver detalle').trigger('click')
  await flushPromises()
  const detail = wrapper.getComponent({ name: 'DetallePresupuesto' })
  expect(detail.props('open')).toBe(true)
  expect(detail.props('budget')).toEqual(expect.objectContaining({
    cliente: expect.objectContaining({ name: 'Cliente API real', address: 'San Martín 123' }),
    taller: expect.objectContaining({ nombre: 'Taller API' }),
    registro: expect.objectContaining({ porcentajeIva: 10.5 }),
  }))
})

test('el documento carga el taller real cuando el presupuesto no lo trae', async () => {
  const budget = {
    ...adaptarPresupuesto(presupuesto),
    cliente: { name: 'Ana Pérez', phone: '1123456789', email: '', address: '' },
    vehiculo: { brand: 'Toyota', model: 'Hilux', engine: '2.8 TDI', plate: 'ABC123', version: 'SRV' },
  }
  const wrapper = mount(ModalCompartirPresupuesto, {
    props: { open: true, budget },
    global: { stubs: {
      CommonModalDialog: { template: '<div><slot /></div>' },
      CommonDocumentPreview: { template: '<div><slot /></div>' },
    } },
  })
  await flushPromises()
  expect(wrapper.get('.workshop-title').text()).toBe('TALLER API')
})

test('el filtro de estados usa botones segmentados y Anulado tiene la letra roja', async () => {
  globalThis.$fetch = jest.fn((url, options = {}) => url === '/api/presupuestos'
    ? Promise.resolve({
        contenido: options.query?.estado === 'ANULADO'
          ? [{ ...presupuesto, id: 22, estado: 'ANULADO' }]
          : [presupuesto, { ...presupuesto, id: 21, estado: 'RECHAZADO' }],
        totalPaginas: 1, totalElementos: 2,
      })
    : Promise.resolve({}))
  const wrapper = mount(Presupuestos, {
    global: { stubs: {
      PresupuestosModalPresupuesto: true, PresupuestosModalCompartirPresupuesto: true, AgendaModalTurno: true,
    } },
  })
  await flushPromises()
  expect(wrapper.findAll('.toolbar-left-group .status-filters button').map(b => b.text().replace(/\d+$/, '').trim())).toEqual(['Pendientes', 'Aceptados', 'Todos'])
  expect(wrapper.find('.toolbar-left-group .list-view-switch').exists()).toBe(true)
  const badges = wrapper.findAll('.quote-card .badge')
  expect(badges[0].classes()).toContain('neutral')
  expect(badges[1].classes()).toContain('anulado')
  expect(badges[1].text()).toBe('Anulado')
  await wrapper.findAll('button').find(b => b.text().includes('Anulados')).trigger('click')
  await flushPromises()
  expect(wrapper.get('.quote-card .badge').classes()).toContain('anulado')
  expect(wrapper.get('.quote-card .badge').text()).toBe('Anulado')
  await wrapper.findAll('button').find(b => b.text().includes('Activos')).trigger('click')
  await flushPromises()
  await wrapper.findAll('.status-filters button')[0].trigger('click')
  await flushPromises()
  expect(globalThis.$fetch).toHaveBeenLastCalledWith('/api/presupuestos', expect.objectContaining({ query: expect.objectContaining({ estado: 'PENDIENTE' }) }))
})
