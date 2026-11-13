<script setup lang="ts">
import {
  Plus,
  Search,
  Package,
  X,
  CarFront,
  LayoutGrid,
  Table,
  Pencil,
  Trash2,
} from '@lucide/vue'
import type { Part } from '~/types'

const { db, lowStock } = useDatabase()
const { money, matches } = useHelpers()
const { notify } = useWorkshopToast()

const search = ref('')
const debouncedSearch = useDebouncedValue(search)
const lowStockOnly = ref(false)
const viewMode = useListView('inventario', 'table', ['table', 'cards'] as const)
const formModalOpen = ref(false)
const editingPart = ref<Part | null>(null)
const deletingPart = ref<Part | null>(null)
const compatibilityPart = ref<Part | null>(null)

function handleCompatibilityUpdated(part: Part) {
  compatibilityPart.value = null
  notify(`Compatibilidad de "${part.name}" actualizada.`)
}

function editPart(part: Part) {
  editingPart.value = part
  formModalOpen.value = true
}
function handleUpdated(part: Part) {
  formModalOpen.value = false
  editingPart.value = null
  notify(`Repuesto "${part.name}" actualizado.`)
}
function deletePart() {
  if (!deletingPart.value) return
  deletingPart.value.archived = true
  deletingPart.value = null
  formModalOpen.value = false
  editingPart.value = null
  notify('Repuesto eliminado del inventario. Su historial se conserva.')
}

const selectedBrand = ref('')
const selectedModel = ref('')
const selectedVersion = ref('')
const compatibilityCatalog = computed(() => [
  ...db.value.vehicleCatalog,
  ...db.value.parts.filter(part => !part.archived).flatMap(part => part.compatible.map(entry => ({ ...entry, engine: '' }))),
])
const availableBrands = computed(() => vehicleCatalogOptions(compatibilityCatalog.value, 'brand'))
const availableModels = computed(() => vehicleCatalogOptions(compatibilityCatalog.value, 'model', selectedBrand.value))
const availableVersions = computed(() => vehicleCatalogOptions(compatibilityCatalog.value, 'version', selectedBrand.value, selectedModel.value))
watch(selectedBrand, () => { selectedModel.value = ''; selectedVersion.value = '' })
watch(selectedModel, () => { selectedVersion.value = '' })
const isVehicleFilterActive = computed(() => !!(selectedBrand.value || selectedModel.value || selectedVersion.value))

// Filtro integral de repuestos
const filteredParts = computed(() =>
  db.value.parts.filter((p) => {
    if (p.archived) return false
    const matchesSearch = matches(debouncedSearch.value, p.name, p.brand, p.oem)
    const matchesStock = !lowStockOnly.value || p.stock <= p.min
    const matchesVehicle =
      !isVehicleFilterActive.value ||
      !p.compatible.length ||
      p.compatible.some(entry => matchesPartCompatibility(entry, selectedBrand.value, selectedModel.value, selectedVersion.value))

    return matchesSearch && matchesStock && matchesVehicle
  })
)

function resetVehicleFilter() {
  selectedBrand.value = ''
  selectedModel.value = ''
  selectedVersion.value = ''
}

