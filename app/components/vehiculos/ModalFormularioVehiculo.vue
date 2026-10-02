<script setup lang="ts">
import { Check, X, Search } from 'lucide-vue-next'
import type { Vehicle, Client } from '~/types'

const props = defineProps<{
  open: boolean
  vehicle?: Vehicle | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created', vehicle: Vehicle): void
  (e: 'updated', vehicle: Vehicle): void
}>()

const { db } = useDatabase()
const { matches } = useHelpers()
const formError = ref('')
const clientSearch = ref('')
const debouncedSearch = useDebouncedValue(clientSearch)
const searchInputRef = ref<HTMLInputElement | null>(null)
const catalogField = ref<'brand' | 'model' | 'engine' | null>(null)
const catalogValue = ref('')
const catalogError = ref('')
const catalogLabels = { brand: 'marca', model: 'modelo', engine: 'motorización' }

useModalEscape(() => props.open, () => emit('close'))

const form = ref({
  client: '' as string | number,
  plate: '',
  brand: '',
  model: '',
  year: 2026,
  engine: '',
  km: 0,
})

const catalog = computed(() => [...(db.value.vehicleCatalog || []), ...db.value.vehicles])
const brands = computed(() => vehicleCatalogOptions(catalog.value, 'brand'))
const models = computed(() => vehicleCatalogOptions(catalog.value, 'model', form.value.brand))
const engines = computed(() => vehicleCatalogOptions(catalog.value, 'engine', form.value.brand, form.value.model))

function changeBrand() {
  form.value.model = ''
  form.value.engine = ''
  catalogField.value = null
}

function changeModel() {
  form.value.engine = ''
  catalogField.value = null
}

function openCatalogEntry(field: 'brand' | 'model' | 'engine') {
  catalogField.value = field
  catalogValue.value = ''
  catalogError.value = ''
}

function addCatalogEntry() {
  const field = catalogField.value
  const value = catalogValue.value.trim().replace(/\s+/g, ' ')
  if (!field || !value) {
    catalogError.value = 'Ingresá un nombre para agregar al catálogo.'
    return
  }
  if ((field !== 'brand' && !form.value.brand) || (field === 'engine' && !form.value.model)) return
  const options = field === 'brand' ? brands.value : field === 'model' ? models.value : engines.value
  const existing = options.find((option) => catalogKey(option) === catalogKey(value))
  if (!existing) {
    db.value.vehicleCatalog ??= []
    db.value.vehicleCatalog.push({
      brand: field === 'brand' ? value : form.value.brand,
      model: field === 'model' ? value : field === 'engine' ? form.value.model : '',
      engine: field === 'engine' ? value : '',
    })
  }
  form.value[field] = existing || value
  if (field === 'brand') changeBrand()
  if (field === 'model') changeModel()
  catalogField.value = null
}

const selectedClient = computed(() =>
  db.value.clients.find((c) => c.id === Number(form.value.client))
)

const filteredClients = computed(() => {
  const query = debouncedSearch.value.trim()
  const active = db.value.clients.filter((c) => c.active)
  if (!query) return []
  return active.filter((c) =>
    matches(query, c.name, c.doc, c.phone, c.email)
  )
})

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      form.value = props.vehicle ? {
        client: props.vehicle.client,
        plate: props.vehicle.plate,
        brand: props.vehicle.brand,
        model: props.vehicle.model,
        year: props.vehicle.year,
        engine: props.vehicle.engine,
        km: props.vehicle.km,
      } : {
        client: '',
        plate: '',
        brand: '',
        model: '',
        year: 2026,
        engine: '',
        km: 0,
      }
      form.value.brand = brands.value.find((value) => catalogKey(value) === catalogKey(form.value.brand)) || form.value.brand
      form.value.model = models.value.find((value) => catalogKey(value) === catalogKey(form.value.model)) || form.value.model
      form.value.engine = engines.value.find((value) => catalogKey(value) === catalogKey(form.value.engine)) || form.value.engine
      clientSearch.value = ''
      formError.value = ''
      catalogField.value = null
      nextTick(() => {
        searchInputRef.value?.focus()
      })
    }
  }
)

function selectClient(client: Client) {
  form.value.client = client.id
  clientSearch.value = ''
  formError.value = ''
}

