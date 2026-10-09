<script setup lang="ts">
import {
  Plus,
  Receipt,
  CreditCard,
  FileText,
} from 'lucide-vue-next'
import type { Budget, Invoice, Order } from '~/types'
import type { BillingEntry, BillingSection } from '~/utils/billingEntries'
import { billingEntryBudget, billingDateMatches, billingPeriodValue } from '~/utils/billingEntries'

const { db, owner, vehicle, vehicleName } = useDatabase()
const { money, matches } = useHelpers()
const route = useRoute()
const { notify } = useWorkshopToast()

const formModalOpen = ref(false)
const orderForInvoiceForm = ref<Order | null>(null)
const invoiceForForm = ref<Invoice | null>(null)
const billingSection = ref<BillingSection>('para-cobrar')
const budgetFilter = ref<'todos' | 'con-presupuesto' | 'sin-presupuesto'>('todos')
const budgetFilters = [
  { value: 'todos', label: 'Todos' },
  { value: 'con-presupuesto', label: 'Con presupuesto' },
  { value: 'sin-presupuesto', label: 'Sin presupuesto' },
] as const
const billingModalOpen = ref(false)
const billingModalTab = ref<'cobro' | 'arca'>('cobro')
const selectedOrderForBilling = ref<Order | null>(null)
const selectedInvoiceForBilling = ref<Invoice | null>(null)

const arcaViewerOpen = ref(false)
const selectedInvoiceForViewer = ref<Invoice | null>(null)
const budgetViewerOpen = ref(false)
const selectedBudgetForViewer = ref<Budget | null>(null)

const entries = computed(() => billingEntries(db.value.orders, db.value.invoices, db.value.quotes))
const dateFilter = ref('')
const selectedDate = ref('')
const calendarDate = computed({
  get: () => selectedDate.value,
  set: (date: string) => {
    selectedDate.value = date
    dateFilter.value = billingPeriodValue(date, 'month')
  },
})
const personFilter = ref('')
const debouncedPersonFilter = useDebouncedValue(personFilter)
const personMenuRequested = ref(false)
const personMenuOpen = computed({
  get: () => personMenuRequested.value && !!personFilter.value.trim(),
  set: (value: boolean) => { personMenuRequested.value = value && !!personFilter.value.trim() },
})
const personOptions = computed(() => debouncedPersonFilter.value.trim() && personFilter.value.trim() === debouncedPersonFilter.value.trim()
  ? [...new Set([...db.value.clients.map(client => client.name), ...entries.value.map(entryPerson)])]
      .filter(name => name && matches(debouncedPersonFilter.value, name))
      .sort((a, b) => a.localeCompare(b, 'es'))
  : [])
const filteredEntries = computed(() => entries.value.filter((entry) =>
  billingDateMatches(entry.invoice?.date ?? entry.order?.date, 'month', dateFilter.value)
  && matches(debouncedPersonFilter.value, entryPerson(entry))))
const filteredInvoices = computed(() => filteredEntries.value.flatMap(entry => entry.invoice ? [entry.invoice] : []))
const collected = computed(() => filteredInvoices.value.filter(invoice => invoice.status === 'Cobrada'))
const unpaid = computed(() => filteredInvoices.value.filter(invoice => invoice.status !== 'Cobrada' && (invoice.isFiscal || invoice.cae)))
const revenue = computed(() => collected.value.reduce((sum, invoice) => sum + invoice.total, 0))
const visibleEntries = computed(() => filteredEntries.value.filter((entry) => {
  if (entry.section !== billingSection.value) return false
  if (billingSection.value !== 'sin-presupuesto') return true
  if (budgetFilter.value === 'todos') return true
  return budgetFilter.value === 'con-presupuesto' ? !!entry.budget : !entry.budget
}))
const billingSections: { value: BillingSection; label: string; empty: string }[] = [
  { value: 'para-cobrar', label: 'Emitidas', empty: 'No hay facturas emitidas pendientes de cobro.' },
  { value: 'cobradas', label: 'Cobradas', empty: 'Todavía no hay facturas cobradas.' },
  { value: 'sin-presupuesto', label: 'Sin emitir', empty: 'No hay órdenes de trabajo finalizadas pendientes de emitir factura.' },
]

function entryVehicle(entry: BillingEntry) {
  return entry.invoice?.vehicle ?? entry.order!.vehicle
}

