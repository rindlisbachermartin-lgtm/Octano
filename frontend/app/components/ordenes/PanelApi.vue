<script setup lang="ts">
import { Plus, Search, Wrench, ArrowUpRight, LayoutGrid, Table } from '@lucide/vue'
import { authError } from '~/composables/useAuthApi'
import { estadosOrden, fechaOrden, type EstadoOrden, type OrdenApi, type PaginaOrdenes } from '~/utils/ordenApi'
import { vehicleLabel } from '~/utils/vehicleDisplay'
import { paginasPresupuestos } from '~/utils/presupuestoApi'
import IconBidonAceite from '~/components/icons/IconBidonAceite.vue'
import IconCorreaDistribucion from '~/components/icons/IconCorreaDistribucion.vue'

const { authorization, user } = useAuthApi()
const { statusClass } = useHelpers()
const { today } = useWorkshopDay()
const mecanico = computed(() => user.value?.rol === 'MECANICO')
const search = ref('')
const consulta = useDebouncedValue(search)
const route = useRoute()
const estadoInicial = Object.entries(estadosOrden).find(([key, label]) => route.query.estado === key || route.query.estado === label)?.[0]
const estado = ref<EstadoOrden | ''>((estadoInicial as EstadoOrden) || 'EN_PROCESO')
const pagina = ref(0)
const revision = ref(0)
const ordenes = ref<OrdenApi[]>([])
const total = ref(0)
const paginas = ref(0)
const cargando = ref(false)
const error = ref('')
const creando = ref(false)
const editando = ref<OrdenApi | null>(null)
const seleccionada = ref<number | null>(null)
const viewMode = useListView('ordenes', 'cards', ['table', 'cards'] as const)
const monthlyCounts = ref<{ total: number; services: number; timing: number; other: number } | null>(null)
const activeStatusCounts = ref<Record<string, number>>({})
const errorResumen = ref('')

watch([authorization, () => today.value.slice(0, 7), revision], async ([headers, mes], _, onCleanup) => {
  const controller = new AbortController()
  const { signal } = controller
  onCleanup(() => controller.abort())
  monthlyCounts.value = null
  activeStatusCounts.value = {}
  errorResumen.value = ''
  if (!headers) return
  const [year, month] = mes.split('-').map(Number)
  const hastaMes = `${mes}-${new Date(Date.UTC(year, month, 0)).getUTCDate()}`
  const results = await Promise.allSettled([
    (async () => {
      const rows = await paginasPresupuestos<OrdenApi>('/api/ordenes-trabajo', headers, signal, { desde: `${mes}-01`, hasta: hastaMes })
      if (!signal.aborted) monthlyCounts.value = {
        total: rows.length,
        services: rows.filter(o => o.tiposTrabajo.includes('SERVICE')).length,
        timing: rows.filter(o => o.tiposTrabajo.includes('DISTRIBUCION')).length,
        other: rows.filter(o => o.tiposTrabajo.includes('OTRO') || !o.tiposTrabajo.length).length,
      }
    })(),
    (async () => {
      const counts = await Promise.all(['PENDIENTE_INGRESO', 'EN_ESPERA', 'EN_PROCESO'].map(async estado => {
        const page = await $fetch<PaginaOrdenes<OrdenApi>>('/api/ordenes-trabajo', { headers, signal, query: { estado, pagina: 0, tamano: 1 } })
        return [estado, page.totalElementos] as const
      }))
      if (!signal.aborted) activeStatusCounts.value = Object.fromEntries(counts)
    })(),
  ])
  if (!signal.aborted && results.some(r => r.status === 'rejected')) errorResumen.value = 'No se pudieron cargar todos los contadores de órdenes.'
}, { immediate: true })

watch([consulta, estado], () => { pagina.value = 0 })
watch([authorization, consulta, estado, pagina, revision], async (_, __, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  const headers = authorization.value
  ordenes.value = []
  total.value = 0
  paginas.value = 0
  error.value = ''
  cargando.value = !!headers
  if (!headers) return
  try {
    const result = await $fetch<PaginaOrdenes<OrdenApi>>('/api/ordenes-trabajo', {
      headers, signal: controller.signal,
      query: { q: consulta.value.trim().slice(0, 250), estado: estado.value || undefined, pagina: pagina.value, tamano: 20 },
    })
    if (controller.signal.aborted) return
    if (pagina.value > 0 && !result.contenido.length && result.totalElementos > 0) { pagina.value--; return }
    ordenes.value = result.contenido
    total.value = result.totalElementos
    paginas.value = result.totalPaginas
  } catch (err) {
    if (!controller.signal.aborted) error.value = authError(err, 'No se pudieron cargar las órdenes de trabajo.')
  } finally {
    if (!controller.signal.aborted) cargando.value = false
  }
}, { immediate: true })

