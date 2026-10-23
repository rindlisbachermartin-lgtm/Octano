import { mount } from '@vue/test-utils'
import Titular from '../../app/components/vehiculos/FormularioTitular.vue'
import { prepararBaseDePrueba, buscar } from './helpers'

describe('Formulario de cambio de titular', () => {
  let db

  // Cada prueba empieza con datos nuevos.
  beforeEach(() => {
    const baseDePrueba = prepararBaseDePrueba()
    db = baseDePrueba.db
  })

  test('rechaza confirmar sin seleccionar otro titular', async () => {
    // Intentamos cambiar al dueño del vehículo sin elegir a otra persona.
    const formulario = mount(Titular, { props: { vehicle: db.value.vehicles[0] } })

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('titular activo diferente')
    expect(formulario.emitted('saved')).toBeUndefined()
    expect(db.value.vehicles[0].client).toBe(1)
  })

  test('la búsqueda excluye al titular actual y a clientes inactivos', async () => {
    // Buscamos personas que no se pueden elegir como nuevo titular.
    const formulario = mount(Titular, { props: { vehicle: db.value.vehicles[0] } })
    await buscar(formulario, 'input', 'Ana')

    expect(formulario.findAll('.selection-results button')).toHaveLength(0)
    await buscar(formulario, 'input', 'inactivo')

    expect(formulario.findAll('.selection-results button')).toHaveLength(0)
  })

  test('cambia el titular, registra el historial y conserva los datos del vehículo', async () => {
    // Elegimos a Luis como nuevo dueño y confirmamos el cambio.
    const vehicle = db.value.vehicles[0]
    const formulario = mount(Titular, { props: { vehicle } })
    await buscar(formulario, 'input', 'Luis')
    await formulario.get('.selection-results button').trigger('click')

    expect(formulario.text()).toContain('Nuevo titular: Luis Gómez')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.emitted('saved')).toHaveLength(1)
    expect(vehicle.client).toBe(2)
    expect(vehicle.ownershipHistory).toEqual([{ from: 1, to: 2, date: expect.any(String) }])
    expect(vehicle.qrCode).toBe('qr-existente')
    expect(vehicle.plate).toBe('ABC-123')
  })
})
