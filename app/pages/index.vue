<script setup lang="ts">
import {
  Plus,
  CarFront,
  ArrowUpRight,
} from 'lucide-vue-next'

const { db, activeOrders, vehicle, vehicleName } = useDatabase()
const { statusClass } = useHelpers()
const { notify } = useWorkshopToast()

const orderModalOpen = ref(false)
const detailOrderId = ref<number | null>(null)
const detailOrderOpen = ref(false)
const appointmentModalOpen = ref(false)

function openOrderDetail(id: number) {
  detailOrderId.value = id
  detailOrderOpen.value = true
}

function handleOrderCreated(id: number) {
  orderModalOpen.value = false
  notify('Orden de trabajo creada con éxito.')
  openOrderDetail(id)
}
</script>

<template>
  <div class="page-content dashboard-home">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          Lunes, 7 de septiembre de 2026
        </div>
        <h1>Panel general</h1>
        <p>Tu operación en tiempo real.</p>
      </div>
      <button class="button primary" @click="orderModalOpen = true">
        <Plus :size="17" />Nueva orden
      </button>
    </section>

    <!-- Top KPI cards -->
    <DashboardTarjetasResumen />

    <!-- Main Dashboard Columns: Trabajos en marcha + Agenda & Stock -->
    <div class="dashboard-columns">
      <!-- Active Orders table -->
      <section class="panel orders-panel">
        <div class="panel-top">
          <div>
            <h2>
              Trabajos en marcha
              <span class="count-bubble">{{ activeOrders.length }}</span>
            </h2>
          </div>
          <NuxtLink class="text-button" to="/ordenes">
            Ver todos <ArrowUpRight :size="14" />
          </NuxtLink>
        </div>

        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>ORDEN / VEHÍCULO</th>
                <th>TRABAJO</th>
                <th>ESTADO</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="o in activeOrders" :key="o.id">
                <td>
                  <button class="table-vehicle" @click="openOrderDetail(o.id)">
                    <span class="vehicle-square"><CarFront :size="20" /></span>
                    <span>
                      <strong>{{ vehicleName(o.vehicle) }}</strong>
                      <small>#{{ o.id }} <span>·</span> {{ vehicle(o.vehicle).plate }}</small>
                    </span>
                  </button>
                </td>
                <td>
                  <span class="service-text">{{ o.service }}</span>
                  <small class="mechanic">
                    <span class="micro-avatar">{{ o.mechanic[0] }}</span>
                    {{ o.mechanic }}
                  </small>
                </td>
                <td>
                  <span :class="['badge', statusClass(o.status)]">
                    <i></i>{{ o.status }}
                  </span>
                </td>
                <td>
                  <button
                    class="icon-button"
                    :aria-label="`Abrir orden ${o.id}`"
                    @click="openOrderDetail(o.id)"
                  >
                    <ArrowUpRight :size="17" />
                  </button>
                </td>
              </tr>
              <tr v-if="!activeOrders.length">
                <td colspan="4" class="empty-state">
                  Todo listo. No hay trabajos pendientes.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- Side Column: Agenda & Stock -->
      <div class="dashboard-side-col">
        <DashboardTablaProximosTurnos @new-appointment="appointmentModalOpen = true" />
        <DashboardPanelAlertasInventario />
      </div>
    </div>

    <!-- Modals -->
    <OrdenesModalFormularioOrden
      :open="orderModalOpen"
      @close="orderModalOpen = false"
      @created="handleOrderCreated"
    />

    <OrdenesModalDetalleOrden
      :open="detailOrderOpen"
      :order-id="detailOrderId"
      @close="detailOrderOpen = false"
      @updated="detailOrderOpen = false"
    />

    <AgendaModalTurno
      :open="appointmentModalOpen"
      @close="appointmentModalOpen = false"
      @created="
        appointmentModalOpen = false;
        notify('Turno agendado.');
      "
    />
  </div>
</template>

<style scoped>
.dashboard-side-col {
  display: flex;
  flex-direction: column;
  gap: 22px;
}
</style>
