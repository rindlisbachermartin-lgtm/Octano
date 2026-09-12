<script setup lang="ts">
import {
  Droplets,
  Calendar,
  Gauge,
  Wrench,
  CheckCircle2,
  AlertCircle,
  Car,
  Layers,
  ShieldCheck,
  Clock,
  FileText,
  Check
} from 'lucide-vue-next'
import type { Order, Vehicle, ServiceRecord, TimingBeltRecord } from '~/types'

definePageMeta({
  layout: 'blank'
})

const route = useRoute()
const { db, vehicle, vehicleName, client } = useDatabase()

const vehicleId = computed(() => Number(route.params.id))
const currentVehicle = computed(() => vehicle(vehicleId.value))
const vehicleOwner = computed(() =>
  currentVehicle.value?.client ? client(currentVehicle.value.client) : null
)

// Completed orders for this vehicle sorted from newest to oldest
const finishedOrders = computed(() =>
  db.value.orders
    .filter((o) => o.vehicle === vehicleId.value && o.status === 'Finalizado')
    .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())
)

// Active filter for repair history
const selectedRepairFilter = ref('Todos')

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

const isServiceDueSoon = computed(() => {
  if (!currentVehicle.value?.km || !nextServiceKm.value) return false
  return currentVehicle.value.km >= nextServiceKm.value - 1500
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

// 3. Repair categories & filtering
const availableCategories = computed(() => {
  const cats = new Set<string>()
  cats.add('Todos')
  finishedOrders.value.forEach((o) => {
    if (o.serviceTypes && o.serviceTypes.length) {
      o.serviceTypes.forEach((t) => cats.add(t))
    } else {
      cats.add('General')
    }
  })
  return Array.from(cats)
})

const filteredRepairs = computed(() => {
  if (selectedRepairFilter.value === 'Todos') {
    return finishedOrders.value
  }
  return finishedOrders.value.filter((o) => {
    if (o.serviceTypes && o.serviceTypes.length) {
      return o.serviceTypes.includes(selectedRepairFilter.value)
    }
    return selectedRepairFilter.value === 'General'
  })
})
</script>

<template>
  <div class="public-page qr-sheet-page">
    <div class="sheet-top-bar">
      <NuxtLink to="/" class="brand">
        <span class="brand-symbol">o<span>·</span></span>
        <span>octa<span class="brand-light">no</span></span>
      </NuxtLink>
    </div>

    <template v-if="currentVehicle?.id">
      <!-- Header del Vehículo -->
      <header class="vehicle-sheet-header">
        <div class="header-eyebrow-row">
          <span class="sheet-badge">FICHA DIGITAL DE MANTENIMIENTO</span>
          <span v-if="currentVehicle.qrCode" class="qr-pill">
            QR: {{ currentVehicle.qrCode }}
          </span>
        </div>

        <div class="header-main-row">
          <div>
            <h1 class="vehicle-title">{{ vehicleName(vehicleId) }}</h1>
            <p class="vehicle-specs-line">
              <span>Año {{ currentVehicle.year }}</span>
              <span class="spec-bullet">·</span>
              <span>Motor {{ currentVehicle.engine }}</span>
              <span v-if="vehicleOwner" class="spec-bullet">·</span>
              <span v-if="vehicleOwner">Titular: {{ vehicleOwner.name }}</span>
            </p>
          </div>
          <div class="header-plate-box">
            <span class="plate large-plate">{{ currentVehicle.plate }}</span>
          </div>
        </div>

        <!-- Barra de métricas actuales -->
        <div class="vehicle-metrics-strip">
          <div class="metric-block">
            <span class="metric-label">Kilometraje actual</span>
            <strong class="metric-value">
              {{ currentVehicle.km?.toLocaleString('es-AR') }} km
            </strong>
          </div>
          <div class="metric-block">
            <span class="metric-label">Historial de intervenciones</span>
            <strong class="metric-value">
              {{ finishedOrders.length }} servicios realizados
            </strong>
          </div>
          <div class="metric-block">
            <span class="metric-label">Estado general</span>
            <span class="metric-status-badge">
              <ShieldCheck :size="14" /> Garantía de taller activa
            </span>
          </div>
        </div>
      </header>

      <!-- SECCIÓN 1: ÚLTIMO SERVICE DE MANTENIMIENTO -->
      <section class="sheet-card featured-service-card">
        <div class="card-header-row">
          <div class="card-title-group">
            <div class="card-icon-bubble blue">
              <Droplets :size="20" />
            </div>
            <div>
              <h2>Último Service de Mantenimiento</h2>
              <span class="card-subtitle">
                Cambio de aceite y sustitución de filtros
              </span>
            </div>
          </div>
          <div>
            <span
              v-if="latestService"
              :class="['status-chip', isServiceDueSoon ? 'chip-amber' : 'chip-green']"
            >
              <CheckCircle2 v-if="!isServiceDueSoon" :size="13" />
              <AlertCircle v-else :size="13" />
              {{ isServiceDueSoon ? 'Service recomendado pronto' : 'Service al día' }}
            </span>
            <span v-else class="status-chip chip-neutral">
              Sin registro en sistema
            </span>
          </div>
        </div>

        <div v-if="latestService" class="service-details-grid">
          <!-- Aceite Utilizado -->
          <div class="detail-cell highlight-cell">
            <span class="cell-label">Aceite de motor especificado</span>
            <strong class="cell-value-large">{{ latestService.oil }}</strong>
            <small class="cell-hint">Viscosidad y tipo homologado para este motor</small>
          </div>

          <!-- Fecha y Kilometraje -->
          <div class="detail-cell">
            <span class="cell-label">Fecha y kilometraje</span>
            <strong class="cell-value">
              {{ latestService.date }} · {{ latestService.km?.toLocaleString('es-AR') }} km
            </strong>
            <small class="cell-hint">Kilómetros registrados al momento del cambio</small>
          </div>

          <!-- Filtros Reemplazados -->
          <div class="detail-cell full-width">
            <span class="cell-label">Filtros reemplazados</span>
            <div class="chips-wrap">
              <span
                v-for="filter in latestService.filters"
                :key="filter"
                class="filter-tag"
              >
                <Check :size="12" /> {{ filter }}
              </span>
            </div>
          </div>

          <!-- Próximo Service -->
          <div v-if="nextServiceKm" class="detail-cell next-service-cell full-width">
            <div class="next-service-info">
              <span class="cell-label">Próximo service sugerido</span>
              <strong class="next-km-text">
                A los {{ nextServiceKm.toLocaleString('es-AR') }} km
              </strong>
              <small class="cell-hint">
                O al cumplirse 1 año desde el último cambio (+10.000 km).
              </small>
            </div>
            <div v-if="currentVehicle.km" class="km-countdown">
              <span class="countdown-label">Faltan aprox.</span>
              <strong class="countdown-value">
                {{ Math.max(0, nextServiceKm - currentVehicle.km).toLocaleString('es-AR') }} km
              </strong>
            </div>
          </div>
        </div>

        <div v-else class="empty-record-box">
          <AlertCircle :size="24" class="muted" />
          <p>
            No hay registros de service de aceite y filtros guardados para este vehículo.
          </p>
          <small>
            Al realizar y finalizar una orden de service se actualizarán automáticamente estos datos.
          </small>
        </div>
      </section>

      <!-- SECCIÓN 2: ÚLTIMO CAMBIO DE DISTRIBUCIÓN -->
      <section class="sheet-card featured-timing-card">
        <div class="card-header-row">
          <div class="card-title-group">
            <div class="card-icon-bubble amber">
              <Layers :size="20" />
            </div>
            <div>
              <h2>Último Cambio de Distribución</h2>
              <span class="card-subtitle">
                Kit de correa dentada, tensores y bomba de agua
              </span>
            </div>
          </div>
          <div>
            <span
              v-if="latestTimingBelt"
              class="status-chip chip-green"
            >
              <CheckCircle2 :size="13" /> Distribución vigente
            </span>
            <span v-else class="status-chip chip-neutral">
              Sin registro en taller
            </span>
          </div>
        </div>

        <div v-if="latestTimingBelt" class="service-details-grid">
          <div class="detail-cell">
            <span class="cell-label">Fecha del reemplazo</span>
            <strong class="cell-value">{{ latestTimingBelt.date }}</strong>
            <small class="cell-hint">Instalado en Octano Taller Central</small>
          </div>

          <div class="detail-cell">
            <span class="cell-label">Kilometraje al momento del cambio</span>
            <strong class="cell-value">
              {{ latestTimingBelt.km?.toLocaleString('es-AR') }} km
            </strong>
            <small class="cell-hint">Lectura registrada en el odómetro</small>
          </div>

          <!-- Componentes sustituidos -->
          <div class="detail-cell full-width">
            <span class="cell-label">Componentes y piezas sustituidas</span>
            <div class="chips-wrap">
              <span
                v-for="part in latestTimingBelt.parts"
                :key="part"
                class="filter-tag timing-part-tag"
              >
                <Check :size="12" /> {{ part }}
              </span>
            </div>
          </div>

          <!-- Próximo cambio de distribución -->
          <div v-if="nextTimingBeltKm" class="detail-cell next-timing-cell full-width">
            <div class="next-service-info">
              <span class="cell-label">Próximo cambio recomendado</span>
              <strong class="next-km-text">
                A los {{ nextTimingBeltKm.toLocaleString('es-AR') }} km
              </strong>
              <small class="cell-hint">
                Intervalo estimado de 60.000 km o 5 años para proteger las válvulas y el motor.
              </small>
            </div>
            <div v-if="currentVehicle.km" class="km-countdown">
              <span class="countdown-label">Vida útil remanente</span>
              <strong class="countdown-value">
                {{ Math.max(0, nextTimingBeltKm - currentVehicle.km).toLocaleString('es-AR') }} km
              </strong>
            </div>
          </div>
        </div>

        <div v-else class="empty-record-box">
          <AlertCircle :size="24" class="muted" />
          <p>
            No hay registros de cambio de distribución realizados en nuestras instalaciones.
          </p>
          <small>
            Se sugiere consultar el plan de mantenimiento de fábrica para prevenir cortes de correa.
          </small>
        </div>
      </section>

      <!-- SECCIÓN 3: APARTADO DE ARREGLOS Y REPARACIONES -->
      <section class="sheet-card repairs-history-section">
        <div class="repairs-header-block">
          <div class="card-title-group">
            <div class="card-icon-bubble neutral">
              <Wrench :size="20" />
            </div>
            <div>
              <h2>Historial de Arreglos y Reparaciones</h2>
              <span class="card-subtitle">
                Registro cronológico de trabajos técnicos efectuados en el vehículo
              </span>
            </div>
          </div>

          <!-- Filtros de Categorías de Arreglos -->
          <div v-if="availableCategories.length > 2" class="category-filters-row">
            <button
              v-for="cat in availableCategories"
              :key="cat"
              type="button"
              class="cat-filter-btn"
              :class="{ 'is-active': selectedRepairFilter === cat }"
              @click="selectedRepairFilter = cat"
            >
              {{ cat }}
            </button>
          </div>
        </div>

        <!-- Listado de Arreglos Realizados -->
        <div v-if="filteredRepairs.length" class="repairs-timeline">
          <article
            v-for="o in filteredRepairs"
            :key="o.id"
            class="repair-entry-card"
          >
            <div class="repair-top-meta">
              <div class="meta-left">
                <span class="repair-date">{{ o.date }}</span>
              </div>
              <span v-if="o.km" class="repair-km-badge">
                {{ o.km.toLocaleString('es-AR') }} km
              </span>
            </div>

            <h3 class="repair-service-title">{{ o.service }}</h3>

            <!-- Badges de Tipos de Trabajo -->
            <div v-if="o.serviceTypes && o.serviceTypes.length" class="repair-types-row">
              <span
                v-for="st in o.serviceTypes"
                :key="st"
                class="repair-type-chip"
              >
                {{ st }}
              </span>
            </div>



            <!-- Repuestos colocados en este arreglo -->
            <div v-if="o.parts && o.parts.length" class="repair-parts-block">
              <span class="mini-title">Repuestos e insumos colocados:</span>
              <div class="parts-chips-container">
                <span
                  v-for="part in o.parts"
                  :key="part.id || part.name"
                  class="repair-part-pill"
                >
                  {{ part.name }}
                </span>
              </div>
            </div>

            <!-- Observaciones del mecánico -->
            <div v-if="o.notes || o.diagnosis" class="repair-notes-block">
              <span class="mini-title">Observaciones técnicas del taller:</span>
              <p class="repair-notes-text">
                {{ o.notes || o.diagnosis }}
              </p>
            </div>
          </article>
        </div>

        <div v-else class="empty-record-box">
          <Wrench :size="24" class="muted" />
          <p>
            No se encontraron arreglos registrados
            <template v-if="selectedRepairFilter !== 'Todos'">
              para la categoría "{{ selectedRepairFilter }}"
            </template>
            .
          </p>
          <small>Los trabajos realizados y finalizados se registran aquí automáticamente.</small>
        </div>
      </section>

      <!-- Footer y Disclaimer -->
      <footer class="sheet-footer">
        <p class="disclaimer-text">
          Ficha técnica digital emitida por Octano Taller Central. La información corresponde a los servicios y mantenimientos realizados en nuestras instalaciones.
        </p>
      </footer>
    </template>

    <template v-else>
      <div class="panel not-found-panel">
        <AlertCircle :size="48" class="text-amber" />
        <h1>Ficha no disponible</h1>
        <p>Este vehículo no se encuentra registrado en el sistema del taller.</p>
        <NuxtLink to="/" class="button primary" style="margin-top: 1.5rem">
          Ir al taller
        </NuxtLink>
      </div>
    </template>
  </div>
</template>

<style scoped>
.qr-sheet-page {
  max-width: 740px;
  margin: 0 auto;
  padding: 30px 20px 60px;
}

.sheet-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
}

.sheet-top-bar > .brand {
  margin-bottom: 0;
  padding: 0;
}

.btn-back {
  font-size: 12px;
}

/* Header del Vehículo */
.vehicle-sheet-header {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 22px 24px;
  margin-bottom: 22px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.03);
}

