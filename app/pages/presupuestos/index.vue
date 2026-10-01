<script setup lang="ts">
import {
  Plus,
  Download,
  Share2,
  FileText,
  Search,
  CalendarDays,
  Eye,
  LayoutGrid,
  Table,
  Trash2,
  Archive,
} from 'lucide-vue-next'
import type { Budget } from '~/types'

const { db, vehicle, vehicleName, owner } = useDatabase()
const { money, statusClass, matches } = useHelpers()
const { notify } = useWorkshopToast()

const route = useRoute()
const search = ref('')
const budgetSection = ref<'activos' | 'archivados'>('activos')
const viewMode = useListView('presupuestos', 'cards', ['table', 'cards'] as const)
const formModalOpen = ref(false)
const shareModalOpen = ref(false)
const selectedBudget = ref<Budget | null>(null)
const autoDownloadForModal = ref(false)
const budgetToDelete = ref<Budget | null>(null)
const appointmentsToDelete = computed(() => {
  const budget = db.value.quotes.find((q) => q.id === budgetToDelete.value?.id)
  return budget ? getAppointmentsForBudget(budget) : []
})

onMounted(() => {
  if (route.query.nuevo === '1' || route.query.nuevo === 'true') {
    formModalOpen.value = true
  }
})

watch(() => route.query.nuevo, (v) => {
  if (v === '1' || v === 'true') {
    formModalOpen.value = true
  }
})

const archivedQuotes = computed(() =>
  db.value.quotes.filter((q) => isBudgetArchived(q, db.value.invoices))
)
const activeQuotes = computed(() =>
  db.value.quotes.filter((q) => !isBudgetArchived(q, db.value.invoices))
)

const filteredQuotes = computed(() =>
  (budgetSection.value === 'archivados' ? archivedQuotes.value : activeQuotes.value).filter((q) => {
    const v = vehicle(q.vehicle)
    const o = owner(q.vehicle)
    return matches(
      search.value,
      q.id,
      q.description,
      v?.plate,
      v?.brand,
      v?.model,
      o?.name
    )
  })
)

function getSubtotal(q: Budget): number {
  return budgetAmounts(q).subtotal
}

function getTax(q: Budget): number {
  return budgetAmounts(q).tax
}

function getTotalWithTax(q: Budget): number {
  return budgetAmounts(q).total
}

function handleCreated(budget: Budget) {
  budgetSection.value = 'activos'
  formModalOpen.value = false
  selectedBudget.value = budget
  autoDownloadForModal.value = false
  shareModalOpen.value = true
  notify(`Presupuesto #${budget.id} generado exitosamente.`)
}

function openViewBudget(q: Budget) {
  selectedBudget.value = q
  autoDownloadForModal.value = false
  shareModalOpen.value = true
}

function openDownloadBudget(q: Budget) {
  selectedBudget.value = q
  autoDownloadForModal.value = true
  shareModalOpen.value = true
}

function openShareBudget(q: Budget) {
  selectedBudget.value = q
  autoDownloadForModal.value = false
  shareModalOpen.value = true
}

function openShare(q: Budget) {
  openViewBudget(q)
}

function getAppointmentForBudget(q: Budget) {
  if (q.appointmentId) {
    return db.value.appointments.find((a) => a.id === q.appointmentId)
  }
  return db.value.appointments.find((a) => a.budgetId === q.id && a.status !== 'Cancelado')
}

function getAppointmentsForBudget(q: Budget) {
  return db.value.appointments.filter((appointment) =>
    appointment.budgetId === q.id || appointment.id === q.appointmentId
  )
}

function canDeleteBudget(q: Budget): boolean {
  return q.orderId == null && !['Convertido', 'En taller', 'Archivado'].includes(q.status)
}

function requestDeleteBudget(q: Budget) {
  if (!canDeleteBudget(q)) return
  budgetToDelete.value = q
}

function cancelDeleteBudget() {
  budgetToDelete.value = null
}