function clearClient() {
  form.value.client = ''
  clientSearch.value = ''
  nextTick(() => {
    searchInputRef.value?.focus()
  })
}

function submit() {
  formError.value = ''
  if (!form.value.client) {
    formError.value = 'Por favor buscá y seleccioná un cliente titular.'
    return
  }

  const rawPlate = form.value.plate.toUpperCase().trim()
  if (!/^(?:[A-Z]{3}[- ]?\d{3}|[A-Z]{2}[- ]?\d{3}[- ]?[A-Z]{2})$/.test(rawPlate)) {
    formError.value = 'Ingresá una patente válida: XXX-999 o XX-999-XX.'
    return
  }
  const compactPlate = rawPlate.replace(/[- ]/g, '')
  const plate = compactPlate.length === 6
    ? `${compactPlate.slice(0, 3)}-${compactPlate.slice(3)}`
    : `${compactPlate.slice(0, 2)}-${compactPlate.slice(2, 5)}-${compactPlate.slice(5)}`
  if (!plate || !form.value.brand.trim() || !form.value.model.trim()) {
    formError.value = 'Por favor completá los campos obligatorios.'
    return
  }

  if (!brands.value.includes(form.value.brand) || !models.value.includes(form.value.model)
    || (form.value.engine && !engines.value.includes(form.value.engine))) {
    formError.value = 'Seleccioná una marca, modelo y motorización del catálogo.'
    return
  }

  if (db.value.vehicles.some((v) => v.id !== props.vehicle?.id && v.plate.toUpperCase().replace(/[-\s]/g, '') === compactPlate)) {
    formError.value = 'Esta patente ya está registrada.'
    return
  }

  if (!selectedClient.value || (!selectedClient.value.active && selectedClient.value.id !== props.vehicle?.client)) {
    formError.value = 'Seleccioná un cliente activo.'
    return
  }
  if (!Number.isInteger(Number(form.value.year)) || form.value.year < 1900 || form.value.year > new Date().getFullYear() + 1
    || !Number.isInteger(Number(form.value.km)) || form.value.km < 0 || form.value.km > 9999999) {
    formError.value = 'Revisá el año y el kilometraje: deben ser números enteros dentro del rango permitido.'
    return
  }

  if (props.vehicle) {
    const nextOwner = Number(form.value.client)
    if (props.vehicle.client !== nextOwner) {
      props.vehicle.ownershipHistory ??= []
      props.vehicle.ownershipHistory.push({ from: props.vehicle.client, to: nextOwner, date: new Date().toISOString() })
    }
    Object.assign(props.vehicle, {
      ...form.value, plate, client: Number(form.value.client),
      brand: form.value.brand.trim(), model: form.value.model.trim(), engine: form.value.engine.trim(),
    })
    emit('updated', props.vehicle)
    return
  }

  const newVehicle: Vehicle = {
    id: Date.now(),
    client: Number(form.value.client),
    plate,
    brand: form.value.brand.trim(),
    model: form.value.model.trim(),
    year: Number(form.value.year),
    engine: form.value.engine.trim(),
    km: Number(form.value.km) || 0,
    color: '#bbc5b7',
    qrCode: null,
  }

  db.value.vehicles.push(newVehicle)
  emit('created', newVehicle)
}
</script>

