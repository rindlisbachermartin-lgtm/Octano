<script setup lang="ts">
import { X, QrCode, CheckCircle2, Search, ArrowRight } from 'lucide-vue-next'

const props = defineProps<{
  open: boolean
  preselectedVehicleId?: number | null
  preselectedCode?: string | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'assigned', data: { qrCode: string; vehicleId: number }): void
}>()

const { db, availableQrs, assignQrToVehicle, client } = useDatabase()
const { notify } = useWorkshopToast()
const { matches } = useHelpers()

useModalEscape(() => props.open, () => emit('close'))

const selectedCode = ref('')
const customCode = ref('')
const selectedVehicleId = ref<number | null>(null)
const searchVehicle = ref('')
const debouncedSearch = useDebouncedValue(searchVehicle)
const useManualCode = ref(false)
const formError = ref('')
const selectedVehicle = computed(() => db.value.vehicles.find((v) => v.id === selectedVehicleId.value))

watch(
  () => [props.open, props.preselectedVehicleId, props.preselectedCode] as const,
  ([isOpen, id, code]) => {
    if (!isOpen) return
    selectedVehicleId.value = id ?? null
    selectedCode.value = code || availableQrs.value[0]?.code || ''
    customCode.value = ''
    searchVehicle.value = ''
    useManualCode.value = availableQrs.value.length === 0
    formError.value = ''
  },
  { immediate: true }
)

const activeCode = computed(() => {
  if (useManualCode.value) return customCode.value.trim().toUpperCase()
  return selectedCode.value
})

const filteredVehicles = computed(() => {
  const q = debouncedSearch.value.trim()
  if (!q) return []
  return db.value.vehicles.filter((v) => {
    const c = client(v.client)
    return matches(q, v.plate, v.brand, v.model, c?.name, c?.doc, c?.phone)
  })
})

function handleConfirm() {
  formError.value = ''
  if (!activeCode.value || !selectedVehicle.value) { formError.value = 'Seleccioná un código QR y un vehículo.'; return }
  if (!/^[A-Z0-9-]{3,40}$/.test(activeCode.value)) { formError.value = 'Usá entre 3 y 40 caracteres: letras, números o guiones.'; return }
  if (selectedVehicle.value.qrCode?.trim().toUpperCase() === activeCode.value) { formError.value = 'Ese QR ya está asignado a este vehículo. Elegí un código nuevo.'; return }
  const registeredQr = db.value.qrCodes.find(qr => qr.code.trim().toUpperCase() === activeCode.value)
  if (!registeredQr) { formError.value = 'Ese QR todavía no fue creado. Generá primero una plantilla desde Códigos QR.'; return }
  if (registeredQr.status !== 'disponible') { formError.value = 'Ese código ya no está disponible. Elegí otro.'; return }
  const success = assignQrToVehicle(activeCode.value, selectedVehicle.value.id)
  if (success) {
    const v = db.value.vehicles.find((item) => item.id === selectedVehicleId.value)
    notify(`Código QR ${activeCode.value} vinculado al vehículo ${v?.plate || ''}.`)
    emit('assigned', { qrCode: activeCode.value, vehicleId: selectedVehicle.value.id })
    emit('close')
  } else {
    formError.value = 'Ese QR ya está asignado a otro vehículo. Elegí un código disponible.'
  }
}
</script>

<template>
  <CommonFormPage v-if="open">
    <div class="dialog-header">
      <div style="display: flex; align-items: center; gap: 8px">
        <QrCode :size="20" />
        <h2>{{ selectedVehicle?.qrCode ? 'Asignar nuevo QR' : 'Asignar QR' }}</h2>
      </div>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <div class="detail-body">
      <p v-if="selectedVehicle?.qrCode" class="muted">QR actual: <strong>{{ selectedVehicle.qrCode }}</strong>. Se reemplazará al confirmar el código nuevo.</p>
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
            Escribir código
          </button>
        </div>

        <div v-if="!useManualCode" style="margin-top: 10px">
          <select v-if="availableQrs.length" v-model="selectedCode" aria-label="Código QR disponible" style="font-family: monospace; font-size: 13px">
            <option v-for="item in availableQrs" :key="item.code" :value="item.code">
              {{ item.code }} — Disponible (generado el {{ item.createdAt }})
            </option>
          </select>
          <div v-else class="empty-state" style="padding: 10px; font-size: 12px">
            No quedan QR disponibles. Generá una plantilla desde Códigos QR antes de asignarlos.
          </div>
        </div>

        <div v-else style="margin-top: 10px">
          <input
            v-model="customCode"
            aria-label="Código QR del sticker"
            maxlength="40"
            placeholder="Ej. OCT-2045 o código del sticker…"
            style="font-family: monospace; text-transform: uppercase"
          />
          <p class="muted search-hint">Solo podés asignar códigos que ya fueron generados en Códigos QR.</p>
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

          <p v-if="!searchVehicle.trim()" class="muted search-hint">Empezá a escribir para buscar vehículos.</p>
          <div v-else class="vehicles-scroll-box">
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
            <p v-if="!filteredVehicles.length && searchVehicle.trim() === debouncedSearch.trim()" class="muted">No se encontraron vehículos con esa búsqueda.</p>
          </div>
        </template>
      </div>

      <div class="summary-preview" v-if="activeCode && selectedVehicleId">
        <span>Vinculación:</span>
        <span class="qr-code-pill">{{ activeCode }}</span>
        <ArrowRight :size="14" />
        <span class="plate small-plate">{{ db.vehicles.find(v => v.id === selectedVehicleId)?.plate }}</span>
      </div>
      <p v-if="formError" class="error-message" role="alert">{{ formError }}</p>

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
  </CommonFormPage>
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

:global(html.dark .vehicles-scroll-box) {
  border-color: rgba(255, 255, 255, 0.1);
  background: #202023;
}
:global(html.dark .vehicle-select-row) {
  border-bottom-color: rgba(255, 255, 255, 0.06);
  color: #f5f5f7;
}
:global(html.dark .vehicle-select-row:hover) {
  background: rgba(255, 255, 255, 0.06);
}
:global(html.dark .vehicle-select-row.active) {
  background: rgba(10, 132, 255, 0.16);
  border-color: rgba(10, 132, 255, 0.3);
}
:global(html.dark .summary-preview) {
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(255, 255, 255, 0.08);
  color: #f5f5f7;
}
:global(html.dark .qr-code-pill) {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}
</style>