function guardada(orden: OrdenApi) {
  if (creando.value) { search.value = ''; pagina.value = 0 }
  creando.value = false
  editando.value = null
  seleccionada.value = null
  estado.value = orden.estado
  revision.value++
}

function editar(orden: OrdenApi) {
  seleccionada.value = null
  editando.value = orden
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div><div class="eyebrow">ÓRDENES DE TRABAJO</div><h1>{{ mecanico ? 'Mis órdenes de trabajo' : 'Del ingreso a la entrega.' }}</h1></div>
      <button v-if="!mecanico" class="button primary" @click="creando = true"><Plus :size="17" /> Nueva orden</button>
    </section>
    <section class="stats-grid summary-stats monthly-summary" aria-label="Órdenes de trabajo de este mes">
      <article class="stat-card summary-logo-card">
        <div><span>Órdenes de trabajo</span><Wrench :size="19" /></div>
        <strong>{{ monthlyCounts?.total ?? '—' }}</strong>
        <small>Este mes</small>
        <CommonOctanoLogo class="summary-logo" />
      </article>
      <article class="stat-card">
        <div><span>Services</span><IconBidonAceite :size="19" /></div>
        <strong>{{ monthlyCounts?.services ?? '—' }}</strong>
        <small>Este mes</small>
      </article>
      <article class="stat-card">
        <div><span>Distribuciones</span><IconCorreaDistribucion :size="19" /></div>
        <strong>{{ monthlyCounts?.timing ?? '—' }}</strong>
        <small>Este mes</small>
      </article>
      <article class="stat-card">
        <div><span>Otros trabajos</span><Wrench :size="19" /></div>
        <strong>{{ monthlyCounts?.other ?? '—' }}</strong>
        <small>Este mes</small>
      </article>
    </section>

    <p v-if="errorResumen" role="alert">{{ errorResumen }} <button class="button small" @click="revision++">Reintentar contadores</button></p>
    <div class="list-toolbar">
      <label class="search-box"><Search :size="17" /><input v-model="search" maxlength="250" aria-label="Buscar órdenes" placeholder="Buscar por patente, auto o cliente…" /></label>
      <div class="toolbar-left-group">
        <div class="segmented status-filters" role="group" aria-label="Estado de las órdenes">
          <button v-for="(label, key) in estadosOrden" :key="key" type="button" :class="{ selected: estado === key }" :aria-pressed="estado === key" @click="estado = key">{{ label }}<span v-if="activeStatusCounts[key] > 0" class="status-count">{{ activeStatusCounts[key] }}</span></button>
        </div>
        <div class="segmented list-view-switch">
          <button type="button" :aria-pressed="viewMode === 'cards'" :class="{ selected: viewMode === 'cards' }" title="Ver en formato tarjetas" @click="viewMode = 'cards'"><LayoutGrid :size="14" /> Tarjetas</button>
          <button type="button" :aria-pressed="viewMode === 'table'" :class="{ selected: viewMode === 'table' }" title="Ver en formato tabla" @click="viewMode = 'table'"><Table :size="14" /> Tabla</button>
        </div>
      </div>
    </div>
    <p v-if="error" role="alert">{{ error }} <button class="button small" @click="revision++">Reintentar</button></p>
    <p v-else-if="cargando" role="status">Cargando órdenes…</p>
    <template v-else>
      <div v-if="viewMode === 'cards'" class="orders-grid order-grid">
        <button v-for="o in ordenes" :key="o.id" type="button" class="order-card" @click="seleccionada = o.id">
          <CommonBrandLogo :brand="o.marca" class="order-card-brand" />
          <div class="section-heading"><span class="plate">{{ o.patente }}</span><span :class="['badge', statusClass(estadosOrden[o.estado])]">{{ estadosOrden[o.estado] }}</span></div>
          <div class="order-vehicle-info"><h2>{{ vehicleLabel(o.marca, o.modelo, o.version) }}</h2><p>{{ o.clienteNombre }}</p></div>
          <div class="service-preview"><strong>{{ o.descripcion }}</strong><small>OT #{{ o.id }} · {{ new Date(o.fechaCreacion).toLocaleDateString('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' }) }}</small></div>
          <div class="order-meta-info">
            <span v-if="o.kilometraje !== null" class="meta-tag">{{ o.kilometraje.toLocaleString('es-AR') }} km</span>
            <span v-if="o.indicaciones" class="meta-tag">Observaciones</span>
          </div>
          <footer><div class="mechanic"><span>{{ o.mecanicoNombre || 'Sin mecánico asignado' }}</span></div><span>Ver orden <ArrowUpRight :size="16" /></span></footer>
        </button>
      </div>
      <section v-else-if="ordenes.length" class="panel table-scroll">
        <table><thead><tr><th>ORDEN</th><th>CLIENTE</th><th>VEHÍCULO</th><th>PATENTE</th><th>TRABAJO</th><th>ESTADO</th><th>MECÁNICO</th><th>ACCIONES</th></tr></thead>
          <tbody><tr v-for="o in ordenes" :key="o.id"><td>#{{ o.id }}</td><td>{{ o.clienteNombre }}</td><td>{{ o.marca }} {{ o.modelo }}</td><td><span class="plate">{{ o.patente }}</span></td><td>{{ o.descripcion }}</td><td><span :class="['badge', statusClass(estadosOrden[o.estado])]">{{ estadosOrden[o.estado] }}</span></td><td>{{ o.mecanicoNombre || 'Sin asignar' }}</td><td><button class="text-button" @click="seleccionada = o.id">Ver orden <ArrowUpRight :size="15" /></button></td></tr></tbody>
        </table>
      </section>
      <div v-if="!ordenes.length" class="empty-state"><Wrench :size="38" /><h3>No encontramos órdenes</h3><p>Probá con otra búsqueda o cambiá los filtros.</p></div>
      <div v-if="paginas > 1" class="orders-pagination" aria-label="Paginación de órdenes">
        <button class="button small" :disabled="pagina === 0" @click="pagina--">Anterior</button>
        <button class="button small" :disabled="pagina + 1 >= paginas" @click="pagina++">Siguiente</button>
      </div>
    </template>
    <OrdenesFormularioApi v-if="creando || editando" :orden="editando" @close="creando = false; editando = null" @saved="guardada" />
    <OrdenesDetalleApi v-if="seleccionada !== null" :id="seleccionada" @close="seleccionada = null" @edit="editar" @updated="revision++" />
  </div>
</template>

<style scoped>



.order-card h2, .order-card strong, td { overflow-wrap: anywhere; }
.order-card { position: relative; overflow: hidden; }
:global(html.dark .app-shell .order-card) { background: #1c1c1e !important; }
.order-card-brand {
  position: absolute;
  top: 50%;
  right: 16px;
  width: 160px;
  height: 160px;
  transform: translate(50%, -50%);
  opacity: .45;
  color: #9ca3af;
  pointer-events: none;
}
.order-card > :not(.order-card-brand) { position: relative; z-index: 1; }
.order-card .plate, .order-card .service-preview { background: transparent !important; border: 0 !important; box-shadow: none; padding-inline: 0; }
.order-meta-info { display: flex; flex-wrap: wrap; gap: 6px; margin: 10px 0 12px; }
.meta-tag { display: inline-flex; align-items: center; gap: 4px; padding: 3px 8px; background: #f1f5f9; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 11px; color: #475569; font-weight: 550; }
:global(html.dark .order-meta-info .meta-tag) { background: rgba(255,255,255,.06); border-color: rgba(255,255,255,.1); color: #a1a1aa; }

.monthly-summary { grid-template-columns: repeat(4, minmax(0, 1fr)); max-width: 964px; }
@media (max-width: 700px) { .monthly-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); } }

.status-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: rgba(100, 116, 139, 0.16);
  color: inherit;
  font-weight: 700;
}

:global(html.dark .orders-empty-state h3) { color: #fff; }
.toolbar-left-group {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: nowrap;
}

.list-toolbar {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) auto;
  align-items: center;
  gap: 12px;
}
.list-toolbar .search-box { min-width: 0; width: 100%; height: 40px; }
.list-toolbar .search-box input { min-width: 0; width: 100%; }
.toolbar-left-group > .segmented { min-height: 40px; }
.toolbar-left-group > .segmented button { white-space: nowrap; }

@media (max-width: 1100px) {
  .list-toolbar { grid-template-columns: minmax(0, 1fr); }
  .toolbar-left-group { justify-content: space-between; flex-wrap: wrap; }
}


.orders-pagination { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
@media (max-width: 600px) { .status-filters { flex-wrap: wrap; } }
</style>
