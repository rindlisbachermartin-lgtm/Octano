<script setup lang="ts">
import { Check, X, Search, AlertTriangle } from '@lucide/vue'
import type { Appointment, Vehicle } from '~/types'
import { validateAppointment, appointmentsOverlap } from '~/utils/appointments'
import { isScheduledAppointment } from '~/utils/appointmentLifecycle'
import { budgetConflictsWithOrder } from '~/utils/orderWorkflow'
import { authError } from '~/composables/useAuthApi'
import { adaptarTurno, type DisponibilidadTurno, type ConflictoTurno, type PaginaTurnos, type TurnoApi } from '~/utils/turnoApi'
import { adaptarTitular, adaptarVehiculo, type ClienteTitularApi, type VehiculoApi } from '~/utils/vehiculoApi'

const props = defineProps<{
  open: boolean
  defaultDate?: string
  defaultTime?: string
  appointment?: Appointment | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created', appointment: Appointment): void
  (e: 'updated', appointment: Appointment): void
}>()

const { db, client, issueAppointmentOrder, syncAppointmentOrder } = useDatabase()
const { authorization, puedeAdministrar } = useAuthApi()
const { matches } = useHelpers()
const formError = ref('')
const saving = ref(false)
const vehicleSearch = ref('')
const debouncedSearch = useDebouncedValue(vehicleSearch)
const searchInputRef = ref<HTMLInputElement | null>(null)
const showOverlapPrompt = ref(false)
const conflicts = ref<ConflictoTurno[]>([])
const { today } = useWorkshopDay()

useModalEscape(() => props.open && !saving.value, () => emit('close'))

const form = ref({
  vehicle: '' as string | number,
  date: props.defaultDate || today.value,
  time: '11:00',
  endTime: '',
  reason: '',
  budgetId: '' as string | number,
})

interface PresupuestoTurnoApi {
  id: number
  descripcion: string
  estado: 'PENDIENTE' | 'ACEPTADO' | 'RECHAZADO' | 'ANULADO'
  ordenId: number | null
}

const apiBudgets = ref<{ id: number; description: string }[]>([])
const apiVehicles = ref<Vehicle[]>([])
const apiClients = ref<Record<number, ClienteTitularApi>>({})
const apiSelectedVehicle = ref<Vehicle | null>(null)
const searching = ref(false)
const searchError = ref('')
const budgetError = ref('')

const availableBudgets = computed(() => authorization.value ? apiBudgets.value : db.value.quotes.filter((q) =>
  q.vehicle === Number(form.value.vehicle) &&
  (q.id === props.appointment?.budgetId || (!q.orderId && !q.appointmentId && !['En taller', 'Convertido', 'Archivado'].includes(q.status)
    && !budgetConflictsWithOrder(props.appointment ? db.value.orders.find(order => order.id === props.appointment?.orderId || order.appointmentId === props.appointment?.id) : undefined, q)))
))
watch(() => form.value.vehicle, (vehicle) => {
  if (authorization.value) {
    if (vehicle !== props.appointment?.vehicle) form.value.budgetId = ''
  } else if (!availableBudgets.value.some((q) => q.id === Number(form.value.budgetId))) {
    form.value.budgetId = ''
  }
})
watch(() => form.value.budgetId, (id) => {
  const budget = availableBudgets.value.find((q) => q.id === Number(id))
  if (budget && budget.id !== props.appointment?.budgetId) form.value.reason = budget.description
})

const selectedVehicle = computed(() =>
  authorization.value ? apiSelectedVehicle.value : db.value.vehicles.find((v) => v.id === Number(form.value.vehicle))
)

function vehicleOwner(v: Vehicle) {
  if (!authorization.value) return client(v.client)
  const titular = apiClients.value[v.client]
  return titular ? adaptarTitular(titular) : { name: v.registro?.titular, doc: '', phone: '' }
}

const filteredVehicles = computed(() => {
  if (authorization.value) {
    return vehicleSearch.value.trim() === debouncedSearch.value.trim() ? apiVehicles.value : []
  }
  const query = debouncedSearch.value.trim()
  if (!query) return []
  return db.value.vehicles.filter((v) => {
    const c = client(v.client)
    return matches(query, v.plate, v.brand, v.model, c?.name, c?.doc, c?.phone)
  })
})

