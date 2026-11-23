import { ref, reactive } from 'vue'
import { TextEncoder } from 'node:util'
import { mount, flushPromises } from '@vue/test-utils'
import Login from '../../app/pages/login.vue'
import Registro from '../../app/pages/registro.vue'
import Cliente from '../../app/components/clientes/ModalFormularioCliente.vue'
import Repuesto from '../../app/components/inventario/ModalRepuesto.vue'
import Vehiculo from '../../app/components/vehiculos/ModalFormularioVehiculo.vue'
import Titular from '../../app/components/vehiculos/FormularioTitular.vue'
import { campo, abrirFormulario } from './helpers'

const cliente = { id: 1, name: 'Cliente original', doc: '30123456', phone: '1123456789', email: '', active: true }
const vehiculo = { id: 10, clienteId: 1, clienteNombre: cliente.name, marca: 'Toyota', modelo: 'Hilux', patente: 'ABC123', anio: 2020, kilometraje: 0, activo: true }
let auth
beforeEach(() => {
  globalThis.TextEncoder = TextEncoder
  auth = { authorization: ref({ Authorization: 'Bearer prueba' }), user: ref({ id: 1 }), homePath: ref('/'), login: jest.fn().mockResolvedValue(), register: jest.fn().mockResolvedValue() }
  globalThis.useAuthApi = () => auth
  globalThis.useBusquedaTitulares = () => ({ resultados: ref([cliente, { ...cliente, id: 2, name: 'Nuevo titular' }]), cargando: ref(false), error: ref('') })
  globalThis.useCatalogoVehiculos = () => ({ marcaId: ref(1), modeloId: ref(2), versionId: ref(''), marcas: ref([{ id: 1, nombre: 'Toyota' }]), modelos: ref([{ id: 2, nombre: 'Hilux' }]), versiones: ref([]), cargando: reactive({ marcas: false, modelos: false, versiones: false }), errores: reactive({}), cargar: jest.fn(), abrir: jest.fn(), cerrar: jest.fn() })
  globalThis.$fetch = jest.fn()
})

test('el login exige credenciales y usa la ruta de la sesión real', async () => {
  const w = mount(Login)
  await w.get('form').trigger('submit')
  expect(auth.login).not.toHaveBeenCalled()
  await campo(w, 'Correo').setValue('usuario@example.test')
  await campo(w, 'Contraseña').setValue('clave-de-prueba')
  auth.homePath.value = '/mecanico'
  await w.get('form').trigger('submit')
  await flushPromises()
  expect(auth.login).toHaveBeenCalledWith('usuario@example.test', 'clave-de-prueba')
  expect(navigateTo).toHaveBeenCalledWith('/mecanico')
  expect(w.text()).not.toContain('Explorar la demo')
})

test('el login no abre una sesión local si falla la API', async () => {
  auth.login.mockRejectedValueOnce({ statusCode: 503 })
  const w = mount(Login)
  await campo(w, 'Correo').setValue('usuario@example.test')
  await campo(w, 'Contraseña').setValue('clave-de-prueba')
  await w.get('form').trigger('submit')
  await flushPromises()
  expect(w.get('[role="alert"]').text()).toContain('No pudimos iniciar')
  expect(navigateTo).not.toHaveBeenCalled()
  expect(w.get('button[type="submit"]').element.disabled).toBe(false)
})

test('el registro valida la contraseña y envía el taller a la API', async () => {
  const w = mount(Registro)
  await campo(w, 'Correo').setValue('usuario@example.test')
  await campo(w, 'Teléfono').setValue('1123456789')
  await campo(w, 'Contraseña').setValue('corta')
  await campo(w, 'Repetí').setValue('corta')
  await w.get('form').trigger('submit')
  expect(auth.register).not.toHaveBeenCalled()
  expect(w.get('[role="alert"]').text()).toContain('12 caracteres')
  await campo(w, 'Contraseña').setValue('clave-de-prueba')
  await campo(w, 'Repetí').setValue('clave-de-prueba')
  await w.get('form').trigger('submit')
  await campo(w, 'Nombre del taller').setValue('Taller de prueba')
  await campo(w, 'Calle').setValue('Roca')
  await campo(w, 'Altura').setValue('100')
  await w.get('form').trigger('submit')
  await flushPromises()
  expect(auth.register).toHaveBeenCalledWith('usuario@example.test', 'clave-de-prueba', { nombre: 'Taller de prueba', calle: 'Roca', altura: 100, telefono: '1123456789' })
})