function entryPerson(entry: BillingEntry) {
  return owner(entryVehicle(entry))?.name || entry.invoice?.clientName || ''
}

function entryAmount(entry: BillingEntry) {
  const additional = entry.order?.parts.filter((part) => part.additional).reduce((sum, part) => sum + part.price, 0) || 0
  return entry.invoice?.total ?? (entry.budget ? budgetAmounts(entry.budget).total + Math.round((additional + (entry.order?.laborAmount ?? entry.budget.labor) - entry.budget.labor) * 1.21 * 100) / 100 : null)
}

onMounted(() => {
  const order = db.value.orders.find((o) => o.id === Number(route.query.orden) && o.status === 'Finalizado')
  if (!order || getInvoiceForOrder(order)) return
  billingSection.value = 'sin-presupuesto'
  budgetFilter.value = getBudgetForOrder(order) ? 'con-presupuesto' : 'sin-presupuesto'
  openBillingForOrder(order, 'arca')
})

function openEntryDetail(entry: BillingEntry) {
  if (entry.section === 'sin-presupuesto') {
    if (entry.budget) {
      selectedBudgetForViewer.value = entry.budget
      budgetViewerOpen.value = true
    } else openInvoiceForm(entry.order, 'arca', entry.invoice)
    return
  }
  if (entry.invoice) openArcaViewer(entry.invoice)
}

function openEntryBilling(entry: BillingEntry, tab: 'cobro' | 'arca') {
  if (entry.section === 'cobradas' || (entry.section === 'para-cobrar' && tab === 'arca')) return
  if (entry.invoice) openBillingForInvoice(entry.invoice, tab)
  else if (entry.order) openBillingForOrder(entry.order, tab)
}

function getBudgetForOrder(order: Order) {
  return billingEntryBudget(order, db.value.quotes)
}

function getInvoiceForOrder(order: Order) {
  return db.value.invoices.find((invoice) => invoice.orderId === order.id)
}

function openInvoiceForm(order: Order | null = null, tab: 'cobro' | 'arca' = 'arca', invoice: Invoice | null = null) {
  billingModalTab.value = tab
  orderForInvoiceForm.value = order
  invoiceForForm.value = invoice
  formModalOpen.value = true
}

function openBillingForOrder(order: Order, tab: 'cobro' | 'arca', invoice: Invoice | null = null) {
  const currentInvoice = invoice ?? getInvoiceForOrder(order)
  if (currentInvoice?.status === 'Cobrada' || (tab === 'arca' && (currentInvoice?.isFiscal || currentInvoice?.cae))) return
  if (!invoice && !getBudgetForOrder(order) && !getInvoiceForOrder(order)) {
    openInvoiceForm(order, tab)
    return
  }
  selectedOrderForBilling.value = order
  selectedInvoiceForBilling.value = invoice
  billingModalTab.value = tab
  billingModalOpen.value = true
}

function openArcaViewer(inv: Invoice) {
  selectedInvoiceForViewer.value = inv
  arcaViewerOpen.value = true
}

function handleBillingCompleted(invoice: Invoice) {
  billingModalOpen.value = false
  billingSection.value = invoice.status === 'Cobrada' ? 'cobradas' : 'para-cobrar'
  arcaViewerOpen.value = false
  selectedInvoiceForViewer.value = null
}

function handleCreated(invoice: Invoice) {
  formModalOpen.value = false
  orderForInvoiceForm.value = null
  invoiceForForm.value = null
  billingModalOpen.value = false
  arcaViewerOpen.value = false
  billingSection.value = 'sin-presupuesto'
  budgetFilter.value = 'todos'
  notify(`Comprobante #${invoice.id} guardado. Podés emitirlo o registrar el cobro desde el listado.`)
}

function getOrderForInvoice(inv: Invoice): Order | undefined {
  if (inv.orderId) return db.value.orders.find((o) => o.id === inv.orderId)
  return undefined
}

