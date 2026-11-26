<script setup lang="ts">
import type { PresupuestoResumenApi } from '~/utils/presupuestoApi'
import { Plus, FileText, Search, CalendarDays, Eye, LayoutGrid, Table, Archive, Pencil, X, Ban } from '@lucide/vue';
import type { Budget } from '~/types'
import { authError } from '~/composables/useAuthApi'
import { adaptarPresupuesto, adaptarResumenPresupuesto, type PresupuestoApi, type PresupuestoConDatos, type EstadoPresupuesto } from '~/utils/presupuestoApi';
import { adaptarVehiculo, adaptarTitular, type VehiculoApi, type ClienteTitularApi } from '~/utils/vehiculoApi'

const auth = useAuthApi()
const apiQuotes = ref<PresupuestoConDatos[]>([])
const loading = ref(false)
const error = ref('')
const busy = ref(false)
const revision = ref(0)
const pagina = ref(0)
const totalPaginas = ref(0)
const total = ref(0)
const editingBudget = ref<PresupuestoConDatos | null>(null)
const stateFilter = ref<EstadoPresupuesto | ''>('')
const stateCounts = ref<Record<string, number>>({})
const stateFilters: { value: EstadoPresupuesto | ''; label: string }[] = [
  { value: 'PENDIENTE', label: 'Pendientes' },
  { value: 'ACEPTADO', label: 'Aceptados' },
  { value: '', label: 'Todos' },
]
const appointmentBudget = ref<PresupuestoConDatos | null>(null)
const pendingState = ref<{ budget: PresupuestoConDatos; estado: EstadoPresupuesto } | null>(null)
const vehicle = (q: Budget) => ({ plate: registro(q).patente })
const vehicleName = (q: Budget) => registro(q).vehiculo
const owner = (q: Budget) => ({ name: registro(q).titular })
const registro = (q: Budget) => (q as PresupuestoConDatos).registro
const { money, statusClass, matches } = useHelpers()
const { notify } = useWorkshopToast()

const route = useRoute()
const search = ref('')
const debouncedSearch = useDebouncedValue(search)
const budgetSection = ref<'activos' | 'archivados'>('activos')
const viewMode = useListView('presupuestos', 'cards', ['table', 'cards'] as const)
const formModalOpen = ref(false)
const shareModalOpen = ref(false)
const selectedBudget = ref<Budget | null>(null)
const autoDownloadForModal = ref(false)

watch([debouncedSearch, budgetSection, stateFilter], () => { pagina.value = 0 })
watch([auth.authorization, debouncedSearch, budgetSection, stateFilter, pagina, revision], async ([headers], _, onCleanup) => {
  const controller = new AbortController()
  const { signal } = controller
  onCleanup(() => controller.abort())
  apiQuotes.value = []
  error.value = ''
  loading.value = !!headers
  if (!headers) return
  try {
    const result = await $fetch<{ contenido: PresupuestoResumenApi[]; totalPaginas: number; totalElementos: number }>('/api/presupuestos', {
      headers, signal, query: {
        q: debouncedSearch.value.trim(), pagina: pagina.value, tamano: 20,
        ...(budgetSection.value === 'archivados' ? { estado: 'ANULADO' } : stateFilter.value ? { estado: stateFilter.value } : {}),
      },
    })
    if (!signal.aborted) {
      if (pagina.value > 0 && !result.contenido.length) { pagina.value = Math.max(0, result.totalPaginas - 1); return }
      apiQuotes.value = result.contenido.map(adaptarResumenPresupuesto)
      totalPaginas.value = result.totalPaginas
      total.value = result.totalElementos
    }
  } catch (err) {
    if (!signal.aborted) error.value = authError(err, 'No se pudieron cargar los presupuestos.')
  } finally {
    if (!signal.aborted) loading.value = false
  }
}, { immediate: true })

