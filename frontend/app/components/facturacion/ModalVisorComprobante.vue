<script setup lang="ts">
import { X, Printer, Download } from '@lucide/vue'
import type { ComprobanteApi } from '~/utils/comprobanteApi'
import { fechaLabel, medioPagoLabel, numeroComprobante, vehiculoLabel } from '~/utils/comprobanteApi'
import { crearPdfComprobante } from '~/utils/comprobantePdf'

const props = defineProps<{ open: boolean; comprobante: ComprobanteApi | null }>()
const emit = defineEmits<{ (e: 'close'): void }>()
useModalEscape(() => props.open, () => emit('close'))

const importe = (valor: number) => Number(valor || 0).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const pagos = computed(() => props.comprobante?.cobros ?? [])
const numero = computed(() => props.comprobante?.numero
  ? `${String(props.comprobante.puntoVenta).padStart(4, '0')}-${String(props.comprobante.numero).padStart(8, '0')}`
  : numeroComprobante(props.comprobante!))
const domicilio = (parte: ComprobanteApi['emisor']) => [parte.calle, parte.altura].filter((dato) => dato != null && dato !== '').join(' ') || '—'

function descargar() {
  if (!props.comprobante) return
  crearPdfComprobante(props.comprobante).save(`${props.comprobante.estado === 'BORRADOR' ? 'borrador' : 'factura-simulada'}-${props.comprobante.numero ?? props.comprobante.id}.pdf`)
}
function imprimir() {
  if (!props.comprobante) return
  const pdf = crearPdfComprobante(props.comprobante)
  pdf.autoPrint()
  window.open(pdf.output('bloburl'), '_blank', 'noopener')
}
</script>

