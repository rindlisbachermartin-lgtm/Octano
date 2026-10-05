<script setup lang="ts">
import { Check, X, Search, Plus, Trash2 } from 'lucide-vue-next'
import type { Invoice, Order, Vehicle } from '~/types'

const props = defineProps<{
  open: boolean
  order?: Order | null
  invoice?: Invoice | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created', invoice: Invoice): void
}>()

const { db, client } = useDatabase()
const { matches, money } = useHelpers()
const formError = ref('')
const vehicleSearch = ref('')
const debouncedSearch = useDebouncedValue(vehicleSearch)
const searchInputRef = ref<HTMLInputElement | null>(null)

useModalEscape(() => props.open, () => emit('close'))

const form = ref({
  vehicle: '' as string | number,
  description: '',
  labor: 0,
  items: [] as { description: string; quantity: number; unitPrice: number }[],
})
const partToAdd = ref('')
const partsAmount = computed(() => form.value.items.reduce((sum, item) =>
  sum + Number(item.quantity || 0) * Number(item.unitPrice || 0), 0))
const netAmount = computed(() => Number(form.value.labor || 0) + partsAmount.value)
const invoiceType = computed(() => invoiceTypeForVat(db.value.issuerVatCondition || 'IVA Responsable Inscripto',
  selectedVehicle.value ? client(selectedVehicle.value.client)?.vatCondition || 'Consumidor Final' : 'Consumidor Final'))
const vatAmount = computed(() => invoiceType.value === 'C' ? 0 : Math.round(netAmount.value * 0.21 * 100) / 100)
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
  const query = debouncedSearch.value.trim()
  if (!query) return []
  return db.value.vehicles.filter((v) => {
    const c = client(v.client)
    return matches(query, v.plate, v.brand, v.model, c?.name, c?.doc, c?.phone)
  })
})

