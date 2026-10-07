<script setup lang="ts">
import { Check, X, Search, Plus, Trash2 } from 'lucide-vue-next'
import type { PartEditorItem } from '~/types/partEditor'
import type { Part } from '~/types'

const props = withDefaults(defineProps<{ minQuantity?: number }>(), { minQuantity: 1 })
const items = defineModel<PartEditorItem[]>({ required: true })
const { db } = useDatabase()
const { money, matches } = useHelpers()
const { notify } = useWorkshopToast()
const partsSubtotal = computed(() => items.value.reduce((sum, item) => sum + (Number(item.quantity) || 0) * (Number(item.unitPrice) || 0), 0))

const openDropdownKey = ref<number | null>(null)
const debouncedPartSearches = useDebouncedValue(() => items.value.map((item) => ({
  key: item.key, query: item.searchQuery,
})))
const settledPartQuery = (item: PartEditorItem) =>
  debouncedPartSearches.value.find((search) => search.key === item.key)?.query || ''
const highlightedIndex = ref<Record<number, number>>({})
watch(debouncedPartSearches, () => { highlightedIndex.value = {} })
const partInputRefs = ref<Record<number, HTMLInputElement | null>>({})
const customInputRefs = ref<Record<number, HTMLInputElement | null>>({})

function setPartInputRef(key: number, el: any) {
  if (el) {
    partInputRefs.value[key] = el
  } else {
    delete partInputRefs.value[key]
  }
}

function setCustomInputRef(key: number, el: any) {
  if (el) {
    customInputRefs.value[key] = el
  } else {
    delete customInputRefs.value[key]
  }
}

function getPartLabel(p: { name: string; brand?: string }): string {
  return p.brand ? `${p.name} (${p.brand})` : p.name
}

const filteredPartsByKey = computed(() => new Map(debouncedPartSearches.value.map(({ key, query }) => [
  key, query.trim() ? db.value.parts.filter((p) => matches(query.trim(), p.name, p.brand, p.oem)) : [],
] as const)))

function getFilteredParts(item: PartEditorItem) {
  if (!item.searchQuery.trim()) return []
  return (filteredPartsByKey.value.get(item.key) || []).filter((part) => !isAlreadySelected(item, part.id))
}

function isAlreadySelected(item: PartEditorItem, partId: number) {
  return items.value.some((other) => other.key !== item.key && other.partId === partId)
}

function addItem() {
  const key = Date.now() + Math.random()
  const newItem: PartEditorItem = {
    key,
    partId: '',
    customName: '',
    searchQuery: '',
    quantity: 1,
    unitPrice: 0,
  }
  items.value.push(newItem)
  openDropdownKey.value = key
  highlightedIndex.value[key] = -1
  nextTick(() => {
    partInputRefs.value[key]?.focus()
  })
}

function removeItem(index: number) {
  const it = items.value[index]
  if (it && openDropdownKey.value === it.key) {
    openDropdownKey.value = null
  }
  items.value.splice(index, 1)
}

function selectPart(item: PartEditorItem, p: Part) {
  if (isAlreadySelected(item, p.id)) {
    notify('Este repuesto ya está agregado. Ajustá la cantidad en su fila.')
    return
  }
  item.partId = p.id
  item.customName = p.name
  item.searchQuery = getPartLabel(p)
  item.unitPrice = p.price || 0
  openDropdownKey.value = null
}

function selectCustom(item: PartEditorItem, initialName?: string) {
  item.partId = 'custom'
  const name = initialName !== undefined && initialName !== 'Personalizado'
    ? initialName
    : (item.searchQuery && item.searchQuery !== 'Personalizado' ? item.searchQuery : item.customName)
  item.customName = name
  item.searchQuery = 'Personalizado'
  if (!item.unitPrice) {
    item.unitPrice = 0
  }
  openDropdownKey.value = null
  nextTick(() => {
    customInputRefs.value[item.key]?.focus()
  })
}

function clearItemPart(item: PartEditorItem) {
  item.partId = ''
  item.customName = ''
  item.searchQuery = ''
  item.unitPrice = 0
  openDropdownKey.value = item.key
  highlightedIndex.value[item.key] = -1
  nextTick(() => {
    partInputRefs.value[item.key]?.focus()
  })
}

function onPartInputFocus(item: PartEditorItem, e: FocusEvent) {
  openDropdownKey.value = item.key
  highlightedIndex.value[item.key] = -1
  const input = e.target as HTMLInputElement
  if (input) {
    input.select()
  }
}

