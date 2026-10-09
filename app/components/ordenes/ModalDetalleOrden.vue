<script setup lang="ts">
import { X, Check, Receipt, Gauge, Calendar, Filter, Droplets, FileText, Pencil, Play } from 'lucide-vue-next'
import type { Invoice, Appointment } from '~/types'
import { canEditIssuedOrder, canEditOrderWork, canEditOrderDetails, orderDisplayParts, releaseUnstartedOrderParts, saveOrderCompletionData } from '~/utils/orderWorkflow'
import type { OrderCompletionData } from '~/utils/orderWorkflow'
import { billingEntryBudget } from '~/utils/billingEntries'

const props = defineProps<{
  orderId: number | null
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'edit', orderId: number): void
  (e: 'updated', orderId: number): void
}>()

const { db, vehicle, vehicleName, owner, recordOrderCompletion, activateAppointmentOrder, cancelAppointmentOrder } = useDatabase()
const { money, statusClass } = useHelpers()
const auth = useOwnerAccount()
const { notify } = useWorkshopToast()
const { today } = useWorkshopDay()
const completionOpen = ref(false)
const completionError = ref('')
const futureAction = ref<{ appointment: Appointment; action: 'cancelar' | 'ingreso' } | null>(null)
watch(() => [props.open, props.orderId], () => { futureAction.value = null; completionOpen.value = false; completionError.value = '' })
const arcaViewerOpen = ref(false)
const activeInvoiceForViewer = ref<Invoice | null>(null)

const selectedOrder = computed(() =>
  db.value.orders.find((o) => o.id === props.orderId && (!auth.isMechanic.value || o.mechanic === auth.mechanic.value))
)

const currentVehicle = computed(() =>
  selectedOrder.value ? vehicle(selectedOrder.value.vehicle) : null
)

const currentOwner = computed(() =>
  selectedOrder.value ? owner(selectedOrder.value.vehicle) : null
)

const orderTotalAmount = computed(() => {
  return orderInvoice.value?.total ?? null
})

const orderInvoice = computed(() => {
  if (!selectedOrder.value) return null
  return db.value.invoices.find(
    (i) => i.orderId === selectedOrder.value?.id || (i.vehicle === selectedOrder.value?.vehicle && i.description.includes(selectedOrder.value?.service || ''))
  ) || null
})

function openArcaViewer(inv: Invoice) {
  activeInvoiceForViewer.value = inv
  arcaViewerOpen.value = true
}

function changeStatus(action: 'iniciar' | 'finalizar' | 'cancelar' | 'ingreso', confirmed = false) {
  const order = selectedOrder.value
  if (!props.open || !order || !canEditOrderWork(order)) return
  if (auth.isMechanic.value && ['cancelar', 'ingreso'].includes(action)) return
  if (action === 'iniciar' && !['Nicolás', 'Santiago'].includes(order.mechanic)) {
    notify('Asigná un mecánico desde Editar orden antes de iniciar el trabajo.')
    return
  }
  if (action === 'finalizar') {
    if (order.status === 'En proceso') { completionError.value = ''; completionOpen.value = true }
    return
  }
  const appointment = db.value.appointments.find(appointment => appointment.id === order.appointmentId)
  if ((action === 'cancelar' || action === 'ingreso') && order.status === 'Pendiente de ingreso' && appointment && appointment.date > today.value && !confirmed) {
    futureAction.value = { appointment, action }; return
  }
  if (action === 'cancelar') {
    if (appointment && order.status === 'Pendiente de ingreso') cancelAppointmentOrder(appointment)
    releaseUnstartedOrderParts(db.value, order)
    order.status = 'Cancelado'; order.bay = null
  } else {
    if (action === 'ingreso') { if (!appointment || order.status !== 'Pendiente de ingreso') return; activateAppointmentOrder(appointment) }
    if (action === 'iniciar') { if (order.status !== 'En espera') return; order.status = 'En proceso'; order.startedAt = new Date().toISOString() }

  }
  notify(`Orden #${order.id}: ${order.status}.`)
  emit('updated', order.id)
}
function confirmCompletion(data: OrderCompletionData) {
  const order = selectedOrder.value
  if (!props.open || !completionOpen.value || !order) return
  completionError.value = saveOrderCompletionData(db.value, order, data)
  if (completionError.value) return
  order.status = 'Finalizado'
  order.bay = null
  recordOrderCompletion(order)
  db.value.notifications.unshift({ id: Date.now(), title: `El vehículo de la OT #${order.id} está listo para retirar`, detail: `OT #${order.id} finalizada por ${order.mechanic}`, read: false })
  completionOpen.value = false
  notify(`Orden #${order.id}: Finalizado.`)
  emit('updated', order.id)
}
function confirmFutureAction() {
  const action = futureAction.value?.action
  futureAction.value = null
  if (action) changeStatus(action, true)
}

const displayedParts = computed(() => selectedOrder.value
  ? orderDisplayParts(selectedOrder.value, billingEntryBudget(selectedOrder.value, db.value.quotes), db.value.parts) : [])
