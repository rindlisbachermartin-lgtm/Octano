<script setup lang="ts">
import { X, CircleCheck, ExternalLink, RefreshCw, QrCode } from 'lucide-vue-next'
import QRCode from 'qrcode'
import type { Vehicle } from '~/types'

const props = defineProps<{
  vehicleId: number | null
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { db, client } = useDatabase()
const { statusClass } = useHelpers()

useModalEscape(() => props.open, () => emit('close'))

const selectedVehicle = computed(() =>
  db.value.vehicles.find((v) => v.id === props.vehicleId)
)

const vehicleOrders = computed(() => {
  if (!props.vehicleId) return []
  return db.value.orders.filter((o) => o.vehicle === props.vehicleId)
})

const qr = ref('')
const assignModalOpen = ref(false)

watch(
  () => [props.vehicleId, selectedVehicle.value?.qrCode],
  async () => {
    const v = selectedVehicle.value
    if (v?.qrCode && import.meta.client) {
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
  assignModalOpen.value = true
}
</script>

<template>
  <dialog v-if="open && selectedVehicle" class="dialog" open>
    <div class="dialog-header">
      <h2>Ficha del vehículo</h2>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <div class="detail-intro">
      <div>
        <h2>{{ selectedVehicle.brand }} {{ selectedVehicle.model }}</h2>
        <span class="plate">{{ selectedVehicle.plate }}</span>
        <p>
          {{ selectedVehicle.year }} · {{ selectedVehicle.engine }} ·
          {{ Number(selectedVehicle.km).toLocaleString('es-AR') }} km
        </p>
        <p>{{ client(selectedVehicle.client)?.name }}</p>
      </div>
    </div>

    <div class="detail-body">
      <!-- QR Block: If assigned -->
      <div v-if="selectedVehicle.qrCode && qr" class="qr-block">
        <img :src="qr" :alt="`QR ${selectedVehicle.qrCode}`" />
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
              @click="emit('close')"
            >
              Ver ficha pública <ExternalLink :size="15" />
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
              @click="emit('close')"
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
    </div>

    <footer class="modal-footer">
      <button type="button" class="button" @click="emit('close')">Cerrar</button>
    </footer>

    <!-- Nested QR assignment modal -->
    <VehiculosModalVincularQr
      :open="assignModalOpen"
      :preselected-vehicle-id="selectedVehicle.id"
      @close="assignModalOpen = false"
    />
  </dialog>
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

:global(html.dark) .unassigned-qr-block {
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(255, 255, 255, 0.15);
}

:global(html.dark) .unassigned-qr-placeholder {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(255, 255, 255, 0.2);
  color: #8e8e93;
}

:global(html.dark) .code-pill {
  background: rgba(255, 255, 255, 0.08);
  color: #f5f5f7;
}
</style>
