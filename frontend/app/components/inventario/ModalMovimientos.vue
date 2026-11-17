<script setup lang="ts">
import { X } from '@lucide/vue'
import type { Part } from '~/types'
import type { PaginaRepuestos } from '~/utils/repuestoApi'

type Tipo = 'ENTRADA' | 'SALIDA' | 'AJUSTE'
interface Movimiento {
  id: number
  tipo: Tipo
  cantidad: number
  stockPosterior: number
  fecha: string
  motivo: string
}

const props = defineProps<{ part: Part; editable: boolean }>()
const emit = defineEmits<{ close: []; updated: [] }>()
const auth = useAuthApi()
const tipo = ref<Tipo>('ENTRADA')
const cantidad = ref<number | ''>('')
const motivo = ref('')
const stock = ref(props.part.stock)
const listado = ref<PaginaRepuestos<Movimiento>>()
const cargando = ref(false)
const guardando = ref(false)
const error = ref('')
const errorAlta = ref('')
const enviado = ref(false)
const etiquetas = { ENTRADA: 'Entrada', SALIDA: 'Salida', AJUSTE: 'Ajuste' }
const recurso = `/api/repuestos/${props.part.id}/movimientos`
const cantidadValida = computed(() => typeof cantidad.value === 'number'
  && /^\d{1,12}(?:\.\d{1,3})?$/.test(String(cantidad.value))
  && (tipo.value === 'AJUSTE' || cantidad.value > 0))

async function cargar(pagina = 0) {
  const headers = auth.authorization.value
  if (!headers || cargando.value) return
  cargando.value = true
  error.value = ''
  try {
    listado.value = await $fetch<PaginaRepuestos<Movimiento>>(recurso, { headers, query: { pagina } })
    if (pagina === 0 && listado.value.contenido[0]) stock.value = listado.value.contenido[0].stockPosterior
  } catch (err) {
    error.value = authError(err, 'No pudimos cargar los movimientos.')
  } finally {
    cargando.value = false
  }
}

async function registrar() {
  enviado.value = true
  errorAlta.value = ''
  const headers = auth.authorization.value
  if (!props.editable || !headers || guardando.value || cargando.value || !cantidadValida.value || !motivo.value.trim()) return
  guardando.value = true
  try {
    const movimiento = await $fetch<Movimiento>(recurso, {
      method: 'POST', headers, body: { tipo: tipo.value, cantidad: cantidad.value, motivo: motivo.value.trim() },
    })
    stock.value = movimiento.stockPosterior
    cantidad.value = ''
    motivo.value = ''
    enviado.value = false
    emit('updated')
    await cargar()
  } catch (err) {
    errorAlta.value = authError(err, 'No pudimos registrar el movimiento.')
  } finally {
    guardando.value = false
  }
}

function cerrar() {
  if (!guardando.value) emit('close')
}

useModalEscape(() => true, cerrar)
onMounted(() => cargar())
</script>

<template>
  <CommonFormPage>
    <div class="dialog-header">
      <h2>Movimientos de stock</h2>
      <button type="button" class="icon-button" aria-label="Cerrar" :disabled="guardando" @click="cerrar"><X :size="18" /></button>
    </div>
    <form v-if="editable" class="entry-form" novalidate @submit.prevent="registrar">
      <div class="form-fields">
        <p><strong>{{ part.name }}</strong> · Stock actual: {{ stock }}</p>
        <div class="form-grid">
          <label>Movimiento
            <select v-model="tipo" :disabled="guardando">
              <option v-for="(etiqueta, clave) in etiquetas" :key="clave" :value="clave">{{ etiqueta }}</option>
            </select>
          </label>
          <label>{{ tipo === 'AJUSTE' ? 'Nuevo saldo' : 'Cantidad' }}
            <input v-model.number="cantidad" type="number" min="0" step="0.001" :disabled="guardando">
            <CommonFormatoInvalido v-if="(enviado || cantidad !== '') && !cantidadValida" />
          </label>
        </div>
        <label>Motivo
          <input v-model="motivo" maxlength="250" :disabled="guardando">
          <CommonFormatoInvalido v-if="enviado && !motivo.trim()" />
        </label>
        <p v-if="errorAlta" role="alert">{{ errorAlta }}</p>
        <button type="submit" class="button primary" :disabled="guardando || cargando">Registrar movimiento</button>
      </div>
    </form>
    <div class="detail-body">
      <p v-if="!editable"><strong>{{ part.name }}</strong> · Stock actual: {{ stock }}</p>
      <p v-if="cargando" role="status">Cargando movimientos…</p>
      <p v-else-if="error" role="alert">{{ error }} <button type="button" class="button" @click="cargar()">Reintentar</button></p>
      <div v-else-if="listado?.contenido.length" class="table-scroll">
        <table>
          <thead><tr><th>Fecha</th><th>Tipo</th><th>Cantidad</th><th>Saldo</th><th>Motivo</th></tr></thead>
          <tbody>
            <tr v-for="m in listado.contenido" :key="m.id">
              <td>{{ new Date(m.fecha).toLocaleString('es-AR') }}</td><td>{{ etiquetas[m.tipo] }}</td>
              <td>{{ m.cantidad }}</td><td>{{ m.stockPosterior }}</td><td>{{ m.motivo }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else class="muted">No hay movimientos registrados.</p>
      <div v-if="listado && listado.totalPaginas > 1" class="modal-footer">
        <button type="button" class="button" :disabled="cargando || guardando || listado.pagina === 0" @click="cargar(listado.pagina - 1)">Anterior</button>
        <button type="button" class="button" :disabled="cargando || guardando || listado.pagina + 1 >= listado.totalPaginas" @click="cargar(listado.pagina + 1)">Siguiente</button>
      </div>
    </div>
  </CommonFormPage>
</template>