<template>
  <CommonFormPage v-if="open">
    <div class="dialog-header">
      <h2>{{ vehicle ? 'Editar vehículo' : 'Registrar vehículo' }}</h2>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <form @submit.prevent="submit" class="entry-form" novalidate>
      <div class="form-fields">
        <p class="muted">Los campos marcados con * son obligatorios.</p>
        <!-- Client Search Field -->
        <div class="field-block">
          <label class="block-label">
            Cliente titular <span class="required-star">*</span>
          </label>

          <!-- Selected Client Card -->
          <div v-if="selectedClient" class="selected-client-card">
            <div class="client-info">
              <strong>{{ selectedClient.name }}</strong>
              <small class="client-sub">
                {{ selectedClient.doc ? 'DNI ' + selectedClient.doc : '' }}
                {{ selectedClient.phone ? ' · Tel. ' + selectedClient.phone : '' }}
              </small>
            </div>
            <button
              type="button"
              class="button small change-client-btn"
              @click="clearClient"
            >
              Cambiar
            </button>
          </div>

          <!-- Client Search Input & Results -->
          <div v-else class="client-search-container">
            <div class="client-search-box">
              <Search :size="16" class="search-icon" />
              <input
                ref="searchInputRef"
                v-model="clientSearch"
                type="text"
                placeholder="Escribí para buscar por nombre, DNI o teléfono..."
                autocomplete="off"
              />
              <button
                v-if="clientSearch"
                type="button"
                class="icon-button clear-icon-btn"
                @click="clientSearch = ''"
                aria-label="Limpiar búsqueda"
              >
                <X :size="14" />
              </button>
            </div>

            <!-- Client Results List -->
            <p v-if="!clientSearch.trim()" class="muted search-hint">Empezá a escribir para buscar clientes.</p>
            <div v-else class="client-results-list">
              <div
                v-for="c in filteredClients"
                :key="c.id"
                class="client-result-item"
                @click="selectClient(c)"
              >
                <div class="result-details">
                  <strong class="result-name">{{ c.name }}</strong>
                  <span class="result-meta">
                    {{ c.doc ? 'DNI ' + c.doc : '' }}
                    {{ c.phone ? ' · ' + c.phone : '' }}
                    {{ c.email ? ' · ' + c.email : '' }}
                  </span>
                </div>
              </div>

              <div v-if="!filteredClients.length && clientSearch.trim() === debouncedSearch.trim()" class="client-empty-state">
                <p>No se encontraron clientes para "<strong>{{ clientSearch }}</strong>"</p>
                <small>Podés verificar el nombre o dar de alta al cliente en la sección Clientes.</small>
              </div>
            </div>
          </div>

          <!-- Hidden select for form bindings and test compatibility -->
          <select v-model="form.client" aria-label="Cliente" class="sr-only">
            <option value="">Seleccionar cliente...</option>
            <option
              v-for="c in db.clients.filter((c) => c.active)"
              :key="c.id"
              :value="c.id"
            >
              {{ c.name }}
            </option>
          </select>
        </div>

        <label>
          Patente *
          <input
            v-model="form.plate"
            aria-label="Patente"
            required
            maxlength="10"
            placeholder="ABC-123 o AB-123-CD"
          />
          <small class="muted">Formatos: XXX-999 o XX-999-XX. También podés escribirla sin guiones.</small>
        </label>

        <div class="form-grid">
          <label>
            Marca *
            <select v-model="form.brand" aria-label="Marca" required @change="changeBrand">
              <option value="">Seleccionar marca…</option>
              <option v-for="brand in brands" :key="brand" :value="brand">{{ brand }}</option>
            </select>
            <button type="button" class="text-button catalog-add" @click="openCatalogEntry('brand')">Agregar marca al catálogo</button>
          </label>
          <label>
            Modelo *
            <select v-model="form.model" aria-label="Modelo" required :disabled="!form.brand" @change="changeModel">
              <option value="">{{ form.brand ? 'Seleccionar modelo…' : 'Primero seleccioná una marca' }}</option>
              <option v-for="model in models" :key="model" :value="model">{{ model }}</option>
            </select>
            <button type="button" class="text-button catalog-add" :disabled="!form.brand" @click="openCatalogEntry('model')">Agregar modelo al catálogo</button>
          </label>
          <label>
            Año *
            <input
              v-model.number="form.year"
              aria-label="Año"
              required
              type="number"
              min="1900"
              :max="new Date().getFullYear() + 1"
            />
          </label>
          <label>
            Motorización
            <select v-model="form.engine" aria-label="Motorización" :disabled="!form.model">
              <option value="">{{ form.model ? 'Sin registrar / Seleccionar motorización…' : 'Primero seleccioná un modelo' }}</option>
              <option v-for="engine in engines" :key="engine" :value="engine">{{ engine }}</option>
            </select>
            <button type="button" class="text-button catalog-add" :disabled="!form.model" @click="openCatalogEntry('engine')">Agregar motorización al catálogo</button>
          </label>
        </div>

        <div v-if="catalogField" class="catalog-entry">
          <label>
            Nueva {{ catalogLabels[catalogField] }}
            <input v-model="catalogValue" :aria-label="`Nueva ${catalogLabels[catalogField]}`" maxlength="80" @keydown.enter.prevent="addCatalogEntry" />
          </label>
          <small v-if="catalogField !== 'brand'" class="muted">{{ form.brand }}<template v-if="catalogField === 'engine'"> · {{ form.model }}</template></small>
          <p v-if="catalogError" class="error-message" role="alert">{{ catalogError }}</p>
          <div class="catalog-entry-actions">
            <button type="button" class="button small" @click="catalogField = null">Cancelar</button>
            <button type="button" class="button small primary" @click="addCatalogEntry">Agregar y seleccionar</button>
          </div>
        </div>

        <label>
          Kilometraje
          <input
            v-model.number="form.km"
            type="number"
            min="0"
            max="9999999"
          />
        </label>

        <p v-if="formError" class="error-message" role="alert">
          {{ formError }}
        </p>
      </div>

      <footer class="dialog-footer modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary">
          <Check :size="16" />Guardar vehículo
        </button>
      </footer>
    </form>
  </CommonFormPage>
