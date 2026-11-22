<script setup lang="ts">
import { X, Search, Check } from '@lucide/vue'
import { authError } from '~/composables/useAuthApi'
import type { VehiculoApi } from '~/utils/vehiculoApi'
import { tiposTrabajoOrden, type OrdenApi, type DetalleOrdenApi, type TipoTrabajoOrden, type PaginaOrdenes } from '~/utils/ordenApi'

const props = defineProps<{ orden: OrdenApi | null }>()
const emit = defineEmits<{ close: []; saved: [orden: OrdenApi] }>()
const { authorization } = useAuthApi()
const { money } = useHelpers()
const error = ref('')
const guardando = ref(false)
const cargando = ref(false)
const mecanicos = ref<{ id: number; nombre: string; apellido: string }[]>([])
const vehiculos = ref<VehiculoApi[]>([])
const seleccionado = ref<VehiculoApi | null>(null)
const busqueda = ref('')
const consulta = useDebouncedValue(busqueda)
const buscando = ref(false)
const errorBusqueda = ref('')
const paginaVehiculos = ref(0)
const paginasVehiculos = ref(0)
interface Presupuesto { id: number; descripcion: string; estado: string; ordenId: number | null }
interface DetallePresupuesto extends Presupuesto { turnoId: number | null; manoObra: number; porcentajeIva: number; tiposTrabajo: TipoTrabajoOrden[] }
const presupuestos = ref<Presupuesto[]>([])
const errorPresupuesto = ref('')
const cargandoPresupuesto = ref(false)
const form = reactive({
  vehiculoId: props.orden?.vehiculoId ?? 0,
  presupuestoId: props.orden?.presupuestoId ?? '',
  mecanicoId: props.orden?.mecanicoId ?? '',
  descripcion: props.orden?.descripcion ?? '',
  tiposTrabajo: [...(props.orden?.tiposTrabajo ?? [])] as TipoTrabajoOrden[],
  indicaciones: props.orden?.indicaciones ?? '',
  manoObra: props.orden?.manoObra ?? 0,
  porcentajeIva: props.orden?.porcentajeIva ?? 21,
})
const vinculado = computed(() => Boolean(form.presupuestoId))
function cerrar() { if (!guardando.value) emit('close') }
useModalEscape(() => true, cerrar)

watch(authorization, async (headers, _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  mecanicos.value = []
  cargando.value = !!headers
  if (!headers) return
  try {
    const result = await $fetch<typeof mecanicos.value>('/api/mecanicos', { headers, signal: controller.signal, query: { activo: true } })
    if (!controller.signal.aborted) mecanicos.value = result
  } catch (err) {
    if (!controller.signal.aborted) error.value = authError(err, 'No se pudieron cargar los mecánicos. Cerrá y volvé a abrir el formulario.')
  } finally { if (!controller.signal.aborted) cargando.value = false }
}, { immediate: true })

watch(consulta, () => { paginaVehiculos.value = 0 })
watch([authorization, consulta, paginaVehiculos], async ([headers, q], _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  vehiculos.value = []
  errorBusqueda.value = ''
  buscando.value = !!(headers && q.trim() && !props.orden)
  if (!buscando.value) return
  try {
    const result = await $fetch<PaginaOrdenes<VehiculoApi>>('/api/vehiculos', {
      headers, signal: controller.signal, query: { q: q.trim(), activo: true, pagina: paginaVehiculos.value, tamano: 20 },
    })
    if (!controller.signal.aborted) { vehiculos.value = result.contenido; paginasVehiculos.value = result.totalPaginas }
  } catch (err) {
    if (!controller.signal.aborted) errorBusqueda.value = authError(err, 'No se pudieron buscar vehículos.')
  } finally { if (!controller.signal.aborted) buscando.value = false }
})

watch([authorization, () => form.vehiculoId], async ([headers, vehiculoId], _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  presupuestos.value = []
  errorPresupuesto.value = ''
  if (!headers || !vehiculoId || props.orden) return
  try {
    let pagina = 0
    let totalPaginas: number
    const items: Presupuesto[] = []
    do {
      const result = await $fetch<PaginaOrdenes<Presupuesto>>('/api/presupuestos', {
        headers, signal: controller.signal, query: { vehiculoId, pagina, tamano: 100 },
      })
      items.push(...result.contenido)
      totalPaginas = result.totalPaginas
      pagina++
    } while (pagina < totalPaginas && !controller.signal.aborted)
    if (!controller.signal.aborted) presupuestos.value = items.filter(p => !p.ordenId && ['PENDIENTE', 'ACEPTADO'].includes(p.estado))
  } catch (err) {
    if (!controller.signal.aborted) errorPresupuesto.value = authError(err, 'No se pudieron cargar los presupuestos.')
  }
}, { immediate: true })

