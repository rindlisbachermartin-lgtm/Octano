import { mount, flushPromises } from '@vue/test-utils'
import Registro from '../../app/pages/registro.vue'
import { field, mockAuth, mockDatabase } from './helpers'

let auth
beforeEach(() => { mockDatabase(); auth = mockAuth() })

async function firstStep() {
  const wrapper = mount(Registro)
  await field(wrapper, 'Tu nombre').setValue('  Ana Pérez  ')
  await field(wrapper, 'Correo electrónico').setValue('ana@example.com')
  await field(wrapper, 'Teléfono').setValue('1123456789')
  await field(wrapper, 'Contraseña').setValue('Clave123')
  await field(wrapper, 'Repetí').setValue('Clave123')
  return wrapper
}

test.each([
  ['Correo electrónico', 'invalido', 'correo electrónico válido'],
  ['Teléfono', '123', '10 a 15'],
  ['Contraseña', 'corta', 'al menos 8'],
  ['Repetí', 'Diferente123', 'no coinciden'],
])('valida %s antes de avanzar', async (label, value, message) => {
  const wrapper = await firstStep()
  await field(wrapper, label).setValue(value)
  await wrapper.get('form').trigger('submit')
  expect(wrapper.get('[role="alert"]').text()).toContain(message)
  expect(auth.register).not.toHaveBeenCalled()
})

test('no registra una segunda cuenta en la demo', async () => {
  auth.account.value = { email: 'existente@example.com' }
  const wrapper = await firstStep()
  await wrapper.get('form').trigger('submit')
  expect(wrapper.get('[role="alert"]').text()).toContain('Ya hay una cuenta')
  expect(auth.register).not.toHaveBeenCalled()
})

test('valida CUIT en el segundo paso y registra al completar el taller', async () => {
  const wrapper = await firstStep()
  await wrapper.get('form').trigger('submit')
  expect(wrapper.text()).toContain('PASO 2 DE 2')
  expect(auth.register).not.toHaveBeenCalled()
  await field(wrapper, 'Nombre del taller').setValue('  Taller Central  ')
  await field(wrapper, 'Nombre o razón social').setValue('Ana Pérez')
  await field(wrapper, 'CUIT').setValue('20123456780')
  await wrapper.get('form').trigger('submit')
  expect(wrapper.get('[role="alert"]').text()).toContain('dígito verificador')
  expect(auth.register).not.toHaveBeenCalled()
  await field(wrapper, 'CUIT').setValue('20123456786')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(auth.register).toHaveBeenCalledWith(expect.objectContaining({ name: 'Ana Pérez', workshop: 'Taller Central', cuit: '20-12345678-6' }), 'Clave123')
  expect(navigateTo).toHaveBeenCalledWith('/')
})

test('muestra errores al registrar y vuelve a habilitar el envío', async () => {
  const wrapper = await firstStep()
  await wrapper.get('form').trigger('submit')
  await field(wrapper, 'Nombre del taller').setValue('Taller')
  await field(wrapper, 'Nombre o razón social').setValue('Ana')
  await field(wrapper, 'CUIT').setValue('20123456786')
  auth.register.mockRejectedValueOnce(new Error('No se pudo guardar'))
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(wrapper.get('[role="alert"]').text()).toBe('No se pudo guardar')
  expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
  expect(navigateTo).not.toHaveBeenCalled()
})
