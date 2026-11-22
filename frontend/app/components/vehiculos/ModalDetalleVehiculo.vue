<script setup lang="ts">
import { X, CircleCheck, ExternalLink, RefreshCw, QrCode } from '@lucide/vue'
import QRCode from 'qrcode'
import type { Vehicle } from '~/types'
import { authError } from '~/composables/useAuthApi'

const props = defineProps<{
  vehicleId: number | null
  open: boolean
  vehicle?: Vehicle | null
  busy?: boolean
  error?: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'edit', vehicle: Vehicle): void
  (e: 'assignQr', vehicle: Vehicle): void
  (e: 'change-status', vehicle: Vehicle): void
}>()

const { db, client } = useDatabase()
const auth = useAuthApi()
const historial = ref<{ id: number; clienteAnteriorId: number | null; clienteNuevoId: number; fecha: string }[]>([])
const errorHistorial = ref('')
const cargandoHistorial = ref(false)
const nombresTitulares = ref<Record<number, string>>({})
interface TrabajoVehiculo {
  id: number
  descripcion: string
  fechaCreacion: string
  finFecha: string | null
}
const trabajos = ref<TrabajoVehiculo[]>([])
const paginaTrabajos = ref(0)
const paginasTrabajos = ref(0)
const cargandoTrabajos = ref(false)
const errorTrabajos = ref('')
const revisionTrabajos = ref(0)
const ordenConsulta = ref<number | null>(null)
watch([() => props.open, () => props.vehicleId], () => { ordenConsulta.value = null })
const vehiculoHistorial = computed(() => props.open && props.vehicle?.registro ? props.vehicle.id : null)
watch(vehiculoHistorial, () => { paginaTrabajos.value = 0 })
watch([auth.authorization, vehiculoHistorial, paginaTrabajos, revisionTrabajos], async ([headers, vehiculoId], _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  trabajos.value = []
  paginasTrabajos.value = 0
  errorTrabajos.value = ''
  cargandoTrabajos.value = !!(headers && vehiculoId)
  if (!headers || !vehiculoId) return
  try {
    // El historial pertenece al vehículo, incluso cuando cambia su titular.
    const result = await $fetch<{ contenido: TrabajoVehiculo[]; totalPaginas: number }>('/api/ordenes-trabajo', {
      headers, signal: controller.signal, query: { vehiculoId, estado: 'FINALIZADO', pagina: paginaTrabajos.value, tamano: 20 },
    })
    if (!controller.signal.aborted) { trabajos.value = result.contenido; paginasTrabajos.value = result.totalPaginas }
  } catch (err) {
    if (!controller.signal.aborted) errorTrabajos.value = authError(err, 'No se pudo cargar el historial de trabajos.')
  } finally { if (!controller.signal.aborted) cargandoTrabajos.value = false }
}, { immediate: true })


const selectedVehicle = computed(() =>
  props.vehicle === undefined ? db.value.vehicles.find((v) => v.id === props.vehicleId) : props.vehicle
)

watch([() => props.open ? props.vehicle : null, auth.authorization], async ([vehiculo, headers], _, onCleanup) => {
  historial.value = []
  nombresTitulares.value = {}
  errorHistorial.value = ''
  cargandoHistorial.value = false
  if (!vehiculo?.registro || !headers) return
  const consulta = new AbortController()
  onCleanup(() => consulta.abort())
  cargandoHistorial.value = true
  try {
    const opciones = { headers, signal: consulta.signal }
    const cambios = await $fetch<typeof historial.value>(`/api/vehiculos/${vehiculo.id}/titularidad`, opciones)
    const ids = new Set(cambios.flatMap(c => [c.clienteAnteriorId, c.clienteNuevoId]).filter(id => id !== null))
    ids.delete(vehiculo.client)
    const nombres = await Promise.all([...ids].map(async id => {
      try {
        const titular = await $fetch<{ nombre: string }>(`/api/clientes/${id}`, opciones)
        return [id, titular.nombre] as const
      } catch {
        return [id, `Cliente #${id}`] as const
      }
    }))
    if (!consulta.signal.aborted) {
      historial.value = cambios
      nombresTitulares.value = { ...Object.fromEntries(nombres), [vehiculo.client]: vehiculo.registro.titular }
    }
  } catch (err) {
    if (!consulta.signal.aborted) errorHistorial.value = authError(err, 'No pudimos cargar el historial de titulares.')
  } finally {
    if (!consulta.signal.aborted) cargandoHistorial.value = false
  }
}, { immediate: true })

function cerrar() {
  if (!props.busy) emit('close')
}

const vehicleOrders = computed(() => {
  if (!props.vehicleId) return []
  return db.value.orders.filter((o) => o.vehicle === props.vehicleId && o.status === 'Finalizado')
})

const qr = ref('')