<template>
  <CommonModalDialog v-if="open && comprobante" class="dialog invoice-viewer-modal" @close="emit('close')">
    <div class="dialog-header modal-top-toolbar">
      <h2>{{ comprobante.estado === 'BORRADOR' ? 'Borrador' : 'Factura' }} {{ comprobante.tipo === 'X' ? 'simulada' : comprobante.tipo }} · {{ numero }}</h2>
      <div class="toolbar-actions">
        <button type="button" class="button small outlined" @click="descargar"><Download :size="15" />Descargar</button>
        <button type="button" class="button small outlined" @click="imprimir"><Printer :size="15" />Imprimir</button>
        <button type="button" class="icon-button" aria-label="Cerrar" @click="emit('close')"><X :size="18" /></button>
      </div>
    </div>

    <div class="invoice-paper-wrapper">
      <CommonDocumentPreview :paper-width="794">
        <article class="invoice-paper" :aria-label="comprobante.estado === 'BORRADOR' ? 'Borrador' : 'Factura simulada'">
          <div v-if="comprobante.estado === 'BORRADOR'" class="draft-watermark" aria-hidden="true">BORRADOR</div>
          <header class="invoice-header-box">
            <div class="header-left">
              <h3 class="company-name">{{ comprobante.emisor.nombre }}</h3>
              <div class="company-details">
                <span>Servicios Mecánicos y Reparación Integral</span>
                <span>{{ domicilio(comprobante.emisor) }}</span>
                <span>{{ comprobante.emisor.telefono || '—' }}</span>
                <strong>{{ comprobante.emisor.condicionIva || '—' }}</strong>
              </div>
            </div>
            <div class="header-center-letter">
              <div class="letter-box"><strong>{{ comprobante.tipo }}</strong><span>SIMULADO</span></div>
              <span class="original-label">{{ comprobante.estado === 'BORRADOR' ? 'BORRADOR' : 'ORIGINAL' }}</span>
            </div>
            <div class="header-right">
              <h2 class="invoice-doc-type">{{ comprobante.estado === 'BORRADOR' ? 'BORRADOR' : 'FACTURA' }}</h2>
              <strong class="doc-number">{{ numero }}</strong>
              <div><strong>Fecha:</strong> {{ fechaLabel(comprobante.fecha) }}</div>
              <div class="doc-tax-info">
                <div><strong>CUIT:</strong> {{ comprobante.emisor.documento || '—' }}</div>
                <div><strong>Cond. IVA:</strong> {{ comprobante.emisor.condicionIva || '—' }}</div>
                <div><strong>Orden:</strong> {{ comprobante.ordenId ? `#${comprobante.ordenId}` : 'Sin orden' }}</div>
              </div>
            </div>
          </header>

          <section class="invoice-client-box">
            <div><strong>Nombre:</strong> {{ comprobante.receptor.nombre }}</div>
            <div><strong>CUIT / DNI:</strong> {{ comprobante.receptor.documento || '—' }}</div>
            <div><strong>Domicilio:</strong> {{ domicilio(comprobante.receptor) }}</div>
            <div><strong>Teléfono:</strong> {{ comprobante.receptor.telefono || '—' }}</div>
            <div><strong>Cond. IVA:</strong> {{ comprobante.receptor.condicionIva || '—' }}</div>
            <div><strong>Concepto:</strong> {{ comprobante.descripcion }}</div>
          </section>

          <section class="invoice-items-section">
            <table class="invoice-items-table">
              <colgroup><col style="width: 14%" /><col style="width: 43%" /><col style="width: 13%" /><col style="width: 15%" /><col style="width: 15%" /></colgroup>
              <thead><tr><th>Código</th><th>Descripción</th><th class="numeric">Cantidad</th><th class="numeric">P. unitario</th><th class="numeric">Importe</th></tr></thead>
              <tbody>
                <tr v-for="item in comprobante.items" :key="item.renglon">
                  <td>{{ item.codigoOem || String(item.renglon).padStart(4, '0') }}</td><td>{{ item.descripcion }}</td>
                  <td class="numeric">{{ Number(item.cantidad).toLocaleString('es-AR') }}</td>
                  <td class="numeric">{{ importe(item.precioUnitario) }}</td><td class="numeric">{{ importe(item.subtotal) }}</td>
                </tr>
                <tr v-if="comprobante.manoObra > 0"><td>—</td><td>Mano de obra</td><td class="numeric">1</td><td class="numeric">{{ importe(comprobante.manoObra) }}</td><td class="numeric">{{ importe(comprobante.manoObra) }}</td></tr>
              </tbody>
            </table>
          </section>

          <section class="invoice-totals-section">
            <div class="totals-table-box">
              <div><strong>Subtotal: $</strong><span>{{ importe(comprobante.subtotal) }}</span></div>
              <div><strong>Dto./Recargo: $</strong><span>{{ importe(0) }}</span></div>
              <div><strong>IVA {{ Number(comprobante.porcentajeIva).toLocaleString('es-AR') }}%: $</strong><span>{{ importe(comprobante.montoIva) }}</span></div>
              <div class="total-final-line"><strong>Total: $</strong><strong>{{ importe(comprobante.total) }}</strong></div>
            </div>
          </section>
          <section class="invoice-observations">
            <div><strong>Vehículo:</strong> {{ vehiculoLabel(comprobante.vehiculo) }} · Patente: {{ comprobante.vehiculo.patente }}</div>
            <div><strong>Pagado:</strong> $ {{ importe(comprobante.pagado) }} · <strong>Saldo pendiente:</strong> $ {{ importe(comprobante.saldo) }}</div>
            <div v-for="pago in pagos" :key="pago.id">{{ fechaLabel(pago.fecha) }} · {{ medioPagoLabel(pago.medioPago) }} · $ {{ importe(pago.importe) }}<template v-if="pago.observaciones"> · {{ pago.observaciones }}</template></div>
          </section>
          <footer class="invoice-simulation-footer">
            <div><strong>CAE SIMULADO</strong><span>{{ comprobante.caeSimulado || 'Pendiente de emisión' }}</span></div>
            <p>Documento de demostración sin validez fiscal. No fue autorizado por ARCA.</p>
          </footer>
          <div class="invoice-software-credit">Comprobante generado con <strong>Octano</strong></div>
        </article>
      </CommonDocumentPreview>
    </div>
  </CommonModalDialog>
</template>

