import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
jest.mock('../../app/utils/presupuestoApi', () => ({ paginasPresupuestos: jest.fn() }))
const Factura = require('../../app/components/facturacion/ModalComprobanteManual.vue').default
const { paginasPresupuestos } = require('../../app/utils/presupuestoApi')
const EditorRepuestos = require('../../app/components/common/EditorRepuestos.vue').default

test('factura selecciona cliente y vehículo en campos separados y permite quitar ambas elecciones', async () => {
  const originales = { useAuthApi: globalThis.useAuthApi, useBusquedaTitulares: globalThis.useBusquedaTitulares, $fetch: globalThis.$fetch }
  globalThis.useAuthApi = () => ({ authorization: ref({ Authorization: 'test' }), user: ref({ tallerId: 1 }) })
  globalThis.useBusquedaTitulares = () => ({
    resultados: ref([{ id: 1, name: 'Cliente de prueba', doc: '123', active: true }]),
    cargando: ref(false), error: ref(''),
  })
  globalThis.$fetch = jest.fn(async (url) => {
    if (url === '/api/condiciones-iva') return [{ id: 1, nombre: 'Responsable Monotributo' }]
    if (url.startsWith('/api/talleres/')) return { condicionIva: null }
    if (url === '/api/vehiculos') return { contenido: [{ id: 2, clienteId: 1, patente: 'ABC-123', marca: 'Ford', modelo: 'Ka', clienteNombre: 'Cliente de prueba' }] }
    return { id: 1, nombre: 'Cliente de prueba', documento: '123', activo: true, condicionIvaId: 1 }
  })
  paginasPresupuestos.mockImplementation(async (url) => url === '/api/vehiculos'
    ? [{ id: 2, clienteId: 1, patente: 'ABC-123', marca: 'Ford', modelo: 'Ka' }]
    : [])
  try {
    const wrapper = mount(Factura, { props: { open: false }, global: { stubs: { CommonEditorRepuestos: true, CommonBrandLogo: true } } })
    await wrapper.setProps({ open: true })
    await flushPromises()
    expect(wrapper.get('#factura-vehiculo').element.disabled).toBe(false)
    expect(wrapper.get('fieldset').element.disabled).toBe(true)
    expect(wrapper.get('fieldset').attributes('inert')).toBeDefined()
    expect(wrapper.get('button[type="submit"]').element.disabled).toBe(true)
    expect(paginasPresupuestos.mock.calls.some(([url]) => url === '/api/repuestos')).toBe(false)
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('guardar')).toBeUndefined()
    await wrapper.get('#factura-cliente').setValue('Cliente')
    await wrapper.findAll('.target-result-item')[0].trigger('click')
    await flushPromises()
    expect(paginasPresupuestos).toHaveBeenCalledWith('/api/vehiculos', expect.anything(), expect.anything(), { clienteId: 1, activo: true })
    expect(wrapper.get('fieldset').element.disabled).toBe(true)
    await wrapper.get('#factura-vehiculo').trigger('focus')
    await wrapper.get('#factura-vehiculo').setValue('ABC')
    await wrapper.get('.target-result-item').trigger('click')
    await flushPromises()
    expect(wrapper.get('[aria-label="Quitar vehículo"]').exists()).toBe(true)
    expect(wrapper.get('fieldset').element.disabled).toBe(false)
    expect(wrapper.get('button[type="submit"]').element.disabled).toBe(false)
    expect(paginasPresupuestos).toHaveBeenCalledWith('/api/repuestos', expect.anything(), expect.anything(), { activo: true, vehiculoId: 2 })
    await wrapper.get('[aria-label="Quitar vehículo"]').trigger('click')
    expect(wrapper.get('#factura-vehiculo').element.value).toBe('')
    expect(wrapper.get('fieldset').element.disabled).toBe(true)
    await wrapper.get('#factura-vehiculo').trigger('focus')
    await wrapper.get('.target-result-item').trigger('click')
    await wrapper.get('[aria-label="Quitar cliente"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[aria-label="Quitar vehículo"]').exists()).toBe(false)
    expect(wrapper.get('#factura-vehiculo').element.disabled).toBe(false)
    expect(wrapper.get('#factura-cliente').element.value).toBe('')
    expect(wrapper.get('fieldset').element.disabled).toBe(true)
    expect(wrapper.get('button[type="submit"]').element.disabled).toBe(true)
    await wrapper.get('#factura-cliente').trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('guardar')).toBeUndefined()
    await wrapper.get('#factura-vehiculo').trigger('focus')
    await wrapper.get('#factura-vehiculo').setValue('ABC')
    await new Promise(resolve => setTimeout(resolve, 320))
    await flushPromises()
    expect($fetch).toHaveBeenCalledWith('/api/vehiculos', expect.objectContaining({ query: { q: 'ABC', activo: true, tamano: 20 } }))
    expect(wrapper.get('.target-result-item').text()).toContain('Cliente de prueba')
    await wrapper.get('.target-result-item').trigger('click')
    await flushPromises()
    expect(wrapper.get('[aria-label="Quitar cliente"]').element.parentElement.textContent).toContain('Cliente de prueba')
    expect(wrapper.get('[aria-label="Quitar vehículo"]').exists()).toBe(true)
    expect(wrapper.get('fieldset').element.disabled).toBe(false)
    expect(wrapper.findAll('label').find(label => label.text().includes('Condición de IVA del cliente')).get('select').element.value).toBe('1')
    const emisor = wrapper.findAll('label').find(label => label.text().includes('Condición de IVA del taller')).get('select')
    expect(emisor.element.selectedIndex).toBe(0)
    await emisor.setValue('1')
    await wrapper.get('input[required][placeholder]').setValue('Service')
    await wrapper.get('input[type="number"]').setValue('100')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('guardar')[0][0]).toEqual(expect.objectContaining({ condicionIvaEmisorId: 1, condicionIvaReceptorId: 1 }))
  } finally {
    Object.assign(globalThis, originales)
  }
})