watch(
  () => [props.vehicleId, selectedVehicle.value?.qrCode],
  async () => {
    const v = selectedVehicle.value
    if (v?.qrCode && !v.registro && import.meta.client) {
      try {
        qr.value = await QRCode.toDataURL(
          `${window.location.origin}/qr/${v.qrCode}?demo=1`,
          { margin: 1, width: 180, color: { dark: '#0f172a', light: '#ffffff' } }
        )
      } catch {
        qr.value = ''
      }
    } else {
      qr.value = ''
    }
  },
  { immediate: true }
)

function openReassign() {
  if (selectedVehicle.value) emit('assignQr', selectedVehicle.value)
}
</script>

<template>
  <CommonModalDialog v-if="open && selectedVehicle && ordenConsulta === null" class="dialog" @close="cerrar">
    <div class="dialog-header">
      <h2>Ficha del vehículo</h2>
      <button class="icon-button" aria-label="Cerrar" :disabled="busy" @click="cerrar">
        <X :size="18" />
      </button>
    </div>

    <div class="detail-intro">
      <div class="vehicle-detail-summary">
        <h2>{{ vehicleLabel(selectedVehicle.brand, selectedVehicle.model, selectedVehicle.version) }}</h2>
        <span class="plate">{{ selectedVehicle.plate }}</span>
        <p>
          {{ selectedVehicle.year }}<template v-if="vehicleFuel(selectedVehicle.version)"> · {{ vehicleFuel(selectedVehicle.version) }}</template> ·
          {{ kilometrajeVisible(selectedVehicle) }}
        </p>
        <p>{{ selectedVehicle.registro?.titular ?? client(selectedVehicle.client)?.name }}</p>
      </div>
      <CommonBrandLogo :brand="selectedVehicle.brand" class="vehicle-detail-brand" />
    </div>

    <div class="detail-body">
      <template v-if="selectedVehicle.registro">
        <section class="work-history" aria-label="Historial de trabajos">
          <h3>Historial de trabajos</h3>
          <p class="muted history-hint">Todos los trabajos del vehículo, incluidos los de titulares anteriores.</p>
          <p v-if="cargandoTrabajos" role="status">Cargando trabajos…</p>
          <p v-else-if="errorTrabajos" role="alert">{{ errorTrabajos }} <button class="text-button" @click="revisionTrabajos++">Reintentar</button></p>
          <template v-else>
            <article v-for="trabajo in trabajos" :key="trabajo.id" class="work-entry">
              <div><strong>{{ trabajo.descripcion }}</strong><small>{{ new Date(trabajo.finFecha || trabajo.fechaCreacion).toLocaleDateString('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' }) }} · OT #{{ trabajo.id }}</small></div>
              <button class="button small" @click="ordenConsulta = trabajo.id">Ver OT</button>
            </article>
            <p v-if="!trabajos.length" class="muted">Este vehículo todavía no tiene trabajos finalizados.</p>
          </template>
          <div v-if="paginasTrabajos > 1" class="history-pagination"><button class="button small" :disabled="cargandoTrabajos || paginaTrabajos === 0" @click="paginaTrabajos--">Anterior</button><span>{{ paginaTrabajos + 1 }} / {{ paginasTrabajos }}</span><button class="button small" :disabled="cargandoTrabajos || paginaTrabajos + 1 >= paginasTrabajos" @click="paginaTrabajos++">Siguiente</button></div>
        </section>
        <details :key="selectedVehicle.id" class="ownership-history">
        <summary>Historial de titulares</summary>
        <p v-if="cargandoHistorial" role="status">Cargando historial…</p>
        <p v-else-if="errorHistorial" role="alert">{{ errorHistorial }}</p>
        <div v-for="cambio in historial" :key="cambio.id" class="history-row">
          <RefreshCw :size="18" />
          <div>
            <strong v-if="cambio.clienteAnteriorId !== null">{{ nombresTitulares[cambio.clienteAnteriorId] }} → </strong>
            <strong>{{ nombresTitulares[cambio.clienteNuevoId] }}</strong>
            <small>{{ new Date(cambio.fecha).toLocaleDateString('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' }) }}</small>
          </div>
        </div>
        <p v-if="!cargandoHistorial && !errorHistorial && !historial.length" class="muted">No hay titulares registrados en el historial.</p>
        </details>
      </template>
      <template v-else>
      <!-- QR Block: If assigned -->
      <div v-if="selectedVehicle.qrCode" class="qr-block">
        <img v-if="qr" :src="qr" :alt="`QR ${selectedVehicle.qrCode}`" />
        <div v-else class="unassigned-qr-placeholder"><QrCode :size="38" /></div>
        <div>
          <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 6px">
            <span class="badge green">QR Asignado</span>
            <span class="code-pill">{{ selectedVehicle.qrCode }}</span>
          </div>
          <h3>Su historia, siempre a mano.</h3>
          <p>Escaneá este sticker pegado en el vehículo para acceder a su historial técnico.</p>
          <div style="display: flex; flex-wrap: wrap; gap: 10px; margin-top: 10px">
            <NuxtLink
              class="text-button"
              :to="`/ficha/${selectedVehicle.id}`"
              target="_blank"
              rel="noopener noreferrer"
              title="Abrir ficha pública en una pestaña nueva"
            >
              Ver ficha pública <ExternalLink :size="14" />
            </NuxtLink>
            <button class="text-button" @click="openReassign">
              <RefreshCw :size="14" /> Asignar nuevo QR (por pérdida)
            </button>
          </div>
        </div>
      </div>

      <!-- QR Block: If unassigned -->
      <div v-else class="qr-block unassigned-qr-block">
        <div class="unassigned-qr-placeholder">
          <QrCode :size="38" />
        </div>
        <div>
          <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 6px">
            <span class="badge neutral">Sin QR asignado</span>
          </div>
          <h3>Este vehículo no tiene QR vinculado</h3>
          <p class="muted" style="font-size: 11px">
            Podés imprimir una plantilla de stickers y asignarle un código escaneándolo, o vincular uno directamente ahora.
          </p>
          <div style="display: flex; flex-wrap: wrap; gap: 10px; margin-top: 10px">
            <button class="button small primary" @click="openReassign">
              <QrCode :size="14" /> Asignar código QR
            </button>
            <NuxtLink
              class="text-button"
              :to="`/ficha/${selectedVehicle.id}`"
              target="_blank"
              rel="noopener noreferrer"
              title="Abrir ficha pública en una pestaña nueva"
            >
              Ver ficha pública <ExternalLink :size="15" />
            </NuxtLink>
          </div>
        </div>
      </div>

      <h3>Actividad del vehículo</h3>
      <div
        v-for="o in vehicleOrders"
        :key="o.id"
        class="history-row"
      >
        <CircleCheck :size="18" />
        <div>
          <strong>{{ o.service }}</strong>
          <small>{{ o.date }} · OT #{{ o.id }}</small>
        </div>
        <button class="button small" @click="ordenConsulta = o.id">Ver OT</button>
      </div>
      <p v-if="!vehicleOrders.length" class="muted">
        Este vehículo todavía no tiene órdenes registradas.
      </p>
      <details v-if="selectedVehicle.ownershipHistory?.length" class="ownership-history">
        <summary>Historial de titulares</summary>
        <div v-for="(change, index) in selectedVehicle.ownershipHistory" :key="index" class="history-row">
          <RefreshCw :size="18" />
          <div>
            <strong>{{ client(change.from)?.name || 'Titular anterior' }} → {{ client(change.to)?.name || 'Nuevo titular' }}</strong>
            <small>{{ new Date(change.date).toLocaleDateString('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' }) }}</small>
          </div>
        </div>
      </details>
      </template>
      <p v-if="error" class="error-message" role="alert">{{ error }}</p>
    </div>

    <footer class="modal-footer">
      <button type="button" class="button" :disabled="busy" @click="emit('edit', selectedVehicle)">Editar vehículo</button>
      <button v-if="selectedVehicle.registro" type="button" class="button" :disabled="busy" @click="emit('change-status', selectedVehicle)">{{ selectedVehicle.registro.activo ? 'Archivar' : 'Reactivar' }}</button>
      <button type="button" class="button" :disabled="busy" @click="cerrar">Cerrar</button>
    </footer>

  </CommonModalDialog>
  <OrdenesConsultaApi v-if="open && selectedVehicle?.registro && ordenConsulta !== null" :id="ordenConsulta" @close="ordenConsulta = null" />
  <OrdenesModalDetalleOrden v-else-if="open && selectedVehicle && ordenConsulta !== null" :open="true" :order-id="ordenConsulta" @close="ordenConsulta = null" />
