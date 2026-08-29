<template>
  <div class="clientes-page">
    <!-- Header de la sección -->
    <div class="page-title-row">
      <div>
        <h2 class="title-main">Clientes</h2>
        <p class="title-sub">Gestión del listado de clientes y sus vehículos registrados.</p>
      </div>

      <button class="btn-new-client" @click="abrirModalNuevoCliente">
        <span class="material-symbols-outlined icon-btn-inline">person_add</span>
        <span>Nuevo Cliente</span>
      </button>
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

      <div class="filter-dropdown-wrapper">
        <select v-model="filtroCondicionFiscal" class="filter-select">
          <option value="">Todas las condiciones fiscales (ARCA)</option>
          <option value="Consumidor Final">Consumidor Final</option>
          <option value="Responsable Inscripto">Responsable Inscripto</option>
          <option value="Monotributo">Monotributo</option>
          <option value="Exento">Exento</option>
        </select>
      </div>
    </div>

    <!-- Data Table Surface -->
    <div class="table-container-card">
      <div class="table-scroll-wrapper">
        <table class="data-table">
          <thead>
            <tr class="table-head-row">
              <th class="th-cell">Cliente</th>
              <th class="th-cell">Condición Fiscal</th>
              <th class="th-cell">Vehículo</th>
              <th class="th-cell">Última Visita</th>
              <th class="th-cell text-right">Total Gastado</th>
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

              <!-- Columna Condición Fiscal ARCA -->
              <td class="td-cell">
                <span :class="['fiscal-badge', obtenerClaseCondicionFiscal(cliente.condicionFiscal)]">
                  {{ cliente.condicionFiscal }}
                </span>
              </td>

              <!-- Columna Vehículo -->
              <td class="td-cell">
                <div class="vehicle-model">{{ cliente.vehiculoPrincipal || 'Sin vehículo' }}</div>
                <div class="vehicle-plate">{{ cliente.patentePrincipal || 'S/P' }}</div>
              </td>

              <!-- Columna Última Visita -->
              <td class="td-cell text-muted">
                {{ cliente.ultimaVisita || 'Sin visitas' }}
              </td>

              <!-- Columna Total Gastado -->
              <td class="td-cell text-right font-medium">
                ${{ cliente.totalGastado.toLocaleString('es-AR', { minimumFractionDigits: 2 }) }}
              </td>

              <!-- Columna Acciones -->
              <td class="td-cell text-center">
                <div class="row-actions">
                  <button class="btn-action-icon" @click="verDetalleCliente(cliente)" title="Ver detalle y vehículos">
                    <span class="material-symbols-outlined">visibility</span>
                  </button>
                  <button class="btn-action-icon" @click="abrirModalEditarCliente(cliente)" title="Editar cliente">
                    <span class="material-symbols-outlined">edit</span>
                  </button>
                  <button class="btn-action-icon text-danger" @click="darDeBajaCliente(cliente)" title="Dar de baja (RF-01)">
                    <span class="material-symbols-outlined">delete</span>
                  </button>
                </div>
              </td>
            </tr>

            <tr v-if="clientesFiltrados.length === 0">
              <td colspan="6" class="empty-state-cell">
                <span class="material-symbols-outlined empty-icon">person_search</span>
                <p>No se encontraron clientes que coincidan con la búsqueda.</p>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination Footer -->
      <div class="pagination-footer">
        <span class="pagination-info">
          Mostrando {{ clientesFiltrados.length }} de {{ listaClientes.length }} clientes registrados
        </span>
        <div class="pagination-controls">
          <button class="btn-pagination" disabled>Anterior</button>
          <button class="btn-pagination">Siguiente</button>
        </div>
      </div>
    </div>

    <!-- Modales Modulares -->
    <ModalFormularioCliente
      v-model:esVisible="esModalFormularioVisible"
      :editandoId="clienteEditandoId"
      :formulario="formularioCliente"
      @guardar="guardarCliente"
    />

    <ModalDetalleCliente
      :cliente="clienteSeleccionado"
      @cerrar="clienteSeleccionado = null"
    />
  </div>
</template>

<script setup lang="ts">
import type { Cliente, FormularioCliente, CondicionFiscal } from '~/types/cliente'
import ModalFormularioCliente from '~/components/clientes/ModalFormularioCliente.vue'
import ModalDetalleCliente from '~/components/clientes/ModalDetalleCliente.vue'

const filtroBusqueda = ref('')
const filtroCondicionFiscal = ref('')
const esModalFormularioVisible = ref(false)
const clienteEditandoId = ref<number | null>(null)
const clienteSeleccionado = ref<Cliente | null>(null)

const formularioCliente = reactive<FormularioCliente>({
  nombre: '',
  cuit: '',
  condicionFiscal: 'Consumidor Final',
  email: '',
  telefono: '',
  patente: '',
  vehiculo: '',
  anio: 2022,
  kilometraje: 0
})