watch([auth.authorization, debouncedSearch, revision], async ([headers], _, onCleanup) => {
  stateCounts.value = {}
  if (!headers) return
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  try {
    const entries = await Promise.all(stateFilters.map(async (filtro) => {
      const result = await $fetch<{ totalElementos: number }>('/api/presupuestos', {
        headers, signal: controller.signal,
        query: { q: debouncedSearch.value.trim(), tamano: 1, ...(filtro.value ? { estado: filtro.value } : {}) },
      })
      return [filtro.value, Number(result.totalElementos)] as const
    }))
    if (!controller.signal.aborted) stateCounts.value = Object.fromEntries(entries)
  } catch { /* Los contadores del filtro son opcionales. */ }
}, { immediate: true })

function newBudget() {
  editingBudget.value = null
  formModalOpen.value = true
}

async function editBudget(q: Budget) {
  if (busy.value || !auth.authorization.value) return
  busy.value = true
  error.value = ''
  try {
    const item = await $fetch<PresupuestoApi>(`/api/presupuestos/${q.id}`, { headers: auth.authorization.value })
    if (item.estado !== 'PENDIENTE' || item.turnoId || item.ordenId) {
      error.value = 'Solo se puede editar un presupuesto pendiente y sin turno u orden activa.'
      revision.value++
      return
    }
    const presupuesto = adaptarPresupuesto(item)
    // El titular del presupuesto puede no coincidir con el titular actual del vehículo.
    try {
      const cliente = await $fetch<ClienteTitularApi & { calle?: string; altura?: number }>(`/api/clientes/${item.clienteId}`, { headers: auth.authorization.value })
      presupuesto.cliente = {
        ...adaptarTitular(cliente),
        address: [cliente.calle, cliente.altura].filter(value => value != null && value !== '').join(' '),
      }
    } catch {
      // El nombre guardado en el presupuesto alcanza para mostrarlo en la edición.
    }
    editingBudget.value = presupuesto
    formModalOpen.value = true
  } catch (err) {
    error.value = authError(err, 'No se pudo cargar el presupuesto.')
  } finally { busy.value = false }
}

async function changeState() {
  const change = pendingState.value
  if (!change || !auth.authorization.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await $fetch(`/api/presupuestos/${change.budget.id}/estado`, {
      method: 'PATCH', headers: auth.authorization.value, body: { estado: change.estado },
    })
    pendingState.value = null
    revision.value++
    notify('Estado del presupuesto actualizado.')
  } catch (err) {
    error.value = authError(err, 'No se pudo cambiar el estado del presupuesto.')
  } finally { busy.value = false }
}

function requestState(q: Budget, estado: EstadoPresupuesto) {
  error.value = ''
  pendingState.value = { budget: q as PresupuestoConDatos, estado }
}

function canEdit(q: Budget) {
  return registro(q).estado === 'PENDIENTE' && !q.appointmentId && !q.orderId
}

function canSchedule(q: Budget) {
  return ['PENDIENTE', 'ACEPTADO'].includes(registro(q).estado) && !q.appointmentId && !q.orderId
}

function appointmentSaved() {
  appointmentBudget.value = null
  revision.value++
  notify('Turno agendado.')
}

onMounted(() => {
  if (route.query.nuevo === '1' || route.query.nuevo === 'true') {
    formModalOpen.value = true
  }
})

watch(() => route.query.nuevo, (v) => {
  if (v === '1' || v === 'true') {
    formModalOpen.value = true
  }
})

const archivedQuotes = computed(() =>
  apiQuotes.value.filter(q => q.registro.estado === 'ANULADO')
)
const activeQuotes = computed(() =>
  apiQuotes.value.filter(q => q.registro.estado !== 'ANULADO')
)

const filteredQuotes = computed(() =>
  budgetSection.value === 'archivados' ? archivedQuotes.value : activeQuotes.value
)

function getSubtotal(q: Budget): number {
  return budgetAmounts(q).subtotal
}

