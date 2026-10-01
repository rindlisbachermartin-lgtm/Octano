<script setup lang="ts">
import {
  Gauge,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  AlertCircle,
  ShieldCheck,
} from 'lucide-vue-next'
import IconBidonAceite from '~/components/icons/IconBidonAceite.vue'
import IconCorreaDistribucion from '~/components/icons/IconCorreaDistribucion.vue'
import type { ServiceRecord, TimingBeltRecord } from '~/types'

definePageMeta({
  layout: 'blank'
})

const route = useRoute()
const { db, vehicle, vehicleName } = useDatabase()

const isEmbedded = computed(() => route.query.embedded === '1')
const vehicleId = computed(() => Number(route.params.id))
const currentVehicle = computed(() => vehicle(vehicleId.value))

// Detail toggle states
const showServiceDetail = ref(false)
const showTimingDetail = ref(false)

// Completed orders for this vehicle sorted from newest to oldest
const finishedOrders = computed(() =>
  db.value.orders
    .filter((o) => o.vehicle === vehicleId.value && o.status === 'Finalizado')
    .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())
)

// 1. Resolve Latest Service (from finished orders or vehicle seed)
const latestService = computed<ServiceRecord | null>(() => {
  const serviceOrder = finishedOrders.value.find((o) => {
    const types = o.serviceTypes || []
    return (
      types.some((t) => t.toLowerCase().includes('service')) ||
      o.service.toLowerCase().includes('service') ||
      o.service.toLowerCase().includes('aceite')
    )
  })

  if (serviceOrder) {
    const filterParts = (serviceOrder.parts || [])
      .filter((p) => p.name.toLowerCase().includes('filtro'))
      .map((p) => p.name)
    const oilPart = (serviceOrder.parts || []).find((p) =>
      p.name.toLowerCase().includes('aceite')
    )

    const resolvedFilters = (serviceOrder.replacedFilters && serviceOrder.replacedFilters.length > 0)
      ? serviceOrder.replacedFilters
      : (filterParts.length ? filterParts : ['Filtro de aceite', 'Filtro de aire'])

    return {
      date: serviceOrder.date,
      km: serviceOrder.km || currentVehicle.value?.km || 0,
      oil: serviceOrder.oilSpec || oilPart?.name || 'Aceite sintético 5W-30',
      filters: resolvedFilters,
      notes: serviceOrder.notes || serviceOrder.service,
    }
  }

  return currentVehicle.value?.lastService || null
})

// Calculate next service interval (+10.000 km)
const nextServiceKm = computed(() => {
  if (!latestService.value?.km) return null
  return latestService.value.km + 10000
})

// 2. Resolve Latest Timing Belt (from finished orders or vehicle seed)
const latestTimingBelt = computed<TimingBeltRecord | null>(() => {
  const timingOrder = finishedOrders.value.find((o) => {
    const types = o.serviceTypes || []
    return (
      types.some((t) => t.toLowerCase().includes('distribuci')) ||
      o.service.toLowerCase().includes('distribuci') ||
      o.service.toLowerCase().includes('correa')
    )
  })

  if (timingOrder) {
    const timingParts = (timingOrder.parts || [])
      .filter(
        (p) =>
          p.name.toLowerCase().includes('distribuci') ||
          p.name.toLowerCase().includes('correa') ||
          p.name.toLowerCase().includes('bomba') ||
          p.name.toLowerCase().includes('tensor')
      )
      .map((p) => p.name)

    return {
      date: timingOrder.date,
      km: timingOrder.km || currentVehicle.value?.km || 0,
      parts: timingParts.length
        ? timingParts
        : ['Kit de distribución', 'Bomba de agua'],
      notes: timingOrder.notes || timingOrder.service,
    }
  }

  return currentVehicle.value?.lastTimingBelt || null
})

const nextTimingBeltKm = computed(() => {
  if (!latestTimingBelt.value?.km) return null
  return latestTimingBelt.value.km + 60000
})
</script>

