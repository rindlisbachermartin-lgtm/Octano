<script setup lang="ts">
import {
  X,
  Check,
  Ban,
  Receipt,
  Camera,
  CarFront,
  User,
  Phone,
  Gauge,
  Calendar,
  Filter,
  Droplets,
} from 'lucide-vue-next'
import type { Order } from '~/types'

const props = defineProps<{
  orderId: number | null
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'updated'): void
}>()

const { db, vehicle, vehicleName, owner, recordOrderCompletion } = useDatabase()
const { money, statusClass } = useHelpers()
const { notify } = useToast()

useModalEscape(() => props.open, () => emit('close'))

const selectedOrder = computed(() =>
  db.value.orders.find((o) => o.id === props.orderId)
)

const currentVehicle = computed(() =>
  selectedOrder.value ? vehicle(selectedOrder.value.vehicle) : null
)

const currentOwner = computed(() =>
  selectedOrder.value ? owner(selectedOrder.value.vehicle) : null
)

// Administrative Action 1: Finalizar orden
function finishOrder() {
  if (!selectedOrder.value) return
  const o = selectedOrder.value
  o.status = 'Finalizado'
  o.bay = null

  recordOrderCompletion(o)

  // Register invoice automatically if not exists
  const invoiceTotal =
    75000 + o.parts.reduce((sum, p) => sum + (p.price || 0), 0)
  const invoiceId = Math.max(126, ...db.value.invoices.map((i) => i.id)) + 1
  db.value.invoices.unshift({
    id: invoiceId,
    vehicle: o.vehicle,
    description: o.service,
    total: invoiceTotal,
    type: 'B',
    status: 'Pendiente',
    date: new Date().toISOString().slice(0, 10),
  })

  // Notification for client
  db.value.notifications.unshift({
    id: Date.now(),
    title: `El ${vehicleName(o.vehicle)} está listo para retirar`,
    detail: `OT #${o.id} finalizada · Aviso al cliente generado`,
    read: false,
  })

  notify(`Orden #${o.id} finalizada. Factura y aviso al cliente generados.`)
  emit('updated')
  emit('close')
}

// Administrative Action 2: Dar de baja orden
function cancelOrder() {
  if (!selectedOrder.value) return
  const o = selectedOrder.value
  o.status = 'Cancelado'
  o.bay = null

  notify(`Orden de trabajo #${o.id} dada de baja.`)
  emit('updated')
  emit('close')
}
</script>

