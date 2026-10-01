<script setup lang="ts">
import {
  Plus,
  Search,
  ArrowUpRight,
  LayoutGrid,
  Table,
} from 'lucide-vue-next'
import type { Vehicle } from '~/types'

const { db, client } = useDatabase()
const { matches } = useHelpers()
const { notify } = useWorkshopToast()

const search = ref('')
const viewMode = useListView('vehiculos', 'cards', ['table', 'cards'] as const)
const formModalOpen = ref(false)
const detailVehicleId = ref<number | null>(null)
const detailModalOpen = ref(false)
const vincularModalOpen = ref(false)
const selectedVehicle = ref<Vehicle | null>(null)
const transferOpen = ref(false)

function openNew() {
  selectedVehicle.value = null
  formModalOpen.value = true
}

function openEdit(v: Vehicle) {
  selectedVehicle.value = v
  detailModalOpen.value = false
  formModalOpen.value = true
}

function openTransfer(v: Vehicle) {
  selectedVehicle.value = v
  detailModalOpen.value = false
  transferOpen.value = true
}

function openQr(v: Vehicle) {
  selectedVehicle.value = v
  vincularModalOpen.value = true
}

function handleUpdated() {
  formModalOpen.value = false
  notify('Datos del vehículo actualizados.')
}

function handleTransfer() {
  transferOpen.value = false
  notify('Titular actualizado. El historial del vehículo se conserva.')
}

const filteredVehicles = computed(() =>
  db.value.vehicles.filter((v) =>
    matches(
      search.value,
      v.plate,
      v.brand,
      v.model,
      client(v.client)?.name,
      v.engine,
      v.year
    )
  )
)

function openDetail(v: Vehicle) {
  detailVehicleId.value = v.id
  detailModalOpen.value = true
}

