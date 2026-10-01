<script setup lang="ts">
import { Check, X, Search, Plus, Trash2 } from 'lucide-vue-next'
import type { Invoice, Order, Vehicle } from '~/types'

const props = defineProps<{
  open: boolean
  order?: Order | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created', invoice: Invoice): void
}>()

const { db, client } = useDatabase()
const { matches, money } = useHelpers()
const formError = ref('')
const vehicleSearch = ref('')
const searchInputRef = ref<HTMLInputElement | null>(null)

useModalEscape(() => props.open, () => emit('close'))

const form = ref({
  vehicle: '' as string | number,
  type: 'B',
  description: '',
  labor: 0,
  items: [] as { description: string; quantity: number; unitPrice: number }[],
})
const partToAdd = ref('')
const partsAmount = computed(() => form.value.items.reduce((sum, item) =>
  sum + Number(item.quantity || 0) * Number(item.unitPrice || 0), 0))
const netAmount = computed(() => Number(form.value.labor || 0) + partsAmount.value)
const vatAmount = computed(() => form.value.type === 'C' ? 0 : Math.round(netAmount.value * 0.21))
const totalAmount = computed(() => netAmount.value + vatAmount.value)

function addPart() {
  const part = db.value.parts.find((item) => item.id === Number(partToAdd.value))
  if (!part) return
  form.value.items.push({ description: part.name, quantity: 1, unitPrice: part.price })
  partToAdd.value = ''
}

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
  () => [props.open, props.order] as const,
  ([isOpen]) => {
    if (isOpen) {
      form.value = {
        vehicle: props.order?.vehicle || '',
        type: 'B',
        description: props.order?.service || '',
        labor: 0,
        items: (props.order?.parts || []).map((part) => ({
          description: part.name, quantity: 1, unitPrice: part.price,
        })),
      }
      vehicleSearch.value = ''
      formError.value = ''
      partToAdd.value = ''
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

  if (props.order && db.value.invoices.some((invoice) => invoice.orderId === props.order!.id)) {
    formError.value = 'Esta orden ya tiene una factura. Podés cobrarla desde Facturación.'
    return
  }

  if (!form.value.description.trim() || !Number.isFinite(totalAmount.value) || totalAmount.value <= 0
    || !Number.isFinite(Number(form.value.labor)) || Number(form.value.labor) < 0
    || form.value.items.some((item) => !item.description.trim()
      || !Number.isFinite(Number(item.quantity)) || Number(item.quantity) <= 0
      || !Number.isFinite(Number(item.unitPrice)) || Number(item.unitPrice) < 0)) {
    formError.value = 'Completá el concepto, la mano de obra y los repuestos con cantidades e importes válidos. El total debe ser mayor a cero.'
    return
  }

  const id = Math.max(126, ...db.value.invoices.map((i) => i.id)) + 1
  const newInvoice: Invoice = {
    id,
    vehicle: Number(form.value.vehicle),
    orderId: props.order?.id,
    type: form.value.type,
    description: form.value.description,
    total: totalAmount.value,
    laborAmount: Number(form.value.labor),
    partsAmount: partsAmount.value,
    netAmount: netAmount.value,
    vatAmount: vatAmount.value,
    isFiscal: false,
    items: [
      ...(Number(form.value.labor) > 0 ? [{
        description: `Mano de obra: ${form.value.description.trim()}`,
        quantity: 1, unitPrice: Number(form.value.labor), total: Number(form.value.labor),
      }] : []),
      ...form.value.items.map((item) => ({
        description: item.description.trim(), quantity: Number(item.quantity),
        unitPrice: Number(item.unitPrice), total: Number(item.quantity) * Number(item.unitPrice),
      })),
    ],
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
      <h2>{{ order ? `Armar factura · OT #${order.id}` : 'Nueva factura' }}</h2>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <form @submit.prevent="submit" class="entry-form">
      <div class="form-fields">
        <div class="integration-notice">
          Comprobante de demostración, sin validez fiscal. No se solicitará CAE a ARCA.
        </div>
        <p v-if="order" class="muted">Este trabajo no tiene presupuesto. Agregá los repuestos y la mano de obra para dejar la factura lista para cobrar.</p>

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
              v-if="!order"
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
          <select v-model="form.vehicle" :disabled="!!order" aria-label="Vehículo" class="sr-only">
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
          Mano de obra ($)
          <input
            v-model.number="form.labor"
            type="number"
            min="0"
            step="0.01"
            required
          />
        </label>

        <section class="invoice-parts">
          <h3>Repuestos e insumos</h3>
          <div class="invoice-part-picker">
            <label>
              Repuesto del inventario
              <select v-model="partToAdd">
                <option value="">Seleccionar repuesto</option>
                <option v-for="part in db.parts" :key="part.id" :value="part.id">{{ part.name }} · {{ money(part.price) }}</option>
              </select>
            </label>
            <button type="button" class="button small" :disabled="!partToAdd" @click="addPart"><Plus :size="14" /> Agregar</button>
          </div>
          <div v-for="(item, index) in form.items" :key="index" class="invoice-part-row">
            <label>Repuesto <input v-model="item.description" required placeholder="Nombre del repuesto" /></label>
            <label>Cantidad <input v-model.number="item.quantity" type="number" min="0.01" step="0.01" required /></label>
            <label>Precio unitario ($) <input v-model.number="item.unitPrice" type="number" min="0" step="0.01" required /></label>
            <strong>{{ money(item.quantity * item.unitPrice) }}</strong>
            <button type="button" class="icon-button" :aria-label="`Quitar repuesto ${index + 1}`" @click="form.items.splice(index, 1)"><Trash2 :size="16" /></button>
          </div>
          <button type="button" class="button small" @click="form.items.push({ description: '', quantity: 1, unitPrice: 0 })"><Plus :size="14" /> Agregar repuesto manual</button>
        </section>

        <div class="invoice-totals">
          <div><span>Subtotal</span><strong>{{ money(netAmount) }}</strong></div>
          <div><span>{{ form.type === 'C' ? 'IVA' : 'IVA (21%)' }}</span><strong>{{ money(vatAmount) }}</strong></div>
          <div><span>Total a cobrar</span><strong>{{ money(totalAmount) }}</strong></div>
        </div>

        <p v-if="formError" class="error-message" role="alert">
          {{ formError }}
        </p>
      </div>

      <footer class="dialog-footer modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary">
          <Check :size="16" />Guardar factura pendiente de cobro
        </button>
      </footer>
    </form>
  </CommonFormPage>
</template>

<style scoped>
.invoice-parts { display: flex; flex-direction: column; gap: 14px; }
.invoice-part-picker { display: flex; align-items: flex-end; gap: 10px; }
.invoice-part-picker label { flex: 1; }
.invoice-part-row { display: grid; grid-template-columns: minmax(150px, 2fr) minmax(80px, 1fr) minmax(100px, 1fr) auto auto; gap: 10px; align-items: end; }
.invoice-part-row strong { align-self: center; }
.invoice-totals { display: flex; flex-direction: column; gap: 10px; padding-top: 16px; border-top: 1px solid var(--line); }
.invoice-totals > div { display: flex; justify-content: space-between; gap: 12px; }
@media (max-width: 700px) {
  .invoice-part-row { grid-template-columns: 1fr 1fr; }
  .invoice-part-row > label:first-child { grid-column: 1 / -1; }
  .invoice-part-picker { flex-wrap: wrap; }
}
</style>
