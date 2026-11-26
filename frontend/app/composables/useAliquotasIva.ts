import { authError } from './useAuthApi'
import type { AliquotaIva } from '~/utils/ivaApi'

export function useAliquotasIva() {
  const { authorization } = useAuthApi()
  const aliquotas = ref<AliquotaIva[]>([])
  const error = ref('')

  async function cargar() {
    try {
      aliquotas.value = await $fetch<AliquotaIva[]>('/api/aliquotas-iva', { headers: authorization.value ?? undefined })
      error.value = ''
    } catch (err) {
      error.value = authError(err, 'No pudimos cargar las alícuotas de IVA.')
    }
  }

  void cargar()
  return { aliquotas, error, cargar }
}
