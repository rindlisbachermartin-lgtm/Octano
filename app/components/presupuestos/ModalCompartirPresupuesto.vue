<script setup lang="ts">
import { Download, X, MessageCircle, Printer } from 'lucide-vue-next'
import type { Budget } from '~/types'

const props = withDefaults(
  defineProps<{
    open: boolean
    budget: Budget | null
    autoDownload?: boolean
  }>(),
  {
    autoDownload: false,
  }
)

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { db, vehicle, owner } = useDatabase()
const { notify } = useWorkshopToast()
const downloading = ref(false)


const currentVehicle = computed(() =>
  props.budget ? vehicle(props.budget.vehicle) : null
)

const currentClient = computed(() =>
  props.budget ? owner(props.budget.vehicle) : null
)

interface BudgetPrintRow {
  description: string
  quantity: string | number
  unitPrice: number
  total: number
}

// Desglose de piezas, repuestos y mano de obra para la tabla
const displayRows = computed<BudgetPrintRow[]>(() => {
  if (!props.budget) return []
  const rows: BudgetPrintRow[] = []

  // 1. Repuestos / Piezas
  if (props.budget.items && props.budget.items.length) {
    props.budget.items.forEach((it) => {
      rows.push({
        description: it.name.toUpperCase(),
        quantity: it.quantity,
        unitPrice: Number(it.unitPrice) || 0,
        total: Number(it.total) || 0,
      })
    })
  } else if (props.budget.materials && Number(props.budget.materials) > 0) {
    rows.push({
      description: 'REPUESTOS E INSUMOS ESTIMADOS',
      quantity: 1,
      unitPrice: Number(props.budget.materials),
      total: Number(props.budget.materials),
    })
  }

  // 2. Mano de Obra
  if (props.budget.labor && Number(props.budget.labor) > 0) {
    const laborDesc = props.budget.description
      ? `MANO DE OBRA (${props.budget.description.toUpperCase()})`
      : 'MANO DE OBRA'
    rows.push({
      description: laborDesc,
      quantity: '1',
      unitPrice: Number(props.budget.labor),
      total: Number(props.budget.labor),
    })
  }

  return rows
})

const amounts = computed(() => props.budget
  ? budgetAmounts(props.budget)
  : { subtotal: 0, tax: 0, total: 0 })

// Filas vacías adicionales para completar la hoja A4 (mínimo 14 filas como en el PDF)
const emptyRowsCount = computed(() => {
  const current = displayRows.value.length
  return Math.max(0, 14 - current)
})

function formatMoney(amount: number): string {
  return `$ ${Number(amount || 0).toLocaleString('es-AR', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`
}

function formatDate(dateStr?: string): string {
  if (!dateStr) {
    const d = new Date()
    return `${d.getDate()}/${d.getMonth() + 1}/${d.getFullYear()}`
  }
  const parts = dateStr.split('-')
  if (parts.length === 3) {
    return `${Number(parts[2])}/${Number(parts[1])}/${parts[0]}`
  }
  return dateStr
}

function buildShareText(): string {
  if (!props.budget || !currentVehicle.value) return ''
  const v = currentVehicle.value
  const c = currentClient.value
  const b = props.budget
  
  let text = `*PRESUPUESTO #${b.id} — TALLER CENTRAL*\n`
  text += `Fecha: ${formatDate(b.date)}\n`
  text += `Cliente: ${c?.name || 'Cliente Particular'}\n`
  text += `Vehículo: ${v.brand} ${v.model} (${v.plate})\n`
  if (b.description) text += `Observación: ${b.description}\n\n`

  text += `*DETALLE DE PIEZAS Y SERVICIOS:*\n`
  displayRows.value.forEach((r) => {
    text += `• ${r.description} (Cant: ${r.quantity}) = ${formatMoney(r.total)}\n`
  })

  text += `\nSubtotal: ${formatMoney(amounts.value.subtotal)}\n`
  text += `IVA (21%): ${formatMoney(amounts.value.tax)}\n`
  text += `*TOTAL: ${formatMoney(amounts.value.total)}*\n\n`
  text += `_Presupuesto o estimación, bajo reserva del desmontaje._\n`
  text += `_Validez del presupuesto: 15 días._\n`
  text += `Taller Central · Av. San Martín 1420 - Centro (Tel: 011 4567-8900)`
  
  return text
}