.header-eyebrow-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.sheet-badge {
  font-size: 10px;
  font-weight: 800;
  color: #2563eb;
  letter-spacing: 0.8px;
}

.qr-pill {
  font-size: 11px;
  font-weight: 700;
  color: #0f172a;
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 5px;
  font-family: monospace;
}

.header-main-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.vehicle-title {
  font-size: 28px;
  font-weight: 800;
  color: #0f172a;
  margin: 0 0 6px 0;
  letter-spacing: -0.5px;
}

.vehicle-specs-line {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.spec-bullet {
  color: #cbd5e1;
}

.header-plate-box {
  flex-shrink: 0;
}

.large-plate {
  font-size: 18px;
  padding: 6px 14px;
  border-width: 2px;
}

.vehicle-metrics-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid #f1f5f9;
}

.metric-block {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.metric-label {
  font-size: 11px;
  color: #64748b;
  font-weight: 600;
}

.metric-value {
  font-size: 15px;
  color: #0f172a;
  font-weight: 700;
}

.metric-status-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 600;
  color: #166534;
}

/* Tarjetas Destacadas */
.sheet-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 22px 24px;
  margin-bottom: 22px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.03);
}

.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.card-title-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-icon-bubble {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.card-icon-bubble.blue {
  background: #eff6ff;
  color: #2563eb;
}

.card-icon-bubble.amber {
  background: #fffbeb;
  color: #d97706;
}

.card-icon-bubble.neutral {
  background: #f1f5f9;
  color: #475569;
}

.card-title-group h2 {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
}

.card-subtitle {
  font-size: 12px;
  color: #64748b;
  display: block;
  margin-top: 2px;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 20px;
}

.chip-green {
  background: #dcfce7;
  color: #166534;
}

.chip-amber {
  background: #fef3c7;
  color: #92400e;
}

.chip-neutral {
  background: #f1f5f9;
  color: #64748b;
}

/* Grilla de Detalles */
.service-details-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.detail-cell {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-cell.full-width {
  grid-column: 1 / -1;
}

.detail-cell.highlight-cell {
  background: #f0f7ff;
  border-color: #bfdbfe;
}

.cell-label {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
}

.cell-value-large {
  font-size: 15px;
  font-weight: 800;
  color: #1e40af;
}

.cell-value {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.cell-hint {
  font-size: 10.5px;
  color: #64748b;
}

.chips-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.filter-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  font-weight: 600;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #334155;
  padding: 3px 8px;
  border-radius: 6px;
}

.timing-part-tag {
  background: #fffbeb;
  border-color: #fde68a;
  color: #92400e;
}

/* Tarjeta de Próximo Service */
.next-service-cell,
.next-timing-cell {
  background: #ffffff;
  border: 1px dashed #2563eb;
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  gap: 14px;
  flex-wrap: wrap;
}

.next-timing-cell {
  border-color: #d97706;
}

.next-service-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.next-km-text {
  font-size: 16px;
  font-weight: 800;
  color: #2563eb;
}

.next-timing-cell .next-km-text {
  color: #d97706;
}

.km-countdown {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  padding: 6px 12px;
  border-radius: 8px;
}

.next-timing-cell .km-countdown {
  background: #fffbeb;
  border-color: #fde68a;
}

.countdown-label {
  font-size: 9.5px;
  font-weight: 700;
  color: #64748b;
  text-transform: uppercase;
}

.countdown-value {
  font-size: 14px;
  font-weight: 800;
  color: #1e40af;
}

.next-timing-cell .countdown-value {
  color: #92400e;
}

.empty-record-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 6px;
  padding: 24px 16px;
  background: #f8fafc;
  border-radius: 10px;
  border: 1px dashed #cbd5e1;
}

.empty-record-box p {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #475569;
}

.empty-record-box small {
  font-size: 11px;
  color: #64748b;
}

/* SECCIÓN 3: HISTORIAL DE ARREGLOS */
.repairs-header-block {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 20px;
}

.category-filters-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.cat-filter-btn {
  font-size: 11px;
  font-weight: 600;
  padding: 5px 12px;
  border-radius: 6px;
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #475569;
  cursor: pointer;
  transition: all 0.12s ease;
}

.cat-filter-btn:hover {
  background: #f1f5f9;
}

.cat-filter-btn.is-active {
  background: #0f172a;
  border-color: #0f172a;
  color: #ffffff;
}

.repairs-timeline {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.repair-entry-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: border-color 0.15s ease;
}

.repair-entry-card:hover {
  border-color: #cbd5e1;
}

.repair-top-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.meta-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.repair-date {
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
}

.repair-id {
  font-size: 11px;
  color: #64748b;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  padding: 1px 6px;
  border-radius: 4px;
}

.repair-mechanic {
  font-size: 11px;
  color: #64748b;
}

.repair-km-badge {
  font-size: 11.5px;
  font-weight: 700;
  color: #1e40af;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  padding: 2px 8px;
  border-radius: 5px;
}

.repair-service-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
}

