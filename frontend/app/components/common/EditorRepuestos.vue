<script setup lang="ts">
import type { Part } from '~/types'
import { Check, X, Search, Plus, Pencil, Trash2 } from '@lucide/vue'
import type { PartEditorItem } from '~/types/partEditor'

const props = withDefaults(defineProps<{ minQuantity?: number; parts?: Part[]; catalogPricesReadonly?: boolean; validateStock?: boolean; allowCustom?: boolean }>(), { minQuantity: 1, catalogPricesReadonly: false, validateStock: false, allowCustom: true })
const items = defineModel<PartEditorItem[]>({ required: true })
const availableParts = computed(() => props.parts ?? [])
const { money, matches } = useHelpers()
const { notify } = useWorkshopToast()
const partsSubtotal = computed(() => items.value.reduce((sum, item) => sum + (Number(item.quantity) || 0) * (Number(item.unitPrice) || 0), 0))

function emptyDraft() {
  return {
    key: null as number | null,
    partId: '' as number | '' | 'custom',
    customName: '',
    searchQuery: '',
    quantity: '' as number | string,
    unitPrice: '' as number | string,
  }
}
const draft = ref(emptyDraft())
const selectedPart = computed(() => availableParts.value.find(p => p.id === draft.value.partId))
const stockError = computed(() => {
  if (!props.validateStock || typeof draft.value.partId !== 'number') return ''
  const part = selectedPart.value
  if (!part || part.archived || !Number.isFinite(part.stock)) return 'El repuesto ya no está disponible.'
  if (part.stock <= 0) return 'Sin stock disponible.'
  if (Number(draft.value.quantity) > part.stock) return 'La cantidad supera el stock disponible.'
  return ''
})
const editingKey = computed(() => draft.value.key)
const listedItems = computed(() => items.value.filter((item) => item.key !== editingKey.value))
const searchInputRef = ref<HTMLInputElement | null>(null)
const customInputRef = ref<HTMLInputElement | null>(null)
const openDropdown = ref(false)
const highlightedIndex = ref(-1)
const settledQuery = useDebouncedValue(() => draft.value.searchQuery)

function getPartLabel(p: { name: string; brand?: string }): string {
  return p.brand ? `${p.name} (${p.brand})` : p.name
}

function itemPart(item: PartEditorItem) {
  return typeof item.partId === 'number' ? availableParts.value.find(p => p.id === item.partId) : undefined
}

function itemName(item: PartEditorItem): string {
  if (item.partId === 'custom') return item.customName || 'Personalizado'
  return itemPart(item)?.name || item.searchQuery || 'Repuesto'
}

function itemBrand(item: PartEditorItem): string {
  return itemPart(item)?.brand || ''
}

function itemCode(item: PartEditorItem): string {
  return itemPart(item)?.oem || ''
}

const filteredParts = computed(() => {
  const query = settledQuery.value.trim()
  if (!query || draft.value.partId === 'custom') return []
  return availableParts.value.filter((p) =>
    !p.archived
    && matches(query, p.name, p.brand, p.oem)
    && !items.value.some((it) => it.key !== editingKey.value && it.partId === p.id)
  )
})

function selectPart(p: Part) {
  draft.value.partId = p.id
  draft.value.customName = p.name
  draft.value.searchQuery = getPartLabel(p)
  draft.value.unitPrice = p.price || 0
  openDropdown.value = false
  highlightedIndex.value = -1
}

function selectCustom(name?: string) {
  if (!props.allowCustom) return
  draft.value.partId = 'custom'
  draft.value.customName = name ?? (draft.value.searchQuery && draft.value.searchQuery !== 'Personalizado' ? draft.value.searchQuery : draft.value.customName)
  draft.value.searchQuery = 'Personalizado'
  openDropdown.value = false
  highlightedIndex.value = -1
  nextTick(() => customInputRef.value?.focus())
}

function resetDraft() {
  draft.value = emptyDraft()
  openDropdown.value = false
  highlightedIndex.value = -1
  nextTick(() => searchInputRef.value?.focus())
}

function onSearchFocus(e: FocusEvent) {
  openDropdown.value = true
  highlightedIndex.value = -1
  ;(e.target as HTMLInputElement)?.select()
}

function onSearchInput() {
  openDropdown.value = true
  highlightedIndex.value = -1
  if (typeof draft.value.partId === 'number') {
    const part = availableParts.value.find((p) => p.id === draft.value.partId)
    if (part && draft.value.searchQuery !== getPartLabel(part)) {
      draft.value.partId = ''
      draft.value.customName = ''
      draft.value.unitPrice = 0
    }
  }
}

