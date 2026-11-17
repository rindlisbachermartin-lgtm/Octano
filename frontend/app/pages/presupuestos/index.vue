<script setup lang="ts">
import {
  Plus,
  Download,
  FileText,
  Search,
  CalendarDays,
  Eye,
  LayoutGrid,
  Table,
  Trash2,
  Archive,
  Pencil,
} from '@lucide/vue'
import type { Budget } from '~/types'
import { authError } from '~/composables/useAuthApi'
import { adaptarPresupuesto, paginasPresupuestos, type PresupuestoApi, type PresupuestoResumenApi, type PresupuestoConDatos, type EstadoPresupuesto } from '~/utils/presupuestoApi'
import { adaptarVehiculo, adaptarTitular, type VehiculoApi, type ClienteTitularApi } from '~/utils/vehiculoApi'

const { db, vehicle: demoVehicle, vehicleName: demoVehicleName, owner: demoOwner, cancelAppointmentOrder } = useDatabase()
const auth = useAuthApi()
const connected = computed(() => !!auth.authorization.value)
const apiQuotes = ref<PresupuestoConDatos[]>([])
const loading = ref(false)
const error = ref('')
const busy = ref(false)
const revision = ref(0)
const editingBudget = ref<PresupuestoConDatos | null>(null)
const stateFilter = ref<EstadoPresupuesto | ''>('')
const appointmentBudget = ref<PresupuestoConDatos | null>(null)
const pendingState = ref<{ budget: PresupuestoConDatos; estado: EstadoPresupuesto } | null>(null)
const quoteByVehicle = (id: number) => apiQuotes.value.find(q => q.vehicle === id)
const vehicle = (id: number) => connected.value ? { plate: quoteByVehicle(id)?.registro.patente } : demoVehicle(id)
const vehicleName = (id: number) => connected.value ? quoteByVehicle(id)?.registro.vehiculo ?? '' : demoVehicleName(id)
const owner = (id: number) => connected.value ? { name: quoteByVehicle(id)?.registro.titular } : demoOwner(id)
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
const budgetToDelete = ref<Budget | null>(null)
const appointmentsToDelete = computed(() => {
  const budget = db.value.quotes.find((q) => q.id === budgetToDelete.value?.id)
  return budget ? getAppointmentsForBudget(budget) : []
})

