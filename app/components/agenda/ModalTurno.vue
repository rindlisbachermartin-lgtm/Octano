<script setup lang="ts">
import { Check, X, Search, AlertTriangle } from 'lucide-vue-next'
import type { Appointment, Vehicle } from '~/types'
import { validateAppointment, appointmentsOverlap } from '~/utils/appointments'
import { isScheduledAppointment } from '~/utils/appointmentLifecycle'

const props = defineProps<{
  open: boolean
  defaultDate?: string
  defaultTime?: string
  appointment?: Appointment | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created', appointment: Appointment): void
  (e: 'updated', appointment: Appointment): void
}>()

const { db, client, issueAppointmentOrder, syncAppointmentOrder } = useDatabase()
const { matches } = useHelpers()
const formError = ref('')
const vehicleSearch = ref('')
const debouncedSearch = useDebouncedValue(vehicleSearch)
const searchInputRef = ref<HTMLInputElement | null>(null)
const showOverlapPrompt = ref(false)
const { today } = useWorkshopDay()

useModalEscape(() => props.open, () => emit('close'))

const form = ref({
  vehicle: '' as string | number,
  date: props.defaultDate || today.value,
  time: '11:00',
  endTime: '',
  reason: '',
  budgetId: '' as string | number,
})

const availableBudgets = computed(() => db.value.quotes.filter((q) =>
  q.vehicle === Number(form.value.vehicle) &&
  (q.id === props.appointment?.budgetId || (!q.orderId && !q.appointmentId && !['En taller', 'Convertido', 'Archivado'].includes(q.status)))
))
watch(() => form.value.vehicle, () => {
  if (!availableBudgets.value.some((q) => q.id === Number(form.value.budgetId))) form.value.budgetId = ''
})
watch(() => form.value.budgetId, (id) => {
  const budget = availableBudgets.value.find((q) => q.id === Number(id))
  if (budget && budget.id !== props.appointment?.budgetId) form.value.reason = budget.description
})

const selectedVehicle = computed(() =>
  db.value.vehicles.find((v) => v.id === Number(form.value.vehicle))
)

const filteredVehicles = computed(() => {
  const query = debouncedSearch.value.trim()
  if (!query) return []
  return db.value.vehicles.filter((v) => {
    const c = client(v.client)
    return matches(query, v.plate, v.brand, v.model, c?.name, c?.doc, c?.phone)
  })
})

const overlappingAppointment = computed(() => {
  if (!form.value.date || !form.value.time) return null
  return (
    db.value.appointments.find(
      (a) =>
        a.id !== props.appointment?.id &&
        a.date === form.value.date &&
        appointmentsOverlap(a, form.value) &&
        !['Cancelado', 'No asistió'].includes(a.status)
    ) || null
  )
})

const overlappingVehicle = computed(() => {
  if (!overlappingAppointment.value) return null
  return db.value.vehicles.find((v) => v.id === overlappingAppointment.value?.vehicle)
})

const overlappingClient = computed(() => {
  if (!overlappingVehicle.value) return null
  return client(overlappingVehicle.value.client)
})

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      form.value = {
        vehicle: props.appointment?.vehicle || '',
        date: props.appointment?.date || props.defaultDate || today.value,
        time: props.appointment?.time || props.defaultTime || '11:00',
        endTime: props.appointment?.endTime || '',
        reason: props.appointment?.reason || '',
        budgetId: props.appointment?.budgetId || '',
      }
      vehicleSearch.value = ''
      formError.value = ''
      showOverlapPrompt.value = false
      nextTick(() => {
        searchInputRef.value?.focus()
      })
    }
  }
)

watch(
  () => [form.value.vehicle, form.value.date, form.value.time, form.value.endTime, form.value.reason, form.value.budgetId],
  () => {
    showOverlapPrompt.value = false
  }
)

function selectVehicle(v: Vehicle) {
  form.value.vehicle = v.id
  vehicleSearch.value = ''
  formError.value = ''
}

function clearVehicle() {
  form.value.vehicle = ''
  vehicleSearch.value = ''
  nextTick(() => {
    searchInputRef.value?.focus()
  })
}

