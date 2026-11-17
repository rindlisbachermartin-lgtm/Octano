import { mount, flushPromises } from '@vue/test-utils'
import Login from '../../app/pages/login.vue'
import { campo, prepararSesionDePrueba, prepararBaseDePrueba } from './helpers'

describe('Formulario de inicio de sesión', () => {
  let sesion

  // Cada prueba empieza con una base y una sesión de prueba nuevas.
  beforeEach(() => {
    prepararBaseDePrueba()
    sesion = prepararSesionDePrueba()
  })

  test('no autentica con campos vacíos', async () => {
    // Intentamos ingresar sin escribir el correo ni la contraseña.
    const formulario = mount(Login)

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('correo y tu contraseña')
    expect(sesion.login).not.toHaveBeenCalled()
  })

  test('inicia sesión con los valores ingresados y navega al inicio', async () => {
    // Ingresamos con correo y contraseña y marcamos la opción de mantener la sesión.
    const formulario = mount(Login)
    await campo(formulario, 'Correo electrónico').setValue('ana@example.com')
    await campo(formulario, 'Contraseña').setValue('Clave123')
    await formulario.get('input[type="checkbox"]').setValue(true)

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')
    // Esperamos a que termine la respuesta del ingreso o del registro.
    await flushPromises()

    expect(sesion.login).toHaveBeenCalledWith('ana@example.com', 'Clave123', true)
    expect(navigateTo).toHaveBeenCalledWith('/')
  })

  test('muestra el error de autenticación y permite reintentar', async () => {
    // Simulamos una contraseña incorrecta para comprobar el mensaje de error.
    sesion.login.mockRejectedValueOnce(new Error('Credenciales incorrectas'))
    const formulario = mount(Login)
    await campo(formulario, 'Correo electrónico').setValue('ana@example.com')
    await campo(formulario, 'Contraseña').setValue('Incorrecta')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')
    // Esperamos a que termine la respuesta del ingreso o del registro.
    await flushPromises()

    expect(formulario.get('[role="alert"]').text()).toBe('Credenciales incorrectas')
    expect(formulario.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    expect(navigateTo).not.toHaveBeenCalled()
  })

  test('envía las credenciales al acceso de mecánico cuando se elige ese rol', async () => {
    // Elegimos el acceso de mecánico antes de ingresar.
    const formulario = mount(Login)
    await formulario.findAll('button').find(b => b.text() === 'Mecánico').trigger('click')
    await campo(formulario, 'Correo electrónico').setValue('nicolas@taller.demo')
    await campo(formulario, 'Contraseña').setValue('Octano123')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')
    // Esperamos a que termine la respuesta del ingreso o del registro.
    await flushPromises()

    expect(sesion.loginMechanic).toHaveBeenCalledWith('nicolas@taller.demo', 'Octano123', false)
    expect(sesion.login).not.toHaveBeenCalled()
  })

  test('bloquea el botón mientras la autenticación está pendiente', async () => {
    // Dejamos el ingreso esperando una respuesta para comprobar que el botón se bloquee.
    let terminarIngreso
    sesion.login.mockReturnValueOnce(new Promise(resolve => {
      terminarIngreso = resolve
    }))
    const formulario = mount(Login)
    await campo(formulario, 'Correo electrónico').setValue('ana@example.com')
    await campo(formulario, 'Contraseña').setValue('Clave123')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('button[type="submit"]').attributes('disabled')).toBeDefined()

    // Ahora damos la respuesta: el botón debe volver a estar disponible.
    terminarIngreso()
    // Esperamos a que termine la respuesta del ingreso o del registro.
    await flushPromises()

    expect(formulario.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
  })
})
