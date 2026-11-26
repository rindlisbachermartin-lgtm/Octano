<script setup lang="ts">
import { useSlots } from 'vue'
import type { DetalleOrdenApi } from '~/utils/ordenApi'
withDefaults(defineProps<{ items: DetalleOrdenApi['items']; showPrices?: boolean }>(), { showPrices: true })
const { money } = useHelpers()
const slots = useSlots()
</script>

<template>
  <div v-if="items.length" class="order-parts-table-wrap">
    <table class="order-parts-table">
      <thead><tr><th>Código</th><th>Descripción</th><th class="numeric">Cantidad</th><th v-if="showPrices" class="numeric">P. unitario</th><th v-if="showPrices" class="numeric">Subtotal</th><th v-if="slots.actions"><span class="sr-only">Acciones</span></th></tr></thead>
      <tbody><tr v-for="item in items" :key="item.id">
        <td>{{ item.codigoOem || '—' }}</td><td class="description">{{ item.descripcion }}</td>
        <td class="numeric"><slot name="quantity" :item="item">{{ Number(item.cantidad).toLocaleString('es-AR') }}</slot></td>
        <td v-if="showPrices" class="numeric">{{ money(item.precioUnitario) }}</td><td v-if="showPrices" class="numeric">{{ money(item.subtotal) }}</td>
        <td v-if="slots.actions"><slot name="actions" :item="item" /></td>
      </tr></tbody>
    </table>
  </div>
  <p v-else class="muted">No se registraron repuestos en esta orden.</p>
</template>

<style scoped>
.order-parts-table-wrap { overflow-x: auto; }
.order-parts-table { width: 100%; border-collapse: collapse; font-size: 12px; }
th { padding: 10px 8px; border-bottom: 1px solid var(--line); color: var(--muted); font-size: 10px; text-transform: uppercase; white-space: nowrap; text-align: center; }
td { padding: 12px 8px; border-bottom: 1px solid var(--line); vertical-align: middle; text-align: center; }
.description { min-width: 140px; overflow-wrap: anywhere; }
.numeric { text-align: center; font-variant-numeric: tabular-nums; white-space: nowrap; }
p { font-size: 13px; }
</style>
