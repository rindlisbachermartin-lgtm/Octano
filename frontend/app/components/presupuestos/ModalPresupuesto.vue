<script setup lang="ts">
import { Check, X, Search } from '@lucide/vue'
import type { Budget, Vehicle, BudgetItem } from '~/types'
import type { PartEditorItem } from '~/types/partEditor'
import type { Client, Part } from '~/types'
import { authError } from '~/composables/useAuthApi'
import { adaptarPresupuesto, paginasPresupuestos, tiposTrabajoPresupuesto, type PresupuestoApi, type PresupuestoConDatos, type TipoTrabajoPresupuesto } from '~/utils/presupuestoApi'
import { adaptarVehiculo, adaptarTitular, type VehiculoApi, type ClienteTitularApi } from '~/utils/vehiculoApi'
import { adaptarRepuesto, type RepuestoApi } from '~/utils/repuestoApi'

const props = defineProps<{
  open: boolean
  budget?: PresupuestoConDatos | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created', budget: Budget): void
  (e: 'updated', budget: Budget): void
}>()

const { db, client: demoClient } = useDatabase()
const auth = useAuthApi()
const saving = ref(false)
const loading = ref(false)
const dataError = ref('')
const apiVehicles = ref<Vehicle[]>([])
const apiClients = ref<Client[]>([])
const apiParts = ref<Part[]>([])
const vehicles = computed(() => auth.authorization.value ? apiVehicles.value : db.value.vehicles)
const parts = computed(() => auth.authorization.value ? apiParts.value : db.value.parts)
const client = (id: number) => auth.authorization.value ? apiClients.value.find(item => item.id === id) : demoClient(id)
const { money, matches } = useHelpers()
const formError = ref('')
const vehicleSearch = ref('')
const debouncedSearch = useDebouncedValue(vehicleSearch)
const searchInputRef = ref<HTMLInputElement | null>(null)

function close() { if (!saving.value) emit('close') }
useModalEscape(() => props.open && !saving.value, close)

const form = ref({
  vehicle: '' as string | number,
  description: '',
  labor: 0,
  items: [] as PartEditorItem[],
  serviceTypes: [] as string[],
  vat: 21,
})

const availableWorkTypes = [
  { value: 'Service de mantenimiento', label: 'Service' },
  { value: 'Cambio de distribución', label: 'Distribución' },
  { value: 'Otro trabajo', label: 'Otro trabajo' },
]

function toggleWork(value: string) {
  const selected = form.value.serviceTypes
  form.value.serviceTypes = selected.includes(value) ? selected.filter(type => type !== value) : [...selected, value]
}

const selectedVehicle = computed(() =>
  vehicles.value.find((v) => v.id === Number(form.value.vehicle))
)

const filteredVehicles = computed(() => {
  const query = debouncedSearch.value.trim()
  if (!query) return []
  return vehicles.value.filter((v) => {
    const c = client(v.client)
    return matches(query, v.plate, v.brand, v.model, c?.name, c?.doc, c?.phone)
  })
})

const partsSubtotal = computed(() => {
  return form.value.items.reduce((acc, item) => {
    const qty = Number(item.quantity) || 0
    const price = Number(item.unitPrice) || 0
    return acc + Math.round((qty * price + Number.EPSILON) * 100) / 100
  }, 0)
})

const netSubtotal = computed(() => {
  return Math.round((partsSubtotal.value + (Number(form.value.labor) || 0) + Number.EPSILON) * 100) / 100
})

const vatAmount = computed(() => {
  return Math.round((netSubtotal.value * form.value.vat / 100 + Number.EPSILON) * 100) / 100
})

const grandTotal = computed(() => {
  return Math.round((netSubtotal.value + vatAmount.value + Number.EPSILON) * 100) / 100
})

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      form.value = {
        vehicle: '',
        description: '',
        labor: 0,
        items: [],
        serviceTypes: [],
        vat: 21,
      }
      if (props.budget) {
        const budget = props.budget
        form.value = {
          vehicle: budget.vehicle, description: budget.description, labor: budget.labor,
          serviceTypes: [...(budget.serviceTypes ?? [])], vat: budget.registro.porcentajeIva,
          items: (budget.items ?? []).map((item, index) => ({
            key: index + 1, partId: item.partId ?? 'custom', customName: item.name,
            searchQuery: item.name, quantity: item.quantity, unitPrice: item.unitPrice,
          })),
        }
      }
      vehicleSearch.value = ''
      formError.value = ''
      nextTick(() => {
        searchInputRef.value?.focus()
      })
    }
  }
)