function getTax(q: Budget): number {
  return budgetAmounts(q).tax
}

function getTotalWithTax(q: Budget): number {
  return budgetAmounts(q).total
}

function handleCreated(budget: Budget) {
  pagina.value = 0
  budgetSection.value = 'activos'
  stateFilter.value = ''
  formModalOpen.value = false
  selectedBudget.value = null
  autoDownloadForModal.value = false
  shareModalOpen.value = false
  search.value = ''
  editingBudget.value = null
  revision.value++
  notify(`Presupuesto #${budget.id} generado exitosamente.`)
}

function handleUpdated(budget: Budget) {
  formModalOpen.value = false
  editingBudget.value = null
  revision.value++
  notify(`Presupuesto #${budget.id} actualizado.`)
}

async function openViewBudget(q: Budget) {
  if (auth.authorization.value) {
    if (busy.value) return
    busy.value = true
    error.value = ''
    try {
      const headers = auth.authorization.value
      const item = await $fetch<PresupuestoApi>(`/api/presupuestos/${q.id}`, { headers })
      const [client, car, taller] = await Promise.all([
        $fetch<ClienteTitularApi & { calle?: string; altura?: number }>(`/api/clientes/${item.clienteId}`, { headers }),
        $fetch<VehiculoApi>(`/api/vehiculos/${item.vehiculoId}`, { headers }),
        $fetch<import('~/utils/presupuestoApi').TallerPresupuesto>(`/api/talleres/${auth.user.value!.tallerId}`, { headers }),
      ])
      const budget = adaptarPresupuesto(item)
      budget.cliente = { ...adaptarTitular(client), address: [client.calle, client.altura].filter(value => value != null && value !== '').join(' ') }
      budget.vehiculo = adaptarVehiculo(car)
      budget.taller = taller
      selectedBudget.value = budget
      autoDownloadForModal.value = false
      shareModalOpen.value = true
    } catch (err) {
      error.value = authError(err, 'No se pudo cargar el detalle del presupuesto.')
    } finally { busy.value = false }
    return
  }
  selectedBudget.value = q
  autoDownloadForModal.value = false
  shareModalOpen.value = true
}

function openShare(q: Budget) {
  openViewBudget(q)
}

function turnoPendiente(q: Budget) {
  const datos = registro(q)
  if (!datos.turnoFecha || !datos.turnoHora) return false
  const ahora = new Date().toLocaleString('sv-SE', { timeZone: 'America/Argentina/Buenos_Aires' })
  return datos.turnoFecha + ' ' + datos.turnoHora.slice(0, 5) > ahora.slice(0, 16)
}

