<script setup lang="ts">
import {
  Plus,
  Map,
  List,
  CarFront,
  ChevronRight,
  ArrowUpRight,
} from 'lucide-vue-next'

const { db, activeOrders, vehicle, vehicleName } = useDatabase()
const { statusClass } = useHelpers()
const { notify } = useToast()

const viewMode = ref<'map' | 'list'>('map')
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
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          <span class="tiny-star">✳</span>
          LUNES, 7 DE SEPTIEMBRE DE 2026
        </div>
      </div>
      <button class="button primary" @click="orderModalOpen = true">
        <Plus :size="17" />Nueva orden
      </button>
    </section>

    <!-- Top KPI cards -->
    <DashboardTarjetasResumen />

    <!-- Main Live Workshop & Agenda -->
    <div class="dashboard-columns">
      <section class="panel workshop-panel">
        <div class="panel-top">
          <div>
            <h2>El taller, en vivo <span class="live-dot"></span></h2>
          </div>
          <div class="segmented">
            <button
              :class="{ selected: viewMode === 'map' }"
              aria-label="Ver plano del taller"
              @click="viewMode = 'map'"
            >
              <Map :size="15" />Plano
            </button>
            <button
              :class="{ selected: viewMode === 'list' }"
              aria-label="Ver lista del taller"
              @click="viewMode = 'list'"
            >
              <List :size="15" />
            </button>
          </div>
        </div>

        <div v-if="viewMode === 'map'" class="workshop-map">
          <div class="map-meta">
            <span><i></i> PLANTA 01</span>
            <span>CAPACIDAD {{ db.orders.filter((o) => o.bay).length }}/4</span>
          </div>
          <div class="bays">
            <button
              v-for="bay in 4"
              :key="bay"
              class="bay"
              :class="{ empty: !db.orders.some((o) => o.bay === bay) }"
              @click="
                db.orders.some((o) => o.bay === bay)
                  ? openOrderDetail(db.orders.find((o) => o.bay === bay)!.id)
                  : (orderModalOpen = true)
              "
            >
              <span class="bay-label">PUESTO <strong>0{{ bay }}</strong></span>
              <template v-if="db.orders.some((o) => o.bay === bay)">
                <div class="occupied-bay">
                  <span class="vehicle-icon-wrap">
                    <CarFront :size="26" />
                  </span>
                  <span class="bay-name">
                    {{ vehicleName(db.orders.find((o) => o.bay === bay)!.vehicle) }}
                  </span>
                  <span class="plate">
                    {{ vehicle(db.orders.find((o) => o.bay === bay)!.vehicle).plate }}
                  </span>
                  <span class="bay-status">
                    <i :class="statusClass(db.orders.find((o) => o.bay === bay)!.status)"></i>
                    {{ db.orders.find((o) => o.bay === bay)!.status }}
                  </span>
                </div>
              </template>
              <template v-else>
                <div class="empty-bay">
                  <Plus :size="23" />
                  <span>El próximo<br />gran trabajo.</span>
                </div>
                <span class="bay-name">Puesto disponible</span>
                <span class="bay-status">Asignar vehículo <ArrowUpRight :size="13" /></span>
              </template>
            </button>
          </div>
          <div class="map-road">
            <span>←</span><span>ACCESO AL TALLER</span><span>→</span>
          </div>
        </div>

        <div v-else class="workshop-list">
          <button
            v-for="o in db.orders"
            :key="o.id"
            @click="openOrderDetail(o.id)"
          >
            <CarFront :size="22" />
            <span>
              <strong>{{ vehicleName(o.vehicle) }}</strong>
              <small>{{ o.service }}</small>
            </span>
            <span :class="['badge', statusClass(o.status)]">{{ o.status }}</span>
            <ChevronRight :size="16" />
          </button>
        </div>

        <footer class="map-footer">
          <span><span class="green-dot"></span>2 mecánicos en equipo</span>
          <NuxtLink class="text-button" to="/ordenes">
            Ver todas las órdenes <ArrowUpRight :size="14" />
          </NuxtLink>
        </footer>
      </section>

      <!-- Agenda Panel -->
      <DashboardTablaProximosTurnos @new-appointment="appointmentModalOpen = true" />
    </div>

    <!-- Active Orders table & Stock alert -->
    <div class="dashboard-columns bottom-columns">
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
              <tr v-for="o in activeOrders.slice(0, 3)" :key="o.id">
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

      <DashboardPanelAlertasInventario />
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