function handleCreated(p: Part) {
  formModalOpen.value = false
  notify(`Repuesto "${p.name}" agregado al inventario.`)
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          TALLER CENTRAL / REPUESTOS
        </div>
        <h1>La pieza que necesitás.</h1>
      </div>
      <button class="button primary" @click="editingPart = null; formModalOpen = true">
        <Plus :size="17" />Nuevo repuesto
      </button>
    </section>

    <section class="panel inventory-filters" aria-label="Filtros de repuestos">
      <div class="inventory-filter-row">
        <div class="filter-field">
          <label class="filter-label" for="inventory-search">Buscar repuesto</label>
          <label class="search-box">
            <Search :size="17" />
            <input
              id="inventory-search"
              v-model="search"
              placeholder="Nombre o código OEM…"
              aria-label="Buscar repuestos"
            />
          </label>
        </div>
        <!-- Selector Marca -->
        <div class="filter-field">
          <label class="filter-label" for="inventory-brand">Marca</label>
          <div class="filter-control">
          <select id="inventory-brand" v-model="selectedBrand" class="select-field">
            <option value="" disabled>Seleccionar marca</option>
            <option v-for="b in availableBrands" :key="b" :value="b">{{ b }}</option>
          </select>
          <button v-if="selectedBrand" type="button" class="icon-button filter-clear" aria-label="Limpiar marca" @click="resetVehicleFilter"><X :size="14" /></button>
          </div>
        </div>

        <!-- Selector Modelo -->
        <div class="filter-field">
          <label class="filter-label" for="inventory-model">Modelo</label>
          <div class="filter-control">
          <select
            id="inventory-model"
            v-model="selectedModel"
            class="select-field"
            :disabled="!selectedBrand || !availableModels.length"
          >
            <option value="" disabled>Seleccionar modelo</option>
            <option v-for="m in availableModels" :key="m" :value="m">{{ m }}</option>
          </select>
          <button v-if="selectedModel" type="button" class="icon-button filter-clear" aria-label="Limpiar modelo" @click="selectedModel = ''"><X :size="14" /></button>
          </div>
        </div>

        <div class="filter-field">
          <label class="filter-label" for="inventory-version">Versión</label>
          <div class="filter-control">
            <select id="inventory-version" v-model="selectedVersion" class="select-field" :disabled="!selectedModel || !availableVersions.length">
              <option value="">Todas las versiones</option>
              <option v-for="option in availableVersions" :key="option" :value="option">{{ option }}</option>
            </select>
          </div>
        </div>

        <div class="segmented status-filters" role="group" aria-label="Filtrar repuestos">
          <button
            type="button"
            :class="{ selected: lowStockOnly }"
            :aria-pressed="lowStockOnly"
            @click="lowStockOnly = !lowStockOnly"
          >
            Stock bajo
            <span>{{ lowStock.length }}</span>
          </button>
        </div>

      </div>
    </section>

    <!-- Barra de estado cuando hay un filtro aplicado -->
    <div v-if="isVehicleFilterActive" class="active-filter-alert">
      <span class="active-tag">
        <CarFront :size="14" />
        Filtrando repuestos compatibles con:
        <strong>{{ selectedBrand || 'Cualquier marca' }}</strong>
        <template v-if="selectedModel"> · <strong>{{ selectedModel }}</strong></template>
        <template v-if="selectedVersion"> · <strong>{{ selectedVersion }}</strong></template>
      </span>
      <span class="active-count">
        {{ filteredParts.length }} {{ filteredParts.length === 1 ? 'pieza compatible' : 'piezas compatibles' }}
      </span>
    </div>

    <div class="inventory-view-switch">
        <div class="segmented">
          <button
            type="button"
            :class="{ selected: viewMode === 'table' }"
            :aria-pressed="viewMode === 'table'"
            @click="viewMode = 'table'"
            title="Ver en formato tabla"
          >
            <Table :size="14" /> Tabla
          </button>
          <button
            type="button"
            :class="{ selected: viewMode === 'cards' }"
            :aria-pressed="viewMode === 'cards'"
            @click="viewMode = 'cards'"
            title="Ver en formato tarjetas"
          >
            <LayoutGrid :size="14" /> Tarjetas
          </button>
        </div>
    </div>

    <!-- VISTA 1: TABLA (TABLE) -->
    <section v-if="viewMode === 'table' && filteredParts.length" class="panel table-scroll">
      <table>
        <thead>
          <tr>
            <th>REPUESTO</th>
            <th>MARCA</th>
            <th>CÓDIGO OEM</th>
            <th>STOCK</th>
            <th>PRECIO</th>
            <th>ACCIONES</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in filteredParts" :key="p.id">
            <td>
              <strong>{{ p.name }}</strong>
            </td>
            <td>
              <span>{{ p.brand }}</span>
            </td>
            <td>{{ p.oem }}</td>
            <td>
              <span :class="['badge', p.stock <= p.min ? 'amber' : 'green']">
                {{ p.stock }} un. (mín {{ p.min }})
              </span>
            </td>
            <td>
              <strong>{{ money(p.price) }}</strong>
            </td>
            <td><div class="part-actions"><button type="button" class="button small" @click="editPart(p)"><Pencil :size="14" /> Editar</button><button type="button" class="button small" @click="compatibilityPart = p"><CarFront :size="14" /> Compatibilidad</button><button type="button" class="button small" @click="deletingPart = p"><Trash2 :size="14" /> Eliminar</button></div></td>
          </tr>
        </tbody>
      </table>
    </section>

    <!-- VISTA 2: TARJETAS (CARDS) -->
    <div v-else-if="viewMode === 'cards' && filteredParts.length" class="parts-grid">
      <article
        v-for="p in filteredParts"
        :key="p.id"
        class="panel part-card"
      >
        <div class="part-card-header">
          <span class="part-brand-tag">{{ p.brand }}</span>
          <span :class="['badge', p.stock <= p.min ? 'amber' : 'green']">
            {{ p.stock }} un. (mín {{ p.min }})
          </span>
        </div>
        <h3 class="part-name">{{ p.name }}</h3>
        <p class="part-oem">OEM: <code>{{ p.oem }}</code></p>
        <div v-if="p.compatible && p.compatible.length" class="part-compat-info">
          <small class="muted">Compatible con {{ p.compatible.length }} combinaciones de marca, modelo y versión</small>
        </div>
        <footer class="part-card-footer">
          <span class="part-price-label">Precio</span>
          <strong class="part-price-val">{{ money(p.price) }}</strong>
        </footer>
        <div class="part-actions"><button type="button" class="button small" @click="editPart(p)"><Pencil :size="14" /> Editar</button><button type="button" class="button small" @click="compatibilityPart = p"><CarFront :size="14" /> Compatibilidad</button><button type="button" class="button small" @click="deletingPart = p"><Trash2 :size="14" /> Eliminar</button></div>
      </article>
    </div>

    <div v-if="!filteredParts.length" class="empty-state">
      <Package :size="38" class="muted" />
      <h3>No encontramos repuestos</h3>
      <p>No hay repuestos que coincidan con la búsqueda o filtro aplicado.</p>
    </div>

    <!-- Modal -->
    <InventarioModalCompatibilidad
      v-if="compatibilityPart"
      :part="compatibilityPart"
      @close="compatibilityPart = null"
      @updated="handleCompatibilityUpdated"
    />
    <InventarioModalRepuesto
      :open="formModalOpen"
      :part="editingPart"
      @close="formModalOpen = false"
      @created="handleCreated"
      @updated="handleUpdated"
      @delete="deletingPart = $event"
    />
    <CommonModalDialog v-if="deletingPart" class="dialog" aria-label="Eliminar repuesto" @close="deletingPart = null">
      <div class="dialog-header"><h2>Eliminar repuesto</h2><button class="icon-button" aria-label="Cerrar" @click="deletingPart = null"><X :size="18" /></button></div>
      <div class="detail-body"><p>¿Querés eliminar <strong>{{ deletingPart.name }}</strong> del inventario?</p><p class="muted">Los repuestos registrados en órdenes, presupuestos y facturas se conservan.</p></div>
      <footer class="modal-footer"><button class="button" @click="deletingPart = null">Cancelar</button><button class="button primary" @click="deletePart"><Trash2 :size="16" /> Eliminar repuesto</button></footer>
    </CommonModalDialog>
  </div>
</template>

<style scoped>
.part-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.inventory-filters { padding: 18px; margin-bottom: 20px; }
.inventory-filter-row {
  display: grid;
  grid-template-columns: minmax(160px, 1.4fr) repeat(3, minmax(110px, 1fr)) auto;
  align-items: end;
  gap: 10px;
}
.inventory-filter-row .search-box { min-width: 0; height: 38px; min-height: 38px; flex-shrink: 0; width: 100%; }
.inventory-filter-row .search-box input { min-width: 0; width: 100%; padding: 0 6px; }
.inventory-filter-row .segmented { height: 38px; white-space: nowrap; }
.inventory-view-switch { display: flex; justify-content: flex-end; margin-bottom: 14px; }
@media (max-width: 760px) {
  .inventory-view-switch { display: none; }
}
.inventory-filter-row .segmented button { display: inline-flex; align-items: center; gap: 5px; }
@media (max-width: 1000px) {
  .inventory-filter-row { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .inventory-filter-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .inventory-filter-row > .filter-field:first-child { grid-column: 1 / -1; }
}

.filter-field {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.filter-control { display: flex; align-items: center; gap: 4px; min-width: 0; }
.filter-control > .select-field { flex: 1; width: 100%; min-width: 0; }
.filter-clear { flex-shrink: 0; width: 28px; height: 38px; }

.filter-label {
  font-size: 10px;
  font-weight: 750;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.select-field {
  height: 38px;
  min-width: 140px;
  font-size: 12px;
  font-weight: 600;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid #cbd5e1;
  background-color: #ffffff;
  color: #0f172a;
  cursor: pointer;
  transition: all 0.15s ease;
}

.select-field:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
}

.select-field:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background-color: #f1f5f9;
}

.active-filter-alert {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  margin-bottom: 20px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  font-size: 12px;
  color: #1e40af;
}

.active-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.active-count {
  font-weight: 700;
  background: #dbeafe;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 11px;
}

/* Dark mode adjustments */
:global(html.dark .select-field) {
  background-color: #1e293b;
  border-color: #475569;
  color: #f8fafc;
}

:global(html.dark .select-field:disabled) {
  background-color: #0f172a;
}

:global(html.dark .active-filter-alert) {
  background: rgba(37, 99, 235, 0.15);
  border-color: rgba(37, 99, 235, 0.3);
  color: #93c5fd;
}

:global(html.dark .active-count) {
  background: rgba(37, 99, 235, 0.25);
  color: #bfdbfe;
}

.toolbar-left-group {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.parts-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}

@media (max-width: 900px) {
  .parts-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 600px) {
  .parts-grid {
    grid-template-columns: 1fr;
  }
}

.part-card {
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 10px;
  border-radius: 12px;
}

.part-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.part-brand-tag {
  font-size: 11px;
  font-weight: 700;
  color: #2563eb;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

:global(html.dark .part-brand-tag) {
  color: #60a5fa;
}

.part-name {
  margin: 0;
  font-size: 14.5px;
  font-weight: 600;
  line-height: 1.35;
}

.part-oem {
  margin: 0;
  font-size: 11.5px;
  color: #64748b;
}

.part-oem code {
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
}

:global(html.dark .part-oem code) {
  background: rgba(255, 255, 255, 0.08);
  color: #e2e8f0;
}

.part-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid #f1f5f9;
  padding-top: 12px;
  margin-top: 4px;
}

:global(html.dark .part-card-footer) {
  border-top-color: rgba(255, 255, 255, 0.08);
}

.part-price-label {
  font-size: 10.5px;
  color: #64748b;
}

.part-price-val {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

:global(html.dark .part-price-val) {
  color: #f8fafc;
}
</style>
