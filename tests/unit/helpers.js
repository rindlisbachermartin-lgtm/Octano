import { ref } from 'vue'
import { DEMO_VEHICLE_CATALOG } from '../../app/utils/vehicleCatalog'

export function mockDatabase() {
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
  const createOrder = jest.fn((vehicle, service, mechanic) => {
    db.value.orders.push({ id: 100, vehicle, service, mechanic, parts: [], status: 'En espera' })
    return 100
  })
  useDatabase.mockReturnValue({ db, client: id => db.value.clients.find(c => c.id === id), createOrder })
  return { db, createOrder }
}

export function mockAuth() {
  const auth = {
    initialize: jest.fn(), login: jest.fn().mockResolvedValue(undefined),
    loginMechanic: jest.fn().mockResolvedValue(undefined), register: jest.fn().mockResolvedValue(undefined),
    owner: ref(null), account: ref(null), homePath: ref('/'), isMechanic: ref(false),
  }
  useOwnerAccount.mockReturnValue(auth)
  return auth
}

export async function openForm(component, props = {}) {
  const { mount } = await import('@vue/test-utils')
  const wrapper = mount(component, { props: { ...props, open: false } })
  await wrapper.setProps({ open: true })
  return wrapper
}

export function field(wrapper, label) {
  const input = wrapper.findAll('label').find(node => node.text().includes(label))
  if (!input) throw new Error(`No se encontró el campo: ${label}`)
  return input.get('input, select, textarea')
}

export async function search(wrapper, selector, value) {
  jest.useFakeTimers()
  await wrapper.get(selector).setValue(value)
  await jest.advanceTimersByTimeAsync(300)
}
