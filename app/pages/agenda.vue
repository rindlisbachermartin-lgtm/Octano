<script setup lang="ts">
import {
  ChevronLeft,
  ChevronRight,
  Plus,
  X,
  CalendarDays,
  Clock,
  Play,
  Check,
  Wrench,
  User,
  FileText,
} from 'lucide-vue-next'
import type { Appointment } from '~/types'

const { db, vehicle, vehicleName, owner, createOrder } = useDatabase()
const { statusClass } = useHelpers()
const { notify } = useWorkshopToast()

const viewMode = useListView('agenda', 'dia', ['dia', 'semana'] as const)
const { today } = useWorkshopDay()
const date = ref(today.value)
const modalDate = ref(today.value)
const modalOpen = ref(false)
const selectedTime = ref('11:00')

const prettyDate = computed(() =>
  new Intl.DateTimeFormat('es-AR', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  }).format(new Date(`${date.value}T12:00:00`))
)

// Calculate Monday of the current selected date
function getMonday(dStr: string) {
  const d = new Date(`${dStr}T12:00:00`)
  const day = d.getDay()
  const diff = d.getDate() - day + (day === 0 ? -6 : 1)
  return new Date(d.setDate(diff))
}

const weekDays = computed(() => {
  const monday = getMonday(date.value)
  return Array.from({ length: 6 }, (_, i) => {
    const d = new Date(monday)
    d.setDate(monday.getDate() + i)
    const dateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
      d.getDate()
    ).padStart(2, '0')}`
    const dayName = new Intl.DateTimeFormat('es-AR', { weekday: 'short' })
      .format(d)
      .toUpperCase()
      .replace('.', '')
    const dayNum = d.getDate()
    const monthName = new Intl.DateTimeFormat('es-AR', { month: 'short' })
      .format(d)
      .toUpperCase()
      .replace('.', '')
    const appointments = db.value.appointments
      .filter((a) => a.date === dateStr)
      .sort((a, b) => a.time.localeCompare(b.time))
    return {
      dateStr,
      dayName,
      dayNum,
      monthName,
      appointments,
      isCurrent: dateStr === date.value,
    }
  })
})

const weekLabel = computed(() => {
  const monday = getMonday(date.value)
  const saturday = new Date(monday)
  saturday.setDate(monday.getDate() + 5)
  const startDay = monday.getDate()
  const endDay = saturday.getDate()
  const startMonth = new Intl.DateTimeFormat('es-AR', { month: 'long' }).format(monday)
  const endMonth = new Intl.DateTimeFormat('es-AR', { month: 'long' }).format(saturday)
  if (startMonth.toLowerCase() === endMonth.toLowerCase()) {
    return `Semana del ${startDay} al ${endDay} de ${startMonth}`
  }
  return `Semana del ${startDay} de ${startMonth} al ${endDay} de ${endMonth}`
})

const weekAppointmentsCount = computed(() => {
  return weekDays.value.reduce((acc, d) => acc + d.appointments.length, 0)
})

const dailyAppointments = computed(() =>
  db.value.appointments
    .filter((a) => a.date === date.value)
    .sort((a, b) => a.time.localeCompare(b.time))
)

function changeDay(amount: number) {
  const d = new Date(`${date.value}T12:00:00`)
  d.setDate(d.getDate() + amount)
  date.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
    d.getDate()
  ).padStart(2, '0')}`
}

function changeWeek(amount: number) {
  changeDay(amount * 7)
}

function handlePrev() {
  if (viewMode.value === 'dia') changeDay(-1)
  else changeWeek(-1)
}

function handleNext() {
  if (viewMode.value === 'dia') changeDay(1)
  else changeWeek(1)
}

function switchToDay(dStr: string) {
  date.value = dStr
  viewMode.value = 'dia'
}

function openModalForDate(dStr: string, time = '11:00') {
  modalDate.value = dStr
  selectedTime.value = time
  modalOpen.value = true
}

function checkIn(a: Appointment) {
  if (a.status === 'En Taller') return

  let initialParts: any[] = []
  let linkedBudget = null

  if (a.budgetId) {
    linkedBudget = db.value.quotes.find((q) => q.id === a.budgetId)
    if (linkedBudget) {
      linkedBudget.status = 'En taller'
      if (linkedBudget.items && linkedBudget.items.length) {
        initialParts = linkedBudget.items.map((item, idx) => ({
          id: item.partId || Date.now() + idx,
          name: item.quantity > 1 ? `${item.name} (x${item.quantity})` : item.name,
          price: item.unitPrice * item.quantity,
        }))
      }
    }
  }

  const orderId = createOrder(a.vehicle, a.reason, 'Nicolás', null, initialParts)
  a.status = 'En Taller'

  if (linkedBudget) {
    linkedBudget.orderId = orderId
  }

  const v = vehicle(a.vehicle)
  const vName = v?.brand ? `${v.brand} ${v.model}` : 'vehículo'
  notify(`Orden de trabajo #${orderId} iniciada para ${vName} (${v?.plate || ''}).`)
}

function openForHour(hour: string) {
  openModalForDate(date.value, hour)
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          TALLER CENTRAL / AGENDA
        </div>
        <h1>Cada turno, en su lugar.</h1>
      </div>
      <button class="button primary" @click="openModalForDate(date)">
        <Plus :size="17" />Nuevo turno
      </button>
    </section>

    <section class="panel">
      <!-- Toolbar -->
      <div class="panel-top agenda-toolbar">
        <div class="date-switch">
          <button
            class="icon-button outlined"
            aria-label="Anterior"
            @click="handlePrev"
          >
            <ChevronLeft :size="18" />
          </button>
          <h2>{{ viewMode === 'dia' ? prettyDate : weekLabel }}</h2>
          <button
            class="icon-button outlined"
            aria-label="Siguiente"
            @click="handleNext"
          >
            <ChevronRight :size="18" />
          </button>
        </div>

        <div class="toolbar-center-controls">
          <!-- Segmented Toggle: Día / Semana -->
          <div class="segmented">
            <button
              :class="{ selected: viewMode === 'dia' }"
            :aria-pressed="viewMode === 'dia'"
              @click="viewMode = 'dia'"
            >
              <Clock :size="14" /> Día
            </button>
            <button
              :class="{ selected: viewMode === 'semana' }"
            :aria-pressed="viewMode === 'semana'"
              @click="viewMode = 'semana'"
            >
              <CalendarDays :size="14" /> Semana
            </button>
          </div>

          <CommonDatePicker v-model="date" label="Ir al día" class="agenda-date-picker" />
        </div>
      </div>

      <!-- VIEW 1: DÍA -->
      <div v-if="viewMode === 'dia'" class="schedule">
        <div
          v-for="hour in [
            '08:00',
            '09:00',
            '10:00',
            '11:00',
            '12:00',
            '13:00',
            '14:00',
            '15:00',
            '16:00',
            '17:00',
          ]"
          :key="hour"
          class="schedule-row"
        >
          <time>{{ hour }}</time>
          <div>
            <article
              v-for="a in dailyAppointments.filter(
                (a) => a.time.slice(0, 2) === hour.slice(0, 2)
              )"
              :key="a.id"
              class="appointment"
            >
              <div>
                <span class="eyebrow" style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
                  <span>{{ a.time }} · {{ vehicle(a.vehicle)?.plate }}</span>
                  <NuxtLink
                    v-if="a.budgetId"
                    to="/presupuestos"
                    class="appointment-budget-tag"
                    title="Ver presupuesto de origen"
                  >
                    <FileText :size="10" /> Presupuesto #{{ a.budgetId }}
                  </NuxtLink>
                </span>
                <h3>
                  {{ vehicleName(a.vehicle) }}
                  <span :class="['badge', statusClass(a.status)]">{{ a.status }}</span>
                </h3>
                <p>{{ a.reason }} · {{ owner(a.vehicle)?.name }}</p>
              </div>
              <div class="row-actions">
                <button
                  v-if="!['En Taller', 'Cancelado'].includes(a.status)"
                  class="button small primary"
                  @click="checkIn(a)"
                >
                  <Play :size="13" /> Iniciar OT
                </button>
                <span
                  v-else-if="a.status === 'En Taller'"
                  class="badge"
                  style="background: #ecfdf5; color: #065f46; border: 1px solid #a7f3d0; font-weight: 600; display: inline-flex; align-items: center; gap: 5px; padding: 4px 9px;"
                >
                  <Check :size="13" /> En taller · OT Creada
                </span>
                <span
                  v-else-if="a.status === 'Cancelado'"
                  class="badge neutral"
                >
                  Cancelado
                </span>
                <button
                  v-if="!['En Taller', 'Cancelado'].includes(a.status)"
                  class="icon-button"
                  aria-label="Cancelar turno"
                  title="Cancelar turno"
                  @click="
                    a.status = 'Cancelado';
                    notify('Turno cancelado.');
                  "
                >
                  <X :size="16" />
                </button>
              </div>
            </article>

            <button
              v-if="!dailyAppointments.some((a) => a.time.slice(0, 2) === hour.slice(0, 2))"
              class="free-slot"
              @click="openForHour(hour)"
            >
              <Plus :size="14" /> Agendar turno
            </button>
          </div>
        </div>
      </div>

      <!-- VIEW 2: SEMANA -->
      <div v-else class="week-view-container">
        <div class="week-summary-bar">
          <div class="week-summary-info">
            <strong>{{ weekAppointmentsCount }} turnos programados</strong>
            <span class="muted"> para esta semana</span>
          </div>
          <span class="week-summary-tip">
            Seleccioná <strong>Iniciar OT</strong> para comenzar directamente el trabajo en taller.
          </span>
        </div>

        <div class="week-scroll-wrapper">
          <div class="week-columns-grid">
            <div
              v-for="day in weekDays"
              :key="day.dateStr"
              class="week-day-column"
              :class="{ 'is-active-day': day.isCurrent }"
            >
              <!-- Day Header -->
              <div class="week-day-header" @click="switchToDay(day.dateStr)">
                <div class="day-title-wrap">
                  <span class="week-day-name">{{ day.dayName }}</span>
                  <span class="week-day-num">{{ day.dayNum }}</span>
                </div>
                <div class="day-header-actions">
                  <span v-if="day.appointments.length" class="count-bubble" :class="{ 'has-appointments': day.appointments.length > 0 }">
                    {{ day.appointments.length }}
                  </span>
                  <button
                    class="icon-button small-btn"
                    title="Nuevo turno este día"
                    @click.stop="openModalForDate(day.dateStr)"
                  >
                    <Plus :size="13" />
                  </button>
                </div>
              </div>

              <!-- Day Appointments List -->
              <div class="week-day-body">
                <article
                  v-for="a in day.appointments"
                  :key="a.id"
                  class="week-card"
                  :class="{
                    'is-in-shop': a.status === 'En Taller',
                    'is-cancelled': a.status === 'Cancelado',
                  }"
                >
                  <!-- Card Header: Time + Cancel -->
                  <div class="week-card-top">
                    <div style="display: flex; align-items: center; gap: 5px;">
                      <span class="week-card-time">{{ a.time }} hs</span>
                      <NuxtLink
                        v-if="a.budgetId"
                        to="/presupuestos"
                        class="week-card-budget-tag"
                        title="Originado en presupuesto"
                        @click.stop
                      >
                        <FileText :size="9" /> #{{ a.budgetId }}
                      </NuxtLink>
                    </div>
                    <button
                      v-if="!['En Taller', 'Cancelado'].includes(a.status)"
                      class="week-card-close-btn"
                      title="Cancelar turno"
                      aria-label="Cancelar turno"
                      @click.stop="
                        a.status = 'Cancelado';
                        notify('Turno cancelado.');
                      "
                    >
                      <X :size="13" />
                    </button>
                    <span v-else-if="a.status === 'Cancelado'" class="week-card-cancelled-tag">
                      Cancelado
                    </span>
                  </div>

                  <!-- Card Body -->
                  <div class="week-card-info">
                    <div class="week-card-vehicle">
                      <strong :title="vehicleName(a.vehicle)">{{ vehicleName(a.vehicle) }}</strong>
                      <span class="plate small-plate">{{ vehicle(a.vehicle)?.plate }}</span>
                    </div>
                    <p class="week-card-reason">{{ a.reason }}</p>
                    <small class="week-card-client">{{ owner(a.vehicle)?.name }}</small>
                  </div>

                  <!-- Card Action -->
                  <div class="week-card-action">
                    <button
                      v-if="!['En Taller', 'Cancelado'].includes(a.status)"
                      class="button small primary week-start-ot-btn"
                      @click="checkIn(a)"
                    >
                      Iniciar OT
                    </button>
                    <span v-else-if="a.status === 'En Taller'" class="badge green week-in-shop-badge">
                      En taller
                    </span>
                  </div>
                </article>

                <!-- Free Slot / Agendar button in column -->
                <button
                  class="week-free-btn"
                  @click="openModalForDate(day.dateStr)"
                >
                  <Plus :size="13" />
                  <span>Agendar turno</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <AgendaModalTurno
      :open="modalOpen"
      :default-date="modalDate"
      @close="modalOpen = false"
      @created="
        modalOpen = false;
        notify('Turno agendado.');
      "
    />
  </div>