watch(() => [props.open, authorization.value, debouncedSearch.value.trim()] as const,
  async ([open, headers, query], _, onCleanup) => {
    const controller = new AbortController()
    const { signal } = controller
    onCleanup(() => controller.abort())
    apiVehicles.value = []
    searchError.value = ''
    searching.value = !!(open && headers && query)
    if (!open || !headers || !query) return
    try {
      const [vehicles, clients] = await Promise.all([
        $fetch<PaginaTurnos<VehiculoApi>>('/api/vehiculos', { headers, signal, query: { activo: true, q: query.slice(0, 250), tamano: 50 } }),
        $fetch<PaginaTurnos<ClienteTitularApi>>('/api/clientes', { headers, signal, query: { activo: true, q: query.slice(0, 90), tamano: 20 } }),
      ])
      const matchesByOwner = await Promise.all(clients.contenido.map(({ id }) =>
        $fetch<PaginaTurnos<VehiculoApi>>('/api/vehiculos', { headers, signal, query: { activo: true, clienteId: id, tamano: 30 } }),
      ))
      if (signal.aborted) return
      const results = [...vehicles.contenido, ...matchesByOwner.flatMap((page) => page.contenido)]
      for (const titular of clients.contenido) apiClients.value[titular.id] = titular
      apiVehicles.value = [...new Map(results.map((v) => [v.id, v])).values()].map(adaptarVehiculo)
    } catch (err) {
      if (!signal.aborted) searchError.value = authError(err, 'No se pudo buscar vehículos.')
    } finally {
      if (!signal.aborted) searching.value = false
    }
  })

watch(() => [props.open, authorization.value, form.value.vehicle] as const,
  async ([open, headers, vehicle], _, onCleanup) => {
    const controller = new AbortController()
    const { signal } = controller
    onCleanup(() => controller.abort())
    apiBudgets.value = []
    budgetError.value = ''
    if (!open || !headers || !vehicle) return
    const editingCurrentVehicle = Number(vehicle) === props.appointment?.vehicle
    if (editingCurrentVehicle && apiSelectedVehicle.value?.id !== Number(vehicle)) {
      try {
        const item = await $fetch<VehiculoApi>(`/api/vehiculos/${vehicle}`, { headers, signal })
        if (!signal.aborted) apiSelectedVehicle.value = adaptarVehiculo(item)
      } catch (err) {
        if (!signal.aborted) formError.value = authError(err, 'No se pudo cargar el vehículo del turno.')
      }
    }
    if (signal.aborted) return
    const linkedId = editingCurrentVehicle ? props.appointment?.budgetId : null
    try {
      const items: PresupuestoTurnoApi[] = []
      let pagina = 0
      let totalPaginas: number
      do {
        const result = await $fetch<PaginaTurnos<PresupuestoTurnoApi>>('/api/presupuestos', {
          headers, signal, query: { vehiculoId: vehicle, pagina, tamano: 100 },
        })
        items.push(...result.contenido)
        totalPaginas = result.totalPaginas
        pagina++
      } while (pagina < totalPaginas && !signal.aborted)
      if (signal.aborted) return
      apiBudgets.value = items
        .filter((item) => item.id === linkedId || (!item.ordenId && ['PENDIENTE', 'ACEPTADO'].includes(item.estado)))
        .map((item) => ({ id: item.id, description: item.descripcion }))
    } catch (err) {
      if (!signal.aborted) budgetError.value = authError(err, 'No se pudieron cargar los presupuestos.')
    }
    if (!signal.aborted && linkedId && !apiBudgets.value.some((item) => item.id === linkedId)) {
      apiBudgets.value.unshift({ id: linkedId, description: 'Presupuesto vinculado' })
    }
  })

const overlappingAppointment = computed(() => {
  if (!form.value.date || !form.value.time) return null
  return (
    db.value.appointments.find(
      (a) =>
        a.id !== props.appointment?.id &&
        a.date === form.value.date &&
        appointmentsOverlap(a, form.value) &&
        !['Cancelado', 'No asistió'].includes(a.status)
    ) || null
  )
})

const overlappingVehicle = computed(() => {
  if (!overlappingAppointment.value) return null
  return db.value.vehicles.find((v) => v.id === overlappingAppointment.value?.vehicle)
})

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      apiSelectedVehicle.value = null
      apiClients.value = {}
      form.value = {
        vehicle: props.appointment?.vehicle || '',
        date: props.appointment?.date || (props.defaultDate && props.defaultDate >= today.value ? props.defaultDate : today.value),
        time: props.appointment?.time || props.defaultTime || '11:00',
        endTime: props.appointment?.endTime || '',
        reason: props.appointment?.reason || '',
        budgetId: props.appointment?.budgetId || '',
      }
      vehicleSearch.value = ''
      formError.value = ''
      showOverlapPrompt.value = false
      nextTick(() => {
        searchInputRef.value?.focus()
      })
    }
  }
)

