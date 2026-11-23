<script setup lang="ts">
defineProps<{ repuestos: number; manoObra: number; subtotal: number; porcentajeIva: number; iva: number; total: number; estimado?: boolean }>()
const { money } = useHelpers()
</script>

<template>
  <div class="order-totals">
    <h3>Resumen de la orden</h3>
    <dl aria-label="Importes de la orden">
      <div><dt>Repuestos</dt><dd>{{ money(repuestos) }}</dd></div>
      <div><dt>Mano de obra</dt><dd>{{ money(manoObra) }}</dd></div>
      <div><dt>Subtotal</dt><dd>{{ money(subtotal) }}</dd></div>
      <div><dt>IVA ({{ porcentajeIva }}%)</dt><dd>{{ money(iva) }}</dd></div>
      <div class="order-total"><dt>Total</dt><dd>{{ money(total) }}</dd></div>
    </dl>
    <p v-if="estimado" class="muted">Los importes se confirman al guardar.</p>
  </div>
</template>

<style scoped>
.order-totals { padding: 16px; border: 1px solid var(--line); border-radius: 10px; background: var(--surface); }
h3 { margin: 0 0 12px; font-size: 14px; color: var(--ink); }
dl { display: grid; gap: 10px; margin: 0; }
dl > div { display: flex; justify-content: space-between; align-items: baseline; gap: 16px; }
dt { color: var(--muted); }
dd { margin: 0; font-weight: 600; text-align: right; font-variant-numeric: tabular-nums; }
.order-total { margin-top: 4px; padding-top: 12px; border-top: 1px solid var(--line); font-size: 17px; }
.order-total dt { color: var(--ink); font-weight: 700; }
p { margin: 12px 0 0; font-size: 12px; }
</style>
