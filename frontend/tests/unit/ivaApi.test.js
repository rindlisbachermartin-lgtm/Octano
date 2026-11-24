import { ref } from 'vue'
import { flushPromises } from '@vue/test-utils'
import { etiquetaIva, valoresIva } from '../../app/utils/ivaApi'
import { useAliquotasIva } from '../../app/composables/useAliquotasIva'

const catalogo = [{ id: 1, porcentaje: 0 }, { id: 2, porcentaje: 10.5 }, { id: 3, porcentaje: 21 }]

test('muestra 10,5% con coma y conserva un porcentaje histórico que ya no está en el catálogo', () => {
  expect(catalogo.map(item => etiquetaIva(item.porcentaje))).toEqual(['0%', '10,5%', '21%'])
  expect(valoresIva(catalogo, 10.5)).toEqual([0, 10.5, 21])
  expect(valoresIva(catalogo, 6)).toEqual([6, 0, 10.5, 21])
})

test('carga las alícuotas desde la API con la autorización de la sesión', async () => {
  const headers = { Authorization: 'Bearer prueba' }
  globalThis.useAuthApi = () => ({ authorization: ref(headers) })
  globalThis.$fetch = jest.fn(async () => catalogo)
  const { aliquotas, error } = useAliquotasIva()
  await flushPromises()
  expect($fetch).toHaveBeenCalledWith('/api/aliquotas-iva', { headers })
  expect(aliquotas.value).toEqual(catalogo)
  expect(error.value).toBe('')
})

test('si falla la consulta, no inventa alícuotas y muestra un error accionable', async () => {
  globalThis.useAuthApi = () => ({ authorization: ref(undefined) })
  globalThis.$fetch = jest.fn(async () => { throw { statusCode: 503 } })
  const { aliquotas, error } = useAliquotasIva()
  await flushPromises()
  expect(aliquotas.value).toEqual([])
  expect(error.value).toContain('No pudimos cargar')
})