function confirmDeleteBudget() {
  const q = db.value.quotes.find((budget) => budget.id === budgetToDelete.value?.id)
  if (!q) return
  if (!canDeleteBudget(q)) {
    cancelDeleteBudget()
    notify('No se puede eliminar un presupuesto que ya se convirtió en una orden de trabajo.')
    return
  }

  const appointmentIds = new Set(getAppointmentsForBudget(q).map((appointment) => appointment.id))
  db.value.appointments = db.value.appointments.filter((appointment) => !appointmentIds.has(appointment.id))
  db.value.quotes = db.value.quotes.filter((budget) => budget.id !== q.id)
  if (selectedBudget.value?.id === q.id) {
    shareModalOpen.value = false
    selectedBudget.value = null
  }
  cancelDeleteBudget()
  notify(appointmentIds.size
    ? `Presupuesto #${q.id} y ${appointmentIds.size === 1 ? 'su turno eliminados' : 'sus turnos eliminados'}.`
    : `Presupuesto #${q.id} eliminado.`)
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          TALLER CENTRAL / PRESUPUESTOS
        </div>
        <h1>Claridad antes de empezar.</h1>
      </div>
      <button class="button primary" @click="formModalOpen = true">
        <Plus :size="17" />Nuevo presupuesto
      </button>
    </section>

    <div class="budget-sections">
      <div class="segmented" aria-label="Apartados de presupuestos">
        <button
          type="button"
          :class="{ selected: budgetSection === 'activos' }"
          :aria-pressed="budgetSection === 'activos'"
          @click="budgetSection = 'activos'"
        >
          <FileText :size="15" /> Activos ({{ activeQuotes.length }})
        </button>
        <button
          type="button"
          :class="{ selected: budgetSection === 'archivados' }"
          :aria-pressed="budgetSection === 'archivados'"
          @click="budgetSection = 'archivados'"
        >
          <Archive :size="15" /> Archivados ({{ archivedQuotes.length }})
        </button>
      </div>
      <p v-if="budgetSection === 'archivados'" class="muted">Presupuestos cuya factura ya fue pagada.</p>
    </div>

    <div class="list-toolbar">
      <label class="search-box">
        <Search :size="17" />
        <input
          v-model="search"
          placeholder="Buscar por auto o cliente"
          aria-label="Buscar presupuestos"
        />
      </label>
      <div class="toolbar-left-group">
        <span class="muted">{{ filteredQuotes.length }} presupuestos</span>
        <div class="segmented">
          <button
            type="button"
            :class="{ selected: viewMode === 'cards' }"
            :aria-pressed="viewMode === 'cards'"
            @click="viewMode = 'cards'"
            title="Ver en formato tarjetas"
          >
            <LayoutGrid :size="14" /> Tarjetas
          </button>
          <button
            type="button"
            :class="{ selected: viewMode === 'table' }"
            :aria-pressed="viewMode === 'table'"
            @click="viewMode = 'table'"
            title="Ver en formato tabla"
          >
            <Table :size="14" /> Tabla
          </button>
        </div>
      </div>

    </div>

    <!-- VISTA 1: TARJETAS (CARDS) -->
    <div v-if="viewMode === 'cards'" class="quote-grid">
      <article
        v-for="q in filteredQuotes"
        :key="q.id"
        class="panel quote-card"
      >
        <div class="section-heading">
          <span class="eyebrow">PRESUPUESTO #{{ q.id }}</span>
          <span :class="['badge', budgetSection === 'archivados' || q.status === 'Pendiente' ? 'neutral' : statusClass(q.status)]">
            {{ budgetSection === 'archivados' ? 'Archivado' : q.status }}
          </span>
        </div>
        <h2>{{ vehicleName(q.vehicle) }}</h2>
        <p>{{ owner(q.vehicle)?.name }} · {{ vehicle(q.vehicle)?.plate }}</p>
        <h3>{{ q.description }}</h3>

        <!-- Financial Breakdown Lines -->
        <div class="quote-line">
          <span>Mano de obra</span>
          <strong>{{ money(q.labor) }}</strong>
        </div>
        <div class="quote-line">
          <span>
            Repuestos e insumos
            <small v-if="q.items && q.items.length" class="muted" style="font-size: 10px; display: block">
              ({{ q.items.length }} {{ q.items.length === 1 ? 'ítem' : 'ítems' }})
            </small>
          </span>
          <strong>{{ money(q.materials) }}</strong>
        </div>
        <div class="quote-line subtotal-line-clean">
          <span>Subtotal</span>
          <strong>{{ money(getSubtotal(q)) }}</strong>
        </div>
        <div class="quote-line vat-line-clean">
          <span>IVA (21%)</span>
          <strong>{{ money(getTax(q)) }}</strong>
        </div>
        <div class="quote-total">
          <span>Total estimado</span>
          <strong>{{ money(getTotalWithTax(q)) }}</strong>
        </div>

        <!-- Assigned Appointment Indicator if any -->
        <div v-if="getAppointmentForBudget(q)" class="budget-appointment-pill">
          <CalendarDays :size="13" class="pill-icon" />
          <span>Turno agendado: <strong>{{ getAppointmentForBudget(q)?.date }} a las {{ getAppointmentForBudget(q)?.time }} hs</strong></span>
        </div>

        <!-- Action Buttons -->
        <div class="quote-actions-row">
          <button class="button primary quote-action-btn" @click="openViewBudget(q)">
            <Eye :size="14" /> Ver detalle
          </button>
          <button class="button outlined quote-action-btn" @click="openDownloadBudget(q)">
            <Download :size="14" /> Descargar PDF
          </button>
          <button class="button outlined quote-action-btn" @click="openShareBudget(q)">
            <Share2 :size="14" /> Compartir
          </button>
          <button
            v-if="canDeleteBudget(q)"
            type="button"
            class="button outlined quote-delete-btn"
            :aria-label="`Eliminar presupuesto #${q.id}`"
            @click="requestDeleteBudget(q)"
          >
            <Trash2 :size="14" /> Eliminar
          </button>
        </div>
      </article>
    </div>

    <!-- VISTA 2: TABLA (TABLE) -->
    <section v-else-if="viewMode === 'table' && filteredQuotes.length" class="panel table-scroll">
      <table>
        <thead>
          <tr>
            <th>PRESUPUESTO</th>
            <th>VEHÍCULO / CLIENTE</th>
            <th>TRABAJO / DESCRIPCIÓN</th>
            <th>MANO DE OBRA</th>
            <th>REPUESTOS</th>
            <th>SUBTOTAL</th>
            <th>IVA (21%)</th>
            <th>TOTAL ESTIMADO</th>
            <th style="text-align: right">ACCIONES</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="q in filteredQuotes" :key="q.id">
            <td>
              <div class="table-budget-meta">
                <strong>#{{ q.id }}</strong>
                <span :class="['badge', budgetSection === 'archivados' || q.status === 'Pendiente' ? 'neutral' : statusClass(q.status)]" style="font-size: 8.5px; padding: 2px 6px">
                  {{ budgetSection === 'archivados' ? 'Archivado' : q.status }}
                </span>
                <small v-if="getAppointmentForBudget(q)" class="muted table-subtext">
                  Turno: {{ getAppointmentForBudget(q)?.date }} {{ getAppointmentForBudget(q)?.time }}hs
                </small>
              </div>
            </td>
            <td>
              <div class="table-vehicle-cell">
                <strong>{{ vehicleName(q.vehicle) }}</strong>
                <small>{{ vehicle(q.vehicle)?.plate }} · {{ owner(q.vehicle)?.name }}</small>
              </div>
            </td>
            <td>
              <div class="table-work-desc">{{ q.description }}</div>
            </td>
            <td>
              <span>{{ money(q.labor) }}</span>
            </td>
            <td>
              <span>{{ money(q.materials) }}</span>
              <small v-if="q.items && q.items.length" class="muted" style="display: block; font-size: 9px">
                {{ q.items.length }} {{ q.items.length === 1 ? 'ítem' : 'ítems' }}
              </small>
            </td>
            <td>
              <span>{{ money(getSubtotal(q)) }}</span>
            </td>
            <td>
              <span>{{ money(getTax(q)) }}</span>
            </td>
            <td>
              <strong class="table-total-amt">{{ money(getTotalWithTax(q)) }}</strong>
            </td>
            <td style="text-align: right">
              <div class="table-row-actions">
                <button
                  class="button small"
                  title="Ver detalle del presupuesto"
                  @click="openShare(q)"
                >
                  <Eye :size="13" /> Ver detalle
                </button>
                <button
                  v-if="canDeleteBudget(q)"
                  type="button"
                  class="button small quote-delete-btn"
                  :aria-label="`Eliminar presupuesto #${q.id}`"
                  @click="requestDeleteBudget(q)"
                >
                  <Trash2 :size="13" /> Eliminar
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <div v-if="!filteredQuotes.length" class="empty-state">
      <FileText :size="38" class="muted" />
      <h3>{{ budgetSection === 'archivados' ? 'No se encontraron presupuestos archivados' : 'No se encontraron presupuestos activos' }}</h3>
      <p v-if="search">Probá con otra búsqueda.</p>
      <p v-else-if="budgetSection === 'archivados'">Cuando se registre el pago de una factura, su presupuesto aparecerá acá.</p>
      <p v-else>Creá un presupuesto nuevo para empezar.</p>
    </div>

    <CommonModalDialog
      v-if="budgetToDelete"
      class="dialog delete-budget-dialog"
      role="alertdialog"
      aria-labelledby="delete-budget-title"
      aria-describedby="delete-budget-description"
      @close="cancelDeleteBudget"
    >
      <template v-if="budgetToDelete">
        <div class="delete-budget-content">
          <h2 id="delete-budget-title">
            ¿Eliminar presupuesto #{{ budgetToDelete.id }}{{ appointmentsToDelete.length === 1 ? ' y su turno' : appointmentsToDelete.length ? ' y sus turnos' : '' }}?
          </h2>
          <p v-if="appointmentsToDelete.length" id="delete-budget-description">
            Se eliminarán el presupuesto y {{ appointmentsToDelete.length === 1 ? 'el turno asociado' : 'los turnos asociados' }} de la agenda. ¿Estás seguro de que querés eliminar ambos? Esta acción no se puede deshacer.
          </p>
          <p v-else id="delete-budget-description">¿Estás seguro de que querés eliminar este presupuesto? Esta acción no se puede deshacer.</p>
          <p v-for="appointment in appointmentsToDelete" :key="appointment.id" class="muted">
            Turno: {{ appointment.date }} · {{ appointment.time }} hs
          </p>
        </div>
        <footer class="dialog-footer">
          <button type="button" class="button" autofocus @click="cancelDeleteBudget">Cancelar</button>
          <button type="button" class="button delete-confirm-btn" @click="confirmDeleteBudget">
            <Trash2 :size="14" /> {{ appointmentsToDelete.length === 1 ? 'Eliminar presupuesto y turno' : appointmentsToDelete.length ? 'Eliminar presupuesto y turnos' : 'Eliminar presupuesto' }}
          </button>
        </footer>
      </template>
    </CommonModalDialog>

    <!-- Create Modal -->
    <PresupuestosModalPresupuesto
      :open="formModalOpen"
      @close="formModalOpen = false"
      @created="handleCreated"
    />

    <!-- Share & Print Modal -->
    <PresupuestosModalCompartirPresupuesto
      :open="shareModalOpen"
      :budget="selectedBudget"
      :auto-download="autoDownloadForModal"
      @close="shareModalOpen = false"
    />

  </div>
</template>

<style scoped>
.budget-sections {
  margin-bottom: 18px;
}

.budget-sections .segmented {
  flex-wrap: wrap;
}

.budget-sections p {
  margin: 10px 0 0;
  font-size: 13px;
}

.delete-budget-dialog {
  width: 420px;
}

.delete-budget-content {
  padding: 24px;
}

.delete-budget-content h2 {
  margin: 0 0 12px;
  font-size: 18px;
}

.delete-budget-content p {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.5;
}

.delete-budget-dialog .dialog-footer {
  padding: 16px 24px;
  flex-wrap: wrap;
}

.button.delete-confirm-btn {
  background: #dc2626 !important;
  border-color: #dc2626 !important;
  color: #ffffff !important;
}

.button.delete-confirm-btn:hover {
  background: #b91c1c !important;
  border-color: #b91c1c !important;
}

.quote-card .badge.neutral,
.table-budget-meta .badge.neutral {
  background: #f4f4f5;
  color: #52525b;
}

.quote-actions-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.button.quote-delete-btn {
  color: #dc2626;
}

:global(html.dark) .button.quote-delete-btn {
  color: #f87171;
}

.quote-action-btn {
  flex: 1.3;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.budget-appointment-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding: 6px 10px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  font-size: 12px;
  color: #1e40af;
}

.pill-icon {
  color: #2563eb;
  flex-shrink: 0;
}

:global(html.dark .budget-appointment-pill) {
  background: rgba(10, 132, 255, 0.12);
  border-color: rgba(10, 132, 255, 0.25);
  color: #93c5fd;
}

:global(html.dark .pill-icon) {
  color: #60a5fa;
}

.subtotal-line-clean {
  color: #334155;
  font-weight: 550;
}

.vat-line-clean {
  color: #2563eb;
  font-weight: 550;
}

:global(html.dark .subtotal-line-clean) {
  color: #d1d1d6;
}

:global(html.dark .vat-line-clean) {
  color: #64d2ff;
}

.toolbar-left-group {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.table-budget-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.table-subtext {
  font-size: 9.5px;
  display: block;
}

.table-vehicle-cell {
  display: flex;
  flex-direction: column;
}

.table-vehicle-cell small {
  margin-top: 2px;
}

.table-work-desc {
  max-width: 260px;
  font-size: 11px;
  color: #334155;
  line-height: 1.4;
  word-break: break-word;
}

:global(html.dark .table-work-desc) {
  color: #cbd5e1;
}

.table-total-amt {
  font-size: 12px;
  color: #0f172a;
}

:global(html.dark .table-total-amt) {
  color: #f8fafc;
}

.table-row-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
}
</style>