<template>
  <div class="public-page qr-sheet-page" :class="{ 'is-embedded': isEmbedded }">
    <!-- Top workshop brand (hidden if embedded inside smartphone mockup) -->
    <div v-if="!isEmbedded" class="sheet-top-bar">
      <NuxtLink to="/" class="brand">
        <span class="brand-symbol">o<span>·</span></span>
        <span>octa<span class="brand-light">no</span></span>
      </NuxtLink>
    </div>

    <!-- TARJETA ÚNICA DE VEHÍCULO Y SERVICIOS -->
    <template v-if="currentVehicle?.id">
      <div class="vehicle-single-card">
        <!-- Encabezado de la tarjeta -->
        <div class="card-header">
          <div class="header-eyebrow-row">
            <span class="eyebrow-badge">
              <ShieldCheck :size="13" /> FICHA DIGITAL DE MANTENIMIENTO
            </span>
            <span v-if="currentVehicle.qrCode" class="qr-code-pill">
              QR: {{ currentVehicle.qrCode }}
            </span>
          </div>

          <div class="vehicle-main-row">
            <div class="vehicle-info-block">
              <h1 class="vehicle-name">{{ vehicleName(vehicleId) }}</h1>
              <p class="vehicle-specs">
                <span>Año {{ currentVehicle.year }}</span>
                <span class="bullet">·</span>
                <span>Motor {{ currentVehicle.engine }}</span>
              </p>
            </div>
            <div class="plate-box">
              <span class="plate large-plate">{{ currentVehicle.plate }}</span>
            </div>
          </div>

          <!-- Odómetro actual registrado -->
          <div class="odometer-strip">
            <Gauge :size="15" class="odometer-icon" />
            <span class="odometer-label">Kilometraje actual registrado:</span>
            <strong class="odometer-value">{{ currentVehicle.km?.toLocaleString('es-AR') }} km</strong>
          </div>
        </div>

        <hr class="card-divider" />

        <!-- BLOQUE 1: ÚLTIMO SERVICIO -->
        <div class="maintenance-block service-block">
          <div class="maintenance-main-row">
            <div class="maintenance-left">
              <div class="status-indicator blue-tag">
                <IconBidonAceite :size="16" />
                <span>ÚLTIMO SERVICIO</span>
              </div>
              <div class="maintenance-summary">
                <div class="summary-km-line">
                  <span v-if="latestService?.km" class="km-text">
                    A los <strong>{{ latestService.km.toLocaleString('es-AR') }} km</strong>
                  </span>
                  <span v-else class="empty-km-text">
                    Sin registro de service
                  </span>
                </div>
                <span v-if="latestService?.date" class="date-text">
                  Realizado el {{ latestService.date }}
                </span>
              </div>
            </div>

            <!-- Botón Ver detalle al lado del servicio -->
            <button
              v-if="latestService"
              type="button"
              class="btn-toggle-detail"
              :class="{ 'is-open': showServiceDetail }"
              @click="showServiceDetail = !showServiceDetail"
            >
              <span>{{ showServiceDetail ? 'Ocultar detalle' : 'Ver detalle' }}</span>
              <ChevronUp v-if="showServiceDetail" :size="15" />
              <ChevronDown v-else :size="15" />
            </button>
          </div>

          <!-- Panel de detalle expandible del servicio -->
          <Transition name="accordion">
            <div v-if="showServiceDetail && latestService" class="detail-accordion-panel blue-theme">
              <!-- Aceite utilizado -->
              <div class="detail-item highlight-box">
                <span class="detail-label">Aceite de motor utilizado:</span>
                <strong class="oil-value">{{ latestService.oil }}</strong>
                <small class="detail-hint">Especificación y viscosidad homologada por el fabricante</small>
              </div>

              <!-- Filtros reemplazados -->
              <div v-if="latestService.filters?.length" class="detail-item">
                <span class="detail-label">Filtros reemplazados:</span>
                <div class="chips-container">
                  <span v-for="f in latestService.filters" :key="f" class="chip-item">
                    <Check :size="13" /> {{ f }}
                  </span>
                </div>
              </div>

              <!-- Próximo service sugerido -->
              <div v-if="nextServiceKm" class="detail-item next-step-box">
                <span class="detail-label">Próximo service sugerido:</span>
                <div class="next-km-line">
                  <strong>A los {{ nextServiceKm.toLocaleString('es-AR') }} km</strong>
                  <span class="next-hint">O al cumplirse 1 año (+10.000 km)</span>
                </div>
              </div>

              <!-- Observaciones adicionales -->
              <div v-if="latestService.notes" class="detail-item notes-box">
                <span class="detail-label">Observaciones técnicas:</span>
                <p class="notes-text">{{ latestService.notes }}</p>
              </div>
            </div>
          </Transition>
        </div>

        <hr class="card-divider" />

        <!-- BLOQUE 2: ÚLTIMO CAMBIO DE DISTRIBUCIÓN -->
        <div class="maintenance-block timing-block">
          <div class="maintenance-main-row">
            <div class="maintenance-left">
              <div class="status-indicator amber-tag">
                <IconCorreaDistribucion :size="16" />
                <span>ÚLTIMO CAMBIO DE DISTRIBUCIÓN</span>
              </div>
              <div class="maintenance-summary">
                <div class="summary-km-line">
                  <span v-if="latestTimingBelt?.km" class="km-text">
                    A los <strong>{{ latestTimingBelt.km.toLocaleString('es-AR') }} km</strong>
                  </span>
                  <span v-else class="empty-km-text">
                    Sin registro en taller
                  </span>
                </div>
                <span v-if="latestTimingBelt?.date" class="date-text">
                  Realizado el {{ latestTimingBelt.date }}
                </span>
              </div>
            </div>

            <!-- Botón Ver detalle al lado de distribución -->
            <button
              v-if="latestTimingBelt"
              type="button"
              class="btn-toggle-detail"
              :class="{ 'is-open': showTimingDetail }"
              @click="showTimingDetail = !showTimingDetail"
            >
              <span>{{ showTimingDetail ? 'Ocultar detalle' : 'Ver detalle' }}</span>
              <ChevronUp v-if="showTimingDetail" :size="15" />
              <ChevronDown v-else :size="15" />
            </button>
          </div>

          <!-- Panel de detalle expandible de distribución -->
          <Transition name="accordion">
            <div v-if="showTimingDetail && latestTimingBelt" class="detail-accordion-panel amber-theme">
              <!-- Componentes y repuestos de distribución -->
              <div v-if="latestTimingBelt.parts?.length" class="detail-item">
                <span class="detail-label">Componentes sustituidos:</span>
                <div class="chips-container">
                  <span v-for="part in latestTimingBelt.parts" :key="part" class="chip-item amber-chip">
                    <Check :size="13" /> {{ part }}
                  </span>
                </div>
              </div>

              <!-- Próximo cambio de distribución -->
              <div v-if="nextTimingBeltKm" class="detail-item next-step-box amber-next">
                <span class="detail-label">Próximo cambio recomendado:</span>
                <div class="next-km-line">
                  <strong>A los {{ nextTimingBeltKm.toLocaleString('es-AR') }} km</strong>
                  <span class="next-hint">Intervalo preventivo (+60.000 km o 5 años)</span>
                </div>
              </div>

              <!-- Notas adicionales de distribución -->
              <div v-if="latestTimingBelt.notes" class="detail-item notes-box">
                <span class="detail-label">Observaciones técnicas:</span>
                <p class="notes-text">{{ latestTimingBelt.notes }}</p>
              </div>
            </div>
          </Transition>
        </div>

        <!-- Crédito sutil del software Octano -->
        <div class="card-system-footer">
          <span>Historial provisto por el sistema <strong>Octano</strong></span>
        </div>
      </div>
    </template>

    <!-- Error / Vehículo no encontrado -->
    <div v-else class="panel empty-state" style="max-width: 480px; margin: 40px auto; text-align: center;">
      <AlertCircle :size="48" style="color: #94a3b8; margin: 0 auto 16px;" />
      <h2>Vehículo no encontrado</h2>
      <p class="muted">No existe ningún vehículo registrado con este identificador.</p>
      <NuxtLink to="/" class="button primary" style="margin-top: 1rem;">
        Ir al taller
      </NuxtLink>
    </div>
  </div>