const laborAmount = computed(() => selectedOrder.value?.laborAmount ?? billingEntryBudget(selectedOrder.value, db.value.quotes)?.labor ?? 0)
</script>

<template>
  <CommonModalDialog v-if="open && selectedOrder && !arcaViewerOpen && !completionOpen" class="dialog detail-modal" @close="emit('close')">
    <div class="dialog-header">
      <div>
        <span class="eyebrow">CONSULTA / ÓRDENES DE TRABAJO</span>
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
      <p v-if="selectedOrder.status === 'Pendiente de ingreso'" class="muted">Orden emitida para un turno. El auto todavía no ingresó al taller.</p>
      <p v-if="selectedOrder.cancellationReason" class="muted">Motivo de baja: {{ selectedOrder.cancellationReason }}</p>
      <!-- General Info Grid -->
      <div class="info-grid-panel">
        <div class="info-item">
          <span class="info-label"><Gauge :size="13" /> Kilometraje</span>
          <strong>
            {{ (selectedOrder.km || currentVehicle?.km)?.toLocaleString('es-AR') || 'No registrado' }} km
          </strong>
        </div>
        <div class="info-item">
          <span class="info-label"><Calendar :size="13" /> {{ selectedOrder.status === 'Pendiente de ingreso' ? 'Ingreso planificado' : selectedOrder.cancellationReason ? 'Turno planificado' : 'Fecha de ingreso' }}</span>
          <strong>{{ selectedOrder.date }} ({{ selectedOrder.time }})</strong>
        </div>
        <div class="info-item">
          <span class="info-label"><Calendar :size="13" /> Fecha y hora de egreso</span>
          <strong>{{ selectedOrder.exitDate ? `${selectedOrder.exitDate} (${selectedOrder.exitTime || 'Hora no registrada'})` : selectedOrder.status === 'Finalizado' ? 'No registrado' : 'Pendiente de egreso' }}</strong>
        </div>
        <div class="info-item">
          <span class="info-label">Mecánico asignado</span>
          <strong>{{ selectedOrder.mechanic || 'Sin asignar' }}</strong>
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
      <div v-if="selectedOrder.notes || selectedOrder.mechanicNotes" class="detail-section">
        <h3>Observaciones</h3>
        <div v-if="selectedOrder.notes" class="note-box">
          <span class="note-label">Indicaciones del administrador para el mecánico:</span>
          <p>{{ selectedOrder.notes }}</p>
        </div>
        <div v-if="selectedOrder.mechanicNotes" class="note-box" style="margin-top: 8px;">
          <span class="note-label">Observaciones del mecánico:</span>
          <p>{{ selectedOrder.mechanicNotes }}</p>
        </div>
      </div>

      <!-- Repuestos Imputados -->
      <div class="detail-section">
        <OrdenesDetalleRepuestos :parts="displayedParts" :show-prices="!auth.isMechanic.value" />
      </div>
      <div v-if="!auth.isMechanic.value" class="detail-section">
        <h3>Mano de obra</h3>
        <strong>{{ money(laborAmount) }}</strong>
      </div>

      <!-- Photographic Survey -->
      <div class="detail-section">
        <div class="section-heading">
          <h3>Peritaje fotográfico (cargado por mecánico)</h3>
          <span class="muted">{{ selectedOrder.photos?.length || 0 }} fotos</span>
        </div>

        <OrdenesCargarFotosMecanico v-if="auth.isMechanic.value" :order="selectedOrder" />
        <OrdenesGaleriaFotos :order="selectedOrder" />
      </div>

      <!-- SECCIÓN DESTACADA: FACTURACIÓN Y COBRO (Para orden finalizada) -->
      <div v-if="selectedOrder.status === 'Finalizado' && !auth.isMechanic.value" class="detail-section billing-status-card">
        <div class="billing-card-header">
          <div class="billing-title-row">
            <div class="card-icon-tag">
              <Receipt :size="16" />
              <span>ESTADO DE COBRO Y FACTURACIÓN</span>
            </div>
            <span :class="['badge', orderInvoice?.status === 'Cobrada' ? 'green' : 'amber']">
              {{ orderInvoice?.status || 'Para armar' }} <template v-if="orderInvoice?.paymentMethod">({{ orderInvoice.paymentMethod }})</template>
            </span>
          </div>
          <strong v-if="orderTotalAmount != null" class="total-order-val">{{ money(orderTotalAmount) }}</strong>
          <span v-else class="muted">Importe a definir al armar la factura</span>
        </div>

        <div v-if="orderInvoice" class="billing-card-body">
          <div class="invoice-summary-line">
            <div class="inv-type-box">
              <span v-if="orderInvoice.isFiscal" class="badge green">
                Factura {{ orderInvoice.type }} Oficial ARCA (CAE: {{ orderInvoice.cae }})
              </span>
            </div>
            <small class="muted">Comprobante del {{ orderInvoice.date }}</small>
          </div>

          <div v-if="orderInvoice.isFiscal" class="billing-actions-row">
            <button
              v-if="orderInvoice.isFiscal"
              class="button small primary"
              @click="openArcaViewer(orderInvoice)"
            >
              <FileText :size="14" /> Ver / Imprimir Factura ARCA
            </button>
          </div>
        </div>

        <div v-else class="billing-card-body empty-billing">
          <p class="muted">La orden está finalizada. Gestioná el cobro y la emisión de la factura desde Facturación.</p>
          <NuxtLink :to="`/facturacion?orden=${selectedOrder.id}`" class="button small primary" @click="emit('close')"><Receipt :size="14" /> Armar factura</NuxtLink>
        </div>
      </div>
    </div>

    <!-- Administrative Footer -->
    <footer class="modal-footer">
      <button class="button" @click="emit('close')">Cerrar</button>
      <button v-if="!auth.isMechanic.value && canEditOrderDetails(selectedOrder)" class="button" @click="emit('edit', selectedOrder.id)"><Pencil :size="14" /> Editar orden</button>

      <button v-if="selectedOrder.status === 'En espera'" class="button primary" @click="changeStatus('iniciar')"><Play :size="16" /> Iniciar orden</button>
      <button v-if="selectedOrder.status === 'En proceso'" class="button primary" @click="changeStatus('finalizar')"><Check :size="16" /> Finalizar orden</button>
      <template v-if="!auth.isMechanic.value && canEditOrderWork(selectedOrder)">
        <button v-if="selectedOrder.status === 'Pendiente de ingreso'" class="button primary" @click="changeStatus('ingreso')">Registrar ingreso</button>
        <button class="button outlined" @click="changeStatus('cancelar')">Dar de baja</button>
      </template>
      <!-- If Cancelled -->
      <span v-if="selectedOrder.status === 'Cancelado'" class="badge neutral" style="padding: 6px 12px;">
        Orden cancelada / dada de baja
      </span>
    </footer>

  </CommonModalDialog>

  <OrdenesModalFinalizarOrden v-if="open && completionOpen && selectedOrder" :order="selectedOrder" :error="completionError" @close="completionOpen = false" @confirm="confirmCompletion" />

  <AgendaConfirmarAccionTurno v-if="open && futureAction" :appointment="futureAction.appointment" :action="futureAction.action" @close="futureAction = null" @confirm="confirmFutureAction" />

    <!-- Visor de la factura emitida -->
    <FacturacionModalVisorFacturaArca
      :open="arcaViewerOpen"
      :invoice="activeInvoiceForViewer"
      @close="arcaViewerOpen = false"
    />
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

