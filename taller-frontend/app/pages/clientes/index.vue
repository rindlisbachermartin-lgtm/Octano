<template>
  <div class="clientes-page">
    <!-- Header de la sección (Estilo Bosch OfficeOn) -->
    <div class="page-title-row">
      <div>
        <h2 class="title-main">Clientes</h2>
        <p class="title-sub">Gestión del listado de clientes y sus vehículos registrados.</p>
      </div>

      <NuxtLink to="/clientes/nuevo" class="btn-solid-primary">
        <span class="material-symbols-outlined icon-btn-inline">person_add</span>
        <span>Nuevo Cliente</span>
      </NuxtLink>
    </div>

    <!-- Barra de Filtro y Búsqueda -->
    <div class="filter-bar">
      <div class="search-input-wrapper">
        <span class="material-symbols-outlined search-filter-icon">search</span>
        <input 
          v-model="filtroBusqueda" 
          type="text" 
          placeholder="Buscar por nombre, CUIT, email o patente..." 
          class="filter-input"
        />
      </div>
    </div>

    <!-- Data Table Surface (Fondo blanco sólido, bordes limpios) -->
    <div class="table-container-card">
      <div class="table-scroll-wrapper">
        <table class="data-table">
          <thead>
            <tr class="table-head-row">
              <th class="th-cell">Cliente</th>
              <th class="th-cell">Vehículos Registrados</th>
              <th class="th-cell">Última Visita</th>
              <th class="th-cell text-center">Acciones</th>
            </tr>
          </thead>
          <tbody>
            <tr 
              v-for="cliente in clientesFiltrados" 
              :key="cliente.id" 
              class="table-body-row"
            >
              <!-- Columna Cliente -->
              <td class="td-cell">
                <div class="client-name">{{ cliente.nombre }}</div>
                <div class="client-email">{{ cliente.email }} • CUIT: {{ cliente.cuit }}</div>
              </td>

              <!-- Columna Vehículos (Cantidad y detalle) -->
              <td class="td-cell">
                <div class="vehicles-count-wrapper">
                  <div class="vehicles-count-pill" :class="{ 'zero-vehicles': cliente.cantidadVehiculos === 0 }">
                    <span class="material-symbols-outlined text-[16px]">directions_car</span>
                    <span>{{ cliente.cantidadVehiculos }} {{ cliente.cantidadVehiculos === 1 ? 'vehículo' : 'vehículos' }}</span>
                  </div>
                  <div class="vehicle-subdetail" v-if="cliente.cantidadVehiculos > 0">
                    {{ cliente.vehiculoPrincipal }} <span class="vehicle-plate" v-if="cliente.patentePrincipal && cliente.patentePrincipal !== 'S/P'">({{ cliente.patentePrincipal }})</span>
                  </div>
                </div>
              </td>

              <!-- Columna Última Visita -->
              <td class="td-cell text-muted">
                {{ cliente.ultimaVisita || 'Sin visitas' }}
              </td>

              <!-- Columna Acciones -->
              <td class="td-cell text-center">
                <div class="row-actions">
                  <button class="btn-action-icon" @click="verDetalle(cliente)" title="Ver ficha">
                    <span class="material-symbols-outlined">visibility</span>
                  </button>
                  <button class="btn-action-icon text-danger" @click="eliminar(cliente)" title="Dar de baja">
                    <span class="material-symbols-outlined">delete</span>
                  </button>
                </div>
              </td>
            </tr>

            <tr v-if="clientesFiltrados.length === 0">
              <td colspan="4" class="empty-state-cell">
                <span class="material-symbols-outlined empty-icon">person_search</span>
                <p>No se encontraron clientes que coincidan con la búsqueda.</p>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Modal Detalle del Cliente -->
    <ModalDetalleCliente
      :cliente="clienteSeleccionado"
      @cerrar="clienteSeleccionado = null"
    />
  </div>
</template>

<script setup lang="ts">
import type { Cliente } from '~/types/cliente'
import ModalDetalleCliente from '~/components/clientes/ModalDetalleCliente.vue'

const { listaClientes, darDeBajaCliente } = useClientes()

