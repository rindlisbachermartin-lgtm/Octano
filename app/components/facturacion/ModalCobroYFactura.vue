<script setup lang="ts">
import {
  X,
  CreditCard,
  Receipt,
  Check,
  CheckCircle2,
  Sparkles,
  ShieldCheck,
  Building2,
  User,
  Banknote,
  Smartphone,
  ArrowRight,
  Loader2
} from 'lucide-vue-next'
import type { Order, Invoice, Vehicle } from '~/types'

const props = defineProps<{
  open: boolean
  order: Order | null
  initialTab?: 'cobro' | 'arca'
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'completed', invoice: Invoice): void
}>()

const { db, vehicle, vehicleName, client } = useDatabase()
const { money } = useHelpers()
const { notify } = useWorkshopToast()

useModalEscape(() => props.open, () => emit('close'))

const activeTab = ref<'cobro' | 'arca'>('cobro')

watch(() => props.initialTab, (t) => {
  if (t) activeTab.value = t
}, { immediate: true })

const currentVehicle = computed(() =>
  props.order ? vehicle(props.order.vehicle) : null
)

const currentOwner = computed(() =>
  currentVehicle.value?.client ? client(currentVehicle.value.client) : null
)

// Calculate amounts from order
const laborCost = ref(75000)
const partsCost = computed(() =>
  (props.order?.parts || []).reduce((sum, p) => sum + (p.price || 0), 0)
)
const totalAmount = computed(() => laborCost.value + partsCost.value)

// Net & VAT calculations for Factura A / B
const netAmount = computed(() => Math.round(totalAmount.value / 1.21 * 100) / 100)
const vatAmount = computed(() => Math.round((totalAmount.value - netAmount.value) * 100) / 100)

// TAB 1: REGISTRAR COBRO
const paymentMethod = ref('Efectivo')
const paymentNote = ref('')
const paymentSuccess = ref(false)

// TAB 2: GENERAR FACTURA ARCA
const invoiceType = ref<'B' | 'A'>('B')
const ptoVta = ref(3)
const clientDoc = ref('')
const clientName = ref('')
const clientVat = ref('Consumidor Final')
const markAsPaid = ref(true)
const arcaProcessing = ref(false)
const arcaStep = ref('')

watch(() => [props.open, props.order, currentOwner.value], () => {
  if (props.open && props.order) {
    paymentSuccess.value = false
    arcaProcessing.value = false
    arcaStep.value = ''
    clientDoc.value = currentOwner.value?.doc || '32.456.789'
    clientName.value = currentOwner.value?.name || 'Cliente de Taller'
    if (currentOwner.value?.name?.includes('González') || currentOwner.value?.name?.includes('Martínez')) {
      clientVat.value = 'Consumidor Final'
      invoiceType.value = 'B'
    }
  }
}, { immediate: true })

watch(invoiceType, (t) => {
  if (t === 'A') {
    clientVat.value = 'IVA Responsable Inscripto'
    if (!clientDoc.value.startsWith('30-') && !clientDoc.value.startsWith('20-')) {
      clientDoc.value = '30-71452918-7'
    }
  } else {
    clientVat.value = 'Consumidor Final'
    if (clientDoc.value.length > 11) {
      clientDoc.value = currentOwner.value?.doc || '32.456.789'
    }
  }
})

// Action: Confirmar Cobro Interno
function submitPayment() {
  if (!props.order) return

  const o = props.order
  const nextId = Math.max(126, ...db.value.invoices.map((i) => i.id)) + 1

  // Check if an invoice already exists for this order
  let inv = db.value.invoices.find((i) => i.orderId === o.id || (i.vehicle === o.vehicle && i.status === 'Pendiente'))

  if (inv) {
    inv.status = 'Cobrado'
    inv.paymentMethod = paymentMethod.value
    inv.date = new Date().toISOString().slice(0, 10)
  } else {
    inv = {
      id: nextId,
      orderId: o.id,
      vehicle: o.vehicle,
      description: o.service,
      total: totalAmount.value,
      type: 'X', // Recibo de caja / cobro interno
      status: 'Cobrado',
      date: new Date().toISOString().slice(0, 10),
      isFiscal: false,
      paymentMethod: paymentMethod.value,
      laborAmount: laborCost.value,
      partsAmount: partsCost.value,
    }
    db.value.invoices.unshift(inv)
  }

  notify(`Cobro de ${money(totalAmount.value)} registrado (${paymentMethod.value}).`)
  emit('completed', inv)
}

