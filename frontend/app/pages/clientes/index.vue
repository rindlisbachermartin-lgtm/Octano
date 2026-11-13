<script setup lang="ts">
import {
  Plus,
  Search,
  ArrowUpRight,
  LayoutGrid,
  Table,
} from '@lucide/vue'
import type { Client, VatCondition } from '~/types'

interface ClienteApi {
  id: number
  nombre: string
  documento: string
  telefono: string
  email: string | null
  calle: string | null
  altura: number | null
  condicionIvaId: number
  activo: boolean
}

interface CondicionIva {
  id: number
  nombre: VatCondition
}

const { db } = useDatabase()
const { matches } = useHelpers()
const { notify } = useWorkshopToast()
const auth = useAuthApi()
const conectado = computed(() => !!auth.user.value)
const clientes = ref<Client[]>([])
const condiciones = ref<CondicionIva[]>([])
const pagina = ref(0)
const totalPaginas = ref(0)
const cargando = ref(false)
const guardando = ref(false)
const errorLista = ref('')
const errorOperacion = ref('')
let consulta = 0
const vatOptions = computed(() => condiciones.value.map(c => c.nombre))

const search = ref('')
const mostrarArchivados = ref(false)
const debouncedSearch = useDebouncedValue(search)
const viewMode = useListView('clientes', 'table', ['table', 'cards'] as const)
const formModalOpen = ref(false)
const detailModalOpen = ref(false)
const selectedClient = ref<Client | null>(null)
const vehicleModalOpen = ref(false)
const vehicleClientId = ref<number | null>(null)

function assignVehicle(client: Client) {
  vehicleClientId.value = client.id
  detailModalOpen.value = false
  vehicleModalOpen.value = true
}

function handleVehicleCreated() {
  vehicleModalOpen.value = false
  notify('Vehículo registrado y asociado al cliente.')
  return navigateTo('/vehiculos')
}

const filteredClients = computed(() =>
  conectado.value ? clientes.value : db.value.clients.filter(
    (c) => c.active === !mostrarArchivados.value && matches(debouncedSearch.value, c.name, c.doc, c.phone, c.email)
  )
)

function adaptar(cliente: ClienteApi): Client {
  return {
    id: cliente.id,
    name: cliente.nombre,
    doc: cliente.documento,
    phone: cliente.telefono,
    email: cliente.email ?? '',
    address: cliente.calle ?? '',
    streetNumber: cliente.altura,
    vatCondition: condiciones.value.find(c => c.id === cliente.condicionIvaId)?.nombre,
    active: cliente.activo,
  }
}

async function cargarClientes(destino = 0) {
  const headers = auth.authorization.value
  if (!headers) return
  const turno = ++consulta
  const q = debouncedSearch.value.trim()
  cargando.value = true
  errorLista.value = ''
  try {
    const [catalogo, listado] = await Promise.all([
      condiciones.value.length ? Promise.resolve(condiciones.value) : $fetch<CondicionIva[]>('/api/condiciones-iva'),
      $fetch<{ contenido: ClienteApi[]; pagina: number; totalPaginas: number }>('/api/clientes', {
        headers, query: { q, activo: !mostrarArchivados.value, pagina: destino },
      }),
    ])
    if (turno !== consulta || headers !== auth.authorization.value) return
    condiciones.value = catalogo
    if (listado.pagina > 0 && listado.pagina >= listado.totalPaginas) {
      await cargarClientes(Math.max(0, listado.totalPaginas - 1))
      return
    }
    clientes.value = listado.contenido.map(adaptar)
    pagina.value = listado.pagina
    totalPaginas.value = listado.totalPaginas
  } catch (err) {
    if (turno === consulta) errorLista.value = authError(err, 'No pudimos cargar los clientes.')
  } finally {
    if (turno === consulta) cargando.value = false
  }
}

onMounted(() => cargarClientes())
watch([debouncedSearch, mostrarArchivados], () => cargarClientes())
onBeforeUnmount(() => { consulta++ })

function openDetail(c: Client) {
  errorOperacion.value = ''
  selectedClient.value = c
  detailModalOpen.value = true
}

function openEdit(c: Client) {
  errorOperacion.value = ''
  selectedClient.value = c
  detailModalOpen.value = false
  formModalOpen.value = true
}

function openNew() {
  errorOperacion.value = ''
  selectedClient.value = null
  formModalOpen.value = true
}