function handleCreated(v: Vehicle) {
  formModalOpen.value = false
  notify('Vehículo registrado con éxito.')
  openDetail(v)
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          TALLER CENTRAL / VEHÍCULOS
        </div>
        <h1>Cada auto tiene una historia.</h1>
      </div>
      <button class="button primary" @click="openNew">
        <Plus :size="17" /> Nuevo vehículo
      </button>
    </section>

    <div class="list-toolbar">
      <label class="search-box">
        <Search :size="17" />
        <input
          v-model="search"
          placeholder="Patente, modelo o cliente…"
          aria-label="Buscar vehículos"
        />
      </label>
      <div class="toolbar-left-group">
        <span class="muted">{{ filteredVehicles.length }} vehículos registrados</span>
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
    <div v-if="viewMode === 'cards'" class="vehicle-grid">
      <article
        v-for="v in filteredVehicles"
        :key="v.id"
        class="vehicle-card"
      >
        <CommonBrandLogo :brand="v.brand" class="vehicle-card-brand" />
        <div class="section-heading">
          <span class="plate">{{ v.plate }}</span>
          <span v-if="v.qrCode" class="badge green" style="font-size: 8.5px; padding: 2px 6px">
            QR: {{ v.qrCode }}
          </span>
          <span v-else class="badge neutral" style="font-size: 8.5px; padding: 2px 6px">
            Sin QR
          </span>
        </div>
        <div class="vehicle-card-overview">
          <div class="vehicle-card-description">
            <h2>{{ v.brand }} {{ v.model }}</h2>
            <p>
              {{ v.year }} · {{ v.engine || 'Motor sin registrar' }} ·
              {{ Number(v.km).toLocaleString('es-AR') }} km
            </p>
          </div>
        </div>
        <footer>
          <span>{{ client(v.client)?.name }}</span>
        </footer>
        <div class="vehicle-actions">
          <button class="text-button" @click="openDetail(v)">Ver ficha <ArrowUpRight :size="15" /></button>
          <button class="text-button" @click="openEdit(v)">Editar</button>
          <button class="text-button" @click="openTransfer(v)">Cambiar titular</button>
          <button class="text-button" @click="openQr(v)">Gestionar QR</button>
          <NuxtLink class="text-button" :to="`/ficha/${v.id}`">Ficha pública</NuxtLink>
        </div>
      </article>
    </div>

    <!-- VISTA 2: TABLA (TABLE) -->
    <section v-else-if="viewMode === 'table' && filteredVehicles.length" class="panel table-scroll">
      <table>
        <thead>
          <tr>
            <th>PATENTE & QR</th>
            <th>VEHÍCULO</th>
            <th>PROPIETARIO</th>
            <th>AÑO / MOTOR</th>
            <th>KILOMETRAJE</th>
            <th style="text-align: right">ACCIONES</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="v in filteredVehicles" :key="v.id">
            <td>
              <div class="table-plate-cell">
                <span class="plate">{{ v.plate }}</span>
                <span v-if="v.qrCode" class="badge green" style="font-size: 8.5px; padding: 2px 6px">
                  QR: {{ v.qrCode }}
                </span>
                <span v-else class="badge neutral" style="font-size: 8.5px; padding: 2px 6px">
                  Sin QR
                </span>
              </div>
            </td>
            <td>
              <strong>{{ v.brand }} {{ v.model }}</strong>
            </td>
            <td>
              <span>{{ client(v.client)?.name || 'Sin asignar' }}</span>
            </td>
            <td>
              <span>{{ v.year }} · {{ v.engine || 'Motor sin registrar' }}</span>
            </td>
            <td>
              <span>{{ Number(v.km).toLocaleString('es-AR') }} km</span>
            </td>
            <td style="text-align: right">
              <div class="vehicle-actions">
              <button class="text-button" @click="openDetail(v)">
                Ver ficha <ArrowUpRight :size="15" />
              </button>
              <button class="text-button" @click="openEdit(v)">Editar</button>
              <button class="text-button" @click="openTransfer(v)">Cambiar titular</button>
              <button class="text-button" @click="openQr(v)">Gestionar QR</button>
              <NuxtLink class="text-button" :to="`/ficha/${v.id}`">Ficha pública</NuxtLink>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <div v-if="!filteredVehicles.length" class="empty-state">
      <Search />
      <h3>No se encontraron vehículos</h3>
      <p>Probá con otra patente, modelo o cliente.</p>
    </div>

    <!-- Modals -->
    <VehiculosModalFormularioVehiculo
      :open="formModalOpen"
      :vehicle="selectedVehicle"
      @close="formModalOpen = false"
      @created="handleCreated"
      @updated="handleUpdated"
    />

    <VehiculosModalDetalleVehiculo
      :open="detailModalOpen"
      :vehicle-id="detailVehicleId"
      @close="detailModalOpen = false"
      @edit="openEdit"
      @transfer="openTransfer"
    />

    <VehiculosModalVincularQr
      :open="vincularModalOpen"
      :preselected-vehicle-id="selectedVehicle?.id"
      @close="vincularModalOpen = false"
    />
    <VehiculosFormularioTitular v-if="transferOpen && selectedVehicle" :vehicle="selectedVehicle" @close="transferOpen = false" @saved="handleTransfer" />
  </div>
</template>

<style scoped>
.vehicle-card { position: relative; }
.vehicle-card-brand {
  position: absolute;
  top: 50%;
  right: 16px;
  width: 160px;
  height: 160px;
  transform: translate(50%, -50%);
  opacity: 0.35;
  pointer-events: none;
}
.vehicle-card > :not(.vehicle-card-brand) { position: relative; z-index: 1; }
.vehicle-card-overview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 14px;
}
.vehicle-card-description { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.vehicle-card-description h2 { margin-top: 0; }
.vehicle-card-description p { margin-top: 6px; font-size: 12px; color: var(--muted); }
@media (max-width: 600px) {
  .vehicle-grid { grid-template-columns: 1fr; }
}
.toolbar-left-group {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.table-plate-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
