<script setup lang="ts">
import {
  Plus,
  Check,
  Printer,
  Share2,
  FileText,
  Search,
  CalendarDays,
  Wrench,
  Eye,
} from 'lucide-vue-next'
import type { Budget, Appointment } from '~/types'

const { db, vehicle, vehicleName, owner, createOrder } = useDatabase()
const { money, statusClass, matches } = useHelpers()
const { notify } = useToast()

const search = ref('')
const formModalOpen = ref(false)
const shareModalOpen = ref(false)
const assignModalOpen = ref(false)
const selectedBudget = ref<Budget | null>(null)
const budgetForAssign = ref<Budget | null>(null)
const autoPrintForModal = ref(false)

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
  shareModalOpen.value = true
  notify(`Presupuesto #${budget.id} generado exitosamente.`)
}

function openViewBudget(q: Budget) {
  selectedBudget.value = q
  autoPrintForModal.value = false
  shareModalOpen.value = true
}

function openPrintBudget(q: Budget) {
  selectedBudget.value = q
  autoPrintForModal.value = true
  shareModalOpen.value = true
}

function openShareBudget(q: Budget) {
  selectedBudget.value = q
  autoPrintForModal.value = false
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
          <span class="tiny-star">✳</span>
          TALLER CENTRAL / PRESUPUESTOS
        </div>
        <h1>Claridad antes de empezar.</h1>
      </div>
      <button class="button primary" @click="formModalOpen = true">
        <Plus :size="17" />Nuevo presupuesto
      </button>
    </section>

    <div class="list-toolbar">
      <span class="muted">{{ filteredQuotes.length }} presupuestos</span>
      <label class="search-box">
        <Search :size="17" />
        <input
          v-model="search"
          placeholder="Buscar por auto, cliente o trabajo…"
          aria-label="Buscar presupuestos"
        />
      </label>
    </div>

    <div class="quote-grid">
      <article
        v-for="q in filteredQuotes"
        :key="q.id"
        class="panel quote-card"
      >
        <div class="section-heading">
          <span class="eyebrow">PRESUPUESTO #{{ q.id }}</span>
          <span :class="['badge', statusClass(q.status)]">{{ q.status }}</span>
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
          <span>Subtotal Neto</span>
          <strong>{{ money(getSubtotal(q)) }}</strong>
        </div>
        <div class="quote-line vat-line-clean">
          <span>IVA (21%)</span>
          <strong>{{ money(getTax(q)) }}</strong>
        </div>

        <div class="quote-total">
          <div>
            <span style="font-size: 10px; text-transform: uppercase; letter-spacing: 0.5px; color: #64748b">Total con IVA</span>
            <strong>{{ money(getTotalWithTax(q)) }}</strong>
          </div>
          <button
            class="icon-button"
            title="Compartir o imprimir presupuesto"
            aria-label="Compartir o imprimir presupuesto"
            @click="openShare(q)"
          >
            <Share2 :size="17" />
          </button>
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

          <!-- Flow 3: En taller / Convertido -> Botones Ver, Imprimir, Compartir -->
          <template v-else-if="q.orderId || q.status === 'En taller' || q.status === 'Convertido'">
            <button
              class="button primary quote-action-btn"
              title="Ver detalle del presupuesto"
              @click="openViewBudget(q)"
            >
              <Eye :size="14" /> Ver
            </button>
            <button
              class="button outlined quote-action-btn"
              title="Imprimir presupuesto"
              @click="openPrintBudget(q)"
            >
              <Printer :size="14" /> Imprimir
            </button>
            <button
              class="button outlined quote-action-btn"
              title="Compartir por WhatsApp o copiar"
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

          <button
            v-if="!q.orderId && q.status !== 'En taller' && q.status !== 'Convertido'"
            class="button outlined quote-share-btn"
            title="Compartir / Imprimir presupuesto"
            @click="openShare(q)"
          >
            <Printer :size="14" /> Imprimir
          </button>
        </div>
      </article>
    </div>

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
      :auto-print="autoPrintForModal"
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

:global(html.dark) .budget-appointment-pill {
  background: rgba(10, 132, 255, 0.12);
  border-color: rgba(10, 132, 255, 0.25);
  color: #93c5fd;
}

:global(html.dark) .pill-icon {
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

:global(html.dark) .subtotal-line-clean {
  color: #d1d1d6;
}

:global(html.dark) .vat-line-clean {
  color: #64d2ff;
}
</style>