</template>

<style scoped>
.catalog-add { justify-self: start; font-size: 11px; }
.catalog-entry { padding: 12px; border: 1px solid var(--line); border-radius: 8px; }
.catalog-entry-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 10px; }

.field-block {
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.block-label {
  font-size: 11px;
  font-weight: 500;
  color: var(--ink);
}
.required-star {
  color: #ef4444;
}
.selected-client-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
}
.client-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: #dbeafe;
  color: #1d4ed8;
  display: grid;
  place-items: center;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}
.client-avatar.small {
  width: 28px;
  height: 28px;
  font-size: 10px;
}
.client-info {
  flex: 1;
  min-width: 0;
}
.client-info strong {
  display: block;
  font-size: 12px;
  color: #0f172a;
}
.client-sub {
  display: block;
  font-size: 10px;
  color: #64748b;
  margin-top: 2px;
}
.change-client-btn {
  font-size: 10px;
  padding: 5px 10px;
  min-height: 28px;
}
.client-search-container {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.client-search-box {
  display: flex;
  align-items: center;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #ffffff;
  padding: 0 10px;
  gap: 8px;
  transition: border-color 150ms ease, box-shadow 150ms ease;
}
.client-search-box:focus-within {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
}
.search-icon {
  color: #64748b;
  flex-shrink: 0;
}
.client-search-box input {
  border: none !important;
  box-shadow: none !important;
  padding: 10px 0 !important;
  margin: 0 !important;
  font-size: 11px;
  flex: 1;
  background: transparent !important;
}
.clear-icon-btn {
  width: 20px;
  height: 20px;
  color: #94a3b8;
}
.client-results-list {
  max-height: 185px;
  overflow-y: auto;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.05);
}
.client-result-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: background 120ms ease;
}
.client-result-item:last-child {
  border-bottom: none;
}
.client-result-item:hover {
  background: #eff6ff;
}
.result-details {
  flex: 1;
  min-width: 0;
}
.result-name {
  display: block;
  font-size: 11px;
  color: #0f172a;
}
.result-meta {
  display: block;
  font-size: 9px;
  color: #64748b;
  margin-top: 2px;
}
.client-empty-state {
  padding: 16px;
  text-align: center;
  font-size: 11px;
  color: #64748b;
}
.client-empty-state p {
  margin-bottom: 4px;
  color: #475569;
}

:global(html.dark .client-search-box) {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(255, 255, 255, 0.12);
}
:global(html.dark .client-search-box:focus-within) {
  border-color: #0a84ff;
  box-shadow: 0 0 0 3px rgba(10, 132, 255, 0.25);
}
:global(html.dark .client-results-list) {
  background: #252528;
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.4);
}
:global(html.dark .client-result-item) {
  border-bottom-color: rgba(255, 255, 255, 0.06);
}
:global(html.dark .client-result-item:hover) {
  background: rgba(10, 132, 255, 0.12);
}
:global(html.dark .result-name) {
  color: #ffffff;
}
:global(html.dark .selected-client-card) {
  background: rgba(10, 132, 255, 0.1);
  border-color: rgba(10, 132, 255, 0.3);
}
:global(html.dark .selected-client-card strong) {
  color: #ffffff;
}
</style>