watch(
  () => [form.value.vehicle, form.value.date, form.value.time, form.value.endTime, form.value.reason, form.value.budgetId],
  () => {
    showOverlapPrompt.value = false
  }
)

function selectVehicle(v: Vehicle) {
  if (authorization.value) apiSelectedVehicle.value = v
  form.value.vehicle = v.id
  vehicleSearch.value = ''
  formError.value = ''
}

function clearVehicle() {
  apiSelectedVehicle.value = null
  form.value.vehicle = ''
  vehicleSearch.value = ''
  nextTick(() => {
    searchInputRef.value?.focus()
  })
}

async function submit(allowOverlap = false) {
  if (saving.value) return
  formError.value = ''
  if (props.appointment && !isScheduledAppointment(props.appointment)) {
    formError.value = 'Este turno ya no está pendiente de ingreso. Cerrá el formulario y agendá uno nuevo si corresponde.'
    showOverlapPrompt.value = false
    return
  }
  if (!form.value.vehicle) {
    formError.value = 'Por favor buscá y seleccioná un vehículo / cliente titular.'
    return
  }

  if (!form.value.reason.trim()) {
    formError.value = 'Ingresá el motivo de la visita.'
    return
  }

  formError.value = validateAppointment(authorization.value ? [] : db.value.appointments, { ...form.value, vehicle: Number(form.value.vehicle) }, props.appointment?.id)
  if (formError.value) {
    showOverlapPrompt.value = false
    return
  }
  if (form.value.date < today.value) {
    formError.value = 'La fecha del turno no puede ser anterior a hoy.'
    showOverlapPrompt.value = false
    return
  }
  const time = authorization.value ? form.value.time : form.value.endTime || form.value.time
  if (new Date(`${form.value.date}T${time}:00-03:00`).getTime() <= Date.now()) {
    formError.value = authorization.value ? 'El turno debe comenzar en una fecha y hora futuras.' : 'El fin del turno debe ser posterior al momento actual.'
    showOverlapPrompt.value = false
    return
  }
  if (form.value.budgetId && !(authorization.value && form.value.budgetId === props.appointment?.budgetId)
    && !availableBudgets.value.some((q) => q.id === Number(form.value.budgetId))) {
    formError.value = 'Elegí un presupuesto disponible para este vehículo.'
    return
  }

  if (authorization.value && selectedVehicle.value) {
    await saveApiAppointment(selectedVehicle.value, authorization.value, allowOverlap)
    return
  }
  if (authorization.value) return

  // Si hay superposición con otro turno, advertir y solicitar confirmación
  if (overlappingAppointment.value && !allowOverlap) {
    showOverlapPrompt.value = true
    return
  }

  saveAppointment()
}

async function saveApiAppointment(vehicle: Vehicle, headers: Record<string, string>, allowOverlap: boolean) {
  saving.value = true
  const id = props.appointment?.id
  const { date, time, endTime, reason, budgetId } = form.value
  const horario = { fecha: date, horaInicio: time, horaFin: endTime || undefined, vehiculoId: vehicle.id }
  showOverlapPrompt.value = false
  try {
    if (!allowOverlap) {
      const disponibilidad = await $fetch<DisponibilidadTurno>('/api/turnos/disponibilidad', {
        headers, query: { ...horario, excluirTurnoId: id },
      })
      if (disponibilidad.vehiculoConTurno) {
        formError.value = 'Este vehículo ya tiene un turno activo para ese día. Editá el existente o elegí otra fecha.'
        return
      }
      if (disponibilidad.conflictos.length) {
        showApiConflicts(disponibilidad.conflictos, puedeAdministrar.value)
        return
      }
    }
    const turno = await $fetch<TurnoApi>(id ? `/api/turnos/${id}` : '/api/turnos', {
      method: id ? 'PUT' : 'POST', headers,
      body: {
        ...horario, horaFin: endTime || null, clienteId: vehicle.client, motivoVisita: reason.trim(),
        presupuestoId: Number(budgetId) || null, confirmarSuperposicion: allowOverlap,
      },
    })
    const appointment = adaptarTurno(turno)
    if (id) emit('updated', appointment)
    else emit('created', appointment)
  } catch (err) {
    const data = (err as { data?: { codigo?: string; puedeConfirmar?: boolean; conflictos?: ConflictoTurno[] } })?.data
    if (data?.codigo === 'SUPERPOSICION_TURNO' && data.conflictos?.length) {
      showApiConflicts(data.conflictos, data.puedeConfirmar === true)
    } else {
      formError.value = authError(err, 'No se pudo guardar el turno.')
    }
  } finally {
    saving.value = false
  }
}

