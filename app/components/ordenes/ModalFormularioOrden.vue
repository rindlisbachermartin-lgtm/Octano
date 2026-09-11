<script setup lang="ts">
import { Check, X, Search } from 'lucide-vue-next'
import type { Vehicle } from '~/types'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created', orderId: number): void
}>()

const { db, client, createOrder } = useDatabase()
const { matches } = useHelpers()
const formError = ref('')
const vehicleSearch = ref('')
const searchInputRef = ref<HTMLInputElement | null>(null)

useModalEscape(() => props.open, () => emit('close'))

const form = ref({
  vehicle: '' as string | number,
  service: '',
  mechanic: 'Nicolás',
  bay: '' as string | number,
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
        service: '',
        mechanic: 'Nicolás',
        bay: '',
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

  if (!form.value.service.trim()) {
    formError.value = 'Por favor ingresá el trabajo a realizar.'
    return
  }

  if (form.value.bay && db.value.orders.some((o) => o.bay === Number(form.value.bay))) {
    formError.value = 'Ese puesto está ocupado. Elegí un puesto libre o dejalo sin asignar.'
    return
  }

  const id = createOrder(
    Number(form.value.vehicle),
    form.value.service,
    form.value.mechanic,
    form.value.bay ? Number(form.value.bay) : null
  )

  emit('created', id)
}
</script>

<template>
  <dialog v-if="open" class="dialog" open>
    <div class="dialog-header">
      <h2>Nueva orden de trabajo</h2>
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

        <label>
          Trabajo a realizar
          <input
            v-model="form.service"
            required
            maxlength="100"
            placeholder="Ej. Service de los 70.000 km"
          />
        </label>

        <div class="form-grid">
          <label>
            Mecánico
            <select v-model="form.mechanic">
              <option>Nicolás</option>
              <option>Santiago</option>
            </select>
          </label>
          <label>
            Puesto
            <select v-model="form.bay">
              <option value="">Asignar después</option>
              <option
                v-for="n in 4"
                :key="n"
                :value="n"
                :disabled="db.orders.some((o) => o.bay === n)"
              >
                Puesto 0{{ n }}{{ db.orders.some((o) => o.bay === n) ? ' · Ocupado' : ' · Disponible' }}
              </option>
            </select>
          </label>
        </div>

        <p class="form-hint">
          La orden se creará en espera. Podrás agregar el diagnóstico, las tareas y las fotos desde su detalle.
        </p>

        <p v-if="formError" class="error-message" role="alert">
          {{ formError }}
        </p>
      </div>

      <footer class="dialog-footer modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary">
          <Check :size="16" />Crear orden
        </button>
      </footer>
    </form>
  </dialog>
</template>