</template>

<style scoped>
.agenda-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: nowrap;
  gap: 16px;
}

.date-switch {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
}

.date-switch h2 {
  white-space: nowrap;
  margin: 0;
  font-size: 16px;
  line-height: 1.2;
}

.toolbar-center-controls {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  flex-wrap: nowrap;
  flex-shrink: 0;
}

.toolbar-center-controls .segmented,
.agenda-date-picker {
  width: 160px;
}

.toolbar-center-controls .segmented {
  height: 36px;
}

.toolbar-center-controls .segmented button {
  flex: 1;
  justify-content: center;
  height: 28px;
}

.agenda-date-picker :deep(.date-picker-trigger) {
  height: 36px;
  min-height: 36px;
  padding: 5px 8px;
  border-radius: 6px;
  font-size: 10px;
}

:global(html.dark .agenda-date-picker .date-picker-trigger) {
  background: #3a3a3c;
  border-color: rgba(255, 255, 255, 0.06);
  color: #fff;
}

@media (max-width: 768px) {
  .agenda-toolbar {
    flex-wrap: wrap;
    gap: 12px;
  }
  .toolbar-center-controls {
    width: 100%;
    justify-content: space-between;
    flex-wrap: wrap;
  }
}

.week-view-container {
  padding: 0;
  display: flex;
  flex-direction: column;
}