async function loadData(signal: AbortSignal, headers: Record<string, string>) {
  loading.value = true
  dataError.value = ''
  try {
    const [vehicles, clients, parts] = await Promise.all([
      paginasPresupuestos<VehiculoApi>('/api/vehiculos', headers, signal, { activo: true }),
      paginasPresupuestos<ClienteTitularApi>('/api/clientes', headers, signal, { activo: true }),
      paginasPresupuestos<RepuestoApi>('/api/repuestos', headers, signal, { activo: true }),
    ])
    if (signal.aborted) return
    apiClients.value = clients.map(adaptarTitular)
    const clientIds = new Set(clients.map(item => item.id))
    apiVehicles.value = vehicles.filter(item => clientIds.has(item.clienteId)).map(adaptarVehiculo)
    apiParts.value = parts.map(adaptarRepuesto)
  } catch (error) {
    if (!signal.aborted) dataError.value = authError(error, 'No se pudieron cargar los clientes, vehículos y repuestos.')
  } finally {
    if (!signal.aborted) loading.value = false
  }
}

const dataRevision = ref(0)
watch(() => [props.open, auth.authorization.value, dataRevision.value] as const, ([open, headers], _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  apiVehicles.value = []
  apiClients.value = []
  apiParts.value = []
  loading.value = false
  if (open && headers) void loadData(controller.signal, headers)
})

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

async function submit() {
  if (saving.value || loading.value || dataError.value) return
  formError.value = ''
  if (!form.value.vehicle) {
    formError.value = 'Por favor buscá y seleccioná un vehículo / cliente titular.'
    return
  }

  if (!form.value.description.trim()) {
    formError.value = 'Ingresá el detalle del trabajo presupuestado.'
    return
  }

  // Validate items
  for (let i = 0; i < form.value.items.length; i++) {
    const it = form.value.items[i]
    if (it.partId === 'custom' && !it.customName.trim()) {
      formError.value = 'Por favor indicá el nombre del repuesto en la opción (Otro).'
      return
    }
    if (!it.partId) {
      formError.value = 'Por favor seleccioná un repuesto o eliminalo si no lo utilizás.'
      return
    }
  }

  const processedItems: BudgetItem[] = form.value.items.map((it) => {
    let name = it.customName
    const isCustom = it.partId === 'custom'
    if (!isCustom) {
      const p = parts.value.find((part) => part.id === Number(it.partId))
      if (p) name = p.name
    }
    return {
      id: it.key,
      partId: isCustom ? null : Number(it.partId),
      name: name || 'Repuesto',
      quantity: Number(it.quantity) || 1,
      unitPrice: Number(it.unitPrice) || 0,
      total: Math.round(((Number(it.quantity) || 1) * (Number(it.unitPrice) || 0) + Number.EPSILON) * 100) / 100,
      isCustom,
    }
  })

  if (!form.value.serviceTypes.length) {
    formError.value = 'Por favor seleccioná al menos un tipo de trabajo a realizar.'
    return
  }

  if (!validMoney(form.value.labor) || !validMoney(form.value.vat) || form.value.vat > 100
    || form.value.items.some(item => !validMoney(item.unitPrice)
      || !/^\d{1,12}(?:\.\d{1,3})?$/.test(String(item.quantity)) || item.quantity <= 0)) {
    formError.value = 'Formato inválido.'
    return
  }
  if (auth.authorization.value) {
    const vehicle = selectedVehicle.value
    if (!vehicle) return
    saving.value = true
    try {
      const id = props.budget?.id
      const result = await $fetch<PresupuestoApi>(id ? `/api/presupuestos/${id}` : '/api/presupuestos', {
        method: id ? 'PUT' : 'POST', headers: auth.authorization.value,
        body: {
          clienteId: vehicle.client, vehiculoId: vehicle.id, descripcion: form.value.description.trim(),
          manoObra: form.value.labor, porcentajeIva: form.value.vat,
          tiposTrabajo: (Object.keys(tiposTrabajoPresupuesto) as TipoTrabajoPresupuesto[])
            .filter(tipo => form.value.serviceTypes.includes(tiposTrabajoPresupuesto[tipo])),
          items: form.value.items.map(item => ({
            repuestoId: item.partId === 'custom' ? null : Number(item.partId),
            descripcion: item.partId === 'custom' ? inicialMayuscula(item.customName.trim()) : null,
            cantidad: item.quantity, precioUnitario: item.unitPrice,
          })),
        },
      })
      const budget = adaptarPresupuesto(result)
      if (id) emit('updated', budget)
      else emit('created', budget)
    } catch (error) {
      formError.value = authError(error, 'No se pudo guardar el presupuesto.')
    } finally {
      saving.value = false
    }
    return
  }

  const id = Math.max(208, ...db.value.quotes.map((q) => q.id)) + 1
  const newBudget: Budget = {
    id,
    vehicle: Number(form.value.vehicle),
    description: form.value.description,
    labor: Number(form.value.labor) || 0,
    materials: partsSubtotal.value,
    items: processedItems,
    subtotal: netSubtotal.value,
    tax: vatAmount.value,
    total: grandTotal.value,
    status: 'Pendiente',
    date: new Date().toISOString().slice(0, 10),
    serviceTypes: [...form.value.serviceTypes],
  }

  db.value.quotes.unshift(newBudget)
  emit('created', newBudget)
}

