<script setup lang="ts">
import { X, Check, Play, Pencil, Camera, Gauge, Calendar, Droplets, Filter, Receipt } from '@lucide/vue'
import { vehicleLabel } from '~/utils/vehicleDisplay'
import { authError } from '~/composables/useAuthApi'
import { cierreOrden, estadosOrden, filtrosOrden, fechaOrden, type OrdenApi, type DetalleOrdenApi, type FiltroOrden } from '~/utils/ordenApi'
import { prepareOrderPhoto } from '~/utils/orderPhoto'
import ResumenImportes from './ResumenImportes.vue'
import TablaRepuestos from './TablaRepuestos.vue'

const props = defineProps<{ id: number }>()
const emit = defineEmits<{ close: []; edit: [orden: OrdenApi]; updated: [] }>()
const { authorization, user } = useAuthApi()
const { statusClass } = useHelpers()
const detalle = ref<DetalleOrdenApi | null>(null)
const orden = computed(() => detalle.value?.orden)
const mecanico = computed(() => user.value?.rol === 'MECANICO')
const activa = computed(() => !!orden.value && !['FINALIZADO', 'CANCELADO'].includes(orden.value.estado))
const enProceso = computed(() => orden.value?.estado === 'EN_PROCESO')
const admiteFotos = computed(() => ['EN_ESPERA', 'EN_PROCESO'].includes(orden.value?.estado || ''))
const service = computed(() => !!orden.value?.tiposTrabajo.includes('SERVICE'))
const cargando = ref(false)
const guardando = ref(false)
const error = ref('')
const mensaje = ref('')
const revision = ref(0)
const finalizando = ref(false)
const cancelando = ref(false)
const motivo = ref('')
const trabajo = reactive({ diagnostico: '', kilometraje: null as number | null | '', observacionesMecanico: '', aceite: '', filtros: [] as FiltroOrden[] })
const sector = ref('Tablero / kilometraje')
const fotos = ref<Record<number, string>>({})
const errorFotos = ref('')
const revisionFotos = ref(0)
let vigente = true
onBeforeUnmount(() => { vigente = false })

function volverAlDetalle() { if (!guardando.value) finalizando.value = false }
function cerrar() { if (!guardando.value) emit('close') }
function recibir(result: DetalleOrdenApi) {
  detalle.value = result
}

watch([authorization, () => props.id, revision], async ([headers, id], _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  detalle.value = null
  error.value = ''
  cargando.value = !!headers
  if (!headers) return
  try {
    const result = await $fetch<DetalleOrdenApi>(`/api/ordenes-trabajo/${id}`, { headers, signal: controller.signal })
    if (controller.signal.aborted) return
    recibir(result)
    Object.assign(trabajo, { diagnostico: result.orden.diagnostico, kilometraje: result.orden.kilometraje, observacionesMecanico: result.orden.observacionesMecanico, aceite: result.orden.aceite, filtros: [...result.orden.filtros] })
  } catch (err) {
    if (!controller.signal.aborted) error.value = authError(err, 'No se pudo cargar la orden.')
  } finally { if (!controller.signal.aborted) cargando.value = false }
}, { immediate: true })

async function ejecutar(ruta: string, method: 'POST' | 'PUT' | 'PATCH' | 'DELETE', body?: object, ocupado = false) {
  const headers = authorization.value
  if (!headers || !detalle.value || (guardando.value && !ocupado)) return false
  guardando.value = true
  error.value = ''
  mensaje.value = ''
  try {
    const result = await $fetch<DetalleOrdenApi>(`/api/ordenes-trabajo/${props.id}${ruta}`, { method, headers, body })
    if (!vigente || authorization.value?.Authorization !== headers.Authorization) return false
    recibir(result)
    mensaje.value = 'Orden actualizada.'
    emit('updated')
    return true
  } catch (err) {
    if (vigente) error.value = authError(err, 'No se pudo guardar el cambio. Revisá la orden antes de reintentar.')
    return false
  } finally { guardando.value = false }
}

async function finalizar() {
  try {
    const body = cierreOrden(trabajo.kilometraje, trabajo.observacionesMecanico, trabajo.aceite, trabajo.filtros, service.value)
    if (await ejecutar('/finalizacion', 'POST', body)) finalizando.value = false
  } catch (err) { error.value = (err as Error).message }
}
async function cancelar() {
  if (!motivo.value.trim()) { error.value = 'Ingresá el motivo de baja.'; return }
  if (await ejecutar('/cancelacion', 'POST', { motivo: motivo.value.trim() })) cancelando.value = false
}

