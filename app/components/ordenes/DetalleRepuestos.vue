<script setup lang="ts">
import type { InvoiceItem } from '~/types'

withDefaults(defineProps<{ parts: (InvoiceItem & { brand?: string; code?: string })[]; showPrices?: boolean }>(), { showPrices: true })
const { money } = useHelpers()
</script>

<template>
  <div class="parts-detail">
    <h3>Repuestos e insumos imputados</h3>
    <div v-for="(part, index) in parts" :key="index" class="quote-line order-part-row">
      <span class="part-description">
        <span class="part-name">{{ part.description }}{{ part.quantity > 1 ? ` (x${part.quantity})` : '' }}</span>
        <small class="part-metadata">Marca: {{ part.brand || 'No informada' }} · Código: {{ part.code || 'No informado' }}</small>
      </span>
      <strong v-if="showPrices">{{ money(part.total) }}</strong>
    </div>
    <p v-if="!parts.length" class="muted">No se registraron repuestos en esta orden.</p>
  </div>
</template>

<style scoped>
h3 { font-size: 14px; margin: 0 0 8px; color: #0f172a; }
:global(html.dark .parts-detail h3) { color: #f5f5f7; }
.part-description { display: flex; flex-direction: column; gap: 5px; min-width: 0; overflow-wrap: anywhere; }
.order-part-row { align-items: center; }
.order-part-row strong { flex-shrink: 0; font-size: 12px; }
.muted { font-size: 13px; }
</style>