function onPartInputSearch(item: PartEditorItem) {
  openDropdownKey.value = item.key
  highlightedIndex.value[item.key] = -1
  if (item.partId !== '' && item.partId !== 'custom') {
    const p = db.value.parts.find((part) => part.id === Number(item.partId))
    if (p && item.searchQuery !== getPartLabel(p)) {
      item.partId = ''
      item.customName = ''
      item.unitPrice = 0
    }
  }
}

function navigateDown(item: PartEditorItem) {
  if (openDropdownKey.value !== item.key) {
    openDropdownKey.value = item.key
    highlightedIndex.value[item.key] = 0
    return
  }
  const parts = getFilteredParts(item)
  const maxIdx = parts.length // footer is at index parts.length
  const current = highlightedIndex.value[item.key] ?? -1
  highlightedIndex.value[item.key] = (current + 1) > maxIdx ? 0 : current + 1
}

function navigateUp(item: PartEditorItem) {
  if (openDropdownKey.value !== item.key) {
    openDropdownKey.value = item.key
    return
  }
  const parts = getFilteredParts(item)
  const maxIdx = parts.length
  const current = highlightedIndex.value[item.key] ?? 0
  highlightedIndex.value[item.key] = (current - 1) < 0 ? maxIdx : current - 1
}

function selectHighlighted(item: PartEditorItem) {
  if (!item.searchQuery.trim() || settledPartQuery(item) !== item.searchQuery) return
  const parts = getFilteredParts(item)
  const idx = highlightedIndex.value[item.key] ?? -1
  if (idx >= 0 && idx < parts.length) {
    selectPart(item, parts[idx])
  } else if (idx === parts.length) {
    selectCustom(item, item.searchQuery)
  } else if (parts.length === 1) {
    selectPart(item, parts[0])
  } else if (parts.length === 0) {
    selectCustom(item, item.searchQuery)
  }
}

function onDocClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (!target.closest('.part-combobox-wrapper')) {
    if (openDropdownKey.value !== null) {
      const activeItem = items.value.find((it) => it.key === openDropdownKey.value)
      if (activeItem && activeItem.partId) {
        if (activeItem.partId === 'custom') {
          activeItem.searchQuery = 'Personalizado'
        } else {
          const p = db.value.parts.find((part) => part.id === Number(activeItem.partId))
          if (p) {
            activeItem.searchQuery = getPartLabel(p)
          }
        }
      }
      openDropdownKey.value = null
    }
  }
}

onMounted(() => {
  if (import.meta.client) {
    document.addEventListener('click', onDocClick)
  }
})

onBeforeUnmount(() => {
  if (import.meta.client) {
    document.removeEventListener('click', onDocClick)
  }
})

</script>

