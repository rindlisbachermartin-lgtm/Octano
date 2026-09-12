<script setup lang="ts">
import { X, QrCode, CheckCircle2, Search, ArrowRight } from 'lucide-vue-next'
import type { Vehicle } from '~/types'

const props = defineProps<{
  open: boolean
  preselectedVehicleId?: number | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'assigned', data: { qrCode: string; vehicleId: number }): void
}>()

const { db, availableQrs, assignQrToVehicle, client } = useDatabase()
const { notify } = useToast()

useModalEscape(() => props.open, () => emit('close'))

const selectedCode = ref('')
const customCode = ref('')
const selectedVehicleId = ref<number | null>(null)
const searchVehicle = ref('')
const useManualCode = ref(false)

watch(
  () => props.preselectedVehicleId,
  (id) => {
    if (id) {
      selectedVehicleId.value = id
    }
  },
  { immediate: true }
)

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      if (props.preselectedVehicleId) {
        selectedVehicleId.value = props.preselectedVehicleId
      }
      if (availableQrs.value.length > 0 && !selectedCode.value) {
        selectedCode.value = availableQrs.value[0].code
      }
    }
  }
)

const activeCode = computed(() => {
  if (useManualCode.value) return customCode.value.trim().toUpperCase()
  return selectedCode.value
})

const filteredVehicles = computed(() => {
  const q = searchVehicle.value.trim().toLowerCase()
  return db.value.vehicles.filter((v) => {
    if (!q) return true
    const c = client(v.client)
    return (
      v.plate.toLowerCase().includes(q) ||
      v.brand.toLowerCase().includes(q) ||
      v.model.toLowerCase().includes(q) ||
      (c && c.name.toLowerCase().includes(q))
    )
  })
})

function handleConfirm() {
  if (!activeCode.value || !selectedVehicleId.value) return
  
  const success = assignQrToVehicle(activeCode.value, selectedVehicleId.value)
  if (success) {
    const v = db.value.vehicles.find((item) => item.id === selectedVehicleId.value)
    notify(`Código QR ${activeCode.value} vinculado al vehículo ${v?.plate || ''}.`)
    emit('assigned', { qrCode: activeCode.value, vehicleId: selectedVehicleId.value })
    emit('close')
  }
}
</script>

