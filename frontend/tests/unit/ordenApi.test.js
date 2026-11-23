import { ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import Panel from '../../app/components/ordenes/PanelApi.vue'
import Formulario from '../../app/components/ordenes/FormularioApi.vue'
import Detalle from '../../app/components/ordenes/DetalleApi.vue'
import EditorRepuestos from '../../app/components/common/EditorRepuestos.vue'
import ResumenImportes from '../../app/components/ordenes/ResumenImportes.vue'
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
    useWorkshopDay: () => ({ today: ref('2026-11-23') }),
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
  $fetch.mockImplementation((url, { query }) => {
    if (query.tamano !== 20) return Promise.resolve({ contenido: [], totalElementos: 0, totalPaginas: 0 })
    if (!query.q) return new Promise(resolve => { resolver = resolve })
    return Promise.resolve({ contenido: [cabecera({ descripcion: 'Resultado nuevo' })], totalElementos: 1, totalPaginas: 1 })
  })
  const panel = mount(Panel)
  await panel.get('[aria-label="Buscar órdenes"]').setValue('nuevo')
  await flushPromises()
  resolver({ contenido: [cabecera({ descripcion: 'Resultado viejo' })], totalElementos: 1, totalPaginas: 1 })
  await flushPromises()
  expect(panel.text()).toContain('Resultado nuevo')
  expect(panel.text()).not.toContain('Resultado viejo')
})

test('confirmar una orden vuelve al listado actualizado sin abrir el detalle', async () => {
  $fetch.mockResolvedValue({ contenido: [cabecera()], totalElementos: 1, totalPaginas: 1 })
  const panel = mount(Panel, { global: { stubs: { OrdenesFormularioApi: true, OrdenesDetalleApi: true } } })
  await flushPromises()
  await boton(panel, 'Nueva orden').trigger('click')
  const nueva = cabecera({ id: 91, descripcion: 'Orden recién creada' })
  $fetch.mockResolvedValue({ contenido: [nueva], totalElementos: 1, totalPaginas: 1 })
  panel.getComponent({ name: 'OrdenesFormularioApi' }).vm.$emit('saved', nueva)
  await flushPromises()
  expect(panel.find('ordenes-formulario-api-stub').exists()).toBe(false)
  expect(panel.find('ordenes-detalle-api-stub').exists()).toBe(false)
  expect(panel.get('.order-card').text()).toContain('Orden recién creada')
})

