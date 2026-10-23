import Vehiculo from '../../app/components/vehiculos/ModalFormularioVehiculo.vue'
import { campo, prepararBaseDePrueba, abrirFormulario } from './helpers'

describe('Formulario de vehículos', () => {
  let db

  // Cada prueba empieza con datos nuevos.
  beforeEach(() => {
    const baseDePrueba = prepararBaseDePrueba()
    db = baseDePrueba.db
  })

  // Abre el formulario y completa los datos correctos que se repiten en varios casos.
  async function completarFormularioValido(props = {}) {
    const formulario = await abrirFormulario(Vehiculo, { clientId: 1, ...props })
    await campo(formulario, 'Patente').setValue('ab123cd')
    await campo(formulario, 'Marca').setValue('Toyota')
    await campo(formulario, 'Modelo').setValue('Hilux')
    return formulario
  }

  test('sin titular deshabilita los datos y exige seleccionar un cliente', async () => {
    // Abrimos el formulario sin elegir al dueño del vehículo.
    const formulario = await abrirFormulario(Vehiculo)

    expect(formulario.get('fieldset').attributes('disabled')).toBeDefined()

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('seleccioná un cliente')
    expect(formulario.emitted('created')).toBeUndefined()
  })

  test('rechaza una patente con formato incorrecto', async () => {
    // Completamos los demás datos y probamos patente con el valor 'INVALIDA'.
    const formulario = await completarFormularioValido()
    await campo(formulario, 'Patente').setValue('INVALIDA')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('patente válida')
    expect(db.value.vehicles).toHaveLength(1)
    expect(formulario.emitted('created')).toBeUndefined()
  })

  test('rechaza una patente que ya está registrada', async () => {
    // Completamos los demás datos y probamos patente con el valor 'abc123'.
    const formulario = await completarFormularioValido()
    await campo(formulario, 'Patente').setValue('abc123')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('ya está registrada')
    expect(db.value.vehicles).toHaveLength(1)
    expect(formulario.emitted('created')).toBeUndefined()
  })

  test('rechaza un año anterior a 1900', async () => {
    // Completamos los demás datos y probamos año con el valor '1899'.
    const formulario = await completarFormularioValido()
    await campo(formulario, 'Año').setValue('1899')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('año y el kilometraje')
    expect(db.value.vehicles).toHaveLength(1)
    expect(formulario.emitted('created')).toBeUndefined()
  })

  test('rechaza un kilometraje negativo', async () => {
    // Completamos los demás datos y probamos kilometraje con el valor '-1'.
    const formulario = await completarFormularioValido()
    await campo(formulario, 'Kilometraje').setValue('-1')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('año y el kilometraje')
    expect(db.value.vehicles).toHaveLength(1)
    expect(formulario.emitted('created')).toBeUndefined()
  })

  test('rechaza un kilometraje con decimales', async () => {
    // Completamos los demás datos y probamos kilometraje con el valor '1.5'.
    const formulario = await completarFormularioValido()
    await campo(formulario, 'Kilometraje').setValue('1.5')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('año y el kilometraje')
    expect(db.value.vehicles).toHaveLength(1)
    expect(formulario.emitted('created')).toBeUndefined()
  })

  test('crea un vehículo y normaliza la patente', async () => {
    // Guardamos un vehículo con la patente escrita sin guiones y en minúsculas.
    const formulario = await completarFormularioValido()

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    // Revisamos los datos del vehículo que se acaba de crear.
    const vehiculoCreado = formulario.emitted('created')[0][0]
    expect(vehiculoCreado).toMatchObject({
      plate: 'AB-123-CD',
      client: 1,
      brand: 'Toyota',
      model: 'Hilux',
    })
    expect(db.value.vehicles).toHaveLength(2)
  })

  test('editar conserva su patente y QR sin duplicar el registro', async () => {
    // Actualizamos los kilómetros de un vehículo que ya existe.
    const formulario = await abrirFormulario(Vehiculo, { vehicle: db.value.vehicles[0] })
    await campo(formulario, 'Kilometraje').setValue('60000')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    const vehiculoActualizado = formulario.emitted('updated')[0][0]
    expect(vehiculoActualizado).toMatchObject({
      id: 10,
      km: 60000,
      qrCode: 'qr-existente',
      plate: 'ABC-123',
    })
    expect(db.value.vehicles).toHaveLength(1)
  })

  test('cambiar marca limpia el modelo y la motorización dependientes', async () => {
    // Elegimos otra marca para que no quede seleccionado un modelo de la marca anterior.
    const formulario = await abrirFormulario(Vehiculo, { vehicle: db.value.vehicles[0] })
    await campo(formulario, 'Marca').setValue('Ford')

    expect(campo(formulario, 'Modelo').element.value).toBe('')
    expect(campo(formulario, 'Motorización').element.value).toBe('')
  })
})
