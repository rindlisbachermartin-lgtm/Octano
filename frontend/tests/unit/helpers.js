import { ref } from 'vue'
import { mount } from '@vue/test-utils'

// Crea datos de ejemplo. Cada prueba recibe su propia copia y no toca el navegador.
export function prepararBaseDePrueba() {
  const db = ref({
    clients: [
      { id: 1, name: 'Ana Pérez', doc: '30123456', phone: '1123456789', email: '', active: true, vatCondition: 'Consumidor Final' },
      { id: 2, name: 'Luis Gómez', doc: '28456789', phone: '1198765432', email: '', active: true },
      { id: 3, name: 'Cliente inactivo', doc: '20123456', active: false },
    ],
    vehicles: [{ id: 10, client: 1, plate: 'ABC-123', brand: 'Toyota', model: 'Hilux', year: 2020, engine: '2.8 TDI', km: 50000, qrCode: 'qr-existente' }],
    parts: [], orders: [], quotes: [], appointments: [],
  })
  useDatabase.mockReturnValue({ db })
  return { db }
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
