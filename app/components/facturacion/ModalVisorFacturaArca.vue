<script setup lang="ts">
import { X, Printer, Download, MessageCircle, CheckCircle2, ShieldCheck, QrCode as QrIcon } from 'lucide-vue-next'
import QRCode from 'qrcode'
import type { Invoice } from '~/types'

const props = defineProps<{
  open: boolean
  invoice: Invoice | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { db, vehicle, vehicleName, client } = useDatabase()
const { money } = useHelpers()
const { notify } = useWorkshopToast()


const arcaQrImage = ref('')

const currentVehicle = computed(() =>
  props.invoice?.vehicle ? vehicle(props.invoice.vehicle) : null
)

const currentOwner = computed(() =>
  currentVehicle.value?.client ? client(currentVehicle.value.client) : null
)

// Computed fiscal calculations
const totalAmount = computed(() => props.invoice?.total || 0)
const isInvoiceA = computed(() => props.invoice?.type === 'A')
const netAmount = computed(() => {
  if (props.invoice?.netAmount != null) return props.invoice.netAmount
  // If Factura A, separate 21% VAT; if B, can also separate or show total
  return Math.round(totalAmount.value / 1.21 * 100) / 100
})
const vatAmount = computed(() => {
  if (props.invoice?.vatAmount != null) return props.invoice.vatAmount
  return Math.round((totalAmount.value - netAmount.value) * 100) / 100
})

const ptoVtaFormatted = computed(() => {
  const p = props.invoice?.ptoVta || 3
  return String(p).padStart(4, '0')
})

const nroCmpFormatted = computed(() => {
  const n = props.invoice?.nroCmp || props.invoice?.id || 1
  return String(n).padStart(8, '0')
})

const caeCode = computed(() => props.invoice?.cae || '74382910543219')
const caeVtoDate = computed(() => {
  if (props.invoice?.caeVto) return props.invoice.caeVto
  const d = new Date()
  d.setDate(d.getDate() + 10)
  return d.toISOString().slice(0, 10)
})

// Generate official ARCA fiscal QR code
async function generateFiscalQr() {
  if (!import.meta.client || !props.invoice) return
  try {
    // Official ARCA / AFIP QR payload format (JSON in base64 URL)
    const arcaPayload = {
      ver: 1,
      fecha: props.invoice.date || new Date().toISOString().slice(0, 10),
      cuit: 30718294018,
      ptoVta: props.invoice.ptoVta || 3,
      tipoCmp: isInvoiceA.value ? 1 : 6,
      nroCmp: props.invoice.nroCmp || props.invoice.id,
      importe: totalAmount.value,
      moneda: 'PES',
      ctz: 1,
      tipoDocRec: isInvoiceA.value ? 80 : 96,
      nroDocRec: currentOwner.value?.doc ? Number(currentOwner.value.doc.replace(/\D/g, '')) : 0,
      tipoCodAut: 'E',
      codAut: Number(caeCode.value)
    }
    const jsonStr = JSON.stringify(arcaPayload)
    const b64 = btoa(unescape(encodeURIComponent(jsonStr)))
    const arcaUrl = `https://www.arca.gob.ar/fe/qr/?p=${b64}`

    arcaQrImage.value = await QRCode.toDataURL(arcaUrl, {
      margin: 0,
      width: 130,
      color: { dark: '#000000', light: '#ffffff' }
    })
  } catch (err) {
    console.error('Error generating ARCA QR', err)
  }
}

watch(() => [props.open, props.invoice], () => {
  if (props.open && props.invoice) {
    generateFiscalQr()
  }
}, { immediate: true })

function handlePrint() {
  window.print()
}

function shareViaWhatsApp() {
  if (!currentOwner.value || !props.invoice) return
  const msg = `Hola ${currentOwner.value.name}! Te adjuntamos la Factura electrónica ${props.invoice.type} N° ${ptoVtaFormatted.value}-${nroCmpFormatted.value} correspondiente a la orden de trabajo de tu ${vehicleName(props.invoice.vehicle)}. CAE: ${caeCode.value}. Muchas gracias por confiar en Taller Central!`
  const url = `https://wa.me/549${currentOwner.value.phone.replace(/\D/g, '')}?text=${encodeURIComponent(msg)}`
  window.open(url, '_blank')
}
</script>

<template>
  <CommonModalDialog v-if="open && invoice" class="dialog invoice-viewer-modal" @close="emit('close')">
    <!-- Modal Toolbar -->
    <div class="dialog-header modal-top-toolbar no-print">
      <div class="toolbar-title-group">
        <span class="arca-pill">
          <ShieldCheck :size="14" /> COMPROBANTE FISCAL AUTORIZADO POR ARCA
        </span>
        <h2>Factura {{ invoice.type }} Nº {{ ptoVtaFormatted }}-{{ nroCmpFormatted }}</h2>
      </div>

      <div class="toolbar-actions">
        <button class="button small outlined" title="Imprimir o guardar en PDF" @click="handlePrint">
          <Printer :size="15" /> Imprimir / PDF
        </button>
        <button class="button small outlined btn-wa" title="Enviar enlace al cliente por WhatsApp" @click="shareViaWhatsApp">
          <MessageCircle :size="15" /> Enviar por WhatsApp
        </button>
        <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
          <X :size="18" />
        </button>
      </div>
    </div>

    <!-- Official ARCA Invoice Document Template (Boceto legal reglamentario) -->
    <div class="invoice-paper-wrapper">
      <div class="invoice-paper">
        <!-- HEADER ROW: EMISOR | CODIGO LETRA | FACTURA INFO -->
        <header class="invoice-header-box">
          <!-- Left: Datos del Taller Emisor -->
          <div class="header-side header-left">
            <div class="workshop-brand">
              <h3 class="company-name">TALLER CENTRAL S.R.L.</h3>
              <span class="workshop-brand-sub">Servicios Mecánicos & Reparación Integral</span>
            </div>
            <p class="company-details">
              <span>Av. García Salinas 1450 · Trenque Lauquen (Bs. As.)</span>
              <span>Tel: (02392) 45-6789 · Email: administracion@tallercentral.com</span>
              <span><strong>IVA Responsable Inscripto</strong></span>
            </p>
          </div>

          <!-- Center: Letra Fiscal A / B -->
          <div class="header-center-letter">
            <div class="letter-box">
              <span class="letter-char">{{ invoice.type }}</span>
              <span class="letter-cod">COD. 0{{ isInvoiceA ? '1' : '6' }}</span>
            </div>
            <div class="letter-vertical-line"></div>
          </div>

          <!-- Right: Factura, Punto de Venta, CUIT -->
          <div class="header-side header-right">
            <h2 class="invoice-doc-type">FACTURA</h2>
            <div class="doc-number-row">
              <span class="doc-label">Punto de Venta: <strong>{{ ptoVtaFormatted }}</strong></span>
              <span class="doc-label">Comp. Nro: <strong>{{ nroCmpFormatted }}</strong></span>
            </div>
            <div class="doc-tax-info">
              <div>Fecha de Emisión: <strong>{{ invoice.date }}</strong></div>
              <div>CUIT: <strong>30-71829401-8</strong></div>
              <div>Ingresos Brutos: <strong>30-71829401-8</strong></div>
              <div>Inicio de Actividades: <strong>01/03/2018</strong></div>
            </div>
          </div>
        </header>

        <!-- RECEPTOR BLOCK (CLIENTE & VEHICULO) -->
        <section class="invoice-section invoice-client-box">
          <div class="client-grid">
            <div class="client-field">
              <span class="field-lbl">CUIT / DNI:</span>
              <strong>{{ currentOwner?.doc || 'Consumidor Final' }}</strong>
            </div>
            <div class="client-field">
              <span class="field-lbl">Apellido y Nombre / Razón Social:</span>
              <strong>{{ currentOwner?.name || invoice.clientName || 'Cliente del Taller' }}</strong>
            </div>
            <div class="client-field">
              <span class="field-lbl">Condición frente al IVA:</span>
              <span>{{ isInvoiceA ? 'IVA Responsable Inscripto' : 'Consumidor Final' }}</span>
            </div>
            <div class="client-field">
              <span class="field-lbl">Condición de venta:</span>
              <span>{{ invoice.paymentMethod || 'Contado / Inmediato' }}</span>
            </div>
            <div class="client-field full-col">
              <span class="field-lbl">Vehículo vinculado:</span>
              <span>{{ vehicleName(invoice.vehicle) }} · Patente <strong>{{ currentVehicle?.plate }}</strong></span>
            </div>
          </div>
        </section>

        <!-- ITEMS / DETALLE TABLE -->
        <section class="invoice-section invoice-items-section">
          <table class="invoice-items-table">
            <thead>
              <tr>
                <th style="width: 12%">CÓDIGO</th>
                <th style="width: 48%">DESCRIPCIÓN / SERVICIO</th>
                <th style="width: 10%; text-align: center;">CANT.</th>
                <th style="width: 15%; text-align: right;">PRECIO UNIT.</th>
                <th style="width: 15%; text-align: right;">SUBTOTAL</th>
              </tr>
            </thead>
            <tbody>
              <!-- Main service row -->
              <tr>
                <td class="font-mono">SRV-01</td>
                <td>
                  <strong>{{ invoice.description }}</strong>
                  <div class="item-sub-desc">Mano de obra técnica calificada de taller y diagnóstico</div>
                </td>
                <td style="text-align: center;">1.00</td>
                <td style="text-align: right;">{{ money(isInvoiceA ? netAmount : totalAmount) }}</td>
                <td style="text-align: right;"><strong>{{ money(isInvoiceA ? netAmount : totalAmount) }}</strong></td>
              </tr>

              <!-- Items if custom parts exist -->
              <tr v-for="(item, idx) in invoice.items" :key="idx">
                <td class="font-mono">REP-0{{ idx + 1 }}</td>
                <td>{{ item.description }}</td>
                <td style="text-align: center;">{{ item.quantity.toFixed(2) }}</td>
                <td style="text-align: right;">{{ money(item.unitPrice) }}</td>
                <td style="text-align: right;"><strong>{{ money(item.total) }}</strong></td>
              </tr>
            </tbody>
          </table>
        </section>

        <!-- TOTALS & TAX BREAKDOWN -->
        <section class="invoice-section invoice-totals-section">
          <div class="totals-left-notes">
            <span class="badge-payment">
              Estado: <strong>{{ invoice.status }}</strong>
              <template v-if="invoice.paymentMethod"> · {{ invoice.paymentMethod }}</template>
            </span>
          </div>

          <div class="totals-table-box">
            <div v-if="isInvoiceA" class="subtotal-line">
              <span>Importe Neto Gravado:</span>
              <strong>{{ money(netAmount) }}</strong>
            </div>
            <div v-if="isInvoiceA" class="subtotal-line">
              <span>IVA 21.00%:</span>
              <strong>{{ money(vatAmount) }}</strong>
            </div>
            <div class="total-final-line">
              <span>TOTAL FACTURADO:</span>
              <strong class="total-number">{{ money(totalAmount) }}</strong>
            </div>
          </div>
        </section>

        <!-- ARCA OFFICIAL FOOTER (CAE & QR) -->
        <footer class="invoice-arca-footer">
          <div class="arca-qr-col">
            <img v-if="arcaQrImage" :src="arcaQrImage" alt="QR Fiscal ARCA" class="arca-qr-image" />
            <div v-else class="qr-placeholder">
              <QrIcon :size="40" />
            </div>
          </div>

          <div class="arca-info-col">
            <div class="arca-brand-row">
              <div class="arca-logo-badge">
                <strong>ARCA</strong>
                <small>Agencia de Recaudación y Control Aduanero</small>
              </div>
              <span class="arca-legend">Comprobante Autorizado Electrónicamente</span>
            </div>

            <div class="cae-details-grid">
              <div>CAE Nº: <strong>{{ caeCode }}</strong></div>
              <div>Fecha de Vto. de CAE: <strong>{{ caeVtoDate }}</strong></div>
            </div>
          </div>
        </footer>

        <!-- Pie de página con mención del sistema emisor -->
        <div class="invoice-software-credit">
          <span>Comprobante fiscal electrónico emitido mediante <strong>Octano</strong> · Software de Gestión para Talleres Mecánicos</span>
        </div>
      </div>
    </div>
  </CommonModalDialog>
</template>

<style scoped>
.invoice-viewer-modal {
  max-width: 840px;
  width: 95vw;
  padding: 0;
  border-radius: 16px;
  overflow: hidden;
  background: #0f172a;
  border: 1px solid #334155;
}

.modal-top-toolbar {
  background: #1e293b;
  border-bottom: 1px solid #334155;
  padding: 12px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.toolbar-title-group h2 {
  font-size: 17px;
  color: #ffffff;
  margin: 3px 0 0 0;
}

.arca-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.6px;
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.12);
  border: 1px solid rgba(56, 189, 248, 0.3);
  padding: 2px 7px;
  border-radius: 4px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-wa {
  color: #22c55e;
  border-color: rgba(34, 197, 94, 0.4);
}

.btn-wa:hover {
  background: rgba(34, 197, 94, 0.1);
  color: #4ade80;
}

/* Paper Container */
.invoice-paper-wrapper {
  background: #334155;
  padding: 24px;
  max-height: 82vh;
  overflow-y: auto;
}

.invoice-paper {
  background: #ffffff;
  color: #000000;
  border: 2px solid #000000;
  border-radius: 4px;
  font-family: Arial, Helvetica, sans-serif;
  padding: 18px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
  margin: 0 auto;
  max-width: 760px;
}

/* Header Box */
.invoice-header-box {
  display: flex;
  border: 1px solid #000000;
  position: relative;
}

.header-side {
  flex: 1;
  padding: 12px 14px;
}

.header-left {
  border-right: 1px solid transparent;
}

.brand-logo {
  display: flex;
  align-items: baseline;
  gap: 3px;
  font-size: 20px;
  letter-spacing: -0.5px;
}

.brand-sym {
  color: #2563eb;
  font-weight: 900;
}

.company-name {
  font-size: 13px;
  font-weight: 800;
  margin: 4px 0 2px 0;
  color: #000000;
}

.company-details {
  font-size: 10.5px;
  color: #333333;
  margin: 0;
  line-height: 1.4;
  display: flex;
  flex-direction: column;
}

/* Center Letter */
.header-center-letter {
  position: absolute;
  top: -1px;
  left: 50%;
  transform: translateX(-50%);
  width: 52px;
  height: 52px;
  background: #ffffff;
  border: 1px solid #000000;
  border-top: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 5;
}

.letter-char {
  font-size: 26px;
  font-weight: 900;
  line-height: 1;
}

.letter-cod {
  font-size: 8px;
  font-weight: 700;
}

/* Right Header */
.header-right {
  border-left: 1px solid #000000;
  padding-left: 32px;
}

.invoice-doc-type {
  font-size: 20px;
  font-weight: 900;
  margin: 0 0 6px 0;
  letter-spacing: 1px;
}

.doc-number-row {
  display: flex;
  gap: 16px;
  font-size: 12px;
  margin-bottom: 6px;
}

.doc-tax-info {
  font-size: 10.5px;
  line-height: 1.4;
  color: #222222;
}

/* Client Section */
.invoice-client-box {
  border: 1px solid #000000;
  border-top: 0;
  padding: 10px 14px;
}

.client-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 20px;
  font-size: 11px;
}