const listaClientes = ref<Cliente[]>([
  {
    id: 1,
    nombre: 'Carlos Rodríguez',
    cuit: '20-35891234-9',
    condicionFiscal: 'Monotributo',
    email: 'carlos.r@email.com',
    telefono: '+54 9 2392 411223',
    vehiculoPrincipal: 'Toyota Hilux 2.8 TDI',
    patentePrincipal: 'ABC-123',
    ultimaVisita: '12 Oct 2023',
    totalGastado: 1450.00,
    estaActivo: true
  },
  {
    id: 2,
    nombre: 'María Gómez',
    cuit: '27-38192837-4',
    condicionFiscal: 'Consumidor Final',
    email: 'maria.g@email.com',
    telefono: '+54 9 2392 455667',
    vehiculoPrincipal: 'Honda Civic EXL',
    patentePrincipal: 'XYZ-987',
    ultimaVisita: '05 Sep 2023',
    totalGastado: 850.50,
    estaActivo: true
  },
  {
    id: 3,
    nombre: 'Empresa Logística S.A.',
    cuit: '30-71239845-1',
    condicionFiscal: 'Responsable Inscripto',
    email: 'flota@logistica.com',
    telefono: '+54 9 2392 499881',
    vehiculoPrincipal: 'Ford Transit (Flota)',
    patentePrincipal: 'AD 512 MP',
    ultimaVisita: '28 Ago 2023',
    totalGastado: 5200.00,
    estaActivo: true
  },
  {
    id: 4,
    nombre: 'Martín Rindlisbacher',
    cuit: '20-41892831-2',
    condicionFiscal: 'Consumidor Final',
    email: 'martin@email.com',
    telefono: '+54 9 2392 612345',
    vehiculoPrincipal: 'Volkswagen Gol Trend 1.6',
    patentePrincipal: 'AE 341 KC',
    ultimaVisita: '18 Feb 2026',
    totalGastado: 2100.00,
    estaActivo: true
  },
  {
    id: 5,
    nombre: 'Agropecuaria El Ombú S.A.',
    cuit: '30-68192341-8',
    condicionFiscal: 'Responsable Inscripto',
    email: 'contacto@elombu.com',
    telefono: '+54 9 2392 519922',
    vehiculoPrincipal: 'Toyota Hilux 2.8 TDI',
    patentePrincipal: 'AF 892 PL',
    ultimaVisita: '22 Feb 2026',
    totalGastado: 7800.00,
    estaActivo: true
  }
])

const clientesFiltrados = computed(() => {
  return listaClientes.value.filter(cliente => {
    if (!cliente.estaActivo) return false

    const busqueda = filtroBusqueda.value.toLowerCase().trim()
    const coincideTexto = !busqueda || 
      cliente.nombre.toLowerCase().includes(busqueda) || 
      cliente.cuit.toLowerCase().includes(busqueda) || 
      cliente.email.toLowerCase().includes(busqueda) || 
      (cliente.patentePrincipal && cliente.patentePrincipal.toLowerCase().includes(busqueda)) ||
      (cliente.vehiculoPrincipal && cliente.vehiculoPrincipal.toLowerCase().includes(busqueda))
    
    const coincideFiscal = !filtroCondicionFiscal.value || cliente.condicionFiscal === filtroCondicionFiscal.value

    return coincideTexto && coincideFiscal
  })
})

const obtenerClaseCondicionFiscal = (condicion: CondicionFiscal): string => {
  switch (condicion) {
    case 'Responsable Inscripto': return 'badge-ri'
    case 'Monotributo': return 'badge-mono'
    case 'Exento': return 'badge-exento'
    default: return 'badge-cf'
  }
}

const abrirModalNuevoCliente = () => {
  clienteEditandoId.value = null
  formularioCliente.nombre = ''
  formularioCliente.cuit = ''
  formularioCliente.condicionFiscal = 'Consumidor Final'
  formularioCliente.email = ''
  formularioCliente.telefono = ''
  formularioCliente.patente = ''
  formularioCliente.vehiculo = ''
  formularioCliente.anio = 2022
  formularioCliente.kilometraje = 0
  esModalFormularioVisible.value = true
}

const abrirModalEditarCliente = (cliente: Cliente) => {
  clienteEditandoId.value = cliente.id
  formularioCliente.nombre = cliente.nombre
  formularioCliente.cuit = cliente.cuit
  formularioCliente.condicionFiscal = cliente.condicionFiscal
  formularioCliente.email = cliente.email
  formularioCliente.telefono = cliente.telefono
  formularioCliente.patente = cliente.patentePrincipal || ''
  formularioCliente.vehiculo = cliente.vehiculoPrincipal || ''
  esModalFormularioVisible.value = true
}

const verDetalleCliente = (cliente: Cliente) => {
  clienteSeleccionado.value = cliente
}

const darDeBajaCliente = (cliente: Cliente) => {
  if (confirm(`¿Confirma dar de baja lógica al cliente "${cliente.nombre}"? (RF-01)`)) {
    cliente.estaActivo = false
  }
}