watch([authorization, () => detalle.value?.fotos.map(f => f.id).join(','), revisionFotos], async ([headers], _, onCleanup) => {
  const controller = new AbortController()
  const urls: Record<number, string> = {}
  onCleanup(() => { controller.abort(); Object.values(urls).forEach(url => URL.revokeObjectURL(url)) })
  fotos.value = {}
  errorFotos.value = ''
  if (!headers || !detalle.value) return
  const results = await Promise.allSettled(detalle.value.fotos.map(async f => {
    const blob = await $fetch<Blob>(`/api/ordenes-trabajo/${props.id}/fotos/${f.id}`, { headers, signal: controller.signal, responseType: 'blob' })
    if (!controller.signal.aborted) urls[f.id] = URL.createObjectURL(blob)
  }))
  if (controller.signal.aborted) return
  fotos.value = urls
  if (results.some(r => r.status === 'rejected')) errorFotos.value = 'No se pudieron descargar algunas fotos.'
})

async function cargarFoto(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || guardando.value || !admiteFotos.value) return
  const headers = authorization.value
  guardando.value = true
  error.value = ''
  try {
    const datos = await prepareOrderPhoto(file)
    if (!vigente || authorization.value !== headers) return
    await ejecutar('/fotos', 'POST', { sector: sector.value, datos }, true)
  } catch (err) { error.value = err instanceof Error ? err.message : 'No se pudo preparar la foto.' }
  finally { guardando.value = false }
}
</script>

