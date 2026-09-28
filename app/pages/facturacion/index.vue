<script setup lang="ts">
import {
  Plus,
  Receipt,
  CreditCard,
  CircleCheck,
  ShieldCheck,
  FileText,
  Printer,
  Sparkles,
  ArrowRight,
  ClipboardCheck,
  Eye
} from 'lucide-vue-next'
import type { Invoice, Order } from '~/types'

const { db, revenue, unpaid, owner, vehicle, vehicleName } = useDatabase()
const { money, statusClass } = useHelpers()
const { notify } = useWorkshopToast()

const formModalOpen = ref(false)
const billingModalOpen = ref(false)
const billingModalTab = ref<'cobro' | 'arca'>('cobro')
const selectedOrderForBilling = ref<Order | null>(null)

const arcaViewerOpen = ref(false)
const selectedInvoiceForViewer = ref<Invoice | null>(null)

// Finished orders that need billing or payment
const finishedOrders = computed(() =>
  db.value.orders.filter((o) => o.status === 'Finalizado')
)

function openBillingForOrder(order: Order, tab: 'cobro' | 'arca') {
  selectedOrderForBilling.value = order
  billingModalTab.value = tab
  billingModalOpen.value = true
}

function openArcaViewer(inv: Invoice) {
  selectedInvoiceForViewer.value = inv
  arcaViewerOpen.value = true
}

function handleBillingCompleted(invoice: Invoice) {
  billingModalOpen.value = false
  if (invoice.isFiscal) {
    selectedInvoiceForViewer.value = invoice
    arcaViewerOpen.value = true
  }
}

function handleCreated(invoice: Invoice) {
  formModalOpen.value = false
  notify(`Comprobante #${invoice.id} emitido.`)
}

