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
import { orderWorkKinds } from '~/utils/orderWorkflow'

definePageMeta({
  layout: 'blank'
})

const route = useRoute()
const { isDark } = useTheme()
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
  const serviceOrder = finishedOrders.value.find((o) => orderWorkKinds(o).service)

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
  const timingOrder = finishedOrders.value.find((o) => orderWorkKinds(o).timing)

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
  <div class="sheet-background">
  <div class="public-page qr-sheet-page" :class="{ 'is-embedded': isEmbedded }">
    <!-- Top workshop brand (hidden if embedded inside smartphone mockup) -->
    <div v-if="!isEmbedded" class="sheet-top-bar">
      <NuxtLink to="/" class="brand">
        <CommonOctanoLogo />
        <span>octa<span class="brand-light">no</span></span>
      </NuxtLink>
      <CommonThemeToggle v-model="isDark" />
    </div>

    <!-- TARJETA ÚNICA DE VEHÍCULO Y SERVICIOS -->
    <template v-if="currentVehicle?.id">
      <div class="vehicle-single-card">
        <!-- Encabezado de la tarjeta -->
        <div class="card-header">
          <CommonOctanoLogo class="sheet-card-brand" />
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
            <span class="odometer-label">Último kilometraje registrado  :</span>
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
              :aria-expanded="showServiceDetail"
              aria-controls="service-details"
              @click="showServiceDetail = !showServiceDetail"
            >
              <span>{{ showServiceDetail ? 'Ocultar detalle' : 'Ver detalle' }}</span>
              <ChevronUp v-if="showServiceDetail" :size="15" />
              <ChevronDown v-else :size="15" />
            </button>
          </div>

          <!-- Panel de detalle expandible del servicio -->
          <Transition name="accordion">
            <div v-if="showServiceDetail && latestService" id="service-details" class="detail-accordion-panel blue-theme">
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
              :aria-expanded="showTimingDetail"
              aria-controls="timing-details"
              @click="showTimingDetail = !showTimingDetail"
            >
              <span>{{ showTimingDetail ? 'Ocultar detalle' : 'Ver detalle' }}</span>
              <ChevronUp v-if="showTimingDetail" :size="15" />
              <ChevronDown v-else :size="15" />
            </button>
          </div>

          <!-- Panel de detalle expandible de distribución -->
          <Transition name="accordion">
            <div v-if="showTimingDetail && latestTimingBelt" id="timing-details" class="detail-accordion-panel amber-theme">
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
  </div>
</template>

