import { ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import Personal from '../../app/pages/personal.vue'

test('Personal filtra tras 300 ms sin nuevas peticiones y restaura la lista al borrar', async () => {
  globalThis.useAuthApi = () => ({ authorization: ref({ Authorization: 'prueba' }), user: ref({ id: 1 }) })
  globalThis.useWorkshopDay = () => ({ today: ref('2026-11-27') })
  globalThis.$fetch = jest.fn(async url => url === '/api/usuarios' ? [
    { id: 1, email: 'ana@example.test', rol: 'ADMIN', dueno: true, activo: true, mecanicoId: null },
    { id: 2, email: 'luis@example.test', rol: 'RECEPCION', dueno: false, activo: true, mecanicoId: null },
  ] : [])
  const pagina = mount(Personal, { global: { stubs: { CommonFormatoInvalido: true, CommonModalDialog: true } } })
  await flushPromises()
  const filas = () => pagina.findAll('tbody tr')
  expect(filas()).toHaveLength(2)
  jest.useFakeTimers()
  const input = pagina.get('[aria-label="Buscar personal"]')
  await input.setValue('a')
  await jest.advanceTimersByTimeAsync(200)
  await input.setValue('ana')
  await jest.advanceTimersByTimeAsync(299)
  expect(filas()).toHaveLength(2)
  await jest.advanceTimersByTimeAsync(1)
  expect(filas()).toHaveLength(1)
  expect(filas()[0].text()).toContain('ana@example.test')
  await input.setValue('')
  expect(filas()).toHaveLength(2)
  expect($fetch).toHaveBeenCalledTimes(2)
})