<template>
  <CommonModalDialog v-if="!finalizando" class="dialog detail-modal" @close="cerrar">
    <div class="dialog-header"><div><span class="eyebrow">CONSULTA / ÓRDENES DE TRABAJO</span><h2>Orden de Trabajo #{{ id }}</h2></div><button class="icon-button" aria-label="Cerrar detalle" :disabled="guardando" @click="cerrar"><X :size="18" /></button></div>
    <p v-if="cargando" role="status">Cargando orden…</p>
    <p v-if="error" role="alert">{{ error }} <button v-if="!detalle" class="button small" @click="revision++">Reintentar</button></p>
    <p v-if="mensaje" role="status">{{ mensaje }}</p>
    <template v-if="detalle && orden">
      <div class="detail-intro"><div class="order-vehicle-summary"><div class="order-status"><span :class="['badge', statusClass(estadosOrden[orden.estado])]">{{ estadosOrden[orden.estado] }}</span></div><h2>{{ vehicleLabel(orden.marca, orden.modelo, orden.version) }}</h2><span class="plate">{{ orden.patente }}</span><p>{{ orden.clienteNombre }}</p></div><CommonBrandLogo :brand="orden.marca" class="order-vehicle-brand" /></div>
      <fieldset class="detail-body" :disabled="guardando">
        <p v-if="orden.estado === 'PENDIENTE_INGRESO'" class="muted">Orden emitida para un turno. El auto todavía no ingresó al taller.</p>
        <p v-if="orden.motivoCancelacion" class="muted">Motivo de baja: {{ orden.motivoCancelacion }}</p>
        <div class="info-grid-panel">
          <div class="info-item"><span class="info-label"><Gauge :size="12" /> Kilometraje</span><strong>{{ orden.kilometraje === null ? 'Sin registrar' : `${orden.kilometraje.toLocaleString('es-AR')} km` }}</strong></div>
          <div class="info-item"><span class="info-label"><Calendar :size="13" /> {{ orden.estado === 'PENDIENTE_INGRESO' ? 'Ingreso planificado' : 'Fecha de ingreso' }}</span><strong>{{ orden.estado === 'PENDIENTE_INGRESO' ? `${orden.fechaTurno || 'Sin fecha'} (${orden.horaInicioTurno?.slice(0, 5) || 'Sin hora'})` : fechaOrden(orden.ingresoFecha) }}</strong></div>
          <div class="info-item"><span class="info-label"><Calendar :size="13" /> Fecha y hora de egreso</span><strong>{{ orden.finFecha ? fechaOrden(orden.finFecha) : 'Pendiente de egreso' }}</strong></div>
          <div class="info-item"><span class="info-label">Mecánico asignado</span><strong>{{ orden.mecanicoNombre || 'Sin asignar' }}</strong></div>
        </div>
        <section class="detail-section">
          <h3>Trabajo / Servicio</h3><p class="service-highlight">{{ orden.descripcion }}</p>
          <div v-if="orden.aceite || orden.filtros.length" class="service-spec-panel">
            <div v-if="orden.aceite" class="spec-row"><span class="spec-label"><Droplets :size="13" /> Aceite utilizado</span><strong>{{ orden.aceite }}</strong></div>
            <div v-if="orden.filtros.length" class="spec-row"><span class="spec-label"><Filter :size="13" /> Filtros reemplazados</span><div class="filter-chips-list"><span v-for="f in orden.filtros" :key="f" class="filter-chip-item"><Check :size="12" /> {{ filtrosOrden[f] }}</span></div></div>
          </div>
        </section>
        <section v-if="orden.indicaciones || orden.observacionesMecanico" class="detail-section">
          <h3>Observaciones</h3>
          <div v-if="orden.indicaciones" class="note-box"><span class="note-label">Indicaciones del administrador</span><p>{{ orden.indicaciones }}</p></div>
          <div v-if="orden.observacionesMecanico" class="note-box"><span class="note-label">Observaciones del mecánico</span><p>{{ orden.observacionesMecanico }}</p></div>
        </section>
        <section v-if="(enProceso && mecanico) || orden.diagnostico" class="detail-section">
          <h3>Diagnóstico y observaciones</h3>
          <form v-if="enProceso && mecanico" class="form-fields" @submit.prevent="ejecutar('/trabajo', 'PUT', { diagnostico: trabajo.diagnostico, kilometraje: trabajo.kilometraje === '' ? null : trabajo.kilometraje, observacionesMecanico: trabajo.observacionesMecanico })">
            <label>Diagnóstico<textarea v-model="trabajo.diagnostico" maxlength="4000" rows="3" /></label>
            <label>Kilometraje<input v-model.number="trabajo.kilometraje" type="number" min="0" max="9999999" step="1" /></label>
            <label>Observaciones del mecánico<textarea v-model="trabajo.observacionesMecanico" maxlength="4000" rows="3" /></label>
            <button class="button">Guardar trabajo</button>
          </form>
          <template v-else><p>{{ orden.diagnostico || 'Sin diagnóstico registrado.' }}</p><p>{{ orden.observacionesMecanico }}</p><p>Kilometraje: {{ orden.kilometraje === null ? 'Sin registrar' : orden.kilometraje.toLocaleString('es-AR') }}</p></template>
        </section>
        <section class="detail-section">
          <h3>Repuestos e insumos imputados</h3>
          <TablaRepuestos :items="detalle.items" :show-prices="!mecanico" />
        </section>
        <section v-if="!mecanico" class="detail-section"><ResumenImportes :repuestos="detalle.totalRepuestos" :mano-obra="orden.manoObra" :subtotal="detalle.subtotal" :porcentaje-iva="orden.porcentajeIva" :iva="detalle.montoIva" :total="detalle.total" /></section>
        <section class="detail-section">
          <div class="section-heading"><h3>Peritaje fotográfico (cargado por mecánico)</h3><span>{{ detalle.fotos.length }} fotos</span></div>
          <p v-if="!detalle.fotos.length" class="muted">No se adjuntaron fotografías.</p>
          <p v-if="errorFotos" role="alert">{{ errorFotos }} <button class="button small" @click="revisionFotos++">Reintentar fotos</button></p>
          <div class="order-photos"><figure v-for="f in detalle.fotos" :key="f.id"><a v-if="fotos[f.id]" :href="fotos[f.id]" target="_blank" rel="noopener"><img :src="fotos[f.id]" :alt="f.sector" /></a><span v-else>Cargando imagen…</span><figcaption>{{ f.sector }} · {{ fechaOrden(f.fecha) }}</figcaption><button v-if="admiteFotos" class="button small" @click="ejecutar(`/fotos/${f.id}`, 'DELETE')">Quitar foto</button></figure></div>
          <template v-if="admiteFotos && mecanico"><label>Sector de la foto<input v-model="sector" maxlength="80" /></label><label class="photo-upload"><Camera :size="17" /> Adjuntar foto<input type="file" accept="image/*" :disabled="detalle.fotos.length >= 8 || !sector.trim()" @change="cargarFoto" /></label><p class="muted">Hasta 8 fotos. Se preparan en JPEG antes de subirlas.</p></template>
        </section>
        <section v-if="orden.estado === 'FINALIZADO' && !mecanico" class="detail-section billing-status-card">
          <div class="billing-card-header"><div class="billing-title-row"><div class="card-icon-tag"><Receipt :size="16" /><span>FACTURACIÓN INTERNA</span></div></div></div>
          <div class="billing-card-body"><p class="muted">Consultá los comprobantes y cobros internos en Facturación.</p><NuxtLink to="/facturacion" class="button small primary" @click="cerrar"><Receipt :size="14" /> Ir a facturación</NuxtLink></div>
        </section>
        <form v-if="cancelando && activa && !mecanico" class="form-fields detail-section" @submit.prevent="cancelar"><h3>Dar de baja la orden</h3><label>Motivo de baja<textarea v-model="motivo" required maxlength="500" rows="3" /></label><p class="muted">Los materiales consumidos se conservan. Devolvé los recuperados antes de cancelar.</p><button class="button primary">Confirmar baja</button><button type="button" class="button" @click="cancelando = false">Volver</button></form>
      </fieldset>
      <footer class="modal-footer">
        <button v-if="activa && !mecanico && !cancelando" class="button" :disabled="guardando" @click="cancelando = true; finalizando = false">Dar de baja</button>

        <button v-if="activa" class="button" :disabled="guardando" @click="emit('edit', orden)"><Pencil :size="14" /> Editar</button>
        <button v-if="orden.estado === 'PENDIENTE_INGRESO' && !mecanico" class="button primary" :disabled="guardando" @click="ejecutar('/ingreso', 'POST')">Registrar ingreso</button>
        <button v-if="orden.estado === 'EN_ESPERA'" class="button primary" :disabled="guardando || !orden.mecanicoId" @click="ejecutar('/inicio', 'POST')"><Play :size="16" /> Iniciar</button>
        <button v-if="enProceso && !finalizando" class="button primary" :disabled="guardando" @click="finalizando = true; cancelando = false"><Check :size="16" /> Finalizar orden</button>
      </footer>
    </template>
  </CommonModalDialog>
  <CommonModalDialog v-if="finalizando && enProceso && orden" class="dialog completion-dialog" aria-labelledby="completion-title" @close="volverAlDetalle()">
    <div class="dialog-header">
      <div><h2 id="completion-title">Finalizar orden #{{ id }}</h2><p class="muted">Registrá los datos del trabajo antes de cerrarlo.</p></div>
      <button class="icon-button" aria-label="Cerrar" @click="volverAlDetalle()"><X :size="18" /></button>
    </div>
    <form @submit.prevent="finalizar">
      <fieldset class="form-fields completion-fields" :disabled="guardando">
        <label>Kilometraje del vehículo<input v-model.number="trabajo.kilometraje" type="number" inputmode="numeric" min="0" step="1" required autofocus /></label>
        <template v-if="service">
          <label>Aceite utilizado<input v-model="trabajo.aceite" placeholder="Ej. Castrol Edge 5W-30" maxlength="120" required /></label>
          <fieldset><legend>Filtros reemplazados</legend><div class="completion-filters"><label v-for="(label, key) in filtrosOrden" :key="key"><input v-model="trabajo.filtros" type="checkbox" :value="key" />{{ label }}</label></div></fieldset>

        </template>
        <label>Observaciones del mecánico<textarea v-model="trabajo.observacionesMecanico" rows="3" maxlength="4000" placeholder="Observaciones del trabajo realizado" /></label>
        <p v-if="error" class="error-message" role="alert">{{ error }}</p>
      </fieldset>
      <footer class="modal-footer"><button type="button" class="button" :disabled="guardando" @click="volverAlDetalle">Cancelar</button><button type="submit" class="button primary" :disabled="guardando"><Check :size="16" /> Confirmar finalización</button></footer>
    </form>
  </CommonModalDialog>
