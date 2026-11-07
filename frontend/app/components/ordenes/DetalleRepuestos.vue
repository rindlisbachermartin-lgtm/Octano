<script setup lang="ts">
import { Trash2 } from '@lucide/vue'
import type { InvoiceItem, OrderPart } from '~/types'

withDefaults(defineProps<{ parts: (InvoiceItem & { brand?: string; code?: string; assignment?: OrderPart })[]; showPrices?: boolean; removable?: boolean; quantityEditable?: boolean; allowDecrease?: boolean }>(), { showPrices: true, removable: false, quantityEditable: false, allowDecrease: true })
const emit = defineEmits<{ remove: [part: OrderPart]; quantity: [part: OrderPart, quantity: number] }>()
const { money } = useHelpers()
function changeQuantity(event: Event, part: InvoiceItem & { assignment?: OrderPart }) {
  if (!part.assignment) return
  const input = event.target as HTMLInputElement
  emit('quantity', part.assignment, Number(input.value))
  input.value = String(part.assignment.quantity ?? part.quantity)
}
</script>

<template>
  <div class="parts-detail">
    <h3>Repuestos e insumos imputados</h3>
    <div v-for="(part, index) in parts" :key="index" class="quote-line order-part-row">
      <span class="part-description">
        <span class="part-name">{{ part.description }}{{ part.quantity > 1 ? ` (x${part.quantity})` : '' }}</span>
        <small class="part-metadata">Marca: {{ part.brand || 'No informada' }} · Código: {{ part.code || 'No informado' }}</small>
      </span>
      <label v-if="quantityEditable && part.assignment" class="part-quantity">Cantidad
        <input :key="`${index}-${part.quantity}`" :value="part.quantity" type="number" :min="allowDecrease ? 1 : part.quantity" step="1" :aria-label="`Cantidad de ${part.description}`" @change="changeQuantity($event, part)" />
      </label>
      <strong v-if="showPrices">{{ money(part.total) }}</strong>
      <button v-if="removable && part.assignment?.additional" type="button" class="icon-button remove-part" :aria-label="`Eliminar repuesto ${part.description}`" title="Eliminar repuesto y devolver al stock" @click="emit('remove', part.assignment)"><Trash2 :size="16" /></button>
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
.remove-part { flex-shrink: 0; margin-left: auto; }
.part-quantity { flex-shrink: 0; font-size: 11px; display: grid; gap: 3px; }
.part-quantity input { width: 70px; padding: 6px; }
.muted { font-size: 13px; }
</style>