function getOrderForInvoice(inv: Invoice): Order | undefined {
  if (inv.orderId) return db.value.orders.find((o) => o.id === inv.orderId)
  return db.value.orders.find((o) => o.vehicle === inv.vehicle && o.status === 'Finalizado')
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
        <button class="button primary" @click="formModalOpen = true">
          <Plus :size="17" />Nuevo comprobante manual
        </button>
      </div>
    </section>

    <!-- ARCA Status Notice -->
    <div class="integration-notice arca-header-notice">
      <ShieldCheck :size="22" style="color: #2563eb;" />
      <div style="flex: 1;">
        <strong>Conexión con ARCA (ex AFIP) Web Service WSFE</strong>
        <p>Desde cada orden de trabajo terminada podés <strong>registrar el cobro en caja</strong> o <strong>emitir la Factura Electrónica oficial</strong> con CAE y código QR reglamentario.</p>
      </div>
      <span class="badge green">PUNTO DE VENTA 0003 ACTIVO</span>
    </div>

    <!-- SECCIÓN DESTACADA: ÓRDENES TERMINADAS LISTAS PARA FACTURAR O COBRAR -->
    <section v-if="finishedOrders.length" class="panel ready-orders-panel">
      <div class="panel-header-row">
        <div class="header-tag-group">
          <ClipboardCheck :size="18" style="color: #2563eb;" />
          <div>
            <h3>Órdenes de trabajo terminadas para cobro o facturar</h3>
            <p class="muted" style="font-size: 12.5px; margin: 0;">
              Vehículos con trabajo finalizado en el taller listos para cobrar en caja o emitir factura fiscal con CAE.
            </p>
          </div>
        </div>
        <span class="badge neutral">{{ finishedOrders.length }} disponibles</span>
      </div>

      <div class="ready-orders-grid">
        <div v-for="order in finishedOrders" :key="order.id" class="ready-order-card">
          <div class="ready-card-top">
            <div>
              <span class="plate small-plate">{{ vehicle(order.vehicle)?.plate }}</span>
              <h4 style="margin: 4px 0 2px 0;">{{ vehicleName(order.vehicle) }}</h4>
              <small class="muted">{{ owner(order.vehicle)?.name }} · OT #{{ order.id }}</small>
            </div>
            <div class="order-price-badge">
              <span class="price-lbl">Total estimado</span>
              <strong class="price-num">{{ money(75000 + order.parts.reduce((s, p) => s + (p.price || 0), 0)) }}</strong>
            </div>
          </div>

          <p class="ready-service-desc">{{ order.service }}</p>

          <div class="ready-card-actions">
            <button
              class="button small primary btn-cobro"
              title="Registrar cobro en efectivo o transferencia"
              @click="openBillingForOrder(order, 'cobro')"
            >
              <CreditCard :size="14" /> Registrar cobro
            </button>
            <button
              class="button small primary btn-arca"
              title="Emitir factura electrónica oficial con CAE en ARCA"
              @click="openBillingForOrder(order, 'arca')"
            >
              <Receipt :size="14" /> Generar Factura ARCA
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- Stats summary -->
    <section class="stats-grid invoice-stats">
      <div class="stat-card">
        <div>Total cobrado</div>
        <strong>{{ money(revenue) }}</strong>
        <small>Septiembre 2026</small>
      </div>
      <div class="stat-card">
        <div>Pendiente de cobro</div>
        <strong>{{ money(unpaid.reduce((s, i) => s + i.total, 0)) }}</strong>
        <small>{{ unpaid.length }} comprobantes pendientes</small>
      </div>
      <div class="stat-card">
        <div>Comprobantes ARCA emitidos</div>
        <strong>{{ db.invoices.filter(i => i.isFiscal || i.type === 'A' || i.type === 'B').length }}</strong>
        <small>Pto. Venta 0003</small>
      </div>
    </section>

    <!-- Invoices Table -->
    <section class="panel table-scroll">
      <div style="padding: 16px 20px 0; display: flex; justify-content: space-between; align-items: center;">
        <h3 style="margin: 0; font-size: 15px;">Registro de Facturas y Comprobantes</h3>
        <span class="muted" style="font-size: 12px;">{{ db.invoices.length }} comprobantes registrados</span>
      </div>

      <table>
        <thead>
          <tr>
            <th>COMPROBANTE</th>
            <th>TIPO FISCAL</th>
            <th>CLIENTE / CONCEPTO</th>
            <th>FECHA</th>
            <th>IMPORTE</th>
            <th>ESTADO</th>
            <th>ACCIONES</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="i in db.invoices" :key="i.id">
            <td>
              <strong>Factura {{ i.type }}</strong>
              <small v-if="i.isFiscal || i.cae">
                0003-{{ String(i.nroCmp || i.id).padStart(8, '0') }}
              </small>
              <small v-else>
                REC-{{ String(i.id).padStart(6, '0') }}
              </small>
            </td>
            <td>
              <span v-if="i.isFiscal || i.cae" class="badge green" title="Comprobante fiscal con CAE asignado por ARCA">
                <ShieldCheck :size="12" /> ARCA (CAE)
              </span>
              <span v-else class="badge neutral" title="Cobro interno de taller sin CAE">
                Cobro Interno
              </span>
            </td>
            <td>
              <strong>{{ owner(i.vehicle)?.name || i.clientName || 'Cliente registrado' }}</strong>
              <small>{{ i.description }}</small>
            </td>
            <td>{{ i.date }}</td>
            <td>
              <strong>{{ money(i.total) }}</strong>
            </td>
            <td>
              <span :class="['badge', statusClass(i.status)]">
                {{ i.status }}
                <template v-if="i.paymentMethod"> · {{ i.paymentMethod }}</template>
              </span>
            </td>
            <td>
              <div class="row-actions-group">
                <!-- Ver Factura ARCA (Abre el boceto legal con CAE y QR) -->
                <button
                  class="button small outlined"
                  title="Ver factura electrónica oficial"
                  @click="openArcaViewer(i)"
                >
                  <FileText :size="14" /> Ver Factura
                </button>

                <!-- Registrar cobro si pendiente -->
                <button
                  v-if="i.status === 'Pendiente'"
                  class="button small primary btn-cobro"
                  title="Registrar cobro de este comprobante"
                  @click="
                    i.status = 'Cobrado';
                    i.paymentMethod = 'Efectivo';
                    notify(`Cobro de ${money(i.total)} registrado en caja.`);
                  "
                >
                  <CreditCard :size="14" /> Cobrar
                </button>

                <!-- Emitir en ARCA si no es fiscal todavía -->
                <button
                  v-if="!i.isFiscal && !i.cae"
                  class="button small primary btn-arca"
                  title="Convertir y emitir como Factura Electrónica ARCA"
                  @click="
                    selectedOrderForBilling = getOrderForInvoice(i) || { id: i.orderId || 1045, vehicle: i.vehicle, service: i.description, status: 'Finalizado', parts: [], diagnosis: '', mechanic: 'Nicolás', bay: null, date: i.date, time: '10:00', tasks: [], notes: '', photos: [] };
                    openBillingForOrder(selectedOrderForBilling, 'arca');
                  "
                >
                  <Receipt :size="14" /> Facturar ARCA
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <!-- Modal Formulario Manual -->
    <FacturacionModalFactura
      :open="formModalOpen"
      @close="formModalOpen = false"
      @created="handleCreated"
    />

    <!-- Modal Cobro y Factura ARCA desde Orden -->
    <FacturacionModalCobroYFactura
      :open="billingModalOpen"
      :order="selectedOrderForBilling"
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
.arca-header-notice {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #1e3a8a;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  border-radius: 12px;
  margin-bottom: 20px;
}

.ready-orders-panel {
  padding: 18px 20px;
  margin-bottom: 22px;
  background: #ffffff;
  border: 1.5px solid #dbeafe;
}

:global(html.dark .ready-orders-panel) {
  background: #1e293b;
  border-color: #334155;
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
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 10px;
  transition: all 0.15s ease;
}

:global(html.dark .ready-order-card) {
  background: #0f172a;
  border-color: #334155;
}

.ready-order-card:hover {
  border-color: #bfdbfe;
  transform: translateY(-1px);
}

.ready-card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}

.order-price-badge {
  text-align: right;
  flex-shrink: 0;
}

.price-lbl {
  display: block;
  font-size: 10.5px;
  color: #64748b;
}

.price-num {
  font-size: 16px;
  color: #15803d;
  font-weight: 850;
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

.btn-cobro {
  background: #059669;
  border-color: #059669;
}

.btn-cobro:hover {
  background: #047857;
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