<template>
        <div class="parts-builder-section">
          <div class="parts-builder-header">
            <div>
              <span class="block-label">Repuestos e insumos</span>
              <small class="muted" style="display: block; font-size: 11px">
                Elegí repuestos del inventario o cargá uno personalizado seleccionando <strong>(Otro)</strong>.
              </small>
            </div>
            <button type="button" class="button small outlined" @click="addItem">
              <Plus :size="14" /> Agregar repuesto
            </button>
          </div>

          <!-- Items list -->
          <div v-if="items.length" class="budget-items-list">
            <div
              v-for="(item, index) in items"
              :key="item.key"
              class="budget-item-row"
              :class="{ 'has-open-dropdown': openDropdownKey === item.key }"
            >
              <!-- Repuesto Searchable Combobox -->
              <div v-if="item.partId !== 'custom'" class="item-part-select part-combobox-wrapper">
                <span class="mini-label">Repuesto / Insumo</span>

                <!-- Mode 1: Catalog Search Input (when not custom) -->
                <div
                  class="part-search-input-box"
                  :class="{ 'is-focused': openDropdownKey === item.key, 'has-selected': !!item.partId }"
                >
                  <Search :size="14" class="part-search-icon" />
                  <input
                    :ref="(el) => setPartInputRef(item.key, el)"
                    v-model="item.searchQuery"
                    type="text"
                    class="part-search-input"
                    placeholder="Escribí para buscar repuesto..."
                    autocomplete="off"
                    @focus="onPartInputFocus(item, $event)"
                    @input="onPartInputSearch(item)"
                    @keydown.down.prevent="navigateDown(item)"
                    @keydown.up.prevent="navigateUp(item)"
                    @keydown.enter.prevent="selectHighlighted(item)"
                    @keydown.escape.stop="openDropdownKey = null"
                  />
                  <button
                    v-if="item.searchQuery || item.partId"
                    type="button"
                    class="part-clear-btn"
                    title="Limpiar y buscar otro"
                    aria-label="Limpiar repuesto"
                    @click="clearItemPart(item)"
                  >
                    <X :size="13" />
                  </button>
                </div>

                <!-- Floating Dropdown -->
                <div
                  v-if="openDropdownKey === item.key && item.partId !== 'custom' && item.searchQuery.trim()"
                  class="part-dropdown-menu"
                >
                  <div class="part-dropdown-scroll">
                    <p v-if="settledPartQuery(item) !== item.searchQuery" class="muted part-dropdown-empty">Buscando repuestos…</p>
                    <template v-else>
                    <!-- Option items from inventory -->
                    <div
                      v-for="(p, pIdx) in getFilteredParts(item)"
                      :key="p.id"
                      class="part-dropdown-item"
                      :class="{
                        'is-selected': item.partId === p.id,
                        'is-highlighted': highlightedIndex[item.key] === pIdx
                      }"
                      @mousedown.prevent="selectPart(item, p)"
                      @mouseenter="highlightedIndex[item.key] = pIdx"
                    >
                      <div class="part-item-main">
                        <div class="part-item-name-row">
                          <strong class="part-item-name">{{ p.name }}</strong>
                          <span v-if="p.brand" class="part-item-brand">{{ p.brand }}</span>
                        </div>
                        <div class="part-item-sub">
                          <span v-if="p.oem" class="part-item-oem">OEM: {{ p.oem }}</span>
                          <span
                            class="part-stock-pill"
                            :class="{
                              'stock-ok': p.stock > 5,
                              'stock-low': p.stock > 0 && p.stock <= 5,
                              'stock-zero': p.stock <= 0
                            }"
                          >
                            {{ p.stock > 0 ? `Stock: ${p.stock}` : 'Sin stock' }}
                          </span>
                        </div>
                      </div>
                      <div class="part-item-price-col">
                        <span class="part-item-price">{{ money(p.price) }}</span>
                        <Check v-if="item.partId === p.id" :size="14" class="part-item-check" />
                      </div>
                    </div>

                    <!-- Empty state if no parts match -->
                    <div v-if="!getFilteredParts(item).length" class="part-dropdown-empty">
                      <p>No hay repuestos en inventario para "<strong>{{ item.searchQuery }}</strong>"</p>
                      <button
                        type="button"
                        class="button small primary part-create-custom-btn"
                        @mousedown.prevent="selectCustom(item, item.searchQuery)"
                      >
                        <Plus :size="13" /> {{ item.searchQuery ? `Usar "${item.searchQuery}" como personalizado` : 'Cargar repuesto personalizado' }}
                      </button>
                    </div>
                    </template>
                  </div>

                  <!-- Footer: Custom part option (only when there are inventory items in list) -->
                  <div
                    v-if="getFilteredParts(item).length"
                    class="part-dropdown-footer"
                    :class="{ 'is-highlighted': highlightedIndex[item.key] === getFilteredParts(item).length }"
                    @mousedown.prevent="selectCustom(item, item.searchQuery)"
                    @mouseenter="highlightedIndex[item.key] = getFilteredParts(item).length"
                  >
                    <Plus :size="13" />
                    <span>Cargar repuesto personalizado</span>
                  </div>
                </div>

                <!-- Hidden select for form integrity and test access -->
                <select v-model="item.partId" class="sr-only" tabindex="-1" aria-hidden="true">
                  <option value="">Seleccionar repuesto...</option>
                  <option value="custom">Personalizado</option>
                  <option v-for="p in db.parts.filter((part) => !isAlreadySelected(item, part.id))" :key="p.id" :value="p.id">
                    {{ p.name }} ({{ p.brand }})
                  </option>
                </select>
              </div>

              <!-- If Custom: Name Input -->
              <div v-if="item.partId === 'custom'" class="item-custom-name">
                <label>
                  <span class="mini-label">Nombre del repuesto</span>
                  <input
                    :ref="(el) => setCustomInputRef(item.key, el)"
                    v-model="item.customName"
                    placeholder="Descripción del repuesto o insumo..."
                    required
                  />
                </label>
              </div>

              <!-- Quantity Input -->
              <div class="item-qty">
                <label>
                  <span class="mini-label">Cant.</span>
                  <input
                    v-model.number="item.quantity"
                    type="number"
                    :min="props.minQuantity"
                    :step="props.minQuantity"
                    required
                  />
                </label>
              </div>

              <!-- Unit Price Input -->
              <div class="item-unit-price">
                <label>
                  <span class="mini-label">P. Unit ($)</span>
                  <input
                    v-model.number="item.unitPrice"
                    type="number"
                    min="0"
                    step="0.01"
                    required
                  />
                </label>
              </div>

              <!-- Line Subtotal -->
              <div class="item-total">
                <span class="mini-label">Subtotal</span>
                <strong class="item-total-amount">
                  {{ money((Number(item.quantity) || 0) * (Number(item.unitPrice) || 0)) }}
                </strong>
              </div>

              <!-- Delete Button -->
              <button
                type="button"
                class="icon-button item-remove-btn"
                title="Quitar repuesto"
                aria-label="Quitar repuesto"
                @click="removeItem(index)"
              >
                <Trash2 :size="15" />
              </button>
            </div>
          </div>

          <div v-if="items.length" class="parts-subtotal-bar">
            <span>Subtotal de repuestos:</span>
            <strong>{{ money(partsSubtotal) }}</strong>
          </div>
        </div>

