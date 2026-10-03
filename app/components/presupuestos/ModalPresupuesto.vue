<script setup lang="ts">
import { Check, X, Search, Plus, Trash2, Package } from 'lucide-vue-next'
import type { Budget, Vehicle, BudgetItem } from '~/types'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created', budget: Budget): void
}>()

const { db, client } = useDatabase()
const { money, matches } = useHelpers()
const formError = ref('')
const vehicleSearch = ref('')
const debouncedSearch = useDebouncedValue(vehicleSearch)
const searchInputRef = ref<HTMLInputElement | null>(null)

useModalEscape(() => props.open, () => emit('close'))

interface FormItem {
  key: number
  partId: number | '' | 'custom'
  customName: string
  searchQuery: string
  quantity: number
  unitPrice: number
}

const form = ref({
  vehicle: '' as string | number,
  description: '',
  labor: 0,
  items: [] as FormItem[],
  serviceTypes: [] as string[],
  oilSpec: '',
})

const availableWorkTypes = [
  'Service de mantenimiento',
  'Cambio de distribución',
  'Frenos',
  'Tren delantero y suspensión',
  'Embrague y transmisión',
  'Motor e inyección',
  'Electricidad y batería',
  'Reparación general',
]

const standardOils = [
  '5W-30 Sintético',
  '5W-40 Sintético',
  '10W-40 Semi-sintético',
  '15W-40 Mineral',
  '0W-20 Sintético',
  '0W-30 Sintético',
]

function toggleWorkType(type: string) {
  const idx = form.value.serviceTypes.indexOf(type)
  if (idx >= 0) {
    form.value.serviceTypes.splice(idx, 1)
  } else {
    form.value.serviceTypes.push(type)
    if (type === 'Service de mantenimiento' && !form.value.oilSpec) {
      form.value.oilSpec = '5W-30 Sintético'
    }
    if (!form.value.description.trim()) {
      if (type === 'Service de mantenimiento') {
        form.value.description = 'Service completo con cambio de aceite y filtros'
      } else if (type === 'Cambio de distribución') {
        form.value.description = 'Cambio de kit de distribución y bomba de agua'
      } else if (type === 'Frenos') {
        form.value.description = 'Revisión y cambio de pastillas / discos de freno'
      }
    }
  }
}

const openDropdownKey = ref<number | null>(null)
const debouncedPartSearches = useDebouncedValue(() => form.value.items.map((item) => ({
  key: item.key, query: item.searchQuery,
})))
const settledPartQuery = (item: FormItem) =>
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

function getFilteredParts(item: FormItem) {
  if (!item.searchQuery.trim()) return []
  return filteredPartsByKey.value.get(item.key) || []
}

const selectedVehicle = computed(() =>
  db.value.vehicles.find((v) => v.id === Number(form.value.vehicle))
)

const filteredVehicles = computed(() => {
  const query = debouncedSearch.value.trim()
  if (!query) return []
  return db.value.vehicles.filter((v) => {
    const c = client(v.client)
    return matches(query, v.plate, v.brand, v.model, c?.name, c?.doc, c?.phone)
  })
})

const partsSubtotal = computed(() => {
  return form.value.items.reduce((acc, item) => {
    const qty = Number(item.quantity) || 0
    const price = Number(item.unitPrice) || 0
    return acc + (qty * price)
  }, 0)
})

const netSubtotal = computed(() => {
  return partsSubtotal.value + (Number(form.value.labor) || 0)
})

const vatAmount = computed(() => {
  return Math.round(netSubtotal.value * 0.21)
})

const grandTotal = computed(() => {
  return netSubtotal.value + vatAmount.value
})

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      form.value = {
        vehicle: '',
        description: '',
        labor: 0,
        items: [],
        serviceTypes: [],
        oilSpec: '',
      }
      vehicleSearch.value = ''
      formError.value = ''
      openDropdownKey.value = null
      highlightedIndex.value = {}
      nextTick(() => {
        searchInputRef.value?.focus()
      })
    }
  }
)

function selectVehicle(v: Vehicle) {
  form.value.vehicle = v.id
  vehicleSearch.value = ''
  formError.value = ''
}

function clearVehicle() {
  form.value.vehicle = ''
  vehicleSearch.value = ''
  nextTick(() => {
    searchInputRef.value?.focus()
  })
}

