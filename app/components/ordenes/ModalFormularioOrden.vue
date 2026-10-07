<script setup lang="ts">
import { Check, X, Search } from 'lucide-vue-next'
import type { Order, OrderPartSelection, Vehicle, Photo, Appointment } from '~/types'
import { assignOrderParts, validateOrderParts, canEditIssuedOrder, canEditOrderWork, saveOrderWork, editIssuedOrder, orderWorkTypes, orderWorkKinds, orderBillingParts, releaseUnstartedOrderParts } from '~/utils/orderWorkflow'
import { billingEntryBudget } from '~/utils/billingEntries'

const props = defineProps<{ open: boolean; order?: Order | null }>()
const emit = defineEmits<{ close: []; created: [orderId: number]; updated: [orderId: number] }>()
const { db, client, createOrder, recordOrderCompletion, activateAppointmentOrder, cancelAppointmentOrder } = useDatabase()
const { matches, money } = useHelpers()
const { notify } = useWorkshopToast()
const { today } = useWorkshopDay()
const work = ref({ km: null as number | null | '', diagnosis: '', oilSpec: '', oilProvidedByCustomer: false, replacedFilters: [] as string[], photos: [] as Photo[] })
const photoSector = ref('Tablero / kilometraje')
const futureAction = ref<{ appointment: Appointment; action: 'cancelar' | 'ingreso' } | null>(null)
const canPlan = computed(() => !props.order || canEditIssuedOrder(props.order))
const canChange = computed(() => !props.order || canEditOrderWork(props.order))
const linkedBudget = computed(() => props.order ? billingEntryBudget(props.order, db.value.quotes) : null)
const assignedParts = computed(() => props.order ? orderBillingParts(props.order, linkedBudget.value) : [])
const filterOptions = ['Filtro de aceite', 'Filtro de aire', 'Filtro de habitáculo', 'Filtro de combustible']
const draftParts = ref<OrderPartSelection[]>([])
const vehicleSearch = ref('')
const debouncedSearch = useDebouncedValue(vehicleSearch)
const formError = ref('')
const preview = ref(false)
const form = ref({ vehicle: '' as string | number, budgetId: '' as string | number, service: '', serviceTypes: [] as string[], mechanic: 'Nicolás', notes: '' })
const selectedVehicle = computed(() => db.value.vehicles.find((v) => v.id === Number(form.value.vehicle)))
const vehicleLocked = computed(() => !canPlan.value || (!!props.order && (!!props.order.parts.length || !!props.order.appointmentId || !!props.order.budgetId || db.value.quotes.some((q) => q.orderId === props.order?.id))))
const draftPartRows = computed(() => draftParts.value.map((selection) => {
  const part = db.value.parts.find((p) => p.id === selection.partId)
  return { ...selection, name: part?.name || selection.name || '', unitPrice: selection.unitPrice ?? part?.price ?? 0 }
}))
const filteredVehicles = computed(() => {
  const query = debouncedSearch.value.trim()
  return query ? db.value.vehicles.filter((v) => matches(query, v.plate, v.brand, v.model, client(v.client)?.name)) : []
})
const availableBudgets = computed(() => db.value.quotes.filter((q) => q.vehicle === Number(form.value.vehicle) && !q.orderId && !q.appointmentId && q.status === 'Pendiente'))
const selectedBudget = computed(() => availableBudgets.value.find((q) => q.id === Number(form.value.budgetId)))
const plannedService = computed(() => {
  const originalTypes = props.order ? orderWorkTypes(props.order) : []
  const sameTypes = originalTypes.length === form.value.serviceTypes.length && originalTypes.every((type) => form.value.serviceTypes.includes(type))
  return form.value.serviceTypes.includes('Otro trabajo') ? form.value.service.trim()
    : selectedBudget.value?.description || (sameTypes ? props.order?.service : '') || form.value.serviceTypes.join(' y ')
})
const workOptions = [
  { value: 'Service de mantenimiento', label: 'Service' },
  { value: 'Cambio de distribución', label: 'Distribución' },
  { value: 'Otro trabajo', label: 'Otro trabajo' },
]
function toggleWork(value: string) {
  if (value === 'Otro trabajo') form.value.serviceTypes = ['Otro trabajo']
  else {
    const selected = form.value.serviceTypes.filter((type) => type !== 'Otro trabajo')
    form.value.serviceTypes = selected.includes(value) ? selected.filter((type) => type !== value) : [...selected, value]
  }
}