function soloDetalle(q: Budget) {
  return !canEdit(q) && !canSchedule(q) && !['PENDIENTE', 'ACEPTADO'].includes(registro(q).estado)
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          {{ auth.workshopName.value || 'TU TALLER' }} / PRESUPUESTOS
        </div>
        <h1>Claridad antes de empezar.</h1>
      </div>
      <button class="button primary" @click="newBudget">
        <Plus :size="17" />Nuevo presupuesto
      </button>
    </section>

    <div class="budget-sections">
      <div class="segmented" aria-label="Apartados de presupuestos">
        <button
          type="button"
          :class="{ selected: budgetSection === 'activos' }"
          :aria-pressed="budgetSection === 'activos'"
          @click="budgetSection = 'activos'"
        >
          <FileText :size="15" /> Activos
        </button>
        <button
          type="button"
          :class="{ selected: budgetSection === 'archivados' }"
          :aria-pressed="budgetSection === 'archivados'"
          @click="budgetSection = 'archivados'"
        >
          <Archive :size="15" /> {{ 'Anulados' }}
        </button>
      </div>

    </div>

    <div class="list-toolbar">
      <label class="search-box">
        <Search :size="17" />
        <input
          v-model="search"
          placeholder="Buscar por auto o cliente"
          aria-label="Buscar presupuestos"
          maxlength="250"
        />
      </label>
      <div class="toolbar-left-group">
        <span class="muted">{{ total }} presupuestos</span>
        <div v-if="budgetSection === 'activos'" class="segmented status-filters" role="group" aria-label="Filtrar por estado">
          <button
            v-for="filtro in stateFilters"
            :key="filtro.value"
            type="button"
            :class="{ selected: stateFilter === filtro.value }"
            :aria-pressed="stateFilter === filtro.value"
            @click="stateFilter = filtro.value"
          >
            {{ filtro.label }}
            <span v-if="stateCounts[filtro.value] > 0" class="status-count">{{ stateCounts[filtro.value] }}</span>
          </button>
        </div>
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
    <p v-if="loading" class="muted" role="status">Cargando presupuestos…</p>
    <p v-if="error && !pendingState" class="error-message" role="alert">{{ error }} <button type="button" class="button small" @click="revision++">Reintentar</button></p>
    <div v-if="!loading && viewMode === 'cards'" class="quote-grid">
      <article
        v-for="q in filteredQuotes"
        :key="q.id"
        class="panel quote-card"
        :class="{ 'quote-card-viewable': soloDetalle(q) }"
        :tabindex="soloDetalle(q) ? 0 : undefined"
        :role="soloDetalle(q) ? 'button' : undefined"
        @click="soloDetalle(q) && openViewBudget(q)"
        @keydown.enter.self="soloDetalle(q) && openViewBudget(q)"
        @keydown.space.self.prevent="soloDetalle(q) && openViewBudget(q)"
      >
        <div class="section-heading">
          <span class="eyebrow">PRESUPUESTO #{{ q.id }}</span>
          <span :class="['badge', budgetSection === 'archivados' || q.status === 'Rechazado' ? 'anulado' : q.status === 'Pendiente' ? 'neutral' : statusClass(q.status)]">
            {{ q.status === 'Rechazado' || q.status === 'Anulado' ? 'Anulado' : q.status }}
          </span>
        </div>
        <h2>{{ vehicleName(q) }}</h2>
        <p>{{ owner(q)?.name }} · {{ vehicle(q)?.plate }}</p>
        <h3>{{ q.description }}</h3>

        <!-- Financial Breakdown Lines -->
        <div class="quote-line">
          <span>Mano de obra</span>
          <strong>{{ money(q.labor) }}</strong>
        </div>
        <div class="quote-line">
          <span>Repuestos e insumos</span>
          <strong>{{ money(q.materials) }}</strong>
        </div>
        <div class="quote-line subtotal-line-clean">
          <span>Subtotal</span>
          <strong>{{ money(getSubtotal(q)) }}</strong>
        </div>
        <div class="quote-line vat-line-clean">
          <span>IVA ({{ registro(q).porcentajeIva }}%)</span>
          <strong>{{ money(getTax(q)) }}</strong>
        </div>
        <div class="quote-total">
          <span>Total estimado</span>
          <strong>{{ money(getTotalWithTax(q)) }}</strong>
        </div>

        <!-- Assigned Appointment Indicator if any -->
        <NuxtLink v-if="q.appointmentId && turnoPendiente(q)" class="budget-appointment-pill" to="/agenda"><CalendarDays :size="13" /> Turno: {{ registro(q).turnoFecha?.split('-').reverse().join('/') }} a las {{ registro(q).turnoHora?.slice(0, 5) }} hs</NuxtLink>
        <p v-if="q.orderId" class="muted">Orden #{{ q.orderId }}</p>

        <!-- Action Buttons -->
        <p v-if="soloDetalle(q)" class="budget-detail-hint quote-detail-hint">Click para ver detalle</p>
        <div v-else class="quote-actions-row">
          <button class="button outlined quote-action-btn" @click="openViewBudget(q)">
            <Eye :size="14" /> Ver detalle
          </button>

            <button v-if="canEdit(q)" type="button" class="button small" :disabled="busy" @click="editBudget(q)"><Pencil :size="14" /> Editar</button>
            <button v-if="canSchedule(q)" type="button" class="button small" :disabled="busy" @click="appointmentBudget = q as PresupuestoConDatos"><CalendarDays :size="14" /> Agendar turno</button>
            <button v-if="registro(q).estado === 'PENDIENTE'" type="button" class="button small" :disabled="busy" @click="requestState(q, 'RECHAZADO')">Rechazar</button>
            <button v-if="registro(q).estado === 'ACEPTADO'" type="button" class="button small danger" :disabled="busy" @click="requestState(q, 'ANULADO')">Anular</button>

        </div>
      </article>
    </div>

    <!-- VISTA 2: TABLA (TABLE) -->
    <section v-else-if="!loading && viewMode === 'table' && filteredQuotes.length" class="panel budget-table">
      <table>
        <thead>
          <tr>
            <th>NRO</th>
            <th>ESTADO</th>
            <th>VEHÍCULO</th>
            <th>CLIENTE</th>
            <th>TRABAJO</th>
            <th>TOTAL</th>
            <th>ACCIONES</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="q in filteredQuotes" :key="q.id">
            <td>
              <div class="table-budget-meta">
                <strong>#{{ q.id }}</strong>

              </div>
            </td>
            <td>
              <span :class="['badge', budgetSection === 'archivados' || q.status === 'Rechazado' ? 'anulado' : q.status === 'Pendiente' ? 'neutral' : statusClass(q.status)]" style="font-size: 8.5px; padding: 2px 6px">
                {{ q.status === 'Rechazado' || q.status === 'Anulado' ? 'Anulado' : q.status }}
              </span>
            </td>
            <td>
              <div class="table-vehicle-cell">
                <strong>{{ vehicleName(q) }}</strong>
                <span class="plate">{{ vehicle(q)?.plate }}</span>
              </div>
            </td>
            <td>{{ owner(q)?.name }}</td>
            <td>
              <div class="table-work-desc">{{ q.description }}</div>
            </td>
            <td>
              <strong class="table-total-amt">{{ money(getTotalWithTax(q)) }}</strong>
            </td>
            <td>
              <div class="table-row-actions">
                <button
                  type="button"
                  class="icon-button outlined"
                  title="Ver detalle del presupuesto"
                  aria-label="Ver detalle del presupuesto"
                  @click="openShare(q)"
                >
                  <Eye :size="14" />
                </button>

                  <button v-if="canEdit(q)" type="button" class="icon-button outlined" title="Editar presupuesto" aria-label="Editar presupuesto" :disabled="busy" @click="editBudget(q)"><Pencil :size="14" /></button>
                  <button v-if="canSchedule(q)" type="button" class="icon-button outlined" title="Agendar turno" aria-label="Agendar turno" :disabled="busy" @click="appointmentBudget = q as PresupuestoConDatos"><CalendarDays :size="14" /></button>
                  <button v-if="registro(q).estado === 'PENDIENTE'" type="button" class="icon-button outlined table-action-danger" title="Rechazar presupuesto" aria-label="Rechazar presupuesto" :disabled="busy" @click="requestState(q, 'RECHAZADO')"><X :size="14" /></button>
                  <button v-if="registro(q).estado === 'ACEPTADO'" type="button" class="icon-button outlined table-action-danger" title="Anular presupuesto" aria-label="Anular presupuesto" :disabled="busy" @click="requestState(q, 'ANULADO')"><Ban :size="14" /></button>

              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <div v-if="!loading && !error && !filteredQuotes.length" class="empty-state">
      <FileText :size="38" class="muted" />
      <h3>{{ budgetSection === 'archivados' ? 'No se encontraron presupuestos anulados' : 'No se encontraron presupuestos activos' }}</h3>
      <p v-if="search">Probá con otra búsqueda.</p>
      <p v-else>Creá un presupuesto nuevo para empezar.</p>
    </div>

    <!-- Create Modal -->
    <div v-if="totalPaginas > 1" class="toolbar-left-group" aria-label="Paginación de presupuestos">
      <button class="button small" :disabled="loading || pagina === 0" @click="pagina--">Anterior</button>
      <span>{{ pagina + 1 }} / {{ totalPaginas }}</span>
      <button class="button small" :disabled="loading || pagina + 1 >= totalPaginas" @click="pagina++">Siguiente</button>
    </div>

    <PresupuestosModalPresupuesto
      :open="formModalOpen"
      :budget="editingBudget"
      @close="formModalOpen = false"
      @created="handleCreated"
      @updated="handleUpdated"
    />

    <!-- Share & Print Modal -->
    <PresupuestosModalCompartirPresupuesto
      :open="shareModalOpen"
      :budget="selectedBudget"
      :auto-download="autoDownloadForModal"
      @close="shareModalOpen = false"
    />
    <AgendaModalTurno :open="!!appointmentBudget" :budget="appointmentBudget" @close="appointmentBudget = null" @created="appointmentSaved" />
    <CommonModalDialog v-if="pendingState" class="dialog" role="alertdialog" aria-label="Confirmar cambio de estado" @close="!busy && (pendingState = null)">
      <div class="dialog-header"><h2>Cambiar estado del presupuesto #{{ pendingState.budget.id }}</h2></div>
      <div class="form-fields">
        <p>¿Confirmás pasar el presupuesto a {{ { ACEPTADO: 'aceptado', RECHAZADO: 'rechazado', ANULADO: 'anulado', PENDIENTE: 'pendiente' }[pendingState.estado] }}?</p>
        <p v-if="['ANULADO', 'RECHAZADO'].includes(pendingState.estado)" class="muted">Se cancelarán el turno y la orden pendientes de ingreso vinculados. El historial se conserva.</p>
        <p v-if="error" class="error-message" role="alert">{{ error }}</p>
      </div>
      <footer class="dialog-footer">
        <button type="button" class="button" :disabled="busy" @click="pendingState = null; error = ''">Volver</button>
        <button type="button" class="button primary" :disabled="busy" @click="changeState">Confirmar</button>
      </footer>
    </CommonModalDialog>

  </div>
</template>

<style scoped>
.quote-grid {
  grid-template-columns: repeat(auto-fill, minmax(min(290px, 100%), 1fr));
  gap: 14px;
}

.quote-card {
  padding: 14px;
  border-radius: 12px;
}

.quote-card h2 {
  margin: 10px 0 4px;
  font-size: 16px;
}

.quote-card > p {
  margin: 0;
}

.quote-card > h3 {
  margin: 12px 0 6px;
  font-size: 12px;
}

.quote-card .quote-line {
  padding: 6px 0;
  font-size: 11px;
}

.quote-card .quote-total {
  padding: 10px 0;
}

.quote-card .quote-total strong {
  font-size: 18px;
}

.quote-card .quote-actions-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  margin-top: 8px;
}