function onCustomInput() {
  if (draft.value.customName.trim()) return
  draft.value.partId = ''
  draft.value.searchQuery = ''
  draft.value.unitPrice = ''
  openDropdown.value = true
  highlightedIndex.value = -1
  nextTick(() => searchInputRef.value?.focus())
}

function navigateDown() {
  openDropdown.value = true
  const max = filteredParts.value.length - (props.allowCustom ? 0 : 1)
  highlightedIndex.value = Math.min(highlightedIndex.value + 1, max)
}

function navigateUp() {
  openDropdown.value = true
  const max = filteredParts.value.length
  highlightedIndex.value = Math.max(highlightedIndex.value - 1, 0)
}

function selectHighlighted() {
  if (draft.value.searchQuery !== settledQuery.value) return
  const parts = filteredParts.value
  const idx = highlightedIndex.value
  if (idx >= 0 && idx < parts.length) selectPart(parts[idx])
  else if (idx === parts.length || (parts.length === 0 && draft.value.searchQuery.trim())) selectCustom(draft.value.searchQuery)
  else if (parts.length === 1) selectPart(parts[0])
}

function onPickerEnter(e: KeyboardEvent) {
  const target = e.target as HTMLElement | null
  if (target?.tagName !== 'INPUT') return
  e.preventDefault()
  e.stopPropagation()
  if (draft.value.partId === '') {
    if (highlightedIndex.value >= 0) selectHighlighted()
    else if (filteredParts.value.length) selectPart(filteredParts.value[0])
    else if (draft.value.searchQuery.trim()) selectCustom(draft.value.searchQuery)
  }
  if (canCommit.value) commitDraft()
}

const canCommit = computed(() => {
  const d = draft.value
  if (d.partId === '') return false
  if (d.partId === 'custom' && (!props.allowCustom || !d.customName.trim())) return false
  if (d.quantity === '' || !Number.isFinite(Number(d.quantity)) || Number(d.quantity) < props.minQuantity) return false
  if (d.unitPrice === '' || !Number.isFinite(Number(d.unitPrice)) || Number(d.unitPrice) < 0) return false
  if (stockError.value) return false
  return true
})

function commitDraft() {
  if (!canCommit.value) return
  const d = draft.value
  const selected = typeof d.partId === 'number' ? availableParts.value.find((p) => p.id === d.partId) : undefined
  if (d.partId !== 'custom' && items.value.some((it) => it.key !== d.key && it.partId === d.partId)) {
    notify('Este repuesto ya está en la lista. Editá su fila para cambiar la cantidad.')
    return
  }
  const item: PartEditorItem = {
    key: d.key ?? Date.now() + Math.random(),
    partId: d.partId,
    customName: d.customName.trim(),
    searchQuery: selected ? getPartLabel(selected) : 'Personalizado',
    quantity: Number(d.quantity),
    unitPrice: Number(d.unitPrice),
  }
  const index = d.key == null ? -1 : items.value.findIndex((it) => it.key === d.key)
  if (index >= 0) items.value[index] = item
  else items.value.push(item)
  resetDraft()
}

function startEdit(item: PartEditorItem) {
  draft.value = { ...item }
  openDropdown.value = false
  highlightedIndex.value = -1
  nextTick(() => {
    if (item.partId === 'custom') customInputRef.value?.focus()
    else searchInputRef.value?.focus()
  })
}

function removeItem(index: number) {
  const item = items.value[index]
  if (item && item.key === editingKey.value) resetDraft()
  items.value.splice(index, 1)
}

function onDocClick(e: MouseEvent) {
  if (!(e.target as HTMLElement).closest('.part-combobox-wrapper')) openDropdown.value = false
}

onMounted(() => {
  if (import.meta.client) document.addEventListener('click', onDocClick)
})
onBeforeUnmount(() => {
  if (import.meta.client) document.removeEventListener('click', onDocClick)
})
</script>

