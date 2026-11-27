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
} from '@lucide/vue'
import type { Part } from '~/types'
import type { PaginaRepuestos, RepuestoApi } from '~/utils/repuestoApi'

const { money, matches } = useHelpers()
const { notify } = useWorkshopToast()
const auth = useAuthApi()

const puedeEditar = computed(() => auth.puedeAdministrar.value)
const catalogo = useCatalogoVehiculos()
const { marcaId, modeloId, versionId, marcas, modelos, versiones } = catalogo
const listado = ref<PaginaRepuestos<RepuestoApi>>()
const cargando = ref(false)
const errorLista = ref('')
const errorEstado = ref('')
const guardandoEstado = ref(false)
const movimientoPart = ref<Part | null>(null)
let consulta: AbortController | undefined

const search = ref('')
const debouncedSearch = useDebouncedValue(search)
const lowStockOnly = ref(useRoute().query.stockBajo === '1')
const viewMode = useListView('inventario', 'table', ['table', 'cards'] as const)
const formModalOpen = ref(false)
const editingPart = ref<Part | null>(null)
const deletingPart = ref<Part | null>(null)
const compatibilityPart = ref<Part | null>(null)

function editPart(part: Part) {
  editingPart.value = part
  formModalOpen.value = true
}
function handleUpdated(part: Part) {
  formModalOpen.value = false
  editingPart.value = null
  notify(`Repuesto "${part.name}" actualizado.`)
  void cargarRepuestos(listado.value?.pagina)
}
async function deletePart() {
  const part = deletingPart.value
  if (!part || guardandoEstado.value) return

  const headers = auth.authorization.value
  if (!headers || !puedeEditar.value) return
  guardandoEstado.value = true
  errorEstado.value = ''
  try {
    await $fetch(`/api/repuestos/${part.id}/estado`, { method: 'PATCH', headers, body: { activo: !!part.archived } })
  } catch (err) {
    errorEstado.value = authError(err, 'No pudimos cambiar el estado del repuesto.')
    return
  } finally {
    guardandoEstado.value = false
  }

  deletingPart.value = null
  formModalOpen.value = false
  editingPart.value = null
  notify(part.archived ? 'Repuesto reactivado.' : 'Repuesto archivado. Su historial se conserva.')
  await cargarRepuestos(listado.value?.pagina)
}

function pedirCambioEstado(part: Part) {
  errorEstado.value = ''
  deletingPart.value = part
}

const archivados = ref(false)

async function cargarRepuestos(pagina = 0) {
  const headers = auth.authorization.value
  if (!headers) return
  consulta?.abort()
  const actual = new AbortController()
  consulta = actual
  cargando.value = true
  errorLista.value = ''
  try {
    const resultado = await $fetch<PaginaRepuestos<RepuestoApi>>('/api/repuestos', {
      headers, signal: actual.signal,
      query: { q: debouncedSearch.value.trim(), activo: !archivados.value, stockBajo: lowStockOnly.value || undefined,
        marcaId: marcaId.value || undefined, modeloId: modeloId.value || undefined, versionId: versionId.value || undefined, pagina },
    })
    if (actual.signal.aborted) return
    if (pagina > 0 && pagina >= resultado.totalPaginas) {
      await cargarRepuestos(Math.max(0, resultado.totalPaginas - 1))
      return
    }
    listado.value = resultado
  } catch (err) {
    if (!actual.signal.aborted) errorLista.value = authError(err, 'No pudimos cargar los repuestos.')
  } finally {
    if (consulta === actual) cargando.value = false
  }
}

watch(auth.authorization, (headers) => {
  consulta?.abort()
  listado.value = undefined
  errorLista.value = ''
  if (!headers) return
  void catalogo.abrir()
  void cargarRepuestos()
}, { immediate: true })
watch([debouncedSearch, lowStockOnly, marcaId, modeloId, versionId, archivados], () => cargarRepuestos())
onBeforeUnmount(() => consulta?.abort())

// Filtro integral de repuestos
const filteredParts = computed(() =>
  (listado.value?.contenido ?? []).map(adaptarRepuesto)
)