:global(html.dark .info-grid-panel) {
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

:global(html.dark .info-label) {
  color: #8e8e93;
}

.info-item strong {
  font-size: 13px;
  color: #1e293b;
}

:global(html.dark .info-item strong) {
  color: #f5f5f7;
}

.detail-section {
  margin-top: 18px;
  border-top: 1px solid #f1f5f9;
  padding-top: 14px;
}

:global(html.dark .detail-section) {
  border-top-color: rgba(255, 255, 255, 0.08);
}

.detail-section h3 {
  font-size: 14px;
  margin-bottom: 8px;
  color: #0f172a;
}

:global(html.dark .detail-section h3) {
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

:global(html.dark .service-highlight) {
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

:global(html.dark .note-box) {
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

:global(html.dark .note-label) {
  color: #8e8e93;
}

.note-box p {
  font-size: 13px;
  color: #334155;
  margin: 0;
  white-space: pre-line;
}

:global(html.dark .note-box p) {
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

:global(html.dark .btn-cancel-order) {
  border-color: rgba(255, 69, 58, 0.35);
  color: #ff453a;
}

:global(html.dark .btn-cancel-order:hover) {
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

:global(html.dark .service-spec-panel) {
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

:global(html.dark .spec-label) {
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

:global(html.dark .filter-chip-item) {
  background: #1c1c1e;
  border-color: rgba(10, 132, 255, 0.3);
  color: #93c5fd;
}

/* Billing Status Card */
.billing-status-card {
  background: #ffffff;
  border: 1.5px solid #cbd5e1;
  border-radius: 12px;
  padding: 14px 16px;
  margin-top: 14px;
}

:global(html.dark .billing-status-card) {
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(255, 255, 255, 0.15);
}

.billing-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  flex-wrap: wrap;
  gap: 10px;
}

.billing-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.card-icon-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.6px;
  text-transform: uppercase;
  padding: 3px 8px;
  border-radius: 6px;
  background: #ffffff;
  color: #0f172a;
}

:global(html.dark) .card-icon-tag {
  background: transparent;
  color: #f5f5f7;
}

.total-order-val {
  font-size: 18px;
  font-weight: 850;
  color: #0f172a;
}

:global(html.dark .total-order-val) {
  color: #f8fafc;
}

.billing-card-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.invoice-summary-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.inv-type-box {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.billing-actions-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 4px;
}

</style>
