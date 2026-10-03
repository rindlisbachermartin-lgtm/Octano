<script setup lang="ts">
import { QrCode, CheckCircle2, Car, Search, ArrowRight, ExternalLink } from 'lucide-vue-next'
import QRCode from 'qrcode'
import type { Vehicle } from '~/types'

definePageMeta({
  layout: 'blank'
})

const route = useRoute()
const router = useRouter()
const { db, client, vehicleName, assignQrToVehicle } = useDatabase()
const { notify } = useWorkshopToast()
const { matches } = useHelpers()

const code = computed(() => String(route.params.code || '').trim().toUpperCase())

const assignedVehicle = computed(() =>
  db.value.vehicles.find((v) => v.qrCode?.trim().toUpperCase() === code.value)
)

const searchVehicle = ref('')
const debouncedSearch = useDebouncedValue(searchVehicle)
const selectedVehicleId = ref<number | null>(null)
const qrImage = ref('')
const assignedSuccess = ref(false)

const candidateVehicles = computed(() => {
  const query = debouncedSearch.value.trim()
  if (!query) return []
  return db.value.vehicles.filter((v) => {
    const c = client(v.client)
    return matches(query, v.plate, v.brand, v.model, c?.name, c?.doc, c?.phone)
  })
})

async function generateQrPreview() {
  if (!import.meta.client || !code.value) return
  try {
    qrImage.value = await QRCode.toDataURL(
      `${window.location.origin}/qr/${code.value}`,
      { margin: 1, width: 160, color: { dark: '#0f172a', light: '#ffffff' } }
    )
  } catch (e) {
    console.error(e)
  }
}

function handleAssign() {
  if (!selectedVehicleId.value) return
  const success = assignQrToVehicle(code.value, selectedVehicleId.value)
  if (success) {
    assignedSuccess.value = true
    const v = db.value.vehicles.find((item) => item.id === selectedVehicleId.value)
    notify(`Código ${code.value} vinculado a la patente ${v?.plate || ''}.`)
  }
}

onMounted(() => {
  generateQrPreview()
  // If already assigned, automatically redirect after a brief moment or offer direct button
  if (assignedVehicle.value) {
    router.replace(`/ficha/${assignedVehicle.value.id}`)
  }
})
</script>

<template>
  <div class="public-page qr-resolution-page">
    <NuxtLink to="/" class="brand">
      <CommonOctanoLogo />
      <span>octa<span class="brand-light">no</span></span>
    </NuxtLink>

    <!-- State 1: Already Assigned (redirecting or showing direct link) -->
    <div v-if="assignedVehicle" class="panel qr-card-container">
      <div class="qr-status-icon success">
        <CheckCircle2 :size="48" />
      </div>
      <h2>Código QR asignado</h2>
      <p class="muted">
        Este código está vinculado al vehículo <strong>{{ assignedVehicle.brand }} {{ assignedVehicle.model }}</strong>.
      </p>

      <div class="assigned-box">
        <span class="plate large-plate">{{ assignedVehicle.plate }}</span>
        <span class="qr-code-pill">{{ code }}</span>
      </div>

      <NuxtLink :to="`/ficha/${assignedVehicle.id}`" class="button primary" style="width: 100%; margin-top: 1rem">
        Ir a la ficha técnica del vehículo <ArrowRight :size="16" />
      </NuxtLink>
    </div>

    <!-- State 2: Assignment Success Confirmation -->
    <div v-else-if="assignedSuccess" class="panel qr-card-container">
      <div class="qr-status-icon success">
        <CheckCircle2 :size="48" />
      </div>
      <h2>¡Vehículo y código QR vinculados!</h2>
      <p class="muted">
        La patente quedó relacionada correctamente con este código QR.
      </p>

      <div v-if="selectedVehicleId" class="assigned-box">
        <span class="plate large-plate">{{ db.vehicles.find(v => v.id === selectedVehicleId)?.plate }}</span>
        <span class="qr-code-pill">{{ code }}</span>
      </div>

      <div class="buttons-stack" style="margin-top: 1.5rem">
        <NuxtLink :to="`/ficha/${selectedVehicleId}`" class="button primary">
          Ver ficha del vehículo <ExternalLink :size="16" />
        </NuxtLink>
        <NuxtLink to="/vehiculos" class="button outlined">
          Volver al panel del taller
        </NuxtLink>
      </div>
    </div>

    <!-- State 3: Unassigned QR - Assign to a Vehicle -->
    <div v-else class="panel qr-card-container">
      <div class="qr-header-top">
        <div class="qr-image-wrapper">
          <img v-if="qrImage" :src="qrImage" :alt="code" />
          <QrCode v-else :size="36" />
        </div>
        <div>
          <span class="badge green">Código disponible</span>
          <h2 style="margin-top: 4px">{{ code }}</h2>
          <p class="muted" style="font-size: 11px">
            Escaneaste este sticker. Ahora asignalo a la patente del vehículo.
          </p>
        </div>
      </div>

      <hr class="divider" />

      <div class="selection-section">
        <label>
          <strong>Seleccionar vehículo o buscar por patente:</strong>
          <div class="search-box" style="margin-top: 8px">
            <Search :size="16" />
            <input
              v-model="searchVehicle"
              placeholder="Escribí la patente, modelo o cliente…"
              aria-label="Buscar patente o modelo"
            />
          </div>
        </label>

        <p v-if="!searchVehicle.trim()" class="muted search-hint">Empezá a escribir para buscar vehículos.</p>
        <div v-else class="vehicle-options-list">
          <div
            v-for="v in candidateVehicles"
            :key="v.id"
            class="vehicle-option-item"
            :class="{ selected: selectedVehicleId === v.id }"
            @click="selectedVehicleId = v.id"
          >
            <div class="v-opt-left">
              <span class="plate">{{ v.plate }}</span>
              <div>
                <strong>{{ v.brand }} {{ v.model }}</strong>
                <small class="muted">{{ client(v.client)?.name }}</small>
              </div>
            </div>
            <div class="v-opt-right">
              <span v-if="v.qrCode" class="badge neutral" title="Se reemplazará el código actual">
                Tiene QR: {{ v.qrCode }}
              </span>
              <span v-else class="badge green">Sin QR</span>
            </div>
          </div>

          <div v-if="!candidateVehicles.length && searchVehicle.trim() === debouncedSearch.trim()" class="empty-state" style="padding: 20px">
            No se encontraron vehículos con ese criterio.
          </div>
        </div>

        <button
          class="button primary"
          style="width: 100%; margin-top: 1.5rem"
          :disabled="!selectedVehicleId"
          @click="handleAssign"
        >
          <CheckCircle2 :size="17" />
          Vincular código {{ code }} al vehículo seleccionado
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.qr-resolution-page {
  max-width: 580px;
  margin: 0 auto;
  padding: 30px 18px;
}