</template>

<style scoped>
fieldset.detail-body { border: 0; margin: 0; padding: 25px 28px; min-width: 0; }
@media (max-width: 480px) { fieldset.detail-body { padding: 18px 16px; } }

.order-photos { display: flex; flex-wrap: wrap; gap: 12px; }
.order-photos figure { margin: 0; width: 180px; }
.order-photos img { width: 100%; height: 130px; object-fit: cover; border-radius: 8px; }
.photo-upload { margin-top: 12px; }
dd, td, p { overflow-wrap: anywhere; }
td small { display: block; }
.order-vehicle-summary { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.detail-intro > .order-vehicle-brand { flex: 0 0 80px; width: 80px; height: 72px; }
@media (max-width: 480px) {
  .detail-intro > .order-vehicle-brand { flex-basis: 56px; width: 56px; height: 48px; }
}
.info-grid-panel {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 16px;
}

:global(html.dark .info-grid-panel) {
  background: #1c1c1e;
  border-color: rgba(255, 255, 255, 0.08);
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.info-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: #64748b;
  font-weight: 600;
}

:global(html.dark .info-label) {
  color: #8e8e93;
}

.info-item strong {
  font-size: 13px;
  color: #1e293b;
}

:global(html.dark .info-item strong) {
  color: #f5f5f7;
}

.detail-section {
  margin-top: 18px;
  border-top: 1px solid #f1f5f9;
  padding-top: 14px;
}

:global(html.dark .detail-section) {
  border-top-color: rgba(255, 255, 255, 0.08);
}

.detail-section h3 {
  font-size: 14px;
  margin-bottom: 8px;
  color: #0f172a;
}

:global(html.dark .detail-section h3) {
  color: #f5f5f7;
}

.service-highlight {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
  background: var(--surface);
  border: 1px solid #000;
  padding: 10px 12px;
  border-radius: 8px;
}

:global(html.dark .service-highlight) {
  background: #1c1c1e;
  border-color: #000;
  color: #f5f5f7;
}

.note-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
}