async function handleSave(clientData: Partial<Client>) {
  if (conectado.value) {
    const headers = auth.authorization.value
    const condicion = condiciones.value.find(c => c.nombre === clientData.vatCondition)
    if (guardando.value || !headers || !condicion) return
    const id = selectedClient.value?.id
    guardando.value = true
    errorOperacion.value = ''
    try {
      await $fetch(id ? `/api/clientes/${id}` : '/api/clientes', {
        method: id ? 'PUT' : 'POST',
        headers,
        body: {
          nombre: clientData.name,
          documento: clientData.doc,
          telefono: clientData.phone,
          email: clientData.email,
          calle: clientData.address?.trim() || null,
          altura: typeof clientData.streetNumber === 'number' ? clientData.streetNumber : null,
          condicionIvaId: condicion.id,
        },
      })
      formModalOpen.value = false
      notify(id ? 'Datos del cliente actualizados.' : 'Cliente agregado con éxito.')
      await cargarClientes(pagina.value)
    } catch (err) {
      errorOperacion.value = authError(err, 'No pudimos guardar el cliente.')
    } finally {
      guardando.value = false
    }
    return
  }
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

async function cambiarEstado(c: Client) {
  const activo = !c.active
  const mensaje = activo ? 'Cliente desarchivado.' : 'Cliente archivado. Su historial se conserva.'
  if (conectado.value) {
    const headers = auth.authorization.value
    if (!headers || guardando.value) return
    guardando.value = true
    errorOperacion.value = ''
    try {
      await $fetch(`/api/clientes/${c.id}/estado`, { method: 'PATCH', headers, body: { activo } })
      detailModalOpen.value = false
      notify(mensaje)
      await cargarClientes(pagina.value)
    } catch (err) {
      errorOperacion.value = authError(err, activo ? 'No pudimos desarchivar el cliente.' : 'No pudimos archivar el cliente.')
    } finally {
      guardando.value = false
    }
    return
  }
  c.active = activo
  detailModalOpen.value = false
  notify(mensaje)
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          {{ conectado ? auth.workshopName.value || 'TU TALLER' : 'TALLER CENTRAL' }} / CLIENTES
        </div>
        <h1>El motor son las personas.</h1>
      </div>
      <button class="button primary" :disabled="conectado && !condiciones.length" @click="openNew">
        <Plus :size="17" />Nuevo cliente
      </button>
    </section>

    <div class="list-toolbar">
      <div class="client-search-actions">
      <label class="search-box">
        <Search :size="17" />
        <input
          v-model="search"
          placeholder="Nombre, documento o teléfono…"
          aria-label="Buscar clientes"
          maxlength="90"
        />
      </label>
        <button type="button" class="button" :class="{ primary: mostrarArchivados }" :aria-pressed="mostrarArchivados" @click="mostrarArchivados = !mostrarArchivados">
          Archivados
        </button>
      </div>
      <div class="toolbar-left-group">
        <div class="segmented list-view-switch">
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
    <p v-if="cargando" class="muted" role="status">Cargando clientes…</p>
    <div v-else-if="errorLista" class="empty-state">
      <p role="alert">{{ errorLista }}</p>
      <button class="button" @click="cargarClientes()">Reintentar</button>
    </div>
    <section v-else-if="viewMode === 'table' && filteredClients.length" class="panel table-scroll">
      <table>
        <thead>
          <tr>
            <th>CLIENTE</th>
            <th>DNI / CUIT</th>
            <th>CONTACTO</th>
            <th>ESTADO</th>
            <th v-if="!conectado">VEHÍCULOS</th>
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
            <td><span class="badge neutral">{{ c.active ? 'Activo' : 'Archivado' }}</span></td>
            <td v-if="!conectado">
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
          <span v-if="!conectado" class="badge neutral">
            {{ db.vehicles.filter((v) => v.client === c.id).length }} vehículo(s)
          </span>
          <span class="client-link">Ver cliente <ArrowUpRight :size="15" /></span>
          <span v-if="!c.active" class="badge neutral client-card-archived">Archivado</span>
        </footer>
      </button>
    </div>

    <div v-if="!cargando && !errorLista && !filteredClients.length" class="empty-state">
      <Search />
      <h3>{{ mostrarArchivados ? 'No encontramos clientes archivados' : 'No encontramos clientes' }}</h3>
      <p v-if="search.trim()">No hay clientes que coincidan con la búsqueda.</p>
    </div>

    <div v-if="conectado && !errorLista && totalPaginas > 1" class="toolbar-left-group">
      <button class="button" :disabled="cargando || pagina === 0" @click="cargarClientes(pagina - 1)">Anterior</button>
      <span class="muted">Página {{ pagina + 1 }} de {{ totalPaginas }}</span>
      <button class="button" :disabled="cargando || pagina + 1 >= totalPaginas" @click="cargarClientes(pagina + 1)">Siguiente</button>
    </div>

    <!-- Modals -->
    <ClientesModalDetalleCliente
      :open="detailModalOpen"
      :client="selectedClient"
      :show-vehicles="!conectado"
      :busy="guardando"
      :error="errorOperacion"
      @close="detailModalOpen = false"
      @edit="openEdit"
      @archive="cambiarEstado"
      @unarchive="cambiarEstado"
      @assign-vehicle="assignVehicle"
    />

    <ClientesModalFormularioCliente
      :open="formModalOpen"
      :client="selectedClient"
      :vat-options="conectado ? vatOptions : undefined"
      :busy="guardando"
      :error="errorOperacion"
      @close="formModalOpen = false"
      @save="handleSave"
    />
    <VehiculosModalFormularioVehiculo
      v-if="!conectado"
      :open="vehicleModalOpen"
      :client-id="vehicleClientId"
      @close="vehicleModalOpen = false"
      @created="handleVehicleCreated"
    />
  </div>
</template>

<style scoped>
.client-search-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

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

.client-card-archived {
  margin-left: auto;
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
