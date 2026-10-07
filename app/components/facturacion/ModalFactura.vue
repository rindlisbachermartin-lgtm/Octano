<script setup lang="ts">
import { Check, X, Search } from 'lucide-vue-next'
import type { Invoice, Order, Vehicle } from '~/types'
import type { PartEditorItem } from '~/types/partEditor'
import { orderPartInvoiceItem } from '~/utils/orderWorkflow'

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
  items: [] as PartEditorItem[],
})
const partsAmount = computed(() => form.value.items.reduce((sum, item) =>
  sum + Number(item.quantity || 0) * Number(item.unitPrice || 0), 0))
const netAmount = computed(() => Number(form.value.labor || 0) + partsAmount.value)
const invoiceType = computed(() => invoiceTypeForVat(db.value.issuerVatCondition || 'IVA Responsable Inscripto',
  selectedVehicle.value ? client(selectedVehicle.value.client)?.vatCondition || 'Consumidor Final' : 'Consumidor Final'))
const vatAmount = computed(() => invoiceType.value === 'C' ? 0 : Math.round(netAmount.value * 0.21 * 100) / 100)
const totalAmount = computed(() => netAmount.value + vatAmount.value)

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
        items: (draftParts ?? (props.order?.parts || []).map(orderPartInvoiceItem)).map((item, index) => ({
          key: Date.now() + index,
          partId: 'custom',
          customName: item.description,
          searchQuery: 'Personalizado',
          quantity: item.quantity,
          unitPrice: item.unitPrice,
        })),
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
    || form.value.items.some((item) => !item.partId || !item.customName.trim()
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
        description: item.customName.trim(), quantity: Number(item.quantity),
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

        <CommonDependentFields :ready="!!selectedVehicle">
        <div class="form-grid invoice-main-fields">
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

        </div>

        <CommonEditorRepuestos v-model="form.items" :min-quantity="0.01" />

        <div class="invoice-totals">
          <div><span>Subtotal</span><strong>{{ money(netAmount) }}</strong></div>
          <div><span>{{ invoiceType === 'C' ? 'IVA (no corresponde)' : 'IVA (21%)' }}</span><strong>{{ money(vatAmount) }}</strong></div>
          <div><span>Total a cobrar</span><strong>{{ money(totalAmount) }}</strong></div>
        </div>

        </CommonDependentFields>
        <p v-if="formError" class="error-message" role="alert">
          {{ formError }}
        </p>
      </div>

      <footer class="dialog-footer modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary" :disabled="!selectedVehicle">
          <Check :size="16" />Guardar detalle y continuar
        </button>
      </footer>
    </form>
  </CommonFormPage>
</template>

<style scoped>
.form-fields .integration-notice { background: transparent !important; border: 0 !important; margin: 0; padding: 0; font-size: 11px; }
.form-page .invoice-main-fields { grid-template-columns: minmax(0, 1fr) minmax(0, 1.8fr) minmax(0, 1fr); }
.invoice-main-fields small { display: block; font-size: 10px; line-height: 1.3; margin-top: 4px; }
.invoice-totals { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; padding-top: 10px; border-top: 1px solid var(--line); }
.invoice-totals > div { display: flex; flex-direction: column; gap: 4px; font-size: 11px; }
.invoice-totals > div:last-child { text-align: right; }
@media (max-width: 540px) { .form-page .invoice-main-fields { grid-template-columns: 1fr; } }
</style>
