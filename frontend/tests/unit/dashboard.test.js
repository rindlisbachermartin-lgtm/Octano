import { ref, computed } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import Dashboard from '../../app/pages/index.vue'
import { notificarCambioDashboard } from '../../app/composables/useDashboardCambios'

let auth
const turno = (id, horaInicio) => ({ id, horaInicio, fecha: '2026-11-22', patente: `QR${id}`, marca: 'Toyota', modelo: 'Hilux', clienteNombre: 'Cliente real', motivoVisita: 'Service' })
const orden = estado => ({ id: estado === 'EN_PROCESO' ? 1 : 2, estado, patente: 'ABC123', marca: 'Toyota', modelo: 'Hilux', descripcion: 'Revisión de frenos', mecanicoNombre: 'Mecánico real' })
function respuesta(url, options = {}) {
  const estado = options.query?.estado
  if (url === '/api/ordenes-trabajo') return { contenido: [orden(estado)], totalElementos: { EN_PROCESO: 12, EN_ESPERA: 5, PENDIENTE_INGRESO: 9 }[estado], totalPaginas: 3 }
  if (url === '/api/turnos') return { contenido: estado === 'PROGRAMADO' ? [turno(10, '11:00:00'), turno(11, '09:00:00')] : [turno(12, '08:00:00')], totalElementos: estado === 'PROGRAMADO' ? 8 : 3 }
  if (url === '/api/repuestos') return { contenido: [{ id: 1, descripcion: 'Filtro de aceite', codigoOem: 'F1', marca: 'Marca real', stock: 0, stockMinimo: 2 }], totalElementos: 7 }
  if (url === '/api/reportes/resumen') return { ingresos: 33000, cantidadCobros: 3, comprobantesPorCobrar: { cantidad: 2, importe: 5000 }, ingresosPorConcepto: { repuestos: 12000, trabajos: 21000 } }
  throw new Error(`Ruta inesperada: ${url}`)
}
beforeEach(() => {
  auth = { authorization: ref({ Authorization: 'Bearer prueba' }), user: ref({ rol: 'ADMIN' }), workshopName: ref('Taller real') }
  auth.puedeAdministrar = computed(() => auth.user.value?.rol === 'ADMIN')
  Object.assign(globalThis, {
    useAuthApi: () => auth,
    useWorkshopDay: () => ({ today: ref('2026-11-22'), label: ref('domingo, 22 de noviembre de 2026') }),
    $fetch: jest.fn(async (url, options) => respuesta(url, options)),
  })
})
const montar = () => mount(Dashboard, { global: { stubs: { OrdenesDetalleApi: true, OrdenesFormularioApi: true } } })

test('usa totales del servidor, no el tamaño de la página, y ordena los turnos por hora', async () => {
  const w = montar()
  await flushPromises()
  expect(w.findAll('.metric-value').map(item => item.text())).toEqual(['17', '11', '9', '7'])
  expect(w.findAll('.metric-card')[0].text()).toContain('Órdenes en taller')
  expect(w.text()).toContain('Taller real')
  expect(w.text()).toContain('Mecánico real')
  expect(w.text()).toContain('Mostrando 1 de 12')
  expect(w.findAll('.appointment-link time').map(item => item.text())).toEqual(['08:00', '09:00', '11:00'])
  expect(w.findAll('.stock-list .stock-quantity')[0].text()).toContain('Sin stock')
  const comparativo = w.findAll('.comparison-row')
  expect(comparativo).toHaveLength(2)
  expect(comparativo[0].text()).toContain('Venta de repuestos')
  expect(comparativo[0].text()).toContain('36%')
  expect(comparativo[1].text()).toContain('Trabajos')
  expect(comparativo[1].text()).toContain('64%')
  expect($fetch).toHaveBeenCalledWith('/api/reportes/resumen', expect.objectContaining({ headers: auth.authorization.value, query: { desde: '2026-11-01', hasta: '2026-11-22' } }))
  expect($fetch).toHaveBeenCalledWith('/api/turnos', expect.objectContaining({ query: expect.objectContaining({ desde: '2026-11-22', hasta: '2026-11-22', estado: 'PROGRAMADO' }) }))
  expect(useDatabase).not.toHaveBeenCalled()
  expect(useOwnerAccount).not.toHaveBeenCalled()
})

