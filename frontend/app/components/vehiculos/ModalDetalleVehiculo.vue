<script setup lang="ts">
import { X, CircleCheck, ExternalLink, RefreshCw, QrCode } from '@lucide/vue'
import QRCode from 'qrcode'
import type { Vehicle } from '~/types'

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
const { statusClass } = useHelpers()
const auth = useAuthApi()
const historial = ref<{ id: number; clienteAnteriorId: number | null; clienteNuevoId: number; fecha: string }[]>([])
const errorHistorial = ref('')
const cargandoHistorial = ref(false)
const nombresTitulares = ref<Record<number, string>>({})


const selectedVehicle = computed(() =>
  props.vehicle === undefined ? db.value.vehicles.find((v) => v.id === props.vehicleId) : props.vehicle
)

watch(() => props.open ? props.vehicle : null, async (vehiculo, _, onCleanup) => {
  historial.value = []
  nombresTitulares.value = {}
  errorHistorial.value = ''
  cargandoHistorial.value = false
  const headers = auth.authorization.value
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
  return db.value.orders.filter((o) => o.vehicle === props.vehicleId)
})

const qr = ref('')

watch(
  () => [props.vehicleId, selectedVehicle.value?.qrCode],
  async () => {
    const v = selectedVehicle.value
    if (v?.qrCode && !v.registro && import.meta.client) {
      try {
        qr.value = await QRCode.toDataURL(
          `${window.location.origin}/qr/${v.qrCode}`,
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
  <CommonModalDialog v-if="open && selectedVehicle" class="dialog" @close="cerrar">
    <div class="dialog-header">
      <h2>Ficha del vehículo</h2>
      <button class="icon-button" aria-label="Cerrar" :disabled="busy" @click="cerrar">
        <X :size="18" />
      </button>
    </div>

    <div class="detail-intro">
      <div>
        <h2>{{ selectedVehicle.brand }} {{ selectedVehicle.model }}</h2>
        <span class="plate">{{ selectedVehicle.plate }}</span>
        <p>
          {{ selectedVehicle.year }} · {{ selectedVehicle.version || selectedVehicle.engine || 'Sin versión' }} ·
          {{ kilometrajeVisible(selectedVehicle) }}
        </p>
        <p>{{ selectedVehicle.registro?.titular ?? client(selectedVehicle.client)?.name }}</p>
      </div>
    </div>

    <div class="detail-body">
      <template v-if="selectedVehicle.registro">
        <p v-if="selectedVehicle.qrCode">QR: {{ selectedVehicle.qrCode }}</p>
        <h3>Historial de titulares</h3>
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
        <span :class="['badge', statusClass(o.status)]">{{ o.status }}</span>
      </div>
      <p v-if="!vehicleOrders.length" class="muted">
        Este vehículo todavía no tiene órdenes registradas.
      </p>
      <template v-if="selectedVehicle.ownershipHistory?.length">
        <h3>Cambios de titular</h3>
        <div v-for="(change, index) in selectedVehicle.ownershipHistory" :key="index" class="history-row">
          <RefreshCw :size="18" />
          <div>
            <strong>{{ client(change.from)?.name || 'Titular anterior' }} → {{ client(change.to)?.name || 'Nuevo titular' }}</strong>
            <small>{{ new Date(change.date).toLocaleDateString('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' }) }}</small>
          </div>
        </div>
      </template>
      </template>
      <p v-if="error" class="error-message" role="alert">{{ error }}</p>
    </div>

    <footer class="modal-footer">
      <button type="button" class="button" :disabled="busy" @click="emit('edit', selectedVehicle)">Editar vehículo</button>
      <button v-if="selectedVehicle.registro" type="button" class="button" :disabled="busy" @click="emit('change-status', selectedVehicle)">{{ selectedVehicle.registro.activo ? 'Archivar' : 'Reactivar' }}</button>
      <button type="button" class="button" :disabled="busy" @click="cerrar">Cerrar</button>
    </footer>

  </CommonModalDialog>
</template>

<style scoped>
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
