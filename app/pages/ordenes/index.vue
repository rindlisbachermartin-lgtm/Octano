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
          v-for="status in ['Todos', 'En espera', 'En proceso', 'Finalizado']"
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
        <div class="progress-block">
          <div class="progress-bar">
            <i :style="{ width: `${o.progress || 0}%` }"></i>
          </div>
          <span>{{ o.progress || 0 }}% avance</span>
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
