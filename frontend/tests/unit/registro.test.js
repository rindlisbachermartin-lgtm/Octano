import { mount, flushPromises } from '@vue/test-utils'
import Registro from '../../app/pages/registro.vue'
import { campo, prepararSesionDePrueba } from './helpers'

describe('Formulario de registro', () => {
  let sesion

  beforeEach(() => {
    sesion = prepararSesionDePrueba()
  })

  async function completarDatosDeLaCuenta() {
    const formulario = mount(Registro)
    await campo(formulario, 'Correo electrónico').setValue('ana@example.com')
    await campo(formulario, 'Teléfono').setValue('1123456789')
    await campo(formulario, 'Contraseña').setValue('ClaveSegura123')
    await campo(formulario, 'Repetí').setValue('ClaveSegura123')
    return formulario
  }

  async function completarTaller() {
    const formulario = await completarDatosDeLaCuenta()
    await formulario.get('form').trigger('submit')
    expect(formulario.text()).toContain('PASO 2 DE 2')
    expect(sesion.register).not.toHaveBeenCalled()
    await campo(formulario, 'Nombre del taller').setValue('  taller Central  ')
    await campo(formulario, 'Calle').setValue('  avenida Siempre Viva  ')
    await campo(formulario, 'Altura').setValue('123')
    return formulario
  }

  test('configura la validación nativa del correo y teléfono', async () => {
    const formulario = await completarDatosDeLaCuenta()
    await campo(formulario, 'Correo electrónico').setValue('invalido')
    expect(campo(formulario, 'Correo electrónico').element.validity.typeMismatch).toBe(true)
    await campo(formulario, 'Teléfono').setValue('')
    expect(campo(formulario, 'Teléfono').element.validity.valueMissing).toBe(true)
    expect(campo(formulario, 'Teléfono').attributes()).toMatchObject({ minlength: '6', maxlength: '30' })
    expect(sesion.register).not.toHaveBeenCalled()
  })

  test.each(['corta', 'a'.repeat(11), 'á'.repeat(37)])('rechaza una contraseña fuera de los límites: %s', async password => {
    const formulario = await completarDatosDeLaCuenta()
    await campo(formulario, 'Contraseña').setValue(password)
    await formulario.get('form').trigger('submit')
    expect(formulario.get('[role="alert"]').text()).toContain('al menos 12 caracteres y hasta 72 bytes')
    expect(formulario.text()).toContain('PASO 1 DE 2')
    expect(sesion.register).not.toHaveBeenCalled()
  })

  test('rechaza contraseñas que no coinciden', async () => {
    const formulario = await completarDatosDeLaCuenta()
    await campo(formulario, 'Repetí').setValue('Diferente12345')
    await formulario.get('form').trigger('submit')
    expect(formulario.get('[role="alert"]').text()).toContain('no coinciden')
    expect(sesion.register).not.toHaveBeenCalled()
  })

  test('registra la cuenta con los datos normalizados del taller', async () => {
    const formulario = await completarTaller()
    await formulario.get('form').trigger('submit')
    await flushPromises()
    expect(sesion.register).toHaveBeenCalledWith('ana@example.com', 'ClaveSegura123', {
      nombre: 'Taller Central', calle: 'Avenida Siempre Viva', altura: 123, telefono: '1123456789',
    })
    expect(navigateTo).toHaveBeenCalledWith('/')
  })

  test('no registra sin la altura del taller', async () => {
    const formulario = await completarTaller()
    await campo(formulario, 'Altura').setValue('')
    await formulario.get('form').trigger('submit')
    expect(sesion.register).not.toHaveBeenCalled()
    expect(navigateTo).not.toHaveBeenCalled()
  })

  test('muestra errores de la API y vuelve a habilitar el envío', async () => {
    const formulario = await completarTaller()
    sesion.register.mockRejectedValueOnce({ statusCode: 409, data: { detail: 'El correo ya está registrado.' } })
    await formulario.get('form').trigger('submit')
    await flushPromises()
    expect(formulario.get('[role="alert"]').text()).toBe('El correo ya está registrado.')
    expect(formulario.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    expect(navigateTo).not.toHaveBeenCalled()
  })

  test('permite volver al primer paso conservando los datos de la cuenta', async () => {
    const formulario = await completarTaller()
    await formulario.get('.auth-back').trigger('click')
    expect(formulario.text()).toContain('PASO 1 DE 2')
    expect(campo(formulario, 'Correo electrónico').element.value).toBe('ana@example.com')
    expect(sesion.register).not.toHaveBeenCalled()
  })
})
