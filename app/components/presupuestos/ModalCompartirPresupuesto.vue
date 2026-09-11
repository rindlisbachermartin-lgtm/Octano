<script setup lang="ts">
import { Printer, Share2, Copy, Check, X, MessageCircle, FileText, ArrowUpRight, CalendarDays } from 'lucide-vue-next'
import type { Budget } from '~/types'

const props = defineProps<{
  open: boolean
  budget: Budget | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'assignTurno', budget: Budget): void
}>()

const { db, vehicle, vehicleName, owner } = useDatabase()
const { money } = useHelpers()
const { notify } = useToast()

useModalEscape(() => props.open, () => emit('close'))

const copied = ref(false)

const currentVehicle = computed(() =>
  props.budget ? vehicle(props.budget.vehicle) : null
)

const currentClient = computed(() =>
  props.budget ? owner(props.budget.vehicle) : null
)

const subtotal = computed(() => {
  if (!props.budget) return 0
  return Number(props.budget.labor || 0) + Number(props.budget.materials || 0)
})

const tax = computed(() => {
  return Math.round(subtotal.value * 0.21)
})

const totalWithTax = computed(() => {
  return subtotal.value + tax.value
})

function buildShareText(): string {
  if (!props.budget || !currentVehicle.value) return ''
  const v = currentVehicle.value
  const c = currentClient.value
  const b = props.budget
  
  let text = `🔧 *PRESUPUESTO #${b.id} — OCTANO TALLER CENTRAL*\n`
  text += `📅 Fecha: ${b.date || new Date().toISOString().slice(0, 10)}\n`
  text += `🚗 Vehículo: ${v.brand} ${v.model} (${v.plate})\n`
  if (c?.name) text += `👤 Cliente: ${c.name}\n`
  text += `📋 Detalle: ${b.description}\n\n`

  if (b.items && b.items.length) {
    text += `*Repuestos e Insumos:*\n`
    b.items.forEach((item) => {
      text += `• ${item.quantity}x ${item.name} (${money(item.unitPrice)}) = ${money(item.total)}\n`
    })
    text += `Subtotal Repuestos: ${money(b.materials)}\n\n`
  } else if (b.materials) {
    text += `Repuestos e Insumos: ${money(b.materials)}\n`
  }

  text += `Mano de Obra: ${money(b.labor)}\n`
  text += `--------------------------------\n`
  text += `Subtotal Neto: ${money(subtotal.value)}\n`
  text += `IVA (21%): ${money(tax.value)}\n`
  text += `*TOTAL CON IVA: ${money(totalWithTax.value)}*\n\n`
  text += `_Presupuesto válido por 15 días corridos._`
  
  return text
}

function handleCopy() {
  const text = buildShareText()
  if (navigator.clipboard) {
    navigator.clipboard.writeText(text)
    copied.value = true
    notify('Presupuesto copiado al portapapeles.')
    setTimeout(() => {
      copied.value = false
    }, 2500)
  }
}

function handleWhatsApp() {
  const text = encodeURIComponent(buildShareText())
  const rawPhone = currentClient.value?.phone || ''
  const cleanPhone = rawPhone.replace(/\D/g, '')
  
  let url = `https://api.whatsapp.com/send?text=${text}`
  if (cleanPhone.length >= 8) {
    // If international code missing, assume AR (54)
    const phoneWithCountry = cleanPhone.startsWith('54') ? cleanPhone : `54${cleanPhone}`
    url = `https://api.whatsapp.com/send?phone=${phoneWithCountry}&text=${text}`
  }
  
  if (import.meta.client) {
    window.open(url, '_blank')
  }
}

function handlePrint() {
  if (import.meta.client) {
    window.print()
  }
}
</script>