watch([authorization, () => form.presupuestoId], async ([headers, id], _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  cargandoPresupuesto.value = !!(headers && id && !props.orden)
  if (!cargandoPresupuesto.value) return
  error.value = ''
  try {
    const p = await $fetch<DetallePresupuesto>(`/api/presupuestos/${id}`, { headers, signal: controller.signal })
    if (controller.signal.aborted) return
    if (p.turnoId) {
      form.presupuestoId = ''
      error.value = 'Este presupuesto tiene un turno. Utilizá la orden generada por esa reserva.'
      return
    }
    Object.assign(form, { descripcion: p.descripcion, manoObra: p.manoObra, porcentajeIva: p.porcentajeIva, tiposTrabajo: [...p.tiposTrabajo] })
  } catch (err) {
    if (!controller.signal.aborted) {
      form.presupuestoId = ''
      error.value = authError(err, 'No se pudo cargar el presupuesto. Seleccionalo nuevamente.')
    }
  } finally { if (!controller.signal.aborted) cargandoPresupuesto.value = false }
})

function seleccionar(v: VehiculoApi) {
  seleccionado.value = v
  form.vehiculoId = v.id
  form.presupuestoId = ''
  busqueda.value = ''
}

async function guardar() {
  const headers = authorization.value
  if (!headers || guardando.value || cargando.value || cargandoPresupuesto.value) return
  error.value = ''
  if (!form.vehiculoId || !mecanicos.value.some(m => m.id === Number(form.mecanicoId)) || !form.descripcion.trim() || !form.tiposTrabajo.length) {
    error.value = 'Seleccioná vehículo, mecánico activo, tipo de trabajo y descripción.'
    return
  }
  if (!Number.isFinite(form.manoObra) || form.manoObra < 0 || !Number.isFinite(form.porcentajeIva) || form.porcentajeIva < 0 || form.porcentajeIva > 100) {
    error.value = 'Revisá la mano de obra y el porcentaje de IVA.'
    return
  }
  guardando.value = true
  try {
    const result = await $fetch<DetalleOrdenApi>(`/api/ordenes-trabajo${props.orden ? `/${props.orden.id}` : ''}`, {
      method: props.orden ? 'PUT' : 'POST', headers,
      body: { ...form, presupuestoId: form.presupuestoId ? Number(form.presupuestoId) : null, mecanicoId: Number(form.mecanicoId), descripcion: form.descripcion.trim(), indicaciones: form.indicaciones.trim() },
    })
    if (authorization.value?.Authorization === headers.Authorization) emit('saved', result.orden)
  } catch (err) { error.value = authError(err, 'No se pudo guardar la orden. Tus datos siguen en el formulario.') }
  finally { guardando.value = false }
}
</script>