.repair-types-row {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.repair-type-chip {
  font-size: 10.5px;
  font-weight: 600;
  color: #2563eb;
  background: #eff6ff;
  border: 1px solid #dbeafe;
  padding: 2px 7px;
  border-radius: 4px;
}

.mini-title {
  font-size: 11px;
  font-weight: 700;
  color: #475569;
  display: block;
  margin-bottom: 4px;
}

.tasks-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tasks-list li {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #334155;
}

.task-check {
  color: #166534;
  flex-shrink: 0;
}

.parts-chips-container {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.repair-part-pill {
  font-size: 11px;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #334155;
  padding: 2px 8px;
  border-radius: 5px;
}

.repair-notes-block {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
}

.repair-notes-text {
  margin: 0;
  font-size: 12px;
  color: #334155;
  line-height: 1.5;
}

/* Footer de la Ficha */
.sheet-footer {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin-top: 30px;
}

.disclaimer-text {
  font-size: 11.5px;
  color: #64748b;
  max-width: 540px;
  line-height: 1.5;
  margin: 0;
}

.not-found-panel {
  text-align: center;
  padding: 60px 20px;
}

/* MODO OSCURO */
:global(html.dark) .vehicle-sheet-header,
:global(html.dark) .sheet-card {
  background: #1c1c1e;
  border-color: rgba(255, 255, 255, 0.1);
}

:global(html.dark) .vehicle-title {
  color: #ffffff;
}

:global(html.dark) .vehicle-specs-line {
  color: #a1a1a6;
}

:global(html.dark) .vehicle-metrics-strip {
  border-top-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark) .metric-label {
  color: #8e8e93;
}

:global(html.dark) .metric-value {
  color: #ffffff;
}

:global(html.dark) .card-title-group h2 {
  color: #ffffff;
}

:global(html.dark) .card-subtitle {
  color: #a1a1a6;
}

:global(html.dark) .detail-cell {
  background: #252528;
  border-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark) .detail-cell.highlight-cell {
  background: rgba(37, 99, 235, 0.15);
  border-color: rgba(37, 99, 235, 0.35);
}

:global(html.dark) .cell-value {
  color: #ffffff;
}

:global(html.dark) .cell-value-large {
  color: #64d2ff;
}

:global(html.dark) .filter-tag {
  background: #2c2c2e;
  border-color: rgba(255, 255, 255, 0.12);
  color: #ffffff;
}

:global(html.dark) .next-service-cell,
:global(html.dark) .next-timing-cell {
  background: #202023;
}

:global(html.dark) .repair-entry-card {
  background: #252528;
  border-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark) .repair-service-title {
  color: #ffffff;
}

:global(html.dark) .repair-date {
  color: #ffffff;
}

:global(html.dark) .cat-filter-btn {
  background: #2c2c2e;
  border-color: rgba(255, 255, 255, 0.12);
  color: #d1d5db;
}

:global(html.dark) .cat-filter-btn.is-active {
  background: #ffffff;
  color: #0f172a;
  border-color: #ffffff;
}

:global(html.dark) .repair-part-pill {
  background: #1c1c1e;
  border-color: rgba(255, 255, 255, 0.1);
  color: #d1d5db;
}

:global(html.dark) .repair-notes-block {
  background: #1c1c1e;
  border-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark) .repair-notes-text {
  color: #d1d5db;
}

:global(html.dark) .tasks-list li {
  color: #d1d5db;
}

:global(html.dark) .empty-record-box {
  background: #202023;
  border-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark) .empty-record-box p {
  color: #d1d5db;
}

@media (max-width: 600px) {
  .service-details-grid {
    grid-template-columns: 1fr;
  }
  .header-main-row {
    flex-direction: column;
  }
}
</style>