<template>
  <dialog v-if="open && budget" class="dialog detail-modal budget-share-dialog" open>
    <div class="dialog-header no-print">
      <div>
        <span class="eyebrow">OCTANO / PRESUPUESTOS</span>
        <h2>Presupuesto #{{ budget.id }}</h2>
      </div>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <!-- Screen Action Bar (No Print) -->
    <div class="share-actions-bar no-print">
      <div class="share-actions-left">
        <button
          v-if="budget.status !== 'En taller' && budget.status !== 'Convertido'"
          class="button"
          style="background: #0284c7; color: white;"
          @click="emit('assignTurno', budget)"
        >
          <CalendarDays :size="16" /> Asignar turno
        </button>
        <button class="button primary" @click="handlePrint">
          <Printer :size="16" /> Imprimir presupuesto
        </button>
        <button class="button outlined" @click="handleWhatsApp">
          <MessageCircle :size="16" style="color: #25d366" /> Enviar por WhatsApp
        </button>
        <button class="button outlined" @click="handleCopy">
          <Check v-if="copied" :size="16" style="color: #10b981" />
          <Copy v-else :size="16" />
          {{ copied ? 'Copiado' : 'Copiar texto' }}
        </button>
      </div>
    </div>

    <!-- Printable Budget Sheet Container -->
    <div class="printable-budget-document">
      <!-- Printable Document Header -->
      <header class="doc-header">
        <div class="doc-brand">
          <div class="brand-title">
            <span class="brand-sym">o·</span>
            <strong>octano</strong>
          </div>
          <small class="doc-subtitle">TALLER CENTRAL & SERVICIOS MECÁNICOS</small>
          <p class="doc-address">Av. San Martín 1420 · Tel. (011) 4567-8900 · info@octanotaller.com</p>
        </div>
        <div class="doc-meta-box">
          <span class="doc-badge">PRESUPUESTO OFICIAL</span>
          <div class="doc-number">#{{ String(budget.id).padStart(6, '0') }}</div>
          <div class="doc-date">Fecha: {{ budget.date || new Date().toISOString().slice(0, 10) }}</div>
        </div>
      </header>

      <!-- Client & Vehicle Info Section -->
      <section class="doc-parties-grid">
        <div class="doc-info-col">
          <span class="doc-info-label">CLIENTE</span>
          <strong class="doc-info-main">{{ currentClient?.name || 'Cliente particular' }}</strong>
          <span v-if="currentClient?.doc" class="doc-info-sub">DNI / CUIT: {{ currentClient.doc }}</span>
          <span v-if="currentClient?.phone" class="doc-info-sub">Tel: {{ currentClient.phone }}</span>
          <span v-if="currentClient?.email" class="doc-info-sub">{{ currentClient.email }}</span>
        </div>
        <div class="doc-info-col">
          <span class="doc-info-label">VEHÍCULO</span>
          <div style="display: flex; align-items: center; gap: 8px; margin: 2px 0 4px">
            <strong class="doc-info-main">{{ vehicleName(budget.vehicle) }}</strong>
            <span class="plate small-plate">{{ currentVehicle?.plate }}</span>
          </div>
          <span v-if="currentVehicle?.year" class="doc-info-sub">Año: {{ currentVehicle.year }} · Motor: {{ currentVehicle.engine || 'Estándar' }}</span>
          <span v-if="currentVehicle?.km" class="doc-info-sub">Kilometraje: {{ Number(currentVehicle.km).toLocaleString('es-AR') }} km</span>
        </div>
      </section>

      <!-- Work Detail Overview -->
      <section class="doc-description-block">
        <span class="doc-info-label">DETALLE DEL TRABAJO A REALIZAR</span>
        <p class="doc-desc-text">{{ budget.description }}</p>
      </section>

      <!-- Items Table (Repuestos y Materiales) -->
      <section class="doc-table-section">
        <span class="doc-info-label">DESGLOSE DE REPUESTOS E INSUMOS</span>
        <table class="doc-table">
          <thead>
            <tr>
              <th style="width: 50px">CANT.</th>
              <th>DESCRIPCIÓN</th>
              <th style="text-align: right; width: 120px">PRECIO UNIT.</th>
              <th style="text-align: right; width: 120px">SUBTOTAL</th>
            </tr>
          </thead>
          <tbody>
            <template v-if="budget.items && budget.items.length">
              <tr v-for="it in budget.items" :key="it.id">
                <td style="font-weight: 600">{{ it.quantity }}</td>
                <td>
                  {{ it.name }}
                  <small v-if="it.isCustom" class="custom-item-tag">Personalizado</small>
                </td>
                <td style="text-align: right">{{ money(it.unitPrice) }}</td>
                <td style="text-align: right; font-weight: 600">{{ money(it.total) }}</td>
              </tr>
            </template>
            <tr v-else>
              <td colspan="3">Repuestos e insumos generales estimados</td>
              <td style="text-align: right; font-weight: 600">{{ money(budget.materials) }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- Labor & Financial Summary Table -->
      <section class="doc-summary-section">
        <div class="doc-terms">
          <strong>Condiciones del presupuesto:</strong>
          <ul>
            <li>Los precios informados tienen una validez de 15 días desde su emisión.</li>
            <li>En caso de repuestos bajo pedido, el plazo de entrega está sujeto a disponibilidad del distribuidor.</li>
            <li>Garantía de 90 días en mano de obra sobre el trabajo realizado.</li>
          </ul>
        </div>

        <div class="doc-totals-box">
          <div class="totals-row">
            <span>Mano de obra</span>
            <strong>{{ money(budget.labor) }}</strong>
          </div>
          <div class="totals-row">
            <span>Repuestos e insumos</span>
            <strong>{{ money(budget.materials) }}</strong>
          </div>
          <div class="totals-row subtotal-row">
            <span>Subtotal Neto</span>
            <strong>{{ money(subtotal) }}</strong>
          </div>
          <div class="totals-row vat-row">
            <span>IVA (21%)</span>
            <strong>{{ money(tax) }}</strong>
          </div>
          <div class="totals-row final-total-row">
            <span>TOTAL ESTIMADO</span>
            <span class="final-price">{{ money(totalWithTax) }}</span>
          </div>
        </div>
      </section>

      <!-- Document Footer / Signatures -->
      <footer class="doc-footer">
        <div class="signature-line">
          <span>Firma Taller Octano</span>
        </div>
        <div class="signature-line">
          <span>Conformidad del Cliente</span>
        </div>
      </footer>
    </div>

    <footer class="modal-footer no-print">
      <button type="button" class="button" @click="emit('close')">Cerrar</button>
      <button type="button" class="button primary" @click="handlePrint">
        <Printer :size="16" /> Imprimir
      </button>
    </footer>
  </dialog>
</template>

<style scoped>
.share-actions-bar {
  padding: 14px 24px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.share-actions-left {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.printable-budget-document {
  padding: 24px 28px;
  color: #0f172a;
  background: #ffffff;
}

/* Document Header */
.doc-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  border-bottom: 2px solid #0f172a;
  padding-bottom: 18px;
  margin-bottom: 20px;
}

.brand-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: 'Manrope', sans-serif;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -1px;
}

.brand-sym {
  color: #2563eb;
}

.doc-subtitle {
  display: block;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 1.5px;
  color: #64748b;
  margin-top: 2px;
}

.doc-address {
  font-size: 10px;
  color: #64748b;
  margin-top: 5px;
}

.doc-meta-box {
  text-align: right;
}

.doc-badge {
  display: inline-block;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 9.5px;
  font-weight: 700;
  letter-spacing: 1px;
  padding: 3px 8px;
  border-radius: 4px;
  border: 1px solid #bfdbfe;
}

.doc-number {
  font-family: 'Manrope', sans-serif;
  font-size: 22px;
  font-weight: 800;
  color: #0f172a;
  margin-top: 4px;
}

.doc-date {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
}

/* Parties Grid */
.doc-parties-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 18px;
}