function showApiConflicts(items: ConflictoTurno[], canConfirm: boolean) {
  conflicts.value = items
  showOverlapPrompt.value = canConfirm
  if (!canConfirm) formError.value = 'El horario se superpone con otro turno. Solo un administrador puede autorizarlo.'
}

function saveAppointment() {
  if (props.appointment) {
    const previousBudgetId = props.appointment.budgetId
    Object.assign(props.appointment, {
      vehicle: Number(form.value.vehicle),
      date: form.value.date,
      time: form.value.time,
      endTime: form.value.endTime || undefined,
      reason: form.value.reason.trim(),
      budgetId: Number(form.value.budgetId) || null,
    })
    syncAppointmentOrder(props.appointment, previousBudgetId)
    emit('updated', props.appointment)
    return
  }
  const newAppointment: Appointment = {
    id: Date.now(),
    vehicle: Number(form.value.vehicle),
    date: form.value.date,
    time: form.value.time,
    endTime: form.value.endTime || undefined,
    reason: form.value.reason.trim(),
    status: 'Programado',
    budgetId: Number(form.value.budgetId) || null,
  }

  db.value.appointments.push(newAppointment)
  issueAppointmentOrder(newAppointment)
  showOverlapPrompt.value = false
  emit('created', newAppointment)
}
</script>

