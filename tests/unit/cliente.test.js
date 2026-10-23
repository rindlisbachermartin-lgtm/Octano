import { mount } from '@vue/test-utils'
import Cliente from '../../app/components/clientes/ModalFormularioCliente.vue'
import { field, mockDatabase } from './helpers'

let db
beforeEach(() => { ({ db } = mockDatabase()) })

async function validForm(props = {}) {
  const wrapper = mount(Cliente, { props: { open: true, ...props } })
  await field(wrapper, 'Nombre o razón').setValue('  María López  ')
  await field(wrapper, 'DNI /').setValue('34567890')
  await field(wrapper, 'Teléfono').setValue('1123456789')
  return wrapper
}

test('exige los campos obligatorios y no emite save', async () => {
  const wrapper = mount(Cliente, { props: { open: true } })
  await wrapper.get('form').trigger('submit')
  expect(wrapper.get('[role="alert"]').text()).toContain('campos obligatorios')
  expect(wrapper.emitted('save')).toBeUndefined()
})

test.each([
  ['Nombre o razón', 'A', 'entre 2 y 90'],
  ['DNI /', '123', 'DNI'],
  ['Teléfono', '123', 'entre 10 y 15'],
  ['Correo electrónico', 'correo-invalido', 'correo válido'],
])('rechaza %s inválido', async (label, value, message) => {
  const wrapper = await validForm()
  await field(wrapper, label).setValue(value)
  await wrapper.get('form').trigger('submit')
  expect(wrapper.get('[role="alert"]').text()).toContain(message)
  expect(wrapper.emitted('save')).toBeUndefined()
})

test('detecta documentos duplicados aunque tengan separadores', async () => {
  const wrapper = await validForm()
  await field(wrapper, 'DNI /').setValue('30.123.456')
  await wrapper.get('form').trigger('submit')
  expect(wrapper.get('[role="alert"]').text()).toContain('Ya existe un cliente')
  expect(wrapper.emitted('save')).toBeUndefined()
})

test('emite datos normalizados sin modificar directamente la base', async () => {
  const wrapper = await validForm()
  await field(wrapper, 'Correo electrónico').setValue('  maria@example.com  ')
  await wrapper.get('form').trigger('submit')
  expect(wrapper.emitted('save')[0][0]).toEqual(expect.objectContaining({
    name: 'María López', doc: '34.567.890', phone: '1123456789', email: 'maria@example.com', active: true,
  }))
  expect(db.value.clients).toHaveLength(3)
})

test('permite editar conservando el documento del propio cliente', async () => {
  const wrapper = mount(Cliente, { props: { open: true, client: db.value.clients[0] } })
  await field(wrapper, 'Nombre o razón').setValue('Ana actualizada')
  await wrapper.get('form').trigger('submit')
  expect(wrapper.emitted('save')[0][0]).toEqual(expect.objectContaining({ id: 1, name: 'Ana actualizada' }))
  expect(db.value.clients[0].name).toBe('Ana Pérez')
})

test('cancelar emite close sin guardar y reabrir limpia los errores', async () => {
  const wrapper = mount(Cliente, { props: { open: true } })
  await wrapper.get('form').trigger('submit')
  await wrapper.findAll('button').find(b => b.text() === 'Cancelar').trigger('click')
  expect(wrapper.emitted('close')).toHaveLength(1)
  expect(wrapper.emitted('save')).toBeUndefined()
  await wrapper.setProps({ open: false })
  await wrapper.setProps({ open: true })
  expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  expect(field(wrapper, 'Nombre o razón').element.value).toBe('')
})