// Action: Solicitar CAE y Emitir Factura ARCA
async function submitArcaInvoice() {
  if (!props.order) return
  arcaProcessing.value = true
  arcaStep.value = 'Conectando con servidores de ARCA (Web Service WSFE)…'

  await new Promise((r) => setTimeout(r, 700))
  arcaStep.value = 'Validando CUIT y correlatividad del Punto de Venta 0003…'

  await new Promise((r) => setTimeout(r, 800))
  arcaStep.value = 'Autorizando comprobante y solicitando CAE…'

  await new Promise((r) => setTimeout(r, 600))

  const o = props.order
  const nextId = Math.max(126, ...db.value.invoices.map((i) => i.id)) + 1
  const generatedCae = String(Math.floor(74000000000000 + Math.random() * 999999999999))
  
  const vtoDate = new Date()
  vtoDate.setDate(vtoDate.getDate() + 10)

  // Build items from parts + labor
  const invoiceItems = [
    {
      description: `Mano de obra: ${o.service}`,
      quantity: 1,
      unitPrice: laborCost.value,
      total: laborCost.value,
    },
    ...o.parts.map((p) => ({
      description: p.name,
      quantity: 1,
      unitPrice: p.price,
      total: p.price,
    }))
  ]

  // Find or create invoice
  let inv = db.value.invoices.find((i) => i.orderId === o.id)
  if (!inv) {
    inv = {
      id: nextId,
      orderId: o.id,
      vehicle: o.vehicle,
      description: o.service,
      total: totalAmount.value,
      type: invoiceType.value,
      status: markAsPaid.value ? 'Cobrado' : 'Pendiente',
      date: new Date().toISOString().slice(0, 10),
      isFiscal: true,
      cae: generatedCae,
      caeVto: vtoDate.toISOString().slice(0, 10),
      ptoVta: ptoVta.value,
      nroCmp: nextId,
      paymentMethod: markAsPaid.value ? paymentMethod.value : null,
      laborAmount: laborCost.value,
      partsAmount: partsCost.value,
      netAmount: netAmount.value,
      vatAmount: vatAmount.value,
      items: invoiceItems,
      clientName: clientName.value,
      clientDoc: clientDoc.value,
      clientVatCondition: clientVat.value,
    }
    db.value.invoices.unshift(inv)
  } else {
    inv.type = invoiceType.value
    inv.isFiscal = true
    inv.cae = generatedCae
    inv.caeVto = vtoDate.toISOString().slice(0, 10)
    inv.ptoVta = ptoVta.value
    inv.nroCmp = inv.id
    inv.total = totalAmount.value
    inv.netAmount = netAmount.value
    inv.vatAmount = vatAmount.value
    inv.items = invoiceItems
    inv.clientName = clientName.value
    inv.clientDoc = clientDoc.value
    inv.clientVatCondition = clientVat.value
    if (markAsPaid.value) {
      inv.status = 'Cobrado'
      inv.paymentMethod = paymentMethod.value
    }
  }

  arcaProcessing.value = false
  notify(`Factura ${invoiceType.value} Nº 0003-${String(inv.id).padStart(8, '0')} autorizada con éxito por ARCA. CAE: ${generatedCae}.`)
  emit('completed', inv)
}
</script>

