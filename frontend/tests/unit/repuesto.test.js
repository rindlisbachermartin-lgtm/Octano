import Repuesto from '../../app/components/inventario/ModalRepuesto.vue'
import { campo, prepararBaseDePrueba, abrirFormulario } from './helpers'

describe('Formulario de repuestos', () => {
  let db

  // Cada prueba empieza con datos nuevos.
  beforeEach(() => {
    const baseDePrueba = prepararBaseDePrueba()
    db = baseDePrueba.db
  })

  // Abre el formulario y completa los datos correctos que se repiten en varios casos.
  async function completarFormularioValido(props) {
    const formulario = await abrirFormulario(Repuesto, props)
    await campo(formulario, 'Descripción').setValue('  Filtro de aceite  ')
    await campo(formulario, 'Marca').setValue('MANN')
    await campo(formulario, 'Código OEM').setValue('W712')
    return formulario
  }

  test('exige descripción, marca y código', async () => {
    // Intentamos guardar un repuesto con los campos vacíos.
    const formulario = await abrirFormulario(Repuesto)

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('campos obligatorios')
    expect(formulario.emitted('created')).toBeUndefined()
  })

  test('rechaza un stock inicial negativo', async () => {
    // Completamos los demás datos y probamos stock inicial con el valor '-1'.
    const formulario = await completarFormularioValido()
    await campo(formulario, 'Stock inicial').setValue('-1')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.find('[role="alert"]').exists()).toBe(true)
    expect(formulario.emitted('created')).toBeUndefined()
    expect(db.value.parts).toHaveLength(0)
  })

  test('rechaza un stock inicial con decimales', async () => {
    // Completamos los demás datos y probamos stock inicial con el valor '1.5'.
    const formulario = await completarFormularioValido()
    await campo(formulario, 'Stock inicial').setValue('1.5')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.find('[role="alert"]').exists()).toBe(true)
    expect(formulario.emitted('created')).toBeUndefined()
    expect(db.value.parts).toHaveLength(0)
  })

  test('rechaza un stock mínimo negativo', async () => {
    // Completamos los demás datos y probamos stock mínimo con el valor '-1'.
    const formulario = await completarFormularioValido()
    await campo(formulario, 'Stock mínimo').setValue('-1')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.find('[role="alert"]').exists()).toBe(true)
    expect(formulario.emitted('created')).toBeUndefined()
    expect(db.value.parts).toHaveLength(0)
  })

  test('rechaza un costo de compra negativo', async () => {
    // Completamos los demás datos y probamos costo de compra con el valor '-10'.
    const formulario = await completarFormularioValido()
    await campo(formulario, 'Costo de compra').setValue('-10')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.find('[role="alert"]').exists()).toBe(true)
    expect(formulario.emitted('created')).toBeUndefined()
    expect(db.value.parts).toHaveLength(0)
  })

  test('recalcula precio por margen, guarda valores numéricos y limpia espacios', async () => {
    // Cargamos un costo de 1000 y una ganancia del 25 %: el precio debe ser 1250.
    const formulario = await completarFormularioValido()
    await campo(formulario, 'Costo de compra').setValue('1000')
    await campo(formulario, 'Margen').setValue('25')

    expect(campo(formulario, 'Precio de venta').element.value).toBe('1250')
    await campo(formulario, 'Stock inicial').setValue('4')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    const repuestoCreado = formulario.emitted('created')[0][0]
    expect(repuestoCreado).toMatchObject({
      name: 'Filtro de aceite',
      cost: 1000,
      margin: 25,
      price: 1250,
      stock: 4,
    })
    expect(db.value.parts).toHaveLength(1)
  })

  test('editar conserva id y compatibilidades sin insertar otro repuesto', async () => {
    // Editamos un repuesto que ya tiene vehículos compatibles.
    db.value.parts.push({
      id: 5,
      name: 'Filtro',
      brand: 'MANN',
      oem: 'W712',
      cost: 1000,
      margin: 25,
      price: 1250,
      stock: 4,
      min: 2,
      compatible: [{ brand: 'Toyota', model: 'Hilux' }],
    })
    const formulario = await completarFormularioValido({ part: db.value.parts[0] })

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    const repuestoActualizado = formulario.emitted('updated')[0][0]
    expect(repuestoActualizado).toMatchObject({
      id: 5,
      compatible: [{ brand: 'Toyota', model: 'Hilux' }],
    })
    expect(db.value.parts).toHaveLength(1)
  })
})