.client-field {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.client-field.full-col {
  grid-column: 1 / -1;
  border-top: 1px dashed #cccccc;
  padding-top: 4px;
  margin-top: 2px;
}

.field-lbl {
  color: #555555;
  font-size: 10px;
}

/* Items Table */
.invoice-items-section {
  border: 1px solid #000000;
  border-top: 0;
  min-height: 160px;
}

.invoice-items-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
}

.invoice-items-table thead tr {
  background: #f1f5f9;
  border-bottom: 1px solid #000000;
}

.invoice-items-table th {
  padding: 6px 10px;
  font-size: 10px;
  font-weight: 800;
  text-align: left;
}

.invoice-items-table td {
  padding: 8px 10px;
  border-bottom: 1px solid #eeeeee;
  vertical-align: top;
}

.font-mono {
  font-family: monospace;
}

.item-sub-desc {
  font-size: 9.5px;
  color: #666666;
  margin-top: 2px;
}

/* Totals Section */
.invoice-totals-section {
  border: 1px solid #000000;
  border-top: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
}

.badge-payment {
  font-size: 11px;
  background: #f1f5f9;
  border: 1px solid #cbd5e1;
  padding: 3px 8px;
  border-radius: 4px;
}

.totals-table-box {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 240px;
  text-align: right;
  font-size: 11.5px;
}