function validMoney(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) && /^\d{1,12}(?:\.\d{1,2})?$/.test(String(value))
}
</script>

<template>
  <CommonFormPage v-if="open" class="budget-content">
    <div class="dialog-header">
      <div>
        <h2>{{ budget ? 'Editar presupuesto' : 'Nuevo presupuesto' }}</h2>
      </div>
      <button class="icon-button" aria-label="Cerrar" :disabled="saving" @click="close">
        <X :size="18" />
      </button>
    </div>

    <form @submit.prevent="submit" class="entry-form">
      <div class="form-fields" :inert="saving || loading || !!dataError">
        <!-- Vehicle / Client Search Selector -->
        <div class="field-block">
          <label v-if="!selectedVehicle" class="block-label">
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
            <option v-for="v in vehicles" :key="v.id" :value="v.id">
              {{ v.plate }} · {{ v.brand }} {{ v.model }} — {{ client(v.client)?.name }}
            </option>
          </select>
        </div>

        <!-- Selector de Tipos de Trabajo a Realizar -->
        <CommonDependentFields :ready="!!selectedVehicle">
        <div class="field-block work-types-container">
          <fieldset class="work-type-field">
            <legend>Trabajo a realizar <span class="required-star">*</span></legend>
            <div class="work-type-options">
              <button v-for="option in availableWorkTypes" :key="option.value" type="button" class="button" :class="{ primary: form.serviceTypes.includes(option.value) }" :aria-pressed="form.serviceTypes.includes(option.value)" @click="toggleWork(option.value)">{{ option.label }}</button>
            </div>
          </fieldset>

        </div>

        <div class="form-grid budget-main-fields">
        <label>
          Detalle del trabajo / Diagnóstico
          <input
            v-model="form.description"
            maxlength="2000"
            required
            placeholder="Ej. Cambio de pastillas de freno y rectificación de discos"
          />
        </label>

        <!-- Labor Input -->
        <label>
          Mano de obra ($)
          <input
            v-model.number="form.labor"
            type="number"
            min="0"
            step="0.01"
            :aria-invalid="!validMoney(form.labor)"
            required
            placeholder="0"
          />
          <CommonFormatoInvalido v-if="!validMoney(form.labor)" />
        </label>
        </div>

        <CommonEditorRepuestos v-model="form.items" :parts="parts" :min-quantity="0.001" />
        <label v-if="auth.authorization.value">IVA (%)
          <input v-model.number="form.vat" type="number" min="0" max="100" step="0.01" required :aria-invalid="!validMoney(form.vat) || form.vat > 100" />
          <CommonFormatoInvalido v-if="!validMoney(form.vat) || form.vat > 100" />
        </label>

        <!-- Breakdown with VAT / IVA 21% -->
        <div class="quote-summary-card">
          <div class="summary-line">
            <span>Mano de obra</span>
            <strong>{{ money(form.labor || 0) }}</strong>
          </div>
          <div class="summary-line">
            <span>Repuestos e insumos</span>
            <strong>{{ money(partsSubtotal) }}</strong>
          </div>
          <div class="summary-line subtotal-line">
            <span>Subtotal Neto</span>
            <strong>{{ money(netSubtotal) }}</strong>
          </div>
          <div class="summary-line vat-line">
            <span style="display: flex; align-items: center; gap: 4px">
              IVA ({{ form.vat }}%)
            </span>
            <strong>{{ money(vatAmount) }}</strong>
          </div>
          <div class="summary-line total-line">
            <span>Total con IVA</span>
            <strong class="grand-total-display">{{ money(grandTotal) }}</strong>
          </div>
        </div>

        </CommonDependentFields>
        <p v-if="formError" class="error-message" role="alert">
          {{ formError }}
        </p>
      </div>
      <p v-if="loading" class="muted" role="status">Cargando datos del taller…</p>
      <p v-if="dataError" class="error-message" role="alert">{{ dataError }} <button type="button" class="button small" @click="dataRevision++">Reintentar</button></p>

      <footer class="dialog-footer modal-footer">
        <button type="button" class="button" :disabled="saving" @click="close">Cancelar</button>
        <button type="submit" class="button primary" :disabled="!selectedVehicle || saving || loading || !!dataError">
          <Check :size="16" /> {{ budget ? 'Guardar cambios' : 'Guardar y generar presupuesto' }}
        </button>
      </footer>
    </form>
  </CommonFormPage>