useModalEscape(() => props.open, () => emit('close'))
watch(() => [props.open, props.order] as const, ([open]) => {
  if (!open) return
  const o = props.order
  const types = o ? orderWorkTypes(o) : []
  form.value = { vehicle: o?.vehicle || '', budgetId: '', service: o?.service || '', serviceTypes: o ? (types.length ? types : ['Otro trabajo']) : [], mechanic: o?.mechanic || 'Nicolás', notes: o?.notes || '' }
  preview.value = false
  draftParts.value = []
  vehicleSearch.value = ''
  formError.value = ''
  work.value = { km: o?.km ?? null, diagnosis: o?.diagnosis || '', oilSpec: o?.oilSpec || '', oilProvidedByCustomer: o?.oilProvidedByCustomer || false, replacedFilters: [...(o?.replacedFilters || [])], photos: (o?.photos || []).map(photo => ({ ...photo })) }
  futureAction.value = null
  photoSector.value = 'Tablero / kilometraje'
})
watch(() => form.value.vehicle, () => { form.value.budgetId = ''; draftParts.value = [] })
watch(() => form.value.budgetId, () => {
  if (selectedBudget.value) {
    form.value.service = selectedBudget.value.description
    const types = orderWorkTypes({ service: selectedBudget.value.description, serviceTypes: selectedBudget.value.serviceTypes })
    form.value.serviceTypes = types.length ? types : ['Otro trabajo']
  }
})
function selectVehicle(v: Vehicle) {
  form.value.vehicle = v.id
  vehicleSearch.value = ''
}
function saveExistingOrder(): boolean {
  const order = props.order
  if (!order || !canChange.value) return false
  formError.value = validateOrderParts(db.value, canPlan.value ? Number(form.value.vehicle) : order.vehicle, draftParts.value)
  if (formError.value) return false
  const km = work.value.km === '' ? null : work.value.km
  if (km != null && (!Number.isInteger(km) || km < 0)) { formError.value = 'Ingresá un kilometraje válido, sin decimales y mayor o igual a cero.'; return false }
  if (canPlan.value) {
    const service = plannedService.value
    if (!service) { formError.value = 'Completá el trabajo a realizar.'; return false }
    formError.value = editIssuedOrder(db.value, order, { vehicle: Number(form.value.vehicle), service, serviceTypes: [...form.value.serviceTypes], mechanic: form.value.mechanic, notes: form.value.notes.trim(), diagnosis: work.value.diagnosis, oilSpec: work.value.oilSpec, km, tasks: order.tasks.map(task => ({ ...task, name: task.name === order.service ? service : task.name })) })
    if (formError.value) return false
  }
  formError.value = saveOrderWork(db.value, order, { ...work.value, km, notes: form.value.notes.trim(), oilSpec: work.value.oilSpec.trim() })
  if (!formError.value && draftParts.value.length) formError.value = assignOrderParts(db.value, order, draftParts.value)
  if (formError.value) return false
  draftParts.value = []
  return true
}

