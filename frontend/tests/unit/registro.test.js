import { mount, flushPromises } from '@vue/test-utils'
import Registro from '../../app/pages/registro.vue'
import { campo, prepararSesionDePrueba, prepararBaseDePrueba } from './helpers'

describe('Formulario de registro', () => {
  let sesion

  // Cada prueba empieza con una base y una sesión de prueba nuevas.
  beforeEach(() => {
    prepararBaseDePrueba()
    sesion = prepararSesionDePrueba()
  })

  // Completa el primer paso del registro con datos correctos.
  async function completarDatosDeLaCuenta() {
    const formulario = mount(Registro)
    await campo(formulario, 'Tu nombre').setValue('  Ana Pérez  ')
    await campo(formulario, 'Correo electrónico').setValue('ana@example.com')
    await campo(formulario, 'Teléfono').setValue('1123456789')
    await campo(formulario, 'Contraseña').setValue('Clave123')
    await campo(formulario, 'Repetí').setValue('Clave123')
    return formulario
  }

  test('rechaza un correo sin formato válido', async () => {
    // Completamos los demás datos y probamos correo electrónico con el valor 'invalido'.
    const formulario = await completarDatosDeLaCuenta()
    await campo(formulario, 'Correo electrónico').setValue('invalido')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('correo electrónico válido')
    expect(sesion.register).not.toHaveBeenCalled()
  })

  test('rechaza un teléfono demasiado corto', async () => {
    // Completamos los demás datos y probamos teléfono con el valor '123'.
    const formulario = await completarDatosDeLaCuenta()
    await campo(formulario, 'Teléfono').setValue('123')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('10 a 15')
    expect(sesion.register).not.toHaveBeenCalled()
  })

  test('rechaza una contraseña demasiado corta', async () => {
    // Completamos los demás datos y probamos contraseña con el valor 'corta'.
    const formulario = await completarDatosDeLaCuenta()
    await campo(formulario, 'Contraseña').setValue('corta')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('al menos 8')
    expect(sesion.register).not.toHaveBeenCalled()
  })

  test('rechaza contraseñas que no coinciden', async () => {
    // Completamos los demás datos y probamos repetí con el valor 'Diferente123'.
    const formulario = await completarDatosDeLaCuenta()
    await campo(formulario, 'Repetí').setValue('Diferente123')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('no coinciden')
    expect(sesion.register).not.toHaveBeenCalled()
  })

  test('no registra una segunda cuenta en la demo', async () => {
    // Simulamos que ya existe una cuenta e intentamos registrar otra.
    sesion.account.value = { email: 'existente@example.com' }
    const formulario = await completarDatosDeLaCuenta()

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('Ya hay una cuenta')
    expect(sesion.register).not.toHaveBeenCalled()
  })

  test('valida CUIT en el segundo paso y registra al completar el taller', async () => {
    // Primero probamos un CUIT incorrecto y después lo corregimos para crear la cuenta.
    const formulario = await completarDatosDeLaCuenta()

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.text()).toContain('PASO 2 DE 2')
    expect(sesion.register).not.toHaveBeenCalled()
    await campo(formulario, 'Nombre del taller').setValue('  Taller Central  ')
    await campo(formulario, 'Nombre o razón social').setValue('Ana Pérez')
    await campo(formulario, 'CUIT').setValue('20123456780')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('dígito verificador')
    expect(sesion.register).not.toHaveBeenCalled()
    await campo(formulario, 'CUIT').setValue('20123456786')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')
    // Esperamos a que termine la respuesta del ingreso o del registro.
    await flushPromises()

    expect(sesion.register).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Ana Pérez',
        workshop: 'Taller Central',
        cuit: '20-12345678-6',
      }),
      'Clave123',
    )
    expect(navigateTo).toHaveBeenCalledWith('/')
  })

  test('muestra errores al registrar y vuelve a habilitar el envío', async () => {
    // Simulamos un error al guardar la cuenta para comprobar que se pueda reintentar.
    const formulario = await completarDatosDeLaCuenta()

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')
    await campo(formulario, 'Nombre del taller').setValue('Taller')
    await campo(formulario, 'Nombre o razón social').setValue('Ana')
    await campo(formulario, 'CUIT').setValue('20123456786')
    sesion.register.mockRejectedValueOnce(new Error('No se pudo guardar'))

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')
    // Esperamos a que termine la respuesta del ingreso o del registro.
    await flushPromises()

    expect(formulario.get('[role="alert"]').text()).toBe('No se pudo guardar')
    expect(formulario.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    expect(navigateTo).not.toHaveBeenCalled()
  })
})
