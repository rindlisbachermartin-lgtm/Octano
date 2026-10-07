<script setup lang="ts">
import { Check, X, Search } from 'lucide-vue-next'
import type { Budget, Vehicle, BudgetItem } from '~/types'
import type { PartEditorItem } from '~/types/partEditor'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created', budget: Budget): void
}>()

const { db, client } = useDatabase()
const { money, matches } = useHelpers()
const formError = ref('')
const oilOptionsId = useId()
const vehicleSearch = ref('')
const debouncedSearch = useDebouncedValue(vehicleSearch)
const searchInputRef = ref<HTMLInputElement | null>(null)

useModalEscape(() => props.open, () => emit('close'))

const form = ref({
  vehicle: '' as string | number,
  description: '',
  labor: 0,
  items: [] as PartEditorItem[],
  serviceTypes: [] as string[],
  oilSpec: '',
})

const availableWorkTypes = [
  'Service de mantenimiento',
  'Cambio de distribución',
  'Frenos',
  'Tren delantero y suspensión',
  'Embrague y transmisión',
  'Motor e inyección',
  'Electricidad y batería',
  'Reparación general',
]

const standardOils = [
  '5W-30 Sintético',
  '5W-40 Sintético',
  '10W-40 Semi-sintético',
  '15W-40 Mineral',
  '0W-20 Sintético',
  '0W-30 Sintético',
]

function updateWorkTypes(types: string[]) {
  const added = types.find((type) => !form.value.serviceTypes.includes(type))
  form.value.serviceTypes = types
  if (types.includes('Service de mantenimiento') && !form.value.oilSpec) form.value.oilSpec = '5W-30 Sintético'
  if (!form.value.description.trim()) {
    const descriptions: Record<string, string> = {
      'Service de mantenimiento': 'Service completo con cambio de aceite y filtros',
      'Cambio de distribución': 'Cambio de kit de distribución y bomba de agua',
      'Frenos': 'Revisión y cambio de pastillas / discos de freno',
    }
    form.value.description = descriptions[added || ''] || ''
  }
}

function closeWorkTypeMenu(event: KeyboardEvent) {
  event.stopPropagation()
}

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

const partsSubtotal = computed(() => {
  return form.value.items.reduce((acc, item) => {
    const qty = Number(item.quantity) || 0
    const price = Number(item.unitPrice) || 0
    return acc + (qty * price)
  }, 0)
})

const netSubtotal = computed(() => {
  return partsSubtotal.value + (Number(form.value.labor) || 0)
})

const vatAmount = computed(() => {
  return Math.round(netSubtotal.value * 0.21)
})

const grandTotal = computed(() => {
  return netSubtotal.value + vatAmount.value
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
        oilSpec: '',
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
      const p = db.value.parts.find((part) => part.id === Number(it.partId))
      if (p) name = p.name
    }
    return {
      id: it.key,
      partId: isCustom ? null : Number(it.partId),
      name: name || 'Repuesto',
      quantity: Number(it.quantity) || 1,
      unitPrice: Number(it.unitPrice) || 0,
      total: (Number(it.quantity) || 1) * (Number(it.unitPrice) || 0),
      isCustom,
    }
  })

  if (!form.value.serviceTypes.length) {
    formError.value = 'Por favor seleccioná al menos un tipo de trabajo a realizar.'
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
    oilSpec: form.value.oilSpec || '',
  }

  db.value.quotes.unshift(newBudget)
  emit('created', newBudget)
}
</script>

<template>
  <CommonFormPage v-if="open" class="budget-content">
    <div class="dialog-header">
      <div>
        <h2>Nuevo presupuesto</h2>
      </div>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <form @submit.prevent="submit" class="entry-form">
      <div class="form-fields">
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
            <option v-for="v in db.vehicles" :key="v.id" :value="v.id">
              {{ v.plate }} · {{ v.brand }} {{ v.model }} — {{ client(v.client)?.name }}
            </option>
          </select>
        </div>

        <!-- Selector de Tipos de Trabajo a Realizar -->
        <CommonDependentFields :ready="!!selectedVehicle">
        <div class="field-block work-types-container">
          <label class="block-label">
            Tipos de trabajo a realizar
            <span class="required-star">*</span>
          </label>
          <USelectMenu :model-value="form.serviceTypes" :items="availableWorkTypes" multiple :search-input="false" :content="{ onEscapeKeyDown: closeWorkTypeMenu }" placeholder="Seleccioná tipos de trabajo" aria-label="Tipos de trabajo a realizar" class="work-types-select" title="Clasifica el presupuesto y actualiza la ficha técnica del vehículo." @update:model-value="updateWorkTypes" />

          <label v-if="form.serviceTypes.includes('Service de mantenimiento')" class="oil-field">
            Aceite para el service
            <input v-model="form.oilSpec" :list="oilOptionsId" placeholder="Elegí una viscosidad o escribí marca y especificación" title="Se registrará en la ficha técnica del vehículo." />
            <datalist :id="oilOptionsId"><option v-for="oil in standardOils" :key="oil" :value="oil" /></datalist>
          </label>
        </div>

        <div class="form-grid budget-main-fields">
        <label>
          Detalle del trabajo / Diagnóstico
          <input
            v-model="form.description"
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
            required
            placeholder="0"
          />
        </label>
        </div>

        <CommonEditorRepuestos v-model="form.items" />

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
              IVA (21%)
              <span class="badge neutral" style="font-size: 8.5px; padding: 1px 5px">ARCA</span>
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

      <footer class="dialog-footer modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary" :disabled="!selectedVehicle">
          <Check :size="16" /> Guardar y generar presupuesto
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
.work-types-select { width: 100%; min-height: 36px; font-size: 11px; }
.oil-field { display: block; }

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