.week-summary-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 24px;
  background: #f8fafc;
  border-bottom: 1px solid var(--line);
  font-size: 12px;
  gap: 12px;
}

.week-summary-info {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #0f172a;
}

.week-summary-tip {
  font-size: 11px;
  color: #64748b;
}
.week-summary-tip strong {
  color: #2563eb;
}

.week-scroll-wrapper {
  overflow-x: auto;
  padding: 20px;
}

.week-columns-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(210px, 1fr));
  gap: 16px;
  min-width: 1100px;
  align-items: start;
}

.week-day-column {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease;
  min-height: 480px;
}

.week-day-column.is-active-day {
  background: #fdfefe;
  border-color: #93c5fd;
  box-shadow: 0 0 0 1px #93c5fd, 0 4px 16px rgba(37, 99, 235, 0.06);
}

.week-day-column.is-active-day .week-day-header {
  background: #eff6ff;
  border-bottom-color: #bfdbfe;
}

.week-day-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px;
  background: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  cursor: pointer;
  user-select: none;
  transition: background-color 150ms ease;
}

@media (hover: hover) and (pointer: fine) {
  .week-day-header:hover {
    background: #f1f5f9;
  }
}

.day-title-wrap {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.week-day-name {
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
  letter-spacing: 0.6px;
  text-transform: uppercase;
}

.week-day-num {
  font-family: 'Public Sans', sans-serif;
  font-size: 20px;
  font-weight: 800;
  color: #0f172a;
  line-height: 1;
}

.day-header-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.count-bubble {
  background: #f1f5f9;
  color: #475569;
  font-size: 10px;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 999px;
  border: 1px solid #e2e8f0;
}

.count-bubble.has-appointments {
  background: #dbeafe;
  color: #1d4ed8;
  border-color: #bfdbfe;
}

.small-btn {
  width: 26px;
  height: 26px;
  padding: 0;
  display: grid;
  place-items: center;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #475569;
  transition:
    transform 150ms var(--ease),
    background-color 150ms ease,
    border-color 150ms ease;
}

@media (hover: hover) and (pointer: fine) {
  .small-btn:hover {
    background: #f8fafc;
    border-color: #cbd5e1;
    color: #0f172a;
  }
}

.small-btn:active {
  transform: scale(0.95);
}

.week-day-body {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
}

/* ==========================================================================
   WEEK CARD (SENCILLA, SIN BORDES LATERALES NI ELEMENTOS RECARGADOS)
   ========================================================================== */
.week-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
  position: relative;
  transition:
    transform 140ms ease,
    box-shadow 140ms ease,
    border-color 140ms ease;
  box-sizing: border-box;
}

