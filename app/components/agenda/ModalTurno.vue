<script setup lang="ts">
import { Check, X, Search } from 'lucide-vue-next'
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

useModalEscape(() => props.open, () => emit('close'))

const form = ref({
  vehicle: '' as string | number,
  date: props.defaultDate || '2026-09-07',
  time: '11:00',
  reason: '',
})

const selectedVehicle = computed(() =>
  db.value.vehicles.find((v) => v.id === Number(form.value.vehicle))
)

const filteredVehicles = computed(() => {
  const query = vehicleSearch.value.trim()
  if (!query) return db.value.vehicles
  return db.value.vehicles.filter((v) => {
    const c = client(v.client)
    return matches(query, v.plate, v.brand, v.model, c?.name, c?.doc, c?.phone)
  })
})

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      form.value = {
        vehicle: '',
        date: props.defaultDate || '2026-09-07',
        time: '11:00',
        reason: '',
      }
      vehicleSearch.value = ''
      formError.value = ''
      nextTick(() => {
        searchInputRef.value?.focus()
      })
    }
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

  // Check appointment slot overlap
  if (
    db.value.appointments.some(
      (a) =>
        a.date === form.value.date &&
        a.time === form.value.time &&
        a.status !== 'Cancelado'
    )
  ) {
    formError.value = 'Ese horario ya está ocupado. Elegí otro turno.'
    return
  }

  const newAppointment: Appointment = {
    id: Date.now(),
    vehicle: Number(form.value.vehicle),
    date: form.value.date,
    time: form.value.time,
    reason: form.value.reason,
    status: 'Programado',
  }

  db.value.appointments.push(newAppointment)
  emit('created', newAppointment)
}
</script>

<template>
  <dialog v-if="open" class="dialog" open>
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
            <div class="target-results-list">
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
          <label>
            Fecha
            <input type="date" v-model="form.date" required />
          </label>
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

        <p class="form-hint">
          Turnos de 30 minutos. No se permite reservar dos turnos en el mismo horario.
        </p>

        <p v-if="formError" class="error-message" role="alert">
          {{ formError }}
        </p>
      </div>

      <footer class="dialog-footer modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary">
          <Check :size="16" />Confirmar turno
        </button>
      </footer>
    </form>
  </dialog>
</template>