<template>
  <CommonFormPage>
    <div class="dialog-header"><div><h2>{{ orden ? `Editar orden #${orden.id}` : 'Nueva orden de trabajo' }}</h2><p class="muted">{{ orden ? 'La planificación se puede modificar antes de iniciar.' : 'El presupuesto es opcional. Los materiales se agregan después de crear la orden.' }}</p></div><button class="icon-button" aria-label="Cerrar formulario" :disabled="guardando" @click="cerrar"><X :size="18" /></button></div>
    <form class="entry-form" @submit.prevent="guardar">
      <fieldset class="form-fields" :disabled="guardando">
        <div v-if="orden || seleccionado" class="selected-target-card"><span class="plate">{{ orden?.patente || seleccionado?.patente }}</span><div><strong>{{ orden?.marca || seleccionado?.marca }} {{ orden?.modelo || seleccionado?.modelo }}</strong><p>{{ orden?.clienteNombre || seleccionado?.clienteNombre }}</p></div><button v-if="!orden" type="button" class="icon-button clear-vehicle" aria-label="Quitar vehículo seleccionado" title="Quitar vehículo" @click="seleccionado = null; form.vehiculoId = 0; form.presupuestoId = ''"><X :size="16" /></button></div>
        <template v-else>
          <label class="search-box"><Search :size="16" /><input v-model="busqueda" maxlength="250" aria-label="Buscar vehículo para la orden" placeholder="Buscá por patente, marca o modelo…" /></label>
          <p v-if="buscando" role="status">Buscando vehículos…</p><p v-if="errorBusqueda" role="alert">{{ errorBusqueda }}</p>
          <div v-if="consulta.trim() && consulta === busqueda" class="vehicle-results">
            <button v-for="v in vehiculos" :key="v.id" class="vehicle-result" type="button" @click="seleccionar(v)">
              <span class="plate">{{ v.patente }}</span>
              <span class="vehicle-result-info"><strong>{{ v.marca }} {{ v.modelo }}</strong><small>{{ v.clienteNombre }}</small></span>
            </button>
            <p v-if="!buscando && !vehiculos.length && !errorBusqueda" class="muted">No se encontraron vehículos activos.</p>
            <div v-if="paginasVehiculos > 1"><button type="button" class="button small" :disabled="paginaVehiculos === 0 || buscando" @click="paginaVehiculos--">Anterior</button><button type="button" class="button small" :disabled="paginaVehiculos + 1 >= paginasVehiculos || buscando" @click="paginaVehiculos++">Siguiente</button></div>
          </div>
        </template>
        <CommonDependentFields :ready="!!form.vehiculoId" hint="Primero seleccioná un vehículo para completar los demás datos.">
        <p v-if="orden?.turnoId" class="muted">Turno #{{ orden.turnoId }}. El vehículo y presupuesto se corrigen desde la reserva antes del ingreso.</p>
        <p v-if="errorPresupuesto" role="alert">{{ errorPresupuesto }}</p>
        <label>Presupuesto (opcional)<select v-model="form.presupuestoId" :disabled="!!orden || !form.vehiculoId || cargandoPresupuesto"><option value="">Sin presupuesto</option><option v-if="orden?.presupuestoId" :value="orden.presupuestoId">#{{ orden.presupuestoId }} · Presupuesto vinculado</option><option v-for="p in presupuestos" :key="p.id" :value="p.id">#{{ p.id }} · {{ p.descripcion }}</option></select></label>
        <p v-if="cargandoPresupuesto" role="status">Cargando presupuesto…</p>
        <fieldset class="work-type-field">
          <legend>Tipo de trabajo</legend>
          <div class="work-type-options">
            <button v-for="(label, key) in tiposTrabajoOrden" :key="key" type="button" class="button"
              :class="{ primary: form.tiposTrabajo.includes(key) }"
              :aria-pressed="form.tiposTrabajo.includes(key)"
              @click="form.tiposTrabajo = form.tiposTrabajo.includes(key) ? form.tiposTrabajo.filter(t => t !== key) : [...form.tiposTrabajo, key]">
              {{ label }}
            </button>
          </div>
        </fieldset>
        <label>Detalle del trabajo<textarea v-model="form.descripcion" required maxlength="2000" rows="3" /></label>
        <label>Mecánico asignado<select v-model="form.mecanicoId" required :disabled="cargando"><option value="" disabled>Seleccionar mecánico</option><option v-for="m in mecanicos" :key="m.id" :value="m.id">{{ m.nombre }} {{ m.apellido }}</option></select></label>
        <p v-if="!cargando && !mecanicos.length" class="muted">Necesitás un mecánico activo. Podés darlo de alta desde Personal.</p>
        <div class="form-grid"><label>Mano de obra<input v-model.number="form.manoObra" type="number" min="0" max="999999999999.99" step="0.01" required :readonly="vinculado" /></label><label>IVA (%)<input v-model.number="form.porcentajeIva" type="number" min="0" max="100" step="0.01" required :readonly="vinculado" /></label></div>
        <p v-if="vinculado" class="muted">Los materiales y precios se conservan desde el presupuesto. Mano de obra: {{ money(form.manoObra) }}.</p>
        <label>Indicaciones para el mecánico<textarea v-model="form.indicaciones" maxlength="4000" rows="3" /></label>
        </CommonDependentFields>
      </fieldset>
      <p v-if="error" role="alert">{{ error }}</p>
      <footer class="modal-footer"><button type="button" class="button" :disabled="guardando" @click="cerrar">Cancelar</button><button type="submit" class="button primary" :disabled="!form.vehiculoId || guardando || cargando || cargandoPresupuesto"><Check :size="16" /> {{ guardando ? 'Guardando…' : orden ? 'Guardar cambios' : 'Crear orden' }}</button></footer>
    </form>
  </CommonFormPage>
</template>

<style scoped>
fieldset.form-fields { border: 0; min-width: 0; }
.form-fields input, .form-fields select, .form-fields textarea { font-size: 12px; line-height: 1.4; padding: 8px 10px; }
.form-fields input, .form-fields select { min-height: 34px; }
.form-fields .search-box { min-width: 0; padding-inline: 10px; }
.form-fields .search-box input { min-height: 34px; padding: 7px 8px; }
.vehicle-results { max-height: 300px; overflow-y: auto; border: 1px solid var(--line); border-radius: 10px; }
.selected-target-card { display: flex; align-items: center; gap: 12px; padding: 12px; }
.clear-vehicle { margin-left: auto; flex-shrink: 0; }
.vehicle-result { display: grid; grid-template-columns: 90px minmax(0, 1fr); align-items: center; gap: 10px; width: 100%; padding: 9px 12px; border: 0; border-bottom: 1px solid var(--line); background: transparent; color: inherit; text-align: left; cursor: pointer; }
.vehicle-result:last-child { border-bottom: 0; }
.vehicle-result:hover { background: color-mix(in srgb, var(--ink) 5%, transparent); }
.vehicle-result:focus-visible { outline: 2px solid #3b82f6; outline-offset: -3px; }
.vehicle-result .plate { justify-self: start; white-space: nowrap; font-size: 10px; padding: 4px 6px; }
.vehicle-result-info { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.vehicle-result-info strong { font-size: 12px; font-weight: 600; line-height: 1.4; overflow-wrap: anywhere; }
.vehicle-result-info small { font-size: 11px; font-weight: 400; line-height: 1.4; color: var(--muted); overflow-wrap: anywhere; }
.work-type-field { border: 0; padding: 0; margin: 0; min-width: 0; }
.work-type-field legend { padding: 0; margin-bottom: 8px; font-size: 13px; font-weight: 600; }
.work-type-options { display: flex; flex-wrap: wrap; gap: 8px; }
</style>