.quote-card-viewable { cursor: pointer; }
.quote-detail-hint { text-align: center; }

.quote-card .quote-actions-row .button {
  min-height: 32px;
  padding: 7px 8px;
  font-size: 10px;
}

.quote-card .budget-appointment-pill {
  margin-top: 6px;
  padding: 6px 8px;
  font-size: 11px;
}

.budget-sections {
  margin-bottom: 18px;
}

.budget-sections .segmented {
  flex-wrap: wrap;
}

.list-toolbar {
  flex-wrap: wrap;
}

.status-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: rgba(100, 116, 139, 0.16);
  font-weight: 700;
}

.quote-card .badge.anulado,
.budget-table .badge.anulado {
  color: #dc2626 !important;
}

:global(html.dark .quote-card .badge.anulado),
:global(html.dark .budget-table .badge.anulado) {
  color: #ff6b6b !important;
}

.budget-sections p {
  margin: 10px 0 0;
  font-size: 13px;
}

.delete-budget-dialog {
  width: 420px;
}

.delete-budget-content {
  padding: 24px;
}

.delete-budget-content h2 {
  margin: 0 0 12px;
  font-size: 18px;
}

.delete-budget-content p {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.5;
}

.delete-budget-dialog .dialog-footer {
  padding: 16px 24px;
  flex-wrap: wrap;
}

