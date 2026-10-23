import { ref } from 'vue'
import { mount } from '@vue/test-utils'
import { DEMO_VEHICLE_CATALOG } from '../../app/utils/vehicleCatalog'

// Crea datos de ejemplo. Cada prueba recibe su propia copia y no toca el navegador.
export function prepararBaseDePrueba() {
  const db = ref({
    clients: [
      { id: 1, name: 'Ana Pérez', doc: '30123456', phone: '1123456789', email: '', active: true, vatCondition: 'Consumidor Final' },
      { id: 2, name: 'Luis Gómez', doc: '28456789', phone: '1198765432', email: '', active: true },
      { id: 3, name: 'Cliente inactivo', doc: '20123456', active: false },
    ],
    vehicles: [{ id: 10, client: 1, plate: 'ABC-123', brand: 'Toyota', model: 'Hilux', year: 2020, engine: '2.8 TDI', km: 50000, qrCode: 'qr-existente' }],
    vehicleCatalog: DEMO_VEHICLE_CATALOG,
    parts: [], orders: [], quotes: [], appointments: [],
  })
  // Esta función de mentira nos permite saber si el formulario intentó crear una orden.
  const createOrder = jest.fn((vehicle, service, mechanic) => {
    db.value.orders.push({ id: 100, vehicle, service, mechanic, parts: [], status: 'En espera' })
    return 100
  })
  useDatabase.mockReturnValue({ db, client: id => db.value.clients.find(c => c.id === id), createOrder })
  return { db, createOrder }
}

// Simula el acceso a una cuenta sin necesitar usuarios reales.
export function prepararSesionDePrueba() {
  const sesion = {
    initialize: jest.fn(),
    login: jest.fn().mockResolvedValue(undefined),
    loginMechanic: jest.fn().mockResolvedValue(undefined),
    register: jest.fn().mockResolvedValue(undefined),
    owner: ref(null),
    account: ref(null),
    homePath: ref('/'),
    isMechanic: ref(false),
  }
  useOwnerAccount.mockReturnValue(sesion)
  return sesion
}

// Abre el formulario como lo hace la aplicación: primero cerrado y después abierto.
export async function abrirFormulario(componente, props = {}) {
  const formulario = mount(componente, { props: { ...props, open: false } })
  await formulario.setProps({ open: true })
  return formulario
}

// Busca un campo por el texto que aparece al lado, por ejemplo "Teléfono".
export function campo(formulario, nombre) {
  const etiqueta = formulario.findAll('label').find(elemento => elemento.text().includes(nombre))
  if (!etiqueta) throw new Error(`No se encontró el campo: ${nombre}`)
  return etiqueta.get('input, select, textarea')
}

// Escribe una búsqueda y adelanta la espera de 300 ms que tiene el buscador.
export async function buscar(formulario, selector, texto) {
  jest.useFakeTimers()
  await formulario.get(selector).setValue(texto)
  await jest.advanceTimersByTimeAsync(300)
}