</template>

<style scoped>
.budget-content {
  width: 100%;
}
.budget-content .dialog-header { padding: 10px 16px; }
.budget-content .form-fields { padding: 12px 16px; gap: 8px; }
.budget-content .modal-footer { padding: 8px 16px; }
.budget-content .budget-main-fields { grid-template-columns: minmax(0, 2fr) minmax(0, 1fr); }

/* Summary Card with VAT */
.quote-summary-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 8px 12px;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.summary-line {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  font-size: 11px;
  line-height: 1.2;
  color: #475569;
}

.summary-line strong {
  color: #0f172a;
}

.subtotal-line {
  border-top: 1px solid #e2e8f0;
  padding-top: 6px;
  margin-top: 2px;
  font-weight: 600;
}

.vat-line {
  color: #2563eb;
  font-weight: 600;
}

.total-line {
  border-top: 2px solid #0f172a;
  padding-top: 8px;
  margin-top: 4px;
  font-size: 12.5px;
  font-weight: 800;
  color: #0f172a;
}

.grand-total-display {
  font-family: 'Public Sans', sans-serif;
  font-size: 16px;
  line-height: 1.2;
  font-weight: 800;
  color: #2563eb;
}

/* Work Types & Oil Spec */
.work-types-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.work-type-field { border: 0; padding: 0; margin: 0; min-width: 0; }
.work-type-field legend { margin-bottom: 8px; font-size: 13px; font-weight: 600; }
.work-type-options { display: flex; flex-wrap: wrap; gap: 8px; }

/* Dark Mode Overrides */
:global(html.dark .quote-summary-card) {
  background: #202023;
  border-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark .summary-line) {
  color: #a1a1a6;
}

:global(html.dark .summary-line strong) {
  color: #ffffff;
}

:global(html.dark .subtotal-line) {
  border-top-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark .vat-line) {
  color: #64d2ff;
}

:global(html.dark .total-line) {
  border-top-color: rgba(255, 255, 255, 0.2);
  color: #ffffff;
}

:global(html.dark .grand-total-display) {
  color: #64d2ff;
}

.quote-summary-card .summary-line { border: 0; padding: 0; margin: 0; }
@media (max-width: 540px) {
  .budget-content .budget-main-fields { grid-template-columns: 1fr; }
  .quote-summary-card { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .quote-summary-card .total-line { grid-column: 1 / -1; }
}

</style>