<style scoped>
.invoice-paper { position: relative; }
.draft-watermark { position: absolute; inset: 0; display: grid; place-items: center; color: #000; opacity: 0.1; font-size: 84px; font-weight: 700; pointer-events: none; z-index: 1; }
.invoice-viewer-modal { display: flex; flex-direction: column; max-width: 900px; width: 95vw; max-height: 90vh; height: min(1100px, 90dvh); margin: auto; padding: 0; border-radius: 16px; overflow: hidden; background: #ffffff; border: 1px solid #cbd5e1; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25); }
:global(html.dark .invoice-viewer-modal) { background: #252528 !important; border-color: rgba(255, 255, 255, 0.12) !important; }
.modal-top-toolbar { background: #ffffff; border-bottom: 1px solid #e2e8f0; padding: 12px 20px; display: flex; justify-content: space-between; align-items: center; }
:global(html.dark .modal-top-toolbar) { background: #2c2c2e !important; border-bottom-color: rgba(255, 255, 255, 0.08) !important; }
.modal-top-toolbar h2 { margin: 0; font-size: 16px; font-weight: 700; color: #0f172a; }
:global(html.dark .modal-top-toolbar h2) { color: #ffffff !important; }
.toolbar-actions { display: flex; align-items: center; gap: 8px; }
.invoice-paper-wrapper { background: #ffffff; padding: 24px; min-height: 0; flex: 1 1 auto; overflow: auto; }
:global(html.dark .invoice-paper-wrapper) { background: #1c1c1e !important; }
.invoice-paper { width: 210mm; min-height: 297mm; margin: 0 auto; padding: 3mm; box-sizing: border-box; display: flex; flex-direction: column; gap: 5px; background: #fff !important; color: #000 !important; font: 12px/1.4 Arial, Helvetica, sans-serif; box-shadow: 0 4px 20px #0003; }
.invoice-paper * { box-sizing: border-box; color: #000 !important; border-color: #000 !important; }
.invoice-header-box { display: grid; grid-template-columns: 1fr 1fr; position: relative; border: 1px solid; min-height: 202px; }
.header-left { padding: 36px 24px 0; text-align: center; display: flex; flex-direction: column; justify-content: space-between; }
.company-name { font: bold 20px/1.2 Arial, sans-serif; margin: 4px 0 28px; }
.company-details { display: flex; flex-direction: column; font-size: 12px; line-height: 1.5; }
.header-right { border-left: 1px solid; padding: 10px 18px 0 60px; display: flex; flex-direction: column; }
.invoice-doc-type { font: bold 28px/1.2 Arial, sans-serif; margin: 0 0 6px; }
.doc-number { font-size: 18px; margin-bottom: 6px; }
.doc-tax-info { margin-top: auto; padding-top: 28px; font-size: 12px; line-height: 1.6; }
.header-center-letter { position: absolute; left: 50%; top: -1px; transform: translateX(-50%); width: 64px; text-align: center; background: #fff; }
.letter-box { border: 1px solid; display: flex; flex-direction: column; align-items: center; }
.letter-box strong { font-size: 42px; line-height: 1; padding-top: 2px; }
.letter-box span { font-size: 8px; line-height: 1.3; }
.original-label { display: block; background: #e8e8e8 !important; font-size: 11px; }
.invoice-client-box { border: 1px solid; padding: 6px 20px; display: grid; grid-template-columns: 1fr 1fr; gap: 2px 22px; font-size: 12px; }
.invoice-client-box > div { overflow-wrap: anywhere; }
.invoice-items-section { flex: 1; min-height: 460px; }
.invoice-items-table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 12px; }
.invoice-items-table thead { display: table-header-group; }
.invoice-items-table th { padding: 3px 7px; border-top: 1px solid; border-bottom: 1px solid; text-align: left; background: #e8e8e8 !important; font-size: 12px; font-weight: 700; }
.invoice-items-table th:first-child { border-left: 1px solid; }
.invoice-items-table th:last-child { border-right: 1px solid; }
.invoice-items-table td { padding: 2px 7px; vertical-align: top; border: 0; background: #fff !important; overflow-wrap: anywhere; }
.invoice-paper .invoice-items-table tbody tr:hover td { background: #fff !important; }
.invoice-items-table tbody tr:first-child td { padding-top: 6px; }
.invoice-items-table .numeric { text-align: right; white-space: nowrap; }
.invoice-totals-section { border: 1px solid; min-height: 120px; display: flex; justify-content: flex-end; align-items: flex-end; padding: 14px 18px; }
.totals-table-box { width: 230px; display: grid; gap: 5px; }
.totals-table-box > div { display: grid; grid-template-columns: 1fr 110px; gap: 10px; text-align: right; }
.total-final-line { font-size: 14px; }
.invoice-observations { border: 1px solid; min-height: 36px; padding: 8px 12px; font-size: 10px; }
.invoice-simulation-footer { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; padding: 7px 12px 0; min-height: 100px; }
.invoice-simulation-footer div { display: grid; }
.invoice-simulation-footer div strong { font-size: 12px; }
.invoice-simulation-footer div span { font-size: 18px; }
.invoice-simulation-footer p { max-width: 300px; margin: 0; font-size: 11px; text-align: right; }
.invoice-software-credit { text-align: right; font-size: 11px; padding-right: 12px; }
.invoice-software-credit strong { font-size: 16px; margin-left: 5px; }
@media (max-width: 700px) { .modal-top-toolbar { align-items: flex-start; gap: 12px; flex-direction: column; position: relative; padding: 12px; flex-shrink: 0; } .toolbar-actions { flex-wrap: wrap; width: 100%; } .toolbar-actions > .icon-button { position: absolute; top: 12px; right: 12px; } .toolbar-actions > .button { flex: 1 1 auto; white-space: normal; } .invoice-paper-wrapper { padding: 12px; } }
</style>