<template>
  <dialog v-if="open && order" class="dialog modal-billing-action" open>
    <div class="dialog-header">
      <div>
        <span class="eyebrow">FACTURACIÓN & COBROS / ORDEN #{{ order.id }}</span>
        <h2>Gestión de Cobro y Factura ARCA</h2>
      </div>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <!-- Order Summary Bar -->
    <div class="order-summary-strip">
      <div class="summary-left">
        <strong>{{ vehicleName(order.vehicle) }}</strong>
        <span class="plate small-plate">{{ currentVehicle?.plate }}</span>
        <span class="muted">· {{ currentOwner?.name }}</span>
      </div>
      <div class="summary-total">
        <span class="total-lbl">Total orden:</span>
        <strong class="total-val">{{ money(totalAmount) }}</strong>
      </div>
    </div>

    <!-- Tab navigation -->
    <div class="tabs-nav-bar">
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'cobro' }"
        @click="activeTab = 'cobro'"
      >
        <CreditCard :size="16" />
        <span>1. Registrar Cobro (Caja de Taller)</span>
      </button>
      <button
        class="tab-btn arca-tab"
        :class="{ active: activeTab === 'arca' }"
        @click="activeTab = 'arca'"
      >
        <Receipt :size="16" />
        <span>2. Generar Factura Oficial ARCA (con CAE)</span>
      </button>
    </div>

    <!-- TAB 1: REGISTRAR COBRO -->
    <div v-if="activeTab === 'cobro'" class="tab-content">
      <div class="notice-box info-blue">
        <Banknote :size="18" />
        <div>
          <strong>Cobro directo de taller / Recibo X</strong>
          <p>Registrá el dinero entrante en caja sin generar comprobante fiscal en ARCA. Podés emitir la factura electrónica más tarde si el cliente la solicita.</p>
        </div>
      </div>

      <div class="billing-form-grid">
        <div class="form-group">
          <label>Medio de pago:</label>
          <select v-model="paymentMethod" class="input-select">
            <option value="Efectivo">Efectivo</option>
            <option value="Transferencia bancaria">Transferencia bancaria (Alias / CBU)</option>
            <option value="Tarjeta de débito">Tarjeta de débito</option>
            <option value="Tarjeta de crédito">Tarjeta de crédito</option>
            <option value="Mercado Pago / QR">Mercado Pago / Billetera Virtual</option>
          </select>
        </div>

        <div class="form-group">
          <label>Monto a cobrar:</label>
          <div class="amount-display-box">
            <span>Importe final:</span>
            <strong>{{ money(totalAmount) }}</strong>
          </div>
        </div>

        <div class="form-group full-width">
          <label>Observaciones de cobro (opcional):</label>
          <input
            v-model="paymentNote"
            type="text"
            placeholder="Ej: Pago total recibido por mostrador. Se entrega vehículo."
            class="input-text"
          />
        </div>
      </div>

      <div class="tab-footer">
        <button class="button outlined" @click="emit('close')">Cancelar</button>
        <button class="button primary" @click="submitPayment">
          <Check :size="16" /> Registrar cobro de {{ money(totalAmount) }}
        </button>
      </div>
    </div>

    <!-- TAB 2: GENERAR FACTURA ARCA -->
    <div v-else class="tab-content">
      <!-- Loading indicator when communicating with ARCA -->
      <div v-if="arcaProcessing" class="arca-loading-screen">
        <Loader2 :size="40" class="spin-icon" />
        <h3>Comunicando con ARCA…</h3>
        <p>{{ arcaStep }}</p>
      </div>

      <div v-else>
        <div class="notice-box arca-banner">
          <ShieldCheck :size="20" style="color: #2563eb;" />
          <div>
            <strong>Facturación Electrónica Oficial (ARCA)</strong>
            <p>Se solicitará el Código de Autorización Electrónico (CAE) a través del Web Service de ARCA y se generará el documento oficial con código QR fiscal.</p>
          </div>
          <span class="badge green">WSFE ONLINE</span>
        </div>

        <div class="billing-form-grid">
          <!-- Tipo de Factura -->
          <div class="form-group">
            <label>Tipo de comprobante:</label>
            <div class="radio-pill-group">
              <label class="radio-pill" :class="{ selected: invoiceType === 'B' }">
                <input v-model="invoiceType" type="radio" value="B" />
                <strong>Factura B</strong>
                <small>Consumidor Final</small>
              </label>
              <label class="radio-pill" :class="{ selected: invoiceType === 'A' }">
                <input v-model="invoiceType" type="radio" value="A" />
                <strong>Factura A</strong>
                <small>Resp. Inscripto (discrimina IVA)</small>
              </label>
            </div>
          </div>

          <!-- Punto de Venta -->
          <div class="form-group">
            <label>Punto de Venta:</label>
            <input type="text" value="0003 · Facturación Electrónica Web" disabled class="input-text disabled" />
          </div>

          <!-- Datos del cliente -->
          <div class="form-group">
            <label>Razón Social / Nombre:</label>
            <input v-model="clientName" type="text" class="input-text" />
          </div>

          <div class="form-group">
            <label>CUIT / DNI del receptor:</label>
            <input v-model="clientDoc" type="text" class="input-text" />
          </div>

          <!-- Desglose Impositivo -->
          <div class="form-group full-width breakdown-box">
            <div class="breakdown-header">
              <span>Desglose de la Orden de Trabajo #{{ order.id }}:</span>
            </div>
            <div class="breakdown-grid">
              <div class="b-line">
                <span>Mano de obra técnica:</span>
                <strong>{{ money(laborCost) }}</strong>
              </div>
              <div class="b-line">
                <span>Repuestos e insumos ({{ order.parts.length }} ítems):</span>
                <strong>{{ money(partsCost) }}</strong>
              </div>
              <div v-if="invoiceType === 'A'" class="b-line highlight-tax">
                <span>Subtotal Neto Gravado:</span>
                <span>{{ money(netAmount) }}</span>
              </div>
              <div v-if="invoiceType === 'A'" class="b-line highlight-tax">
                <span>IVA 21.00%:</span>
                <span>{{ money(vatAmount) }}</span>
              </div>
              <div class="b-line total-b-line">
                <span>Total a Facturar:</span>
                <strong class="total-big">{{ money(totalAmount) }}</strong>
              </div>
            </div>
          </div>

          <!-- Cobro Simultáneo Checkbox -->
          <div class="form-group full-width pay-toggle-box">
            <label class="toggle-row">
              <input v-model="markAsPaid" type="checkbox" class="cb-input" />
              <span>Registrar como cobrado al emitir</span>
            </label>
            <div v-if="markAsPaid" class="method-subselect">
              <label>Forma de pago:</label>
              <select v-model="paymentMethod" class="input-select small">
                <option value="Efectivo">Efectivo</option>
                <option value="Transferencia bancaria">Transferencia bancaria</option>
                <option value="Tarjeta de débito">Tarjeta de débito</option>
                <option value="Tarjeta de crédito">Tarjeta de crédito</option>
              </select>
            </div>
          </div>
        </div>

        <div class="tab-footer">
          <button class="button outlined" @click="emit('close')">Cancelar</button>
          <button class="button primary btn-emit-arca" @click="submitArcaInvoice">
            <Sparkles :size="16" /> Solicitar CAE y Emitir Factura {{ invoiceType }}
          </button>
        </div>
      </div>
    </div>
  </dialog>
