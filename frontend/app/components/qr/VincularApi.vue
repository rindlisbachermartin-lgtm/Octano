<script setup lang="ts">
import { avanzarConEnter } from '~/utils/formKeyboard'
import { X, Search } from '@lucide/vue'
import { authError } from '~/composables/useAuthApi'
import { normalizarCodigoQr, type CodigoQrApi, type PaginaQr } from '~/utils/qrApi'
import type { VehiculoApi } from '~/utils/vehiculoApi'

const props = defineProps<{ codigo?: string; vehiculoId?: number | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const { authorization, puedeAdministrar } = useAuthApi()
const codigo = ref(props.codigo || '')
const disponibles = ref<CodigoQrApi[]>([])
const vehiculo = ref<VehiculoApi | null>(null)
const busqueda = ref('')
const consulta = useDebouncedValue(busqueda)
const vehiculos = ref<VehiculoApi[]>([])
const paginaVehiculos = ref(0)
const paginasVehiculos = ref(0)
const cargando = ref(false)
const guardando = ref(false)
const error = ref('')
const errorBusqueda = ref('')
let vigente = true
onBeforeUnmount(() => { vigente = false })
function cerrar() { if (!guardando.value) emit('close') }
useModalEscape(() => true, cerrar)

watch(authorization, async (headers, _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  disponibles.value = []
  if (!headers || props.codigo) return
  try {
    const etiquetas: CodigoQrApi[] = []
    let pagina = 0
    let totalPaginas: number
    do {
      const result = await $fetch<PaginaQr<CodigoQrApi>>('/api/codigos-qr', { headers, signal: controller.signal, query: { estado: 'DISPONIBLE', pagina, tamano: 100 } })
      etiquetas.push(...result.contenido)
      totalPaginas = result.totalPaginas
      pagina++
    } while (pagina < totalPaginas && !controller.signal.aborted)
    if (!controller.signal.aborted) disponibles.value = etiquetas
  } catch (err) { if (!controller.signal.aborted) error.value = authError(err, 'No se pudieron cargar las etiquetas disponibles.') }
}, { immediate: true })

watch(authorization, async (headers, _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  vehiculo.value = null
  if (!headers || !props.vehiculoId) return
  cargando.value = true
  try {
    const result = await $fetch<VehiculoApi>(`/api/vehiculos/${props.vehiculoId}`, { headers, signal: controller.signal })
    if (!controller.signal.aborted) vehiculo.value = result
  } catch (err) { if (!controller.signal.aborted) error.value = authError(err, 'No se pudo cargar el vehículo.') }
  finally { if (!controller.signal.aborted) cargando.value = false }
}, { immediate: true })

watch(consulta, () => { paginaVehiculos.value = 0 })
watch([authorization, consulta, paginaVehiculos], async ([headers, q], _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  vehiculos.value = []
  errorBusqueda.value = ''
  cargando.value = false
  if (!headers || !q.trim() || props.vehiculoId) return
  cargando.value = true
  try {
    const result = await $fetch<PaginaQr<VehiculoApi>>('/api/vehiculos', { headers, signal: controller.signal, query: { activo: true, q: q.trim(), pagina: paginaVehiculos.value, tamano: 20 } })
    if (!controller.signal.aborted) { vehiculos.value = result.contenido; paginasVehiculos.value = result.totalPaginas }
  } catch (err) { if (!controller.signal.aborted) errorBusqueda.value = authError(err, 'No se pudieron buscar vehículos.') }
  finally { if (!controller.signal.aborted) cargando.value = false }
})

async function guardar() {
  const headers = authorization.value
  if (!headers || !puedeAdministrar.value || !vehiculo.value || guardando.value) return
  error.value = ''
  let valor: string
  try { valor = normalizarCodigoQr(codigo.value) } catch (err) { error.value = (err as Error).message; return }
  guardando.value = true
  try {
    await $fetch(`/api/codigos-qr/${valor}/vehiculo`, { method: 'PUT', headers, body: { vehiculoId: vehiculo.value.id } })
    if (vigente && authorization.value === headers) emit('saved')
  } catch (err) { error.value = authError(err, 'No se pudo vincular la etiqueta. La selección se conserva.') }
  finally { guardando.value = false }
}
</script>

<template>
  <CommonFormPage>
    <div class="dialog-header"><h2>{{ vehiculo?.codigoQr ? 'Reemplazar código QR' : 'Vincular código QR' }}</h2><button class="icon-button" aria-label="Cerrar vinculación" :disabled="guardando" @click="cerrar"><X :size="18" /></button></div>
    <form @keydown.enter="avanzarConEnter" @submit.prevent="guardar">
      <fieldset class="form-fields" :disabled="guardando">
        <div class="sticker-code"><span>Código del sticker</span><span class="sticker-value">{{ vehiculo?.codigoQr || props.codigo || 'Sin QR asignado' }}</span></div>
        <div v-if="props.codigo && vehiculo?.codigoQr" class="sticker-code"><span>Nuevo código a vincular</span><span class="sticker-value">{{ codigo }}</span></div>
        <template v-if="!props.codigo">
          <label>Etiquetas disponibles
            <span class="label-picker">
              <span v-if="codigo" class="selected-label">{{ codigo }}</span>
              <select v-else :value="codigo" aria-label="Elegir etiqueta disponible" @change="codigo = ($event.target as HTMLSelectElement).value"><option value="" disabled hidden>Seleccionar etiqueta</option><option v-for="c in disponibles" :key="c.codigo" :value="c.codigo">{{ c.codigo }}</option></select>
              <button v-if="codigo" type="button" class="icon-button" aria-label="Quitar etiqueta seleccionada" title="Quitar etiqueta" @click="codigo = ''"><X :size="16" /></button>
            </span>
          </label>
          <p v-if="!disponibles.length" class="muted">Primero generá etiquetas desde Códigos QR para poder vincular una.</p>
        </template>
        <div v-if="vehiculo" class="selected-vehicle"><span class="plate">{{ vehiculo.patente }}</span><div><strong>{{ vehicleLabel(vehiculo.marca, vehiculo.modelo, vehiculo.version) }}</strong><small>{{ vehiculo.clienteNombre }}</small></div><button v-if="!props.vehiculoId" type="button" class="icon-button" aria-label="Quitar vehículo" @click="vehiculo = null"><X :size="16" /></button></div>
        <template v-else-if="!props.vehiculoId"><label class="search-box"><Search :size="16" /><input v-model="busqueda" maxlength="250" aria-label="Buscar vehículo para vincular" placeholder="Patente, marca o modelo…" /></label><div v-if="consulta === busqueda" class="vehicle-results"><button v-for="v in vehiculos" :key="v.id" type="button" class="vehicle-result" @click="vehiculo = v; busqueda = ''"><span class="plate">{{ v.patente }}</span><span><strong>{{ vehicleLabel(v.marca, v.modelo, v.version) }}</strong><small>{{ v.clienteNombre }}</small></span></button><p v-if="consulta && !cargando && !vehiculos.length" class="muted">No se encontraron vehículos activos.</p></div><div v-if="paginasVehiculos > 1" class="actions"><button type="button" class="button small" :disabled="paginaVehiculos === 0 || cargando" @click="paginaVehiculos--">Anterior</button><button type="button" class="button small" :disabled="paginaVehiculos + 1 >= paginasVehiculos || cargando" @click="paginaVehiculos++">Siguiente</button></div></template>
        <p v-if="cargando" role="status">Cargando vehículos…</p><p v-if="errorBusqueda" role="alert">{{ errorBusqueda }}</p>
        <p v-if="vehiculo?.codigoQr" class="replacement-warning">El QR actual quedará inutilizado definitivamente al confirmar el reemplazo.</p>
        <p v-if="error" role="alert">{{ error }}</p>
      </fieldset>
      <footer class="modal-footer"><button type="button" class="button" :disabled="guardando" @click="cerrar">Cancelar</button><button class="button primary" :disabled="guardando || !codigo.trim() || !vehiculo || !puedeAdministrar">{{ guardando ? 'Guardando…' : 'Confirmar vinculación' }}</button></footer>
    </form>
  </CommonFormPage>
</template>

<style scoped>
.form-fields { border: 0; }
.sticker-code { display: flex; flex-direction: column; gap: 8px; padding: 0; border: 0; background: none; }
.sticker-code > span { font-size: 12px; font-weight: 600; }
.sticker-code .sticker-value { padding: 0; border: 0; background: none; box-shadow: none; font-size: 12px; font-weight: 400; line-height: 1.5; overflow-wrap: anywhere; }
input, select { font-size: 12px; padding: 8px 10px; }
.label-picker { display: flex; align-items: center; gap: 8px; }
.label-picker select { flex: 1; min-width: 0; }
.selected-label { flex: 1; min-width: 0; font-size: 12px; overflow-wrap: anywhere; }
.label-picker .icon-button { flex-shrink: 0; }
.vehicle-results { max-height: 240px; overflow: auto; border-radius: 8px; }
.vehicle-result, .selected-vehicle { display: flex; align-items: center; gap: 12px; padding: 10px 12px; }
.vehicle-result { width: 100%; text-align: left; border-bottom: 1px solid var(--line); color: inherit; background: transparent; }
.vehicle-result strong, .selected-vehicle strong { font-size: 12px; }
small { display: block; color: var(--muted); margin-top: 3px; }
.selected-vehicle .icon-button { margin-left: auto; }
.actions { display: flex; align-items: center; gap: 10px; }
.replacement-warning { padding: 0; border: 0; background: transparent; color: var(--muted); font-size: 12px; line-height: 1.5; overflow-wrap: anywhere; }
</style>
