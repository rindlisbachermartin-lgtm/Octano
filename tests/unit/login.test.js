import { mount, flushPromises } from '@vue/test-utils'
import Login from '../../app/pages/login.vue'
import { field, mockAuth, mockDatabase } from './helpers'

let auth
beforeEach(() => { mockDatabase(); auth = mockAuth() })

test('no autentica con campos vacíos', async () => {
  const wrapper = mount(Login)
  await wrapper.get('form').trigger('submit')
  expect(wrapper.get('[role="alert"]').text()).toContain('correo y tu contraseña')
  expect(auth.login).not.toHaveBeenCalled()
})

test('inicia sesión con los valores ingresados y navega al inicio', async () => {
  const wrapper = mount(Login)
  await field(wrapper, 'Correo electrónico').setValue('ana@example.com')
  await field(wrapper, 'Contraseña').setValue('Clave123')
  await wrapper.get('input[type="checkbox"]').setValue(true)
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(auth.login).toHaveBeenCalledWith('ana@example.com', 'Clave123', true)
  expect(navigateTo).toHaveBeenCalledWith('/')
})

test('muestra el error de autenticación y permite reintentar', async () => {
  auth.login.mockRejectedValueOnce(new Error('Credenciales incorrectas'))
  const wrapper = mount(Login)
  await field(wrapper, 'Correo electrónico').setValue('ana@example.com')
  await field(wrapper, 'Contraseña').setValue('Incorrecta')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(wrapper.get('[role="alert"]').text()).toBe('Credenciales incorrectas')
  expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
  expect(navigateTo).not.toHaveBeenCalled()
})

test('envía las credenciales al acceso de mecánico cuando se elige ese rol', async () => {
  const wrapper = mount(Login)
  await wrapper.findAll('button').find(b => b.text() === 'Mecánico').trigger('click')
  await field(wrapper, 'Correo electrónico').setValue('nicolas@taller.demo')
  await field(wrapper, 'Contraseña').setValue('Octano123')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(auth.loginMechanic).toHaveBeenCalledWith('nicolas@taller.demo', 'Octano123', false)
  expect(auth.login).not.toHaveBeenCalled()
})

test('bloquea el botón mientras la autenticación está pendiente', async () => {
  let resolveLogin
  auth.login.mockReturnValueOnce(new Promise(resolve => { resolveLogin = resolve }))
  const wrapper = mount(Login)
  await field(wrapper, 'Correo electrónico').setValue('ana@example.com')
  await field(wrapper, 'Contraseña').setValue('Clave123')
  await wrapper.get('form').trigger('submit')
  expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
  resolveLogin()
  await flushPromises()
  expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
})