watch(
  () => [props.open, props.order, props.invoice] as const,
  ([isOpen]) => {
    if (isOpen) {
      const draft = props.invoice
      const draftParts = draft?.items?.filter((item) => !item.description.startsWith('Mano de obra:'))
      form.value = {
        vehicle: draft?.vehicle || props.order?.vehicle || '',
        description: draft?.description || props.order?.service || '',
        labor: draft ? (draft.laborAmount ?? Math.max(0, (draft.netAmount ?? (draft.type === 'C' ? draft.total : Math.round(draft.total / 1.21 * 100) / 100)) - (draft.partsAmount ?? 0))) : 0,
        items: draftParts ? draftParts.map((item) => ({ description: item.description, quantity: item.quantity, unitPrice: item.unitPrice })) : (props.order?.parts || []).map((part) => ({
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
  if (props.invoice && (props.invoice.isFiscal || props.invoice.cae || props.invoice.status === 'Cobrada')) {
    formError.value = 'Este comprobante ya fue emitido o cobrado y no se puede modificar.'
    return
  }
  if (!form.value.vehicle) {
    formError.value = 'Por favor buscá y seleccioná un vehículo / cliente titular.'
    return
  }

  if (props.order && db.value.invoices.some((invoice) => invoice.orderId === props.order!.id && invoice.id !== props.invoice?.id)) {
    formError.value = 'Esta orden ya tiene un comprobante. Podés ver su detalle desde Facturación.'
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

  const id = props.invoice?.id ?? Math.max(126, ...db.value.invoices.map((i) => i.id)) + 1
  const newInvoice: Invoice = {
    ...props.invoice,
    id,
    vehicle: Number(form.value.vehicle),
    orderId: props.order?.id ?? props.invoice?.orderId,
    type: invoiceType.value,
    issuerVatCondition: db.value.issuerVatCondition || 'IVA Responsable Inscripto',
    clientVatCondition: selectedVehicle.value ? client(selectedVehicle.value.client)?.vatCondition || 'Consumidor Final' : 'Consumidor Final',
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
    status: 'Para armar',
    date: props.invoice?.date || new Date().toISOString().slice(0, 10),
  }

  const draftIndex = props.invoice ? db.value.invoices.findIndex((invoice) => invoice.id === props.invoice!.id) : -1
  if (draftIndex >= 0) db.value.invoices.splice(draftIndex, 1, newInvoice)
  else db.value.invoices.unshift(newInvoice)
  emit('created', newInvoice)
}
</script>

<template>
  <CommonFormPage v-if="open">
    <div class="dialog-header">
      <h2>{{ invoice ? `Armar factura · Borrador #${invoice.id}` : order ? `Armar factura · OT #${order.id}` : 'Nueva factura' }}</h2>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <form @submit.prevent="submit" class="entry-form">
      <div class="form-fields">
        <div class="integration-notice">
          Completá el detalle para continuar con la emisión ARCA o el registro del cobro.
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
              </div>

              <div v-if="!filteredVehicles.length && vehicleSearch.trim() === debouncedSearch.trim()" class="target-empty-state">
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
          Tipo de factura
          <input :value="`Factura ${invoiceType}`" readonly aria-label="Tipo de factura" />
          <small class="muted">Según la condición de IVA del taller y del cliente.</small>
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
          <div v-if="form.items.length" class="invoice-parts-table-scroll">
            <table class="invoice-parts-table" aria-label="Detalle de repuestos e insumos">
              <colgroup><col /><col class="quantity-column" /><col class="price-column" /><col class="subtotal-column" /><col class="remove-column" /></colgroup>
              <thead><tr><th scope="col">Repuesto</th><th scope="col">Cantidad</th><th scope="col">Precio unitario ($)</th><th scope="col" class="part-subtotal">Subtotal</th><th scope="col"><span class="sr-only">Acciones</span></th></tr></thead>
              <tbody>
                <tr v-for="(item, index) in form.items" :key="index">
                  <td><input v-model="item.description" :aria-label="`Repuesto ${index + 1}`" required placeholder="Nombre del repuesto" /></td>
                  <td><input v-model.number="item.quantity" :aria-label="`Cantidad del repuesto ${index + 1}`" type="number" min="0.01" step="0.01" required /></td>
                  <td><input v-model.number="item.unitPrice" :aria-label="`Precio unitario del repuesto ${index + 1}`" type="number" min="0" step="0.01" required /></td>
                  <td class="part-subtotal"><strong>{{ money(item.quantity * item.unitPrice) }}</strong></td>
                  <td><button type="button" class="icon-button" :aria-label="`Quitar repuesto ${index + 1}`" @click="form.items.splice(index, 1)"><Trash2 :size="16" /></button></td>
                </tr>
              </tbody>
            </table>
          </div>
          <button type="button" class="button small" @click="form.items.push({ description: '', quantity: 1, unitPrice: 0 })"><Plus :size="14" /> Agregar repuesto manual</button>
        </section>

        <div class="invoice-totals">
          <div><span>Subtotal</span><strong>{{ money(netAmount) }}</strong></div>
          <div><span>{{ invoiceType === 'C' ? 'IVA (no corresponde)' : 'IVA (21%)' }}</span><strong>{{ money(vatAmount) }}</strong></div>
          <div><span>Total a cobrar</span><strong>{{ money(totalAmount) }}</strong></div>
        </div>

        <p v-if="formError" class="error-message" role="alert">
          {{ formError }}
        </p>
      </div>

      <footer class="dialog-footer modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary">
          <Check :size="16" />Guardar detalle y continuar
        </button>
      </footer>
    </form>
  </CommonFormPage>
</template>

<style scoped>
.form-fields .integration-notice { background: transparent !important; border: 0 !important; }
.invoice-parts { display: flex; flex-direction: column; gap: 14px; }
.invoice-part-picker { display: flex; align-items: flex-end; gap: 10px; }
.invoice-part-picker label { flex: 1; }
.invoice-parts-table-scroll { overflow-x: auto; }
.invoice-parts-table { width: 100%; min-width: 660px; table-layout: fixed; border-collapse: collapse; }
.invoice-parts-table .quantity-column { width: 100px; }
.invoice-parts-table .price-column { width: 170px; }
.invoice-parts-table .subtotal-column { width: 150px; }
.invoice-parts-table .remove-column { width: 42px; }
.invoice-parts-table th, .invoice-parts-table td { padding: 8px 6px; vertical-align: middle; }
.invoice-parts-table th { font-size: 12px; font-weight: 600; text-align: left; }
.invoice-parts-table th:first-child, .invoice-parts-table td:first-child { padding-left: 0; }
.invoice-parts-table th:last-child, .invoice-parts-table td:last-child { padding-right: 0; }
.invoice-parts-table td input { display: block; width: 100%; min-width: 0; margin: 0; }
.invoice-parts-table .part-subtotal { text-align: right; white-space: nowrap; font-variant-numeric: tabular-nums; }
.invoice-parts-table .part-subtotal strong { font-size: 15px; }
.invoice-totals { display: flex; flex-direction: column; gap: 10px; padding-top: 16px; border-top: 1px solid var(--line); }
.invoice-totals > div { display: flex; justify-content: space-between; gap: 12px; }
@media (max-width: 700px) {
  .invoice-part-picker { flex-wrap: wrap; }
}
</style>
