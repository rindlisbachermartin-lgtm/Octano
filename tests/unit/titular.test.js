import { mount } from '@vue/test-utils'
import Titular from '../../app/components/vehiculos/FormularioTitular.vue'
import { mockDatabase, search } from './helpers'

let db
beforeEach(() => { ({ db } = mockDatabase()) })

test('rechaza confirmar sin seleccionar otro titular', async () => {
  const wrapper = mount(Titular, { props: { vehicle: db.value.vehicles[0] } })
  await wrapper.get('form').trigger('submit')
  expect(wrapper.get('[role="alert"]').text()).toContain('titular activo diferente')
  expect(wrapper.emitted('saved')).toBeUndefined()
  expect(db.value.vehicles[0].client).toBe(1)
})

test('la búsqueda excluye al titular actual y a clientes inactivos', async () => {
  const wrapper = mount(Titular, { props: { vehicle: db.value.vehicles[0] } })
  await search(wrapper, 'input', 'Ana')
  expect(wrapper.findAll('.selection-results button')).toHaveLength(0)
  await search(wrapper, 'input', 'inactivo')
  expect(wrapper.findAll('.selection-results button')).toHaveLength(0)
})

test('cambia el titular, registra el historial y conserva los datos del vehículo', async () => {
  const vehicle = db.value.vehicles[0]
  const wrapper = mount(Titular, { props: { vehicle } })
  await search(wrapper, 'input', 'Luis')
  await wrapper.get('.selection-results button').trigger('click')
  expect(wrapper.text()).toContain('Nuevo titular: Luis Gómez')
  await wrapper.get('form').trigger('submit')
  expect(wrapper.emitted('saved')).toHaveLength(1)
  expect(vehicle.client).toBe(2)
  expect(vehicle.ownershipHistory).toEqual([{ from: 1, to: 2, date: expect.any(String) }])
  expect(vehicle.qrCode).toBe('qr-existente')
  expect(vehicle.plate).toBe('ABC-123')
})
