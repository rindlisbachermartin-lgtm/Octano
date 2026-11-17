import Orden from '../../app/components/ordenes/ModalFormularioOrden.vue'
import { campo, prepararSesionDePrueba, prepararBaseDePrueba, abrirFormulario } from './helpers'

describe('Formulario de órdenes de trabajo', () => {
  let db
  let createOrder

  // Reiniciamos los datos y las llamadas a la función que crea órdenes.
  beforeEach(() => {
    const baseDePrueba = prepararBaseDePrueba()
    db = baseDePrueba.db
    createOrder = baseDePrueba.createOrder
    prepararSesionDePrueba()
  })

  // Abre el formulario y completa los datos correctos que se repiten en varios casos.
  async function completarFormularioValido() {
    const formulario = await abrirFormulario(Orden)
    await formulario.get('[aria-label="Vehículo de la orden"]').setValue('10')
    await formulario.findAll('button').find(b => b.text() === 'Service').trigger('click')
    await campo(formulario, 'Detalle del trabajo').setValue('  Cambio de aceite  ')
    await campo(formulario, 'Mecánico asignado').setValue('Nicolás')
    return formulario
  }

  test('exige vehículo, tipo de trabajo y detalle antes de crear', async () => {
    // Intentamos crear una orden sin completar los datos.
    const formulario = await abrirFormulario(Orden)

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('Seleccioná un vehículo')
    expect(createOrder).not.toHaveBeenCalled()
  })

  test('exige un mecánico válido', async () => {
    // Completamos la orden, pero dejamos sin elegir al mecánico.
    const formulario = await completarFormularioValido()
    await campo(formulario, 'Mecánico asignado').setValue('')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('mecánico válido')
    expect(createOrder).not.toHaveBeenCalled()
  })

  test('primero muestra la vista previa y solo crea al confirmar', async () => {
    // Enviamos dos veces: primero revisamos la orden y después la confirmamos.
    const formulario = await completarFormularioValido()

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('h2').text()).toContain('Vista previa')
    expect(formulario.text()).toContain('Cambio de aceite')
    expect(createOrder).not.toHaveBeenCalled()
    expect(formulario.emitted('created')).toBeUndefined()

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(createOrder).toHaveBeenCalledTimes(1)
    expect(db.value.orders[0]).toMatchObject({
      vehicle: 10,
      service: 'Cambio de aceite',
      mechanic: 'Nicolás',
      serviceTypes: ['Service de mantenimiento'],
    })
    expect(formulario.emitted('created')).toEqual([[100]])
  })

  test('volver a editar desde la vista previa conserva el detalle', async () => {
    // Volvemos desde la vista previa para comprobar que el texto siga escrito.
    const formulario = await completarFormularioValido()

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')
    await formulario.findAll('button').find(b => b.text() === 'Volver a editar').trigger('click')

    expect(campo(formulario, 'Detalle del trabajo').element.value).toBe('  Cambio de aceite  ')
    expect(createOrder).not.toHaveBeenCalled()
  })

  test('el perfil mecánico no puede crear órdenes desde este formulario', async () => {
    // Abrimos el formulario como mecánico e intentamos crear una orden.
    useOwnerAccount().isMechanic.value = true
    const formulario = await abrirFormulario(Orden)

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(createOrder).not.toHaveBeenCalled()
  })
})
