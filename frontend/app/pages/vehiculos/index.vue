<script setup lang="ts">
import type { VehiculoApi } from '~/utils/vehiculoApi'
import {
  Plus,
  Search,
  ArrowUpRight,
  LayoutGrid,
  Table,
} from '@lucide/vue'
import type { Vehicle } from '~/types'

const { notify } = useWorkshopToast()
const auth = useAuthApi()

const vehiculos = ref<Vehicle[]>([])
const pagina = ref(0)
const totalPaginas = ref(0)
const total = ref(0)
const cargando = ref(false)
const errorLista = ref('')
const mostrarArchivados = ref(false)
const cambiandoEstado = ref(false)
const errorEstado = ref('')
let consulta: AbortController | undefined

const search = ref('')
const debouncedSearch = useDebouncedValue(search)
const viewMode = useListView('vehiculos', 'cards', ['table', 'cards'] as const)
const formModalOpen = ref(false)
const detailVehicleId = ref<number | null>(null)
const detailModalOpen = ref(false)
const vincularModalOpen = ref(false)
const selectedVehicle = ref<Vehicle | null>(null)
const transferOpen = ref(false)

async function cargarVehiculos(destino = 0) {
  const headers = auth.authorization.value
  if (!headers) return
  consulta?.abort()
  const actual = new AbortController()
  consulta = actual
  cargando.value = true
  errorLista.value = ''
  try {
    const listado = await $fetch<{ contenido: VehiculoApi[]; pagina: number; totalPaginas: number; totalElementos: number }>('/api/vehiculos', {
      headers, signal: actual.signal, query: { q: debouncedSearch.value.trim(), activo: !mostrarArchivados.value, pagina: destino },
    })
    if (actual.signal.aborted) return
    if (listado.pagina > 0 && listado.pagina >= listado.totalPaginas) {
      await cargarVehiculos(Math.max(0, listado.totalPaginas - 1))
      return
    }
    vehiculos.value = listado.contenido.map(adaptarVehiculo)
    pagina.value = listado.pagina
    totalPaginas.value = listado.totalPaginas
    total.value = listado.totalElementos
  } catch (err) {
    if (!actual.signal.aborted) errorLista.value = authError(err, 'No pudimos cargar los vehículos.')
  } finally {
    if (consulta === actual) cargando.value = false
  }
}

onMounted(() => cargarVehiculos())
watch([debouncedSearch, mostrarArchivados], () => cargarVehiculos())
onBeforeUnmount(() => consulta?.abort())

function nombreTitular(v: Vehicle) {
  return v.registro?.titular ?? 'Sin asignar'
}

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
  formModalOpen.value = false
  transferOpen.value = true
}

function openQr(v: Vehicle) {
  selectedVehicle.value = v
  detailModalOpen.value = false
  vincularModalOpen.value = true
}

function handleUpdated() {
  formModalOpen.value = false
  notify('Datos del vehículo actualizados.')
  void cargarVehiculos(pagina.value)
}

function handleTransfer() {
  transferOpen.value = false
  notify('Titular actualizado. El historial del vehículo se conserva.')
  void cargarVehiculos(pagina.value)
}

const filteredVehicles = computed(() =>
  vehiculos.value
)

function openDetail(v: Vehicle) {
  selectedVehicle.value = v
  errorEstado.value = ''
  detailVehicleId.value = v.id
  detailModalOpen.value = true
}

function handleCreated() {
  formModalOpen.value = false
  selectedVehicle.value = null
  detailModalOpen.value = false
  search.value = ''
  notify('Vehículo registrado con éxito.')
  mostrarArchivados.value = false
  void cargarVehiculos()
}

