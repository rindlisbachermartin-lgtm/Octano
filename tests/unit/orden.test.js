import Orden from '../../app/components/ordenes/ModalFormularioOrden.vue'
import { field, mockAuth, mockDatabase, openForm } from './helpers'

let db, createOrder
beforeEach(() => { ({ db, createOrder } = mockDatabase()); mockAuth() })

async function validForm() {
  const wrapper = await openForm(Orden)
  await wrapper.get('[aria-label="Vehículo de la orden"]').setValue('10')
  await wrapper.findAll('button').find(b => b.text() === 'Service').trigger('click')
  await field(wrapper, 'Detalle del trabajo').setValue('  Cambio de aceite  ')
  await field(wrapper, 'Mecánico asignado').setValue('Nicolás')
  return wrapper
}

test('exige vehículo, tipo de trabajo y detalle antes de crear', async () => {
  const wrapper = await openForm(Orden)
  await wrapper.get('form').trigger('submit')
  expect(wrapper.get('[role="alert"]').text()).toContain('Seleccioná un vehículo')
  expect(createOrder).not.toHaveBeenCalled()
})

test('exige un mecánico válido', async () => {
  const wrapper = await validForm()
  await field(wrapper, 'Mecánico asignado').setValue('')
  await wrapper.get('form').trigger('submit')
  expect(wrapper.get('[role="alert"]').text()).toContain('mecánico válido')
  expect(createOrder).not.toHaveBeenCalled()
})

test('primero muestra la vista previa y solo crea al confirmar', async () => {
  const wrapper = await validForm()
  await wrapper.get('form').trigger('submit')
  expect(wrapper.get('h2').text()).toContain('Vista previa')
  expect(wrapper.text()).toContain('Cambio de aceite')
  expect(createOrder).not.toHaveBeenCalled()
  expect(wrapper.emitted('created')).toBeUndefined()
  await wrapper.get('form').trigger('submit')
  expect(createOrder).toHaveBeenCalledTimes(1)
  expect(db.value.orders[0]).toEqual(expect.objectContaining({ vehicle: 10, service: 'Cambio de aceite', mechanic: 'Nicolás', serviceTypes: ['Service de mantenimiento'] }))
  expect(wrapper.emitted('created')).toEqual([[100]])
})

test('volver a editar desde la vista previa conserva el detalle', async () => {
  const wrapper = await validForm()
  await wrapper.get('form').trigger('submit')
  await wrapper.findAll('button').find(b => b.text() === 'Volver a editar').trigger('click')
  expect(field(wrapper, 'Detalle del trabajo').element.value).toBe('  Cambio de aceite  ')
  expect(createOrder).not.toHaveBeenCalled()
})

test('el perfil mecánico no puede crear órdenes desde este formulario', async () => {
  useOwnerAccount().isMechanic.value = true
  const wrapper = await openForm(Orden)
  await wrapper.get('form').trigger('submit')
  expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
  expect(createOrder).not.toHaveBeenCalled()
})