.qr-card-container {
  padding: 28px;
  border-radius: 16px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  box-shadow: 0 4px 18px rgba(15, 23, 42, 0.05);
}

.qr-status-icon.success {
  color: #10b981;
  margin-bottom: 12px;
}

.qr-header-top {
  display: flex;
  align-items: center;
  gap: 16px;
}

.qr-image-wrapper {
  width: 72px;
  height: 72px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.qr-image-wrapper img {
  width: 66px;
  height: 66px;
  object-fit: contain;
}

.divider {
  border: 0;
  border-top: 1px solid #f1f5f9;
  margin: 20px 0;
}

.assigned-box {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 16px;
  margin: 16px 0;
}

.large-plate {
  font-size: 16px;
  padding: 6px 14px;
}

.qr-code-pill {
  font-family: monospace;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #1e293b;
  background: #e2e8f0;
  padding: 5px 10px;
  border-radius: 6px;
}

.vehicle-options-list {
  max-height: 240px;
  overflow-y: auto;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  margin-top: 12px;
}

.vehicle-option-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: background 0.15s ease;
}
.vehicle-option-item:last-child {
  border-bottom: 0;
}
.vehicle-option-item:hover {
  background: #f8fafc;
}
.vehicle-option-item.selected {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.v-opt-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.v-opt-left small {
  display: block;
}

.buttons-stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

:global(html.dark .qr-card-container) {
  background: #252528;
  border-color: rgba(255, 255, 255, 0.08);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
}
:global(html.dark .qr-image-wrapper) {
  background: #2c2c2e;
  border-color: rgba(255, 255, 255, 0.15);
}
:global(html.dark .assigned-box) {
  background: rgba(255, 255, 255, 0.04);
}
:global(html.dark .qr-code-pill) {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}
:global(html.dark .vehicle-options-list) {
  border-color: rgba(255, 255, 255, 0.08);
  background: #202023;
}
:global(html.dark .vehicle-option-item) {
  border-bottom-color: rgba(255, 255, 255, 0.06);
  color: #f5f5f7;
}
:global(html.dark .vehicle-option-item:hover) {
  background: rgba(255, 255, 255, 0.06);
}
:global(html.dark .vehicle-option-item.selected) {
  background: rgba(10, 132, 255, 0.16);
  border-color: rgba(10, 132, 255, 0.3);
}
:global(html.dark .divider) {
  border-top-color: rgba(255, 255, 255, 0.08);
}

</style>
