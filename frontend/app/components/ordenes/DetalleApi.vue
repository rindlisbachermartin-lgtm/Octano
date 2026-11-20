<script setup lang="ts">
import { X, Check, Play, Pencil, Plus, Trash2, Camera } from '@lucide/vue'
import { authError } from '~/composables/useAuthApi'
import { cierreOrden, estadosOrden, tiposTrabajoOrden, filtrosOrden, fechaOrden, type OrdenApi, type DetalleOrdenApi, type FiltroOrden, type PaginaOrdenes } from '~/utils/ordenApi'
import type { RepuestoApi } from '~/utils/repuestoApi'
import { prepareOrderPhoto } from '~/utils/orderPhoto'

const props = defineProps<{ id: number }>()
const emit = defineEmits<{ close: []; edit: [orden: OrdenApi]; updated: [] }>()
const { authorization, user } = useAuthApi()
const { money, statusClass } = useHelpers()
const detalle = ref<DetalleOrdenApi | null>(null)
const orden = computed(() => detalle.value?.orden)
const mecanico = computed(() => user.value?.rol === 'MECANICO')
const activa = computed(() => !!orden.value && !['FINALIZADO', 'CANCELADO'].includes(orden.value.estado))
const planificable = computed(() => activa.value && !orden.value?.inicioFecha && !mecanico.value)
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
const tarea = ref('')
const cantidades = reactive<Record<number, number>>({})
const trabajo = reactive({ diagnostico: '', kilometraje: null as number | null | '', observacionesMecanico: '', aceite: '', filtros: [] as FiltroOrden[] })
const busqueda = ref('')
const consulta = useDebouncedValue(busqueda)
const repuestos = ref<RepuestoApi[]>([])
const buscando = ref(false)
const errorRepuestos = ref('')
const paginaRepuestos = ref(0)
const paginasRepuestos = ref(0)
const repuesto = ref<RepuestoApi | null>(null)
const manual = ref(false)
const item = reactive({ descripcion: '', cantidad: 1, precioUnitario: 0 })
const sector = ref('Tablero / kilometraje')
const fotos = ref<Record<number, string>>({})
const errorFotos = ref('')
const revisionFotos = ref(0)
let vigente = true
onBeforeUnmount(() => { vigente = false })