watch([auth.authorization, debouncedSearch, budgetSection, stateFilter, revision], async ([headers], _, onCleanup) => {
  const controller = new AbortController()
  const { signal } = controller
  onCleanup(() => controller.abort())
  apiQuotes.value = []
  error.value = ''
  loading.value = !!headers
  if (!headers) return
  try {
    const summaries = await paginasPresupuestos<PresupuestoResumenApi>('/api/presupuestos', headers, signal, {
      q: debouncedSearch.value.trim(), ...(budgetSection.value === 'archivados' ? { estado: 'ANULADO' } : stateFilter.value ? { estado: stateFilter.value } : {}),
    })
    const details: PresupuestoApi[] = []
    for (let offset = 0; offset < summaries.length && !signal.aborted; offset += 10) {
      details.push(...await Promise.all(summaries.slice(offset, offset + 10).map(item =>
        $fetch<PresupuestoApi>(`/api/presupuestos/${item.id}`, { headers, signal }),
      )))
    }
    if (!signal.aborted) apiQuotes.value = details.map(adaptarPresupuesto)
  } catch (err) {
    if (!signal.aborted) error.value = authError(err, 'No se pudieron cargar los presupuestos.')
  } finally {
    if (!signal.aborted) loading.value = false
  }
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
    editingBudget.value = adaptarPresupuesto(item)
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
  return connected.value && registro(q).estado === 'PENDIENTE' && !q.appointmentId && !q.orderId
}

function canSchedule(q: Budget) {
  return connected.value && ['PENDIENTE', 'ACEPTADO'].includes(registro(q).estado) && !q.appointmentId && !q.orderId
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
  connected.value ? apiQuotes.value.filter(q => q.registro.estado === 'ANULADO') : db.value.quotes.filter((q) => isBudgetArchived(q, db.value.invoices))
)
const activeQuotes = computed(() =>
  connected.value ? apiQuotes.value.filter(q => q.registro.estado !== 'ANULADO') : db.value.quotes.filter((q) => !isBudgetArchived(q, db.value.invoices))
)

const filteredQuotes = computed(() =>
  (budgetSection.value === 'archivados' ? archivedQuotes.value : activeQuotes.value).filter((q) => {
    if (connected.value) return true
    const v = vehicle(q.vehicle)
    const o = owner(q.vehicle)
    return matches(
      debouncedSearch.value,
      q.id,
      q.description,
      v?.plate,
      v?.brand,
      v?.model,
      o?.name
    )
  })
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
  if (connected.value && auth.authorization.value) {
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

function openDownloadBudget(q: Budget) {
  selectedBudget.value = q
  autoDownloadForModal.value = true
  shareModalOpen.value = true
}

function openShare(q: Budget) {
  openViewBudget(q)
}

function getAppointmentForBudget(q: Budget) {
  if (connected.value) return undefined
  if (q.appointmentId) {
    return db.value.appointments.find((a) => a.id === q.appointmentId)
  }
  return db.value.appointments.find((a) => a.budgetId === q.id && !['Cancelado', 'No asistió'].includes(a.status))
}

function getAppointmentsForBudget(q: Budget) {
  return db.value.appointments.filter((appointment) =>
    appointment.budgetId === q.id || appointment.id === q.appointmentId
  )
}

function canDeleteBudget(q: Budget): boolean {
  if (connected.value) return false
  const order = db.value.orders.find((o) => o.id === q.orderId)
  return (!q.orderId || order?.status === 'Pendiente de ingreso') && !['Convertido', 'En taller', 'Archivado'].includes(q.status)
}

function requestDeleteBudget(q: Budget) {
  if (!canDeleteBudget(q)) return
  budgetToDelete.value = q
}

function cancelDeleteBudget() {
  budgetToDelete.value = null
}

function confirmDeleteBudget() {
  const q = db.value.quotes.find((budget) => budget.id === budgetToDelete.value?.id)
  if (!q) return
  if (!canDeleteBudget(q)) {
    cancelDeleteBudget()
    notify('No se puede eliminar un presupuesto que ya se convirtió en una orden de trabajo.')
    return
  }

  const appointmentIds = new Set(getAppointmentsForBudget(q).map((appointment) => appointment.id))
  getAppointmentsForBudget(q).forEach(cancelAppointmentOrder)
  db.value.appointments = db.value.appointments.filter((appointment) => !appointmentIds.has(appointment.id))
  db.value.quotes = db.value.quotes.filter((budget) => budget.id !== q.id)
  if (selectedBudget.value?.id === q.id) {
    shareModalOpen.value = false
    selectedBudget.value = null
  }
  cancelDeleteBudget()
  notify(appointmentIds.size
    ? `Presupuesto #${q.id} y ${appointmentIds.size === 1 ? 'su turno eliminados' : 'sus turnos eliminados'}.`
    : `Presupuesto #${q.id} eliminado.`)
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          {{ connected ? auth.workshopName.value || 'TU TALLER' : 'TALLER CENTRAL' }} / PRESUPUESTOS
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
          <FileText :size="15" /> Activos<span v-if="!connected"> ({{ activeQuotes.length }})</span>
        </button>
        <button
          type="button"
          :class="{ selected: budgetSection === 'archivados' }"
          :aria-pressed="budgetSection === 'archivados'"
          @click="budgetSection = 'archivados'"
        >
          <Archive :size="15" /> {{ connected ? 'Anulados' : 'Archivados' }}<span v-if="!connected"> ({{ archivedQuotes.length }})</span>
        </button>
      </div>
      <p v-if="!connected && budgetSection === 'archivados'" class="muted">Presupuestos cuya factura ya fue pagada.</p>
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
      <select v-if="connected && budgetSection === 'activos'" v-model="stateFilter" aria-label="Estado del presupuesto">
        <option value="">Todos los estados</option>
        <option value="PENDIENTE">Pendientes</option>
        <option value="ACEPTADO">Aceptados</option>
        <option value="RECHAZADO">Rechazados</option>
      </select>
      <div class="toolbar-left-group">
        <span class="muted">{{ filteredQuotes.length }} presupuestos</span>
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
      >
        <div class="section-heading">
          <span class="eyebrow">PRESUPUESTO #{{ q.id }}</span>
          <span :class="['badge', budgetSection === 'archivados' || q.status === 'Pendiente' ? 'neutral' : statusClass(q.status)]">
            {{ !connected && budgetSection === 'archivados' ? 'Archivado' : q.status }}
          </span>
        </div>
        <h2>{{ vehicleName(q.vehicle) }}</h2>
        <p>{{ owner(q.vehicle)?.name }} · {{ vehicle(q.vehicle)?.plate }}</p>
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
          <span>IVA ({{ connected ? registro(q).porcentajeIva : 21 }}%)</span>
          <strong>{{ money(getTax(q)) }}</strong>
        </div>
        <div class="quote-total">
          <span>Total estimado</span>
          <strong>{{ money(getTotalWithTax(q)) }}</strong>
        </div>

        <!-- Assigned Appointment Indicator if any -->
        <div v-if="getAppointmentForBudget(q)" class="budget-appointment-pill">
          <CalendarDays :size="13" class="pill-icon" />
          <span>Turno agendado: <strong>{{ getAppointmentForBudget(q)?.date }} a las {{ getAppointmentForBudget(q)?.time }} hs</strong></span>
        </div>
        <NuxtLink v-if="connected && q.appointmentId" class="budget-appointment-pill" to="/agenda"><CalendarDays :size="13" /> Turno #{{ q.appointmentId }}</NuxtLink>
        <p v-if="connected && q.orderId" class="muted">Orden #{{ q.orderId }}</p>

        <!-- Action Buttons -->
        <div class="quote-actions-row">
          <button class="button outlined quote-action-btn" @click="openViewBudget(q)">
            <Eye :size="14" /> Ver detalle
          </button>
          <template v-if="connected">
            <button v-if="canEdit(q)" type="button" class="button small" :disabled="busy" @click="editBudget(q)"><Pencil :size="14" /> Editar</button>
            <button v-if="canSchedule(q)" type="button" class="button small" :disabled="busy" @click="appointmentBudget = q as PresupuestoConDatos"><CalendarDays :size="14" /> Agendar turno</button>
            <button v-if="registro(q).estado === 'PENDIENTE'" type="button" class="button small" :disabled="busy" @click="requestState(q, 'ACEPTADO')">Aceptar</button>
            <button v-if="registro(q).estado === 'PENDIENTE'" type="button" class="button small" :disabled="busy" @click="requestState(q, 'RECHAZADO')">Rechazar</button>
            <button v-if="registro(q).estado !== 'ANULADO'" type="button" class="button small danger" :disabled="busy" @click="requestState(q, 'ANULADO')">Anular</button>
          </template>
          <button
            v-if="canDeleteBudget(q)"
            type="button"
            class="button outlined quote-delete-btn"
            :aria-label="`Eliminar presupuesto #${q.id}`"
            @click="requestDeleteBudget(q)"
          >
            <Trash2 :size="14" /> Eliminar
          </button>
        </div>
      </article>
    </div>

    <!-- VISTA 2: TABLA (TABLE) -->
    <section v-else-if="!loading && viewMode === 'table' && filteredQuotes.length" class="panel table-scroll">
      <table>
        <thead>
          <tr>
            <th>PRESUPUESTO</th>
            <th>VEHÍCULO / CLIENTE</th>
            <th>TRABAJO / DESCRIPCIÓN</th>
            <th>MANO DE OBRA</th>
            <th>REPUESTOS</th>
            <th>SUBTOTAL</th>
            <th>IVA</th>
            <th>TOTAL ESTIMADO</th>
            <th>ACCIONES</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="q in filteredQuotes" :key="q.id">
            <td>
              <div class="table-budget-meta">
                <strong>#{{ q.id }}</strong>
                <span :class="['badge', budgetSection === 'archivados' || q.status === 'Pendiente' ? 'neutral' : statusClass(q.status)]" style="font-size: 8.5px; padding: 2px 6px">
                  {{ !connected && budgetSection === 'archivados' ? 'Archivado' : q.status }}
                </span>
                <small v-if="getAppointmentForBudget(q)" class="muted table-subtext">
                  Turno: {{ getAppointmentForBudget(q)?.date }} {{ getAppointmentForBudget(q)?.time }}hs
                </small>
              </div>
            </td>
            <td>
              <div class="table-vehicle-cell">
                <strong>{{ vehicleName(q.vehicle) }}</strong>
                <small>{{ vehicle(q.vehicle)?.plate }} · {{ owner(q.vehicle)?.name }}</small>
              </div>
            </td>
            <td>
              <div class="table-work-desc">{{ q.description }}</div>
            </td>
            <td>
              <span>{{ money(q.labor) }}</span>
            </td>
            <td>
              <span>{{ money(q.materials) }}</span>
              <small v-if="q.items && q.items.length" class="muted" style="display: block; font-size: 9px">
                {{ q.items.length }} {{ q.items.length === 1 ? 'ítem' : 'ítems' }}
              </small>
            </td>
            <td>
              <span>{{ money(getSubtotal(q)) }}</span>
            </td>
            <td>
              <span>{{ money(getTax(q)) }}</span>
            </td>
            <td>
              <strong class="table-total-amt">{{ money(getTotalWithTax(q)) }}</strong>
            </td>
            <td>
              <div class="table-row-actions">
                <button
                  class="button small"
                  title="Ver detalle del presupuesto"
                  @click="openShare(q)"
                >
                  <Eye :size="13" /> Ver detalle
                </button>
                <template v-if="connected">
                  <button v-if="canEdit(q)" type="button" class="button small" :disabled="busy" @click="editBudget(q)">Editar</button>
                  <button v-if="canSchedule(q)" type="button" class="button small" :disabled="busy" @click="appointmentBudget = q as PresupuestoConDatos">Agendar turno</button>
                  <button v-if="registro(q).estado === 'PENDIENTE'" type="button" class="button small" :disabled="busy" @click="requestState(q, 'ACEPTADO')">Aceptar</button>
                  <button v-if="registro(q).estado === 'PENDIENTE'" type="button" class="button small" :disabled="busy" @click="requestState(q, 'RECHAZADO')">Rechazar</button>
                  <button v-if="registro(q).estado !== 'ANULADO'" type="button" class="button small danger" :disabled="busy" @click="requestState(q, 'ANULADO')">Anular</button>
                </template>
                <button
                  v-if="canDeleteBudget(q)"
                  type="button"
                  class="button small quote-delete-btn"
                  :aria-label="`Eliminar presupuesto #${q.id}`"
                  @click="requestDeleteBudget(q)"
                >
                  <Trash2 :size="13" /> Eliminar
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <div v-if="!loading && !error && !filteredQuotes.length" class="empty-state">
      <FileText :size="38" class="muted" />
      <h3>{{ connected && budgetSection === 'archivados' ? 'No se encontraron presupuestos anulados' : budgetSection === 'archivados' ? 'No se encontraron presupuestos archivados' : 'No se encontraron presupuestos activos' }}</h3>
      <p v-if="search">Probá con otra búsqueda.</p>
      <p v-else-if="!connected && budgetSection === 'archivados'">Cuando se registre el pago de una factura, su presupuesto aparecerá acá.</p>
      <p v-else>Creá un presupuesto nuevo para empezar.</p>
    </div>

    <CommonModalDialog
      v-if="budgetToDelete"
      class="dialog delete-budget-dialog"
      role="alertdialog"
      aria-labelledby="delete-budget-title"
      aria-describedby="delete-budget-description"
      @close="cancelDeleteBudget"
    >
      <template v-if="budgetToDelete">
        <div class="delete-budget-content">
          <h2 id="delete-budget-title">
            ¿Eliminar presupuesto #{{ budgetToDelete.id }}{{ appointmentsToDelete.length === 1 ? ' y su turno' : appointmentsToDelete.length ? ' y sus turnos' : '' }}?
          </h2>
          <p v-if="appointmentsToDelete.length" id="delete-budget-description">
            Se eliminarán el presupuesto y {{ appointmentsToDelete.length === 1 ? 'el turno asociado' : 'los turnos asociados' }} del apartado Turnos. ¿Estás seguro de que querés eliminar ambos? Esta acción no se puede deshacer.
          </p>
          <p v-else id="delete-budget-description">¿Estás seguro de que querés eliminar este presupuesto? Esta acción no se puede deshacer.</p>
          <p v-for="appointment in appointmentsToDelete" :key="appointment.id" class="muted">
            Turno: {{ appointment.date }} · {{ appointment.time }} hs
          </p>
        </div>
        <footer class="dialog-footer">
          <button type="button" class="button" autofocus @click="cancelDeleteBudget">Cancelar</button>
          <button type="button" class="button delete-confirm-btn" @click="confirmDeleteBudget">
            <Trash2 :size="14" /> {{ appointmentsToDelete.length === 1 ? 'Eliminar presupuesto y turno' : appointmentsToDelete.length ? 'Eliminar presupuesto y turnos' : 'Eliminar presupuesto' }}
          </button>
        </footer>
      </template>
    </CommonModalDialog>

    <!-- Create Modal -->
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
.table-budget-meta .badge.neutral {
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
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  font-size: 12px;
  color: #1e40af;
}

.pill-icon {
  color: #2563eb;
  flex-shrink: 0;
}

:global(html.dark .budget-appointment-pill) {
  background: rgba(10, 132, 255, 0.12);
  border-color: rgba(10, 132, 255, 0.25);
  color: #93c5fd;
}

:global(html.dark .pill-icon) {
  color: #60a5fa;
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
  align-items: flex-start;
  gap: 4px;
}

.table-subtext {
  font-size: 9.5px;
  display: block;
}

.table-vehicle-cell {
  display: flex;
  flex-direction: column;
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
  align-items: center;
  justify-content: center;
  gap: 6px;
}
</style>
