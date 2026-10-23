import Vehiculo from '../../app/components/vehiculos/ModalFormularioVehiculo.vue'
import { field, mockDatabase, openForm } from './helpers'

let db
beforeEach(() => { ({ db } = mockDatabase()) })

async function validForm(props = {}) {
  const wrapper = await openForm(Vehiculo, { clientId: 1, ...props })
  await field(wrapper, 'Patente').setValue('ab123cd')
  await field(wrapper, 'Marca').setValue('Toyota')
  await field(wrapper, 'Modelo').setValue('Hilux')
  return wrapper
}

test('sin titular deshabilita los datos y exige seleccionar un cliente', async () => {
  const wrapper = await openForm(Vehiculo)
  expect(wrapper.get('fieldset').attributes('disabled')).toBeDefined()
  await wrapper.get('form').trigger('submit')
  expect(wrapper.get('[role="alert"]').text()).toContain('seleccioná un cliente')
  expect(wrapper.emitted('created')).toBeUndefined()
})

test.each([
  ['Patente', 'INVALIDA', 'patente válida'],
  ['Patente', 'abc123', 'ya está registrada'],
  ['Año', '1899', 'año y el kilometraje'],
  ['Kilometraje', '-1', 'año y el kilometraje'],
  ['Kilometraje', '1.5', 'año y el kilometraje'],
])('rechaza %s = %s sin insertar vehículos', async (label, value, message) => {
  const wrapper = await validForm()
  await field(wrapper, label).setValue(value)
  await wrapper.get('form').trigger('submit')
  expect(wrapper.get('[role="alert"]').text()).toContain(message)
  expect(db.value.vehicles).toHaveLength(1)
  expect(wrapper.emitted('created')).toBeUndefined()
})

test('crea un vehículo y normaliza la patente', async () => {
  const wrapper = await validForm()
  await wrapper.get('form').trigger('submit')
  expect(wrapper.emitted('created')[0][0]).toEqual(expect.objectContaining({ plate: 'AB-123-CD', client: 1, brand: 'Toyota', model: 'Hilux' }))
  expect(db.value.vehicles).toHaveLength(2)
})

test('editar conserva su patente y QR sin duplicar el registro', async () => {
  const wrapper = await openForm(Vehiculo, { vehicle: db.value.vehicles[0] })
  await field(wrapper, 'Kilometraje').setValue('60000')
  await wrapper.get('form').trigger('submit')
  expect(wrapper.emitted('updated')[0][0]).toEqual(expect.objectContaining({ id: 10, km: 60000, qrCode: 'qr-existente', plate: 'ABC-123' }))
  expect(db.value.vehicles).toHaveLength(1)
})

test('cambiar marca limpia el modelo y la motorización dependientes', async () => {
  const wrapper = await openForm(Vehiculo, { vehicle: db.value.vehicles[0] })
  await field(wrapper, 'Marca').setValue('Ford')
  expect(field(wrapper, 'Modelo').element.value).toBe('')
  expect(field(wrapper, 'Motorización').element.value).toBe('')
})