function cerrar() { if (!guardando.value) emit('close') }
function recibir(result: DetalleOrdenApi) {
  detalle.value = result
  for (const i of result.items) cantidades[i.id] = i.cantidad
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

async function agregarTarea() {
  if (!tarea.value.trim()) { error.value = 'Ingresá la descripción de la tarea.'; return }
  if (await ejecutar('/tareas', 'POST', { descripcion: tarea.value.trim() })) tarea.value = ''
}
function cambiarTarea(t: DetalleOrdenApi['tareas'][number], event: Event) {
  const checkbox = event.target as HTMLInputElement
  const completada = checkbox.checked
  checkbox.checked = t.completada
  void ejecutar(`/tareas/${t.id}`, 'PATCH', { completada })
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

watch(consulta, () => { paginaRepuestos.value = 0 })
watch([authorization, consulta, paginaRepuestos, activa], async ([headers, q], _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  repuestos.value = []
  errorRepuestos.value = ''
  buscando.value = !!(headers && q.trim() && activa.value)
  if (!buscando.value) return
  try {
    const result = await $fetch<PaginaOrdenes<RepuestoApi>>('/api/repuestos', { headers, signal: controller.signal, query: { q: q.trim(), activo: true, pagina: paginaRepuestos.value, tamano: 20 } })
    if (!controller.signal.aborted) {
      repuestos.value = result.contenido.filter(r => !detalle.value?.items.some(i => i.repuestoId === r.id))
      paginasRepuestos.value = result.totalPaginas
    }
  } catch (err) { if (!controller.signal.aborted) errorRepuestos.value = authError(err, 'No se pudieron buscar repuestos.') }
  finally { if (!controller.signal.aborted) buscando.value = false }
})

async function agregarItem() {
  if ((!manual.value || mecanico.value) && !repuesto.value) { error.value = 'Seleccioná un repuesto del catálogo.'; return }
  const solicitud = manual.value && !mecanico.value
    ? { ...item, repuestoId: null }
    : { repuestoId: repuesto.value!.id, cantidad: item.cantidad }
  if (await ejecutar('/repuestos', 'POST', { items: [solicitud] })) {
    repuesto.value = null
    busqueda.value = ''
    item.descripcion = ''
    item.cantidad = 1
    item.precioUnitario = 0
  }
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
  <CommonModalDialog class="dialog detail-modal" @close="cerrar">
    <div class="dialog-header"><div><span class="eyebrow">ÓRDENES DE TRABAJO</span><h2>Orden #{{ id }}</h2></div><button class="icon-button" aria-label="Cerrar detalle" :disabled="guardando" @click="cerrar"><X :size="18" /></button></div>
    <p v-if="cargando" role="status">Cargando orden…</p>
    <p v-if="error" role="alert">{{ error }} <button v-if="!detalle" class="button small" @click="revision++">Reintentar</button></p>
    <p v-if="mensaje" role="status">{{ mensaje }}</p>
    <template v-if="detalle && orden">
      <div class="detail-intro"><div><span :class="['badge', statusClass(estadosOrden[orden.estado])]">{{ estadosOrden[orden.estado] }}</span><h2>{{ orden.marca }} {{ orden.modelo }} {{ orden.version }}</h2><span class="plate">{{ orden.patente }}</span><p>{{ orden.clienteNombre }}</p></div><CommonBrandLogo :brand="orden.marca" /></div>
      <fieldset class="detail-body" :disabled="guardando">
        <dl class="preview-details">
          <div><dt>Trabajo</dt><dd>{{ orden.descripcion }}</dd></div>
          <div><dt>Tipos de trabajo</dt><dd>{{ orden.tiposTrabajo.map(t => tiposTrabajoOrden[t]).join(' · ') }}</dd></div>
          <div><dt>Mecánico</dt><dd>{{ orden.mecanicoNombre || 'Sin asignar' }}</dd></div>
          <div><dt>Creación</dt><dd>{{ fechaOrden(orden.fechaCreacion) }}</dd></div>
          <div v-if="orden.turnoId"><dt>Turno #{{ orden.turnoId }}</dt><dd>{{ orden.fechaTurno }} {{ orden.horaInicioTurno?.slice(0, 5) }}</dd></div>
          <div><dt>Ingreso</dt><dd>{{ fechaOrden(orden.ingresoFecha) }}</dd></div>
          <div><dt>Inicio</dt><dd>{{ fechaOrden(orden.inicioFecha) }}</dd></div>
          <div><dt>Finalización</dt><dd>{{ fechaOrden(orden.finFecha) }}</dd></div>
          <div v-if="orden.presupuestoId"><dt>Presupuesto</dt><dd>#{{ orden.presupuestoId }}</dd></div>
          <div v-if="orden.indicaciones"><dt>Indicaciones</dt><dd>{{ orden.indicaciones }}</dd></div>
          <div v-if="orden.motivoCancelacion"><dt>Motivo de baja</dt><dd>{{ orden.motivoCancelacion }}</dd></div>
        </dl>
        <section class="detail-section">
          <h3>Tareas · {{ detalle.progreso }}%</h3>
          <ul class="task-list"><li v-for="t in detalle.tareas" :key="t.id"><label class="check-label"><input type="checkbox" :checked="t.completada" :disabled="!enProceso" @change="cambiarTarea(t, $event)" /> {{ t.descripcion }}</label><button v-if="activa && !mecanico" class="icon-button" :aria-label="`Quitar tarea ${t.descripcion}`" @click="ejecutar(`/tareas/${t.id}`, 'DELETE')"><Trash2 :size="15" /></button></li></ul>
          <form v-if="activa && !mecanico" class="inline-form" @submit.prevent="agregarTarea"><label>Nueva tarea<input v-model="tarea" maxlength="500" required /></label><button class="button small"><Plus :size="15" /> Agregar tarea</button></form>
        </section>
        <section class="detail-section">
          <h3>Diagnóstico y observaciones</h3>
          <form v-if="enProceso" class="form-fields" @submit.prevent="ejecutar('/trabajo', 'PUT', { diagnostico: trabajo.diagnostico, kilometraje: trabajo.kilometraje === '' ? null : trabajo.kilometraje, observacionesMecanico: trabajo.observacionesMecanico })">
            <label>Diagnóstico<textarea v-model="trabajo.diagnostico" maxlength="4000" rows="3" /></label>
            <label>Kilometraje<input v-model.number="trabajo.kilometraje" type="number" min="0" max="9999999" step="1" /></label>
            <label>Observaciones del mecánico<textarea v-model="trabajo.observacionesMecanico" maxlength="4000" rows="3" /></label>
            <button class="button">Guardar trabajo</button>
          </form>
          <template v-else><p>{{ orden.diagnostico || 'Sin diagnóstico registrado.' }}</p><p>{{ orden.observacionesMecanico }}</p><p>Kilometraje: {{ orden.kilometraje === null ? 'Sin registrar' : orden.kilometraje.toLocaleString('es-AR') }}</p></template>
          <p v-if="orden.aceite">Aceite: {{ orden.aceite }}</p><p v-if="orden.filtros.length">{{ orden.filtros.map(f => filtrosOrden[f]).join(' · ') }}</p>
        </section>
        <section class="detail-section">
          <h3>Repuestos y materiales</h3>
          <p v-if="activa" class="muted">Planificar no descuenta stock. Al iniciar se consumen todos los materiales; durante el trabajo, se descuenta cada agregado.</p>
          <div v-if="detalle.items.length" class="table-scroll"><table><thead><tr><th>REPUESTO</th><th>CANTIDAD</th><th>PRECIO</th><th>SUBTOTAL</th><th>ACCIONES</th></tr></thead><tbody>
            <tr v-for="i in detalle.items" :key="i.id"><td>{{ i.descripcion }}<small class="muted"> {{ i.codigoOem }} · {{ i.consumido ? 'Consumido' : 'Planificado' }}{{ i.presupuestado ? ' · Presupuestado' : '' }}</small></td><td><form v-if="activa" class="inline-form" @submit.prevent="ejecutar(`/repuestos/${i.id}`, 'PATCH', { cantidad: cantidades[i.id] })"><input v-model.number="cantidades[i.id]" :aria-label="`Cantidad de ${i.descripcion}`" type="number" :min="mecanico ? i.cantidad : 0.001" max="999999999999.999" step="0.001" required /><button class="button small" :aria-label="`Guardar cantidad de ${i.descripcion}`"><Check :size="15" /></button></form><span v-else>{{ i.cantidad }}</span></td><td>{{ money(i.precioUnitario) }}</td><td>{{ money(i.subtotal) }}</td><td><button v-if="activa && !mecanico" class="button small" @click="ejecutar(`/repuestos/${i.id}`, 'DELETE')">{{ i.consumido ? 'Devolver y quitar' : 'Quitar' }}</button></td></tr>
          </tbody></table></div><p v-else class="muted">Sin repuestos asignados.</p>
          <form v-if="activa" class="form-fields" @submit.prevent="agregarItem">
            <label v-if="!mecanico" class="check-label"><input v-model="manual" type="checkbox" /> Concepto manual sin inventario</label>
            <template v-if="!manual || mecanico">
              <label>Buscar repuesto<input v-model="busqueda" maxlength="250" placeholder="Descripción, marca o código…" /></label>
              <p v-if="buscando" role="status">Buscando repuestos…</p><p v-if="errorRepuestos" role="alert">{{ errorRepuestos }}</p>
              <div v-if="consulta && consulta === busqueda" class="vehicle-results"><button v-for="r in repuestos" :key="r.id" type="button" class="vehicle-result" @click="repuesto = r; busqueda = ''">{{ r.descripcion }} · {{ r.codigoOem }} · Stock {{ r.stock }} · {{ money(r.precioPublico) }}</button><p v-if="!buscando && !repuestos.length && !errorRepuestos" class="muted">No hay repuestos disponibles en esta página. Los ya asignados se modifican en la tabla.</p><div v-if="paginasRepuestos > 1"><button type="button" class="button small" :disabled="paginaRepuestos === 0 || buscando" @click="paginaRepuestos--">Anterior</button><button type="button" class="button small" :disabled="paginaRepuestos + 1 >= paginasRepuestos || buscando" @click="paginaRepuestos++">Siguiente</button></div></div>
              <p v-if="repuesto">Seleccionado: {{ repuesto.descripcion }} · {{ money(repuesto.precioPublico) }}</p>
            </template>
            <template v-else><label>Descripción del concepto<input v-model="item.descripcion" required maxlength="250" /></label><label>Precio unitario<input v-model.number="item.precioUnitario" type="number" min="0" max="999999999999.99" step="0.01" required /></label></template>
            <label>Cantidad<input v-model.number="item.cantidad" type="number" min="0.001" max="999999999999.999" step="0.001" required /></label><button class="button"><Plus :size="16" /> Agregar material</button>
          </form>
          <dl class="preview-details"><div><dt>Repuestos</dt><dd>{{ money(detalle.totalRepuestos) }}</dd></div><div><dt>Mano de obra</dt><dd>{{ money(orden.manoObra) }}</dd></div><div><dt>IVA ({{ orden.porcentajeIva }}%)</dt><dd>{{ money(detalle.montoIva) }}</dd></div><div><dt>Total</dt><dd><strong>{{ money(detalle.total) }}</strong></dd></div></dl>
        </section>
        <section class="detail-section">
          <h3>Fotos · {{ detalle.fotos.length }} de 8</h3>
          <p v-if="errorFotos" role="alert">{{ errorFotos }} <button class="button small" @click="revisionFotos++">Reintentar fotos</button></p>
          <div class="order-photos"><figure v-for="f in detalle.fotos" :key="f.id"><a v-if="fotos[f.id]" :href="fotos[f.id]" target="_blank" rel="noopener"><img :src="fotos[f.id]" :alt="f.sector" /></a><span v-else>Cargando imagen…</span><figcaption>{{ f.sector }} · {{ fechaOrden(f.fecha) }}</figcaption><button v-if="admiteFotos" class="button small" @click="ejecutar(`/fotos/${f.id}`, 'DELETE')">Quitar foto</button></figure></div>
          <template v-if="admiteFotos"><label>Sector de la foto<input v-model="sector" maxlength="80" /></label><label class="photo-upload"><Camera :size="17" /> Adjuntar foto<input type="file" accept="image/*" :disabled="detalle.fotos.length >= 8 || !sector.trim()" @change="cargarFoto" /></label><p class="muted">Hasta 8 fotos. Se preparan en JPEG antes de subirlas.</p></template>
        </section>
        <form v-if="finalizando && enProceso" class="form-fields detail-section" @submit.prevent="finalizar">
          <h3>Finalizar orden</h3><label>Kilometraje al finalizar<input v-model.number="trabajo.kilometraje" type="number" min="0" max="9999999" step="1" required /></label>
          <template v-if="service"><label>Aceite utilizado<input v-model="trabajo.aceite" required maxlength="120" /></label><fieldset><legend>Filtros reemplazados</legend><label v-for="(label, key) in filtrosOrden" :key="key" class="check-label"><input v-model="trabajo.filtros" type="checkbox" :value="key" /> {{ label }}</label></fieldset></template>
          <label>Observaciones finales<textarea v-model="trabajo.observacionesMecanico" maxlength="4000" rows="3" /></label>
          <p class="muted">El cierre actualiza el kilometraje del vehículo y finaliza el turno vinculado.</p><button class="button primary">Confirmar finalización</button><button type="button" class="button" @click="finalizando = false">Volver al trabajo</button>
        </form>
        <form v-if="cancelando && activa && !mecanico" class="form-fields detail-section" @submit.prevent="cancelar"><h3>Dar de baja la orden</h3><label>Motivo de baja<textarea v-model="motivo" required maxlength="500" rows="3" /></label><p class="muted">Los materiales consumidos se conservan. Devolvé los recuperados antes de cancelar.</p><button class="button primary">Confirmar baja</button><button type="button" class="button" @click="cancelando = false">Volver</button></form>
      </fieldset>
      <footer class="modal-footer">
        <button class="button" :disabled="guardando" @click="cerrar">Cerrar</button>
        <button v-if="planificable" class="button" :disabled="guardando" @click="emit('edit', orden)"><Pencil :size="16" /> Editar planificación</button>
        <button v-if="orden.estado === 'PENDIENTE_INGRESO' && !mecanico" class="button primary" :disabled="guardando" @click="ejecutar('/ingreso', 'POST')">Registrar ingreso</button>
        <button v-if="orden.estado === 'EN_ESPERA'" class="button primary" :disabled="guardando || !orden.mecanicoId" @click="ejecutar('/inicio', 'POST')"><Play :size="16" /> Iniciar orden</button>
        <button v-if="enProceso && !finalizando" class="button primary" :disabled="guardando" @click="finalizando = true; cancelando = false">Finalizar orden</button>
        <button v-if="activa && !mecanico && !cancelando" class="button" :disabled="guardando" @click="cancelando = true; finalizando = false">Dar de baja</button>
      </footer>
    </template>
  </CommonModalDialog>
</template>

<style scoped>
fieldset.detail-body { border: 0; padding: 20px; min-width: 0; }
.detail-intro > :last-child { max-width: 80px; }
.detail-section { margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--border, #d8dee8); }
.detail-section h3 { margin-bottom: 12px; }
.inline-form, .task-list li { display: flex; align-items: center; gap: 8px; }
.task-list { padding: 0; list-style: none; }
.task-list li { justify-content: space-between; }
.inline-form input[type=number] { width: 100px; }
.order-photos { display: flex; flex-wrap: wrap; gap: 12px; }
.order-photos figure { margin: 0; width: 180px; }
.order-photos img { width: 100%; height: 130px; object-fit: cover; border-radius: 8px; }
.photo-upload { margin-top: 12px; }
dd, td, p { overflow-wrap: anywhere; }
td small { display: block; }
.preview-details > div { display: grid; grid-template-columns: minmax(100px, 1fr) 2fr; gap: 12px; margin: 8px 0; }
.preview-details dd { margin: 0; white-space: pre-wrap; }
.check-label { display: flex; align-items: center; gap: 6px; }
.check-label input { width: auto; }
.vehicle-results { max-height: 240px; overflow-y: auto; }
.vehicle-result { display: block; width: 100%; text-align: left; padding: 12px; border-bottom: 1px solid var(--border, #d8dee8); }
</style>