.doc-info-col {
  display: flex;
  flex-direction: column;
}

.doc-info-label {
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #64748b;
  margin-bottom: 4px;
  text-transform: uppercase;
}

.doc-info-main {
  font-size: 13px;
  color: #0f172a;
}

.doc-info-sub {
  font-size: 11px;
  color: #475569;
  margin-top: 2px;
}

/* Description Block */
.doc-description-block {
  margin-bottom: 18px;
  padding: 12px 14px;
  border-left: 3px solid #2563eb;
  background: #f8fafc;
}

.doc-desc-text {
  font-size: 12px;
  color: #1e293b;
  margin-top: 4px;
  line-height: 1.4;
}

/* Table */
.doc-table-section {
  margin-bottom: 22px;
}

.doc-table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 8px;
  font-size: 11.5px;
}

.doc-table th {
  background: #f1f5f9;
  color: #475569;
  font-size: 9.5px;
  font-weight: 700;
  letter-spacing: 0.5px;
  padding: 8px 10px;
  border-bottom: 1px solid #cbd5e1;
  text-align: left;
}

.doc-table td {
  padding: 9px 10px;
  border-bottom: 1px solid #e2e8f0;
  color: #1e293b;
}

.custom-item-tag {
  display: inline-block;
  font-size: 8.5px;
  color: #64748b;
  background: #f1f5f9;
  padding: 1px 5px;
  border-radius: 3px;
  margin-left: 6px;
}

/* Summary Section */
.doc-summary-section {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 20px;
  align-items: flex-start;
  margin-bottom: 25px;
}