function changeStatus(action: 'iniciar' | 'finalizar' | 'cancelar' | 'ingreso', confirmed = false) {
  const order = props.order
  if (!order || !canChange.value) return
  const appointment = db.value.appointments.find(appointment => appointment.id === order.appointmentId)
  if ((action === 'cancelar' || action === 'ingreso') && order.status === 'Pendiente de ingreso' && appointment && appointment.date > today.value && !confirmed) {
    futureAction.value = { appointment, action }; return
  }
  if (action === 'cancelar') {
    if (appointment && order.status === 'Pendiente de ingreso') cancelAppointmentOrder(appointment)
    releaseUnstartedOrderParts(db.value, order)
    order.status = 'Cancelado'; order.bay = null
  } else {
    if (!saveExistingOrder()) return
    if (action === 'ingreso') { if (!appointment || order.status !== 'Pendiente de ingreso') return; activateAppointmentOrder(appointment) }
    if (action === 'iniciar') { if (order.status !== 'En espera') return; order.status = 'En proceso'; order.startedAt = new Date().toISOString() }
    if (action === 'finalizar') {
      if (order.status !== 'En proceso') return
      order.status = 'Finalizado'; order.bay = null; recordOrderCompletion(order)
      db.value.notifications.unshift({ id: Date.now(), title: `El vehículo de la OT #${order.id} está listo para retirar`, detail: `OT #${order.id} finalizada por ${order.mechanic}`, read: false })
    }
  }
  notify(`Orden #${order.id}: ${order.status}.`)
  emit('updated', order.id)
}
function confirmFutureAction() {
  const action = futureAction.value?.action
  futureAction.value = null
  if (action) changeStatus(action, true)
}
function addPhotos(event: Event) {
  const input = event.target as HTMLInputElement
  if (props.order?.status !== 'En espera') return
  const orderId = props.order.id
  const sector = photoSector.value
  for (const file of Array.from(input.files || [])) {
    if (!file.type.startsWith('image/') || file.size > 2 * 1024 * 1024) { notify('Elegí una imagen de hasta 2 MB.'); continue }
    const reader = new FileReader()
    reader.onload = () => { if (props.open && props.order?.id === orderId && work.value.photos.length < 8) work.value.photos.push({ sector, data: String(reader.result) }) }
    reader.readAsDataURL(file)
  }
  input.value = ''
}

function submit() {
  formError.value = ''
  if (props.order) { if (saveExistingOrder()) emit('updated', props.order.id); return }
  if (!selectedVehicle.value || !form.value.serviceTypes.length || (form.value.serviceTypes.includes('Otro trabajo') && !form.value.service.trim())) {
    formError.value = 'Seleccioná un vehículo y el trabajo a realizar. Si elegís otro trabajo, describilo.'
    return
  }
  const service = plannedService.value
  formError.value = validateOrderParts(db.value, Number(form.value.vehicle), draftParts.value)
  if (formError.value) { preview.value = false; return }
  const tasks = [{ name: service, done: false }]
  const changes = { vehicle: Number(form.value.vehicle), service, serviceTypes: [...form.value.serviceTypes], mechanic: form.value.mechanic, notes: form.value.notes.trim(), diagnosis: '', oilSpec: selectedBudget.value?.oilSpec || '', km: null, tasks }
  if (!['Nicolás', 'Santiago'].includes(changes.mechanic)) {
    formError.value = 'Seleccioná un mecánico válido.'
    return
  }
  if (form.value.budgetId && !selectedBudget.value) {
    formError.value = 'Seleccioná un presupuesto pendiente disponible para este vehículo.'
    preview.value = false
    return
  }
  if (!preview.value) {
    preview.value = true
    return
  }
  const budget = selectedBudget.value
  const initialParts = (budget?.items || []).map((item, i) => ({ id: Number(item.partId) || i + 1, name: item.name, quantity: item.quantity, unitPrice: item.unitPrice, price: item.quantity * item.unitPrice }))
  const id = createOrder(changes.vehicle, changes.service, changes.mechanic, null, initialParts, changes.serviceTypes, changes.oilSpec)
  const order = db.value.orders.find((o) => o.id === id)!
  Object.assign(order, changes, { budgetId: budget?.id || null })
  assignOrderParts(db.value, order, draftParts.value)
  if (budget) { budget.status = 'Convertido'; budget.orderId = id }
  emit('created', id)
}
</script>