</template>

<style scoped>
.qr-sheet-page {
  max-width: 580px;
  margin: 0 auto;
  padding: 30px 18px 60px;
  font-family: inherit;
}

.qr-sheet-page.is-embedded {
  padding: 12px 10px 24px;
}

.sheet-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.sheet-top-bar > .brand {
  margin-bottom: 0;
  padding: 0;
}

/* TARJETA ÚNICA DE VEHÍCULO */
.vehicle-single-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  padding: 22px 20px;
  box-shadow: 0 4px 16px -2px rgba(15, 23, 42, 0.06);
  transition: all 0.2s ease;
}

/* Encabezado */
.card-header {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.header-eyebrow-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.eyebrow-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 10.5px;
  font-weight: 800;
  letter-spacing: 0.6px;
  color: #2563eb;
  background: #eff6ff;
  padding: 3px 8px;
  border-radius: 6px;
  text-transform: uppercase;
}

.qr-code-pill {
  font-size: 11px;
  font-family: monospace;
  font-weight: 700;
  color: #0f172a;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  padding: 2px 7px;
  border-radius: 5px;
}

.vehicle-main-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
}

.vehicle-info-block {
  flex: 1;
  min-width: 180px;
}

.vehicle-name {
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
  margin: 0 0 4px 0;
  letter-spacing: -0.4px;
  line-height: 1.2;
}