<template>
  <div class="parts-builder-section" @keydown.enter="onPickerEnter">
    <!-- Buscador + cantidad + confirmación -->
    <div class="parts-table-wrap parts-picker-wrap">
    <table class="parts-picker-table">
      <thead>
        <tr>
          <th class="col-picker-name">Repuesto / Insumo</th>
          <th class="col-qty num">Cantidad</th>
          <th class="col-price num">Precio unitario</th>
          <th class="col-subtotal num">Subtotal</th>
          <th class="col-actions"><span class="sr-only">Acciones</span></th>
        </tr>
      </thead>
      <tbody>
        <tr :class="{ 'is-editing': editingKey !== null }">
          <td class="col-picker-name part-combobox-wrapper">
            <template v-if="draft.partId !== 'custom'">
            <div class="part-search-input-box" :class="{ 'is-focused': openDropdown }">
              <Search :size="14" class="part-search-icon" />
              <input
                ref="searchInputRef"
                v-model="draft.searchQuery"
                type="text"
                class="part-search-input"
                placeholder="Buscar Repuesto"
                autocomplete="off"
                @focus="onSearchFocus"
                @input="onSearchInput"
                @keydown.down.prevent="navigateDown"
                @keydown.up.prevent="navigateUp"
                @keydown.escape.stop="openDropdown = false"
              />
              <button
                v-if="draft.searchQuery"
                type="button"
                class="part-clear-btn"
                title="Limpiar búsqueda"
                aria-label="Limpiar búsqueda"
                @click="resetDraft"
              >
                <X :size="13" />
              </button>

              <div v-if="openDropdown && draft.searchQuery.trim()" class="part-dropdown-menu">
                <div class="part-dropdown-scroll">
                  <p v-if="settledQuery !== draft.searchQuery" class="muted part-dropdown-empty">Buscando repuestos…</p>
                  <template v-else>
                    <table v-if="filteredParts.length" class="parts-suggestions-table">
                      <thead>
                        <tr>
                          <th>Nombre</th>
                          <th>Marca</th>
                          <th>Código</th>
                          <th>Stock</th>
                          <th class="num">Precio</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr
                          v-for="(p, pIdx) in filteredParts"
                          :key="p.id"
                          :class="{ 'is-highlighted': highlightedIndex === pIdx }"
                          @mousedown.prevent="selectPart(p)"
                          @mouseenter="highlightedIndex = pIdx"
                        >
                          <td class="suggestion-name">
                            <span class="suggestion-name-content">
                              <strong>{{ p.name }}</strong>
                              <Check v-if="draft.partId === p.id" :size="13" class="part-item-check" />
                            </span>
                          </td>
                          <td>{{ p.brand || '—' }}</td>
                          <td>{{ p.oem || '—' }}</td>
                          <td class="suggestion-stock">{{ p.stock > 0 ? p.stock : '-' }}</td>
                          <td class="num">{{ money(p.price) }}</td>
                        </tr>
                      </tbody>
                    </table>

                    <div v-if="!filteredParts.length" class="part-dropdown-empty">
                      <p>No hay repuestos en inventario para "<strong>{{ draft.searchQuery }}</strong>"</p>
                      <button v-if="props.allowCustom" type="button" class="button small primary part-create-custom-btn" @mousedown.prevent="selectCustom(draft.searchQuery)">
                        <Plus :size="13" /> Usar "{{ draft.searchQuery }}" como personalizado
                      </button>
                    </div>
                  </template>
                </div>

                <div
                  v-if="props.allowCustom && filteredParts.length"
                  class="part-dropdown-footer"
                  :class="{ 'is-highlighted': highlightedIndex === filteredParts.length }"
                  @mousedown.prevent="selectCustom(draft.searchQuery)"
                  @mouseenter="highlightedIndex = filteredParts.length"
                >
                  <Plus :size="13" />
                  <span>Cargar repuesto personalizado</span>
                </div>
              </div>
            </div>
          </template>

            <div v-else class="part-search-input-box custom-name-field">
              <input
                ref="customInputRef"
                v-model="draft.customName"
                type="text"
                class="part-search-input"
                aria-label="Descripción del repuesto personalizado"
                placeholder="Descripción del repuesto o insumo..."
                @input="onCustomInput"
              />
            </div>
          </td>
          <td class="col-qty num">
            <input v-model.number="draft.quantity" aria-label="Cantidad" type="number" :min="props.minQuantity" :max="props.validateStock ? selectedPart?.stock : undefined" :step="props.minQuantity" :disabled="draft.partId === '' || (props.validateStock && selectedPart?.stock === 0)" :aria-invalid="!!stockError" />
            <small v-if="selectedPart" class="stock-available" :class="{ 'stock-unavailable': selectedPart.stock === 0 }" role="status">{{ selectedPart.stock === 0 ? '0 en stock' : `(${selectedPart.stock.toLocaleString('es-AR')} en stock)` }}</small>
            <small v-if="stockError && selectedPart?.stock !== 0" class="stock-error" role="alert">{{ stockError }}</small>
          </td>
          <td class="col-price num">
            <input v-model.number="draft.unitPrice" aria-label="Precio unitario" type="number" min="0" step="0.01" :disabled="draft.partId === ''" :readonly="props.catalogPricesReadonly && typeof draft.partId === 'number'" />
          </td>
          <td class="col-subtotal num">
            <strong>{{ draft.quantity !== '' && draft.unitPrice !== '' ? money(Number(draft.quantity) * Number(draft.unitPrice)) : '' }}</strong>
          </td>
          <td class="col-actions actions-col">
            <button v-if="editingKey !== null" type="button" class="icon-button" title="Cancelar edición" aria-label="Cancelar edición" @click="resetDraft">
              <X :size="16" />
            </button>
            <button
              v-if="draft.partId !== ''"
              type="button"
              class="icon-button picker-commit"
              :disabled="!canCommit"
              :title="stockError || (editingKey !== null ? 'Guardar cambios' : 'Agregar a la lista')"
              :aria-label="editingKey !== null ? 'Guardar cambios' : 'Agregar a la lista'"
              @click="commitDraft"
            >
              <Check :size="17" />
            </button>
          </td>
        </tr>
      </tbody>
    </table>
    </div>

    <!-- Lista de repuestos agregados -->
    <div v-if="listedItems.length" class="parts-table-wrap">
      <table class="parts-table">
        <thead>
          <tr>
            <th class="col-name">Repuesto</th>
            <th class="col-brand">Marca</th>
            <th class="col-code">Código</th>
            <th class="col-qty num">Cantidad</th>
            <th class="col-price num">Precio unitario</th>
            <th class="col-subtotal num">Subtotal</th>
            <th class="col-actions actions-col"><span class="sr-only">Acciones</span></th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in listedItems"
            :key="item.key"
          >
            <td class="col-name name-col">{{ itemName(item) }}</td>
            <td class="col-brand">{{ itemBrand(item) || '—' }}</td>
            <td class="col-code">{{ itemCode(item) || '—' }}</td>
            <td class="col-qty num">{{ item.quantity }}</td>
            <td class="col-price num">{{ money(Number(item.unitPrice) || 0) }}</td>
            <td class="col-subtotal num strong">
              {{ money((Number(item.quantity) || 0) * (Number(item.unitPrice) || 0)) }}
            </td>
            <td class="col-actions actions-col">
              <button type="button" class="icon-button item-edit-btn" title="Editar renglón" aria-label="Editar renglón" @click="startEdit(item)">
                <Pencil :size="14" />
              </button>
              <button type="button" class="icon-button item-remove-btn" title="Quitar repuesto" aria-label="Quitar repuesto" @click="removeItem(items.indexOf(item))">
                <Trash2 :size="14" />
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-else-if="!items.length" class="parts-empty muted">Todavía no agregaste repuestos.</p>

    <div v-if="items.length" class="parts-subtotal-bar">
      <span>Subtotal de repuestos:</span>
      <strong>{{ money(partsSubtotal) }}</strong>
    </div>
  </div>