:global(html.dark .note-box) {
  background: #252528;
  border-color: rgba(255, 255, 255, 0.08);
}

.note-label {
  font-size: 11px;
  text-transform: uppercase;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: #64748b;
  display: block;
  margin-bottom: 4px;
}

:global(html.dark .note-label) {
  color: #8e8e93;
}

.note-box p {
  font-size: 13px;
  color: #334155;
  margin: 0;
  white-space: pre-line;
}

:global(html.dark .note-box p) {
  color: #d1d1d6;
}

/* Service Specs Panel */
.service-spec-panel {
  margin-top: 10px;
  background: var(--surface);
  border: 1px solid #000;
  border-radius: 8px;
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

:global(html.dark .service-spec-panel) {
  background: #1c1c1e;
  border-color: #000;
}

.spec-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.spec-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--ink);
}

:global(html.dark .spec-label) {
  color: #f5f5f7;
}

.filter-chips-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 2px;
}

.filter-chip-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: #ffffff;
  border: 1px solid #000;
  color: var(--ink);
  font-size: 12px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 6px;
}

:global(html.dark .filter-chip-item) {
  background: #1c1c1e;
  border-color: #000;
  color: #f5f5f7;
}

/* Billing Status Card */
.billing-status-card {
  background: #ffffff;
  border: 1.5px solid #cbd5e1;
  border-radius: 12px;
  padding: 14px 16px;
  margin-top: 14px;
}

:global(html.dark .billing-status-card) {
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(255, 255, 255, 0.15);
}

.billing-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  flex-wrap: wrap;
  gap: 10px;
}

.billing-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.card-icon-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.6px;
  text-transform: uppercase;
  padding: 3px 8px;
  border-radius: 6px;
  background: #ffffff;
  color: #0f172a;
}

:global(html.dark .card-icon-tag) {
  background: transparent;
  color: #f5f5f7;
}

.billing-card-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.completion-dialog { width: min(480px, calc(100vw - 24px)); }
.completion-fields { padding: 18px 24px; gap: 16px; }
fieldset { border: 0; padding: 0; margin: 0; min-width: 0; }
legend { font-size: 13px; font-weight: 600; margin-bottom: 10px; }
.completion-filters { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.completion-filters label { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.completion-filters input { width: auto; min-height: 0; }
@media (max-width: 380px) { .completion-filters { grid-template-columns: 1fr; } }

.completion-fields { border: 0; margin: 0; }
.section-heading { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.note-box + .note-box { margin-top: 10px; }
.order-status { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
</style>
