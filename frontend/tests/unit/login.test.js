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
    // Ingresamos con correo y contraseña.
    const formulario = mount(Login)
    await campo(formulario, 'Correo electrónico').setValue('ana@example.com')
    await campo(formulario, 'Contraseña').setValue('Clave123')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')
    // Esperamos a que termine la respuesta del ingreso o del registro.
    await flushPromises()

    expect(sesion.login).toHaveBeenCalledWith('ana@example.com', 'Clave123')
    expect(navigateTo).toHaveBeenCalledWith('/')
  })

  test('muestra el error de autenticación y permite reintentar', async () => {
    // Simulamos una contraseña incorrecta para comprobar el mensaje de error.
    sesion.login.mockRejectedValueOnce({ statusCode: 401 })
    const formulario = mount(Login)
    await campo(formulario, 'Correo electrónico').setValue('ana@example.com')
    await campo(formulario, 'Contraseña').setValue('Incorrecta')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')
    // Esperamos a que termine la respuesta del ingreso o del registro.
    await flushPromises()

    expect(formulario.get('[role="alert"]').text()).toBe('El correo o la contraseña no coinciden, o la sesión venció.')
    expect(formulario.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    expect(navigateTo).not.toHaveBeenCalled()
  })

  test('navega al inicio del mecánico según el rol devuelto por la API', async () => {
    sesion.login.mockImplementationOnce(async () => {
      sesion.user.value = { rol: 'MECANICO' }
      sesion.homePath.value = '/mecanico'
    })
    const formulario = mount(Login)
    await campo(formulario, 'Correo electrónico').setValue('nicolas@taller.demo')
    await campo(formulario, 'Contraseña').setValue('Octano123')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')
    // Esperamos a que termine la respuesta del ingreso o del registro.
    await flushPromises()

    expect(sesion.login).toHaveBeenCalledWith('nicolas@taller.demo', 'Octano123')
    expect(navigateTo).toHaveBeenCalledWith('/mecanico')
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