function handleWhatsApp() {
  const text = encodeURIComponent(buildShareText())
  const rawPhone = currentClient.value?.phone || ''
  const cleanPhone = rawPhone.replace(/\D/g, '')
  
  let url = `https://api.whatsapp.com/send?text=${text}`
  if (cleanPhone.length >= 8) {
    const phoneWithCountry = cleanPhone.startsWith('54') ? cleanPhone : `54${cleanPhone}`
    url = `https://api.whatsapp.com/send?phone=${phoneWithCountry}&text=${text}`
  }
  
  if (import.meta.client) {
    window.open(url, '_blank')
  }
}

async function handleDownload() {
  if (!import.meta.client || !props.budget || downloading.value) return
  const budget = props.budget
  const data = {
    id: budget.id, date: formatDate(budget.date),
    client: currentClient.value, vehicle: currentVehicle.value,
    description: budget.description, clientNotes: budget.clientNotes,
    rows: displayRows.value, ...amounts.value,
  }
  downloading.value = true
  try {
    const { createBudgetPdf } = await import('~/utils/budgetPdf')
    const pdf = createBudgetPdf(data)
    await pdf.save(`Presupuesto-${budget.id}.pdf`, { returnPromise: true })
  } catch {
    notify('No se pudo descargar el PDF. Intentá nuevamente.')
  } finally {
    downloading.value = false
  }
}

function handlePrint() {
  const paper = document.querySelector('.pdf-sheet')
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
  frame.srcdoc = `<!doctype html><html><head><title>Presupuesto #${props.budget?.id}</title>${styles}</head><body>${paper.outerHTML}</body></html>`
  document.body.appendChild(frame)
}

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen && props.autoDownload && import.meta.client) {
      handleDownload()
    }
  }
)
</script>