function addItem() {
  const key = Date.now() + Math.random()
  const newItem: FormItem = {
    key,
    partId: '',
    customName: '',
    searchQuery: '',
    quantity: 1,
    unitPrice: 0,
  }
  form.value.items.push(newItem)
  openDropdownKey.value = key
  highlightedIndex.value[key] = -1
  nextTick(() => {
    partInputRefs.value[key]?.focus()
  })
}

function removeItem(index: number) {
  const it = form.value.items[index]
  if (it && openDropdownKey.value === it.key) {
    openDropdownKey.value = null
  }
  form.value.items.splice(index, 1)
}

function selectPart(item: FormItem, p: any) {
  item.partId = p.id
  item.customName = p.name
  item.searchQuery = getPartLabel(p)
  item.unitPrice = p.price || 0
  openDropdownKey.value = null
}

function selectCustom(item: FormItem, initialName?: string) {
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

function clearItemPart(item: FormItem) {
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

function onPartInputFocus(item: FormItem, e: FocusEvent) {
  openDropdownKey.value = item.key
  highlightedIndex.value[item.key] = -1
  const input = e.target as HTMLInputElement
  if (input) {
    input.select()
  }
}

function onPartInputSearch(item: FormItem) {
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

function navigateDown(item: FormItem) {
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

function navigateUp(item: FormItem) {
  if (openDropdownKey.value !== item.key) {
    openDropdownKey.value = item.key
    return
  }
  const parts = getFilteredParts(item)
  const maxIdx = parts.length
  const current = highlightedIndex.value[item.key] ?? 0
  highlightedIndex.value[item.key] = (current - 1) < 0 ? maxIdx : current - 1
}

function selectHighlighted(item: FormItem) {
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
      const activeItem = form.value.items.find((it) => it.key === openDropdownKey.value)
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

function submit() {
  formError.value = ''
  if (!form.value.vehicle) {
    formError.value = 'Por favor buscá y seleccioná un vehículo / cliente titular.'
    return
  }

  if (!form.value.description.trim()) {
    formError.value = 'Ingresá el detalle del trabajo presupuestado.'
    return
  }

  // Validate items
  for (let i = 0; i < form.value.items.length; i++) {
    const it = form.value.items[i]
    if (it.partId === 'custom' && !it.customName.trim()) {
      formError.value = 'Por favor indicá el nombre del repuesto en la opción (Otro).'
      return
    }
    if (!it.partId) {
      formError.value = 'Por favor seleccioná un repuesto o eliminalo si no lo utilizás.'
      return
    }
  }

  const processedItems: BudgetItem[] = form.value.items.map((it) => {
    let name = it.customName
    const isCustom = it.partId === 'custom'
    if (!isCustom) {
      const p = db.value.parts.find((part) => part.id === Number(it.partId))
      if (p) name = p.name
    }
    return {
      id: it.key,
      partId: isCustom ? null : Number(it.partId),
      name: name || 'Repuesto',
      quantity: Number(it.quantity) || 1,
      unitPrice: Number(it.unitPrice) || 0,
      total: (Number(it.quantity) || 1) * (Number(it.unitPrice) || 0),
      isCustom,
    }
  })

  if (!form.value.serviceTypes.length) {
    formError.value = 'Por favor seleccioná al menos un tipo de trabajo a realizar.'
    return
  }

  const id = Math.max(208, ...db.value.quotes.map((q) => q.id)) + 1
  const newBudget: Budget = {
    id,
    vehicle: Number(form.value.vehicle),
    description: form.value.description,
    labor: Number(form.value.labor) || 0,
    materials: partsSubtotal.value,
    items: processedItems,
    subtotal: netSubtotal.value,
    tax: vatAmount.value,
    total: grandTotal.value,
    status: 'Pendiente',
    date: new Date().toISOString().slice(0, 10),
    serviceTypes: [...form.value.serviceTypes],
    oilSpec: form.value.oilSpec || '',
  }

  db.value.quotes.unshift(newBudget)
  emit('created', newBudget)
}
</script>

<template>
  <CommonFormPage v-if="open" class="budget-content">
    <div class="dialog-header">
      <div>
        <span class="eyebrow">OCTANO / PRESUPUESTOS</span>
        <h2>Nuevo presupuesto</h2>
      </div>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <form @submit.prevent="submit" class="entry-form">
      <div class="form-fields">
        <!-- Vehicle / Client Search Selector -->
        <div class="field-block">
          <label class="block-label">
            Vehículo y cliente titular <span class="required-star">*</span>
          </label>

          <!-- Selected Vehicle Card -->
          <div v-if="selectedVehicle" class="selected-target-card">
            <span class="plate">{{ selectedVehicle.plate }}</span>
            <div class="target-info">
              <strong class="target-title">{{ selectedVehicle.brand }} {{ selectedVehicle.model }}</strong>
              <div class="target-sub">
                <span class="target-owner-label">Titular:</span>
                <strong class="target-owner-name">{{ client(selectedVehicle.client)?.name }}</strong>
                <template v-if="client(selectedVehicle.client)?.doc">
                  <span class="target-dot">·</span>
                  <span class="target-doc">DNI {{ client(selectedVehicle.client)?.doc }}</span>
                </template>
                <template v-if="client(selectedVehicle.client)?.phone">
                  <span class="target-dot">·</span>
                  <span class="target-phone">Tel. {{ client(selectedVehicle.client)?.phone }}</span>
                </template>
              </div>
            </div>
            <button
              type="button"
              class="button small change-target-btn"
              @click="clearVehicle"
            >
              Cambiar
            </button>
          </div>

          <!-- Vehicle / Client Search Input & Results -->
          <div v-else class="target-search-container">
            <div class="target-search-box">
              <Search :size="16" class="search-icon" />
              <input
                ref="searchInputRef"
                v-model="vehicleSearch"
                type="text"
                placeholder="Buscá por cliente, DNI, patente o modelo…"
                autocomplete="off"
              />
              <button
                v-if="vehicleSearch"
                type="button"
                class="icon-button clear-icon-btn"
                @click="vehicleSearch = ''"
                aria-label="Limpiar búsqueda"
              >
                <X :size="14" />
              </button>
            </div>

            <!-- Results Dropdown -->
            <p v-if="!vehicleSearch.trim()" class="muted search-hint">Empezá a escribir para buscar vehículos.</p>
            <div v-else class="target-results-list">
              <div
                v-for="v in filteredVehicles"
                :key="v.id"
                class="target-result-item"
                @click="selectVehicle(v)"
              >
                <span class="plate small-plate">{{ v.plate }}</span>
                <div class="result-details">
                  <strong class="result-name">{{ v.brand }} {{ v.model }}</strong>
                  <span class="result-meta">
                    Cliente: <strong>{{ client(v.client)?.name }}</strong>
                    <template v-if="client(v.client)?.doc">
                      · DNI {{ client(v.client)?.doc }}
                    </template>
                  </span>
                </div>
              </div>

              <div v-if="!filteredVehicles.length && vehicleSearch.trim() === debouncedSearch.trim()" class="target-empty-state">
                <p>No se encontraron vehículos ni clientes para "<strong>{{ vehicleSearch }}</strong>"</p>
                <small>Podés verificar los datos o dar de alta el vehículo en la sección Vehículos.</small>
              </div>
            </div>
          </div>

          <!-- Hidden select for accessibility and test compatibility -->
          <select v-model="form.vehicle" aria-label="Vehículo" class="sr-only">
            <option value="">Seleccionar vehículo...</option>
            <option v-for="v in db.vehicles" :key="v.id" :value="v.id">
              {{ v.plate }} · {{ v.brand }} {{ v.model }} — {{ client(v.client)?.name }}
            </option>
          </select>
        </div>

        <!-- Selector de Tipos de Trabajo a Realizar -->
        <div class="field-block work-types-container">
          <label class="block-label">
            Tipos de trabajo a realizar
            <span class="required-star">*</span>
          </label>
          <small class="field-hint">
            Seleccioná las áreas de trabajo para clasificar el presupuesto y actualizar la ficha técnica del vehículo.
          </small>

          <div class="work-types-grid">
            <button
              v-for="wt in availableWorkTypes"
              :key="wt"
              type="button"
              class="work-type-chip"
              :class="{ 'is-selected': form.serviceTypes.includes(wt) }"
              @click="toggleWorkType(wt)"
            >
              <Check v-if="form.serviceTypes.includes(wt)" :size="13" class="chip-check" />
              <span>{{ wt }}</span>
            </button>
          </div>

          <!-- Si seleccionó Service de mantenimiento: Especificación del aceite -->
          <div
            v-if="form.serviceTypes.includes('Service de mantenimiento')"
            class="oil-spec-panel"
          >
            <div class="oil-spec-header">
              <label class="mini-label">Especificación y viscosidad del aceite para el service:</label>
              <small class="muted">Esta información se registrará directamente en el historial y ficha QR del cliente</small>
            </div>
            <div class="oil-quick-options">
              <button
                v-for="oil in standardOils"
                :key="oil"
                type="button"
                class="oil-pill-btn"
                :class="{ 'is-active': form.oilSpec === oil }"
                @click="form.oilSpec = oil"
              >
                {{ oil }}
              </button>
            </div>
            <input
              v-model="form.oilSpec"
              type="text"
              placeholder="Ej: Shell Helix Ultra 5W-30 Sintético o escribir marca/viscosidad personalizada..."
              class="oil-custom-input"
            />
          </div>
        </div>

        <label>
          Detalle del trabajo / Diagnóstico
          <input
            v-model="form.description"
            required
            placeholder="Ej. Cambio de pastillas de freno y rectificación de discos"
          />
        </label>

        <!-- Dynamic Parts / Items Selector -->
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

          <!-- Empty Parts state -->
          <div v-if="!form.items.length" class="empty-parts-hint">
            <Package :size="20" class="muted" />
            <p>No se agregaron repuestos aún. Hacé clic en <strong>Agregar repuesto</strong> para sumar repuestos o insumos.</p>
          </div>

          <!-- Items list -->
          <div v-else class="budget-items-list">
            <div
              v-for="(item, index) in form.items"
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
                  <option v-for="p in db.parts" :key="p.id" :value="p.id">
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
                    min="1"
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

          <div v-if="form.items.length" class="parts-subtotal-bar">
            <span>Subtotal de repuestos:</span>
            <strong>{{ money(partsSubtotal) }}</strong>
          </div>
        </div>

        <!-- Labor Input -->
        <label>
          Mano de obra ($)
          <input
            v-model.number="form.labor"
            type="number"
            min="0"
            required
            placeholder="0"
          />
        </label>

        <!-- Breakdown with VAT / IVA 21% -->
        <div class="quote-summary-card">
          <div class="summary-line">
            <span>Mano de obra</span>
            <strong>{{ money(form.labor || 0) }}</strong>
          </div>
          <div class="summary-line">
            <span>Repuestos e insumos</span>
            <strong>{{ money(partsSubtotal) }}</strong>
          </div>
          <div class="summary-line subtotal-line">
            <span>Subtotal Neto</span>
            <strong>{{ money(netSubtotal) }}</strong>
          </div>
          <div class="summary-line vat-line">
            <span style="display: flex; align-items: center; gap: 4px">
              IVA (21%)
              <span class="badge neutral" style="font-size: 8.5px; padding: 1px 5px">ARCA</span>
            </span>
            <strong>{{ money(vatAmount) }}</strong>
          </div>
          <div class="summary-line total-line">
            <span>TOTAL ESTIMADO (CON IVA)</span>
            <strong class="grand-total-display">{{ money(grandTotal) }}</strong>
          </div>
        </div>

        <p v-if="formError" class="error-message" role="alert">
          {{ formError }}
        </p>
      </div>

      <footer class="dialog-footer modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary">
          <Check :size="16" /> Guardar y generar presupuesto
        </button>
      </footer>
    </form>
  </CommonFormPage>
</template>

<style scoped>
.budget-content {
  width: 100%;
}

.parts-builder-section {
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 10px;
  padding: 14px;
  background: rgba(255, 255, 255, 0.4);
}

.parts-builder-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.empty-parts-hint {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-radius: 8px;
  background: #f8fafc;
  color: #64748b;
  font-size: 11px;
  border: 1px dashed #cbd5e1;
}

.empty-parts-hint p {
  margin: 0;
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
  padding: 10px;
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
  height: 36px;
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
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid #e2e8f0;
  color: #64748b;
}

.parts-subtotal-bar strong {
  font-size: 12px;
  color: #0f172a;
}

/* Summary Card with VAT */
.quote-summary-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.summary-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11.5px;
  color: #475569;
}

.summary-line strong {
  color: #0f172a;
}

.subtotal-line {
  border-top: 1px solid #e2e8f0;
  padding-top: 6px;
  margin-top: 2px;
  font-weight: 600;
}

.vat-line {
  color: #2563eb;
  font-weight: 600;
}

.total-line {
  border-top: 2px solid #0f172a;
  padding-top: 8px;
  margin-top: 4px;
  font-size: 12.5px;
  font-weight: 800;
  color: #0f172a;
}

.grand-total-display {
  font-family: 'Public Sans', sans-serif;
  font-size: 20px;
  font-weight: 800;
  color: #2563eb;
}

/* Work Types & Oil Spec */
.work-types-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-hint {
  font-size: 11px;
  color: #64748b;
  margin-top: -3px;
}

.work-types-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.work-type-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 12px;
  border-radius: 7px;
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #334155;
  font-size: 11.5px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.work-type-chip:hover {
  background: #f1f5f9;
  border-color: #94a3b8;
}

.work-type-chip.is-selected {
  background: #eff6ff;
  border-color: #2563eb;
  color: #1d4ed8;
  font-weight: 600;
}

.chip-check {
  color: #2563eb;
}

.oil-spec-panel {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 4px;
}

.oil-spec-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.oil-quick-options {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.oil-pill-btn {
  font-size: 10.5px;
  padding: 3px 8px;
  border-radius: 5px;
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #475569;
  cursor: pointer;
  transition: all 0.12s ease;
}

.oil-pill-btn:hover {
  background: #f1f5f9;
}

.oil-pill-btn.is-active {
  background: #2563eb;
  border-color: #2563eb;
  color: #ffffff;
  font-weight: 600;
}

.oil-custom-input {
  font-size: 11px;
  padding: 7px 10px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #ffffff;
}

/* Dark Mode Overrides */
:global(html.dark .field-hint) {
  color: #8e8e93;
}

:global(html.dark .work-type-chip) {
  background: #1c1c1e;
  border-color: rgba(255, 255, 255, 0.15);
  color: #d1d5db;
}

:global(html.dark .work-type-chip:hover) {
  background: rgba(255, 255, 255, 0.08);
}

:global(html.dark .work-type-chip.is-selected) {
  background: rgba(37, 99, 235, 0.2);
  border-color: #3b82f6;
  color: #93c5fd;
}

:global(html.dark .chip-check) {
  color: #60a5fa;
}

:global(html.dark .oil-spec-panel) {
  background: #1c1c1e;
  border-color: rgba(255, 255, 255, 0.12);
}

:global(html.dark .oil-pill-btn) {
  background: #2c2c2e;
  border-color: rgba(255, 255, 255, 0.12);
  color: #d1d5db;
}

:global(html.dark .oil-pill-btn:hover) {
  background: #3a3a3c;
}

:global(html.dark .oil-pill-btn.is-active) {
  background: #0a84ff;
  border-color: #0a84ff;
  color: #ffffff;
}

:global(html.dark .oil-custom-input) {
  background: #252528;
  border-color: rgba(255, 255, 255, 0.15);
  color: #ffffff;
}
:global(html.dark .parts-builder-section) {
  border-color: rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.02);
}

:global(html.dark .empty-parts-hint) {
  background: #202023;
  border-color: rgba(255, 255, 255, 0.1);
  color: #8e8e93;
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

:global(html.dark .quote-summary-card) {
  background: #202023;
  border-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark .summary-line) {
  color: #a1a1a6;
}

:global(html.dark .summary-line strong) {
  color: #ffffff;
}

:global(html.dark .subtotal-line) {
  border-top-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark .vat-line) {
  color: #64d2ff;
}

:global(html.dark .total-line) {
  border-top-color: rgba(255, 255, 255, 0.2);
  color: #ffffff;
}

:global(html.dark .grand-total-display) {
  color: #64d2ff;
}

:global(html.dark .item-remove-btn:hover) {
  background: rgba(255, 69, 58, 0.15);
  color: #ff453a;
}
</style>