function openBillingForInvoice(invoice: Invoice, tab: 'cobro' | 'arca') {
  const order = getOrderForInvoice(invoice) || {
    id: -invoice.id, vehicle: invoice.vehicle, service: invoice.description,
    status: 'Finalizado', parts: [], diagnosis: '', mechanic: 'Nicolás', bay: null,
    date: invoice.date, time: '10:00', tasks: [], notes: '', photos: [],
  }
  openBillingForOrder(order, tab, invoice)
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          TALLER CENTRAL / FACTURACIÓN & ARCA
        </div>
        <h1>Las cuentas, al día.</h1>
      </div>
      <div style="display: flex; gap: 8px; flex-wrap: wrap;">
        <button class="button primary" @click="openInvoiceForm()">
          <Plus :size="17" />Nueva factura manual
        </button>
      </div>
    </section>

    <section class="stats-grid summary-stats invoice-stats">
      <div class="stat-card summary-logo-card">
        <CommonOctanoLogo class="summary-logo" />
        <div>Total cobrado</div>
        <strong>{{ money(revenue) }}</strong>
        <small>{{ collected.length }} facturas cobradas</small>
      </div>
      <div class="stat-card">
        <div>Pendiente de cobro</div>
        <strong>{{ money(unpaid.reduce((sum, invoice) => sum + invoice.total, 0)) }}</strong>
        <small>{{ unpaid.length }} {{ unpaid.length === 1 ? 'factura emitida' : 'facturas emitidas' }}</small>
      </div>
      <div class="stat-card">
        <div>Comprobantes ARCA emitidos</div>
        <strong>{{ filteredInvoices.filter((invoice) => invoice.isFiscal || invoice.cae).length }}</strong>
        <small>Pto. Venta 0003</small>
      </div>
    </section>

    <section class="panel ready-orders-panel">
      <div class="billing-filters">
        <CommonDatePicker v-model="calendarDate" label="Mes y año" type="month" year-selection compact />
        <label>Cliente
          <UInputMenu v-model="personFilter" v-model:open="personMenuOpen" mode="autocomplete" :items="personOptions" ignore-filter :open-on-focus="false" :open-on-click="false" :reset-search-term-on-blur="false" placeholder="Escribí el nombre del cliente…" aria-label="Filtrar por cliente" autocomplete="off" class="billing-client-search" :ui="{ base: 'h-10 w-full' }" @input="personMenuRequested = true" @focus="personMenuRequested = true">
            <template #empty><span>{{ personFilter.trim() !== debouncedPersonFilter.trim() ? 'Buscando clientes…' : 'No se encontraron clientes.' }}</span></template>
          </UInputMenu>
        </label>
        <button v-if="dateFilter || personFilter" class="button small outlined" @click="dateFilter = ''; selectedDate = ''; personFilter = ''">Limpiar filtros</button>
      </div>
      <div class="segmented billing-order-filters" aria-label="Apartados de facturación">
        <button
          v-for="section in billingSections"
          :key="section.value"
          type="button"
          :class="{ selected: billingSection === section.value }"
          :aria-pressed="billingSection === section.value"
          @click="billingSection = section.value"
        >
          {{ section.label }} ({{ filteredEntries.filter((entry) => entry.section === section.value).length }})
        </button>
      </div>
      <p v-if="billingSection === 'sin-presupuesto'" class="billing-section-description muted">
        Armá la factura desde la orden de trabajo con su presupuesto asociado. Si no tiene presupuesto, agregá los repuestos y la mano de obra.
      </p>
      <div v-if="billingSection === 'sin-presupuesto'" class="segmented status-filters billing-budget-filters" role="group" aria-label="Filtrar comprobantes sin emitir por presupuesto">
        <button v-for="filter in budgetFilters" :key="filter.value" type="button" :class="{ selected: budgetFilter === filter.value }" :aria-pressed="budgetFilter === filter.value" @click="budgetFilter = filter.value">{{ filter.label }} ({{ filteredEntries.filter(entry => entry.section === 'sin-presupuesto' && (filter.value === 'todos' || (filter.value === 'con-presupuesto' ? !!entry.budget : !entry.budget))).length }})</button>
      </div>

      <div class="ready-orders-grid">
        <div
          v-for="entry in visibleEntries"
          :key="entry.key"
          class="ready-order-card"
          tabindex="0"
          :aria-label="(entry.invoice ? (entry.section === 'sin-presupuesto' ? 'Borrador #' : 'Factura #') + entry.invoice.id : 'Orden de trabajo #' + entry.order?.id) + '. ' + (entry.section === 'sin-presupuesto' && !entry.budget ? 'Click para armar factura' : 'Click para ver detalle')"
          @click="openEntryDetail(entry)"
          @keydown.enter.self="openEntryDetail(entry)"
          @keydown.space.self.prevent="openEntryDetail(entry)"
        >
          <div class="ready-card-top">
            <div>
              <span class="plate small-plate">{{ vehicle(entryVehicle(entry))?.plate }}</span>
              <h4>{{ vehicleName(entryVehicle(entry)) }}</h4>
              <small class="muted">
                {{ owner(entryVehicle(entry))?.name || entry.invoice?.clientName }}
                · {{ entry.invoice ? (entry.section === 'sin-presupuesto' ? 'Borrador #' : 'Factura #') + entry.invoice.id : 'OT #' + entry.order?.id }}
              </small>
            </div>
          </div>
          <p class="ready-service-desc">{{ entry.invoice?.description || entry.order?.service }}</p>
          <div class="billing-card-status">
            <span class="badge" :class="entry.section === 'cobradas' ? 'green' : 'neutral'">
              {{ entry.section === 'sin-presupuesto' ? 'Sin emitir' : entry.invoice?.status }}
            </span>
            <span v-if="entry.budget" class="badge neutral">Presupuesto #{{ entry.budget.id }}</span>
            <span v-else-if="!entry.invoice" class="badge neutral">Sin presupuesto</span>
            <span v-if="entry.invoice?.isFiscal || entry.invoice?.cae" class="badge neutral">ARCA</span>
          </div>
          <small v-if="entry.invoice" class="muted">
            {{ entry.invoice.date }}<template v-if="entry.invoice.paymentMethod"> · {{ entry.invoice.paymentMethod }}</template>
          </small>
          <small v-if="entry.invoice?.paymentDate" class="muted">Cobrado el {{ entry.invoice.paymentDate }}</small>
          <small v-if="entry.invoice?.paymentNote" class="muted">{{ entry.invoice.paymentNote }}</small>
          <div class="order-price-badge">
            <template v-if="entryAmount(entry) !== null">
              <span class="price-lbl">{{ entry.section === 'sin-presupuesto' ? (entry.budget ? 'Total presupuesto' : 'Total a emitir') : 'Total factura' }}</span>
              <strong class="price-num">{{ money(entryAmount(entry)!) }}</strong>
            </template>
            <span v-else class="muted">Importe por definir</span>
          </div>
          <span class="budget-detail-hint">{{ entry.section === 'sin-presupuesto' && !entry.budget ? 'Click para armar factura' : 'Click para ver detalle' }}</span>
          <div v-if="entry.section === 'sin-presupuesto'" class="ready-card-actions">
            <button
              type="button"
              class="button small primary"
              @click.stop="openEntryBilling(entry, 'arca')"
            >
              <Receipt :size="14" /> Emitir con ARCA
            </button>
              <button
                type="button"
                class="button small btn-cobro"
                @click.stop="openEntryBilling(entry, 'cobro')"
              >
                <CreditCard :size="14" /> Registrar cobro sin ARCA
              </button>
          </div>
          <div v-else-if="entry.section === 'para-cobrar'" class="ready-card-actions">
            <button type="button" class="button small primary" @click.stop="openEntryBilling(entry, 'cobro')">
              <CreditCard :size="14" /> Registrar cobro manual
            </button>
          </div>
        </div>
      </div>
      <div v-if="!visibleEntries.length" class="empty-state invoice-empty-state">
        <FileText :size="32" class="muted" />
        <h3>{{ dateFilter || debouncedPersonFilter.trim() ? 'No hay comprobantes que coincidan con los filtros.' : billingSection === 'sin-presupuesto' ? (budgetFilter === 'todos' ? 'No hay comprobantes pendientes de emitir.' : `No hay comprobantes sin emitir ${budgetFilter === 'con-presupuesto' ? 'con' : 'sin'} presupuesto.`) : billingSections.find((section) => section.value === billingSection)?.empty }}</h3>
      </div>
    </section>

    <PresupuestosModalCompartirPresupuesto
      :open="budgetViewerOpen"
      :budget="selectedBudgetForViewer"
      @close="budgetViewerOpen = false"
    />

    <!-- Modal Formulario Manual -->
    <FacturacionModalFactura
      :open="formModalOpen"
      :order="orderForInvoiceForm"
      :invoice="invoiceForForm"
      @close="formModalOpen = false"
      @created="handleCreated"
    />

    <!-- Modal Cobro y Factura ARCA desde Orden -->
    <FacturacionModalCobroYFactura
      :open="billingModalOpen"
      :order="selectedOrderForBilling"
      :invoice="selectedInvoiceForBilling"
      :initial-tab="billingModalTab"
      @close="billingModalOpen = false"
      @completed="handleBillingCompleted"
    />

    <!-- Visor de Boceto Legal Factura ARCA -->
    <FacturacionModalVisorFacturaArca
      :open="arcaViewerOpen"
      :invoice="selectedInvoiceForViewer"
      @close="arcaViewerOpen = false"
    />
  </div>
</template>

<style scoped>
.billing-filters { display: flex; gap: 12px; align-items: end; flex-wrap: wrap; margin-bottom: 16px; }
.billing-filters label { display: grid; gap: 7px; font-size: 12px; color: var(--muted); }
.billing-filters input, .billing-filters select { height: 40px; min-height: 40px; box-sizing: border-box; border: 1px solid var(--line); border-radius: 8px; padding: 6px 10px; background: var(--surface); color: inherit; }
.billing-filters :deep(.date-picker-trigger) { height: 40px; min-height: 40px; box-sizing: border-box; padding: 6px 10px; }
.billing-client-search { width: 240px; max-width: 100%; }
.billing-budget-filters { margin-bottom: 18px; flex-wrap: wrap; }
.billing-order-filters { margin-bottom: 16px; flex-wrap: wrap; }
.billing-card-status { display: flex; flex-wrap: wrap; gap: 6px; }
.billing-section-description { margin: 0 0 16px; font-size: 13px; }
.ready-card-top h4 { margin: 4px 0 2px; }
.invoice-stats { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.ready-orders-panel {
  padding: 18px 20px;
  margin-bottom: 22px;
  background: #ffffff;
  border: 1.5px solid #dbeafe;
}

:global(html.dark .ready-orders-panel) {
  background: #252528;
  border-color: rgba(255, 255, 255, 0.08);
}

.panel-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.header-tag-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-tag-group h3 {
  margin: 0;
  font-size: 15px;
  color: #0f172a;
}

:global(html.dark .header-tag-group h3) {
  color: #f8fafc;
}

.ready-orders-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(290px, 1fr));
  gap: 14px;
}