<template>
  <CommonFormPage v-if="open">
    <div class="dialog-header">
      <div>
        <h2>{{ order ? `Editar orden #${order.id}` : preview ? 'Vista previa de la orden de trabajo' : 'Nueva orden de trabajo' }}</h2>
        <p class="muted">{{ order ? (canPlan ? 'Podés ajustar los datos antes de iniciar el trabajo.' : 'Registrá los datos del trabajo. El vehículo, servicio y mecánico ya están definidos.') : preview ? 'Revisá los datos antes de emitir la orden.' : 'El presupuesto es opcional. Podés armar la factura cuando finalice el trabajo.' }}</p>
      </div>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')"><X :size="18" /></button>
    </div>
    <form class="entry-form" @submit.prevent="submit">
      <div v-if="preview && selectedVehicle" class="form-fields order-preview">
        <div class="selected-target-card">
          <span class="plate">{{ selectedVehicle.plate }}</span>
          <div class="target-info"><strong>{{ selectedVehicle.brand }} {{ selectedVehicle.model }}</strong><p class="muted">{{ client(selectedVehicle.client)?.name }}</p></div>
        </div>
        <dl class="preview-details">
          <div><dt>Trabajo a realizar</dt><dd>{{ plannedService }}</dd></div>
          <div><dt>Mecánico asignado</dt><dd>{{ form.mechanic }}</dd></div>
          <div><dt>Presupuesto</dt><dd>{{ selectedBudget ? `#${selectedBudget.id} · ${selectedBudget.description}` : 'Sin presupuesto' }}</dd></div>
          <div v-if="selectedBudget?.oilSpec"><dt>Especificación del aceite</dt><dd>{{ selectedBudget.oilSpec }}</dd></div>
          <div><dt>Observaciones</dt><dd>{{ form.notes.trim() || 'Sin observaciones' }}</dd></div>
        </dl>
        <div v-if="selectedBudget?.items.length">
          <strong>Repuestos del presupuesto</strong>
          <ul><li v-for="(item, index) in selectedBudget.items" :key="index">{{ item.quantity }} × {{ item.name }}</li></ul>
        </div>
        <div v-if="draftPartRows.length">
          <strong>Repuestos asignados</strong>
          <ul><li v-for="(row, index) in draftPartRows" :key="index">{{ row.quantity }} × {{ row.name }} · {{ money(row.quantity * row.unitPrice) }}</li></ul>
        </div>
        <p class="muted">El mecánico registra y actualiza el kilometraje desde su vista.</p>
      </div>
      <div v-else class="form-fields">
        <div v-if="selectedVehicle" class="selected-target-card">
          <span class="plate">{{ selectedVehicle.plate }}</span>
          <div class="target-info"><strong>{{ selectedVehicle.brand }} {{ selectedVehicle.model }}</strong><p class="muted">{{ client(selectedVehicle.client)?.name }}</p></div>
          <button v-if="!vehicleLocked" type="button" class="button small" @click="form.vehicle = ''">Cambiar vehículo</button>
        </div>
        <div v-else>
          <label class="search-box"><Search :size="16" /><input v-model="vehicleSearch" aria-label="Buscar vehículo para la orden" placeholder="Buscá por patente, modelo o cliente…" /></label>
          <div v-if="vehicleSearch.trim()" class="vehicle-results">
            <button v-for="v in filteredVehicles" :key="v.id" type="button" class="vehicle-result" @click="selectVehicle(v)">
              <span class="plate small-plate">{{ v.plate }}</span>
              <span class="vehicle-result-info"><strong>{{ v.brand }} {{ v.model }}</strong><small class="muted">{{ client(v.client)?.name }}</small></span>
            </button>
            <p v-if="!filteredVehicles.length && vehicleSearch.trim() === debouncedSearch.trim()" class="muted">No se encontraron vehículos.</p>
          </div>
        </div>
        <select v-model="form.vehicle" class="sr-only" aria-label="Vehículo de la orden" :disabled="vehicleLocked"><option value="">Seleccionar vehículo</option><option v-for="v in db.vehicles" :key="v.id" :value="v.id">{{ v.plate }} · {{ v.brand }} {{ v.model }}</option></select>
        <CommonDependentFields :ready="!!selectedVehicle">
        <label v-if="!order">Presupuesto (opcional)
          <input v-if="!selectedVehicle || !availableBudgets.length" :value="selectedVehicle ? 'No hay presupuestos disponibles para este vehículo' : ''" :placeholder="selectedVehicle ? '' : 'Seleccioná un vehículo para ver sus presupuestos'" readonly />
          <select v-else v-model="form.budgetId"><option value="">Sin presupuesto</option><option v-for="budget in availableBudgets" :key="budget.id" :value="budget.id">#{{ budget.id }} · {{ budget.description }}</option></select>
        </label>
        <fieldset class="work-type-field" :disabled="!canPlan">
          <legend>Trabajo a realizar</legend>
          <div class="work-type-options"><button v-for="option in workOptions" :key="option.value" type="button" class="button" :class="{ primary: form.serviceTypes.includes(option.value) }" :aria-pressed="form.serviceTypes.includes(option.value)" @click="toggleWork(option.value)">{{ option.label }}</button></div>
          <p class="muted">Service y distribución se registran en la cartilla digital al finalizar la orden.</p>
        </fieldset>
        <label v-if="form.serviceTypes.includes('Otro trabajo')">Detalle del trabajo<input v-model="form.service" :readonly="!canPlan" required maxlength="300" placeholder="Describí el trabajo solicitado" /></label>
        <label>Mecánico asignado<select v-model="form.mechanic" :disabled="!canPlan"><option>Nicolás</option><option>Santiago</option></select></label>
        <div v-if="selectedVehicle">
          <ul v-if="order ? assignedParts.length : selectedBudget?.items.length" class="assigned-parts">
            <template v-if="order"><li v-for="(part, index) in assignedParts" :key="index">{{ part.quantity }} × {{ part.description }} <strong>{{ money(part.total) }}</strong></li></template>
            <template v-else><li v-for="(part, index) in selectedBudget?.items || []" :key="index">{{ part.quantity || 1 }} × {{ part.name }}</li></template>
          </ul>
          <OrdenesEditorRepuestos v-if="canChange" v-model="draftParts" :vehicle-id="selectedVehicle.id" />
        </div>
        <template v-if="order">
          <div class="form-grid">
            <label>Kilometraje<input v-model.number="work.km" type="number" min="0" step="1" :disabled="!canChange" /></label>
            <label v-if="orderWorkKinds(order).service">Aceite utilizado<input v-model="work.oilSpec" maxlength="120" :disabled="!canChange" /></label>
          </div>
          <template v-if="orderWorkKinds(order).service">
            <label class="check-label"><input v-model="work.oilProvidedByCustomer" type="checkbox" :disabled="!canChange" /> El aceite lo trajo el dueño</label>
            <fieldset class="work-type-field" :disabled="!canChange"><legend>Filtros reemplazados</legend><div class="filter-checks"><label v-for="filter in filterOptions" :key="filter" class="check-label"><input v-model="work.replacedFilters" type="checkbox" :value="filter" /> {{ filter }}</label></div></fieldset>
          </template>
          <label>Diagnóstico técnico<textarea v-model="work.diagnosis" rows="2" :disabled="!canChange" /></label>
          <div class="order-photo-section">
            <strong>Fotos del vehículo</strong>
            <div v-if="order.status === 'En espera'" class="form-grid">
              <label>Sector de la foto<select v-model="photoSector"><option>Tablero / kilometraje</option><option>Frente</option><option>Trasera</option><option>Lateral izquierdo</option><option>Lateral derecho</option><option>Detalle mecánico</option><option>Bajo chasis / motor</option></select></label>
              <label>Adjuntar fotos<input type="file" accept="image/*" capture="environment" multiple @change="addPhotos" /></label>
            </div>
            <div v-if="work.photos.length" class="order-photos"><figure v-for="(photo, index) in work.photos" :key="index"><img :src="photo.data" :alt="photo.sector" /><figcaption>{{ photo.sector }}</figcaption><button v-if="order.status === 'En espera'" type="button" class="button small" @click="work.photos.splice(index, 1)">Quitar foto</button></figure></div>
            <p v-else class="muted">No se adjuntaron fotografías.</p>
          </div>
        </template>
        <label>Observaciones<textarea v-model="form.notes" rows="3" :disabled="!canChange" placeholder="Indicaciones o aclaraciones para el mecánico" /></label>
        </CommonDependentFields>
      </div>
      <p v-if="formError" class="error-message" role="alert">{{ formError }}</p>
      <footer class="dialog-footer modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button v-if="preview" type="button" class="button" @click="preview = false">Volver a editar</button>
        <button type="submit" class="button primary" :disabled="!selectedVehicle || !canChange"><Check :size="16" />{{ order ? 'Guardar cambios' : preview ? 'Crear orden de trabajo' : 'Ver vista previa' }}</button>
        <template v-if="order && canChange">
          <button type="button" class="button outlined" @click="changeStatus('cancelar')">Dar de baja</button>
          <button v-if="order.status === 'Pendiente de ingreso'" type="button" class="button primary" @click="changeStatus('ingreso')">Registrar ingreso</button>
          <button v-if="order.status === 'En espera'" type="button" class="button primary" @click="changeStatus('iniciar')">Iniciar trabajo</button>
          <button v-if="order.status === 'En proceso'" type="button" class="button primary" @click="changeStatus('finalizar')">Finalizar trabajo</button>
        </template>
      </footer>
    </form>
  </CommonFormPage>
  <AgendaConfirmarAccionTurno v-if="open && futureAction" :appointment="futureAction.appointment" :action="futureAction.action" @close="futureAction = null" @confirm="confirmFutureAction" />
