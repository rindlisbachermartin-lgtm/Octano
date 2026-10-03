<script setup lang="ts">
import {
  Plus,
  Search,
  ArrowUpRight,
  LayoutGrid,
  Table,
} from 'lucide-vue-next'
import type { Client } from '~/types'

const { db } = useDatabase()
const { matches } = useHelpers()
const { notify } = useWorkshopToast()

const search = ref('')
const debouncedSearch = useDebouncedValue(search)
const viewMode = useListView('clientes', 'table', ['table', 'cards'] as const)
const formModalOpen = ref(false)
const detailModalOpen = ref(false)
const selectedClient = ref<Client | null>(null)

const filteredClients = computed(() =>
  db.value.clients.filter(
    (c) => c.active && matches(debouncedSearch.value, c.name, c.doc, c.phone, c.email)
  )
)

function openDetail(c: Client) {
  selectedClient.value = c
  detailModalOpen.value = true
}

function openEdit(c: Client) {
  selectedClient.value = c
  detailModalOpen.value = false
  formModalOpen.value = true
}

function openNew() {
  selectedClient.value = null
  formModalOpen.value = true
}

function handleSave(clientData: Partial<Client>) {
  if (selectedClient.value) {
    Object.assign(selectedClient.value, clientData)
    notify('Datos del cliente actualizados.')
  } else {
    const newClient: Client = {
      id: Date.now(),
      name: clientData.name || '',
      doc: clientData.doc || '',
      phone: clientData.phone || '',
      email: clientData.email || '',
      active: true,
    }
    db.value.clients.push(newClient)
    notify('Cliente agregado con éxito.')
  }
  formModalOpen.value = false
}

function handleArchive(c: Client) {
  c.active = false
  detailModalOpen.value = false
  notify('Cliente archivado. Su historial se conserva.')
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          TALLER CENTRAL / CLIENTES
        </div>
        <h1>El motor son las personas.</h1>
      </div>
      <button class="button primary" @click="openNew">
        <Plus :size="17" />Nuevo cliente
      </button>
    </section>

    <div class="list-toolbar">
      <label class="search-box">
        <Search :size="17" />
        <input
          v-model="search"
          placeholder="Nombre, documento o teléfono…"
          aria-label="Buscar clientes"
        />
      </label>
      <div class="toolbar-left-group">
        <span class="muted">{{ filteredClients.length }} clientes activos</span>
        <div class="segmented">
          <button
            type="button"
            :class="{ selected: viewMode === 'table' }"
            :aria-pressed="viewMode === 'table'"
            @click="viewMode = 'table'"
            title="Ver en formato tabla"
          >
            <Table :size="14" /> Tabla
          </button>
          <button
            type="button"
            :class="{ selected: viewMode === 'cards' }"
            :aria-pressed="viewMode === 'cards'"
            @click="viewMode = 'cards'"
            title="Ver en formato tarjetas"
          >
            <LayoutGrid :size="14" /> Tarjetas
          </button>
        </div>
      </div>

    </div>

    <!-- VISTA 1: TABLA (TABLE) -->
    <section v-if="viewMode === 'table' && filteredClients.length" class="panel table-scroll">
      <table>
        <thead>
          <tr>
            <th>CLIENTE</th>
            <th>DNI / CUIT</th>
            <th>CONTACTO</th>
            <th>VEHÍCULOS</th>
            <th style="text-align: right">ACCIONES</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in filteredClients" :key="c.id">
            <td>
              <div class="client-cell">
                <strong>{{ c.name }}</strong>
              </div>
            </td>
            <td>{{ c.doc }}</td>
            <td>
              <span>{{ c.phone || 'Sin teléfono' }}</span>
              <small>{{ c.email || 'Sin correo' }}</small>
            </td>
            <td>
              <span class="badge neutral">
                {{ db.vehicles.filter((v) => v.client === c.id).length }} vehículo(s)
              </span>
            </td>
            <td style="text-align: right">
              <button class="text-button" @click="openDetail(c)">
                Ver cliente <ArrowUpRight :size="15" />
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <!-- VISTA 2: TARJETAS (CARDS) -->
    <div v-else-if="viewMode === 'cards' && filteredClients.length" class="client-grid">
      <button
        v-for="c in filteredClients"
        :key="c.id"
        class="client-card"
        @click="openDetail(c)"
      >
        <CommonOctanoLogo class="client-card-brand" />
        <div class="client-card-header">
          <div>
            <h3>{{ c.name }}</h3>
            <small class="muted">DNI / CUIT: {{ c.doc }}</small>
          </div>
        </div>
        <div class="client-card-body">
          <p v-if="c.phone">Tel. {{ c.phone }}</p>
          <p v-if="c.email" class="muted">Email: {{ c.email }}</p>
        </div>
        <footer class="client-card-footer">
          <span class="badge neutral">
            {{ db.vehicles.filter((v) => v.client === c.id).length }} vehículo(s)
          </span>
          <span class="client-link">Ver cliente <ArrowUpRight :size="15" /></span>
        </footer>
      </button>
    </div>

    <div v-if="!filteredClients.length" class="empty-state">
      <Search />
      <h3>No encontramos clientes</h3>
      <p>No hay clientes que coincidan con la búsqueda.</p>
    </div>

    <!-- Modals -->
    <ClientesModalDetalleCliente
      :open="detailModalOpen"
      :client="selectedClient"
      @close="detailModalOpen = false"
      @edit="openEdit"
      @archive="handleArchive"
    />

    <ClientesModalFormularioCliente
      :open="formModalOpen"
      :client="selectedClient"
      @close="formModalOpen = false"
      @save="handleSave"
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

.client-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}

@media (max-width: 900px) {
  .client-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 600px) {
  .client-grid {
    grid-template-columns: 1fr;
  }
}

.client-card {
  position: relative;
  overflow: hidden;
  text-align: left;
  background: #fff;
  border: 1px solid var(--line);
  padding: 20px;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 14px;
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition:
    transform 160ms var(--ease),
    border-color 160ms ease,
    box-shadow 160ms ease;
}

.client-card-brand {
  position: absolute;
  top: 50%;
  right: 16px;
  width: 160px;
  height: 160px;
  transform: translate(50%, -50%);
  opacity: 0.35;
  color: #9ca3af;
  pointer-events: none;
}
.client-card > :not(.client-card-brand) { position: relative; z-index: 1; }
:global(html.dark .client-card-brand) { color: #71717a; }

.client-card:hover {
  border-color: #93c5fd;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(37, 99, 235, 0.08);
}

:global(html.dark .client-card) {
  background: #1c1c1e;
  border-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark .client-card:hover) {
  border-color: #3b82f6;
}

.client-card-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.client-card-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.client-card-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
}

.client-card-body p {
  margin: 0;
}

.client-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid #f1f5f9;
  padding-top: 12px;
  margin-top: 4px;
}

:global(html.dark .client-card-footer) {
  border-top-color: rgba(255, 255, 255, 0.08);
}

.client-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 600;
  color: #2563eb;
}

:global(html.dark .client-link) {
  color: #60a5fa;
}
</style>
