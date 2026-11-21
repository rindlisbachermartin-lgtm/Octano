<script setup lang="ts">
import { X, Printer, Download } from '@lucide/vue'
import type { ComprobanteApi } from '~/utils/comprobanteApi'
import { estadosComprobante, fechaLabel, medioPagoLabel, numeroComprobante, vehiculoLabel } from '~/utils/comprobanteApi'
import { crearPdfComprobante } from '~/utils/comprobantePdf'

const props = defineProps<{
  open: boolean
  comprobante: ComprobanteApi | null
}>()

const emit = defineEmits<{ (e: 'close'): void }>()

useModalEscape(() => props.open, () => emit('close'))

const dinero = (valor: number) =>
  Number(valor || 0).toLocaleString('es-AR', { style: 'currency', currency: 'ARS', minimumFractionDigits: 2, maximumFractionDigits: 2 })

const estado = computed(() => props.comprobante ? estadosComprobante[props.comprobante.estado] : null)
const pagos = computed(() => props.comprobante?.cobros?.length
  ? props.comprobante.cobros
  : props.comprobante?.cobro ? [props.comprobante.cobro] : [])

function nombreArchivo() {
  const c = props.comprobante
  return `comprobante-interno-${c?.numero ?? c?.id ?? 'borrador'}`.replace(/\s+/g, '-')
}

function descargar() {
  if (!props.comprobante) return
  crearPdfComprobante(props.comprobante).save(`${nombreArchivo()}.pdf`)
}

function imprimir() {
  if (!props.comprobante) return
  const doc = crearPdfComprobante(props.comprobante)
  doc.autoPrint()
  window.open(doc.output('bloburl'), '_blank', 'noopener')
}
</script>

