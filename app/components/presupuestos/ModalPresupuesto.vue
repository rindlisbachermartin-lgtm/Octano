<script setup lang="ts">
import { Check, X, Search, Plus, Trash2, Package } from 'lucide-vue-next'
import type { Budget, Vehicle, BudgetItem } from '~/types'

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
const vehicleSearch = ref('')
const searchInputRef = ref<HTMLInputElement | null>(null)

useModalEscape(() => props.open, () => emit('close'))

interface FormItem {
  key: number
  partId: number | '' | 'custom'
  customName: string
  quantity: number
  unitPrice: number
}

const form = ref({
  vehicle: '' as string | number,
  description: '',
  labor: 0,
  items: [] as FormItem[],
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

function addItem() {
  form.value.items.push({
    key: Date.now() + Math.random(),
    partId: '',
    customName: '',
    quantity: 1,
    unitPrice: 0,
  })
}

function removeItem(index: number) {
  form.value.items.splice(index, 1)
}

function onPartChange(item: FormItem) {
  if (item.partId === 'custom') {
    item.customName = ''
    item.unitPrice = 0
  } else if (item.partId) {
    const p = db.value.parts.find((part) => part.id === Number(item.partId))
    if (p) {
      item.customName = p.name
      item.unitPrice = p.price || 0
    }
  } else {
    item.customName = ''
    item.unitPrice = 0
  }
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
  }

  db.value.quotes.unshift(newBudget)
  emit('created', newBudget)
}
</script>

<template>
  <dialog v-if="open" class="dialog detail-modal budget-modal" open>
    <div class="dialog-header">
      <div>
        <span class="eyebrow">OCTANO / PRESUPUESTOS</span>
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
          Detalle del trabajo / Diagnóstico
          <input
            v-model="form.description"
            required
            placeholder="Ej. Cambio de pastillas de freno y rectificación de discos"
          />
        </label>

        <!-- Dynamic Parts / Items Selector -->
        <div class="parts-builder-section">
          <div class="parts-builder-header">
            <div>
              <span class="block-label">Repuestos e insumos</span>
              <small class="muted" style="display: block; font-size: 11px">
                Elegí repuestos del inventario o cargá uno personalizado seleccionando <strong>(Otro)</strong>.
              </small>
            </div>
            <button type="button" class="button small outlined" @click="addItem">
              <Plus :size="14" /> Agregar repuesto
            </button>
          </div>

          <!-- Empty Parts state -->
          <div v-if="!form.items.length" class="empty-parts-hint">
            <Package :size="20" class="muted" />
            <p>No se agregaron repuestos aún. Hacé clic en <strong>Agregar repuesto</strong> para sumar repuestos o insumos.</p>
          </div>

          <!-- Items list -->
          <div v-else class="budget-items-list">
            <div
              v-for="(item, index) in form.items"
              :key="item.key"
              class="budget-item-row"
            >
              <div class="item-part-select">
                <label class="sr-only">Repuesto</label>
                <select
                  v-model="item.partId"
                  required
                  @change="onPartChange(item)"
                >
                  <option value="" disabled>Seleccionar repuesto...</option>
                  <option value="custom">✏️ (Otro) Personalizado</option>
                  <optgroup label="Repuestos en Inventario">
                    <option
                      v-for="p in db.parts"
                      :key="p.id"
                      :value="p.id"
                    >
                      {{ p.name }} ({{ p.brand }}) · Stock: {{ p.stock }} · {{ money(p.price) }}
                    </option>
                  </optgroup>
                </select>
              </div>

              <!-- If Custom: Name Input -->
              <div v-if="item.partId === 'custom'" class="item-custom-name">
                <input
                  v-model="item.customName"
                  placeholder="Descripción del repuesto o insumo..."
                  required
                />
              </div>

              <!-- Quantity Input -->
              <div class="item-qty">
                <label>
                  <span class="mini-label">Cant.</span>
                  <input
                    v-model.number="item.quantity"
                    type="number"
                    min="1"
                    required
                  />
                </label>
              </div>

              <!-- Unit Price Input -->
              <div class="item-unit-price">
                <label>
                  <span class="mini-label">P. Unit ($)</span>
                  <input
                    v-model.number="item.unitPrice"
                    type="number"
                    min="0"
                    required
                  />
                </label>
              </div>

              <!-- Line Subtotal -->
              <div class="item-total">
                <span class="mini-label">Subtotal</span>
                <strong class="item-total-amount">
                  {{ money((Number(item.quantity) || 0) * (Number(item.unitPrice) || 0)) }}
                </strong>
              </div>

              <!-- Delete Button -->
              <button
                type="button"
                class="icon-button item-remove-btn"
                title="Quitar repuesto"
                aria-label="Quitar repuesto"
                @click="removeItem(index)"
              >
                <Trash2 :size="15" />
              </button>
            </div>
          </div>

          <div v-if="form.items.length" class="parts-subtotal-bar">
            <span>Subtotal de repuestos:</span>
            <strong>{{ money(partsSubtotal) }}</strong>
          </div>
        </div>

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
            <span>TOTAL ESTIMADO (CON IVA)</span>
            <strong class="grand-total-display">{{ money(grandTotal) }}</strong>
          </div>
        </div>

        <p v-if="formError" class="error-message" role="alert">
          {{ formError }}
        </p>
      </div>

      <footer class="dialog-footer modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary">
          <Check :size="16" /> Guardar y generar presupuesto
        </button>
      </footer>
    </form>
  </dialog>
</template>

<style scoped>
.budget-modal {
  width: 680px !important;
}

.parts-builder-section {
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 10px;
  padding: 14px;
  background: rgba(255, 255, 255, 0.4);
}

.parts-builder-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.empty-parts-hint {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-radius: 8px;
  background: #f8fafc;
  color: #64748b;
  font-size: 11px;
  border: 1px dashed #cbd5e1;
}

.empty-parts-hint p {
  margin: 0;
}

.budget-items-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.budget-item-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 10px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.item-part-select {
  flex: 1.5;
  min-width: 150px;
}

.item-part-select select {
  font-size: 11px;
  padding: 8px 10px;
}

.item-custom-name {
  flex: 1.2;
}

.item-custom-name input {
  font-size: 11px;
  padding: 8px 10px;
}

.item-qty {
  width: 65px;
}

.item-qty input {
  font-size: 11px;
  padding: 8px 6px;
  text-align: center;
}

.item-unit-price {
  width: 100px;
}

.item-unit-price input {
  font-size: 11px;
  padding: 8px 8px;
}

.item-total {
  width: 85px;
  display: flex;
  flex-direction: column;
  padding-bottom: 8px;
  text-align: right;
}

.item-total-amount {
  font-size: 12px;
  color: #0f172a;
}

.mini-label {
  display: block;
  font-size: 9px;
  color: #64748b;
  font-weight: 600;
  margin-bottom: 3px;
}

.item-remove-btn {
  color: #94a3b8;
  margin-bottom: 5px;
}

.item-remove-btn:hover {
  color: #ef4444;
  background: #fef2f2;
}

.parts-subtotal-bar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  align-items: center;
  font-size: 11px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid #e2e8f0;
  color: #64748b;
}

