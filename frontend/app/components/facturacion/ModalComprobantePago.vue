<script setup lang="ts">
import { Check, X, Banknote } from '@lucide/vue'
import type { ComprobanteApi, MedioPago } from '~/utils/comprobanteApi'
import { medioPagoLabel, mediosPago, numeroComprobante } from '~/utils/comprobanteApi'

const props = defineProps<{
  open: boolean
  comprobante: ComprobanteApi | null
  busy?: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'confirm', datos: { medioPago: MedioPago; importe: number; observaciones: string }): void
}>()

const medioPago = ref<MedioPago>('EFECTIVO')
const importe = ref<number | null>(null)
const observaciones = ref('')
const error = ref('')

const dinero = (valor: number) =>
  Number(valor || 0).toLocaleString('es-AR', { style: 'currency', currency: 'ARS', minimumFractionDigits: 2, maximumFractionDigits: 2 })

const saldo = computed(() => Number(props.comprobante?.saldo ?? 0))
const pagado = computed(() => Number(props.comprobante?.pagado ?? 0))
const total = computed(() => Number(props.comprobante?.total ?? 0))

watch(() => [props.open, props.comprobante?.id, props.comprobante?.saldo] as const, () => {
  if (!props.open || !props.comprobante) return
  medioPago.value = 'EFECTIVO'
  importe.value = Number(saldo.value.toFixed(2))
  observaciones.value = ''
  error.value = ''
}, { immediate: true })

useModalEscape(() => props.open, () => emit('close'))

function pagarTodo() {
  importe.value = Number(saldo.value.toFixed(2))
}

function pagarMitad() {
  importe.value = Math.round((saldo.value / 2) * 100) / 100
}

function confirmar() {
  const valor = Number(importe.value)
  if (!Number.isFinite(valor) || valor <= 0) {
    error.value = 'Ingresá un importe mayor a cero.'
    return
  }
  if (valor > saldo.value + 0.001) {
    error.value = 'El importe no puede superar el saldo pendiente.'
    return
  }
  error.value = ''
  emit('confirm', {
    medioPago: medioPago.value,
    importe: Math.round(valor * 100) / 100,
    observaciones: observaciones.value.trim(),
  })
}
</script>

<template>
  <CommonModalDialog v-if="open && comprobante" class="dialog comprobante-pago-dialog" @close="emit('close')">
    <div class="dialog-header">
      <div>
        <span class="eyebrow">FACTURACIÓN INTERNA / COBRO</span>
        <h2>Registrar pago · {{ numeroComprobante(comprobante) }}</h2>
      </div>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')"><X :size="18" /></button>
    </div>

    <div class="pago-body">
      <div class="notice-box">
        <Banknote :size="18" />
        <div>
          <strong>Comprobante interno, sin validez fiscal</strong>
          <p>Podés registrar un pago total o parcial. El saldo se actualiza automáticamente.</p>
        </div>
      </div>

      <div class="resumen-pago">
        <div><span>Total</span><strong>{{ dinero(total) }}</strong></div>
        <div><span>Ya pagado</span><strong>{{ dinero(pagado) }}</strong></div>
        <div class="saldo"><span>Saldo pendiente</span><strong>{{ dinero(saldo) }}</strong></div>
      </div>

      <div class="campos-pago">
        <label>
          Medio de pago
          <select v-model="medioPago">
            <option v-for="medio in mediosPago" :key="medio.value" :value="medio.value">{{ medio.label }}</option>
          </select>
        </label>
        <label>
          Importe a pagar
          <input v-model.number="importe" type="number" min="0.01" :max="saldo" step="0.01" inputmode="decimal" />
        </label>
        <div class="atajos">
          <button type="button" class="button small outlined" @click="pagarTodo">Pagar todo</button>
          <button type="button" class="button small outlined" :disabled="saldo <= 0" @click="pagarMitad">Pagar mitad</button>
        </div>
        <label class="full">
          Observaciones (opcional)
          <input v-model="observaciones" type="text" maxlength="1000" placeholder="Ej. Pago en mostrador, entrega de vehículo" />
        </label>
      </div>

      <p v-if="error" class="error-message" role="alert">{{ error }}</p>
    </div>

    <footer class="dialog-footer modal-footer">
      <button type="button" class="button" @click="emit('close')">Cancelar</button>
      <button type="button" class="button primary" :disabled="busy || saldo <= 0" @click="confirmar">
        <Check :size="16" />{{ busy ? 'Registrando…' : `Registrar ${dinero(importe || 0)}` }}
      </button>
    </footer>
  </CommonModalDialog>
</template>

<style scoped>
.comprobante-pago-dialog { max-width: 560px; width: 95vw; }
.pago-body { display: grid; gap: 16px; }
.notice-box { display: flex; gap: 10px; align-items: flex-start; font-size: 12px; }
.notice-box strong { display: block; margin-bottom: 2px; }
.notice-box p { margin: 0; opacity: 0.8; }
.resumen-pago { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.resumen-pago > div { display: grid; gap: 4px; padding: 10px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); }
.resumen-pago span { font-size: 11px; color: var(--muted); }
.resumen-pago strong { font-size: 14px; }
.resumen-pago .saldo { border-color: #2563eb; }
.campos-pago { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.campos-pago label { display: grid; gap: 6px; font-size: 12px; font-weight: 700; }
.campos-pago label.full { grid-column: 1 / -1; }
.campos-pago input, .campos-pago select { height: 40px; border: 1px solid var(--line); border-radius: 8px; padding: 0 10px; background: var(--surface); color: inherit; }
.atajos { grid-column: 1 / -1; display: flex; gap: 8px; }
@media (max-width: 520px) {
  .resumen-pago { grid-template-columns: 1fr; }
  .campos-pago { grid-template-columns: 1fr; }
}
</style>