.week-card:hover {
  transform: translateY(-2px);
  border-color: #cbd5e1;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.06);
}

.week-card.is-cancelled {
  opacity: 0.55;
}

.week-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.week-card-time {
  font-family: 'Public Sans', sans-serif;
  font-size: 11.5px;
  font-weight: 700;
  color: #0f172a;
}

.week-card-close-btn {
  width: 20px;
  height: 20px;
  display: grid;
  place-items: center;
  border-radius: 4px;
  color: #94a3b8;
  padding: 0;
  transition: color 150ms ease, background-color 150ms ease;
}

@media (hover: hover) and (pointer: fine) {
  .week-card-close-btn:hover {
    background: #fee2e2;
    color: #dc2626;
  }
}

.week-card-cancelled-tag {
  font-size: 9.5px;
  font-weight: 600;
  color: #64748b;
  background: #e2e8f0;
  padding: 2px 6px;
  border-radius: 4px;
}

.week-card-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.week-card-vehicle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.week-card-vehicle strong {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.week-card-reason {
  font-size: 11px;
  color: #475569;
  line-height: 1.35;
  margin: 0;
}

.week-card-client {
  font-size: 10px;
  color: #64748b;
  display: block;
}

.week-card-action {
  margin-top: 4px;
  padding-top: 6px;
  border-top: 1px solid #f1f5f9;
}

.week-start-ot-btn {
  width: 100%;
  padding: 7px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  justify-content: center;
}

.week-in-shop-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 5px 8px;
  font-size: 10px;
  font-weight: 600;
}