test.each([['Nombre o razón', 'A'], ['DNI /', '123'], ['Teléfono', '123'], ['Correo electrónico', 'invalido']])('clientes valida %s sin consultar datos locales', async (label, invalid) => {
  const w = mount(Cliente, { props: { open: true, client: { ...cliente, vatCondition: 'Consumidor Final' } } })
  await campo(w, label).setValue(invalid)
  await w.get('form').trigger('submit')
  expect(campo(w, label).attributes('aria-invalid')).toBe('true')
  expect(w.emitted('save')).toBeUndefined()
  expect(useDatabase).not.toHaveBeenCalled()
})

test.each([true, false])('repuestos confirma el alta únicamente si la API acepta: %s', async success => {
  if (success) $fetch.mockResolvedValue({ id: 1, descripcion: 'Filtro', marca: 'Mann', codigoOem: 'W712', precioCosto: 100, margen: 20, precioPublico: 120, stock: 0, stockMinimo: 2, activo: true, compatibilidades: [] })
  else $fetch.mockRejectedValue({ statusCode: 503 })
  const w = await abrirFormulario(Repuesto)
  await w.get('form').trigger('submit')
  expect($fetch).not.toHaveBeenCalled()
  await campo(w, 'Descripción').setValue('Filtro')
  await campo(w, 'Marca').setValue('Mann')
  await campo(w, 'Código OEM').setValue('W712')
  await campo(w, 'Costo').setValue('100')
  await campo(w, 'Margen').setValue('20')
  await w.get('form').trigger('submit')
  await flushPromises()
  expect($fetch).toHaveBeenCalledWith('/api/repuestos', expect.objectContaining({ method: 'POST', body: expect.objectContaining({ descripcion: 'Filtro', precioCosto: 100, margen: 20 }) }))
  expect(!!w.emitted('created')).toBe(success)
  expect(useDatabase).not.toHaveBeenCalled()
})

test.each([true, false])('vehículos valida el formato y guarda únicamente en la API: %s', async success => {
  if (success) $fetch.mockResolvedValue(vehiculo)
  else $fetch.mockRejectedValue({ statusCode: 503 })
  const w = await abrirFormulario(Vehiculo)
  await w.get('.client-search-box input').setValue('Cliente')
  await w.findAll('.client-result-item')[0].trigger('click')
  await campo(w, 'Patente').setValue('invalida')
  await w.get('form').trigger('submit')
  expect($fetch).not.toHaveBeenCalled()
  await campo(w, 'Patente').setValue('abc123')
  await w.get('form').trigger('submit')
  await flushPromises()
  expect($fetch).toHaveBeenCalledWith('/api/vehiculos', expect.objectContaining({ method: 'POST', body: expect.objectContaining({ clienteId: 1, modeloId: 2, patente: 'ABC-123' }) }))
  expect(!!w.emitted('created')).toBe(success)
  expect(useDatabase).not.toHaveBeenCalled()
})

test.each([true, false])('cambiar titular conserva los datos locales y confirma solo la API: %s', async success => {
  const vehicle = { id: 10, client: 1, plate: 'ABC123', brand: 'Toyota', model: 'Hilux', registro: { titular: 'Cliente original' } }
  const original = JSON.parse(JSON.stringify(vehicle))
  if (success) $fetch.mockResolvedValue({})
  else $fetch.mockRejectedValue({ statusCode: 409 })
  const w = mount(Titular, { props: { vehicle } })
  await w.get('form').trigger('submit')
  expect($fetch).not.toHaveBeenCalled()
  await w.get('input').setValue('titular')
  expect(w.findAll('.selection-results button')).toHaveLength(1)
  await w.get('.selection-results button').trigger('click')
  await w.get('form').trigger('submit')
  await flushPromises()
  expect($fetch).toHaveBeenCalledWith('/api/vehiculos/10/titular', expect.objectContaining({ method: 'PATCH', body: { clienteId: 2 } }))
  expect(!!w.emitted('saved')).toBe(success)
  expect(vehicle).toEqual(original)
})