</template>

<style scoped>
.parts-builder-section {
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 10px;
  padding: 10px;
  background: rgba(255, 255, 255, 0.4);
}

.parts-builder-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.budget-items-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.budget-item-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 6px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  position: relative;
}

.budget-item-row.has-open-dropdown {
  z-index: 60;
}

.part-combobox-wrapper {
  flex: 1.8;
  min-width: 210px;
  position: relative;
}

.part-search-input-box {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 8px;
  border: 1px solid #cbd5e1;
  border-radius: 7px;
  background: #ffffff;
  height: 32px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.part-search-input-box.is-focused {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
}

.part-search-input-box.has-selected {
  border-color: #93c5fd;
  background: #f8faff;
}

.part-search-icon {
  color: #64748b;
  flex-shrink: 0;
}

.part-search-input {
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  padding: 0 !important;
  margin: 0 !important;
  font-size: 11px;
  color: #0f172a;
  flex: 1;
  outline: none;
  width: 100%;
}

.part-clear-btn {
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: #e2e8f0;
  border-radius: 50%;
  color: #475569;
  cursor: pointer;
  padding: 0;
  flex-shrink: 0;
  transition: background 0.12s ease;
}

.part-clear-btn:hover {
  background: #cbd5e1;
  color: #0f172a;
}

.part-dropdown-menu {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  min-width: 320px;
  max-width: 440px;
  width: 100%;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.18), 0 4px 10px rgba(15, 23, 42, 0.08);
  z-index: 999;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.part-dropdown-scroll {
  max-height: 220px;
  overflow-y: auto;
}

.part-dropdown-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: background 0.12s ease;
  gap: 10px;
}

.part-dropdown-item:hover,
.part-dropdown-item.is-highlighted {
  background: #eff6ff;
}

.part-dropdown-item.is-selected {
  background: #e0f2fe;
}

.part-item-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.part-item-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.part-item-name {
  font-size: 11px;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.part-item-brand {
  font-size: 9.5px;
  color: #2563eb;
  font-weight: 600;
  background: #dbeafe;
  padding: 1px 5px;
  border-radius: 4px;
}

.part-item-sub {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 10px;
  color: #64748b;
}

.part-stock-pill {
  font-size: 9px;
  font-weight: 600;
  padding: 1px 5px;
  border-radius: 4px;
}

.part-stock-pill.stock-ok {
  background: #dcfce7;
  color: #166534;
}

.part-stock-pill.stock-low {
  background: #f1f1f3;
  color: #52525b;
}

:global(html.dark) .part-stock-pill.stock-low {
  background: rgba(255, 255, 255, 0.08);
  color: #d1d1d6;
}

.part-stock-pill.stock-zero {
  background: #fee2e2;
  color: #991b1b;
}

.part-item-price-col {
  display: flex;
  align-items: center;
  gap: 6px;
  text-align: right;
  flex-shrink: 0;
}

.part-item-price {
  font-size: 11px;
  font-weight: 700;
  color: #0f172a;
}

.part-item-check {
  color: #2563eb;
}

.part-dropdown-empty {
  padding: 12px 10px;
  text-align: center;
  color: #64748b;
  font-size: 11px;
}

.part-dropdown-empty p {
  margin: 0 0 6px 0;
}

.part-create-custom-btn {
  width: 100%;
  font-size: 10.5px;
}

.part-dropdown-footer {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #f8fafc;
  border-top: 1px solid #e2e8f0;
  cursor: pointer;
  font-size: 11px;
  font-weight: 500;
  color: #2563eb;
  transition: background 0.12s ease;
}

.part-dropdown-footer:hover,
.part-dropdown-footer.is-highlighted {
  background: #e2e8f0;
}

.item-custom-name {
  flex: 1.2;
}

.item-custom-name input {
  font-size: 11px;
  padding: 8px 10px;
}

.item-qty {
  width: 65px;
}

.item-qty input {
  font-size: 11px;
  padding: 8px 6px;
  text-align: center;
}
.budget-item-row label > input { margin-top: 0; }
.form-page .budget-item-row input:not(.sr-only) { min-height: 32px; height: 32px; padding-top: 5px; padding-bottom: 5px; }

.item-unit-price {
  width: 100px;
}

.item-unit-price input {
  font-size: 11px;
  padding: 8px 8px;
}

.item-total {
  width: 85px;
  display: flex;
  flex-direction: column;
  padding-bottom: 8px;
  text-align: right;
}

.item-total-amount {
  font-size: 12px;
  color: #0f172a;
}

.mini-label {
  display: block;
  font-size: 9px;
  color: #64748b;
  font-weight: 600;
  margin-bottom: 3px;
}

.item-remove-btn {
  color: #94a3b8;
  margin-bottom: 5px;
}

.item-remove-btn:hover {
  color: #ef4444;
  background: #fef2f2;
}

.parts-subtotal-bar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  align-items: center;
  font-size: 11px;
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px solid #e2e8f0;
  color: #64748b;
}