const guardarCliente = () => {
  if (clienteEditandoId.value) {
    const indice = listaClientes.value.findIndex(c => c.id === clienteEditandoId.value)
    if (indice !== -1) {
      listaClientes.value[indice] = {
        ...listaClientes.value[indice],
        nombre: formularioCliente.nombre,
        cuit: formularioCliente.cuit,
        condicionFiscal: formularioCliente.condicionFiscal,
        email: formularioCliente.email,
        telefono: formularioCliente.telefono,
        vehiculoPrincipal: formularioCliente.vehiculo || listaClientes.value[indice].vehiculoPrincipal,
        patentePrincipal: formularioCliente.patente || listaClientes.value[indice].patentePrincipal
      }
    }
  } else {
    const nuevoCliente: Cliente = {
      id: Date.now(),
      nombre: formularioCliente.nombre,
      cuit: formularioCliente.cuit,
      condicionFiscal: formularioCliente.condicionFiscal,
      email: formularioCliente.email,
      telefono: formularioCliente.telefono,
      vehiculoPrincipal: formularioCliente.vehiculo || 'Sin asignar',
      patentePrincipal: formularioCliente.patente || 'S/P',
      ultimaVisita: 'Hoy',
      totalGastado: 0,
      estaActivo: true
    }
    listaClientes.value.unshift(nuevoCliente)
  }
  esModalFormularioVisible.value = false
}
</script>

<style scoped>
.clientes-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.page-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title-main {
  font-size: 24px;
  font-weight: 700;
  color: var(--on-surface);
  line-height: 32px;
}

.title-sub {
  font-size: 14px;
  color: var(--on-surface-variant);
  margin-top: 4px;
}

.btn-new-client {
  background-color: var(--primary-container);
  color: var(--on-primary-container);
  padding: 8px 16px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: var(--shadow-sm);
  transition: opacity 0.15s ease;
}

.btn-new-client:hover {
  opacity: 0.9;
}

.icon-btn-inline {
  font-size: 18px;
}

/* Filter Bar */
.filter-bar {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.search-input-wrapper {
  position: relative;
  flex: 1;
  min-width: 280px;
}

.search-filter-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--on-surface-variant);
  font-size: 18px;
}

.filter-input {
  width: 100%;
  padding: 10px 16px 10px 38px;
  background-color: var(--surface-container-lowest);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  font-size: 14px;
  font-family: inherit;
  color: var(--on-surface);
  outline: none;
}

.filter-input:focus, .filter-select:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px rgba(0, 97, 153, 0.15);
}

.filter-select {
  padding: 10px 16px;
  background-color: var(--surface-container-lowest);
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  font-size: 14px;
  font-family: inherit;
  color: var(--on-surface);
  outline: none;
  cursor: pointer;
}

/* Data Table Surface */
.table-container-card {
  background-color: var(--surface-container-lowest);
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
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
  background-color: var(--surface-container-low);
  border-bottom: 1px solid var(--border-subtle);
}

.th-cell {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  color: var(--on-surface-variant);
  padding: 12px 16px;
}

.table-body-row {
  border-bottom: 1px solid var(--border-subtle);
  transition: background-color 0.15s ease;
}

.table-body-row:hover {
  background-color: var(--surface-container);
}

.td-cell {
  padding: 12px 16px;
  font-size: 14px;
  color: var(--on-surface);
}

.client-name {
  font-weight: 600;
  color: var(--primary);
}

.client-email {
  font-size: 12px;
  color: var(--on-surface-variant);
  margin-top: 2px;
}

.fiscal-badge {
  font-size: 11px;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 4px;
  display: inline-block;
}

.badge-ri { background-color: #dbeafe; color: #1e40af; }
.badge-mono { background-color: #fef3c7; color: #92400e; }
.badge-cf { background-color: #e0f2fe; color: #0369a1; }
.badge-exento { background-color: #f3f4f6; color: #374151; }

.vehicle-model {
  font-weight: 500;
}

.vehicle-plate {
  font-size: 12px;
  color: var(--on-surface-variant);
  margin-top: 2px;
  font-family: monospace;
  font-weight: 700;
}

.text-muted {
  color: var(--on-surface-variant);
}

.text-right {
  text-align: right;
}

.text-center {
  text-align: center;
}

.font-medium {
  font-weight: 600;
}

.row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.btn-action-icon {
  background: transparent;
  border: none;
  color: var(--secondary);
  padding: 4px;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
}

.btn-action-icon:hover {
  color: var(--primary);
  background-color: var(--surface-container-low);
}

.btn-action-icon.text-danger:hover {
  color: var(--error);
}

.empty-state-cell {
  text-align: center;
  padding: 48px 16px;
  color: var(--on-surface-variant);
}

.empty-icon {
  font-size: 40px;
  color: var(--outline);
  margin-bottom: 8px;
}

/* Pagination Footer */
.pagination-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--border-subtle);
  background-color: var(--surface-container-low);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pagination-info {
  font-size: 14px;
  color: var(--on-surface-variant);
}

.pagination-controls {
  display: flex;
  gap: 8px;
}

.btn-pagination {
  padding: 6px 12px;
  border: 1px solid var(--border-subtle);
  background-color: var(--surface-container-lowest);
  border-radius: 4px;
  font-size: 13px;
  color: var(--on-surface);
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.btn-pagination:hover:not(:disabled) {
  background-color: var(--surface-container);
}

.btn-pagination:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
