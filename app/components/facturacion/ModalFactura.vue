<script setup lang="ts">
import { Check, X, Search } from 'lucide-vue-next'
import type { Invoice, Vehicle } from '~/types'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created', invoice: Invoice): void
}>()

const { db, client } = useDatabase()
const { matches } = useHelpers()
const formError = ref('')
const vehicleSearch = ref('')
const searchInputRef = ref<HTMLInputElement | null>(null)

useModalEscape(() => props.open, () => emit('close'))

const form = ref({
  vehicle: '' as string | number,
  type: 'B',
  description: '',
  total: 0,
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

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      form.value = {
        vehicle: '',
        type: 'B',
        description: '',
        total: 0,
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

  if (!form.value.description.trim() || Number(form.value.total) <= 0) {
    formError.value = 'Ingresá un concepto válido y un importe mayor a cero.'
    return
  }

  const id = Math.max(126, ...db.value.invoices.map((i) => i.id)) + 1
  const newInvoice: Invoice = {
    id,
    vehicle: Number(form.value.vehicle),
    type: form.value.type,
    description: form.value.description,
    total: Number(form.value.total),
    status: 'Pendiente',
    date: new Date().toISOString().slice(0, 10),
  }

  db.value.invoices.unshift(newInvoice)
  emit('created', newInvoice)
}
</script>

<template>
  <CommonFormPage v-if="open">
    <div class="dialog-header">
      <h2>Nuevo comprobante</h2>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <form @submit.prevent="submit" class="entry-form">
      <div class="form-fields">
        <div class="integration-notice">
          Comprobante de demostración, sin validez fiscal. No se solicitará CAE a ARCA.
        </div>

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

        <label>
          Tipo
          <select v-model="form.type">
            <option>A</option>
            <option>B</option>
            <option>C</option>
            <option>Nota de crédito</option>
            <option>Nota de débito</option>
          </select>
        </label>

        <label>
          Concepto
          <input
            v-model="form.description"
            required
            placeholder="Ej. Service de 50.000 km y cambio de aceite"
          />
        </label>

        <label>
          Importe total ($)
          <input
            v-model.number="form.total"
            type="number"
            min="1"
            required
          />
        </label>

        <p v-if="formError" class="error-message" role="alert">
          {{ formError }}
        </p>
      </div>

      <footer class="dialog-footer modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary">
          <Check :size="16" />Emitir comprobante
        </button>
      </footer>
    </form>
  </CommonFormPage>
</template>