<template>
  <dialog v-if="open && selectedOrder" class="dialog detail-modal" open>
    <div class="dialog-header">
      <div>
        <span class="eyebrow">ADMINISTRACIÓN / ÓRDENES DE TRABAJO</span>
        <h2>Orden de Trabajo #{{ selectedOrder.id }}</h2>
      </div>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <!-- Header Summary -->
    <div class="detail-intro">
      <div>
        <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 6px;">
          <span :class="['badge', statusClass(selectedOrder.status)]">
            {{ selectedOrder.status }}
          </span>
          <span v-if="selectedOrder.bay" class="badge neutral">
            Puesto 0{{ selectedOrder.bay }}
          </span>
        </div>
        <h2>{{ vehicleName(selectedOrder.vehicle) }}</h2>
        <span class="plate">{{ currentVehicle?.plate }}</span>
        <p>
          {{ currentOwner?.name }}
          <template v-if="currentOwner?.phone"> · Tel. {{ currentOwner.phone }}</template>
        </p>
      </div>
    </div>

    <div class="detail-body">
      <!-- General Info Grid -->
      <div class="info-grid-panel">
        <div class="info-item">
          <span class="info-label"><Gauge :size="13" /> Kilometraje</span>
          <strong>
            {{ (selectedOrder.km || currentVehicle?.km)?.toLocaleString('es-AR') || 'No registrado' }} km
          </strong>
        </div>
        <div class="info-item">
          <span class="info-label"><Calendar :size="13" /> Fecha de ingreso</span>
          <strong>{{ selectedOrder.date }} ({{ selectedOrder.time }})</strong>
        </div>
        <div class="info-item">
          <span class="info-label">Mecánico asignado</span>
          <strong>{{ selectedOrder.mechanic || 'Sin asignar' }}</strong>
        </div>
        <div class="info-item">
          <span class="info-label">Ubicación en taller</span>
          <strong>{{ selectedOrder.bay ? `Puesto 0${selectedOrder.bay}` : 'Sin puesto' }}</strong>
        </div>
      </div>

      <!-- Service Requested -->
      <div class="detail-section">
        <h3>Trabajo / Servicio</h3>
        <p class="service-highlight">{{ selectedOrder.service }}</p>

        <!-- Detalle de Filtros y Aceite si es un service -->
        <div
          v-if="selectedOrder.replacedFilters?.length || selectedOrder.oilSpec"
          class="service-spec-panel"
        >
          <div v-if="selectedOrder.oilSpec" class="spec-row">
            <span class="spec-label"><Droplets :size="13" /> Aceite:</span>
            <strong>{{ selectedOrder.oilSpec }}</strong>
          </div>
          <div v-if="selectedOrder.replacedFilters?.length" class="spec-row">
            <span class="spec-label"><Filter :size="13" /> Filtros cambiados por mecánico:</span>
            <div class="filter-chips-list">
              <span
                v-for="filter in selectedOrder.replacedFilters"
                :key="filter"
                class="filter-chip-item"
              >
                <Check :size="11" /> {{ filter }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Mechanic Diagnosis & Observations -->
      <div v-if="selectedOrder.diagnosis || selectedOrder.notes" class="detail-section">
        <h3>Observaciones del taller</h3>
        <div v-if="selectedOrder.diagnosis" class="note-box">
          <span class="note-label">Diagnóstico:</span>
          <p>{{ selectedOrder.diagnosis }}</p>
        </div>
        <div v-if="selectedOrder.notes" class="note-box" style="margin-top: 8px;">
          <span class="note-label">Observaciones técnicas:</span>
          <p>{{ selectedOrder.notes }}</p>
        </div>
      </div>

      <!-- Repuestos Imputados -->
      <div class="detail-section">
        <h3>Repuestos e insumos imputados</h3>
        <div
          v-for="(part, index) in selectedOrder.parts"
          :key="index"
          class="quote-line"
        >
          <span>{{ part.name }}</span>
          <strong>{{ money(part.price) }}</strong>
        </div>
        <p v-if="!selectedOrder.parts.length" class="muted" style="font-size: 13px;">
          No se imputaron repuestos adicionales en esta orden.
        </p>
      </div>

      <!-- Photographic Survey -->
      <div class="detail-section">
        <div class="section-heading">
          <h3>Peritaje fotográfico (cargado por mecánico)</h3>
          <span class="muted">{{ selectedOrder.photos?.length || 0 }} fotos</span>
        </div>

        <div v-if="selectedOrder.photos && selectedOrder.photos.length" class="photo-grid">
          <figure v-for="(photo, index) in selectedOrder.photos" :key="index">
            <img :src="photo.data" :alt="photo.sector" />
            <figcaption>{{ photo.sector }}</figcaption>
          </figure>
        </div>
        <p v-else class="muted" style="font-size: 13px;">
          El mecánico aún no cargó fotografías para este vehículo.
        </p>
      </div>
    </div>

    <!-- Administrative Footer: SOLO Dar de baja o Finalizar -->
    <footer class="modal-footer">
      <button class="button" @click="emit('close')">Cerrar</button>

      <!-- Active Order Actions -->
      <template v-if="!['Finalizado', 'Cancelado'].includes(selectedOrder.status)">
        <button
          class="button outlined btn-cancel-order"
          title="Dar de baja y anular la orden de trabajo"
          @click="cancelOrder"
        >
          <Ban :size="15" /> Dar de baja
        </button>

        <button
          class="button primary btn-finish-order"
          title="Finalizar orden y emitir aviso / factura"
          @click="finishOrder"
        >
          <Check :size="16" /> Finalizar orden
        </button>
      </template>

      <!-- If already Finished -->
      <NuxtLink
        v-else-if="selectedOrder.status === 'Finalizado'"
        to="/facturacion"
        class="button primary"
        @click="emit('close')"
      >
        <Receipt :size="16" /> Ver facturación
      </NuxtLink>

      <!-- If Cancelled -->
      <span v-else-if="selectedOrder.status === 'Cancelado'" class="badge neutral" style="padding: 6px 12px;">
        Orden cancelada / dada de baja
      </span>
    </footer>
  </dialog>
</template>

<style scoped>
.info-grid-panel {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 16px;
}

:global(html.dark) .info-grid-panel {
  background: #1c1c1e;
  border-color: rgba(255, 255, 255, 0.08);
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.info-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: #64748b;
  font-weight: 600;
}

:global(html.dark) .info-label {
  color: #8e8e93;
}

.info-item strong {
  font-size: 13px;
  color: #1e293b;
}

:global(html.dark) .info-item strong {
  color: #f5f5f7;
}

.detail-section {
  margin-top: 18px;
  border-top: 1px solid #f1f5f9;
  padding-top: 14px;
}

:global(html.dark) .detail-section {
  border-top-color: rgba(255, 255, 255, 0.08);
}

.detail-section h3 {
  font-size: 14px;
  margin-bottom: 8px;
  color: #0f172a;
}

:global(html.dark) .detail-section h3 {
  color: #f5f5f7;
}

.service-highlight {
  font-size: 14px;
  font-weight: 600;
  color: #1e40af;
  background: #eff6ff;
  border: 1px solid #dbeafe;
  padding: 10px 12px;
  border-radius: 8px;
}

:global(html.dark) .service-highlight {
  background: rgba(10, 132, 255, 0.12);
  border-color: rgba(10, 132, 255, 0.25);
  color: #64d2ff;
}

.note-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
}

