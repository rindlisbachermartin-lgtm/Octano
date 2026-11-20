<script setup lang="ts">
import { Plus, Search, Wrench, ArrowUpRight, LayoutGrid, Table } from '@lucide/vue'
import { authError } from '~/composables/useAuthApi'
import { estadosOrden, fechaOrden, type EstadoOrden, type OrdenApi, type PaginaOrdenes } from '~/utils/ordenApi'

const { authorization, user } = useAuthApi()
const { statusClass } = useHelpers()
const mecanico = computed(() => user.value?.rol === 'MECANICO')
const search = ref('')
const consulta = useDebouncedValue(search)
const route = useRoute()
const estadoInicial = Object.entries(estadosOrden).find(([key, label]) => route.query.estado === key || route.query.estado === label)?.[0]
const estado = ref<EstadoOrden | ''>((estadoInicial as EstadoOrden) || 'EN_PROCESO')
const desde = ref('')
const hasta = ref('')
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

watch([consulta, estado, desde, hasta], () => { pagina.value = 0 })
watch([authorization, consulta, estado, desde, hasta, pagina, revision], async (_, __, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  const headers = authorization.value
  ordenes.value = []
  total.value = 0
  paginas.value = 0
  error.value = ''
  cargando.value = !!headers
  if (!headers) return
  if (desde.value && hasta.value && desde.value > hasta.value) {
    error.value = 'La fecha desde no puede superar la fecha hasta.'
    cargando.value = false
    return
  }
  try {
    const result = await $fetch<PaginaOrdenes<OrdenApi>>('/api/ordenes-trabajo', {
      headers, signal: controller.signal,
      query: { q: consulta.value.trim().slice(0, 250), estado: estado.value || undefined, desde: desde.value || undefined, hasta: hasta.value || undefined, pagina: pagina.value, tamano: 20 },
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
  creando.value = false
  editando.value = null
  seleccionada.value = orden.id
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
      <div><div class="eyebrow">ÓRDENES DE TRABAJO</div><h1>{{ mecanico ? 'Mis órdenes de trabajo' : 'Del ingreso a la entrega.' }}</h1><p class="muted">{{ mecanico ? 'Trabajos asignados a tu cuenta.' : 'Gestioná los trabajos y materiales de tu taller.' }}</p></div>
      <button v-if="!mecanico" class="button primary" @click="creando = true"><Plus :size="17" /> Nueva orden</button>
    </section>
    <div class="list-toolbar">
      <label class="search-box"><Search :size="17" /><input v-model="search" maxlength="250" aria-label="Buscar órdenes" placeholder="Patente, cliente, trabajo o mecánico…" /></label>
      <label>Estado<select v-model="estado"><option value="">Todos</option><option v-for="(label, key) in estadosOrden" :key="key" :value="key">{{ label }}</option></select></label>
      <label>Desde<input v-model="desde" type="date" aria-label="Órdenes creadas desde" /></label>
      <label>Hasta<input v-model="hasta" type="date" aria-label="Órdenes creadas hasta" /></label>
      <div class="segmented list-view-switch">
        <button :aria-pressed="viewMode === 'cards'" :class="{ selected: viewMode === 'cards' }" title="Ver tarjetas" @click="viewMode = 'cards'"><LayoutGrid :size="16" /></button>
        <button :aria-pressed="viewMode === 'table'" :class="{ selected: viewMode === 'table' }" title="Ver tabla" @click="viewMode = 'table'"><Table :size="16" /></button>
      </div>
    </div>
    <p v-if="error" role="alert">{{ error }} <button class="button small" @click="revision++">Reintentar</button></p>
    <p v-else-if="cargando" role="status">Cargando órdenes…</p>
    <template v-else>
      <div v-if="viewMode === 'cards'" class="orders-grid order-grid">
        <button v-for="o in ordenes" :key="o.id" class="order-card" @click="seleccionada = o.id">
          <div class="section-heading"><span class="plate">{{ o.patente }}</span><span :class="['badge', statusClass(estadosOrden[o.estado])]">{{ estadosOrden[o.estado] }}</span></div>
          <h2>{{ o.marca }} {{ o.modelo }}</h2><p>{{ o.clienteNombre }}</p>
          <div class="service-preview"><strong>{{ o.descripcion }}</strong><small>OT #{{ o.id }} · {{ fechaOrden(o.fechaCreacion) }}</small></div>
          <footer><span>{{ o.mecanicoNombre || 'Sin mecánico asignado' }}</span><span>Ver orden <ArrowUpRight :size="16" /></span></footer>
        </button>
      </div>
      <section v-else-if="ordenes.length" class="panel table-scroll">
        <table><thead><tr><th>ORDEN</th><th>CLIENTE</th><th>VEHÍCULO</th><th>PATENTE</th><th>TRABAJO</th><th>ESTADO</th><th>MECÁNICO</th><th>ACCIONES</th></tr></thead>
          <tbody><tr v-for="o in ordenes" :key="o.id"><td>#{{ o.id }}</td><td>{{ o.clienteNombre }}</td><td>{{ o.marca }} {{ o.modelo }}</td><td><span class="plate">{{ o.patente }}</span></td><td>{{ o.descripcion }}</td><td><span :class="['badge', statusClass(estadosOrden[o.estado])]">{{ estadosOrden[o.estado] }}</span></td><td>{{ o.mecanicoNombre || 'Sin asignar' }}</td><td><button class="text-button" @click="seleccionada = o.id">Ver orden <ArrowUpRight :size="15" /></button></td></tr></tbody>
        </table>
      </section>
      <div v-if="!ordenes.length" class="empty-state"><Wrench :size="38" /><h3>No encontramos órdenes</h3><p>Probá con otra búsqueda o cambiá los filtros.</p></div>
      <div v-if="total" class="list-toolbar" aria-label="Paginación de órdenes">
        <span>{{ total }} órdenes · Página {{ pagina + 1 }} de {{ paginas }}</span>
        <button class="button small" :disabled="pagina === 0" @click="pagina--">Anterior</button>
        <button class="button small" :disabled="pagina + 1 >= paginas" @click="pagina++">Siguiente</button>
      </div>
    </template>
    <OrdenesFormularioApi v-if="creando || editando" :orden="editando" @close="creando = false; editando = null" @saved="guardada" />
    <OrdenesDetalleApi v-if="seleccionada !== null" :id="seleccionada" @close="seleccionada = null" @edit="editar" @updated="revision++" />
  </div>
</template>

<style scoped>
.list-toolbar { flex-wrap: wrap; align-items: end; }
.search-box { flex: 1 1 250px; }
.list-toolbar > label:not(.search-box) { display: flex; flex-direction: column; gap: 4px; }
.order-card h2, .order-card strong, td { overflow-wrap: anywhere; }
</style>
