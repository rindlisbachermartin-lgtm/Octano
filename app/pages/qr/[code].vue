<script setup lang="ts">
import { QrCode, CheckCircle2, Search, ArrowRight, X } from 'lucide-vue-next'
import QRCode from 'qrcode'
import type { Vehicle } from '~/types'

definePageMeta({
  layout: 'blank'
})

const route = useRoute()
const router = useRouter()
const { db, assignQrToVehicle } = useDatabase()
const { notify } = useWorkshopToast()
const { isDark } = useTheme()

const code = computed(() => String(route.params.code || '').trim().toUpperCase())

const assignedVehicle = computed(() =>
  db.value.vehicles.find((v) => v.qrCode?.trim().toUpperCase() === code.value)
)

const searchVehicle = ref('')
const registerVehicleOpen = ref(false)
const pendingPlate = ref('')
const replacementVehicle = ref<Vehicle | null>(null)
const qrImage = ref('')
const assignmentError = ref('')
const normalizePlate = (value: string) => value.toUpperCase().replace(/[^A-Z0-9]/g, '')

watch(searchVehicle, () => { assignmentError.value = ''; replacementVehicle.value = null })

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

function bindVehicle(vehicle: Vehicle) {
  const success = assignQrToVehicle(code.value, vehicle.id)
  if (success) {
    registerVehicleOpen.value = false
    notify(`Código ${code.value} vinculado a la patente ${vehicle.plate}.`)
    router.replace(`/ficha/${vehicle.id}`)
  } else {
    assignmentError.value = 'No se pudo vincular el QR. Comprobá que no esté asignado a otro vehículo.'
  }
}

function handleAssign() {
  assignmentError.value = ''
  if (!/^[A-Z0-9-]{3,40}$/.test(code.value)) {
    assignmentError.value = 'El código QR no es válido.'
    return
  }
  const plate = searchVehicle.value.trim().toUpperCase()
  if (!/^(?:[A-Z]{3}[- ]?\d{3}|[A-Z]{2}[- ]?\d{3}[- ]?[A-Z]{2})$/.test(plate)) {
    assignmentError.value = 'Ingresá una patente completa: ABC-123 o AB-123-CD.'
    return
  }
  const existing = db.value.vehicles.find(vehicle => normalizePlate(vehicle.plate) === normalizePlate(plate))
  if (existing) {
    if (existing.qrCode && existing.qrCode.trim().toUpperCase() !== code.value) {
      replacementVehicle.value = existing
      return
    }
    bindVehicle(existing)
    return
  }
  pendingPlate.value = plate
  registerVehicleOpen.value = true
}

function confirmReplacement() {
  const vehicle = db.value.vehicles.find(vehicle => vehicle.id === replacementVehicle.value?.id)
  replacementVehicle.value = null
  if (!vehicle || normalizePlate(vehicle.plate) !== normalizePlate(searchVehicle.value)) return
  bindVehicle(vehicle)
}

onMounted(() => {
  generateQrPreview()
  watch(assignedVehicle, (v) => { if (v) router.replace(`/ficha/${v.id}`) }, { immediate: true })
})
</script>

<template>
  <div class="public-page qr-resolution-page">
    <div class="qr-public-header">
    <NuxtLink to="/" class="brand">
      <CommonOctanoLogo />
      <span>octa<span class="brand-light">no</span></span>
    </NuxtLink>
    <CommonThemeToggle v-model="isDark" />
    </div>

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

      <form class="selection-section" @submit.prevent="handleAssign">
        <label>
          <strong>Patente del vehículo</strong>
          <div class="search-box" style="margin-top: 8px">
            <Search :size="16" />
            <input
              v-model="searchVehicle"
              placeholder="Ej.: AC 284 FN"
              aria-label="Patente del vehículo"
              autocomplete="off"
              required
              maxlength="10"
              autocapitalize="characters"
            />
          </div>
        </label>

        <p class="muted search-hint">Escribí la patente completa. Si el vehículo no está registrado, podrás completar sus datos para vincular este QR.</p>

        <button
          class="button primary"
          style="width: 100%; margin-top: 1.5rem"
          :disabled="!searchVehicle.trim()"
          type="submit"
        >
          <CheckCircle2 :size="17" />
          Asignar código {{ code }}
        </button>
        <p v-if="assignmentError" role="alert" class="muted search-hint">{{ assignmentError }}</p>
      </form>
    </div>
    <VehiculosModalFormularioVehiculo
      :open="registerVehicleOpen"
      :initial-plate="pendingPlate"
      @close="registerVehicleOpen = false"
      @created="bindVehicle"
    />
    <CommonModalDialog v-if="replacementVehicle" class="dialog" aria-labelledby="replace-qr-title" @close="replacementVehicle = null">
      <div class="dialog-header"><h2 id="replace-qr-title">El vehículo ya tiene un QR</h2><button type="button" class="icon-button" aria-label="Cerrar" @click="replacementVehicle = null"><X :size="18" /></button></div>
      <div class="detail-body">
        <p>La patente <strong>{{ replacementVehicle.plate }}</strong> ya tiene asignado el código <strong>{{ replacementVehicle.qrCode }}</strong>.</p>
        <p>¿Querés reemplazarlo por <strong>{{ code }}</strong>?</p>
        <p class="muted">El código anterior quedará disponible y este QR abrirá la ficha del vehículo.</p>
      </div>
      <footer class="modal-footer"><button type="button" class="button" @click="replacementVehicle = null">Cancelar</button><button type="button" class="button primary" @click="confirmReplacement">Asignar este QR</button></footer>
    </CommonModalDialog>
  </div>
</template>

<style scoped>
.qr-public-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 24px; }
.selection-section input { text-transform: uppercase; }
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
:global(html.dark .divider) {
  border-top-color: rgba(255, 255, 255, 0.08);
}

</style>