:global(html.dark) .note-box {
  background: #252528;
  border-color: rgba(255, 255, 255, 0.08);
}

.note-label {
  font-size: 11px;
  text-transform: uppercase;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: #64748b;
  display: block;
  margin-bottom: 4px;
}

:global(html.dark) .note-label {
  color: #8e8e93;
}

.note-box p {
  font-size: 13px;
  color: #334155;
  margin: 0;
  white-space: pre-line;
}

:global(html.dark) .note-box p {
  color: #d1d1d6;
}

.btn-cancel-order {
  border-color: #fca5a5;
  color: #dc2626;
}

.btn-cancel-order:hover {
  background: #fef2f2;
  border-color: #ef4444;
  color: #b91c1c;
}

:global(html.dark) .btn-cancel-order {
  border-color: rgba(255, 69, 58, 0.35);
  color: #ff453a;
}

:global(html.dark) .btn-cancel-order:hover {
  background: rgba(255, 69, 58, 0.15);
}

.btn-finish-order {
  background: #10b981;
  color: white;
}

.btn-finish-order:hover {
  background: #059669;
}

/* Service Specs Panel */
.service-spec-panel {
  margin-top: 10px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

:global(html.dark) .service-spec-panel {
  background: rgba(10, 132, 255, 0.08);
  border-color: rgba(10, 132, 255, 0.2);
}

.spec-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.spec-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: #1e40af;
}

:global(html.dark) .spec-label {
  color: #64d2ff;
}

.filter-chips-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 2px;
}

.filter-chip-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: #ffffff;
  border: 1px solid #93c5fd;
  color: #1e3a8a;
  font-size: 12px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 6px;
}

:global(html.dark) .filter-chip-item {
  background: #1c1c1e;
  border-color: rgba(10, 132, 255, 0.3);
  color: #93c5fd;
}
</style>
