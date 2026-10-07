<script setup lang="ts">
import { Wrench, Search, FileText } from 'lucide-vue-next'
import type { Order } from '~/types'
import { isWorkshopOrder } from '~/utils/appointmentLifecycle'

const props = defineProps<{ mechanic: 'Nicolás' | 'Santiago' }>()
const { db, vehicle, vehicleName } = useDatabase()
const { statusClass, matches } = useHelpers()

const search = ref('')
const debouncedSearch = useDebouncedValue(search)
const statusFilter = ref<'activas' | 'finalizadas'>('activas')
const { today } = useWorkshopDay()
const todaysJobs = computed(() => db.value.orders.filter((order) =>
  order.mechanic === props.mechanic &&
  order.date <= today.value &&
  isWorkshopOrder(order)
))
const jobsInProgress = computed(() => todaysJobs.value.filter((order) => order.status === 'En proceso').length)
const jobsWaiting = computed(() => todaysJobs.value.filter((order) => order.status === 'En espera').length)

// Active order working state
const activeOrderId = ref<number | null>(null)
const selectedOrder = computed(() =>
  db.value.orders.find((o) => o.id === activeOrderId.value && o.mechanic === props.mechanic && (isWorkshopOrder(o) || o.status === 'Finalizado')) || null
)
const editingOrder = ref<Order | null>(null)
// Filtered orders list
const filteredOrders = computed(() => {
  return db.value.orders.filter((o) => {
    // Status filter
    if (statusFilter.value === 'activas') {
      if (!isWorkshopOrder(o)) return false
    } else if (statusFilter.value === 'finalizadas') {
      if (o.status !== 'Finalizado') return false
    }

    // Mechanic filter
    if (o.mechanic !== props.mechanic) {
      return false
    }

    // Search filter
    const v = vehicle(o.vehicle)
    return matches(
      debouncedSearch.value,
      o.id,
      v?.plate,
      v?.brand,
      v?.model,
      o.service,
      o.mechanic
    )
  })
})

