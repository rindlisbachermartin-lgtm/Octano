import { ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import Panel from '../../app/components/ordenes/PanelApi.vue'
import Formulario from '../../app/components/ordenes/FormularioApi.vue'
import Detalle from '../../app/components/ordenes/DetalleApi.vue'
import { cierreOrden } from '../../app/utils/ordenApi'

const cabecera = (changes = {}) => ({
  id: 90, vehiculoId: 34, clienteId: 21, mecanicoId: 7, turnoId: null, presupuestoId: null,
  descripcion: 'Revisión de frenos', tiposTrabajo: ['OTRO'], indicaciones: '', diagnostico: '', observacionesMecanico: '',
  kilometraje: null, aceite: '', filtros: [], manoObra: 12000, porcentajeIva: 21, estado: 'EN_ESPERA',
  clienteNombre: 'Ana Pérez', patente: 'ABC123', marca: 'Toyota', modelo: 'Hilux', mecanicoNombre: 'María López',
  fechaCreacion: '2026-11-20T12:00:00Z', ingresoFecha: null, inicioFecha: null, finFecha: null, ...changes,
})
const respuesta = (changes = {}, extra = {}) => ({
  orden: cabecera(changes), tareas: [{ id: 51, descripcion: 'Revisar frenos', completada: false }],
  items: [], fotos: [], progreso: 0, totalRepuestos: 0, subtotal: 12000, montoIva: 2520, total: 14520, ...extra,
})
const boton = (wrapper, label) => wrapper.findAll('button').find(b => b.text() === label)
const caja = { template: '<section><slot /></section>' }
let auth

beforeEach(() => {
  auth = { authorization: ref({ Authorization: 'Bearer prueba' }), user: ref({ rol: 'ADMIN' }) }
  Object.assign(globalThis, {
    useAuthApi: () => auth,
    useListView: () => ref('cards'),
    useRoute: () => ({ query: {} }),
    useDebouncedValue: value => value,
    $fetch: jest.fn(),
  })
})

test('consulta órdenes reales, pagina y envía los filtros al servidor', async () => {
  $fetch.mockResolvedValue({ contenido: [cabecera()], totalElementos: 21, totalPaginas: 2 })
  const panel = mount(Panel)
  await flushPromises()
  expect(panel.text()).toContain('Ana Pérez')
  expect(useDatabase).not.toHaveBeenCalled()
  await boton(panel, 'Siguiente').trigger('click')
  await flushPromises()
  expect($fetch).toHaveBeenLastCalledWith('/api/ordenes-trabajo', expect.objectContaining({ headers: auth.authorization.value, query: expect.objectContaining({ pagina: 1 }) }))
  await panel.get('[aria-label="Buscar órdenes"]').setValue('ABC123')
  await flushPromises()
  expect($fetch).toHaveBeenLastCalledWith('/api/ordenes-trabajo', expect.objectContaining({ query: expect.objectContaining({ q: 'ABC123', pagina: 0 }) }))
})

test('no reemplaza una búsqueda nueva con una respuesta anterior', async () => {
  let resolver
  $fetch.mockImplementationOnce(() => new Promise(resolve => { resolver = resolve }))
    .mockResolvedValueOnce({ contenido: [cabecera({ descripcion: 'Resultado nuevo' })], totalElementos: 1, totalPaginas: 1 })
  const panel = mount(Panel)
  await panel.get('[aria-label="Buscar órdenes"]').setValue('nuevo')
  await flushPromises()
  resolver({ contenido: [cabecera({ descripcion: 'Resultado viejo' })], totalElementos: 1, totalPaginas: 1 })
  await flushPromises()
  expect(panel.text()).toContain('Resultado nuevo')
  expect(panel.text()).not.toContain('Resultado viejo')
})

test('el mecánico accede al listado sin alta ni filtros que amplíen su alcance', async () => {
  auth.user.value.rol = 'MECANICO'
  $fetch.mockResolvedValue({ contenido: [], totalElementos: 0, totalPaginas: 0 })
  const panel = mount(Panel)
  await flushPromises()
  expect(panel.text()).toContain('Mis órdenes')
  expect(boton(panel, 'Nueva orden')).toBeUndefined()
  expect($fetch.mock.calls[0][1].query).not.toHaveProperty('mecanicoId')
})

test('el alta usa vehículo y mecánico de la API y conserva el formulario ante un error', async () => {
  $fetch.mockImplementation((url, options = {}) => {
    if (url === '/api/mecanicos') return Promise.resolve([{ id: 7, nombre: 'María', apellido: 'López' }])
    if (url === '/api/vehiculos') return Promise.resolve({ contenido: [{ id: 34, patente: 'ABC123', marca: 'Toyota', modelo: 'Hilux', clienteNombre: 'Ana Pérez' }], totalPaginas: 1 })
    if (url === '/api/presupuestos') return Promise.resolve({ contenido: [], totalPaginas: 0 })
    if (options.method === 'POST') return Promise.reject({ statusCode: 400, data: { detail: 'El vehículo está inactivo.' } })
    throw new Error(url)
  })
  const form = mount(Formulario, { props: { orden: null } })
  await flushPromises()
  expect(form.get('.dependent-fields').element.disabled).toBe(true)
  expect(form.get('button[type=submit]').element.disabled).toBe(true)
  await form.get('[aria-label="Buscar vehículo para la orden"]').setValue('ABC123')
  await flushPromises()
  await form.get('.vehicle-result').trigger('click')
  expect(form.get('.dependent-fields').element.disabled).toBe(false)
  await boton(form, 'Otro trabajo').trigger('click')
  expect(boton(form, 'Otro trabajo').attributes('aria-pressed')).toBe('true')
  await boton(form, 'Service').trigger('click')
  expect(boton(form, 'Otro trabajo').attributes('aria-pressed')).toBe('true')
  await boton(form, 'Service').trigger('click')
  expect(boton(form, 'Service').attributes('aria-pressed')).toBe('false')
  await form.findAll('textarea')[0].setValue('Revisión de frenos')
  await form.findAll('select')[1].setValue(7)
  await form.get('form').trigger('submit')
  await flushPromises()
  expect($fetch).toHaveBeenLastCalledWith('/api/ordenes-trabajo', expect.objectContaining({ method: 'POST', headers: auth.authorization.value, body: expect.objectContaining({ vehiculoId: 34, mecanicoId: 7, presupuestoId: null }) }))
  expect(form.get('[role=alert]').text()).toContain('inactivo')
  expect(form.findAll('textarea')[0].element.value).toBe('Revisión de frenos')
  expect(form.emitted('saved')).toBeUndefined()
  await form.get('[aria-label="Quitar vehículo seleccionado"]').trigger('click')
  expect(form.get('.dependent-fields').element.disabled).toBe(true)
  expect(form.get('button[type=submit]').element.disabled).toBe(true)
})

test('editar planificación conserva los vínculos y bloquea envíos duplicados', async () => {
  let resolver
  $fetch.mockResolvedValueOnce([{ id: 7, nombre: 'María', apellido: 'López' }])
    .mockImplementationOnce(() => new Promise(resolve => { resolver = resolve }))
  const form = mount(Formulario, { props: { orden: cabecera({ presupuestoId: 63 }) } })
  await flushPromises()
  await form.get('form').trigger('submit')
  await form.get('form').trigger('submit')
  expect($fetch.mock.calls.filter(([, o]) => o.method === 'PUT')).toHaveLength(1)
  expect($fetch).toHaveBeenLastCalledWith('/api/ordenes-trabajo/90', expect.objectContaining({ method: 'PUT', body: expect.objectContaining({ vehiculoId: 34, presupuestoId: 63, mecanicoId: 7 }) }))
  resolver(respuesta({ presupuestoId: 63 }))
  await flushPromises()
  expect(form.emitted('saved')).toHaveLength(1)
})

test('stock insuficiente conserva el estado y muestra el error del servidor', async () => {
  $fetch.mockResolvedValueOnce(respuesta()).mockRejectedValueOnce({ statusCode: 400, data: { detail: 'Stock insuficiente.' } })
  const detalle = mount(Detalle, { props: { id: 90 }, global: { stubs: { CommonModalDialog: caja } } })
  await flushPromises()
  await boton(detalle, 'Iniciar orden').trigger('click')
  await flushPromises()
  expect($fetch).toHaveBeenLastCalledWith('/api/ordenes-trabajo/90/inicio', expect.objectContaining({ method: 'POST', headers: auth.authorization.value }))
  expect(detalle.get('[role=alert]').text()).toContain('Stock insuficiente')
  expect(detalle.text()).toContain('En espera')
  expect(detalle.emitted('updated')).toBeUndefined()
})

test('tareas y cantidades usan los IDs del renglón y confirman los valores desde la API', async () => {
  const data = respuesta({ estado: 'EN_PROCESO' }, { items: [{ id: 810, repuestoId: 2, descripcion: 'Aceite', cantidad: 1.5, precioUnitario: 10, subtotal: 15, consumido: true }] })
  $fetch.mockResolvedValue(data)
  const detalle = mount(Detalle, { props: { id: 90 }, global: { stubs: { CommonModalDialog: caja } } })
  await flushPromises()
  await detalle.get('.task-list input').setValue(true)
  await flushPromises()
  expect($fetch).toHaveBeenLastCalledWith('/api/ordenes-trabajo/90/tareas/51', expect.objectContaining({ method: 'PATCH', body: { completada: true } }))
  await detalle.get('[aria-label="Cantidad de Aceite"]').setValue(2.125)
  await detalle.get('[aria-label="Guardar cantidad de Aceite"]').trigger('submit')
  await flushPromises()
  expect($fetch).toHaveBeenLastCalledWith('/api/ordenes-trabajo/90/repuestos/810', expect.objectContaining({ method: 'PATCH', body: { cantidad: 2.125 } }))
})

test('el mecánico no puede cancelar, reasignar, quitar materiales ni crear conceptos manuales', async () => {
  auth.user.value.rol = 'MECANICO'
  $fetch.mockResolvedValue(respuesta({ estado: 'EN_PROCESO' }))
  const detalle = mount(Detalle, { props: { id: 90 }, global: { stubs: { CommonModalDialog: caja } } })
  await flushPromises()
  expect(detalle.text()).not.toMatch(/Dar de baja|Editar planificación|Concepto manual|Nueva tarea/)
  expect(boton(detalle, 'Guardar trabajo')).toBeDefined()
  expect(boton(detalle, 'Finalizar orden')).toBeDefined()
})

test('el cierre valida service, permite cero km y limpia aceite y filtros para otros trabajos', () => {
  expect(() => cierreOrden(null, '', '', [], false)).toThrow('kilometraje')
  expect(() => cierreOrden(10000000, '', '', [], false)).toThrow('kilometraje')
  expect(() => cierreOrden(20.5, '', '', [], false)).toThrow('kilometraje')
  expect(() => cierreOrden(0, '', ' ', [], true)).toThrow('aceite')
  expect(cierreOrden(0, ' Listo ', ' 5W30 ', ['ACEITE'], true)).toEqual({ kilometraje: 0, observacionesMecanico: 'Listo', aceite: '5W30', filtros: ['ACEITE'] })
  expect(cierreOrden(100, '', '5W30', ['ACEITE'], false)).toMatchObject({ aceite: '', filtros: [] })
})

test('finaliza un service con sus datos de mantenimiento y muestra la orden cerrada', async () => {
  $fetch.mockResolvedValueOnce(respuesta({ estado: 'EN_PROCESO', tiposTrabajo: ['SERVICE'] }))
    .mockResolvedValueOnce(respuesta({ estado: 'FINALIZADO', tiposTrabajo: ['SERVICE'], kilometraje: 0, aceite: '5W30', filtros: ['ACEITE'] }))
  const detalle = mount(Detalle, { props: { id: 90 }, global: { stubs: { CommonModalDialog: caja } } })
  await flushPromises()
  await boton(detalle, 'Finalizar orden').trigger('click')
  const form = detalle.findAll('form').find(f => f.text().includes('Confirmar finalización'))
  await form.get('input[type=number]').setValue(0)
  await form.get('input[maxlength="120"]').setValue('5W30')
  await form.get('input[value=ACEITE]').setValue(true)
  await form.trigger('submit')
  await flushPromises()
  expect($fetch).toHaveBeenLastCalledWith('/api/ordenes-trabajo/90/finalizacion', expect.objectContaining({ method: 'POST', body: { kilometraje: 0, observacionesMecanico: '', aceite: '5W30', filtros: ['ACEITE'] } }))
  expect(detalle.text()).toContain('Finalizado')
  expect(boton(detalle, 'Editar planificación')).toBeUndefined()
  expect(boton(detalle, 'Finalizar orden')).toBeUndefined()
})

test('la baja exige motivo y confirma el estado con la respuesta del servidor', async () => {
  $fetch.mockResolvedValueOnce(respuesta({ estado: 'EN_PROCESO' }))
    .mockResolvedValueOnce(respuesta({ estado: 'CANCELADO', motivoCancelacion: 'Cliente desiste' }))
  const detalle = mount(Detalle, { props: { id: 90 }, global: { stubs: { CommonModalDialog: caja } } })
  await flushPromises()
  await boton(detalle, 'Dar de baja').trigger('click')
  const form = detalle.findAll('form').find(f => f.text().includes('Confirmar baja'))
  await form.get('textarea').setValue('   ')
  await form.trigger('submit')
  expect($fetch).toHaveBeenCalledTimes(1)
  expect(detalle.get('[role=alert]').text()).toContain('motivo')
  await form.get('textarea').setValue(' Cliente desiste ')
  await form.trigger('submit')
  await flushPromises()
  expect($fetch).toHaveBeenLastCalledWith('/api/ordenes-trabajo/90/cancelacion', expect.objectContaining({ method: 'POST', body: { motivo: 'Cliente desiste' } }))
  expect(detalle.text()).toContain('Cancelado')
})

test('un fallo al completar una tarea conserva su casilla en el estado guardado', async () => {
  $fetch.mockResolvedValueOnce(respuesta({ estado: 'EN_PROCESO' })).mockRejectedValueOnce({ statusCode: 400, data: { detail: 'La orden está cerrada.' } })
  const detalle = mount(Detalle, { props: { id: 90 }, global: { stubs: { CommonModalDialog: caja } } })
  await flushPromises()
  await detalle.get('.task-list input').setValue(true)
  await flushPromises()
  expect(detalle.get('.task-list input').element.checked).toBe(false)
  expect(detalle.emitted('updated')).toBeUndefined()
})

test('descarga fotos con JWT y libera las URLs al cerrar', async () => {
  URL.createObjectURL = jest.fn(() => 'blob:foto')
  URL.revokeObjectURL = jest.fn()
  $fetch.mockResolvedValueOnce(respuesta({}, { fotos: [{ id: 12, sector: 'Motor', fecha: '2026-11-20T12:00:00Z' }] })).mockResolvedValueOnce(new Blob(['imagen']))
  const detalle = mount(Detalle, { props: { id: 90 }, global: { stubs: { CommonModalDialog: caja } } })
  await flushPromises()
  expect($fetch).toHaveBeenLastCalledWith('/api/ordenes-trabajo/90/fotos/12', expect.objectContaining({ headers: auth.authorization.value, responseType: 'blob' }))
  expect(detalle.get('img').attributes('src')).toBe('blob:foto')
  detalle.unmount()
  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:foto')
})
