<script setup lang="ts">
import { Check, X, Search, Plus, Trash2 } from '@lucide/vue'
import { authError } from '~/composables/useAuthApi'
import type { SolicitudComprobante } from '~/composables/useComprobantes'
import type { ComprobanteApi, OrdenPendienteApi } from '~/utils/comprobanteApi'

interface VehiculoOpcion {
  id: number
  patente: string
  marca: string
  modelo: string
  clienteNombre: string
}

interface ItemManual {
  repuestoId: number | null
  descripcion: string
  cantidad: number
  precioUnitario: number
}

const props = defineProps<{
  open: boolean
  orden?: OrdenPendienteApi | null
  comprobante?: ComprobanteApi | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'guardar', solicitud: SolicitudComprobante, id?: number): void
}>()

const auth = useAuthApi()
useModalEscape(() => props.open, () => emit('close'))

const vehiculoFijo = computed(() => props.comprobante?.vehiculoId ?? props.orden?.vehiculoId ?? null)
const vehiculoSeleccionado = ref<VehiculoOpcion | null>(null)
const vehiculoSearch = ref('')
const debouncedSearch = useDebouncedValue(vehiculoSearch)
const opciones = ref<VehiculoOpcion[]>([])
const buscando = ref(false)
const errorBusqueda = ref('')
const formError = ref('')
let consulta: AbortController | undefined

const form = ref({
  descripcion: '',
  manoObra: 0,
  porcentajeIva: 21,
  items: [] as ItemManual[],
})

const dinero = (valor: number) =>
  Number(valor || 0).toLocaleString('es-AR', { style: 'currency', currency: 'ARS', minimumFractionDigits: 2, maximumFractionDigits: 2 })

const materiales = computed(() => form.value.items.reduce((sum, item) =>
  sum + Number(item.cantidad || 0) * Number(item.precioUnitario || 0), 0))
const subtotal = computed(() => Number(form.value.manoObra || 0) + materiales.value)
const montoIva = computed(() => Math.round(subtotal.value * Number(form.value.porcentajeIva || 0) * 100) / 10000)
const total = computed(() => Math.round((subtotal.value + montoIva.value) * 100) / 100)

watch(debouncedSearch, async (texto) => {
  consulta?.abort()
  opciones.value = []
  errorBusqueda.value = ''
  const q = texto.trim()
  const headers = auth.authorization.value
  if (!q || !headers || vehiculoFijo.value) return
  consulta = new AbortController()
  buscando.value = true
  try {
    const respuesta = await $fetch<{ contenido: {
      id: number; patente: string; marca: string; modelo: string; clienteNombre: string
    }[] }>('/api/vehiculos', {
      headers, query: { q, activo: true, tamano: 20 }, signal: consulta.signal,
    })
    if (!consulta.signal.aborted) opciones.value = respuesta.contenido
  } catch (err) {
    if (!consulta.signal.aborted) errorBusqueda.value = authError(err, 'No pudimos buscar vehículos.')
  } finally {
    if (!consulta.signal.aborted) buscando.value = false
  }
})

watch(() => [props.open, props.orden?.ordenId, props.comprobante?.id] as const, () => {
  if (!props.open) return
  consulta?.abort()
  const c = props.comprobante
  form.value = {
    descripcion: c?.descripcion ?? props.orden?.descripcion ?? '',
    manoObra: Number(c?.manoObra ?? 0),
    porcentajeIva: Number(c?.porcentajeIva ?? 21),
    items: (c?.items ?? []).map((item) => ({
      repuestoId: item.repuestoId,
      descripcion: item.descripcion,
      cantidad: Number(item.cantidad),
      precioUnitario: Number(item.precioUnitario),
    })),
  }
  vehiculoSeleccionado.value = null
  vehiculoSearch.value = ''
  opciones.value = []
  formError.value = ''
  errorBusqueda.value = ''
})

function seleccionarVehiculo(v: VehiculoOpcion) {
  vehiculoSeleccionado.value = v
  vehiculoSearch.value = ''
  opciones.value = []
}

function agregarItem() {
  form.value.items.push({ repuestoId: null, descripcion: '', cantidad: 1, precioUnitario: 0 })
}

function quitarItem(index: number) {
  form.value.items.splice(index, 1)
}