function submit(allowOverlap = false) {
  formError.value = ''
  if (props.appointment && !isScheduledAppointment(props.appointment)) {
    formError.value = 'Este turno ya no está pendiente de ingreso. Cerrá el formulario y agendá uno nuevo si corresponde.'
    showOverlapPrompt.value = false
    return
  }
  if (!form.value.vehicle) {
    formError.value = 'Por favor buscá y seleccioná un vehículo / cliente titular.'
    return
  }

  if (!form.value.reason.trim()) {
    formError.value = 'Ingresá el motivo de la visita.'
    return
  }

  formError.value = validateAppointment(db.value.appointments, { ...form.value, vehicle: Number(form.value.vehicle) }, props.appointment?.id)
  if (formError.value) {
    showOverlapPrompt.value = false
    return
  }
  if (new Date(`${form.value.date}T${form.value.endTime}:00-03:00`).getTime() < Date.now()) {
    formError.value = 'El fin del turno debe ser posterior al momento actual.'
    showOverlapPrompt.value = false
    return
  }
  if (form.value.budgetId && !availableBudgets.value.some((q) => q.id === Number(form.value.budgetId))) {
    formError.value = 'Elegí un presupuesto disponible para este vehículo.'
    return
  }

  // Si hay superposición con otro turno, advertir y solicitar confirmación
  if (overlappingAppointment.value && !allowOverlap) {
    showOverlapPrompt.value = true
    return
  }

  saveAppointment()
}

function saveAppointment() {
  if (props.appointment) {
    const previousBudgetId = props.appointment.budgetId
    Object.assign(props.appointment, {
      vehicle: Number(form.value.vehicle),
      date: form.value.date,
      time: form.value.time,
      endTime: form.value.endTime,
      reason: form.value.reason.trim(),
      budgetId: Number(form.value.budgetId) || null,
    })
    syncAppointmentOrder(props.appointment, previousBudgetId)
    emit('updated', props.appointment)
    return
  }
  const newAppointment: Appointment = {
    id: Date.now(),
    vehicle: Number(form.value.vehicle),
    date: form.value.date,
    time: form.value.time,
    endTime: form.value.endTime,
    reason: form.value.reason.trim(),
    status: 'Programado',
    budgetId: Number(form.value.budgetId) || null,
  }

  db.value.appointments.push(newAppointment)
  issueAppointmentOrder(newAppointment)
  showOverlapPrompt.value = false
  emit('created', newAppointment)
}
</script>