test('sin ingresos el comparativo muestra cero por ciento en ambos conceptos', async () => {
  $fetch.mockImplementation(async (url, options) => url === '/api/reportes/resumen'
    ? { ...respuesta(url, options), ingresos: 0, ingresosPorConcepto: { repuestos: 0, trabajos: 0 } }
    : respuesta(url, options))
  const w = montar()
  await flushPromises()
  expect(w.findAll('.comparison-row small').map(item => item.text())).toEqual(['0%', '0%'])
})

test('recepción accede a la operación sin solicitar reportes financieros', async () => {
  auth.user.value.rol = 'RECEPCION'
  const w = montar()
  await flushPromises()
  expect(w.findAll('.metric-value').map(item => item.text())).toEqual(['17', '11', '9', '7'])
  expect(w.find('.finance-panel').exists()).toBe(false)
  expect($fetch.mock.calls.some(([url]) => url.startsWith('/api/reportes'))).toBe(false)
})

test('un error de stock no oculta las órdenes y no se muestra como stock cero', async () => {
  $fetch.mockImplementation(async (url, options) => {
    if (url === '/api/repuestos') throw { statusCode: 503 }
    return respuesta(url, options)
  })
  const w = montar()
  await flushPromises()
  expect(w.findAll('.metric-value')[3].text()).toBe('—')
  expect(w.text()).toContain('No disponible')
  expect(w.text()).toContain('Mecánico real')
  $fetch.mockImplementation(async (url, options) => respuesta(url, options))
  await w.findAll('button').find(button => button.text() === 'Reintentar stock').trigger('click')
  await flushPromises()
  expect(w.findAll('.metric-value')[3].text()).toBe('7')
  expect(w.find('[role="alert"]').exists()).toBe(false)
})

test('los estados vacíos proceden de respuestas vacías de la API', async () => {
  $fetch.mockImplementation(async (url, options) => {
    const result = respuesta(url, options)
    return url === '/api/reportes/resumen' ? result : { ...result, contenido: [], totalElementos: 0 }
  })
  const w = montar()
  await flushPromises()
  expect(w.findAll('.metric-value').map(item => item.text())).toEqual(['0', '0', '0', '0'])
  expect(w.text()).toContain('No hay trabajos en proceso')
  expect(w.text()).toContain('No quedan turnos pendientes')
  expect(w.text()).toContain('Sin repuestos por debajo')
})

test('las pestañas abren el estado correcto y el detalle de la orden real', async () => {
  const w = montar()
  await flushPromises()
  await w.findAll('.work-tabs button')[1].trigger('click')
  expect(w.text()).toContain('Mostrando 1 de 5')
  await w.get('[aria-label="Ver orden 2, ABC123"]').trigger('click')
  expect(w.get('ordenes-detalle-api-stub').attributes('id')).toBe('2')
  await w.findAll('button').find(button => button.text() === 'Nueva orden').trigger('click')
  expect(w.find('ordenes-formulario-api-stub').exists()).toBe(true)
})

test('sin sesión no consulta datos ni inventa indicadores', async () => {
  auth.authorization.value = undefined
  auth.user.value = null
  const w = montar()
  await flushPromises()
  expect($fetch).not.toHaveBeenCalled()
  expect(w.findAll('.metric-value').map(item => item.text())).toEqual(['—', '—', '—', '—'])
})

test('descarta respuestas de una sesión anterior aunque terminen después de la nueva', async () => {
  const pendientes = []
  auth.authorization.value = { Authorization: 'Bearer anterior' }
  $fetch.mockImplementation((url, options) => {
    if (options.headers.Authorization !== 'Bearer anterior') return Promise.resolve(respuesta(url, options))
    return new Promise(resolve => pendientes.push(() => resolve({ ...respuesta(url, options), totalElementos: 999 })))
  })
  const w = montar()
  await flushPromises()
  const primeraSignal = $fetch.mock.calls[0][1].signal
  auth.authorization.value = { Authorization: 'Bearer nuevo' }
  await flushPromises()
  expect(primeraSignal.aborted).toBe(true)
  pendientes.forEach(resolver => resolver())
  await flushPromises()
  expect(w.findAll('.metric-value').map(item => item.text())).toEqual(['17', '11', '9', '7'])
})

test('recarga automáticamente cuando otro módulo notifica un cambio confirmado', async () => {
  const w = montar()
  await flushPromises()
  const llamadasIniciales = $fetch.mock.calls.length
  notificarCambioDashboard()
  await flushPromises()
  expect($fetch.mock.calls.length).toBeGreaterThan(llamadasIniciales)
  expect(w.findAll('.metric-value').map(item => item.text())).toEqual(['17', '11', '9', '7'])
})
