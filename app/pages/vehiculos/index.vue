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
const viewMode = ref<'cards' | 'table'>('cards')
const formModalOpen = ref(false)
const detailVehicleId = ref<number | null>(null)
const detailModalOpen = ref(false)
const vincularModalOpen = ref(false)

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
      <button class="button primary" @click="formModalOpen = true">
        <Plus :size="17" /> Nuevo vehículo
      </button>
    </section>

    <div class="list-toolbar">
      <div class="toolbar-left-group">
        <span class="muted">{{ filteredVehicles.length }} vehículos registrados</span>
        <div class="segmented">
          <button
            type="button"
            :class="{ selected: viewMode === 'cards' }"
            @click="viewMode = 'cards'"
            title="Ver en formato tarjetas"
          >
            <LayoutGrid :size="14" /> Tarjetas
          </button>
          <button
            type="button"
            :class="{ selected: viewMode === 'table' }"
            @click="viewMode = 'table'"
            title="Ver en formato tabla"
          >
            <Table :size="14" /> Tabla
          </button>
        </div>
      </div>
      <label class="search-box">
        <Search :size="17" />
        <input
          v-model="search"
          placeholder="Patente, modelo o cliente…"
          aria-label="Buscar vehículos"
        />
      </label>
    </div>

    <!-- VISTA 1: TARJETAS (CARDS) -->
    <div v-if="viewMode === 'cards'" class="vehicle-grid">
      <button
        v-for="v in filteredVehicles"
        :key="v.id"
        class="vehicle-card"
        @click="openDetail(v)"
      >
        <div class="section-heading">
          <span class="plate">{{ v.plate }}</span>
          <span v-if="v.qrCode" class="badge green" style="font-size: 8.5px; padding: 2px 6px">
            QR: {{ v.qrCode }}
          </span>
          <span v-else class="badge neutral" style="font-size: 8.5px; padding: 2px 6px">
            Sin QR
          </span>
        </div>
        <h2>{{ v.brand }} {{ v.model }}</h2>
        <p>
          {{ v.year }} · {{ v.engine || 'Motor sin registrar' }} ·
          {{ Number(v.km).toLocaleString('es-AR') }} km
        </p>
        <footer>
          <span>{{ client(v.client)?.name }}</span>
          <ArrowUpRight :size="18" />
        </footer>
      </button>
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
              <button class="text-button" @click="openDetail(v)">
                Ver ficha <ArrowUpRight :size="15" />
              </button>
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
      @close="formModalOpen = false"
      @created="handleCreated"
    />

    <VehiculosModalDetalleVehiculo
      :open="detailModalOpen"
      :vehicle-id="detailVehicleId"
      @close="detailModalOpen = false"
    />

    <VehiculosModalVincularQr
      :open="vincularModalOpen"
      @close="vincularModalOpen = false"
    />
  </div>
</template>

<style scoped>
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