</template>

<style scoped>
.vehicle-detail-summary { flex: 1; min-width: 0; }
.vehicle-detail-brand { width: 80px; height: 64px; color: var(--muted); }
.history-hint { margin: 6px 0 14px; font-size: 12px; }
.work-entry { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--line); }
.work-entry strong { font-size: 13px; }
.work-entry small { display: block; color: var(--muted); font-size: 11px; margin-top: 5px; }
.ownership-history { border-top: 1px solid var(--line); padding-top: 16px; }
.ownership-history > summary { cursor: pointer; font-size: 12px; font-weight: 600; color: var(--muted); }
.history-pagination { display: flex; align-items: center; gap: 12px; margin-top: 16px; font-size: 12px; }
.code-pill {
  font-family: monospace;
  font-size: 11px;
  font-weight: 700;
  color: #0f172a;
  background: #e2e8f0;
  padding: 2px 6px;
  border-radius: 4px;
}

.unassigned-qr-block {
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  padding: 16px;
}

.unassigned-qr-placeholder {
  width: 120px;
  height: 120px;
  border: 2px dashed #94a3b8;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  background: #fff;
  flex-shrink: 0;
}

:global(html.dark .unassigned-qr-block) {
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(255, 255, 255, 0.15);
}

:global(html.dark .unassigned-qr-placeholder) {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(255, 255, 255, 0.2);
  color: #8e8e93;
}

:global(html.dark .code-pill) {
  background: rgba(255, 255, 255, 0.08);
  color: #f5f5f7;
}
</style>