.parts-subtotal-bar strong {
  font-size: 12px;
  color: #0f172a;
}

:global(html.dark .parts-builder-section) {
  border-color: rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.02);
}

:global(html.dark .budget-item-row) {
  background: #202023;
  border-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark .part-search-input-box) {
  background: #1c1c1e;
  border-color: rgba(255, 255, 255, 0.15);
}

:global(html.dark .part-search-input-box.has-selected) {
  background: rgba(37, 99, 235, 0.12);
  border-color: rgba(37, 99, 235, 0.4);
}

:global(html.dark .part-search-input) {
  color: #ffffff;
}

:global(html.dark .part-clear-btn) {
  background: #2c2c2e;
  color: #a1a1a6;
}

:global(html.dark .part-clear-btn:hover) {
  background: #3a3a3c;
  color: #ffffff;
}

:global(html.dark .part-dropdown-menu) {
  background: #1c1c1e;
  border-color: rgba(255, 255, 255, 0.15);
  box-shadow: 0 16px 36px rgba(0, 0, 0, 0.6);
}

:global(html.dark .part-dropdown-item) {
  border-bottom-color: rgba(255, 255, 255, 0.06);
}

:global(html.dark .part-dropdown-item:hover),
:global(html.dark .part-dropdown-item.is-highlighted) {
  background: rgba(255, 255, 255, 0.08);
}

:global(html.dark .part-dropdown-item.is-selected) {
  background: rgba(37, 99, 235, 0.25);
}

:global(html.dark .part-item-name) {
  color: #ffffff;
}

:global(html.dark .part-item-brand) {
  background: rgba(37, 99, 235, 0.2);
  color: #60a5fa;
}

:global(html.dark .part-item-sub) {
  color: #a1a1a6;
}

:global(html.dark .part-item-price) {
  color: #ffffff;
}

:global(html.dark .part-dropdown-footer) {
  background: #242426;
  border-top-color: rgba(255, 255, 255, 0.08);
  color: #60a5fa;
}

:global(html.dark .part-dropdown-footer:hover),
:global(html.dark .part-dropdown-footer.is-highlighted) {
  background: #2c2c2e;
}

:global(html.dark .item-total-amount) {
  color: #ffffff;
}

:global(html.dark .mini-label) {
  color: #8e8e93;
}

:global(html.dark .parts-subtotal-bar) {
  border-top-color: rgba(255, 255, 255, 0.08);
  color: #8e8e93;
}

:global(html.dark .parts-subtotal-bar strong) {
  color: #ffffff;
}

:global(html.dark .item-remove-btn:hover) {
  background: rgba(255, 69, 58, 0.15);
  color: #ff453a;
}

@media (max-width: 640px) {
  .parts-builder-header, .budget-item-row { flex-wrap: wrap; }
  .part-combobox-wrapper, .item-custom-name { flex-basis: 100%; min-width: 0; }
  .part-dropdown-menu { min-width: 0; max-width: 100%; }
  .item-total { margin-left: auto; }
}
</style>