</template>

<style scoped>
.parts-builder-section {
  padding: 10px 0 4px;
  background: transparent;
}

.parts-picker-table,
.parts-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  background: transparent;
  font-size: 11px;
}

.parts-picker-table {
  position: relative;
  z-index: 40;
}

.parts-picker-table .col-picker-name { width: 48%; }
.parts-picker-table .col-qty { width: 14%; }
.parts-picker-table .col-price { width: 16%; }
.parts-picker-table .col-subtotal { width: 14%; }
.parts-picker-table .col-actions { width: 8%; }
.parts-table .col-name { width: 30%; }
.parts-table .col-brand { width: 10%; }
.parts-table .col-code { width: 10%; }
.parts-table .col-qty { width: 9%; }
.parts-table .col-price { width: 14%; }
.parts-table .col-subtotal { width: 15%; }
.parts-table .col-actions { width: 12%; }

.parts-picker-table th,
.parts-table th {
  text-align: center;
  vertical-align: middle;
  font-size: 9.5px;
  font-weight: 700;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  background: transparent;
  padding: 8px 14px;
  border-bottom: 1px solid #e2e8f0;
  white-space: nowrap;
}

.parts-picker-table td,
.parts-table td {
  text-align: center;
  padding: 10px 8px;
  color: #334155;
  border-bottom: 1px solid #f1f5f9;
  vertical-align: middle;
}

