<script setup lang="ts">
import { X, Printer, MessageCircle, Download } from 'lucide-vue-next'
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


const arcaQrImage = ref('')

const currentVehicle = computed(() =>
  props.invoice?.vehicle ? vehicle(props.invoice.vehicle) : null
)

const currentOwner = computed(() =>
  currentVehicle.value?.client ? client(currentVehicle.value.client) : null
)

const sourceBudget = computed(() => {
  const orderId = props.invoice?.orderId
  return orderId != null ? db.value.quotes.find((budget) => budget.orderId === orderId) : undefined
})

// Computed fiscal calculations
const totalAmount = computed(() => props.invoice?.total || 0)
const isInvoiceA = computed(() => props.invoice?.type === 'A')
const issuerVatLabel = computed(() => props.invoice?.issuerVatCondition || (props.invoice?.type === 'C' ? 'Responsable Monotributo' : 'IVA Responsable Inscripto'))
const taxLegend = computed(() => invoiceTaxLegend(props.invoice?.type || '', props.invoice?.clientVatCondition || (isInvoiceA.value ? 'IVA Responsable Inscripto' : 'Consumidor Final')))
const invoiceCode = computed(() => ({ A: '01', B: '06', C: '11' }[props.invoice?.type || ''] || '—'))
const amount = (value: number) => value.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const dateLabel = (value?: string | null) => {
  const match = value?.match(/^(\d{4})-(\d{2})-(\d{2})/)
  return match ? `${match[3]}/${match[2]}/${match[1]}` : value || '—'
}
const netAmount = computed(() => {
  if (props.invoice?.netAmount != null) return props.invoice.netAmount
  if (props.invoice?.type === 'C') return totalAmount.value
  return Math.round(totalAmount.value / 1.21 * 100) / 100
})
// Stored items already include labor. Older invoices have only a concept and total.
const displayItems = computed(() => {
  const invoice = props.invoice
  if (!invoice) return []
  const items = invoice.items?.length ? invoice.items : [{
    description: invoice.description, quantity: 1,
    unitPrice: netAmount.value, total: netAmount.value,
  }]
  const itemSubtotal = items.reduce((sum, item) => sum + item.total, 0)
  const hasNetPrices = Math.abs(itemSubtotal - netAmount.value) < 0.02
  const multiplier = !isInvoiceA.value && hasNetPrices && itemSubtotal > 0
    ? totalAmount.value / itemSubtotal : 1
  return items.map((item, index) => ({
    ...item, code: String(index + 1).padStart(4, '0'),
    unitPrice: item.unitPrice * multiplier, total: item.total * multiplier,
  }))
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

const caeCode = computed(() => props.invoice?.cae || '—')

// Generate official ARCA fiscal QR code
async function generateFiscalQr() {
  arcaQrImage.value = ''
  if (!import.meta.client || !props.invoice?.cae) return
  try {
    // Official ARCA / AFIP QR payload format (JSON in base64 URL)
    const arcaPayload = {
      ver: 1,
      fecha: props.invoice.date || new Date().toISOString().slice(0, 10),
      cuit: Number((props.invoice.issuer?.cuit || '30718294018').replace(/\D/g, '')),
      ptoVta: props.invoice.ptoVta || 3,
      tipoCmp: Number(invoiceCode.value),
      nroCmp: props.invoice.nroCmp || props.invoice.id,
      importe: totalAmount.value,
      moneda: 'PES',
      ctz: 1,
      tipoDocRec: (props.invoice.clientDoc || currentOwner.value?.doc || '').replace(/\D/g, '').length === 11 ? 80 : 96,
      nroDocRec: Number((props.invoice.clientDoc || currentOwner.value?.doc || '').replace(/\D/g, '')),
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
  const paper = document.querySelector('.invoice-paper')
  if (!paper) return
  const frame = document.createElement('iframe')
  frame.style.cssText = 'position:fixed;width:0;height:0;border:0;'
  const styles = Array.from(document.querySelectorAll('style, link[rel="stylesheet"]'))
    .map((element) => element.outerHTML).join('')
  frame.onload = async () => {
    const printWindow = frame.contentWindow
    if (!printWindow) { frame.remove(); return }
    await printWindow.document.fonts.ready
    await Promise.all(Array.from(printWindow.document.images).map((img) => img.decode().catch(() => {})))
    printWindow.onafterprint = () => frame.remove()
    printWindow.focus()
    printWindow.print()
  }
  frame.srcdoc = `<!doctype html><html><head><title>Factura ${ptoVtaFormatted.value}-${nroCmpFormatted.value}</title>${styles}</head><body>${paper.outerHTML}</body></html>`
  document.body.appendChild(frame)
}

const downloading = ref(false)

async function handleDownload() {
  if (!import.meta.client || !props.invoice || downloading.value) return
  downloading.value = true
  try {
    const { createInvoicePdf } = await import('~/utils/invoicePdf')
    if (props.invoice.cae && !arcaQrImage.value) await generateFiscalQr()
    const invoice = props.invoice
    const owner = currentOwner.value
    const budget = sourceBudget.value
    const pdf = createInvoicePdf({
      type: invoice.type,
      issuer: invoice.issuer,
      issuerVat: issuerVatLabel.value,
      taxLegend: taxLegend.value,
      code: invoiceCode.value,
      number: `${ptoVtaFormatted.value}-${nroCmpFormatted.value}`,
      date: dateLabel(invoice.date),
      client: {
        name: invoice.clientName || owner?.name || 'Consumidor Final',
        doc: invoice.clientDoc || owner?.doc || '—',
        address: owner?.address || '—', city: owner?.city || '—',
        province: owner?.province || '—', phone: owner?.phone || '—',
        vat: invoice.clientVatCondition || (isInvoiceA.value ? 'Responsable Inscripto' : 'Consumidor Final'),
      },
      paymentMethod: invoice.paymentMethod || 'Contado',
      items: displayItems.value,
      total: totalAmount.value, net: netAmount.value, vat: vatAmount.value,
      budget: budget ? `#${budget.id}${budget.date ? ` · ${dateLabel(budget.date)}` : ''} · ${budget.description}` : 'Sin presupuesto asociado',
      vehicle: `${vehicleName(invoice.vehicle)}${currentVehicle.value?.plate ? ` · Patente: ${currentVehicle.value.plate}` : ''}`,
      cae: invoice.cae, caeDate: dateLabel(invoice.caeVto), qrImage: arcaQrImage.value,
    })
    await pdf.save(`Factura-${invoice.type}-${ptoVtaFormatted.value}-${nroCmpFormatted.value}.pdf`, { returnPromise: true })
  } catch (err) {
    console.error('Error al descargar factura en PDF:', err)
  } finally {
    downloading.value = false
  }
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
        <h2>Factura {{ invoice.type }} Nº {{ ptoVtaFormatted }}-{{ nroCmpFormatted }}</h2>
      </div>

      <div class="toolbar-actions">
        <button class="button small outlined" :disabled="downloading" title="Descargar comprobante en PDF" @click="handleDownload">
          <Download :size="15" /> {{ downloading ? 'Descargando…' : 'Descargar' }}
        </button>
        <button class="button small outlined" title="Imprimir comprobante" @click="handlePrint">
          <Printer :size="15" /> Imprimir
        </button>
        <button class="button small outlined btn-wa" title="Enviar enlace al cliente por WhatsApp" @click="shareViaWhatsApp">
          <MessageCircle :size="15" /> Enviar por WhatsApp
        </button>
        <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
          <X :size="18" />
        </button>
      </div>
    </div>

    <div class="invoice-paper-wrapper">
      <article class="invoice-paper" aria-label="Comprobante de factura">
        <header class="invoice-header-box">
          <div class="header-left">
            <h3 class="company-name">{{ invoice.issuer?.name || 'TALLER CENTRAL S.R.L.' }}</h3>
            <div class="company-details">
              <span>Servicios Mecánicos y Reparación Integral</span>
              <span>{{ invoice.issuer?.address || 'Av. García Salinas 1450' }}</span>
              <span>{{ invoice.issuer ? `${invoice.issuer.city} · ${invoice.issuer.province}` : 'Trenque Lauquen · Buenos Aires' }}</span>
              <span>{{ invoice.issuer?.phone || '(02392) 45-6789' }}</span>
              <strong>{{ issuerVatLabel }}</strong>
            </div>
          </div>
          <div class="header-center-letter">
            <div class="letter-box">
              <strong class="letter-char">{{ invoice.type }}</strong>
              <span class="letter-cod">CÓD. {{ invoiceCode }}</span>
            </div>
            <span class="original-label">ORIGINAL</span>
          </div>
          <div class="header-right">
            <h2 class="invoice-doc-type">{{ invoice.type.startsWith('Nota') ? invoice.type.toUpperCase() : 'FACTURA' }}</h2>
            <strong class="doc-number">{{ ptoVtaFormatted }}-{{ nroCmpFormatted }}</strong>
            <div class="issue-date"><strong>Fecha de Emisión:</strong> {{ dateLabel(invoice.date) }}</div>
            <div class="doc-tax-info">
              <div><strong>CUIT:</strong> {{ invoice.issuer?.cuit || '30-71829401-8' }}</div>
              <div><strong>Ingresos Brutos:</strong> {{ invoice.issuer ? '—' : '30-71829401-8' }}</div>
              <div><strong>Inicio de Actividades:</strong> {{ invoice.issuer?.activityStartDate ? invoice.issuer.activityStartDate.split('-').reverse().join('/') : invoice.issuer ? '—' : '01/03/2018' }}</div>
            </div>
          </div>
        </header>

        <section class="invoice-client-box">
          <div><strong>Nombre:</strong> {{ invoice.clientName || currentOwner?.name || 'Consumidor Final' }}</div>
          <div><strong>CUIT / DNI:</strong> {{ invoice.clientDoc || currentOwner?.doc || '—' }}</div>
          <div><strong>Domicilio:</strong> {{ currentOwner?.address || '—' }}</div>
          <div><strong>Localidad:</strong> {{ currentOwner?.city || '—' }}</div>
          <div><strong>Cond. IVA:</strong> {{ invoice.clientVatCondition || (isInvoiceA ? 'Responsable Inscripto' : 'Consumidor Final') }}</div>
          <div><strong>Provincia:</strong> {{ currentOwner?.province || '—' }}</div>
          <div><strong>Cond. Venta:</strong> {{ invoice.paymentMethod || 'Contado' }}</div>
          <div><strong>Teléfono:</strong> {{ currentOwner?.phone || '—' }}</div>
        </section>

        <section class="invoice-items-section">
          <table class="invoice-items-table">
            <colgroup><col style="width: 15%" /><col style="width: 47%" /><col style="width: 11%" /><col style="width: 13%" /><col style="width: 14%" /></colgroup>
            <thead><tr><th>Código</th><th>Descripción</th><th class="numeric">Cantidad</th><th class="numeric">P. Unitario</th><th class="numeric">Importe</th></tr></thead>
            <tbody>
              <tr v-for="item in displayItems" :key="item.code">
                <td class="numeric">{{ item.code }}</td>
                <td>{{ item.description }}</td>
                <td class="numeric">{{ item.quantity.toLocaleString('es-AR') }}</td>
                <td class="numeric">{{ amount(item.unitPrice) }}</td>
                <td class="numeric">{{ amount(item.total) }}</td>
              </tr>
            </tbody>
          </table>
        </section>

        <section class="invoice-totals-section">
          <div class="totals-table-box">
            <div><strong>Subtotal: $</strong><span>{{ amount(isInvoiceA ? netAmount : totalAmount) }}</span></div>
            <div><strong>Dto./Recargo: $</strong><span>{{ amount(0) }}</span></div>
            <div v-if="isInvoiceA"><strong>IVA 21%: $</strong><span>{{ amount(vatAmount) }}</span></div>
            <div class="total-final-line"><strong>Total: $</strong><strong>{{ amount(totalAmount) }}</strong></div>
          </div>
        </section>
        <div class="invoice-observations">
          <div v-if="taxLegend">{{ taxLegend }}</div>
          <div>
            <strong>Presupuesto de origen:</strong>
            <template v-if="sourceBudget">
              #{{ sourceBudget.id }}<template v-if="sourceBudget.date"> · {{ dateLabel(sourceBudget.date) }}</template>
              · {{ sourceBudget.description }}
            </template>
            <template v-else>Sin presupuesto asociado</template>
          </div>
          <div>Vehículo: {{ vehicleName(invoice.vehicle) }}<template v-if="currentVehicle?.plate"> · Patente: {{ currentVehicle.plate }}</template></div>
        </div>

        <footer class="invoice-arca-footer">
          <img v-if="arcaQrImage" :src="arcaQrImage" alt="QR del comprobante" class="arca-qr-image" />
          <div class="arca-info-col">
            <div class="arca-logo-badge"><strong>ARCA</strong><small>AGENCIA DE RECAUDACIÓN<br />Y CONTROL ADUANERO</small></div>
            <strong class="arca-legend">{{ invoice.cae ? 'Comprobante Autorizado' : 'Comprobante sin autorización fiscal' }}</strong>
            <small class="arca-disclaimer" v-if="invoice.cae">Esta Administración Federal no se responsabiliza por los datos ingresados en el detalle de la operación.</small>
          </div>
          <div class="cae-details-grid">
            <div><strong>CAE N°:</strong><span>{{ caeCode }}</span></div>
            <div><strong>Fecha de Vto. de CAE:</strong><span>{{ dateLabel(invoice.caeVto) }}</span></div>
          </div>
        </footer>
        <div class="invoice-software-credit">Comprobante generado con <strong>Octano</strong></div>
      </article>
    </div>
  </CommonModalDialog>
</template>

<style scoped>
.invoice-viewer-modal {
  display: flex;
  flex-direction: column;
  max-width: 900px;
  width: 95vw;
  max-height: 90vh;
  margin: auto;
  padding: 0;
  border-radius: 16px;
  overflow: hidden;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
}

:global(html.dark .invoice-viewer-modal) {
  background: #252528 !important;
  border-color: rgba(255, 255, 255, 0.12) !important;
}

.modal-top-toolbar {
  background: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  padding: 12px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

:global(html.dark .modal-top-toolbar) {
  background: #2c2c2e !important;
  border-bottom-color: rgba(255, 255, 255, 0.08) !important;
}

.toolbar-title-group h2 {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
}

:global(html.dark .toolbar-title-group h2),
:global(html.dark .modal-top-toolbar h2) {
  color: #ffffff !important;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-button {
  color: #64748b;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  width: 32px;
  height: 32px;
  padding: 0;
  background: transparent;
  border: none;
  cursor: pointer;
  transition: color 150ms ease, background 150ms ease;
}

.icon-button:hover {
  color: #0f172a;
  background: #f1f5f9;
}

:global(html.dark) .icon-button {
  color: #a1a1a6 !important;
}

:global(html.dark) .icon-button:hover {
  color: #ffffff !important;
  background: rgba(255, 255, 255, 0.08) !important;
}

.btn-wa {
  color: #22c55e;
  border-color: rgba(34, 197, 94, 0.4);
}

.btn-wa:hover {
  background: rgba(34, 197, 94, 0.1);
  color: #4ade80;
}

:global(html:not(.dark)) .btn-wa {
  background: #ffffff !important;
  color: #22c55e !important;
  border-color: rgba(34, 197, 94, 0.6) !important;
}

:global(html:not(.dark)) .btn-wa:hover,
:global(html:not(.dark)) .btn-wa:focus-visible {
  background: #f0fdf4 !important;
  color: #16a34a !important;
  border-color: #22c55e !important;
}

:global(html:not(.dark)) .btn-wa:active {
  background: #dcfce7 !important;
}

.invoice-paper-wrapper { background: #ffffff; padding: 24px; max-height: 82vh; min-height: 0; flex: 1; overflow: auto; }
:global(html.dark) .invoice-paper-wrapper {
  background: #1c1c1e !important;
}
.invoice-paper { width: 210mm; min-height: 297mm; box-sizing: border-box; padding: 3mm; margin: 0 auto; background: #fff !important; color: #000 !important; font: 12px/1.4 Arial, Helvetica, sans-serif; box-shadow: 0 4px 20px #0003; display: flex; flex-direction: column; gap: 5px; }
.invoice-paper * { box-sizing: border-box; color: #000 !important; border-color: #000 !important; }
.invoice-header-box { display: grid; grid-template-columns: 1fr 1fr; position: relative; border: 1px solid; min-height: 202px; }
.header-left { text-align: center; padding: 36px 24px 0; display: flex; flex-direction: column; justify-content: space-between; }
.company-name { font: bold 20px/1.2 Arial, sans-serif; margin: 4px 0 28px; }
.company-details { display: flex; flex-direction: column; font-size: 12px; line-height: 1.5; }
.header-right { border-left: 1px solid; padding: 10px 18px 0 60px; display: flex; flex-direction: column; }
.invoice-doc-type { font: bold 28px/1.2 Arial, sans-serif; margin: 0 0 6px; }
.doc-number { font-size: 18px; margin-bottom: 6px; }
.issue-date { font-size: 13px; }
.doc-tax-info { margin-top: auto; padding-top: 28px; font-size: 12px; line-height: 1.6; }
.header-center-letter { position: absolute; left: 50%; top: -1px; transform: translateX(-50%); width: 64px; text-align: center; background: #fff; }
.letter-box { border: 1px solid; display: flex; flex-direction: column; align-items: center; }
.letter-char { font-size: 42px; line-height: 1; padding-top: 2px; }
.letter-cod { font-size: 11px; line-height: 1.3; }
.original-label { background: #e8e8e8 !important; display: block; font-size: 11px; line-height: 1.4; }
.invoice-client-box { border: 1px solid; padding: 6px 20px; display: grid; grid-template-columns: 1fr 1fr; gap: 2px 22px; font-size: 12px; }
.invoice-client-box > div { overflow-wrap: anywhere; }
.invoice-items-section { flex: 1; min-height: 460px; }
.invoice-items-table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 12px; background: #fff !important; color: #000 !important; }
.invoice-items-table thead { display: table-header-group; }
.invoice-items-table th { padding: 3px 7px; border-top: 1px solid; border-bottom: 1px solid; text-align: left; background: #e8e8e8 !important; font-size: 12px; font-weight: 700; }
.invoice-items-table th:first-child { border-left: 1px solid; }
.invoice-items-table th:last-child { border-right: 1px solid; }
.invoice-items-table td { padding: 2px 7px; vertical-align: top; border: 0; background: #fff !important; overflow-wrap: anywhere; }
.invoice-items-table tbody tr:first-child td { padding-top: 6px; }
.invoice-items-table .numeric { text-align: right; white-space: nowrap; }
.invoice-totals-section { border: 1px solid; min-height: 120px; display: flex; justify-content: flex-end; align-items: flex-end; padding: 14px 18px; }
.totals-table-box { width: 230px; display: flex; flex-direction: column; gap: 5px; }
.totals-table-box > div { display: grid; grid-template-columns: 1fr 110px; text-align: right; gap: 10px; }
.total-final-line { font-size: 14px; }
.invoice-observations { border: 1px solid; min-height: 36px; padding: 8px 12px; font-size: 10px; }
.invoice-arca-footer { position: relative; display: flex; align-items: flex-start; gap: 14px; padding: 7px 12px 0; min-height: 100px; }
.arca-qr-image { width: 88px; height: 88px; flex: none; }
.arca-info-col { flex: 1; padding-top: 1px; }
.arca-logo-badge strong { display: block; font-size: 27px; line-height: 1; color: #505550 !important; }
.arca-logo-badge small { display: block; font-size: 5px; line-height: 1.3; color: #505550 !important; margin: 3px 0 8px; }
.arca-legend { display: block; font-style: italic; font-size: 12px; }
.arca-disclaimer { display: block; font-size: 7px; font-style: italic; margin-top: 2px; max-width: 420px; }
.cae-details-grid { display: flex; flex-direction: column; gap: 3px; font-size: 12px; text-align: right; flex: none; }
.cae-details-grid > div { display: flex; justify-content: flex-end; gap: 12px; }
.invoice-software-credit { text-align: right; font-size: 11px; padding-right: 12px; }
.invoice-software-credit strong { font-size: 16px; margin-left: 5px; }
@media (max-width: 700px) {
  .modal-top-toolbar { align-items: flex-start; gap: 12px; flex-direction: column; }
  .toolbar-actions { flex-wrap: wrap; }
  .invoice-paper-wrapper { padding: 12px; }
}
@media print {
  @page { size: A4; margin: 0; }
  :global(html), :global(body) { margin: 0 !important; padding: 0 !important; background: #fff !important; }
  .invoice-paper { margin: 0; box-shadow: none; print-color-adjust: exact; -webkit-print-color-adjust: exact; }
  .invoice-header-box, .invoice-client-box, .invoice-totals-section, .invoice-observations, .invoice-arca-footer, .invoice-items-table tr { break-inside: avoid; }
}
</style>