function submit() {
  formError.value = ''
  const vehiculoId = vehiculoFijo.value ?? vehiculoSeleccionado.value?.id
  if (!vehiculoId) {
    formError.value = 'Buscá y seleccioná el vehículo del cliente.'
    return
  }
  if (!form.value.descripcion.trim()) {
    formError.value = 'Ingresá el concepto del trabajo.'
    return
  }
  if (!Number.isFinite(total.value) || total.value <= 0) {
    formError.value = 'El total debe ser mayor a cero. Agregá mano de obra o repuestos.'
    return
  }
  if (form.value.items.some((item) => !item.descripcion.trim() || !(Number(item.cantidad) > 0) || !(Number(item.precioUnitario) >= 0))) {
    formError.value = 'Revisá los renglones: descripción, cantidad y precio unitario deben ser válidos.'
    return
  }

  const solicitud: SolicitudComprobante = {
    ordenId: props.orden?.ordenId ?? props.comprobante?.ordenId ?? null,
    vehiculoId,
    descripcion: form.value.descripcion.trim(),
    manoObra: Number(form.value.manoObra || 0),
    porcentajeIva: Number(form.value.porcentajeIva || 0),
    items: form.value.items.map((item) => ({
      ...(item.repuestoId ? { repuestoId: item.repuestoId } : {}),
      descripcion: item.descripcion.trim(),
      cantidad: Number(item.cantidad),
      precioUnitario: Number(item.precioUnitario),
    })),
  }
  emit('guardar', solicitud, props.comprobante?.id)
}

onBeforeUnmount(() => consulta?.abort())
</script>

<template>
  <CommonFormPage v-if="open" class="comprobante-manual-form">
    <div class="dialog-header">
      <div>
        <span class="eyebrow">FACTURACIÓN INTERNA</span>
        <h2>
          {{ comprobante ? `Editar borrador #${comprobante.id}` : orden ? `Nuevo comprobante · OT #${orden.ordenId}` : 'Nuevo comprobante manual' }}
        </h2>
      </div>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')"><X :size="18" /></button>
    </div>

    <form class="entry-form" @submit.prevent="submit">
      <div class="form-fields">
        <div class="integration-notice">
          Comprobante interno, sin validez fiscal. No se emite ARCA ni se genera CAE.
        </div>

        <div class="field-block">
          <label class="block-label">Vehículo y cliente titular <span class="required-star">*</span></label>
          <div v-if="vehiculoFijo" class="vehiculo-fijo">
            <span class="plate small-plate">{{ orden?.patente ?? comprobante?.vehiculo?.patente }}</span>
            <div>
              <strong>{{ orden ? `${orden.marca} ${orden.modelo}` : comprobante?.vehiculo ? `${comprobante.vehiculo.marca} ${comprobante.vehiculo.modelo}` : 'Vehículo' }}</strong>
              <small class="muted">{{ orden?.clienteNombre ?? comprobante?.receptor?.nombre }}</small>
            </div>
          </div>
          <template v-else>
            <div v-if="vehiculoSeleccionado" class="vehiculo-fijo">
              <span class="plate small-plate">{{ vehiculoSeleccionado.patente }}</span>
              <div>
                <strong>{{ vehiculoSeleccionado.marca }} {{ vehiculoSeleccionado.modelo }}</strong>
                <small class="muted">{{ vehiculoSeleccionado.clienteNombre }}</small>
              </div>
              <button type="button" class="button small outlined" @click="vehiculoSeleccionado = null">Cambiar</button>
            </div>
            <div v-else class="target-search-container">
              <div class="target-search-box">
                <Search :size="16" class="search-icon" />
                <input v-model="vehiculoSearch" type="text" autocomplete="off" placeholder="Buscá por patente, marca, modelo o cliente…" />
              </div>
              <p v-if="!vehiculoSearch.trim()" class="muted search-hint">Empezá a escribir para buscar vehículos.</p>
              <div v-else class="target-results-list">
                <div v-for="v in opciones" :key="v.id" class="target-result-item" @click="seleccionarVehiculo(v)">
                  <span class="plate small-plate">{{ v.patente }}</span>
                  <div>
                    <strong>{{ v.marca }} {{ v.modelo }}</strong>
                    <small class="muted">{{ v.clienteNombre }}</small>
                  </div>
                </div>
                <p v-if="buscando" class="muted">Buscando vehículos…</p>
                <p v-if="errorBusqueda" class="error-message" role="alert">{{ errorBusqueda }}</p>
                <p v-if="!buscando && !opciones.length && vehiculoSearch.trim() === debouncedSearch.trim()" class="muted">No se encontraron vehículos.</p>
              </div>
            </div>
          </template>
        </div>

        <div class="form-grid campos-principales">
          <label class="ancho">
            Concepto <span class="required-star">*</span>
            <input v-model="form.descripcion" required placeholder="Ej. Service de 50.000 km y cambio de aceite" />
          </label>
          <label>
            Mano de obra ($)
            <input v-model.number="form.manoObra" type="number" min="0" step="0.01" />
          </label>
          <label>
            Porcentaje de IVA (%)
            <input v-model.number="form.porcentajeIva" type="number" min="0" max="100" step="0.01" />
          </label>
        </div>

        <div class="field-block">
          <div class="renglones-header">
            <label class="block-label">Repuestos e insumos</label>
            <button type="button" class="button small outlined" @click="agregarItem"><Plus :size="14" />Agregar renglón</button>
          </div>
          <div v-for="(item, index) in form.items" :key="index" class="renglon">
            <input v-model="item.descripcion" type="text" placeholder="Descripción del repuesto o trabajo" aria-label="Descripción del renglón" />
            <input v-model.number="item.cantidad" type="number" min="0.01" step="0.01" aria-label="Cantidad" />
            <input v-model.number="item.precioUnitario" type="number" min="0" step="0.01" aria-label="Precio unitario" />
            <strong>{{ dinero(item.cantidad * item.precioUnitario) }}</strong>
            <button type="button" class="icon-button" aria-label="Quitar renglón" @click="quitarItem(index)"><Trash2 :size="15" /></button>
          </div>
          <p v-if="!form.items.length" class="muted">Sin renglones. Podés dejar sólo mano de obra.</p>
        </div>

        <div class="totales">
          <div><span>Repuestos</span><strong>{{ dinero(materiales) }}</strong></div>
          <div><span>Mano de obra</span><strong>{{ dinero(form.manoObra) }}</strong></div>
          <div><span>IVA {{ Number(form.porcentajeIva).toLocaleString('es-AR') }}%</span><strong>{{ dinero(montoIva) }}</strong></div>
          <div class="total"><span>Total</span><strong>{{ dinero(total) }}</strong></div>
        </div>

        <p v-if="formError" class="error-message" role="alert">{{ formError }}</p>
      </div>

      <footer class="dialog-footer modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary"><Check :size="16" />Guardar comprobante</button>
      </footer>
    </form>
  </CommonFormPage>