<template>
  <CommonFormPage v-if="open" class="appointment-form-page">
    <div class="dialog-header">
      <h2>{{ appointment ? 'Editar turno' : 'Agendar un turno' }}</h2>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <form @submit.prevent="submit()" class="entry-form">
      <div class="form-fields">
        <!-- Vehicle / Client Search Selector -->
        <div class="field-block">
          <label class="block-label">
            Vehículo y cliente titular <span class="required-star">*</span>
          </label>

          <!-- Selected Vehicle Card -->
          <div v-if="selectedVehicle" class="selected-target-card">
            <span class="plate">{{ selectedVehicle.plate }}</span>
            <div class="target-info">
              <strong class="target-title">{{ selectedVehicle.brand }} {{ selectedVehicle.model }}</strong>
              <div class="target-sub">
                <span class="target-owner-label">Titular:</span>
                <strong class="target-owner-name">{{ client(selectedVehicle.client)?.name }}</strong>
                <template v-if="client(selectedVehicle.client)?.doc">
                  <span class="target-dot">·</span>
                  <span class="target-doc">DNI {{ client(selectedVehicle.client)?.doc }}</span>
                </template>
                <template v-if="client(selectedVehicle.client)?.phone">
                  <span class="target-dot">·</span>
                  <span class="target-phone">Tel. {{ client(selectedVehicle.client)?.phone }}</span>
                </template>
              </div>
            </div>
            <button
              type="button"
              class="button small change-target-btn"
              @click="clearVehicle"
            >
              Cambiar
            </button>
          </div>

          <!-- Vehicle / Client Search Input & Results -->
          <div v-else class="target-search-container">
            <div class="target-search-box">
              <Search :size="16" class="search-icon" />
              <input
                ref="searchInputRef"
                v-model="vehicleSearch"
                type="text"
                placeholder="Buscá por cliente, DNI, patente o modelo…"
                autocomplete="off"
              />
              <button
                v-if="vehicleSearch"
                type="button"
                class="icon-button clear-icon-btn"
                @click="vehicleSearch = ''"
                aria-label="Limpiar búsqueda"
              >
                <X :size="14" />
              </button>
            </div>

            <!-- Results Dropdown -->
            <p v-if="!vehicleSearch.trim()" class="muted search-hint">Empezá a escribir para buscar vehículos.</p>
            <div v-else class="target-results-list">
              <div
                v-for="v in filteredVehicles"
                :key="v.id"
                class="target-result-item"
                @click="selectVehicle(v)"
              >
                <span class="plate small-plate">{{ v.plate }}</span>
                <div class="result-details">
                  <strong class="result-name">{{ v.brand }} {{ v.model }}</strong>
                  <span class="result-meta">
                    Cliente: <strong>{{ client(v.client)?.name }}</strong>
                    <template v-if="client(v.client)?.doc">
                      · DNI {{ client(v.client)?.doc }}
                    </template>
                  </span>
                </div>
              </div>

              <div v-if="!filteredVehicles.length && vehicleSearch.trim() === debouncedSearch.trim()" class="target-empty-state">
                <p>No se encontraron vehículos ni clientes para "<strong>{{ vehicleSearch }}</strong>"</p>
                <small>Podés verificar los datos o dar de alta el vehículo en la sección Vehículos.</small>
              </div>
            </div>
          </div>

          <!-- Hidden select for accessibility and test compatibility -->
          <select v-model="form.vehicle" aria-label="Vehículo" class="sr-only">
            <option value="">Seleccionar vehículo...</option>
            <option v-for="v in db.vehicles" :key="v.id" :value="v.id">
              {{ v.plate }} · {{ v.brand }} {{ v.model }} — {{ client(v.client)?.name }}
            </option>
          </select>
        </div>

        <CommonDependentFields :ready="!!selectedVehicle">
        <label>
          Presupuesto (opcional)
          <select v-model="form.budgetId" :disabled="!selectedVehicle">
            <option value="">Sin presupuesto</option>
            <option v-for="budget in availableBudgets" :key="budget.id" :value="budget.id">#{{ budget.id }} · {{ budget.description }}</option>
          </select>
        </label>
        <p class="muted">Se emitirá una orden pendiente de ingreso. Al llegar el auto, registrá su ingreso para habilitarla al mecánico. Si no ingresa antes del fin del turno, se dará de baja.</p>

        <div class="form-grid">
          <CommonDatePicker v-model="form.date" label="Fecha" compact />
          <label>
            Hora de inicio estimada
            <input v-model="form.time" type="time" required />
          </label>
          <label>
            Hora de fin estimada
            <input v-model="form.endTime" type="time" required />
          </label>
        </div>
        <p class="muted">Podés agendar varios vehículos en el mismo horario si tenés puestos de atención disponibles. Si hay superposición, te pediremos confirmarla.</p>

        <label>
          Motivo de la visita
          <input
            v-model="form.reason"
            required
            placeholder="Ej. Service de 10.000 km y revisión de frenos"
            maxlength="120"
          />
        </label>

        <!-- Cuadro de confirmación compacto y neutral por superposición de turnos -->
        <div v-if="showOverlapPrompt" class="overlap-confirm-prompt">
          <div class="prompt-header">
            <AlertTriangle :size="15" class="warning-icon" />
            <span>El horario se superpone con el turno de <strong>{{ overlappingVehicle?.plate }}</strong> a las <strong>{{ overlappingAppointment?.time }} hs</strong>. Confirmá otro turno si tenés un puesto de atención disponible.</span>
          </div>
          <div class="prompt-actions">
            <button type="button" class="button small" @click="showOverlapPrompt = false">
              Cambiar horario
            </button>
            <button type="button" class="button small primary" @click="submit(true)">
              <Check :size="14" /> Confirmar turno simultáneo
            </button>
          </div>
        </div>

        </CommonDependentFields>
        <p v-if="formError" class="error-message" role="alert">
          {{ formError }}
        </p>
      </div>

      <footer v-if="!showOverlapPrompt" class="dialog-footer modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary" :disabled="!selectedVehicle">
          <Check :size="16" />{{ appointment ? 'Guardar cambios' : 'Confirmar turno' }}
        </button>
      </footer>
    </form>
  </CommonFormPage>
</template>

<style scoped>
.overlap-confirm-prompt {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  flex-wrap: wrap;
}

:global(html.dark .overlap-confirm-prompt) {
  background: #202023;
  border-color: rgba(255, 255, 255, 0.1);
  color: #f1f5f9;
}

.prompt-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #334155;
  flex: 1;
  min-width: 200px;
}

:global(html.dark .prompt-header) {
  color: #cbd5e1;
}

.prompt-header .warning-icon {
  color: #64748b;
  flex-shrink: 0;
}

:global(html.dark .prompt-header .warning-icon) {
  color: #94a3b8;
}

.prompt-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
</style>
