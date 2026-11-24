import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
jest.mock('../../app/utils/presupuestoApi', () => ({ paginasPresupuestos: jest.fn() }))
const Factura = require('../../app/components/facturacion/ModalComprobanteManual.vue').default
const { paginasPresupuestos } = require('../../app/utils/presupuestoApi')

test('factura selecciona cliente y vehículo en campos separados y permite quitar ambas elecciones', async () => {
  const originales = { useAuthApi: globalThis.useAuthApi, useBusquedaTitulares: globalThis.useBusquedaTitulares, $fetch: globalThis.$fetch }
  globalThis.useAuthApi = () => ({ authorization: ref({ Authorization: 'test' }), user: ref({ tallerId: 1 }) })
  globalThis.useBusquedaTitulares = () => ({
    resultados: ref([{ id: 1, name: 'Cliente de prueba', doc: '123', active: true }]),
    cargando: ref(false), error: ref(''),
  })
  globalThis.$fetch = jest.fn(async (url) => {
    if (url === '/api/condiciones-iva') return [{ id: 1, nombre: 'Responsable Monotributo' }]
    if (url.startsWith('/api/talleres/')) return { condicionIva: { id: 1 } }
    if (url === '/api/vehiculos') return { contenido: [{ id: 2, clienteId: 1, patente: 'ABC-123', marca: 'Ford', modelo: 'Ka', clienteNombre: 'Cliente de prueba' }] }
    return { id: 1, nombre: 'Cliente de prueba', documento: '123', activo: true, condicionIvaId: 1 }
  })
  paginasPresupuestos.mockImplementation(async (url) => url === '/api/vehiculos'
    ? [{ id: 2, clienteId: 1, patente: 'ABC-123', marca: 'Ford', modelo: 'Ka' }]
    : [])
  try {
    const wrapper = mount(Factura, { props: { open: false }, global: { stubs: { CommonEditorRepuestos: true } } })
    await wrapper.setProps({ open: true })
    await flushPromises()
    expect(wrapper.get('#factura-vehiculo').element.disabled).toBe(false)
    expect(wrapper.get('fieldset').element.disabled).toBe(true)
    expect(wrapper.get('fieldset').attributes('inert')).toBeDefined()
    expect(wrapper.get('button[type="submit"]').element.disabled).toBe(true)
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
    expect(wrapper.get('[aria-label="Quitar vehículo"]').exists()).toBe(true)
    expect(wrapper.get('fieldset').element.disabled).toBe(false)
    expect(wrapper.get('button[type="submit"]').element.disabled).toBe(false)
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
    const condicion = wrapper.findAll('label').find(label => label.text().includes('Condición de IVA del cliente')).get('select')
    expect(condicion.element.value).toBe('1')
  } finally {
    Object.assign(globalThis, originales)
  }
})