const filtroBusqueda = ref('')
const clienteSeleccionado = ref<Cliente | null>(null)

const clientesFiltrados = computed(() => {
  return listaClientes.value.filter(cliente => {
    if (!cliente.estaActivo) return false

    const busqueda = filtroBusqueda.value.toLowerCase().trim()
    return !busqueda || 
      cliente.nombre.toLowerCase().includes(busqueda) || 
      cliente.cuit.toLowerCase().includes(busqueda) || 
      cliente.email.toLowerCase().includes(busqueda) || 
      (cliente.patentePrincipal && cliente.patentePrincipal.toLowerCase().includes(busqueda)) ||
      (cliente.vehiculoPrincipal && cliente.vehiculoPrincipal.toLowerCase().includes(busqueda))
  })
})

const verDetalle = (cliente: Cliente) => {
  clienteSeleccionado.value = cliente
}

const eliminar = (cliente: Cliente) => {
  if (confirm(`¿Confirma dar de baja al cliente "${cliente.nombre}"?`)) {
    darDeBajaCliente(cliente.id)
  }
}
</script>

<style scoped>
.clientes-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1280px;
}

.page-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title-main {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.2;
}

.title-sub {
  font-size: 13px;
  color: var(--text-muted);
  margin-top: 2px;
}

.icon-btn-inline {
  font-size: 17px;
}

/* Filter Bar */
.filter-bar {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.search-input-wrapper {
  position: relative;
  flex: 1;
  min-width: 260px;
}

.search-filter-icon {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-subtle);
  font-size: 17px;
}

.filter-input {
  width: 100%;
  padding: 8px 12px 8px 34px;
  background-color: #ffffff;
  border: 1px solid var(--border-subtle);
  border-radius: var(--border-radius-sm);
  font-size: 13px;
  font-family: inherit;
  color: var(--text-main);
  outline: none;
}

.filter-input:focus {
  border-color: var(--border-focus);
}

/* Data Table Surface */
.table-container-card {
  background-color: #ffffff;
  border: 1px solid var(--border-subtle);
  border-radius: var(--border-radius-md);
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.table-scroll-wrapper {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  text-align: left;
  border-collapse: collapse;
}

.table-head-row {
  background-color: var(--surface-low);
  border-bottom: 1px solid var(--border-subtle);
}

.th-cell {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.03em;
  text-transform: uppercase;
  color: var(--text-muted);
  padding: 10px 14px;
}

.table-body-row {
  border-bottom: 1px solid var(--border-subtle);
  transition: background-color 0.1s ease;
}

.table-body-row:hover {
  background-color: var(--surface-low);
}

.td-cell {
  padding: 11px 14px;
  font-size: 13px;
  color: var(--text-main);
}

.client-name {
  font-weight: 600;
  color: var(--primary);
}

.client-email {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 1px;
}

/* Vehicles Count formatting */
.vehicles-count-wrapper {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.vehicles-count-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 600;
  color: var(--primary);
  background-color: var(--primary-light);
  padding: 2px 8px;
  border-radius: var(--border-radius-sm);
  width: fit-content;
}

.vehicles-count-pill.zero-vehicles {
  background-color: var(--surface-low);
  color: var(--text-muted);
}

.vehicle-subdetail {
  font-size: 11px;
  color: var(--text-muted);
}

.vehicle-plate {
  font-family: monospace;
  font-weight: 700;
}

.text-muted { color: var(--text-muted); }
.text-center { text-align: center; }

.row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.btn-action-icon {
  background: transparent;
  border: 1px solid transparent;
  color: var(--text-muted);
  padding: 5px;
  border-radius: var(--border-radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.1s ease;
}

.btn-action-icon:hover {
  color: var(--primary);
  background-color: var(--surface-low);
  border-color: var(--border-subtle);
}

.btn-action-icon.text-danger:hover {
  color: #ba1a1a;
}

.empty-state-cell {
  text-align: center;
  padding: 40px 16px;
  color: var(--text-muted);
}

.empty-icon {
  font-size: 36px;
  color: var(--text-subtle);
  margin-bottom: 6px;
}
</style>