function selectOrder(order: Order) { activeOrderId.value = order.id }
function closeOrder() { activeOrderId.value = null }
function editOrder(id: number) {
  editingOrder.value = db.value.orders.find(order => order.id === id && order.mechanic === props.mechanic) || null
  closeOrder()
}
function handleEdited(id: number) {
  editingOrder.value = null
  activeOrderId.value = id
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          TALLER CENTRAL / ÁREA MECÁNICA
        </div>
        <h1>Hola, {{ mechanic }}</h1>
      </div>

    </section>

    <section class="mechanic-summary" aria-label="Resumen de trabajos para hoy">
      <article class="stat-card mechanic-jobs-card">
        <div><span>Trabajos para hoy</span><Wrench :size="19" /></div>
        <strong>{{ String(todaysJobs.length).padStart(2, '0') }}</strong>
        <small>{{ jobsInProgress }} en proceso <span class="separator">/</span> {{ jobsWaiting }} en espera</small>
        <CommonOctanoLogo class="mechanic-jobs-logo" />
      </article>
    </section>

    <!-- Toolbar -->
    <div class="list-toolbar">
      <label class="search-box">
        <Search :size="17" />
        <input
          v-model="search"
          placeholder="Buscar por patente, auto o trabajo…"
          aria-label="Buscar órdenes para mecánico"
        />
      </label>
      <div class="segmented status-filters" role="group" aria-label="Estado de los trabajos">
        <button
          :class="{ selected: statusFilter === 'activas' }"
          :aria-pressed="statusFilter === 'activas'"
          @click="statusFilter = 'activas'"
        >
          Órdenes en taller
        </button>
        <button
          :class="{ selected: statusFilter === 'finalizadas' }"
          :aria-pressed="statusFilter === 'finalizadas'"
          @click="statusFilter = 'finalizadas'"
        >
          Finalizadas
        </button>
      </div>


    </div>

    <!-- Orders Cards Grid for Mechanics -->
    <div class="mechanic-cards-grid">
      <article
        v-for="o in filteredOrders"
        :key="o.id"
        class="panel mechanic-order-card"
        :class="{ 'is-selected': activeOrderId === o.id }"
        @click="selectOrder(o)"
      >
        <CommonBrandLogo :brand="vehicle(o.vehicle)?.brand || ''" class="mechanic-order-brand" />
        <div class="card-top-row">
          <span class="plate">{{ vehicle(o.vehicle)?.plate }}</span>
          <span :class="['badge', statusClass(o.status)]">{{ o.status }}</span>
        </div>

        <div class="vehicle-info-block">
          <h2>{{ vehicleName(o.vehicle) }}</h2>
          <small class="mechanic-tag">Mecánico: <strong>{{ o.mechanic }}</strong></small>
        </div>

        <!-- SOLAMENTE TRABAJO A REALIZAR -->
        <div class="service-block">
          <span class="service-label">Trabajo a realizar:</span>
          <strong class="service-work-text">{{ o.service }}</strong>
        </div>

        <div class="card-action-row" @click.stop>
          <button
            type="button"
            class="button outlined btn-card-action"
            @click="selectOrder(o)"
          >
            <FileText :size="16" /> Ver trabajo
          </button>
        </div>
      </article>
    </div>

    <div v-if="!filteredOrders.length" class="empty-state mechanic-empty-state">
      <Wrench :size="38" class="muted" />
      <h3>No hay órdenes con este criterio</h3>
      <p>Probá cambiando el estado o buscando otra patente.</p>
    </div>

    <OrdenesModalDetalleOrden :open="!!selectedOrder" :order-id="activeOrderId" @close="closeOrder" @edit="editOrder" />
    <OrdenesModalFormularioOrden :open="!!editingOrder" :order="editingOrder" @close="editingOrder = null" @updated="handleEdited" />
  </div>
</template>

<style scoped>
:global(html.dark .mechanic-empty-state h3) { color: #fff; }
.mechanic-summary { margin-bottom: 18px; }
.mechanic-jobs-card { position: relative; overflow: hidden; max-width: 320px; padding: 16px 18px; cursor: default; }
.mechanic-jobs-card > div:first-child { font-size: 12px; }
.mechanic-jobs-card > strong { font-size: 28px; margin: 10px 0; }
.mechanic-jobs-card > small { font-size: 10px; }
.mechanic-jobs-card .mechanic-jobs-logo { position: absolute; top: 50%; right: 16px; width: 160px; height: 160px; transform: translate(50%, -50%); color: #9ca3af; opacity: .35; pointer-events: none; }
.mechanic-jobs-card > :not(.mechanic-jobs-logo) { position: relative; z-index: 1; }
.mechanic-jobs-card:hover, .mechanic-jobs-card:active { transform: none; }
:global(html.dark .mechanic-jobs-logo) { color: #71717a; }
@media (max-width: 600px) { .mechanic-jobs-card { max-width: none; padding: 12px 10px; } .mechanic-jobs-card > strong { font-size: 20px; } .mechanic-jobs-card .mechanic-jobs-logo { width: 110px; height: 110px; } }

.mechanic-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
  margin-top: 14px;
}

.mechanic-order-card {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 18px;
  border-radius: 14px;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
  border: 1px solid var(--line);
}

.mechanic-order-brand { position: absolute; top: 50%; right: 16px; width: 160px; height: 160px; transform: translate(50%, -50%); opacity: .45; pointer-events: none; }
.mechanic-order-card > :not(.mechanic-order-brand) { position: relative; z-index: 1; }

.mechanic-order-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  border-color: #0284c7;
}

.mechanic-order-card.is-selected {
  border-color: #0284c7;
  background: #f0f9ff;
}

:global(html.dark .mechanic-order-card.is-selected) {
  background: rgba(10, 132, 255, 0.12);
  border-color: #0a84ff;
}

.card-top-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.vehicle-info-block h2 {
  font-size: 18px;
  margin: 0 0 2px 0;
  color: #0f172a;
}

:global(html.dark .vehicle-info-block h2) {
  color: #f5f5f7;
}

.mechanic-tag {
  font-size: 12px;
  color: #64748b;
}

:global(html.dark .mechanic-tag) {
  color: #8e8e93;
}

.service-block {
  margin: 14px 0 16px 0;
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px solid #f1f5f9;
  border-radius: 10px;
}

:global(html.dark .service-block) {
  background: #252528;
  border-color: rgba(255, 255, 255, 0.06);
}

.service-label {
  display: block;
  font-size: 11px;
  text-transform: uppercase;
  color: #64748b;
  letter-spacing: 0.5px;
  font-weight: 650;
  margin-bottom: 4px;
}

:global(html.dark .service-label) {
  color: #8e8e93;
}

.service-work-text {
  font-size: 15px;
  color: #0284c7;
  line-height: 1.4;
  display: block;
}

:global(html.dark .service-work-text) {
  color: #64d2ff;
}

.btn-select-order {
  margin-top: auto;
  width: 100%;
  display: inline-flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
}

/* Modal Styling */
</style>