<template>
  <CommonFormPage v-if="open" class="appointment-form-page">
    <div class="dialog-header">
      <h2>{{ appointment ? 'Editar turno' : 'Agendar un turno' }}</h2>
      <button class="icon-button" aria-label="Cerrar" :disabled="saving" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <form @submit.prevent="submit()" class="entry-form">
      <div class="form-fields" :inert="saving">
        <!-- Vehicle / Client Search Selector -->
        <div class="field-block">
          <label class="block-label">
            Vehículo y cliente titular <span class="required-star">*</span>
          </label>

          <!-- Selected Vehicle Card -->
          <div v-if="selectedVehicle" class="selected-target-card">
            <span class="plate">{{ selectedVehicle.plate }}</span>
            <div class="target-info">
              <strong class="target-title">{{ selectedVehicle.brand }} {{ selectedVehicle.model }}</strong>
              <div class="target-sub">
                <span class="target-owner-label">Titular:</span>
                <strong class="target-owner-name">{{ vehicleOwner(selectedVehicle)?.name }}</strong>
                <template v-if="vehicleOwner(selectedVehicle)?.doc">
                  <span class="target-dot">·</span>
                  <span class="target-doc">DNI {{ vehicleOwner(selectedVehicle)?.doc }}</span>
                </template>
                <template v-if="vehicleOwner(selectedVehicle)?.phone">
                  <span class="target-dot">·</span>
                  <span class="target-phone">Tel. {{ vehicleOwner(selectedVehicle)?.phone }}</span>
                </template>
              </div>
            </div>
            <button
              type="button"
              class="button small change-target-btn"
              @click="clearVehicle"
            >
              Cambiar
            </button>
          </div>

          <!-- Vehicle / Client Search Input & Results -->
          <div v-else class="target-search-container">
            <div class="target-search-box">
              <Search :size="16" class="search-icon" />
              <input
                ref="searchInputRef"
                v-model="vehicleSearch"
                type="text"
                placeholder="Buscá por cliente, DNI, patente o modelo…"
                autocomplete="off"
              />
              <button
                v-if="vehicleSearch"
                type="button"
                class="icon-button clear-icon-btn"
                @click="vehicleSearch = ''"
                aria-label="Limpiar búsqueda"
              >
                <X :size="14" />
              </button>
            </div>

            <!-- Results Dropdown -->
            <p v-if="!vehicleSearch.trim()" class="muted search-hint">Empezá a escribir para buscar vehículos.</p>
            <p v-else-if="authorization && (searching || vehicleSearch.trim() !== debouncedSearch.trim())" class="muted search-hint" role="status">Buscando vehículos…</p>
            <p v-else-if="searchError" class="error-message" role="alert">{{ searchError }}</p>
            <div v-else class="target-results-list">
              <div
                v-for="v in filteredVehicles"
                :key="v.id"
                class="target-result-item"
                @click="selectVehicle(v)"
              >
                <span class="plate small-plate">{{ v.plate }}</span>
                <div class="result-details">
                  <strong class="result-name">{{ v.brand }} {{ v.model }}</strong>
                  <span class="result-meta">
                    Cliente: <strong>{{ vehicleOwner(v)?.name }}</strong>
                    <template v-if="vehicleOwner(v)?.doc">
                      · DNI {{ vehicleOwner(v)?.doc }}
                    </template>
                  </span>
                </div>
              </div>

              <div v-if="!filteredVehicles.length && vehicleSearch.trim() === debouncedSearch.trim()" class="target-empty-state">
                <p>No se encontraron vehículos ni clientes para "<strong>{{ vehicleSearch }}</strong>"</p>
                <small>Podés verificar los datos o dar de alta el vehículo en la sección Vehículos.</small>
              </div>
            </div>
          </div>

          <!-- Hidden select for accessibility and test compatibility -->
          <select v-if="!authorization" v-model="form.vehicle" aria-label="Vehículo" class="sr-only">
            <option value="">Seleccionar vehículo...</option>
            <option v-for="v in db.vehicles" :key="v.id" :value="v.id">
              {{ v.plate }} · {{ v.brand }} {{ v.model }} — {{ client(v.client)?.name }}
            </option>
          </select>
        </div>

        <CommonDependentFields :ready="!!selectedVehicle">
        <label>
          Presupuesto (opcional)
          <select v-model="form.budgetId" :disabled="!selectedVehicle">
            <option value="">Sin presupuesto</option>
            <option v-for="budget in availableBudgets" :key="budget.id" :value="budget.id">#{{ budget.id }} · {{ budget.description }}</option>
          </select>
        </label>
        <p v-if="budgetError" class="error-message" role="alert">{{ budgetError }}</p>
        <p class="muted">Se emitirá una orden pendiente de ingreso. Al llegar el auto, registrá su ingreso para habilitarla al mecánico.<template v-if="!authorization"> Si no ingresa antes del fin estimado o, si queda vacío, del cierre del día, se dará de baja.</template></p>

        <div class="form-grid appointment-schedule">
          <CommonDatePicker v-model="form.date" label="Fecha" compact :min-date="today" />
          <label>
            Hora de inicio estimada
            <input v-model="form.time" type="time" required />
          </label>
          <label>
            Hora de fin estimada (opcional)
            <input v-model="form.endTime" type="time" />
          </label>
        </div>
        <p class="muted">Podés agendar varios vehículos en el mismo horario si tenés puestos de atención disponibles. Si hay superposición, te pediremos confirmarla.</p>

        <label>
          Motivo de la visita
          <input
            v-model="form.reason"
            required
            placeholder="Ej. Service de 10.000 km y revisión de frenos"
            :maxlength="authorization ? 500 : 120"
          />
        </label>

        <!-- Cuadro de confirmación compacto y neutral por superposición de turnos -->
        <div v-if="showOverlapPrompt" class="overlap-confirm-prompt">
          <div class="prompt-header">
            <AlertTriangle :size="15" class="warning-icon" />
            <span v-if="authorization">El horario se superpone con {{ conflicts.map((item) => `${item.patente} a las ${item.horaInicio.slice(0, 5)} hs`).join(', ') }}. Confirmá otro turno si tenés un puesto de atención disponible.</span>
            <span v-else>El horario se superpone con el turno de <strong>{{ overlappingVehicle?.plate }}</strong> a las <strong>{{ overlappingAppointment?.time }} hs</strong>. Confirmá otro turno si tenés un puesto de atención disponible.</span>
          </div>
          <div class="prompt-actions">
            <button type="button" class="button small" @click="showOverlapPrompt = false">
              Cambiar horario
            </button>
            <button type="button" class="button small primary" @click="submit(true)">
              <Check :size="14" /> Confirmar turno simultáneo
            </button>
          </div>
        </div>

        </CommonDependentFields>
        <p v-if="formError" class="error-message" role="alert">
          {{ formError }}
        </p>
      </div>

      <footer v-if="!showOverlapPrompt" class="dialog-footer modal-footer">
        <button type="button" class="button" :disabled="saving" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary" :disabled="!selectedVehicle || saving">
          <Check :size="16" />{{ appointment ? 'Guardar cambios' : 'Confirmar turno' }}
        </button>
      </footer>
    </form>
  </CommonFormPage>
</template>

<style scoped>
.overlap-confirm-prompt {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  flex-wrap: wrap;
}

:global(html.dark .overlap-confirm-prompt) {
  background: #202023;
  border-color: rgba(255, 255, 255, 0.1);
  color: #f1f5f9;
}

.prompt-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #334155;
  flex: 1;
  min-width: 200px;
}

:global(html.dark .prompt-header) {
  color: #cbd5e1;
}

.prompt-header .warning-icon {
  color: #64748b;
  flex-shrink: 0;
}

:global(html.dark .prompt-header .warning-icon) {
  color: #94a3b8;
}

.prompt-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
</style>