.button.delete-confirm-btn {
  background: #dc2626 !important;
  border-color: #dc2626 !important;
  color: #ffffff !important;
}

.button.delete-confirm-btn:hover {
  background: #b91c1c !important;
  border-color: #b91c1c !important;
}

.quote-card .badge.neutral,
.budget-table .badge.neutral {
  background: #f4f4f5;
  color: #52525b;
}

.quote-actions-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.button.quote-delete-btn {
  color: #dc2626;
}

:global(html.dark) .button.quote-delete-btn {
  color: #f87171;
}

.quote-action-btn {
  flex: 1.3;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.budget-appointment-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding: 6px 10px;
  background: transparent;
  border: 0;
  border-radius: 8px;
  font-size: 12px;
  color: #52525b;
}

.pill-icon {
  color: inherit;
  flex-shrink: 0;
}

:global(html.dark .budget-appointment-pill) {
  background: transparent;
  border: 0;
  color: #a1a1aa;
}

:global(html.dark .pill-icon) {
  color: inherit;
}

.subtotal-line-clean {
  color: #334155;
  font-weight: 550;
}

.vat-line-clean {
  color: #2563eb;
  font-weight: 550;
}

:global(html.dark .subtotal-line-clean) {
  color: #d1d1d6;
}

:global(html.dark .vat-line-clean) {
  color: #64d2ff;
}

