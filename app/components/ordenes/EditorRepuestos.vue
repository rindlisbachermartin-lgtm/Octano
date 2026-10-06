<script setup lang="ts">
import { Search, Plus, Trash2, Package, X } from 'lucide-vue-next'
import type { OrderPartSelection, Part } from '~/types'

const props = defineProps<{ vehicleId: number }>()
const items = defineModel<OrderPartSelection[]>({ required: true })
const { db } = useDatabase()
const { money, matches } = useHelpers()
const openIndex = ref<number | null>(null)
const highlighted = ref(0)
const subtotal = computed(() => items.value.reduce((sum, item) => sum + (Number(item.quantity) || 0) * (Number(item.unitPrice) || 0), 0))
function results(item: OrderPartSelection) {
  const query = item.name?.trim()
  return query ? db.value.parts.filter((part) => (!part.compatible.length || part.compatible.includes(props.vehicleId)) && matches(query, part.name, part.brand, part.oem)) : []
}
function add() {
  items.value.push({ partId: -1, name: '', quantity: 1, unitPrice: 0 })
}
function select(item: OrderPartSelection, part: Part) {
  item.partId = part.id
  item.name = part.name
  item.unitPrice = part.price
  openIndex.value = null
}
function custom(item: OrderPartSelection) {
  item.partId = 0
  openIndex.value = null
}
function search(item: OrderPartSelection, index: number) {
  item.partId = -1
  item.unitPrice = 0
  openIndex.value = index
  highlighted.value = 0
}
function navigate(item: OrderPartSelection, index: number, direction: number) {
  openIndex.value = index
  const count = results(item).length + 1
  highlighted.value = (highlighted.value + direction + count) % count
}
function choose(item: OrderPartSelection) {
  if (!item.name?.trim() || openIndex.value === null) return
  const part = results(item)[highlighted.value]
  if (part) { if (part.stock > 0) select(item, part) }
  else custom(item)
}
</script>

<template>
  <div class="parts-builder-section">
    <div class="parts-builder-header">
      <div><span class="block-label">Repuestos e insumos</span><small class="muted">Elegí repuestos del inventario o cargá uno personalizado seleccionando <strong>(Otro)</strong>.</small></div>
      <button type="button" class="button small outlined" @click="add"><Plus :size="14" /> Agregar repuesto</button>
    </div>
    <div v-if="!items.length" class="empty-parts-hint"><Package :size="20" class="muted" /><p>No se agregaron repuestos aún. Hacé clic en <strong>Agregar repuesto</strong> para sumar repuestos o insumos.</p></div>
    <div v-else class="budget-items-list">
      <div v-for="(item, index) in items" :key="index" class="budget-item-row" :class="{ 'has-open-dropdown': openIndex === index }">
        <div v-if="item.partId !== 0" class="item-part-select part-combobox-wrapper">
          <label class="mini-label" :for="`order-part-${index}`">Repuesto / Insumo</label>
          <div class="part-search-input-box" :class="{ 'is-focused': openIndex === index, 'has-selected': item.partId > 0 }">
            <Search :size="14" class="muted" />
            <input :id="`order-part-${index}`" v-model="item.name" class="part-search-input" placeholder="Escribí para buscar repuesto..." autocomplete="off" role="combobox" :aria-expanded="openIndex === index && !!item.name?.trim()" :aria-controls="`order-part-options-${index}`" @focus="openIndex = index; highlighted = 0" @blur="openIndex = null" @input="search(item, index)" @keydown.down.prevent="navigate(item, index, 1)" @keydown.up.prevent="navigate(item, index, -1)" @keydown.enter.prevent="choose(item)" @keydown.escape.stop.prevent="openIndex = null" />
            <button v-if="item.name" type="button" class="part-clear-btn" aria-label="Limpiar repuesto" @click="item.name = ''; search(item, index)"><X :size="13" /></button>
          </div>
          <div v-if="openIndex === index && item.name?.trim()" :id="`order-part-options-${index}`" class="part-dropdown-menu" role="listbox">
            <div class="part-dropdown-scroll">
              <button v-for="(part, resultIndex) in results(item)" :key="part.id" type="button" class="part-dropdown-item" :class="{ 'is-highlighted': highlighted === resultIndex }" role="option" :aria-selected="item.partId === part.id" :disabled="part.stock <= 0" @mousedown.prevent="select(item, part)" @click="select(item, part)" @mouseenter="highlighted = resultIndex">
                <span class="part-item-main"><span><strong>{{ part.name }}</strong> <span class="part-item-brand">{{ part.brand }}</span></span><small class="muted">OEM: {{ part.oem }} · {{ part.stock > 0 ? `Stock: ${part.stock}` : 'Sin stock' }}</small></span><strong>{{ money(part.price) }}</strong>
              </button>
              <p v-if="!results(item).length" class="part-dropdown-empty">No hay repuestos compatibles en inventario para «{{ item.name }}».</p>
            </div>
            <button type="button" class="part-dropdown-footer" :class="{ 'is-highlighted': highlighted === results(item).length }" @mousedown.prevent="custom(item)" @click="custom(item)"><Plus :size="13" /> (Otro) · Cargar repuesto personalizado</button>
          </div>
        </div>
        <label v-else class="item-custom-name"><span class="mini-label">Nombre del repuesto (Otro)</span><input v-model="item.name" placeholder="Descripción del repuesto o insumo..." required /></label>
        <label class="item-qty"><span class="mini-label">Cant.</span><input v-model.number="item.quantity" type="number" min="1" step="1" required /></label>
        <label class="item-unit-price"><span class="mini-label">P. Unit ($)</span><input v-model.number="item.unitPrice" type="number" min="0" step="0.01" required /></label>
        <div class="item-total"><span class="mini-label">Subtotal</span><strong>{{ money((Number(item.quantity) || 0) * (Number(item.unitPrice) || 0)) }}</strong></div>
        <button type="button" class="icon-button item-remove-btn" :aria-label="`Quitar repuesto ${index + 1}`" @click="items.splice(index, 1); openIndex = null"><Trash2 :size="15" /></button>
      </div>
    </div>
    <div v-if="items.length" class="parts-subtotal-bar"><span>Subtotal de repuestos:</span><strong>{{ money(subtotal) }}</strong></div>
  </div>
