import { mount } from '@vue/test-utils'
import Cliente from '../../app/components/clientes/ModalFormularioCliente.vue'
import { campo, prepararBaseDePrueba } from './helpers'

describe('Formulario de clientes', () => {
  let db

  // Cada prueba empieza con datos nuevos.
  beforeEach(() => {
    const baseDePrueba = prepararBaseDePrueba()
    db = baseDePrueba.db
  })

  // Abre el formulario y completa los datos correctos que se repiten en varios casos.
  async function completarFormularioValido(props = {}) {
    const formulario = mount(Cliente, { props: { open: true, ...props } })
    await campo(formulario, 'Nombre o razón').setValue('  María López  ')
    await campo(formulario, 'DNI /').setValue('34567890')
    await campo(formulario, 'Teléfono').setValue('1123456789')
    return formulario
  }

  test('muestra un error y no guarda si faltan los datos obligatorios', async () => {
    // Intentamos guardar un cliente sin completar ningún dato.
    const formulario = mount(Cliente, { props: { open: true } })

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('campos obligatorios')
    expect(formulario.emitted('save')).toBeUndefined()
  })

  test('rechaza un nombre demasiado corto', async () => {
    // Completamos los demás datos y probamos nombre o razón con el valor 'A'.
    const formulario = await completarFormularioValido()
    await campo(formulario, 'Nombre o razón').setValue('A')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('entre 2 y 90')
    expect(formulario.emitted('save')).toBeUndefined()
  })

  test('rechaza un documento demasiado corto', async () => {
    // Completamos los demás datos y probamos dni / con el valor '123'.
    const formulario = await completarFormularioValido()
    await campo(formulario, 'DNI /').setValue('123')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('DNI')
    expect(formulario.emitted('save')).toBeUndefined()
  })

  test('rechaza un teléfono demasiado corto', async () => {
    // Completamos los demás datos y probamos teléfono con el valor '123'.
    const formulario = await completarFormularioValido()
    await campo(formulario, 'Teléfono').setValue('123')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('entre 10 y 15')
    expect(formulario.emitted('save')).toBeUndefined()
  })

  test('rechaza un correo sin formato válido', async () => {
    // Completamos los demás datos y probamos correo electrónico con el valor 'correo-invalido'.
    const formulario = await completarFormularioValido()
    await campo(formulario, 'Correo electrónico').setValue('correo-invalido')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('correo válido')
    expect(formulario.emitted('save')).toBeUndefined()
  })

  test('detecta documentos duplicados aunque tengan separadores', async () => {
    // Escribimos el documento de un cliente que ya existe, pero con puntos.
    const formulario = await completarFormularioValido()
    await campo(formulario, 'DNI /').setValue('30.123.456')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.get('[role="alert"]').text()).toContain('Ya existe un cliente')
    expect(formulario.emitted('save')).toBeUndefined()
  })

  test('envía los datos sin espacios de más y sin cambiar la base', async () => {
    // Guardamos datos con espacios para comprobar que se envíen limpios.
    const formulario = await completarFormularioValido()
    await campo(formulario, 'Correo electrónico').setValue('  maria@example.com  ')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    // El formulario avisa que quiere guardar. Revisamos los datos de ese aviso.
    const datosEnviados = formulario.emitted('save')[0][0]
    expect(datosEnviados).toMatchObject({
      name: 'María López',
      doc: '34.567.890',
      phone: '1123456789',
      email: 'maria@example.com',
      active: true,
    })

    expect(db.value.clients).toHaveLength(3)
  })

  test('permite editar conservando el documento del propio cliente', async () => {
    // Cambiamos el nombre de un cliente sin cambiar su documento.
    const formulario = mount(Cliente, { props: { open: true, client: db.value.clients[0] } })
    await campo(formulario, 'Nombre o razón').setValue('Ana actualizada')

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')

    expect(formulario.emitted('save')[0][0]).toMatchObject({ id: 1, name: 'Ana actualizada' })
    expect(db.value.clients[0].name).toBe('Ana Pérez')
  })

  test('cancelar cierra sin guardar y volver a abrir borra los errores', async () => {
    // Cancelamos un formulario con errores y lo volvemos a abrir.
    const formulario = mount(Cliente, { props: { open: true } })

    // Enviamos el formulario.
    await formulario.get('form').trigger('submit')
    await formulario.findAll('button').find(b => b.text() === 'Cancelar').trigger('click')

    expect(formulario.emitted('close')).toHaveLength(1)
    expect(formulario.emitted('save')).toBeUndefined()

    // Cerramos y abrimos otra vez: el formulario debe quedar vacío y sin errores.
    await formulario.setProps({ open: false })
    await formulario.setProps({ open: true })

    expect(formulario.find('[role="alert"]').exists()).toBe(false)
    expect(campo(formulario, 'Nombre o razón').element.value).toBe('')
  })
})