async function cambiarEstado(v: Vehicle) {
  const headers = auth.authorization.value
  if (!headers || !v.registro || cambiandoEstado.value) return
  const activo = !v.registro.activo
  cambiandoEstado.value = true
  errorEstado.value = ''
  try {
    await $fetch(`/api/vehiculos/${v.id}/estado`, { method: 'PATCH', headers, body: { activo } })
    detailModalOpen.value = false
    notify(activo ? 'Vehículo reactivado.' : 'Vehículo archivado.')
    await cargarVehiculos(pagina.value)
  } catch (err) {
    errorEstado.value = authError(err, 'No pudimos cambiar el estado del vehículo.')
  } finally {
    cambiandoEstado.value = false
  }
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          {{ auth.workshopName.value || 'TU TALLER' }} / VEHÍCULOS
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
          maxlength="250"
        />
      </label>
      <div class="toolbar-left-group">
        <button  type="button" class="button" :class="{ primary: mostrarArchivados }" :aria-pressed="mostrarArchivados" @click="mostrarArchivados = !mostrarArchivados">Archivados</button>
        <span class="muted">{{ total }} vehículos</span>
        <div class="segmented list-view-switch">
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
    <p v-if="cargando" role="status" class="muted">Cargando vehículos…</p>
    <div v-else-if="errorLista" class="empty-state">
      <p role="alert">{{ errorLista }}</p>
      <button type="button" class="button" @click="cargarVehiculos(pagina)">Reintentar</button>
    </div>
    <div v-else-if="viewMode === 'cards'" class="vehicle-grid">
      <article
        v-for="v in filteredVehicles"
        :key="v.id"
        class="vehicle-card"
        role="button"
        tabindex="0"
        :aria-label="`Ver detalle de ${[v.brand, v.model, vehicleVersion(v.version)].filter(Boolean).join(' ')}, patente ${v.plate}`"
        @click="openDetail(v)"
        @keydown.enter.self.prevent="openDetail(v)"
        @keydown.space.self.prevent="openDetail(v)"
      >
        <CommonBrandLogo :brand="v.brand" class="vehicle-card-brand" />
        <div class="section-heading">
          <span class="plate">{{ v.plate }}</span>
          <span v-if="v.qrCode" class="badge neutral qr-present" style="font-size: 8.5px; padding: 2px 6px">
            Con QR
          </span>
          <span v-else class="badge neutral" style="font-size: 8.5px; padding: 2px 6px">
            Sin QR
          </span>
        </div>
        <div class="vehicle-card-overview">
          <div class="vehicle-card-description">
            <h2>{{ vehicleLabel(v.brand, v.model, v.version) }}</h2>
            <p>
              {{ v.year }}<template v-if="vehicleFuel(v.version)"> · {{ vehicleFuel(v.version) }}</template> ·
              {{ kilometrajeVisible(v) }}
            </p>
          </div>
        </div>
        <footer>
          <span>{{ nombreTitular(v) }}</span>
        </footer>
        <div class="vehicle-actions" @click.stop>
          <button class="text-button" @click="openEdit(v)">Editar</button>
          <button v-if="auth.puedeAdministrar.value && v.registro?.activo" class="text-button" @click="openQr(v)">Gestionar QR</button>
          <NuxtLink v-if="v.qrCode" :to="`/qr/${v.qrCode}`" target="_blank" rel="noopener" class="text-button">Ficha pública</NuxtLink>

        </div>
      </article>
    </div>

    <!-- VISTA 2: TABLA (TABLE) -->
    <section v-else-if="viewMode === 'table' && filteredVehicles.length" class="panel table-scroll">
      <table>
        <thead>
          <tr>
            <th>PATENTE</th>
            <th>MARCA</th>
            <th>MODELO</th>
            <th>VERSIÓN</th>
            <th>COMBUSTIBLE</th>
            <th>AÑO</th>
            <th>QR</th>
            <th>ACCIONES</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="v in filteredVehicles" :key="v.id">
            <td><span class="plate">{{ v.plate }}</span></td>
            <td>{{ v.brand }}</td>
            <td>{{ v.model }}</td>
            <td>{{ vehicleVersion(v.version) || 'Sin registrar' }}</td>
            <td>{{ vehicleFuel(v.version) || 'Sin registrar' }}</td>
            <td>{{ v.year }}</td>
            <td>
                <span v-if="v.qrCode" class="badge neutral qr-present" style="font-size: 8.5px; padding: 2px 6px">
                  Con QR
                </span>
                <span v-else class="badge neutral" style="font-size: 8.5px; padding: 2px 6px">
                  Sin QR
                </span>
            </td>
            <td>
              <div class="vehicle-actions">
              <button class="text-button" @click="openDetail(v)">
                Ver ficha <ArrowUpRight :size="15" />
              </button>
              <button class="text-button" @click="openEdit(v)">Editar</button>
              <button v-if="auth.puedeAdministrar.value && v.registro?.activo" class="text-button" @click="openQr(v)">Gestionar QR</button>
              <NuxtLink v-if="v.qrCode" :to="`/qr/${v.qrCode}`" target="_blank" rel="noopener" class="text-button">Ficha pública</NuxtLink>

              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <div v-if="!cargando && !errorLista && !filteredVehicles.length" class="empty-state">
      <Search />
      <h3>No se encontraron vehículos</h3>
      <p>Probá con otra patente, modelo o cliente.</p>
    </div>

    <div v-if="!errorLista && totalPaginas > 1" class="toolbar-left-group pagination-controls">
      <button type="button" class="button" :disabled="cargando || pagina === 0" @click="cargarVehiculos(pagina - 1)">Anterior</button>
      <button type="button" class="button" :disabled="cargando || pagina + 1 >= totalPaginas" @click="cargarVehiculos(pagina + 1)">Siguiente</button>
    </div>

    <!-- Modals -->
    <VehiculosModalFormularioVehiculo
      :open="formModalOpen"
      :vehicle="selectedVehicle"
      @close="formModalOpen = false"
      @created="handleCreated"
      @updated="handleUpdated"
      @transfer="openTransfer"
    />

    <VehiculosModalDetalleVehiculo
      :open="detailModalOpen"
      :vehicle-id="detailVehicleId"
      :vehicle="selectedVehicle"
      :busy="cambiandoEstado"
      :error="errorEstado"
      @close="detailModalOpen = false"
      @edit="openEdit"
      @assign-qr="openQr"
      @change-status="cambiarEstado"
    />

    <QrVincularApi v-if="vincularModalOpen && selectedVehicle" :vehiculo-id="selectedVehicle.id" @close="vincularModalOpen = false" @saved="vincularModalOpen = false; cargarVehiculos(pagina)" />
    <VehiculosFormularioTitular v-if="transferOpen && selectedVehicle" :vehicle="selectedVehicle" @close="transferOpen = false" @saved="handleTransfer" />
  </div>
</template>

<style scoped>
.badge.qr-present { color: #334155; border: 1px solid #3b82f6; }
:global(html.dark .page-content .badge.qr-present) { color: #e4e4e7 !important; border-color: #3b82f6 !important; }
.vehicle-card { position: relative; cursor: pointer; }
.vehicle-card .section-heading { display: flex; align-items: center; }
.vehicle-card .section-heading > .badge { margin-inline-start: 20px; }
:global(html.dark .app-shell .vehicle-card) { background: #1c1c1e !important; }
.vehicle-card:focus-visible { outline: 2px solid var(--blue); outline-offset: 3px; }
.vehicle-card-brand {
  position: absolute;
  top: 50%;
  right: 16px;
  width: 160px;
  height: 160px;
  transform: translate(50%, -50%);
  opacity: 0.45;
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
.pagination-controls { margin-top: 20px; }

</style>