.parts-subtotal-bar strong {
  font-size: 12px;
  color: #0f172a;
}

/* Summary Card with VAT */
.quote-summary-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.summary-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11.5px;
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
  font-family: 'Manrope', sans-serif;
  font-size: 20px;
  font-weight: 800;
  color: #2563eb;
}

/* Dark Mode Overrides */
:global(html.dark) .parts-builder-section {
  border-color: rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.02);
}

:global(html.dark) .empty-parts-hint {
  background: #202023;
  border-color: rgba(255, 255, 255, 0.1);
  color: #8e8e93;
}

:global(html.dark) .budget-item-row {
  background: #202023;
  border-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark) .item-total-amount {
  color: #ffffff;
}

:global(html.dark) .mini-label {
  color: #8e8e93;
}

:global(html.dark) .parts-subtotal-bar {
  border-top-color: rgba(255, 255, 255, 0.08);
  color: #8e8e93;
}

:global(html.dark) .parts-subtotal-bar strong {
  color: #ffffff;
}

:global(html.dark) .quote-summary-card {
  background: #202023;
  border-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark) .summary-line {
  color: #a1a1a6;
}

:global(html.dark) .summary-line strong {
  color: #ffffff;
}

:global(html.dark) .subtotal-line {
  border-top-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark) .vat-line {
  color: #64d2ff;
}

:global(html.dark) .total-line {
  border-top-color: rgba(255, 255, 255, 0.2);
  color: #ffffff;
}

:global(html.dark) .grand-total-display {
  color: #64d2ff;
}

:global(html.dark) .item-remove-btn:hover {
  background: rgba(255, 69, 58, 0.15);
  color: #ff453a;
}
</style>