.parts-picker-table .num,
.parts-table .num {
  text-align: center;
}

.parts-picker-table th { padding-inline: 8px; white-space: normal; }
.parts-picker-table td { vertical-align: top; }
.parts-picker-table .col-subtotal strong { display: flex; align-items: center; justify-content: center; min-height: 40px; }
.parts-picker-table td.col-actions { padding: 16px 4px 10px; }
.stock-available { display: block; margin-top: 6px; color: var(--muted, #64748b); font-size: 11px; line-height: 1.4; overflow-wrap: anywhere; }
.stock-available.stock-unavailable { color: #dc2626; border: 0; background: transparent; box-shadow: none; padding: 0; }
:global(html.dark .stock-available.stock-unavailable) { color: #ff6961; }
.stock-error { display: block; margin-top: 6px; color: #dc2626; font-size: 11px; line-height: 1.4; }
:global(html.dark .stock-error) { color: #ff6961; }

.parts-picker-table .col-actions,
.parts-table .col-actions {
  text-align: center;
  white-space: nowrap;
}

.parts-picker-table .col-actions .icon-button,
.parts-table .col-actions .icon-button {
  width: 27px;
  height: 27px;
  margin-left: 4px;
  vertical-align: middle;
}

.parts-picker-table tbody tr.is-editing td {
  box-shadow: inset 0 -2px 0 #94a3b8;
}

.part-combobox-wrapper {
  position: relative;
}

.part-search-input-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  border: 1px solid #cbd5e1;
  border-radius: 7px;
  background: transparent;
  height: 40px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.part-search-input-box.is-focused {
  border-color: #94a3b8;
  box-shadow: 0 0 0 3px rgba(148, 163, 184, 0.2);
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
  font-size: 13px;
  color: #0f172a;
  flex: 1;
  outline: none;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
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

.custom-name-field {
  display: block;
}

.custom-name-field input {
  width: 100%;
  height: 40px;
  font-size: 13px;
  padding: 0 12px;
}

.part-dropdown-menu {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  min-width: 520px;
  max-width: 680px;
  width: 100%;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.18), 0 4px 10px rgba(15, 23, 42, 0.08);
  /* El dropdown flota sobre el modal: mantiene su propio fondo para no volverse ilegible. */
  z-index: 999;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.part-dropdown-scroll {
  max-height: 220px;
  overflow-y: auto;
}

.parts-suggestions-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
  table-layout: fixed;
}

.parts-suggestions-table th {
  text-align: center;
  font-size: 9px;
  font-weight: 700;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  padding: 6px 12px;
  border-bottom: 1px solid #e2e8f0;
  white-space: nowrap;
}

.parts-suggestions-table td {
  text-align: center;
  padding: 6px 8px;
  color: #334155;
  border-bottom: 1px solid #f1f5f9;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.parts-suggestions-table tbody tr {
  cursor: pointer;
  transition: background 0.12s ease;
}

.parts-suggestions-table tbody tr:hover td,
.parts-suggestions-table tbody tr.is-highlighted td {
  background: #f1f5f9;
}

.parts-suggestions-table th:nth-child(1),
.parts-suggestions-table td:nth-child(1) { width: 34%; }
.parts-suggestions-table th:nth-child(2),
.parts-suggestions-table td:nth-child(2) { width: 18%; }
.parts-suggestions-table th:nth-child(3),
.parts-suggestions-table td:nth-child(3) { width: 18%; }
.parts-suggestions-table th:nth-child(4),
.parts-suggestions-table td:nth-child(4) { width: 14%; }
.parts-suggestions-table th:nth-child(5),
.parts-suggestions-table td:nth-child(5) { width: 16%; }

.parts-suggestions-table .num {
  text-align: center;
}

.parts-suggestions-table .suggestion-name-content {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-width: 0;
}

.parts-suggestions-table .suggestion-name strong {
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.parts-suggestions-table .suggestion-stock {
  font-weight: 600;
  color: #0f172a;
}

:global(html.dark .parts-suggestions-table .suggestion-stock) {
  color: #ffffff;
}

.part-item-check {
  color: #475569;
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
  color: #475569;
  transition: background 0.12s ease;
}

.part-dropdown-footer:hover,
.part-dropdown-footer.is-highlighted {
  background: #e2e8f0;
}

.parts-picker-table .col-subtotal strong {
  font-size: 12px;
  color: #0f172a;
}

.parts-picker-table .col-qty input,
.parts-picker-table .col-price input {
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  height: 40px;
  font-size: 12px;
  padding: 0 6px;
}

.custom-name-field input,
.parts-picker-table .col-qty input,
.parts-picker-table .col-price input {
  background: transparent !important;
}

.parts-picker-table .col-qty input:disabled,
.parts-picker-table .col-price input:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.parts-picker-table .col-qty input,
.parts-picker-table .col-price input {
  text-align: center;
}

.parts-builder-section :is(.picker-commit, .item-remove-btn) {
  color: #94a3b8;
  border: 0;
  background: transparent;
}

.parts-builder-section .picker-commit:hover:not(:disabled) {
  background: transparent;
  color: #2563eb;
}

.picker-commit:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.mini-label {
  display: block;
  font-size: 10px;
  color: #64748b;
  font-weight: 600;
  margin-bottom: 5px;
}

.parts-table-wrap {
  margin-top: 18px;
}

.parts-picker-wrap {
  margin-top: 0;
}

.parts-table {
  font-size: 11px;
}

.parts-table tbody tr:hover td {
  background: transparent;
}

.parts-table tbody tr.is-editing td {
  background: transparent;
}

.parts-table .strong {
  font-weight: 700;
  color: #0f172a;
}

.parts-table .name-col {
  font-weight: 600;
  color: #0f172a;
  white-space: normal;
}

.item-edit-btn {
  color: #64748b;
}

.item-edit-btn:hover {
  color: #334155;
  background: #f1f5f9;
}

.parts-builder-section .item-remove-btn:hover {
  color: #ef4444;
  background: transparent;
}

.parts-empty {
  font-size: 11px;
  margin: 10px 0 0;
  text-align: center;
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

:global(html.dark .parts-builder-section),
:global(html.dark .parts-picker-table),
:global(html.dark .parts-table) {
  background: transparent;
}

:global(html.dark .parts-picker-table tbody tr.is-editing td) {
  box-shadow: inset 0 -2px 0 rgba(255, 255, 255, 0.35);
}

:global(html.dark .parts-picker-table th),
:global(html.dark .parts-table th) {
  background: transparent;
  color: #8e8e93;
  border-bottom-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark .parts-picker-table td),
:global(html.dark .parts-table td) {
  color: #d1d1d6;
  border-bottom-color: rgba(255, 255, 255, 0.06);
}

:global(html.dark .parts-table tbody tr:hover td),
:global(html.dark .parts-table tbody tr.is-editing td),
:global(html.dark .parts-picker-table tbody tr.is-editing td) {
  background: transparent;
}

:global(html.dark .part-search-input-box) {
  background: transparent;
  border-color: rgba(255, 255, 255, 0.15);
}

:global(html.dark .part-search-input),
:global(html.dark .custom-name-field input),
:global(html.dark .parts-picker-table .col-qty input),
:global(html.dark .parts-picker-table .col-price input) {
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

:global(html.dark .parts-suggestions-table th) {
  color: #8e8e93;
  border-bottom-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark .parts-suggestions-table td) {
  color: #d1d1d6;
  border-bottom-color: rgba(255, 255, 255, 0.06);
}

:global(html.dark .parts-suggestions-table tbody tr:hover td),
:global(html.dark .parts-suggestions-table tbody tr.is-highlighted td) {
  background: rgba(255, 255, 255, 0.08);
}

:global(html.dark .parts-suggestions-table .suggestion-name strong),
:global(html.dark .parts-picker-table .col-subtotal strong),
:global(html.dark .parts-table .name-col),
:global(html.dark .parts-table .strong),
:global(html.dark .parts-subtotal-bar strong) {
  color: #ffffff;
}

:global(html.dark .mini-label),
:global(html.dark .parts-subtotal-bar),
:global(html.dark .parts-empty) {
  color: #8e8e93;
}

:global(html.dark .part-dropdown-footer) {
  background: #242426;
  border-top-color: rgba(255, 255, 255, 0.08);
  color: #d1d1d6;
}

:global(html.dark .part-dropdown-footer:hover),
:global(html.dark .part-dropdown-footer.is-highlighted) {
  background: #2c2c2e;
}

@media (max-width: 640px) {
  .part-dropdown-menu {
    min-width: 0;
    max-width: 100%;
  }
}
</style>