.week-free-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 9px 12px;
  font-size: 11px;
  font-weight: 550;
  color: #64748b;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  transition:
    background-color 150ms ease,
    border-color 150ms ease,
    color 150ms ease,
    transform 150ms var(--ease);
  margin-top: auto;
}

@media (hover: hover) and (pointer: fine) {
  .week-free-btn:hover {
    background: #ffffff;
    border-color: #3b82f6;
    color: #2563eb;
  }
}

.week-free-btn:active {
  transform: scale(0.97);
}

:global(html.dark .week-summary-bar) {
  background: rgba(36, 36, 38, 0.6);
  border-bottom-color: rgba(255, 255, 255, 0.08);
}
:global(html.dark .week-summary-info) {
  color: #ffffff;
}
:global(html.dark .week-summary-tip) {
  color: #8e8e93;
}
:global(html.dark .week-summary-tip strong) {
  color: #0a84ff;
}

:global(html.dark .week-day-column) {
  background: #202023;
  border-color: rgba(255, 255, 255, 0.08);
}
:global(html.dark .week-day-column.is-active-day) {
  background: #232326;
  border-color: #0a84ff;
  box-shadow: 0 0 0 1px #0a84ff, 0 4px 20px rgba(10, 132, 255, 0.12);
}
:global(html.dark .week-day-header) {
  background: #252528;
  border-bottom-color: rgba(255, 255, 255, 0.08);
}
:global(html.dark .week-day-column.is-active-day .week-day-header) {
  background: rgba(10, 132, 255, 0.15);
  border-bottom-color: rgba(10, 132, 255, 0.3);
}
:global(html.dark .week-day-name) {
  color: #8e8e93;
}
:global(html.dark .week-day-num) {
  color: #ffffff;
}
:global(html.dark .small-btn) {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(255, 255, 255, 0.1);
  color: #a1a1a6;
}
:global(html.dark .small-btn:hover) {
  background: rgba(255, 255, 255, 0.12);
  color: #ffffff;
}
:global(html.dark .count-bubble) {
  background: #38383c;
  border-color: rgba(255, 255, 255, 0.14);
  color: #f5f5f7;
  font-weight: 600;
}
:global(html.dark .count-bubble.has-appointments) {
  background: rgba(10, 132, 255, 0.2);
  border-color: rgba(10, 132, 255, 0.4);
  color: #64d2ff;
}

