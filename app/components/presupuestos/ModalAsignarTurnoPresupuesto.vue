<script setup lang="ts">
import { CalendarDays, Check, X, Clock, Calendar, CarFront, User, FileText, AlertTriangle } from 'lucide-vue-next'
import type { Budget, Appointment } from '~/types'
import { validateAppointment, appointmentsOverlap } from '~/utils/appointments'

const props = defineProps<{
  open: boolean
  budget: Budget | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'assigned', appointment: Appointment): void
}>()

const { db, vehicle, vehicleName, owner, issueAppointmentOrder } = useDatabase()
const { notify } = useWorkshopToast()

useModalEscape(() => props.open, () => emit('close'))

const { today } = useWorkshopDay()
const formDate = ref(today.value)
const formTime = ref('10:00')
const formEndTime = ref('')
const formError = ref('')
const showOverlapPrompt = ref(false)

const currentVehicle = computed(() =>
  props.budget ? vehicle(props.budget.vehicle) : null
)

const currentClient = computed(() =>
  props.budget ? owner(props.budget.vehicle) : null
)

const overlappingAppointment = computed(() => {
  if (!formDate.value || !formTime.value) return null
  return (
    db.value.appointments.find(
      (a) =>
        a.date === formDate.value &&
        appointmentsOverlap(a, { time: formTime.value, endTime: formEndTime.value }) &&
        !['Cancelado', 'No asistió'].includes(a.status)
    ) || null
  )
})

const overlappingVehicle = computed(() => {
  if (!overlappingAppointment.value) return null
  return vehicle(overlappingAppointment.value.vehicle)
})

const overlappingClient = computed(() => {
  if (!overlappingAppointment.value) return null
  return owner(overlappingAppointment.value.vehicle)
})

watch(
  () => [props.open, props.budget],
  ([isOpen]) => {
    if (isOpen) {
      formDate.value = today.value
      formTime.value = '10:00'
      formEndTime.value = ''
      formError.value = ''
      showOverlapPrompt.value = false
    }
  }
)

watch(
  () => [formDate.value, formTime.value, formEndTime.value],
  () => {
    showOverlapPrompt.value = false
  }
)

function submit(allowOverlap = false) {
  formError.value = ''
  if (!props.budget) return

  formError.value = validateAppointment(db.value.appointments, {
    vehicle: props.budget.vehicle, date: formDate.value, time: formTime.value, endTime: formEndTime.value,
  })
  if (formError.value) {
    showOverlapPrompt.value = false
    return
  }
  if (new Date(`${formDate.value}T${formEndTime.value}:00-03:00`).getTime() < Date.now()) {
    formError.value = 'El fin del turno debe ser posterior al momento actual.'
    showOverlapPrompt.value = false
    return
  }
  if (props.budget.orderId || props.budget.appointmentId || ['En taller', 'Convertido', 'Archivado'].includes(props.budget.status)) {
    formError.value = 'Este presupuesto ya tiene un turno u orden asociados o está archivado.'
    showOverlapPrompt.value = false
    return
  }

  // Si hay superposición de turnos, advertir y consultar al usuario
  if (overlappingAppointment.value && !allowOverlap) {
    showOverlapPrompt.value = true
    return
  }

  confirmAssign()
}

function confirmAssign() {
  if (!props.budget) return

  const newAppointment: Appointment = {
    id: Date.now(),
    vehicle: props.budget.vehicle,
    date: formDate.value,
    time: formTime.value,
    endTime: formEndTime.value,
    reason: props.budget.description,
    status: 'Programado',
    budgetId: props.budget.id,
  }

  db.value.appointments.push(newAppointment)
  issueAppointmentOrder(newAppointment)

  showOverlapPrompt.value = false
  notify(`Turno agendado para el ${formDate.value} a las ${formTime.value} hs.`)
  emit('assigned', newAppointment)
  emit('close')
}
</script>

<template>
  <CommonFormPage v-if="open && budget" class="appointment-form-page">
    <div class="dialog-header">
      <div>
        <span class="eyebrow">PRESUPUESTOS / PASO 2</span>
        <h2>Asignar turno para este presupuesto</h2>
      </div>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <form @submit.prevent="submit()" class="entry-form">
      <div class="form-fields">
        <!-- Target Info Box -->
        <div class="selected-target-card">
          <span class="plate">{{ currentVehicle?.plate }}</span>
          <div class="target-info">
            <strong class="target-title">{{ vehicleName(budget.vehicle) }}</strong>
            <div class="target-sub">
              <span class="target-owner-label">Titular:</span>
              <strong class="target-owner-name">{{ currentClient?.name }}</strong>
              <template v-if="currentClient?.phone">
                <span class="target-dot">·</span>
                <span class="target-phone">Tel. {{ currentClient.phone }}</span>
              </template>
            </div>
          </div>
        </div>

        <div class="budget-work-box">
          <span class="work-label">Trabajo presupuestado (#{{ budget.id }}):</span>
          <p class="work-desc">{{ budget.description }}</p>
        </div>

        <!-- Date & Time Picker -->
        <div class="form-grid">
          <CommonDatePicker v-model="formDate" label="Fecha del turno" />

          <label>
            Hora de inicio estimada
            <input v-model="formTime" type="time" required />
          </label>
          <label>
            Hora de fin estimada
            <input v-model="formEndTime" type="time" required />
          </label>
        </div>

        <div class="workflow-hint">
          <CalendarDays :size="20" class="hint-icon" />
          <p>
            Al agendar se emite una orden <strong>pendiente de ingreso</strong>. Cuando llegue el auto, seleccioná <strong>Registrar ingreso</strong> en Turnos para habilitarla al mecánico. Si pasa el fin del turno sin ingreso, la orden se dará de baja.
          </p>
        </div>

        <!-- Diálogo de confirmación compacto y neutral por superposición -->
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

        <p v-if="formError" class="error-message" role="alert">
          {{ formError }}
        </p>
      </div>

      <footer v-if="!showOverlapPrompt" class="dialog-footer modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary">
          <Check :size="16" /> Confirmar y agendar turno
        </button>
      </footer>
    </form>
  </CommonFormPage>
</template>

<style scoped>
.budget-work-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 14px;
}

.work-label {
  font-size: 10px;
  font-weight: 700;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.work-desc {
  font-size: 12px;
  color: #0f172a;
  margin: 3px 0 0;
  font-weight: 500;
}

.workflow-hint {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  background: transparent;
  border: 0;
  border-radius: 8px;
  padding: 12px 14px;
  font-size: 11px;
  color: #1e40af;
  line-height: 1.45;
}

.workflow-hint p {
  margin: 0;
}

.hint-icon {
  color: #2563eb;
  flex-shrink: 0;
  margin-top: 1px;
}

:global(html.dark .budget-work-box) {
  background: #202023;
  border-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark .work-desc) {
  color: #f5f5f7;
}

:global(html.dark .workflow-hint) {
  background: transparent;
  border: 0;
  color: #64d2ff;
}

:global(html.dark .hint-icon) {
  color: #0a84ff;
}

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