function handleCreated(p: Part) {
  formModalOpen.value = false
  notify(`Repuesto "${p.name}" agregado al inventario.`)
  archivados.value = false
  void cargarRepuestos()
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          {{ auth.workshopName.value || 'TU TALLER' }} / REPUESTOS
        </div>
        <h1>La pieza que necesitás.</h1>
      </div>
      <button v-if="puedeEditar" class="button primary" @click="editingPart = null; formModalOpen = true">
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
              maxlength="250"
            />
          </label>
        </div>

          <div class="filter-field">
            <label class="filter-label" for="api-marca">Marca</label>
            <select id="api-marca" v-model="marcaId" class="select-field" :disabled="catalogo.cargando.marcas">
              <option value="">Todas las marcas</option>
              <option v-for="marca in marcas" :key="marca.id" :value="marca.id">{{ marca.nombre }}</option>
            </select>
          </div>
          <div class="filter-field">
            <label class="filter-label" for="api-modelo">Modelo</label>
            <select id="api-modelo" v-model="modeloId" class="select-field" :disabled="!marcaId || catalogo.cargando.modelos">
              <option value="">Seleccionar modelo</option>
              <option v-for="modelo in modelos" :key="modelo.id" :value="modelo.id">{{ modelo.nombre }}</option>
            </select>
          </div>
          <div class="filter-field">
            <label class="filter-label" for="api-version">Versión</label>
            <select id="api-version" v-model="versionId" class="select-field" :disabled="!modeloId || catalogo.cargando.versiones">
              <option value="">Todas las versiones</option>
              <option v-for="version in versiones" :key="version.id" :value="version.id">{{ version.nombre }}</option>
            </select>
          </div>

        <div class="segmented status-filters" role="group" aria-label="Filtrar repuestos">
          <button
            type="button"
            :class="{ selected: lowStockOnly }"
            :aria-pressed="lowStockOnly"
            @click="lowStockOnly = !lowStockOnly"
          >
            Stock bajo

          </button>
          <button  type="button" :class="{ selected: archivados }" :aria-pressed="archivados" @click="archivados = !archivados">Archivados</button>
        </div>

      </div>
      <template v-for="(aviso, nivel) in catalogo.errores" :key="nivel">
        <p v-if="aviso" role="alert">{{ aviso }} <button type="button" class="button" @click="catalogo.cargar(nivel)">Reintentar</button></p>
      </template>
    </section>

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
    <p v-if="cargando" role="status">Cargando repuestos…</p>
    <p v-else-if="errorLista" role="alert">{{ errorLista }} <button type="button" class="button" @click="cargarRepuestos()">Reintentar</button></p>
    <section v-else-if="viewMode === 'table' && filteredParts.length" class="panel table-scroll">
      <table>
        <thead>
          <tr>
            <th>REPUESTO</th>
            <th>MARCA</th>
            <th>CÓDIGO OEM</th>
            <th>STOCK</th>
            <th>PRECIO</th>
            <th class="actions-column">ACCIONES</th>
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
            <td class="actions-column"><div class="part-actions">
              <button v-if="puedeEditar" type="button" class="button small" @click="editPart(p)"><Pencil :size="14" /> Editar</button>
              <button type="button" class="button small" @click="compatibilityPart = p"><CarFront :size="14" /> Compatibilidad</button>
              <button  type="button" class="button small" @click="movimientoPart = p">Movimientos</button>
            </div></td>
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
        <div class="part-actions">
          <button v-if="puedeEditar" type="button" class="button small" @click="editPart(p)"><Pencil :size="14" /> Editar</button>
          <button type="button" class="button small" @click="compatibilityPart = p"><CarFront :size="14" /> Compatibilidad</button>
          <button  type="button" class="button small" @click="movimientoPart = p">Movimientos</button>
        </div>
      </article>
    </div>

    <div v-if="!cargando && !errorLista && !filteredParts.length" class="empty-state">
      <Package :size="38" class="muted" />
      <h3>No encontramos repuestos</h3>
      <p>No hay repuestos que coincidan con la búsqueda o filtro aplicado.</p>
    </div>
    <div v-if="listado && !errorLista && listado.totalPaginas > 1" class="toolbar-left-group">
      <button type="button" class="button" :disabled="cargando || listado.pagina === 0" @click="cargarRepuestos(listado.pagina - 1)">Anterior</button>
      <button type="button" class="button" :disabled="cargando || listado.pagina + 1 >= listado.totalPaginas" @click="cargarRepuestos(listado.pagina + 1)">Siguiente</button>
    </div>

    <!-- Modal -->
    <InventarioModalCompatibilidadApi v-if="compatibilityPart" :part="compatibilityPart"
      :editable="puedeEditar && !compatibilityPart.archived" @close="compatibilityPart = null" @updated="cargarRepuestos(listado?.pagina)" />

    <InventarioModalMovimientos v-if="movimientoPart" :part="movimientoPart"
      :editable="puedeEditar && !movimientoPart.archived" @close="movimientoPart = null" @updated="cargarRepuestos(listado?.pagina)" />
    <InventarioModalRepuesto
      :open="formModalOpen"
      :part="editingPart"
      @close="formModalOpen = false"
      @created="handleCreated"
      @updated="handleUpdated"
      @delete="pedirCambioEstado"
    />
    <CommonModalDialog v-if="deletingPart" class="dialog" @close="!guardandoEstado && (deletingPart = null)">
      <div class="dialog-header"><h2>{{ deletingPart.archived ? 'Reactivar repuesto' : 'Archivar repuesto' }}</h2><button class="icon-button" aria-label="Cerrar" :disabled="guardandoEstado" @click="deletingPart = null"><X :size="18" /></button></div>
      <div class="detail-body"><p>¿Querés {{ deletingPart.archived ? 'reactivar' : 'archivar' }} <strong>{{ deletingPart.name }}</strong>?</p><p v-if="errorEstado" role="alert">{{ errorEstado }}</p></div>
      <footer class="modal-footer"><button class="button" :disabled="guardandoEstado" @click="deletingPart = null">Cancelar</button><button class="button primary" :disabled="guardandoEstado" @click="deletePart">{{ deletingPart.archived ? 'Reactivar' : 'Archivar' }}</button></footer>
    </CommonModalDialog>
  </div>
</template>

<style scoped>
.part-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.actions-column { width: 1%; white-space: nowrap; }
.actions-column .part-actions { justify-content: center; flex-wrap: nowrap; }
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
  width: 100%;
  min-width: 0;
  font-size: 12px;
  font-weight: 600;
  padding: 0 12px;
  text-align: start;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  border-radius: 8px;
  border: 1px solid #cbd5e1;
  background-color: #ffffff;
  color: #0f172a;
  cursor: pointer;
  transition: all 0.15s ease;
}

@supports (appearance: base-select) and selector(select::picker-icon) {
  .select-field { align-items: center; gap: 6px; }
  .select-field::picker-icon { margin-inline-start: auto; }
  .select-field option {
    padding: 6px 10px;
    white-space: normal;
    overflow-wrap: anywhere;
  }
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

/* Dark mode adjustments */
:global(html.dark .select-field) {
  background-color: #1e293b;
  border-color: #475569;
  color: #f8fafc;
}

:global(html.dark .select-field:disabled) {
  background-color: #0f172a;
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