</template>

<style scoped>
.check-label { display: flex; flex-direction: row; align-items: center; gap: 8px; }
.check-label input { min-height: 0; width: auto; }
.filter-checks { display: flex; gap: 12px; flex-wrap: wrap; }
.order-photo-section { display: grid; gap: 12px; }
.order-photos { display: flex; flex-wrap: wrap; gap: 12px; }
.order-photos figure { margin: 0; width: 140px; }
.order-photos img { width: 140px; height: 100px; object-fit: cover; border-radius: 8px; }
.order-photos figcaption { font-size: 12px; margin: 4px 0; }
.vehicle-results { display: flex; flex-direction: column; max-height: 240px; overflow: auto; margin-top: 8px; border: 1px solid var(--line); border-radius: 8px; }
.vehicle-result { display: flex; align-items: center; gap: 12px; width: 100%; padding: 12px; border: 0; border-bottom: 1px solid var(--line); background: transparent; color: inherit; text-align: left; cursor: pointer; }
.vehicle-result:last-child { border-bottom: 0; }
.vehicle-result:hover { background: var(--surface); }
.vehicle-result-info { display: flex; flex-direction: column; gap: 4px; }
.work-type-field { border: 0; padding: 0; margin: 0; min-width: 0; }
.work-type-field legend { margin-bottom: 8px; font-size: 13px; font-weight: 600; }
.work-type-options { display: flex; flex-wrap: wrap; gap: 8px; }
.work-type-field p { margin: 8px 0 0; font-size: 12px; }
.preview-details { display: grid; gap: 16px; margin: 0; }
.preview-details dt { font-size: 13px; color: var(--muted); margin-bottom: 4px; }
.preview-details dd { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.assigned-parts { padding: 0; list-style: none; }
.assigned-parts li { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 0; }
</style>
