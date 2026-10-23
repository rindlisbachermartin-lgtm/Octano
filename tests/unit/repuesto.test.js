import Repuesto from '../../app/components/inventario/ModalRepuesto.vue'
import { field, mockDatabase, openForm } from './helpers'

let db
beforeEach(() => { ({ db } = mockDatabase()) })

async function validForm(props) {
  const wrapper = await openForm(Repuesto, props)
  await field(wrapper, 'Descripción').setValue('  Filtro de aceite  ')
  await field(wrapper, 'Marca').setValue('MANN')
  await field(wrapper, 'Código OEM').setValue('W712')
  return wrapper
}

test('exige descripción, marca y código', async () => {
  const wrapper = await openForm(Repuesto)
  await wrapper.get('form').trigger('submit')
  expect(wrapper.get('[role="alert"]').text()).toContain('campos obligatorios')
  expect(wrapper.emitted('created')).toBeUndefined()
})

test.each([['Stock inicial', '-1'], ['Stock inicial', '1.5'], ['Stock mínimo', '-1'], ['Costo de compra', '-10']])('rechaza %s = %s', async (label, value) => {
  const wrapper = await validForm()
  await field(wrapper, label).setValue(value)
  await wrapper.get('form').trigger('submit')
  expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  expect(wrapper.emitted('created')).toBeUndefined()
  expect(db.value.parts).toHaveLength(0)
})

test('recalcula precio por margen, guarda valores numéricos y limpia espacios', async () => {
  const wrapper = await validForm()
  await field(wrapper, 'Costo de compra').setValue('1000')
  await field(wrapper, 'Margen').setValue('25')
  expect(field(wrapper, 'Precio de venta').element.value).toBe('1250')
  await field(wrapper, 'Stock inicial').setValue('4')
  await wrapper.get('form').trigger('submit')
  expect(wrapper.emitted('created')[0][0]).toEqual(expect.objectContaining({ name: 'Filtro de aceite', cost: 1000, margin: 25, price: 1250, stock: 4 }))
  expect(db.value.parts).toHaveLength(1)
})

test('editar conserva id y compatibilidades sin insertar otro repuesto', async () => {
  db.value.parts.push({ id: 5, name: 'Filtro', brand: 'MANN', oem: 'W712', cost: 1000, margin: 25, price: 1250, stock: 4, min: 2, compatible: [{ brand: 'Toyota', model: 'Hilux' }] })
  const wrapper = await validForm({ part: db.value.parts[0] })
  await wrapper.get('form').trigger('submit')
  expect(wrapper.emitted('updated')[0][0]).toEqual(expect.objectContaining({ id: 5, compatible: [{ brand: 'Toyota', model: 'Hilux' }] }))
  expect(db.value.parts).toHaveLength(1)
})
