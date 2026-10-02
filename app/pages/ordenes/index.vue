<script setup lang="ts">
import {
  Plus,
  Search,
  ArrowUpRight,
  LayoutGrid,
  Table,
} from 'lucide-vue-next'

const { db, vehicle, vehicleName, owner } = useDatabase()
const { statusClass, matches } = useHelpers()
const { notify } = useWorkshopToast()

const search = ref('')
const debouncedSearch = useDebouncedValue(search)
const filter = ref('Todos')
const viewMode = useListView('ordenes', 'cards', ['table', 'cards'] as const)
const newOrderOpen = ref(false)
const detailOrderId = ref<number | null>(null)
const detailOrderOpen = ref(false)

const filteredOrders = computed(() =>
  db.value.orders.filter(
    (o) =>
      (filter.value === 'Todos' || o.status === filter.value) &&
      matches(
        debouncedSearch.value,
        o.id,
        vehicleName(o.vehicle),
        vehicle(o.vehicle)?.plate,
        owner(o.vehicle)?.name,
        o.service
      )
  )
)

function openDetail(id: number) {
  detailOrderId.value = id
  detailOrderOpen.value = true
}

function handleCreated(id: number) {
  newOrderOpen.value = false
  notify('Orden de trabajo creada.')
  openDetail(id)
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          TALLER CENTRAL / ÓRDENES DE TRABAJO
        </div>
        <h1>Del ingreso a la entrega.</h1>
      </div>
      <button class="button primary" @click="newOrderOpen = true">
        <Plus :size="17" />Nueva orden
      </button>
    </section>

    <div class="list-toolbar">
      <label class="search-box">
        <Search :size="17" />
        <input
          v-model="search"
          placeholder="Buscar por patente, auto o cliente…"
          aria-label="Buscar órdenes"
        />
      </label>
      <div class="toolbar-left-group">
        <div class="filter-tabs">
          <button
            v-for="status in ['Todos', 'En espera', 'En proceso', 'Finalizado', 'Cancelado']"
            :key="status"
            :class="{ active: filter === status }"
            @click="filter = status"
          >
            {{ status }}
          </button>
        </div>

        <div class="segmented">
          <button
            type="button"
            :class="{ selected: viewMode === 'cards' }"
            :aria-pressed="viewMode === 'cards'"
            @click="viewMode = 'cards'"
            title="Ver en formato tarjetas"
          >
            <LayoutGrid :size="14" /> Tarjetas
          </button>
          <button
            type="button"
            :class="{ selected: viewMode === 'table' }"
            :aria-pressed="viewMode === 'table'"
            @click="viewMode = 'table'"
            title="Ver en formato tabla"
          >
            <Table :size="14" /> Tabla
          </button>
        </div>
      </div>


    </div>

    <!-- VISTA 1: TARJETAS (CARDS) -->
    <div v-if="viewMode === 'cards'" class="orders-grid order-grid">
      <button
        v-for="o in filteredOrders"
        :key="o.id"
        class="order-card"
        @click="openDetail(o.id)"
      >
        <div class="section-heading">
          <span class="plate">{{ vehicle(o.vehicle)?.plate }}</span>
          <span :class="['badge', statusClass(o.status)]">{{ o.status }}</span>
        </div>
        <div class="order-vehicle-info">
          <h2>{{ vehicleName(o.vehicle) }}</h2>
          <p>{{ owner(o.vehicle)?.name }}</p>
        </div>
        <div class="service-preview">
          <strong>{{ o.service }}</strong>
          <small>OT #{{ o.id }} · {{ o.date }}</small>
        </div>
        <div class="order-meta-info">
          <span v-if="o.km || vehicle(o.vehicle)?.km" class="meta-tag">
            {{ (o.km || vehicle(o.vehicle)?.km)?.toLocaleString('es-AR') }} km
          </span>
          <span v-if="o.photos && o.photos.length" class="meta-tag">
            {{ o.photos.length }} {{ o.photos.length === 1 ? 'foto' : 'fotos' }}
          </span>
          <span v-if="o.notes" class="meta-tag">
            Observaciones
          </span>
        </div>
        <footer>
          <div class="mechanic">
            <span>{{ o.mechanic }}</span>
            <small v-if="o.bay">· Puesto 0{{ o.bay }}</small>
          </div>
          <span>Ver orden <ArrowUpRight :size="16" /></span>
        </footer>
      </button>
    </div>

    <!-- VISTA 2: TABLA (TABLE) -->
    <section v-else-if="viewMode === 'table' && filteredOrders.length" class="panel table-scroll">
      <table>
        <thead>
          <tr>
            <th>ORDEN</th>
            <th>VEHÍCULO</th>
            <th>CLIENTE</th>
            <th>SERVICIO / TRABAJO</th>
            <th>ESTADO</th>
            <th>MECÁNICO</th>
            <th style="text-align: right">ACCIONES</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="o in filteredOrders" :key="o.id">
            <td>
              <div class="table-order-meta">
                <strong>OT #{{ o.id }}</strong>
                <small class="muted" style="display: block; font-size: 9px">{{ o.date }}</small>
              </div>
            </td>
            <td>
              <div class="table-vehicle-cell">
                <span class="plate" style="font-size: 10px; padding: 2px 6px">{{ vehicle(o.vehicle)?.plate }}</span>
                <strong>{{ vehicleName(o.vehicle) }}</strong>
              </div>
            </td>
            <td>
              <span>{{ owner(o.vehicle)?.name || 'Sin cliente' }}</span>
            </td>
            <td>
              <div class="table-service-desc">
                <strong>{{ o.service }}</strong>
                <div class="table-service-tags">
                  <span v-if="o.km || vehicle(o.vehicle)?.km" class="meta-tag-mini">
                    {{ (o.km || vehicle(o.vehicle)?.km)?.toLocaleString('es-AR') }} km
                  </span>

                </div>
              </div>
            </td>
            <td>
              <span :class="['badge', statusClass(o.status)]">{{ o.status }}</span>
            </td>
            <td>
              <div class="table-mechanic-cell">
                <span>{{ o.mechanic }}</span>
                <small v-if="o.bay" class="muted">· Puesto 0{{ o.bay }}</small>
              </div>
            </td>
            <td style="text-align: right">
              <button class="text-button" @click="openDetail(o.id)">
                Ver orden <ArrowUpRight :size="15" />
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <div v-if="!filteredOrders.length" class="empty-state">
      <Search />
      <h3>No encontramos órdenes</h3>
      <p>Probá con otra patente o cambiá el filtro.</p>
    </div>

    <!-- Modals -->
    <OrdenesModalFormularioOrden
      :open="newOrderOpen"
      @close="newOrderOpen = false"
      @created="handleCreated"
    />

    <OrdenesModalDetalleOrden
      :open="detailOrderOpen"
      :order-id="detailOrderId"
      @close="detailOrderOpen = false"
      @updated="detailOrderOpen = false"
    />
  </div>
</template>

<style scoped>
.order-card .plate,
.order-card .service-preview {
  background: transparent !important;
  border: 0 !important;
  box-shadow: none;
}

.order-card .plate {
  padding-inline: 0;
}

.order-card .service-preview {
  padding-inline: 0;
}

.toolbar-left-group {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.order-meta-info {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 10px 0 12px 0;
}

.meta-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 11px;
  color: #475569;
  font-weight: 550;
}

:global(html.dark .meta-tag) {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(255, 255, 255, 0.1);
  color: #a1a1aa;
}

.table-vehicle-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.table-service-desc {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 250px;
}

.table-service-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.meta-tag-mini {
  font-size: 9.5px;
  padding: 1px 5px;
  border-radius: 4px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  color: #64748b;
}

:global(html.dark .meta-tag-mini) {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(255, 255, 255, 0.1);
  color: #94a3b8;
}

.table-mechanic-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