<template>
  <CommonModalDialog v-if="open && comprobante" class="dialog visor-comprobante-dialog" @close="emit('close')">
    <div class="dialog-header">
      <div>
        <span class="eyebrow">COMPROBANTE INTERNO · SIN VALIDEZ FISCAL</span>
        <h2>{{ numeroComprobante(comprobante) }}</h2>
      </div>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')"><X :size="18" /></button>
    </div>

    <div class="visor-body">
      <div class="visor-actions">
        <span v-if="estado" class="badge" :class="estado.badge">{{ estado.label }}</span>
        <div class="visor-buttons">
          <button type="button" class="button small outlined" @click="imprimir"><Printer :size="15" />Imprimir</button>
          <button type="button" class="button small primary" @click="descargar"><Download :size="15" />Descargar PDF</button>
        </div>
      </div>

      <div class="aviso-interno">
        Documento interno del taller. No reemplaza una factura ni tiene validez fiscal.
      </div>

      <section class="visor-bloque">
        <h3>Datos del comprobante</h3>
        <div class="visor-grid">
          <div><span>Fecha</span><strong>{{ fechaLabel(comprobante.fecha) }}</strong></div>
          <div><span>Orden de trabajo</span><strong>{{ comprobante.ordenId ? `#${comprobante.ordenId}` : 'Sin orden' }}</strong></div>
          <div class="ancho"><span>Concepto</span><strong>{{ comprobante.descripcion || '—' }}</strong></div>
        </div>
      </section>

      <section class="visor-bloque">
        <h3>Emisor y receptor</h3>
        <div class="visor-dos-columnas">
          <div class="visor-caja">
            <span class="caja-titulo">Emisor</span>
            <strong>{{ comprobante.emisor?.nombre || '—' }}</strong>
            <small>Documento: {{ comprobante.emisor?.documento || '—' }}</small>
            <small>Condición IVA: {{ comprobante.emisor?.condicionIva || '—' }}</small>
          </div>
          <div class="visor-caja">
            <span class="caja-titulo">Receptor</span>
            <strong>{{ comprobante.receptor?.nombre || '—' }}</strong>
            <small>Documento: {{ comprobante.receptor?.documento || '—' }}</small>
            <small>Condición IVA: {{ comprobante.receptor?.condicionIva || '—' }}</small>
          </div>
        </div>
        <p class="vehiculo-linea">
          <strong>Vehículo:</strong>
          {{ comprobante.vehiculo ? `${vehiculoLabel(comprobante.vehiculo)} · ${comprobante.vehiculo.patente}` : '—' }}
        </p>
      </section>

      <section class="visor-bloque">
        <h3>Renglones</h3>
        <div class="tabla-scroll">
          <table>
            <thead><tr><th>Cant.</th><th>Descripción</th><th>P. unitario</th><th>Subtotal</th></tr></thead>
            <tbody>
              <tr v-for="item in comprobante.items" :key="item.renglon">
                <td class="numero">{{ Number(item.cantidad).toLocaleString('es-AR') }}</td>
                <td>{{ item.descripcion }}</td>
                <td class="numero">{{ dinero(item.precioUnitario) }}</td>
                <td class="numero">{{ dinero(item.subtotal) }}</td>
              </tr>
              <tr v-if="!comprobante.items?.length"><td colspan="4" class="muted">Sin renglones cargados.</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="visor-bloque visor-totales">
        <div><span>Repuestos</span><strong>{{ dinero(comprobante.totalRepuestos) }}</strong></div>
        <div><span>Mano de obra</span><strong>{{ dinero(comprobante.manoObra) }}</strong></div>
        <div><span>IVA {{ Number(comprobante.porcentajeIva).toLocaleString('es-AR') }}%</span><strong>{{ dinero(comprobante.montoIva) }}</strong></div>
        <div class="total"><span>Total</span><strong>{{ dinero(comprobante.total) }}</strong></div>
      </section>

      <section class="visor-bloque">
        <h3>Pagos registrados</h3>
        <div v-if="pagos.length" class="tabla-scroll">
          <table>
            <thead><tr><th>Fecha</th><th>Medio</th><th>Observaciones</th><th>Importe</th></tr></thead>
            <tbody>
              <tr v-for="pago in pagos" :key="pago.id">
                <td>{{ fechaLabel(pago.fecha) }}</td>
                <td>{{ medioPagoLabel(pago.medioPago) }}</td>
                <td>{{ pago.observaciones || '—' }}</td>
                <td class="numero">{{ dinero(pago.importe) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-else class="muted">Todavía no hay pagos registrados.</p>
        <div class="resumen-saldo">
          <div><span>Pagado</span><strong>{{ dinero(comprobante.pagado) }}</strong></div>
          <div><span>Saldo pendiente</span><strong>{{ dinero(comprobante.saldo) }}</strong></div>
        </div>
      </section>
    </div>

    <footer class="dialog-footer modal-footer">
      <button type="button" class="button" @click="emit('close')">Cerrar</button>
    </footer>
  </CommonModalDialog>
</template>

<style scoped>
.visor-comprobante-dialog { max-width: 860px; width: 95vw; }
.visor-body { display: grid; gap: 16px; max-height: 65vh; overflow-y: auto; padding-right: 4px; }
.visor-actions { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap; }
.visor-buttons { display: flex; gap: 8px; flex-wrap: wrap; }
.aviso-interno { padding: 10px 12px; border: 1px dashed var(--line); border-radius: 8px; font-size: 12px; color: var(--muted); }
.visor-bloque h3 { margin: 0 0 8px; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }
.visor-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.visor-grid .ancho { grid-column: 1 / -1; }
.visor-grid > div, .visor-caja { display: grid; gap: 3px; padding: 8px 10px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); font-size: 12px; }
.visor-grid span, .visor-caja span, .visor-caja small { color: var(--muted); font-size: 11px; }
.visor-dos-columnas { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.vehiculo-linea { margin: 10px 0 0; font-size: 12px; }
.tabla-scroll { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 12px; }
th, td { padding: 6px 8px; border-bottom: 1px solid var(--line); text-align: left; }
th { font-size: 10px; text-transform: uppercase; color: var(--muted); }
td.numero { text-align: right; white-space: nowrap; }
.visor-totales { margin-left: auto; width: min(320px, 100%); display: grid; gap: 6px; }
.visor-totales > div { display: flex; justify-content: space-between; gap: 10px; font-size: 12px; }
.visor-totales .total { border-top: 1px solid var(--line); padding-top: 6px; font-size: 14px; }
.resumen-saldo { display: flex; justify-content: space-between; gap: 10px; margin-top: 10px; padding-top: 10px; border-top: 1px solid var(--line); font-size: 12px; }
@media (max-width: 620px) {
  .visor-grid, .visor-dos-columnas { grid-template-columns: 1fr; }
  .visor-grid .ancho { grid-column: auto; }
}
</style>
