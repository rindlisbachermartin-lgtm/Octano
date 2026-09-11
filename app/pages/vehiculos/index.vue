<script setup lang="ts">
import {
  Plus,
  Search,
  ScanLine,
  ArrowUpRight,
  Printer,
  QrCode,
} from 'lucide-vue-next'
import type { Vehicle } from '~/types'

const { db, client } = useDatabase()
const { matches } = useHelpers()
const { notify } = useToast()

const search = ref('')
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
          <span class="tiny-star">✳</span>
          TALLER CENTRAL / VEHÍCULOS
        </div>
        <h1>Cada auto tiene una historia.</h1>
      </div>
      <div style="display: flex; gap: 0.5rem; flex-wrap: wrap">
        <NuxtLink to="/vehiculos/plantilla-qr" class="button outlined">
          <Printer :size="16" /> Plantilla de QRs
        </NuxtLink>
        <button class="button outlined" @click="vincularModalOpen = true">
          <QrCode :size="16" /> Vincular QR
        </button>
        <button class="button primary" @click="formModalOpen = true">
          <Plus :size="17" /> Nuevo vehículo
        </button>
      </div>
    </section>

    <div class="list-toolbar">
      <span class="muted">{{ filteredVehicles.length }} vehículos registrados</span>
      <label class="search-box">
        <Search :size="17" />
        <input
          v-model="search"
          placeholder="Patente, modelo o cliente…"
          aria-label="Buscar vehículos"
        />
      </label>
    </div>

    <div class="vehicle-grid">
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