.subtotal-line {
  display: flex;
  justify-content: space-between;
  color: #444444;
}

.total-final-line {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  border-top: 1px solid #000000;
  padding-top: 4px;
  margin-top: 4px;
  font-size: 13px;
}

.total-number {
  font-size: 16px;
  font-weight: 900;
  color: #000000;
}

/* ARCA Fiscal Footer */
.invoice-arca-footer {
  border: 1px solid #000000;
  border-top: 0;
  padding: 10px 14px;
  display: flex;
  align-items: center;
  gap: 16px;
  background: #fafafa;
}

.arca-qr-image {
  width: 90px;
  height: 90px;
  display: block;
}

.qr-placeholder {
  width: 90px;
  height: 90px;
  background: #eeeeee;
  display: flex;
  align-items: center;
  justify-content: center;
}

.arca-info-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.arca-brand-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px dashed #cccccc;
  padding-bottom: 6px;
}

.arca-logo-badge strong {
  font-size: 16px;
  letter-spacing: 1px;
  color: #000000;
  margin-right: 8px;
}

.arca-logo-badge small {
  font-size: 9.5px;
  color: #666666;
}

.arca-legend {
  font-size: 10px;
  font-weight: 700;
  color: #2563eb;
}

.cae-details-grid {
  display: flex;
  gap: 24px;
  font-size: 11.5px;
}

.workshop-brand-sub {
  display: block;
  font-size: 11px;
  font-weight: 500;
  color: #555555;
  margin-top: 2px;
  margin-bottom: 6px;
}

.invoice-software-credit {
  margin-top: 14px;
  padding-top: 8px;
  border-top: 1px dashed #cccccc;
  text-align: center;
  font-size: 9.5px;
  color: #777777;
}

/* Print Styles */
@media print {
  .no-print {
    display: none !important;
  }
  .invoice-viewer-modal {
    position: static !important;
    width: 100% !important;
    max-width: 100% !important;
    border: 0 !important;
    background: transparent !important;
  }
  .invoice-paper-wrapper {
    background: transparent !important;
    padding: 0 !important;
    max-height: none !important;
    overflow: visible !important;
  }
  .invoice-paper {
    box-shadow: none !important;
    border: 1.5px solid #000000 !important;
  }
}
</style>