</template>

<style scoped>
.comprobante-manual-form { max-width: 820px; }
.form-fields { display: grid; gap: 16px; }
.integration-notice { background: transparent !important; border: 0 !important; margin: 0; padding: 0; font-size: 11px; }
.vehiculo-fijo { display: flex; align-items: center; gap: 10px; padding: 10px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); }
.vehiculo-fijo > div { display: grid; gap: 2px; }
.target-search-box { position: relative; }
.target-search-box input { width: 100%; height: 40px; padding: 0 10px 0 34px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); color: inherit; box-sizing: border-box; }
.target-search-box .search-icon { position: absolute; left: 10px; top: 12px; color: var(--muted); }
.search-hint { margin: 6px 0 0; font-size: 12px; }
.target-results-list { display: grid; gap: 4px; margin-top: 6px; max-height: 200px; overflow-y: auto; }
.target-result-item { display: flex; align-items: center; gap: 10px; padding: 8px; border: 1px solid var(--line); border-radius: 8px; cursor: pointer; }
.target-result-item:hover { border-color: var(--primary); }
.target-result-item > div { display: grid; gap: 2px; font-size: 12px; }
.campos-principales { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.campos-principales .ancho { grid-column: 1 / -1; }
.campos-principales label { display: grid; gap: 6px; font-size: 12px; font-weight: 700; }
.campos-principales input { height: 40px; padding: 0 10px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); color: inherit; }
.renglones-header { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.renglon { display: grid; grid-template-columns: minmax(0, 1fr) 80px 110px 100px 32px; gap: 8px; align-items: center; margin-bottom: 6px; }
.renglon input { height: 36px; padding: 0 8px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); color: inherit; min-width: 0; }
.renglon strong { text-align: right; font-size: 12px; white-space: nowrap; }
.totales { display: grid; gap: 6px; width: min(320px, 100%); margin-left: auto; padding-top: 10px; border-top: 1px solid var(--line); }
.totales > div { display: flex; justify-content: space-between; gap: 10px; font-size: 12px; }
.totales .total { font-size: 14px; }
@media (max-width: 620px) {
  .campos-principales { grid-template-columns: 1fr; }
  .renglon { grid-template-columns: minmax(0, 1fr) 70px 70px 32px; }
  .renglon strong { display: none; }
}
</style>