</template>

<style scoped>
.modal-billing-action {
  max-width: 680px;
  width: 95vw;
}

.order-summary-strip {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px 14px;
  margin-bottom: 16px;
}

.summary-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
}

.small-plate {
  font-size: 11px;
  padding: 1px 6px;
}

.summary-total {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.total-lbl {
  font-size: 11.5px;
  color: #64748b;
}

.total-val {
  font-size: 17px;
  color: #0f172a;
  font-weight: 800;
}

/* Tabs Bar */
.tabs-nav-bar {
  display: flex;
  gap: 8px;
  border-bottom: 2px solid #e2e8f0;
  margin-bottom: 18px;
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  font-size: 13px;
  font-weight: 650;
  color: #64748b;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: all 0.15s ease;
  cursor: pointer;
}

.tab-btn:hover {
  color: #0f172a;
}

.tab-btn.active {
  color: #2563eb;
  border-bottom-color: #2563eb;
}

.tab-btn.arca-tab.active {
  color: #2563eb;
  border-bottom-color: #2563eb;
}

/* Notices */
.notice-box {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 12px 14px;
  border-radius: 10px;
  font-size: 12.5px;
  margin-bottom: 16px;
  line-height: 1.4;
}

.notice-box strong {
  display: block;
  font-size: 13px;
  margin-bottom: 2px;
}

.notice-box p {
  margin: 0;
  color: inherit;
  opacity: 0.85;
}

.info-blue {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #1e40af;
}

.arca-banner {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #166534;
}

/* Form Grid */
.billing-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-group.full-width {
  grid-column: 1 / -1;
}

.form-group label {
  font-size: 12px;
  font-weight: 700;
  color: #475569;
}

.input-select,
.input-text {
  padding: 8px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  font-size: 13px;
  background: #ffffff;
  color: #0f172a;
}

.input-text.disabled {
  background: #f1f5f9;
  color: #64748b;
  cursor: not-allowed;
}

.amount-display-box {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
}

.amount-display-box strong {
  font-size: 16px;
  color: #15803d;
}

/* Radio Pill Group */
.radio-pill-group {
  display: flex;
  gap: 8px;
}

.radio-pill {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 8px 10px;
  border: 1.5px solid #cbd5e1;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.radio-pill input {
  display: none;
}

.radio-pill:hover {
  border-color: #94a3b8;
}

.radio-pill.selected {
  background: #eff6ff;
  border-color: #2563eb;
  color: #1d4ed8;
}

.radio-pill strong {
  font-size: 13px;
}

.radio-pill small {
  font-size: 10.5px;
  color: #64748b;
}

/* Breakdown Box */
.breakdown-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 14px;
}