test.each([null, 0])('las tarjetas conservan el diseño original con datos API y kilometraje %s', async kilometraje => {
  $fetch.mockResolvedValue({ contenido: [cabecera({ kilometraje, indicaciones: 'Revisar al ingresar', version: '2.8 TDI' })], totalElementos: 1, totalPaginas: 1 })
  const panel = mount(Panel, { global: { stubs: { CommonBrandLogo: true, OrdenesDetalleApi: true } } })
  await flushPromises()
  const card = panel.get('.order-card')
  expect(card.get('.order-card-brand').attributes('brand')).toBe('Toyota')
  expect(card.get('.order-vehicle-info').text()).toContain('Ana Pérez')
  expect(card.get('.service-preview').text()).toContain('Revisión de frenos')
  expect(card.get('.mechanic').text()).toBe('María López')
  expect(card.get('.order-meta-info').text()).toContain('Observaciones')
  expect(card.findAll('.meta-tag').some(tag => tag.text() === '0 km')).toBe(kilometraje === 0)
  await card.trigger('click')
  expect(panel.get('ordenes-detalle-api-stub').attributes('id')).toBe('90')
  expect(useDatabase).not.toHaveBeenCalled()
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

test('el resumen suma todas las páginas del mes y los estados se filtran en el servidor', async () => {
  $fetch.mockImplementation((url, { query }) => {
    if (query.tamano === 100) return Promise.resolve({ contenido: query.pagina === 0 ? [cabecera({ tiposTrabajo: ['SERVICE', 'DISTRIBUCION'] })] : [cabecera({ id: 91, tiposTrabajo: ['OTRO'] })], totalElementos: 2, totalPaginas: 2 })
    return Promise.resolve({ contenido: [], totalElementos: query.estado === 'EN_ESPERA' ? 4 : 0, totalPaginas: 0 })
  })
  const panel = mount(Panel)
  await flushPromises()
  expect(panel.findAll('.monthly-summary strong').map(el => el.text())).toEqual(['2', '1', '1', '1'])
  const espera = panel.findAll('.status-filters button').find(el => el.text().startsWith('En espera'))
  expect(espera.get('.status-count').text()).toBe('4')
  await espera.trigger('click')
  await flushPromises()
  expect($fetch).toHaveBeenLastCalledWith('/api/ordenes-trabajo', expect.objectContaining({ query: expect.objectContaining({ estado: 'EN_ESPERA', pagina: 0, tamano: 20 }) }))
  expect($fetch).toHaveBeenCalledWith('/api/ordenes-trabajo', expect.objectContaining({ query: expect.objectContaining({ desde: '2026-11-01', hasta: '2026-11-30', pagina: 1 }) }))
  expect(panel.findAll('.monthly-summary strong').map(el => el.text())).toEqual(['2', '1', '1', '1'])
})

test('un error del resumen deja los contadores sin inventar valores y permite reintentar', async () => {
  $fetch.mockImplementation((url, { query }) => query.tamano === 100 ? Promise.reject(new Error('Sin conexión')) : Promise.resolve({ contenido: [], totalElementos: 0, totalPaginas: 0 }))
  const panel = mount(Panel)
  await flushPromises()
  expect(panel.findAll('.monthly-summary strong').map(el => el.text())).toEqual(['—', '—', '—', '—'])
  expect(panel.get('[role=alert]').text()).toContain('contadores')
  $fetch.mockResolvedValue({ contenido: [], totalElementos: 0, totalPaginas: 0 })
  await boton(panel, 'Reintentar contadores').trigger('click')
  await flushPromises()
  expect(panel.findAll('.monthly-summary strong').map(el => el.text())).toEqual(['0', '0', '0', '0'])
})

test('editar una orden en proceso agrega repuestos con el editor común y conserva el borrador si falla', async () => {
  let intentos = 0
  const data = respuesta({ estado: 'EN_PROCESO', inicioFecha: '2026-11-20T12:00:00Z' })
  $fetch.mockImplementation((url, options = {}) => {
    if (url === '/api/mecanicos') return Promise.resolve([])
    if (url === '/api/repuestos') return Promise.resolve({ contenido: [{ id: 8, descripcion: 'Aceite', marca: 'Marca', precioPublico: 10, activo: true, stock: 5 }], totalPaginas: 1 })
    if (url.endsWith('/repuestos')) {
      if (++intentos === 1) return Promise.reject({ statusCode: 400, data: { detail: 'Stock insuficiente.' } })
      return Promise.resolve({ ...data, items: [{ id: 800, repuestoId: 8, cantidad: 1.5, descripcion: 'Aceite' }] })
    }
    return Promise.resolve(data)
  })
  const form = mount(Formulario, { props: { orden: data.orden }, global: { components: { CommonEditorRepuestos: EditorRepuestos } } })
  await flushPromises()
  const editor = form.getComponent(EditorRepuestos)
  expect(editor.props('parts')[0].id).toBe(8)
  editor.vm.$emit('update:modelValue', [{ key: 1, partId: 8, customName: 'Aceite', searchQuery: 'Aceite', quantity: 1.5, unitPrice: 10 }])
  await form.get('form').trigger('submit')
  await flushPromises()
  expect(form.get('[role=alert]').text()).toContain('Stock insuficiente')
  expect(editor.props('modelValue')).toHaveLength(1)
  expect(form.emitted('saved')).toBeUndefined()
  await form.get('form').trigger('submit')
  await flushPromises()
  expect($fetch).toHaveBeenCalledWith('/api/ordenes-trabajo/90/trabajo', expect.objectContaining({ method: 'PUT' }))
  expect($fetch).toHaveBeenLastCalledWith('/api/ordenes-trabajo/90/repuestos', expect.objectContaining({ method: 'POST', body: { items: [{ repuestoId: 8, cantidad: 1.5 }] } }))
  expect($fetch.mock.calls.some(([url, options]) => url === '/api/ordenes-trabajo' && options.method === 'POST')).toBe(false)
  expect(form.emitted('saved')).toHaveLength(1)
})

test('el alta usa vehículo y mecánico de la API y conserva el formulario ante un error', async () => {
  $fetch.mockImplementation((url, options = {}) => {
    if (url === '/api/mecanicos') return Promise.resolve([{ id: 7, nombre: 'María', apellido: 'López' }])
    if (url === '/api/vehiculos') return Promise.resolve({ contenido: [{ id: 34, patente: 'ABC123', marca: 'Toyota', modelo: 'Hilux', clienteNombre: 'Ana Pérez' }], totalPaginas: 1 })
    if (url === '/api/presupuestos') return Promise.resolve({ contenido: [], totalPaginas: 0 })
    if (url === '/api/repuestos') return Promise.resolve({ contenido: [], totalPaginas: 0 })
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
  await flushPromises()
  expect($fetch).toHaveBeenCalledWith('/api/repuestos', expect.objectContaining({ query: expect.objectContaining({ vehiculoId: 34, activo: true }) }))
  await boton(form, 'Otro trabajo').trigger('click')
  expect(boton(form, 'Otro trabajo').attributes('aria-pressed')).toBe('true')
  await boton(form, 'Service').trigger('click')
  expect(boton(form, 'Otro trabajo').attributes('aria-pressed')).toBe('true')
  await boton(form, 'Service').trigger('click')
  expect(boton(form, 'Service').attributes('aria-pressed')).toBe('false')
  await form.get('input[maxlength="2000"]').setValue('Revisión de frenos')
  await form.findAll('select')[1].setValue(7)
  await form.get('form').trigger('submit')
  expect(form.get('.order-preview').text()).toContain('Revisión de frenos')
  expect($fetch.mock.calls.some(([, options]) => options.method === 'POST')).toBe(false)
  await form.get('form').trigger('submit')
  await flushPromises()
  expect($fetch).toHaveBeenLastCalledWith('/api/ordenes-trabajo', expect.objectContaining({ method: 'POST', headers: auth.authorization.value, body: expect.objectContaining({ vehiculoId: 34, mecanicoId: 7, presupuestoId: null }) }))
  expect(form.get('[role=alert]').text()).toContain('inactivo')
  expect(form.get('input[maxlength="2000"]').element.value).toBe('Revisión de frenos')
  expect(form.emitted('saved')).toBeUndefined()
  await form.get('[aria-label="Quitar vehículo seleccionado"]').trigger('click')
  expect(form.get('.dependent-fields').element.disabled).toBe(true)
  expect(form.get('button[type=submit]').element.disabled).toBe(true)
})

test('editar planificación conserva los vínculos y bloquea envíos duplicados', async () => {
  let resolver
  $fetch.mockImplementation((url, options) => {
    if (url === '/api/mecanicos') return Promise.resolve([{ id: 7, nombre: 'María', apellido: 'López' }])
    if (url === '/api/repuestos') return Promise.resolve({ contenido: [], totalPaginas: 0 })
    if (options.method === 'PUT') return new Promise(resolve => { resolver = resolve })
    return Promise.resolve(respuesta({ presupuestoId: 63 }))
  })
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

test('el resumen aparece solo en la vista previa e incluye repuestos vinculados y nuevos, mano de obra e IVA', async () => {
  const data = respuesta({ presupuestoId: 63, manoObra: 100, porcentajeIva: 21 }, { totalRepuestos: 250, subtotal: 350, montoIva: 73.5, total: 423.5 })
  $fetch.mockImplementation(url => {
    if (url === '/api/mecanicos') return Promise.resolve([{ id: 7, nombre: 'María', apellido: 'López' }])
    if (url === '/api/repuestos') return Promise.resolve({ contenido: [], totalPaginas: 0 })
    if (url === '/api/vehiculos') return Promise.resolve({ contenido: [{ id: 34, patente: 'ABC123', marca: 'Toyota', modelo: 'Hilux' }], totalPaginas: 1 })
    if (url === '/api/presupuestos') return Promise.resolve({ contenido: [{ id: 63, descripcion: 'Service', estado: 'ACEPTADO', ordenId: null }], totalPaginas: 1 })
    if (url === '/api/presupuestos/63') return Promise.resolve({ id: 63, descripcion: 'Service', manoObra: 100, porcentajeIva: 21, totalRepuestos: 250, tiposTrabajo: ['SERVICE'], turnoId: null })
    return Promise.resolve(data)
  })
  const form = mount(Formulario, { props: { orden: null }, global: { components: { CommonEditorRepuestos: EditorRepuestos } } })
  await flushPromises()
    await form.get('[aria-label="Buscar vehículo para la orden"]').setValue('ABC123')
    await flushPromises()
    await form.get('.vehicle-result').trigger('click')
    await flushPromises()
    await form.findAll('select')[0].setValue(63)
    await flushPromises()
  await form.findAll('select')[1].setValue(7)
  expect(form.findComponent(ResumenImportes).exists()).toBe(false)
  await form.get('form').trigger('submit')
  expect(form.getComponent(ResumenImportes).props()).toMatchObject({ repuestos: 250, manoObra: 100, subtotal: 350, iva: 73.5, total: 423.5 })
  await boton(form, 'Volver a editar').trigger('click')
  expect(form.findComponent(ResumenImportes).exists()).toBe(false)
  form.getComponent(EditorRepuestos).vm.$emit('update:modelValue', [{ key: 1, partId: 'custom', customName: 'Insumo', searchQuery: 'Personalizado', quantity: 2, unitPrice: 25 }])
  await flushPromises()
  await form.get('form').trigger('submit')
  expect(form.getComponent(ResumenImportes).props()).toMatchObject({ repuestos: 300, manoObra: 100, subtotal: 400, iva: 84, total: 484 })
})

test('el detalle muestra los importes confirmados por la API y los oculta al mecánico', async () => {
  $fetch.mockResolvedValue(respuesta({ manoObra: 100, porcentajeIva: 21 }, { totalRepuestos: 250, subtotal: 350, montoIva: 73.5, total: 423.5 }))
  const detalle = mount(Detalle, { props: { id: 90 }, global: { stubs: { CommonModalDialog: caja } } })
  await flushPromises()
  expect(detalle.getComponent(ResumenImportes).props()).toMatchObject({ repuestos: 250, manoObra: 100, subtotal: 350, iva: 73.5, total: 423.5 })
  auth.user.value.rol = 'MECANICO'
  await flushPromises()
  expect(detalle.findComponent(ResumenImportes).exists()).toBe(false)
})

test('crear una orden envía los materiales juntos y conserva el borrador ante un rechazo', async () => {
  let fallar = true
  $fetch.mockImplementation((url, options = {}) => {
    if (url === '/api/mecanicos') return Promise.resolve([{ id: 7, nombre: 'María', apellido: 'López' }])
    if (url === '/api/vehiculos') return Promise.resolve({ contenido: [{ id: 34, patente: 'ABC123', marca: 'Toyota', modelo: 'Hilux', clienteNombre: 'Ana Pérez' }], totalPaginas: 1 })
    if (url === '/api/repuestos' || url === '/api/presupuestos') return Promise.resolve({ contenido: [], totalPaginas: 0 })
    if (url === '/api/ordenes-trabajo' && fallar) { fallar = false; return Promise.reject({ statusCode: 400, data: { detail: 'Revisá los materiales.' } }) }
    return Promise.resolve(respuesta())
  })
  const form = mount(Formulario, { props: { orden: null }, global: { components: { CommonEditorRepuestos: EditorRepuestos } } })
  await flushPromises()
  await form.get('[aria-label="Buscar vehículo para la orden"]').setValue('ABC123')
  await flushPromises()
  await form.get('.vehicle-result').trigger('click')
  await boton(form, 'Otro trabajo').trigger('click')
  await form.get('input[maxlength="2000"]').setValue('Revisión')
  await form.findAll('select')[1].setValue(7)
  await form.get('input[max="999999999999.99"]').setValue(100)
  form.getComponent(EditorRepuestos).vm.$emit('update:modelValue', [{ key: 1, partId: 'custom', customName: 'Abrazadera', searchQuery: 'Personalizado', quantity: 2, unitPrice: 150 }])
  await form.get('form').trigger('submit')
  expect(form.get('.order-preview').text()).toContain('2 × Abrazadera')
  expect(form.get('.order-preview').get('.order-total').isVisible()).toBe(true)
  expect(form.get('.order-preview').get('.order-total dd').text()).toBe(useHelpers().money(484))
  expect(form.findAllComponents(ResumenImportes)).toHaveLength(1)
  expect(form.emitted('saved')).toBeUndefined()
  await form.get('form').trigger('submit')
  await flushPromises()
  expect(form.get('[role=alert]').text()).toContain('Revisá los materiales')
  expect(form.emitted('saved')).toBeUndefined()
  expect(form.getComponent(EditorRepuestos).props('modelValue')).toHaveLength(1)
  await form.get('form').trigger('submit')
  await form.get('form').trigger('submit')
  await flushPromises()
  expect($fetch.mock.calls.filter(([url, options]) => url === '/api/ordenes-trabajo' && options.method === 'POST')).toHaveLength(2)
  expect($fetch.mock.calls.some(([url]) => url.endsWith('/90/repuestos'))).toBe(false)
  expect($fetch).toHaveBeenLastCalledWith('/api/ordenes-trabajo', expect.objectContaining({ method: 'POST', body: expect.objectContaining({ items: [{ repuestoId: null, descripcion: 'Abrazadera', cantidad: 2, precioUnitario: 150 }] }) }))
  expect(form.emitted('saved')).toHaveLength(1)
})

test('stock insuficiente conserva el estado y muestra el error del servidor', async () => {
  $fetch.mockResolvedValueOnce(respuesta()).mockRejectedValueOnce({ statusCode: 400, data: { detail: 'Stock insuficiente.' } })
  const detalle = mount(Detalle, { props: { id: 90 }, global: { stubs: { CommonModalDialog: caja } } })
  await flushPromises()
  await boton(detalle, 'Iniciar').trigger('click')
  await flushPromises()
  expect($fetch).toHaveBeenLastCalledWith('/api/ordenes-trabajo/90/inicio', expect.objectContaining({ method: 'POST', headers: auth.authorization.value }))
  expect(detalle.get('[role=alert]').text()).toContain('Stock insuficiente')
  expect(detalle.text()).toContain('En espera')
  expect(detalle.emitted('updated')).toBeUndefined()
})

test('el alta bloquea antes de la vista previa si un repuesto supera el stock y acepta el saldo exacto', async () => {
  $fetch.mockImplementation((url, options = {}) => {
    if (url === '/api/mecanicos') return Promise.resolve([{ id: 7, nombre: 'María', apellido: 'López' }])
    if (url === '/api/vehiculos') return Promise.resolve({ contenido: [{ id: 34, patente: 'ABC123', marca: 'Toyota', modelo: 'Hilux' }], totalPaginas: 1 })
    if (url === '/api/repuestos') return Promise.resolve({ contenido: [{ id: 8, descripcion: 'Filtro', activo: true, precioPublico: 10, stock: 1.5 }], totalPaginas: 1 })
    if (url === '/api/presupuestos') return Promise.resolve({ contenido: [], totalPaginas: 0 })
    return Promise.resolve(respuesta())
  })
  const form = mount(Formulario, { props: { orden: null }, global: { components: { CommonEditorRepuestos: EditorRepuestos } } })
  await flushPromises()
  await form.get('[aria-label="Buscar vehículo para la orden"]').setValue('ABC123')
  await flushPromises()
  await form.get('.vehicle-result').trigger('click')
  await boton(form, 'Otro trabajo').trigger('click')
  await form.get('input[maxlength="2000"]').setValue('Revisión')
  await form.findAll('select')[1].setValue(7)
  const editor = form.getComponent(EditorRepuestos)
  const item = { key: 1, partId: 8, customName: 'Filtro', searchQuery: 'Filtro', quantity: 1.501, unitPrice: 10 }
  editor.vm.$emit('update:modelValue', [item])
  await form.get('form').trigger('submit')
  expect(form.get('[role=alert]').text()).toContain('No hay stock suficiente de Filtro')
  expect(form.find('.order-preview').exists()).toBe(false)
  expect($fetch.mock.calls.some(([, options]) => options.method === 'POST')).toBe(false)
  editor.vm.$emit('update:modelValue', [{ ...item, quantity: 1.5 }])
  await form.get('form').trigger('submit')
  expect(form.get('.order-preview').text()).toContain('1.5 × Filtro')
  await form.get('form').trigger('submit')
  await flushPromises()
  expect($fetch).toHaveBeenLastCalledWith('/api/ordenes-trabajo', expect.objectContaining({ body: expect.objectContaining({ items: [{ repuestoId: 8, cantidad: 1.5 }] }) }))
})

test('las cantidades usan el ID del renglón y confirman los valores desde la API', async () => {
  const data = respuesta({ estado: 'EN_PROCESO' }, { items: [{ id: 810, repuestoId: 2, descripcion: 'Aceite', cantidad: 1.5, precioUnitario: 10, subtotal: 15, consumido: true }] })
  $fetch.mockImplementation(url => {
    if (url === '/api/mecanicos') return Promise.resolve([])
    if (url === '/api/repuestos') return Promise.resolve({ contenido: [], totalPaginas: 0 })
    return Promise.resolve(data)
  })
  const detalle = mount(Formulario, { props: { orden: data.orden } })
  await flushPromises()
  await detalle.get('[aria-label="Cantidad de Aceite"]').setValue(2.125)
  expect(detalle.find('[aria-label="Guardar cantidad de Aceite"]').exists()).toBe(false)
  await detalle.get('[aria-label="Cantidad de Aceite"]').trigger('change')
  await flushPromises()
  expect($fetch).toHaveBeenLastCalledWith('/api/ordenes-trabajo/90/repuestos/810', expect.objectContaining({ method: 'PATCH', body: { cantidad: 2.125 } }))
  expect(detalle.get('[aria-label="Cantidad de Aceite"]').element.value).toBe('1.5')
})

test('el detalle muestra una tabla de repuestos de solo lectura y ordena los botones del pie', async () => {
  $fetch.mockResolvedValue(respuesta({}, { items: [{ id: 810, codigoOem: 'A-1', descripcion: 'Aceite', cantidad: 1.5, precioUnitario: 10, subtotal: 15 }] }))
  const detalle = mount(Detalle, { props: { id: 90 }, global: { stubs: { CommonModalDialog: caja } } })
  await flushPromises()
  const tabla = detalle.get('.order-parts-table')
  expect(tabla.text()).toContain('Aceite')
  expect(tabla.text()).toContain('1,5')
  expect(tabla.find('input, button').exists()).toBe(false)
  expect(detalle.findAll('.modal-footer button').map(b => b.text())).toEqual(['Dar de baja', 'Editar', 'Iniciar'])
  await boton(detalle, 'Editar').trigger('click')
  expect(detalle.emitted('edit')[0][0].id).toBe(90)
})

test('el mecánico edita materiales sin consultar personal ni poder quitar o reducir cantidades', async () => {
  auth.user.value.rol = 'MECANICO'
  const data = respuesta({ estado: 'EN_PROCESO' }, { items: [{ id: 810, repuestoId: 2, descripcion: 'Aceite', cantidad: 1.5, precioUnitario: 10, subtotal: 15 }] })
  $fetch.mockImplementation(url => url === '/api/repuestos' ? Promise.resolve({ contenido: [], totalPaginas: 0 }) : Promise.resolve(data))
  const form = mount(Formulario, { props: { orden: data.orden }, global: { components: { CommonEditorRepuestos: EditorRepuestos } } })
  await flushPromises()
  expect($fetch.mock.calls.some(([url]) => url === '/api/mecanicos')).toBe(false)
  expect(form.find('[aria-label="Eliminar repuesto Aceite"]').exists()).toBe(false)
  expect(form.getComponent(EditorRepuestos).props('allowCustom')).toBe(false)
  await form.get('[aria-label="Cantidad de Aceite"]').setValue(1)
  await form.get('[aria-label="Cantidad de Aceite"]').trigger('change')
  expect($fetch.mock.calls.some(([, options]) => options.method === 'PATCH')).toBe(false)
  expect(form.get('[role=alert]').text()).toContain('cantidad')
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
  expect(detalle.find('.detail-modal').exists()).toBe(false)
  expect(detalle.find('.completion-dialog').exists()).toBe(true)
  await boton(detalle, 'Cancelar').trigger('click')
  expect(detalle.find('.detail-modal').exists()).toBe(true)
  expect($fetch.mock.calls.some(([, options]) => options.method === 'POST')).toBe(false)
  await boton(detalle, 'Finalizar orden').trigger('click')
  const form = detalle.findAll('form').find(f => f.text().includes('Confirmar finalización'))
  await form.get('input[type=number]').setValue(0)
  await form.get('input[maxlength="120"]').setValue('5W30')
  await form.get('input[value=ACEITE]').setValue(true)
  await form.trigger('submit')
  await flushPromises()
  expect($fetch).toHaveBeenLastCalledWith('/api/ordenes-trabajo/90/finalizacion', expect.objectContaining({ method: 'POST', body: { kilometraje: 0, observacionesMecanico: '', aceite: '5W30', filtros: ['ACEITE'] } }))
  expect(detalle.text()).toContain('Finalizado')
  expect(boton(detalle, 'Editar')).toBeUndefined()
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
