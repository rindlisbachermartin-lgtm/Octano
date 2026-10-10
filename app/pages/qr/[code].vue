<script setup lang="ts">
import { QrCode, CheckCircle2, ArrowRight } from 'lucide-vue-next'

definePageMeta({ layout: 'blank' })

const route = useRoute()
const router = useRouter()
const { db } = useDatabase()
const { isDark } = useTheme()

const code = computed(() => String(route.params.code || '').trim().toUpperCase())
const qrRecord = computed(() => db.value.qrCodes.find(qr => qr.code.trim().toUpperCase() === code.value))
const assignedVehicle = computed(() => {
  const qr = qrRecord.value
  if (!qr || qr.status !== 'asignado') return undefined
  return db.value.vehicles.find(vehicle => vehicle.id === qr.vehicleId && vehicle.qrCode?.trim().toUpperCase() === code.value)
})

onMounted(() => {
  watch(assignedVehicle, vehicle => {
    if (vehicle) router.replace(`/ficha/${vehicle.id}`)
  }, { immediate: true })
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

    <div v-if="assignedVehicle" class="panel qr-card-container">
      <CheckCircle2 :size="48" class="qr-status-icon success" />
      <h2>Código QR asignado</h2>
      <p class="muted">Este código está vinculado al vehículo <strong>{{ assignedVehicle.brand }} {{ assignedVehicle.model }}</strong>.</p>
      <div class="assigned-box">
        <span class="plate large-plate">{{ assignedVehicle.plate }}</span>
        <span class="qr-code-pill">{{ code }}</span>
      </div>
      <NuxtLink :to="`/ficha/${assignedVehicle.id}`" class="button primary vehicle-link">
        Ir a la ficha técnica del vehículo <ArrowRight :size="16" />
      </NuxtLink>
    </div>

    <div v-else class="panel qr-card-container">
      <QrCode :size="48" class="qr-status-icon" />
      <h2>{{ qrRecord ? 'QR pendiente de asignación' : 'Código QR no registrado' }}</h2>
      <p v-if="qrRecord" class="muted">El taller debe vincular este QR a un vehículo desde administración. Una vez asignado, podrás consultar su ficha digital.</p>
      <p v-else class="muted">Este código QR no fue creado por el taller. Consultá con administración para verificar la etiqueta.</p>
      <span class="qr-code-pill">{{ code }}</span>
    </div>
  </div>
</template>

<style scoped>
.qr-public-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 24px; }
.qr-resolution-page { max-width: 580px; margin: 0 auto; padding: 30px 18px; }
.qr-card-container { padding: 28px; border-radius: 16px; background: #fff; border: 1px solid #e2e8f0; box-shadow: 0 4px 18px rgba(15, 23, 42, 0.05); }
.qr-status-icon { margin-bottom: 12px; }
.qr-status-icon.success { color: #10b981; }
.assigned-box { display: flex; align-items: center; justify-content: center; gap: 16px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; padding: 16px; margin: 16px 0; }
.large-plate { font-size: 16px; padding: 6px 14px; }
.qr-code-pill { display: inline-block; font-family: monospace; font-size: 14px; font-weight: 700; letter-spacing: 1px; color: #1e293b; background: #e2e8f0; padding: 5px 10px; border-radius: 6px; }
.vehicle-link { width: 100%; margin-top: 1rem; }
:global(html.dark .qr-card-container) { background: #252528; border-color: rgba(255, 255, 255, 0.08); box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5); }
:global(html.dark .assigned-box) { background: rgba(255, 255, 255, 0.04); border-color: rgba(255, 255, 255, 0.08); }
:global(html.dark .qr-code-pill) { background: rgba(255, 255, 255, 0.08); color: #fff; }
</style>