.breakdown-header {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  margin-bottom: 6px;
}

.breakdown-grid {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12.5px;
}

.b-line {
  display: flex;
  justify-content: space-between;
  color: #475569;
}

.b-line.highlight-tax {
  font-size: 12px;
  color: #2563eb;
}

.total-b-line {
  border-top: 1px solid #cbd5e1;
  padding-top: 6px;
  margin-top: 4px;
  font-size: 13px;
  color: #0f172a;
}

.total-big {
  font-size: 16px;
  font-weight: 800;
  color: #15803d;
}

/* Payment toggle */
.pay-toggle-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.toggle-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.cb-input {
  width: 17px;
  height: 17px;
  accent-color: #2563eb;
}

.method-subselect {
  display: flex;
  align-items: center;
  gap: 8px;
}

.method-subselect label {
  font-size: 12px;
  color: #64748b;
}

.input-select.small {
  padding: 4px 8px;
  font-size: 12px;
}

/* Footer */
.tab-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px solid #e2e8f0;
}

.btn-emit-arca {
  background: #2563eb;
}

/* Loading state */
.arca-loading-screen {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  text-align: center;
}

.spin-icon {
  animation: spin 1s linear infinite;
  color: #2563eb;
  margin-bottom: 14px;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.arca-loading-screen h3 {
  font-size: 17px;
  color: #0f172a;
  margin: 0 0 6px 0;
}

.arca-loading-screen p {
  font-size: 13px;
  color: #64748b;
  margin: 0;
}

:global(html.dark .order-summary-strip),
:global(html.dark .amount-display-box),
:global(html.dark .breakdown-box),
:global(html.dark .pay-toggle-box) {
  background: #202023;
  border-color: #3a3a3c;
  color: #f5f5f7;
}

:global(html.dark .tabs-nav-bar),
:global(html.dark .tab-footer),
:global(html.dark .total-b-line) {
  border-color: #3a3a3c;
}

:global(html.dark .total-val),
:global(html.dark .total-b-line),
:global(html.dark .arca-loading-screen h3) {
  color: #f5f5f7;
}

:global(html.dark .total-lbl),
:global(html.dark .form-group label),
:global(html.dark .breakdown-header),
:global(html.dark .b-line),
:global(html.dark .method-subselect label),
:global(html.dark .radio-pill small),
:global(html.dark .arca-loading-screen p) {
  color: #a1a1a6;
}

:global(html.dark .tab-btn) {
  color: #a1a1a6;
}

:global(html.dark .tab-btn:hover),
:global(html.dark .tab-btn.active) {
  color: #64d2ff;
}

:global(html.dark .info-blue),
:global(html.dark .radio-pill.selected) {
  background: rgba(10, 132, 255, 0.14);
  border-color: rgba(10, 132, 255, 0.35);
  color: #9bd5ff;
}

:global(html.dark .arca-banner) {
  background: rgba(48, 209, 88, 0.12);
  border-color: rgba(48, 209, 88, 0.3);
  color: #8ee6a7;
}

:global(html.dark .radio-pill) {
  border-color: #48484a;
}

:global(html.dark .input-text.disabled) {
  background: #252528 !important;
  color: #a1a1a6 !important;
}
</style>
