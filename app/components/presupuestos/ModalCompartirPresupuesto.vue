<script setup lang="ts">
import { Download, X, MessageCircle, CalendarDays } from 'lucide-vue-next'
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
  (e: 'assignTurno', budget: Budget): void
}>()

const { db, vehicle, owner } = useDatabase()
const { notify } = useWorkshopToast()
const downloading = ref(false)

useModalEscape(() => props.open, () => emit('close'))

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

// Total calculado directamente de las filas o labor + materials
const calculatedTotal = computed(() => {
  if (!props.budget) return 0
  if (displayRows.value.length) {
    return displayRows.value.reduce((acc, r) => acc + (Number(r.total) || 0), 0)
  }
  return Number(props.budget.labor || 0) + Number(props.budget.materials || 0)
})

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

  text += `\n*TOTAL: ${formatMoney(calculatedTotal.value)}*\n\n`
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
    rows: displayRows.value, total: calculatedTotal.value,
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
  <dialog v-if="open && budget" class="dialog budget-pdf-modal" open>
    <!-- Modal Toolbar (Screen Only) -->
    <div class="modal-top-bar no-print">
      <div class="top-title-group">
        <span class="top-badge">PRESUPUESTO</span>
        <h2>Presupuesto #{{ budget.id }}</h2>
      </div>

      <div class="top-actions-group">
        <button
          v-if="budget.status !== 'En taller' && budget.status !== 'Convertido'"
          class="button small"
          style="background: #0284c7; color: white;"
          @click="emit('assignTurno', budget)"
        >
          <CalendarDays :size="15" /> Asignar turno
        </button>
        <button class="button small primary" :disabled="downloading" @click="handleDownload">
          <Download :size="15" /> {{ downloading ? 'Descargando…' : 'Descargar PDF' }}
        </button>
        <button class="button small outlined btn-wa" @click="handleWhatsApp">
          <MessageCircle :size="15" /> WhatsApp
        </button>
        <button class="icon-button close-btn" aria-label="Cerrar" @click="emit('close')">
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
            <div class="footer-total-label">TOTAL</div>
            <div class="footer-total-amount">{{ formatMoney(calculatedTotal) }}</div>
          </div>
        </div>
      </div>
    </div>
  </dialog>
</template>

<style scoped>
/* 
 * CRITICAL MODAL OVERRIDES:
 * Force high specificity to defeat generic dialog.dialog styles in main.css 
 */
:global(dialog.dialog.budget-pdf-modal),
:global(dialog[open].budget-pdf-modal),
:global(html.dark dialog.dialog.budget-pdf-modal),
:global(html.dark .dialog.budget-pdf-modal),
.budget-pdf-modal {
  position: fixed !important;
  top: 50% !important;
  left: 50% !important;
  transform: translate(-50%, -50%) !important;
  width: 860px !important;
  max-width: 95vw !important;
  max-height: 94vh !important;
  margin: 0 !important;
  padding: 0 !important;
  border-radius: 12px !important;
  background: #0f172a !important;
  border: 1px solid #334155 !important;
  box-shadow: 0 25px 60px -15px rgba(0, 0, 0, 0.8) !important;
  overflow: hidden !important;
  display: flex !important;
  flex-direction: column !important;
  z-index: 1000 !important;
}

/* Modal Toolbar */
.modal-top-bar {
  background: #1e293b !important;
  border-bottom: 1px solid #334155 !important;
  padding: 10px 18px !important;
  display: flex !important;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: space-between !important;
  align-items: center !important;
  flex-shrink: 0 !important;
}

.top-title-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.top-badge {
  background: #2563eb;
  color: #ffffff !important;
  font-size: 9.5px;
  font-weight: 800;
  letter-spacing: 0.6px;
  padding: 2px 7px;
  border-radius: 4px;
}

.top-title-group h2 {
  font-size: 15px;
  font-weight: 700;
  color: #ffffff !important;
  margin: 0;
}

.top-actions-group {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.top-actions-group .close-btn {
  color: #94a3b8 !important;
}
.top-actions-group .close-btn:hover {
  color: #ffffff !important;
  background: rgba(255, 255, 255, 0.1) !important;
}

.btn-wa {
  color: #22c55e !important;
  border-color: rgba(34, 197, 94, 0.5) !important;
}

.btn-wa:hover {
  background: rgba(34, 197, 94, 0.15) !important;
}

/* Scroll wrapper for the paper */
.pdf-sheet-scroll-wrapper {
  background: #334155 !important;
  padding: 24px 16px !important;
  overflow-y: auto !important;
  flex: 1 !important;
  min-height: 0;
  display: block !important;
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
  width: 32% !important;
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