.toolbar-left-group {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.table-budget-meta {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 4px;
}

.table-subtext {
  font-size: 9.5px;
  display: block;
}

.table-vehicle-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
}

.table-vehicle-cell small {
  margin-top: 2px;
}

.table-work-desc {
  max-width: 260px;
  font-size: 11px;
  color: #334155;
  line-height: 1.4;
  word-break: break-word;
}

:global(html.dark .table-work-desc) {
  color: #cbd5e1;
}

.table-total-amt {
  font-size: 12px;
  color: #0f172a;
}

:global(html.dark .table-total-amt) {
  color: #f8fafc;
}

.table-row-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 5px;
}
.budget-table { min-width: 0; width: 100%; }
.budget-table table { width: 100%; min-width: 0; table-layout: fixed; }
.budget-table th, .budget-table td { padding: 12px 8px; white-space: normal; overflow-wrap: anywhere; vertical-align: middle; }
.budget-table th { font-size: 9px; }
.budget-table td { font-size: 11px; }
.budget-table th:nth-child(1) { width: 12%; }
.budget-table th:nth-child(2) { width: 10%; }
.budget-table th:nth-child(3) { width: 18%; }
.budget-table th:nth-child(4) { width: 14%; }
.budget-table th:nth-child(5) { width: 19%; }
.budget-table th:nth-child(6) { width: 10%; }
.budget-table th:nth-child(7) { width: 17%; }
.budget-table .plate { max-width: 100%; padding: 2px 5px; font-size: 8px; white-space: nowrap; }
.budget-table .table-work-desc { max-width: none; }
.budget-table .table-row-actions .icon-button { width: 29px; height: 29px; }
.budget-table .table-action-danger { color: #dc2626; }
:global(html.dark) .table-action-danger { color: #f87171; }
.budget-table .badge { white-space: normal; }
</style>