.vehicle-specs {
  font-size: 13px;
  color: #64748b;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.bullet {
  color: #cbd5e1;
}

.plate-box {
  flex-shrink: 0;
}

/* Odómetro registrado */
.odometer-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 8px 12px;
  font-size: 12.5px;
  color: #475569;
}

.odometer-icon {
  color: #2563eb;
  flex-shrink: 0;
}

.odometer-value {
  color: #0f172a;
  font-weight: 750;
  margin-left: auto;
}

/* Divisor */
.card-divider {
  border: 0;
  border-top: 1px solid #f1f5f9;
  margin: 18px 0;
}

/* BLOQUE DE MANTENIMIENTO */
.maintenance-block {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.maintenance-main-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.maintenance-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.status-indicator {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.status-indicator.blue-tag {
  color: #2563eb;
}

.status-indicator.amber-tag {
  color: #71717a;
}

.maintenance-summary {
  display: flex;
  flex-direction: column;
}

.summary-km-line .km-text {
  font-size: 16px;
  color: #0f172a;
}

.summary-km-line .km-text strong {
  font-weight: 800;
}

.empty-km-text {
  font-size: 14px;
  color: #94a3b8;
  font-style: italic;
}

.date-text {
  font-size: 11.5px;
  color: #64748b;
}

/* Botón Ver Detalle */
.btn-toggle-detail {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 700;
  color: #2563eb;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
  flex-shrink: 0;
}

.btn-toggle-detail:hover {
  background: #dbeafe;
  border-color: #93c5fd;
  color: #1d4ed8;
}

.btn-toggle-detail.is-open {
  background: #2563eb;
  color: #ffffff;
  border-color: #2563eb;
}

/* Panel desplegable de detalle */
.detail-accordion-panel {
  border-radius: 12px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-size: 12.5px;
  margin-top: 4px;
}

.detail-accordion-panel.blue-theme {
  background: #f0f7ff;
  border: 1px solid #bae6fd;
}

.detail-accordion-panel.amber-theme {
  background: #f8f8fa;
  border: 1px solid #e4e4e7;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-label {
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.highlight-box {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
}

.oil-value {
  font-size: 15px;
  color: #0f172a;
  font-weight: 800;
}

.detail-hint {
  font-size: 11px;
  color: #64748b;
}

/* Chips de filtros y partes */
.chips-container {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 2px;
}

.chip-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: #ffffff;
  border: 1px solid #bfdbfe;
  color: #1e40af;
  font-size: 11.5px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 6px;
}

.chip-item.amber-chip {
  border-color: #d4d4d8;
  color: #52525b;
}

/* Próximo paso / km */
.next-step-box {
  background: rgba(255, 255, 255, 0.7);
  border-radius: 8px;
  padding: 8px 12px;
}

.next-km-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.next-km-line strong {
  font-size: 13.5px;
  color: #15803d;
}

.next-hint {
  font-size: 11px;
  color: #64748b;
}

.amber-next strong {
  color: #52525b;
}

:global(html.dark) .amber-next strong,
:global(html.dark) .status-indicator.amber-tag {
  color: #d1d1d6;
}

.notes-text {
  margin: 0;
  font-size: 12px;
  color: #334155;
  line-height: 1.45;
}

/* Acordeón Transition */
.accordion-enter-active,
.accordion-leave-active {
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
  overflow: hidden;
}

.accordion-enter-from,
.accordion-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* Pie de tarjeta */
.card-footer-action {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.footer-left {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.workshop-title {
  font-size: 13px;
  font-weight: 750;
  color: #0f172a;
}

.workshop-desc {
  font-size: 11.5px;
  color: #64748b;
}

.btn-whatsapp {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #16a34a;
  border-color: #16a34a;
  color: #ffffff;
  font-size: 12.5px;
  font-weight: 700;
  padding: 8px 14px;
  border-radius: 8px;
  text-decoration: none;
}

.btn-whatsapp:hover {
  background: #15803d;
  border-color: #15803d;
}

.card-system-footer {
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px dashed #e2e8f0;
  text-align: center;
  font-size: 10.5px;
  color: #94a3b8;
}

:global(html.dark .card-system-footer) {
  border-color: #334155;
  color: #64748b;
}

/* Dark Mode Support */
:global(html.dark .vehicle-single-card) {
  background: #1e293b;
  border-color: #334155;
  box-shadow: 0 4px 16px -2px rgba(0, 0, 0, 0.3);
}

:global(html.dark .vehicle-name) {
  color: #f8fafc;
}

:global(html.dark .vehicle-specs) {
  color: #94a3b8;
}

:global(html.dark .odometer-strip) {
  background: #0f172a;
  border-color: #334155;
  color: #cbd5e1;
}

:global(html.dark .odometer-value) {
  color: #f8fafc;
}

:global(html.dark .card-divider) {
  border-top-color: #334155;
}

:global(html.dark .summary-km-line .km-text) {
  color: #f8fafc;
}

:global(html.dark .detail-accordion-panel.blue-theme) {
  background: #0f172a;
  border-color: #1e40af;
}

:global(html.dark .detail-accordion-panel.amber-theme) {
  background: #0f172a;
  border-color: #3f3f46;
}

:global(html.dark .highlight-box) {
  background: #1e293b;
  border-color: #334155;
}

:global(html.dark .oil-value) {
  color: #f8fafc;
}

:global(html.dark .chip-item) {
  background: #1e293b;
  border-color: #1d4ed8;
  color: #93c5fd;
}

:global(html.dark .chip-item.amber-chip) {
  background: #1e293b;
  border-color: #52525b;
  color: #d1d1d6;
}

:global(html.dark .next-step-box) {
  background: #1e293b;
}

:global(html.dark .notes-text) {
  color: #cbd5e1;
}
</style>