<template>
  <CommonModalDialog v-if="open && budget" class="dialog budget-pdf-modal invoice-viewer-modal" @close="emit('close')">
    <!-- Modal Toolbar (Screen Only) -->
    <div class="dialog-header modal-top-toolbar no-print">
      <div class="toolbar-title-group">
        <h2>Presupuesto #{{ budget.id }}</h2>
      </div>

      <div class="toolbar-actions">
        <button class="button small outlined" :disabled="downloading" title="Descargar comprobante en PDF" @click="handleDownload">
          <Download :size="15" /> {{ downloading ? 'Descargando…' : 'Descargar' }}
        </button>
        <button class="button small outlined" title="Imprimir presupuesto" @click="handlePrint">
          <Printer :size="15" /> Imprimir
        </button>
        <button class="button small outlined btn-wa" title="Enviar enlace al cliente por WhatsApp" @click="handleWhatsApp">
          <MessageCircle :size="15" /> Enviar por WhatsApp
        </button>
        <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
          <X :size="18" />
        </button>
      </div>
    </div>

    <!-- Paper Scroll Container -->
    <div class="pdf-sheet-scroll-wrapper">
      <div class="pdf-sheet">
        <!-- 1. TITLE -->
        <h1 class="pdf-doc-title">PRESUPUESTO - ESTIMACION REPARACION</h1>

        <!-- 2. TOP META BAR: N° PRESUPUESTO & FECHA CREACION -->
        <div class="pdf-meta-boxes-row">
          <div class="meta-box left-box">
            <span class="meta-box-label">N° Presupuesto:</span>
            <span class="meta-box-value">{{ budget.id }}</span>
          </div>

          <div class="meta-box right-box">
            <span class="meta-box-label">Fecha Creación:</span>
            <span class="meta-box-value">{{ formatDate(budget.date) }}</span>
          </div>
        </div>

        <!-- 3. HEADER ROW: WORKSHOP INFO & CLIENT INFO TABLE -->
        <div class="pdf-header-row">
          <!-- Workshop Details Box -->
          <div class="pdf-workshop-card">
            <h2 class="workshop-title">TALLER CENTRAL</h2>
            <div class="workshop-info-lines">
              <p>Av. San Martín 1420 - Centro</p>
              <p>Tel: (011) 4567-8900</p>
              <p>Mail: administracion@tallercentral.com</p>
            </div>
          </div>

          <!-- Client Details Box (Strict Key-Value Grid) -->
          <div class="pdf-client-card">
            <table class="grid-table client-table">
              <tbody>
                <tr>
                  <td class="cell-key">Cliente:</td>
                  <td class="cell-val bold-text">{{ currentClient?.name ? currentClient.name.toUpperCase() : '' }}</td>
                </tr>
                <tr>
                  <td class="cell-key">Teléfono:</td>
                  <td class="cell-val">{{ currentClient?.phone || '' }}</td>
                </tr>
                <tr>
                  <td class="cell-key">Dirección:</td>
                  <td class="cell-val">{{ currentClient?.address || '' }}</td>
                </tr>
                <tr>
                  <td class="cell-key">Mail:</td>
                  <td class="cell-val">{{ currentClient?.email || '' }}</td>
                </tr>
                <tr>
                  <td class="cell-key">Observación:</td>
                  <td class="cell-val">{{ budget.clientNotes || '' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 4. VEHICLE DETAILS SECTION (FULL WIDTH TABLE) -->
        <div class="pdf-vehicle-section">
          <table class="grid-table vehicle-table">
            <tbody>
              <tr>
                <td class="cell-key cell-vehicle-key">MARCA:</td>
                <td class="cell-val bold-text">{{ currentVehicle?.brand ? currentVehicle.brand.toUpperCase() : '' }}</td>
              </tr>
              <tr>
                <td class="cell-key cell-vehicle-key">MODELO:</td>
                <td class="cell-val bold-text">
                  {{ currentVehicle?.model ? currentVehicle.model.toUpperCase() : '' }}
                  {{ currentVehicle?.engine ? currentVehicle.engine.toUpperCase() : '' }}
                </td>
              </tr>
              <tr>
                <td class="cell-key cell-vehicle-key">MATRICULA:</td>
                <td class="cell-val bold-text">{{ currentVehicle?.plate || '' }}</td>
              </tr>
              <tr>
                <td class="cell-key cell-vehicle-key">VIN:</td>
                <td class="cell-val">{{ currentVehicle?.vin || currentVehicle?.engine || '' }}</td>
              </tr>
              <tr>
                <td class="cell-key cell-vehicle-key">OBSERVACION:</td>
                <td class="cell-val">{{ budget.description ? budget.description.toUpperCase() : '' }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 5. TABLE OF ITEMS -->
        <div class="pdf-table-wrapper">
          <!-- Items Table -->
          <table class="grid-table items-table">
            <thead>
              <tr>
                <th class="th-desc">REFERENCIA / DETALLE PIEZAS</th>
                <th class="th-qty">CANTIDAD</th>
                <th class="th-unit">VALOR UNITARIO</th>
                <th class="th-total">TOTAL</th>
              </tr>
            </thead>
            <tbody>
              <!-- Data rows -->
              <tr v-for="(row, idx) in displayRows" :key="idx" class="item-data-row">
                <td class="td-desc">{{ row.description }}</td>
                <td class="td-qty">{{ row.quantity }}</td>
                <td class="td-unit">{{ formatMoney(row.unitPrice) }}</td>
                <td class="td-total">{{ formatMoney(row.total) }}</td>
              </tr>

              <!-- Fill with empty rows to complete paper layout -->
              <tr v-for="n in emptyRowsCount" :key="'blank-' + n" class="item-empty-row">
                <td class="td-desc">&nbsp;</td>
                <td class="td-qty">&nbsp;</td>
                <td class="td-unit">&nbsp;</td>
                <td class="td-total">&nbsp;</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 6. BOTTOM SUMMARY SECTION: DISCLAIMER & TOTAL -->
        <div class="pdf-footer-summary-row">
          <div class="footer-disclaimer-cell">
            <p>Presupuesto o estimación, bajo reserva del desmontaje.</p>
            <p>Los valores son expresados en pesos argentinos</p>
            <p>Validez presupuesto 15 días.</p>
          </div>

          <div class="footer-total-container">
            <div class="footer-amount-row">
              <div class="footer-total-label">Subtotal</div>
              <div class="footer-total-amount">{{ formatMoney(amounts.subtotal) }}</div>
            </div>
            <div class="footer-amount-row">
              <div class="footer-total-label">IVA (21%)</div>
              <div class="footer-total-amount">{{ formatMoney(amounts.tax) }}</div>
            </div>
            <div class="footer-amount-row">
              <div class="footer-total-label">TOTAL</div>
              <div class="footer-total-amount">{{ formatMoney(amounts.total) }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </CommonModalDialog>
</template>

<style scoped>
/* 
 * MODAL OVERRIDES: Identical to ModalVisorFacturaArca
 */
.budget-pdf-modal,
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

:global(html.dark .budget-pdf-modal),
:global(html.dark .invoice-viewer-modal) {
  background: #252528 !important;
  border-color: rgba(255, 255, 255, 0.12) !important;
}

/* Modal Toolbar */
.modal-top-toolbar {
  background: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  padding: 12px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
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
:global(html.dark .modal-top-toolbar h2),
:global(html.dark .budget-pdf-modal h2) {
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
  color: #22c55e !important;
  border-color: rgba(34, 197, 94, 0.4) !important;
}

.btn-wa:hover {
  background: rgba(34, 197, 94, 0.1) !important;
  color: #4ade80 !important;
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

/* Scroll wrapper for the paper */
.pdf-sheet-scroll-wrapper {
  background: #ffffff !important;
  padding: 24px !important;
  max-height: 82vh !important;
  min-height: 0;
  flex: 1 !important;
  overflow-y: auto !important;
  display: block !important;
}

:global(html.dark .pdf-sheet-scroll-wrapper) {
  background: #1c1c1e !important;
}

:global(dialog.budget-pdf-modal::backdrop) {
  background: rgba(0, 0, 0, 0.45);
}

@media (max-width: 700px) {
  .modal-top-toolbar { align-items: flex-start; gap: 12px; flex-direction: column; }
  .toolbar-actions { flex-wrap: wrap; }
  .pdf-sheet-scroll-wrapper { padding: 12px !important; }
}

/*
 * PURE WHITE PAPER SHEET & COMPLETE DARK MODE IMMUNITY:
 * The sheet must look exactly like printed paper regardless of active theme.
 */
.pdf-sheet {
  width: 100% !important;
  max-width: 780px !important;
  background: #ffffff !important;
  color: #000000 !important;
  padding: 32px 36px !important;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.35) !important;
  font-family: Arial, Helvetica, sans-serif !important;
  box-sizing: border-box !important;
  margin: 0 auto !important;
  border: 1px solid #cbd5e1 !important;
}

:global(html.dark .pdf-sheet) {
  background: #ffffff !important;
  color: #000000 !important;
}

/* Override all child text & borders inside sheet */
.pdf-sheet * {
  color: #000000 !important;
  border-color: #000000 !important;
  box-sizing: border-box !important;
}

:global(html.dark .pdf-sheet *) {
  color: #000000 !important;
  border-color: #000000 !important;
}

/* 1. Main Document Title */
.pdf-doc-title {
  text-align: center !important;
  font-size: 18px !important;
  font-weight: 900 !important;
  letter-spacing: 0.6px !important;
  color: #000000 !important;
  margin: 0 0 14px 0 !important;
  text-transform: uppercase !important;
}

/* 2. Top Meta Bar */
.pdf-meta-boxes-row {
  display: flex !important;
  justify-content: space-between !important;
  margin-bottom: 10px !important;
  gap: 16px !important;
}

.meta-box {
  display: flex !important;
  border: 1px solid #000000 !important;
  font-size: 11px !important;
}

.meta-box.left-box {
  width: 250px !important;
}

.meta-box.right-box {
  width: 280px !important;
}

.meta-box-label {
  padding: 4px 8px !important;
  border-right: 1px solid #000000 !important;
  white-space: nowrap !important;
  font-weight: 500 !important;
}

.meta-box-value {
  padding: 4px 8px !important;
  font-weight: 700 !important;
  flex: 1 !important;
  text-align: center !important;
}

/* 3. Header Row: Workshop & Client */
.pdf-header-row {
  display: grid !important;
  grid-template-columns: 1fr 1fr !important;
  gap: 12px !important;
  margin-bottom: 10px !important;
}

/* Workshop Box */
.pdf-workshop-card {
  border: 1px solid #000000 !important;
  padding: 8px 12px !important;
  display: flex !important;
  flex-direction: column !important;
  justify-content: center !important;
  background: #ffffff !important;
}

.workshop-title {
  font-size: 17px !important;
  font-weight: 900 !important;
  letter-spacing: 0.3px !important;
  margin: 0 0 4px 0 !important;
  color: #000000 !important;
}

.workshop-info-lines p {
  margin: 1.5px 0 !important;
  font-size: 11px !important;
  line-height: 1.3 !important;
  color: #000000 !important;
}

/* Client Box */
.pdf-client-card {
  border: 1px solid #000000 !important;
  background: #ffffff !important;
}

/* Shared Grid Tables */
.grid-table,
:global(html.dark .pdf-sheet .grid-table) {
  width: 100% !important;
  border-collapse: collapse !important;
  font-size: 11px !important;
  background: #ffffff !important;
  margin: 0 !important;
}

.grid-table td,
.grid-table th,
:global(html.dark .pdf-sheet .grid-table td),
:global(html.dark .pdf-sheet .grid-table th) {
  border: 1px solid #000000 !important;
  padding: 3px 6px !important;
  box-sizing: border-box !important;
  background: #ffffff !important;
  color: #000000 !important;
}

.cell-key {
  width: 85px !important;
  white-space: nowrap !important;
  font-weight: 500 !important;
}

.cell-val {
  color: #000000 !important;
}

.bold-text {
  font-weight: 700 !important;
}

/* 4. Vehicle Box */
.pdf-vehicle-section {
  border: 1px solid #000000 !important;
  margin-bottom: 10px !important;
  background: #ffffff !important;
}

.cell-vehicle-key {
  width: 105px !important;
  font-weight: 700 !important;
}

/* 5. Items Table Container & Watermark */
.pdf-table-wrapper {
  position: relative !important;
  margin-bottom: 0 !important;
  background: #ffffff !important;
}

/* Items Table */
.items-table,
:global(html.dark .pdf-sheet .items-table) {
  position: relative !important;
  z-index: 1 !important;
  background: transparent !important;
}

.items-table thead th,
:global(html.dark .pdf-sheet .items-table thead th) {
  background: transparent !important;
  font-weight: 700 !important;
  text-align: center !important;
  padding: 5px 6px !important;
  font-size: 10px !important;
  letter-spacing: 0.2px !important;
  color: #000000 !important;
}

.th-desc, .td-desc {
  text-align: left !important;
  width: 58% !important;
}

.th-qty, .td-qty {
  text-align: center !important;
  width: 10% !important;
}

.th-unit, .td-unit {
  text-align: right !important;
  width: 16% !important;
}

.th-total, .td-total {
  text-align: right !important;
  width: 16% !important;
}

.item-data-row td,
:global(html.dark .pdf-sheet .item-data-row td) {
  font-size: 10.5px !important;
  color: #000000 !important;
  background: transparent !important;
}

.item-empty-row td,
:global(html.dark .pdf-sheet .item-empty-row td) {
  height: 21px !important;
  background: transparent !important;
}

/* 6. Footer Summary Row: Disclaimer & Total */
.pdf-footer-summary-row {
  display: flex !important;
  border: 1px solid #000000 !important;
  border-top: 0 !important;
  font-size: 11px !important;
  background: #ffffff !important;
}

.footer-disclaimer-cell {
  flex: 1 !important;
  padding: 5px 8px !important;
  border-right: 1px solid #000000 !important;
  display: flex !important;
  flex-direction: column !important;
  justify-content: center !important;
  gap: 1.5px !important;
}

.footer-disclaimer-cell p {
  margin: 0 !important;
  font-size: 10px !important;
  line-height: 1.3 !important;
  color: #000000 !important;
}

.footer-total-container {
  display: flex !important;
  flex-direction: column !important;
  width: 38% !important;
}

.footer-amount-row {
  display: flex !important;
  min-height: 26px !important;
}

.footer-amount-row + .footer-amount-row {
  border-top: 1px solid #000000 !important;
}

.footer-total-label {
  width: 40% !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  font-weight: 900 !important;
  border-right: 1px solid #000000 !important;
  letter-spacing: 0.5px !important;
  font-size: 11.5px !important;
  color: #000000 !important;
}

.footer-total-amount {
  width: 60% !important;
  display: flex !important;
  align-items: center !important;
  justify-content: flex-end !important;
  padding-right: 8px !important;
  font-weight: 900 !important;
  font-size: 12.5px !important;
  color: #000000 !important;
}

/* Print Styles */
@media print {
  @page {
    size: A4 portrait;
    margin: 8mm 10mm;
  }

  body {
    background: #ffffff !important;
    color: #000000 !important;
  }

  .no-print {
    display: none !important;
  }

  dialog.dialog.budget-pdf-modal,
  .budget-pdf-modal {
    position: static !important;
    width: 100% !important;
    max-width: 100% !important;
    border: none !important;
    background: #ffffff !important;
    box-shadow: none !important;
    padding: 0 !important;
    margin: 0 !important;
  }

  .pdf-sheet-scroll-wrapper {
    background: transparent !important;
    padding: 0 !important;
    max-height: none !important;
    overflow: visible !important;
    display: block !important;
  }

  .pdf-sheet {
    box-shadow: none !important;
    padding: 0 !important;
    max-width: 100% !important;
    width: 100% !important;
    border: none !important;
  }
}
</style>