.ready-order-card {
  cursor: pointer;
  background: #f8fafc;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 10px;
  transition: all 0.15s ease;
}

.budget-detail-hint {
  font-size: 13px;
  font-weight: 700;
  color: #334155;
}

:global(html.dark .budget-detail-hint) {
  color: #e2e8f0;
}

:global(html.dark .ready-order-card) {
  background: #2c2c2e;
  border-color: rgba(255, 255, 255, 0.1);
}

.ready-order-card:hover {
  border-color: #bfdbfe;
  transform: translateY(-1px);
}

:global(html.dark .ready-order-card:hover) {
  border-color: rgba(255, 255, 255, 0.22);
}

.ready-order-card:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: 3px;
}

.ready-card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}

.order-price-badge {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid var(--line);
}

.price-lbl {
  display: block;
  font-size: 12px;
  color: #64748b;
}

.price-num {
  font-size: 18px;
  color: #0f172a;
  font-weight: 850;
}

:global(html.dark .order-price-badge .price-num) {
  color: #f5f5f7;
}

.ready-service-desc {
  font-size: 12.5px;
  color: #475569;
  margin: 0;
  line-height: 1.4;
}

:global(html.dark .ready-service-desc) {
  color: #cbd5e1;
}

.ready-card-actions {
  display: flex;
  gap: 8px;
  margin-top: 6px;
  flex-wrap: wrap;
}

.button.btn-cobro {
  background: #ffffff !important;
  border-color: #cbd5e1 !important;
  color: #0f172a !important;
}

.button.btn-cobro:hover {
  background: #f8fafc !important;
  border-color: #94a3b8 !important;
}

:global(html.dark .ready-card-actions .button.btn-cobro) {
  background: #3a3a3c !important;
  border-color: #52525b !important;
  color: #f5f5f7 !important;
}

:global(html.dark .ready-card-actions .button.btn-cobro:hover) {
  background: #48484a !important;
  border-color: #71717a !important;
}

.btn-arca {
  background: #2563eb;
  border-color: #2563eb;
}

.btn-arca:hover {
  background: #1d4ed8;
}

.row-actions-group {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