<style scoped>
.sheet-background { min-height: 100dvh; display: flex; align-items: center; justify-content: center; padding: 24px 0; background: #fff; }
:global(html:has(.sheet-background)) { overflow-y: auto; }
:global(html.dark .sheet-background) { background: #1c1c1e; }
.qr-sheet-page {
  --sheet-surface: #fff;
  --sheet-panel: #f8fafc;
  --sheet-ink: #0f172a;
  --sheet-muted: #64748b;
  --sheet-line: #e2e8f0;
  --sheet-logo: #9ca3af;
  --sheet-accent: #2563eb;
  max-width: 600px;
  width: 100%;
  min-width: 0;
  margin: 0 auto;
  padding: 0 20px;
  color: var(--sheet-ink);
}
:global(html.dark .qr-sheet-page) {
  --sheet-surface: #1c1c1e;
  --sheet-panel: #242426;
  --sheet-ink: #f5f5f7;
  --sheet-muted: #a1a1aa;
  --sheet-line: #ffffff14;
  --sheet-logo: #71717a;
  --sheet-accent: #60a5fa;
}
.qr-sheet-page.is-embedded { padding: 12px 10px 24px; }
.sheet-top-bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
.sheet-top-bar > .brand { margin: 0; padding: 0; }
.vehicle-single-card { position: relative; overflow: hidden; background: var(--sheet-surface); border: 1px solid var(--sheet-line); border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px #0000000a; }
.card-header { position: relative; display: flex; flex-direction: column; gap: 14px; }
.sheet-card-brand { position: absolute; top: 50%; right: -4px; width: 160px; height: 160px; transform: translate(50%, -50%); color: var(--sheet-logo); opacity: .35; pointer-events: none; }
.card-header > :not(.sheet-card-brand) { position: relative; z-index: 1; }
.header-eyebrow-row { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; }
.eyebrow-badge { display: inline-flex; align-items: center; gap: 6px; font-size: 9px; font-weight: 600; letter-spacing: .5px; color: var(--sheet-muted); }
.eyebrow-badge svg { flex-shrink: 0; color: var(--sheet-accent); }
.qr-code-pill { font-size: 10px; color: var(--sheet-muted); border: 1px solid var(--sheet-line); border-radius: 5px; padding: 3px 6px; }
.vehicle-main-row { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; flex-wrap: wrap; }
.vehicle-info-block { flex: 1; min-width: 180px; }
.vehicle-name { font-size: 22px; font-weight: 600; color: var(--sheet-ink); margin: 0 0 7px; letter-spacing: -.4px; line-height: 1.25; }
.vehicle-specs { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; font-size: 12px; color: var(--sheet-muted); margin: 0; }
.bullet { color: var(--sheet-muted); }
.plate-box { flex-shrink: 0; }
.plate-box .large-plate { font-size: 13px; background: var(--sheet-panel); border: 1px solid var(--sheet-line); color: var(--sheet-ink); padding: 6px 9px; border-radius: 6px; }
.odometer-strip { display: flex; align-items: center; gap: 8px; padding-top: 12px; border-top: 1px solid var(--sheet-line); font-size: 12px; color: var(--sheet-muted); flex-wrap: wrap; }
.odometer-icon { color: var(--sheet-accent); flex-shrink: 0; }
.odometer-value { margin-left: auto; color: var(--sheet-ink); font-weight: 600; }
.card-divider { border: 0; border-top: 1px solid var(--sheet-line); margin: 20px 0; }
.maintenance-block { display: flex; flex-direction: column; gap: 12px; }
.maintenance-main-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
.maintenance-left { display: flex; flex-direction: column; gap: 8px; flex: 1; min-width: 170px; }
.status-indicator { display: inline-flex; align-items: center; gap: 7px; font-size: 10px; font-weight: 600; letter-spacing: .4px; color: var(--sheet-muted); }
.status-indicator svg { flex-shrink: 0; }
.status-indicator.blue-tag svg { color: var(--sheet-accent); }
.maintenance-summary { display: flex; flex-direction: column; gap: 4px; }
.km-text { font-size: 15px; color: var(--sheet-ink); }
.km-text strong { font-weight: 600; }
.empty-km-text { font-size: 13px; color: var(--sheet-muted); }
.date-text { font-size: 11px; color: var(--sheet-muted); }
.btn-toggle-detail { display: inline-flex; align-items: center; gap: 6px; padding: 7px 10px; font-size: 11px; font-weight: 550; color: var(--sheet-accent); background: var(--sheet-panel); border: 1px solid var(--sheet-line); border-radius: 7px; flex-shrink: 0; transition: border-color 150ms ease; }
.btn-toggle-detail.is-open { border-color: var(--sheet-accent); }
.detail-accordion-panel { display: flex; flex-direction: column; gap: 14px; border: 1px solid var(--sheet-line); background: var(--sheet-panel); border-radius: 9px; padding: 14px; font-size: 12px; }
.detail-item { display: flex; flex-direction: column; gap: 5px; }
.detail-label { font-size: 10px; font-weight: 550; color: var(--sheet-muted); }
.highlight-box, .next-step-box { background: var(--sheet-surface); border: 1px solid var(--sheet-line); border-radius: 7px; padding: 10px 12px; }
.oil-value { font-size: 14px; color: var(--sheet-ink); font-weight: 600; }
.detail-hint, .next-hint { font-size: 10px; color: var(--sheet-muted); line-height: 1.6; }
.chips-container { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 2px; }
.chip-item { display: inline-flex; align-items: center; gap: 5px; background: var(--sheet-surface); border: 1px solid var(--sheet-line); color: var(--sheet-ink); font-size: 11px; padding: 4px 7px; border-radius: 6px; }
.chip-item svg { color: var(--sheet-accent); }
.next-km-line { display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; }
.next-km-line strong { font-size: 13px; font-weight: 600; color: var(--sheet-ink); }
.notes-text { margin: 0; font-size: 12px; color: var(--sheet-ink); line-height: 1.6; overflow-wrap: anywhere; }
.card-system-footer { margin-top: 20px; padding-top: 12px; border-top: 1px solid var(--sheet-line); text-align: center; font-size: 10px; color: var(--sheet-muted); }
.accordion-enter-active, .accordion-leave-active { transition: opacity 180ms ease-out, transform 180ms ease-out; }
.accordion-enter-from, .accordion-leave-to { opacity: 0; transform: translateY(-4px); }
@media (hover: hover) and (pointer: fine) { .btn-toggle-detail:hover { border-color: var(--sheet-accent); } }
@media (max-width: 400px) {
  .qr-sheet-page { padding: 0 16px; }
  .vehicle-single-card { padding: 18px; }
  .vehicle-name { font-size: 20px; }
  .odometer-label { font-size: 11px; }
}
@media (prefers-reduced-motion: reduce) { .accordion-enter-active, .accordion-leave-active, .btn-toggle-detail { transition: none; } .accordion-enter-from, .accordion-leave-to { transform: none; } }
:global(html[data-keyboard]) .accordion-enter-active, :global(html[data-keyboard]) .accordion-leave-active { transition: none; }
</style>
