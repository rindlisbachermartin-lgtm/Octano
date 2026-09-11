<script setup lang="ts">
import {
  Plus,
  Search,
  ArrowUpRight,
} from 'lucide-vue-next'

const { db, vehicle, vehicleName, owner } = useDatabase()
const { statusClass, matches } = useHelpers()
const { notify } = useToast()

const search = ref('')
const filter = ref('Todos')
const newOrderOpen = ref(false)
const detailOrderId = ref<number | null>(null)
const detailOrderOpen = ref(false)

const filteredOrders = computed(() =>
  db.value.orders.filter(
    (o) =>
      (filter.value === 'Todos' || o.status === filter.value) &&
      matches(
        search.value,
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
          <span class="tiny-star">✳</span>
          TALLER CENTRAL / ÓRDENES DE TRABAJO
        </div>
        <h1>Del ingreso a la entrega.</h1>
      </div>
      <button class="button primary" @click="newOrderOpen = true">
        <Plus :size="17" />Nueva orden
      </button>
    </section>

    <div class="list-toolbar">
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
      <label class="search-box">
        <Search :size="17" />
        <input
          v-model="search"
          placeholder="Buscar por patente, auto o cliente…"
          aria-label="Buscar órdenes"
        />
      </label>
    </div>

    <div class="orders-grid order-grid">
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
            📷 {{ o.photos.length }} {{ o.photos.length === 1 ? 'foto' : 'fotos' }}
          </span>
          <span v-if="o.notes" class="meta-tag">
            📝 Observaciones
          </span>
        </div>
        <footer>
          <div class="mechanic">
            <span class="micro-avatar">{{ o.mechanic[0] }}</span>
            <span>{{ o.mechanic }}</span>
            <small v-if="o.bay">· Puesto 0{{ o.bay }}</small>
          </div>
          <span>Ver orden <ArrowUpRight :size="16" /></span>
        </footer>
      </button>
    </div>

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

:global(html.dark) .meta-tag {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(255, 255, 255, 0.1);
  color: #a1a1aa;
}
</style>
