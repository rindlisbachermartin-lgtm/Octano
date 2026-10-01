<script setup lang="ts">
import { Check, X, Search, AlertTriangle } from 'lucide-vue-next'
import type { Appointment, Vehicle } from '~/types'

const props = defineProps<{
  open: boolean
  defaultDate?: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created', appointment: Appointment): void
}>()

const { db, client } = useDatabase()
const { matches } = useHelpers()
const formError = ref('')
const vehicleSearch = ref('')
const searchInputRef = ref<HTMLInputElement | null>(null)
const showOverlapPrompt = ref(false)
const { today } = useWorkshopDay()

useModalEscape(() => props.open, () => emit('close'))

const form = ref({
  vehicle: '' as string | number,
  date: props.defaultDate || today.value,
  time: '11:00',
  reason: '',
})

const selectedVehicle = computed(() =>
  db.value.vehicles.find((v) => v.id === Number(form.value.vehicle))
)

const filteredVehicles = computed(() => {
  const query = vehicleSearch.value.trim()
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
        a.date === form.value.date &&
        a.time === form.value.time &&
        a.status !== 'Cancelado'
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
        vehicle: '',
        date: props.defaultDate || today.value,
        time: '11:00',
        reason: '',
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
  () => [form.value.date, form.value.time],
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

function submit() {
  formError.value = ''
  if (!form.value.vehicle) {
    formError.value = 'Por favor buscá y seleccioná un vehículo / cliente titular.'
    return
  }

  if (!form.value.reason.trim()) {
    formError.value = 'Ingresá el motivo de la visita.'
    return
  }

  // Si hay superposición con otro turno, advertir y solicitar confirmación
  if (overlappingAppointment.value && !showOverlapPrompt.value) {
    showOverlapPrompt.value = true
    return
  }

  saveAppointment()
}

function saveAppointment() {
  const newAppointment: Appointment = {
    id: Date.now(),
    vehicle: Number(form.value.vehicle),
    date: form.value.date,
    time: form.value.time,
    reason: form.value.reason,
    status: 'Programado',
  }

  db.value.appointments.push(newAppointment)
  showOverlapPrompt.value = false
  emit('created', newAppointment)
}
</script>

<template>
  <CommonFormPage v-if="open">
    <div class="dialog-header">
      <h2>Agendar un turno</h2>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <form @submit.prevent="submit" class="entry-form">
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
              <strong>{{ selectedVehicle.brand }} {{ selectedVehicle.model }}</strong>
              <small class="target-sub">
                Titular: <strong>{{ client(selectedVehicle.client)?.name }}</strong>
                <template v-if="client(selectedVehicle.client)?.doc">
                  · DNI {{ client(selectedVehicle.client)?.doc }}
                </template>
                <template v-if="client(selectedVehicle.client)?.phone">
                  · Tel. {{ client(selectedVehicle.client)?.phone }}
                </template>
              </small>
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
                <button type="button" class="select-chip">Seleccionar</button>
              </div>

              <div v-if="!filteredVehicles.length" class="target-empty-state">
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

        <div class="form-grid">
          <CommonDatePicker v-model="form.date" label="Fecha" compact />
          <label>
            Hora
            <select v-model="form.time">
              <option
                v-for="time in [
                  '08:00', '08:30', '09:00', '09:30', '10:00', '10:30',
                  '11:00', '11:30', '12:00', '12:30', '13:00', '13:30',
                  '14:00', '14:30', '15:00', '15:30', '16:00', '16:30', '17:00'
                ]"
                :key="time"
              >
                {{ time }}
              </option>
            </select>
          </label>
        </div>

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
            <span>Ya existe un turno a las <strong>{{ form.time }} hs</strong> para <strong>{{ overlappingVehicle?.plate }}</strong> ({{ overlappingVehicle?.brand }} {{ overlappingVehicle?.model }}). ¿Asignar igualmente?</span>
          </div>
          <div class="prompt-actions">
            <button type="button" class="button small" @click="showOverlapPrompt = false">
              Cambiar horario
            </button>
            <button type="button" class="button small primary" @click="saveAppointment">
              <Check :size="14" /> Asignar igual
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
          <Check :size="16" />Confirmar turno
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
