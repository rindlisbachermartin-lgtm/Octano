<script setup lang="ts">
import {
  Plus,
  Check,
  Download,
  Share2,
  FileText,
  Search,
  CalendarDays,
  Wrench,
  Eye,
  LayoutGrid,
  Table,
} from 'lucide-vue-next'
import type { Budget, Appointment } from '~/types'

const { db, vehicle, vehicleName, owner, createOrder } = useDatabase()
const { money, statusClass, matches } = useHelpers()
const { notify } = useWorkshopToast()

const route = useRoute()
const search = ref('')
const viewMode = useListView('presupuestos', 'cards', ['table', 'cards'] as const)
const formModalOpen = ref(false)
const shareModalOpen = ref(false)
const assignModalOpen = ref(false)
const selectedBudget = ref<Budget | null>(null)
const budgetForAssign = ref<Budget | null>(null)
const autoDownloadForModal = ref(false)

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

const filteredQuotes = computed(() =>
  db.value.quotes.filter((q) => {
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
  return Number(q.labor || 0) + Number(q.materials || 0)
}

function getTax(q: Budget): number {
  return Math.round(getSubtotal(q) * 0.21)
}

function getTotalWithTax(q: Budget): number {
  return getSubtotal(q) + getTax(q)
}

function convertQuote(q: Budget) {
  if (q.status === 'Convertido' || q.status === 'En taller') return
  q.status = 'Convertido'
  const initialParts = (q.items || [])
    .filter((it) => it.partId && it.partId !== 'custom')
    .map((it) => ({
      id: Number(it.partId),
      name: it.name,
      price: it.unitPrice,
    }))
  const orderId = createOrder(
    q.vehicle,
    q.description,
    'Nicolás',
    null,
    initialParts,
    q.serviceTypes || [],
    q.oilSpec || ''
  )
  q.orderId = orderId
  notify(`Presupuesto aprobado. Orden de trabajo #${orderId} creada.`)
}

function handleCreated(budget: Budget) {
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

function openAssignTurno(q: Budget) {
  budgetForAssign.value = q
  assignModalOpen.value = true
}

function openAssignTurnoFromShare(b: Budget) {
  shareModalOpen.value = false
  openAssignTurno(b)
}

function handleTurnoAssigned(appointment: Appointment) {
  assignModalOpen.value = false
}

function getAppointmentForBudget(q: Budget) {
  if (q.appointmentId) {
    return db.value.appointments.find((a) => a.id === q.appointmentId)
  }
  return db.value.appointments.find((a) => a.budgetId === q.id && a.status !== 'Cancelado')
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

    <div class="list-toolbar">
      <label class="search-box">
        <Search :size="17" />
        <input
          v-model="search"
          placeholder="Buscar por auto, cliente o trabajo…"
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
          <span :class="['badge', q.status === 'Pendiente' ? 'neutral' : statusClass(q.status)]">{{ q.status }}</span>
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
        <div class="quote-total">
          <span>Total estimado</span>
          <strong>{{ money(getSubtotal(q)) }}</strong>
        </div>

        <!-- Assigned Appointment Indicator if any -->
        <div v-if="getAppointmentForBudget(q)" class="budget-appointment-pill">
          <CalendarDays :size="13" class="pill-icon" />
          <span>Turno agendado: <strong>{{ getAppointmentForBudget(q)?.date }} a las {{ getAppointmentForBudget(q)?.time }} hs</strong></span>
        </div>

        <!-- Action Buttons -->
        <div class="quote-actions-row">
          <!-- Flow 1: Asignar turno (If Pendiente) -->
          <button
            v-if="q.status === 'Pendiente'"
            class="button primary quote-action-btn"
            @click="openAssignTurno(q)"
          >
            <CalendarDays :size="15" /> Asignar turno
          </button>

          <!-- Flow 2: Con turno -> Ir a la Agenda a iniciar la OT -->
          <NuxtLink
            v-else-if="q.status === 'Con turno'"
            to="/agenda"
            class="button primary quote-action-btn btn-turn-assigned"
          >
            <CalendarDays :size="15" /> Ver en Agenda
          </NuxtLink>

          <!-- Flow 3: En taller / Convertido -> Botones Ver detalle, Descargar PDF, Compartir -->
          <template v-else-if="q.orderId || q.status === 'En taller' || q.status === 'Convertido'">
            <button
              class="button primary quote-action-btn"
              title="Ver detalle del presupuesto"
              @click="openViewBudget(q)"
            >
              <Eye :size="14" /> Ver detalle
            </button>
            <button
              class="button outlined quote-action-btn"
              title="Descargar presupuesto como PDF"
              @click="openDownloadBudget(q)"
            >
              <Download :size="14" /> Descargar PDF
            </button>
            <button
              class="button outlined quote-action-btn"
              title="Compartir por WhatsApp"
              @click="openShareBudget(q)"
            >
              <Share2 :size="14" /> Compartir
            </button>
          </template>

          <!-- Fallback direct OT -->
          <button
            v-if="q.status === 'Pendiente'"
            class="button outlined quote-direct-btn"
            title="Iniciar OT directamente sin agendar turno previo"
            @click="convertQuote(q)"
          >
            <Check :size="14" /> OT directa
          </button>

          <!-- Botón Ver detalle -->
          <button
            v-if="!q.orderId && q.status !== 'En taller' && q.status !== 'Convertido'"
            class="button outlined quote-share-btn"
            title="Ver detalle del presupuesto"
            @click="openShare(q)"
          >
            <Eye :size="14" /> Ver detalle
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
            <th>TOTAL ESTIMADO</th>
            <th style="text-align: right">ACCIONES</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="q in filteredQuotes" :key="q.id">
            <td>
              <div class="table-budget-meta">
                <strong>#{{ q.id }}</strong>
                <span :class="['badge', q.status === 'Pendiente' ? 'neutral' : statusClass(q.status)]" style="font-size: 8.5px; padding: 2px 6px">
                  {{ q.status }}
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
              <strong class="table-total-amt">{{ money(getSubtotal(q)) }}</strong>
            </td>
            <td style="text-align: right">
              <div class="table-row-actions">
                <button
                  v-if="q.status === 'Pendiente'"
                  class="button primary small"
                  title="Asignar turno"
                  @click="openAssignTurno(q)"
                >
                  <CalendarDays :size="13" /> Turno
                </button>
                <NuxtLink
                  v-else-if="q.status === 'Con turno'"
                  to="/agenda"
                  class="button primary small"
                  title="Ver en Agenda"
                >
                  <CalendarDays :size="13" /> Agenda
                </NuxtLink>
                <button
                  v-if="q.status === 'Pendiente'"
                  class="button small"
                  title="Iniciar OT directamente"
                  @click="convertQuote(q)"
                >
                  <Check :size="13" /> OT directa
                </button>
                <button
                  class="button small"
                  title="Ver detalle del presupuesto"
                  @click="openShare(q)"
                >
                  <Eye :size="13" /> Ver detalle
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <div v-if="!filteredQuotes.length" class="empty-state">
      <FileText :size="38" class="muted" />
      <h3>No se encontraron presupuestos</h3>
      <p>Probá con otra búsqueda o creá uno nuevo.</p>
    </div>

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
      @assign-turno="openAssignTurnoFromShare"
    />

    <!-- Assign Turno Modal -->
    <PresupuestosModalAsignarTurnoPresupuesto
      :open="assignModalOpen"
      :budget="budgetForAssign"
      @close="assignModalOpen = false"
      @assigned="handleTurnoAssigned"
    />
  </div>
</template>

<style scoped>
.quote-card .badge.neutral,
.table-budget-meta .badge.neutral {
  background: #f4f4f5;
  color: #52525b;
}

.quote-actions-row {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

.quote-action-btn {
  flex: 1.3;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.btn-turn-assigned {
  background: #0284c7;
  color: #ffffff;
}
.btn-turn-assigned:hover {
  background: #0369a1;
}

.btn-in-shop {
  background: #10b981;
  color: #ffffff;
}
.btn-in-shop:hover {
  background: #059669;
}

.quote-direct-btn {
  flex: 0.9;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
  padding: 0 10px;
}

.quote-share-btn {
  flex: 0.9;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
  padding: 0 10px;
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