</template>

<style scoped>
.parts-builder-section { border: 1px solid var(--line); border-radius: 10px; padding: 14px; }
.parts-builder-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 12px; }
.block-label { font-size: 13px; font-weight: 600; }
.parts-builder-header small { display: block; font-size: 11px; margin-top: 4px; }
.empty-parts-hint { display: flex; align-items: center; gap: 10px; padding: 16px; border: 1px dashed var(--line); border-radius: 8px; font-size: 12px; }
.empty-parts-hint p { margin: 0; }
.budget-items-list { display: flex; flex-direction: column; gap: 10px; }
.budget-item-row { display: grid; grid-template-columns: minmax(210px, 1fr) 65px 100px 85px 32px; gap: 10px; align-items: end; padding: 10px; background: var(--surface); border: 1px solid var(--line); border-radius: 8px; position: relative; }
.has-open-dropdown { z-index: 10; }
.mini-label { display: block; font-size: 9px; font-weight: 600; color: var(--muted); margin-bottom: 3px; }
.budget-item-row input { height: 36px; font-size: 11px; padding: 8px; }
.part-combobox-wrapper { position: relative; min-width: 0; }
.part-search-input-box { display: flex; align-items: center; gap: 6px; padding: 0 8px; border: 1px solid var(--line); border-radius: 7px; height: 36px; background: var(--background, transparent); }
.part-search-input-box.is-focused { border-color: #2563eb; box-shadow: 0 0 0 3px rgba(37,99,235,.15); }
.part-search-input { border: none !important; box-shadow: none !important; background: transparent !important; padding: 0 !important; flex: 1; min-width: 0; }
.part-clear-btn { display: flex; align-items: center; justify-content: center; border: 0; background: transparent; color: inherit; padding: 0; cursor: pointer; }
.part-dropdown-menu { position: absolute; top: calc(100% + 4px); left: 0; width: max(100%, 320px); max-width: min(440px, 80vw); background: var(--surface); border: 1px solid var(--line); border-radius: 8px; box-shadow: 0 12px 28px rgba(0,0,0,.2); z-index: 999; overflow: hidden; }
.part-dropdown-scroll { max-height: 220px; overflow-y: auto; }
.part-dropdown-item { display: flex; justify-content: space-between; align-items: center; gap: 10px; width: 100%; padding: 8px 10px; text-align: left; background: transparent; color: inherit; border: 0; border-bottom: 1px solid var(--line); font-size: 11px; cursor: pointer; }
.part-dropdown-item:disabled { opacity: .5; cursor: default; }
.part-dropdown-item:hover, .is-highlighted { background: rgba(37,99,235,.12); }
.part-item-main { display: flex; flex-direction: column; gap: 4px; }
.part-item-brand { color: #60a5fa; font-size: 10px; }
.part-dropdown-empty { font-size: 11px; padding: 12px 10px; }
.part-dropdown-footer { display: flex; align-items: center; gap: 6px; width: 100%; border: 0; border-top: 1px solid var(--line); padding: 10px; background: transparent; color: #60a5fa; font-size: 11px; cursor: pointer; }
.item-total { display: flex; flex-direction: column; justify-content: center; height: 56px; font-size: 12px; text-align: right; }
.item-remove-btn { margin-bottom: 2px; }
.parts-subtotal-bar { display: flex; justify-content: flex-end; gap: 10px; margin-top: 10px; padding-top: 8px; border-top: 1px solid var(--line); font-size: 11px; }
@media (max-width: 760px) { .parts-builder-header { flex-wrap: wrap; } .budget-item-row { grid-template-columns: 1fr 1fr 32px; } .item-part-select, .item-custom-name { grid-column: 1 / -1; } .item-total { grid-column: 1 / 3; } }
</style>