:global(html.dark .week-card) {
  background: #252528;
  border-color: rgba(255, 255, 255, 0.08);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
}
:global(html.dark .week-card:hover) {
  border-color: rgba(255, 255, 255, 0.16);
}
:global(html.dark .week-card-time) {
  color: #f5f5f7;
}
:global(html.dark .week-card-vehicle strong) {
  color: #ffffff;
}
:global(html.dark .week-card-reason) {
  color: #d1d1d6;
}
:global(html.dark .week-card-client) {
  color: #8e8e93;
}
:global(html.dark .week-card-action) {
  border-top-color: rgba(255, 255, 255, 0.06);
}
:global(html.dark .week-card-close-btn) {
  color: #8e8e93;
}
:global(html.dark .week-card-close-btn:hover) {
  background: rgba(255, 69, 58, 0.15);
  color: #ff453a;
}
:global(html.dark .week-start-ot-btn) {
  background: #0a84ff;
  color: #ffffff;
}
:global(html.dark .week-start-ot-btn:hover) {
  background: #0071e3;
}
:global(html.dark .week-in-shop-badge) {
  background: rgba(48, 209, 88, 0.15);
  border-color: rgba(48, 209, 88, 0.3);
  color: #30d158;
}
:global(html.dark .week-free-btn) {
  border-color: rgba(255, 255, 255, 0.15);
  color: #8e8e93;
}
:global(html.dark .week-free-btn:hover) {
  background: rgba(255, 255, 255, 0.06);
  border-color: #0a84ff;
  color: #0a84ff;
}
:global(html.dark .week-card-cancelled-tag) {
  background: rgba(255, 255, 255, 0.08);
  color: #8e8e93;
}

.appointment-budget-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 7px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 9999px;
  font-size: 10px;
  font-weight: 600;
  color: #475569;
  text-decoration: none;
  transition: all 0.15s ease;
}

.appointment-budget-tag:hover {
  background: #e2e8f0;
  color: #1e293b;
}

:global(html.dark .appointment-budget-tag) {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.12);
  color: #a1a1aa;
}

:global(html.dark .appointment-budget-tag:hover) {
  background: rgba(255, 255, 255, 0.14);
  color: #ffffff;
}

.week-card-budget-tag {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 1px 5px;
  background: #eff6ff;
  border: 1px solid #dbeafe;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 650;
  color: #2563eb;
  text-decoration: none;
}

.week-card-budget-tag:hover {
  background: #dbeafe;
}

:global(html.dark .week-card-budget-tag) {
  background: rgba(10, 132, 255, 0.15);
  border-color: rgba(10, 132, 255, 0.3);
  color: #64d2ff;
}

:global(html.dark .week-card-budget-tag:hover) {
  background: rgba(10, 132, 255, 0.25);
}

</style>
