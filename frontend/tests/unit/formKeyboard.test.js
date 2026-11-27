import { mount } from '@vue/test-utils'
import { avanzarConEnter } from '../../app/utils/formKeyboard'
import Cliente from '../../app/components/clientes/ModalFormularioCliente.vue'
import { inicialesNombre, inicialMayuscula } from '../../app/utils/inicialMayuscula'

let contenedor
beforeEach(() => {
  contenedor = document.createElement('div')
  document.body.append(contenedor)
  // JSDOM no calcula geometría; simulamos los controles que tienen layout.
  jest.spyOn(HTMLElement.prototype, 'getClientRects').mockImplementation(function () {
    return this.closest('[data-sin-layout]') ? [] : [{}]
  })
})
afterEach(() => {
  contenedor.remove()
  jest.restoreAllMocks()
})

function formulario(html) {
  contenedor.innerHTML = `<form>${html}</form>`
  const form = contenedor.querySelector('form')
  form.addEventListener('keydown', avanzarConEnter)
  return form
}

function enter(campo, opciones = {}) {
  campo.focus()
  const evento = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true, ...opciones })
  campo.dispatchEvent(evento)
  return evento
}

test('Enter recorre los campos editables y en el último bloquea el envío', () => {
  const form = formulario('<input id="nombre"><select id="iva"><option>21%</option></select><input id="telefono"><button type="submit">Guardar</button>')
  const [nombre, iva, telefono] = form.querySelectorAll('input, select')
  expect(enter(nombre).defaultPrevented).toBe(true)
  expect(document.activeElement).toBe(iva)
  enter(iva)
  expect(document.activeElement).toBe(telefono)
  expect(enter(telefono).defaultPrevented).toBe(true)
  expect(document.activeElement).toBe(telefono)
})

test('omite controles deshabilitados, ocultos, de solo lectura y acciones intermedias', () => {
  const form = formulario(`
    <input id="primero"><button type="button">Ver contraseña</button>
    <fieldset disabled><input></fieldset><input readonly><input type="hidden">
    <div hidden><input></div><div inert><input></div><div aria-hidden="true"><input></div>
    <div data-sin-layout><input></div><input style="visibility:hidden"><input tabindex="-1">
    <input id="siguiente"><input type="submit" value="Guardar">`)
  enter(form.querySelector('#primero'))
  expect(document.activeElement).toBe(form.querySelector('#siguiente'))
  enter(form.querySelector('#siguiente'))
  expect(document.activeElement).toBe(form.querySelector('#siguiente'))
})

test('conserva los saltos de línea y la activación explícita de botones', () => {
  const form = formulario('<input><textarea></textarea><button type="submit">Guardar</button>')
  enter(form.querySelector('input'))
  expect(document.activeElement).toBe(form.querySelector('textarea'))
  expect(enter(form.querySelector('textarea')).defaultPrevented).toBe(false)
  expect(enter(form.querySelector('button')).defaultPrevented).toBe(false)
})

test('incluye selectores de fecha y permite abrirlos con Enter', () => {
  const form = formulario('<input><button type="button" aria-haspopup="dialog">Fecha</button><input>')
  const selector = form.querySelector('button')
  enter(form.querySelector('input'))
  expect(document.activeElement).toBe(selector)
  expect(enter(selector).defaultPrevented).toBe(false)
})

test('respeta Enter ya manejado por un selector, la composición y los modificadores', () => {
  const form = formulario('<input><input>')
  const campo = form.querySelector('input')
  for (const opciones of [{ isComposing: true }, { shiftKey: true }, { ctrlKey: true }, { altKey: true }, { metaKey: true }, { key: 'Tab' }]) {
    expect(enter(campo, opciones).defaultPrevented).toBe(false)
    expect(document.activeElement).toBe(campo)
  }
  expect(enter(campo, { repeat: true }).defaultPrevented).toBe(true)
  expect(document.activeElement).toBe(campo)
  campo.addEventListener('keydown', evento => evento.preventDefault(), { once: true })
  enter(campo)
  expect(document.activeElement).toBe(campo)
})

test('el formulario real de cliente avanza con Enter y conserva Guardar', async () => {
  const cliente = mount(Cliente, { attachTo: contenedor,
    global: { mocks: { inicialesNombre, inicialMayuscula }, stubs: { CommonFormatoInvalido: true } },
    props: {
    open: true,
    client: { name: 'Ana Pérez', doc: '30123456', phone: '1123456789', email: '', active: true, vatCondition: 'Consumidor Final' },
  } })
  const nombre = cliente.get('[aria-label="Nombre o razón social"]').element
  const documento = cliente.get('[aria-label="DNI / CUIT / CUIL"]').element
  expect(enter(nombre).defaultPrevented).toBe(true)
  expect(document.activeElement).toBe(documento)
  expect(cliente.emitted('save')).toBeUndefined()
  await cliente.get('button[type="submit"]').trigger('click')
  expect(cliente.emitted('save')).toHaveLength(1)
})