test('facturación filtra por el vehículo fijo y conserva históricos fuera de las sugerencias', async () => {
  const originales = { useAuthApi: globalThis.useAuthApi, useBusquedaTitulares: globalThis.useBusquedaTitulares, $fetch: globalThis.$fetch }
  globalThis.useAuthApi = () => ({ authorization: ref({ Authorization: 'test' }), user: ref({ tallerId: 1 }) })
  globalThis.useBusquedaTitulares = () => ({ resultados: ref([]), cargando: ref(false), error: ref('') })
  globalThis.$fetch = jest.fn(async url => {
    if (url === '/api/repuestos/7') return { id: 7, descripcion: 'Histórico', activo: true }
    if (url === '/api/condiciones-iva') return []
    return { condicionIva: null }
  })
  paginasPresupuestos.mockReset()
  paginasPresupuestos.mockResolvedValue([{ id: 8, descripcion: 'Compatible', activo: true }])
  try {
    const wrapper = mount(Factura, { props: { open: false, comprobante: {
      id: 10, vehiculoId: 2, items: [{ repuestoId: 7, descripcion: 'Histórico', cantidad: 1, precioUnitario: 80 }],
    } }, global: { stubs: { CommonEditorRepuestos: EditorRepuestos, CommonBrandLogo: true } } })
    await wrapper.setProps({ open: true })
    await flushPromises()
    const editor = wrapper.findComponent(EditorRepuestos)
    expect(paginasPresupuestos).toHaveBeenCalledWith('/api/repuestos', expect.anything(), expect.anything(), { activo: true, vehiculoId: 2 })
    expect(editor.props('searchParts').map(p => p.id)).toEqual([8])
    expect(editor.props('parts').map(p => p.id)).toEqual([8, 7])
    expect(wrapper.get('.parts-table').text()).toContain('Histórico')
    expect(editor.props('modelValue')[0].unitPrice).toBe(80)
  } finally { Object.assign(globalThis, originales) }
})