<template>
  <dialog v-if="open" class="dialog" open>
    <div class="dialog-header">
      <div style="display: flex; align-items: center; gap: 8px">
        <QrCode :size="20" />
        <h2>Vincular código QR a un vehículo</h2>
      </div>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <div class="detail-body">
      <!-- Step 1: Pick or Input QR Code -->
      <div>
        <label>
          <strong>1. Seleccioná el código QR del sticker:</strong>
        </label>

        <div style="display: flex; gap: 8px; margin-top: 6px">
          <button
            type="button"
            class="button small"
            :class="{ primary: !useManualCode }"
            @click="useManualCode = false"
          >
            Elegir de plantilla disponible ({{ availableQrs.length }})
          </button>
          <button
            type="button"
            class="button small"
            :class="{ primary: useManualCode }"
            @click="useManualCode = true"
          >
            Escribir o escanear código
          </button>
        </div>

        <div v-if="!useManualCode" style="margin-top: 10px">
          <select v-if="availableQrs.length" v-model="selectedCode" style="font-family: monospace; font-size: 13px">
            <option v-for="item in availableQrs" :key="item.code" :value="item.code">
              {{ item.code }} — Disponible (generado el {{ item.createdAt }})
            </option>
          </select>
          <div v-else class="empty-state" style="padding: 10px; font-size: 12px">
            No quedan QRs disponibles en la plantilla. Escribí uno manualmente o generá más en la sección de plantillas.
          </div>
        </div>

        <div v-else style="margin-top: 10px">
          <input
            v-model="customCode"
            placeholder="Ej. OCT-2045 o código del sticker…"
            style="font-family: monospace; text-transform: uppercase"
          />
        </div>
      </div>

      <!-- Step 2: Pick Vehicle -->
      <div style="margin-top: 12px">
        <!-- Si ya viene un vehículo preseleccionado, mostrarlo directamente sin buscar -->
        <template v-if="preselectedVehicleId">
          <label>
            <strong>2. Vehículo seleccionado:</strong>
          </label>
          <div
            v-if="db.vehicles.find(v => v.id === preselectedVehicleId)"
            class="vehicle-select-row active"
            style="margin-top: 6px; cursor: default"
          >
            <div style="display: flex; align-items: center; gap: 10px">
              <span class="plate small-plate">
                {{ db.vehicles.find(v => v.id === preselectedVehicleId)?.plate }}
              </span>
              <div>
                <strong>
                  {{ db.vehicles.find(v => v.id === preselectedVehicleId)?.brand }}
                  {{ db.vehicles.find(v => v.id === preselectedVehicleId)?.model }}
                </strong>
                <small class="muted" style="display: block">
                  {{ client(db.vehicles.find(v => v.id === preselectedVehicleId)?.client ?? 0)?.name }}
                </small>
              </div>
            </div>
          </div>
        </template>

        <!-- Si no viene preseleccionado, mostrar buscador completo -->
        <template v-else>
          <label>
            <strong>2. Seleccioná el vehículo / patente:</strong>
            <div class="search-box" style="margin-top: 6px">
              <Search :size="16" />
              <input
                v-model="searchVehicle"
                placeholder="Buscar por patente, modelo o cliente…"
                aria-label="Buscar vehículo para vincular"
              />
            </div>
          </label>

          <div class="vehicles-scroll-box">
            <div
              v-for="v in filteredVehicles"
              :key="v.id"
              class="vehicle-select-row"
              :class="{ active: selectedVehicleId === v.id }"
              @click="selectedVehicleId = v.id"
            >
              <div style="display: flex; align-items: center; gap: 10px">
                <span class="plate small-plate">{{ v.plate }}</span>
                <div>
                  <strong>{{ v.brand }} {{ v.model }}</strong>
                  <small class="muted" style="display: block">{{ client(v.client)?.name }}</small>
                </div>
              </div>
              <div>
                <span v-if="v.qrCode" class="badge neutral" title="Se reemplazará el código actual">
                  Actual: {{ v.qrCode }}
                </span>
                <span v-else class="badge green">Sin QR</span>
              </div>
            </div>
          </div>
        </template>
      </div>

      <div class="summary-preview" v-if="activeCode && selectedVehicleId">
        <span>Vinculación:</span>
        <span class="qr-code-pill">{{ activeCode }}</span>
        <ArrowRight :size="14" />
        <span class="plate small-plate">{{ db.vehicles.find(v => v.id === selectedVehicleId)?.plate }}</span>
      </div>

      <div class="modal-footer" style="padding: 16px 0 0; background: none">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button
          type="button"
          class="button primary"
          :disabled="!activeCode || !selectedVehicleId"
          @click="handleConfirm"
        >
          <CheckCircle2 :size="16" /> Confirmar vinculación
        </button>
      </div>
    </div>
  </dialog>
</template>

<style scoped>
.vehicles-scroll-box {
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  margin-top: 8px;
}

.vehicle-select-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: background 0.15s ease;
}
.vehicle-select-row:last-child {
  border-bottom: 0;
}
.vehicle-select-row:hover {
  background: #f8fafc;
}
.vehicle-select-row.active {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.small-plate {
  font-size: 11px;
  padding: 2px 6px;
}

.summary-preview {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  margin-top: 12px;
  font-size: 12px;
}

.qr-code-pill {
  font-family: monospace;
  font-size: 12px;
  font-weight: 700;
  color: #1e293b;
  background: #e2e8f0;
  padding: 3px 6px;
  border-radius: 4px;
}

:global(html.dark) .vehicles-scroll-box {
  border-color: rgba(255, 255, 255, 0.1);
  background: #202023;
}
:global(html.dark) .vehicle-select-row {
  border-bottom-color: rgba(255, 255, 255, 0.06);
  color: #f5f5f7;
}
:global(html.dark) .vehicle-select-row:hover {
  background: rgba(255, 255, 255, 0.06);
}
:global(html.dark) .vehicle-select-row.active {
  background: rgba(10, 132, 255, 0.16);
  border-color: rgba(10, 132, 255, 0.3);
}
:global(html.dark) .summary-preview {
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(255, 255, 255, 0.08);
  color: #f5f5f7;
}
:global(html.dark) .qr-code-pill {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}
</style>