.doc-terms {
  font-size: 10px;
  color: #64748b;
  line-height: 1.5;
}

.doc-terms ul {
  padding-left: 14px;
  margin: 6px 0 0;
}

.doc-totals-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 16px;
}

.totals-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11.5px;
  padding: 4px 0;
  color: #475569;
}

.totals-row strong {
  color: #0f172a;
}

.subtotal-row {
  border-top: 1px solid #e2e8f0;
  padding-top: 8px;
  margin-top: 4px;
  font-weight: 600;
}

.vat-row {
  color: #2563eb;
  font-weight: 600;
}

.final-total-row {
  border-top: 2px solid #0f172a;
  padding-top: 10px;
  margin-top: 6px;
  font-size: 13px;
  font-weight: 800;
  color: #0f172a;
}

.final-price {
  font-family: 'Manrope', sans-serif;
  font-size: 20px;
  font-weight: 800;
  color: #0f172a;
}

/* Footer / Signatures */
.doc-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 40px;
  padding-top: 10px;
}

.signature-line {
  width: 190px;
  border-top: 1px solid #94a3b8;
  text-align: center;
  padding-top: 6px;
}

.signature-line span {
  font-size: 9.5px;
  color: #64748b;
}

/* Dark Mode Modal View Adjustments */
:global(html.dark) .share-actions-bar {
  background: #202023;
  border-bottom-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark) .printable-budget-document {
  background: #252528;
  color: #f5f5f7;
}

:global(html.dark) .doc-header {
  border-bottom-color: rgba(255, 255, 255, 0.15);
}

:global(html.dark) .brand-sym {
  color: #0a84ff;
}

:global(html.dark) .doc-number {
  color: #ffffff;
}

:global(html.dark) .doc-badge {
  background: rgba(10, 132, 255, 0.16);
  color: #64d2ff;
  border-color: rgba(10, 132, 255, 0.3);
}

:global(html.dark) .doc-parties-grid {
  background: #202023;
  border-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark) .doc-info-main {
  color: #ffffff;
}

:global(html.dark) .doc-description-block {
  background: #202023;
  border-left-color: #0a84ff;
}

:global(html.dark) .doc-desc-text {
  color: #f5f5f7;
}

:global(html.dark) .doc-table th {
  background: rgba(255, 255, 255, 0.04);
  color: #8e8e93;
  border-bottom-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark) .doc-table td {
  border-bottom-color: rgba(255, 255, 255, 0.06);
  color: #f5f5f7;
}

:global(html.dark) .custom-item-tag {
  background: rgba(255, 255, 255, 0.08);
  color: #8e8e93;
}

:global(html.dark) .doc-totals-box {
  background: #202023;
  border-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark) .totals-row strong {
  color: #ffffff;
}

:global(html.dark) .subtotal-row {
  border-top-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark) .vat-row {
  color: #64d2ff;
}

:global(html.dark) .final-total-row {
  border-top-color: rgba(255, 255, 255, 0.2);
  color: #ffffff;
}

:global(html.dark) .final-price {
  color: #ffffff;
}

/* Media Print Rules: Full Clean White Paper for Printing */
@media print {
  body {
    background: #ffffff !important;
    color: #000000 !important;
  }
  .no-print,
  :deep(.sidebar),
  :deep(.topbar),
  :deep(.sidebar-backdrop) {
    display: none !important;
  }
  dialog.dialog.budget-share-dialog {
    position: static !important;
    transform: none !important;
    box-shadow: none !important;
    border: none !important;
    width: 100% !important;
    max-width: 100% !important;
    background: #ffffff !important;
    padding: 0 !important;
    margin: 0 !important;
    color: #000000 !important;
  }
  .printable-budget-document {
    background: #ffffff !important;
    color: #000000 !important;
    padding: 0 !important;
  }
  .doc-header {
    border-bottom: 2px solid #000000 !important;
  }
  .doc-parties-grid {
    background: #f8fafc !important;
    border: 1px solid #cbd5e1 !important;
  }
  .doc-table th {
    background: #f1f5f9 !important;
    color: #000000 !important;
  }
  .doc-table td {
    color: #000000 !important;
  }
  .doc-totals-box {
    background: #f8fafc !important;
    border: 1px solid #cbd5e1 !important;
  }
  .final-total-row,
  .final-price {
    color: #000000 !important;
  }
}
</style>
