<script setup lang="ts">
import { Check, X, Search, ChevronRight } from '@lucide/vue'
import type { Budget, Vehicle, BudgetItem } from '~/types'
import type { PartEditorItem } from '~/types/partEditor'
import type { Client, Part } from '~/types'
import { authError } from '~/composables/useAuthApi'
import { adaptarPresupuesto, paginasPresupuestos, tiposTrabajoPresupuesto, type PresupuestoApi, type PresupuestoConDatos, type TipoTrabajoPresupuesto } from '~/utils/presupuestoApi'
import { adaptarVehiculo, adaptarTitular, type VehiculoApi, type ClienteTitularApi } from '~/utils/vehiculoApi'
import { adaptarRepuesto, type RepuestoApi } from '~/utils/repuestoApi'

const props = defineProps<{
  open: boolean
  budget?: PresupuestoConDatos | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created', budget: Budget): void
  (e: 'updated', budget: Budget): void
}>()

const { db, client: demoClient } = useDatabase()
const auth = useAuthApi()
const saving = ref(false)
const loading = ref(false)
const dataError = ref('')
const apiVehicles = ref<Vehicle[]>([])
const apiClients = ref<Client[]>([])
const apiParts = ref<Part[]>([])
const vehicles = computed(() => auth.authorization.value ? apiVehicles.value : db.value.vehicles)
const parts = computed(() => auth.authorization.value ? apiParts.value : db.value.parts)
const archivedParts = computed(() => parts.value.filter(part => part.archived && form.value.items.some(item => Number(item.partId) === part.id)))
const client = (id: number) => auth.authorization.value ? apiClients.value.find(item => item.id === id) : demoClient(id)
const { money, matches } = useHelpers()
const formError = ref('')
const vehicleSearch = ref('')
const debouncedSearch = useDebouncedValue(vehicleSearch)
const searchInputRef = ref<HTMLInputElement | null>(null)
const clientSearch = ref('')
const selectedClientId = ref<number | null>(null)
const vehicleSearchOpen = ref(false)
const selectedClient = computed(() => client(selectedClientId.value ?? selectedVehicle.value?.client ?? 0))
const filteredClients = computed(() => {
  if (!clientSearch.value.trim()) return []
  const clients = auth.authorization.value ? apiClients.value : db.value.clients
  return clients.filter(c => c.active && matches(clientSearch.value, c.name, c.doc, c.phone))
})

function close() { if (!saving.value) emit('close') }
useModalEscape(() => props.open && !saving.value, close)

const form = ref({
  vehicle: '' as string | number,
  description: '',
  labor: 0,
  items: [] as PartEditorItem[],
  serviceTypes: [] as string[],
  vat: 21,
})

const availableWorkTypes = [
  { value: 'Service de mantenimiento', label: 'Service' },
  { value: 'Cambio de distribución', label: 'Distribución' },
  { value: 'Otro trabajo', label: 'Otro trabajo' },
]

function toggleWork(value: string) {
  const selected = form.value.serviceTypes
  form.value.serviceTypes = selected.includes(value) ? selected.filter(type => type !== value) : [...selected, value]
}

const selectedVehicle = computed(() =>
  vehicles.value.find((v) => v.id === Number(form.value.vehicle))
)

const filteredVehicles = computed(() => {
  const query = debouncedSearch.value.trim()
  if (!selectedClient.value && !query) return []
  return vehicles.value.filter(v => (!selectedClient.value || v.client === selectedClient.value.id) && matches(query, v.plate, v.brand, v.model))
})

const partsSubtotal = computed(() => {
  return form.value.items.reduce((acc, item) => {
    const qty = Number(item.quantity) || 0
    const price = Number(item.unitPrice) || 0
    return acc + Math.round((qty * price + Number.EPSILON) * 100) / 100
  }, 0)
})

const netSubtotal = computed(() => {
  return Math.round((partsSubtotal.value + (Number(form.value.labor) || 0) + Number.EPSILON) * 100) / 100
})

const vatAmount = computed(() => {
  return Math.round((netSubtotal.value * form.value.vat / 100 + Number.EPSILON) * 100) / 100
})

const grandTotal = computed(() => {
  return Math.round((netSubtotal.value + vatAmount.value + Number.EPSILON) * 100) / 100
})

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      form.value = {
        vehicle: '',
        description: '',
        labor: 0,
        items: [],
        serviceTypes: [],
        vat: 21,
      }
      if (props.budget) {
        const budget = props.budget
        form.value = {
          vehicle: budget.vehicle, description: budget.description, labor: budget.labor,
          serviceTypes: [...(budget.serviceTypes ?? [])], vat: budget.registro.porcentajeIva,
          items: (budget.items ?? []).map((item, index) => ({
            key: index + 1, partId: item.partId ?? 'custom', customName: item.name,
            searchQuery: item.name, quantity: item.quantity, unitPrice: item.unitPrice,
          })),
        }
      }
      vehicleSearch.value = ''
      clientSearch.value = ''
      selectedClientId.value = props.budget?.registro.clienteId ?? null
      vehicleSearchOpen.value = false
      formError.value = ''
      nextTick(() => {
        searchInputRef.value?.focus()
      })
    }
  }
)

async function loadData(signal: AbortSignal, headers: Record<string, string>) {
  loading.value = true
  dataError.value = ''
  try {
    const [vehicles, clients, parts] = await Promise.all([
      paginasPresupuestos<VehiculoApi>('/api/vehiculos', headers, signal, { activo: true }),
      paginasPresupuestos<ClienteTitularApi>('/api/clientes', headers, signal, { activo: true }),
      paginasPresupuestos<RepuestoApi>('/api/repuestos', headers, signal, { activo: true }),
    ])
    // El solicitante del presupuesto puede ser un titular anterior, incluso archivado.
    const presupuestoClienteId = props.budget?.registro.clienteId
    if (presupuestoClienteId && !clients.some(item => item.id === presupuestoClienteId)) {
      clients.push(await $fetch<ClienteTitularApi>(`/api/clientes/${presupuestoClienteId}`, { headers, signal }))
    }
    if (signal.aborted) return
    apiClients.value = clients.map(adaptarTitular)
    const clientIds = new Set(clients.map(item => item.id))
    apiVehicles.value = vehicles.filter(item => item.id === props.budget?.vehicle || clientIds.has(item.clienteId)).map(adaptarVehiculo)
    const ids = [...new Set((props.budget?.items ?? []).map(item => item.partId).filter((id): id is number => typeof id === 'number' && !parts.some(part => part.id === id)))]
    const previousParts = await Promise.all(ids.map(id => $fetch<RepuestoApi>(`/api/repuestos/${id}`, { headers, signal })))
    if (signal.aborted) return
    apiParts.value = [...parts, ...previousParts].map(adaptarRepuesto)
  } catch (error) {
    if (!signal.aborted) dataError.value = authError(error, 'No se pudieron cargar los clientes, vehículos y repuestos.')
  } finally {
    if (!signal.aborted) loading.value = false
  }
}

const dataRevision = ref(0)
watch(() => [props.open, auth.authorization.value, dataRevision.value] as const, ([open, headers], _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  apiVehicles.value = []
  apiClients.value = []
  apiParts.value = []
  loading.value = false
  if (open && headers) void loadData(controller.signal, headers)
})

function selectVehicle(v: Vehicle) {
  form.value.vehicle = v.id
  selectedClientId.value = v.client
  vehicleSearchOpen.value = false
  vehicleSearch.value = ''
  formError.value = ''
}

function clearVehicle() {
  form.value.vehicle = ''
  vehicleSearch.value = ''
  vehicleSearchOpen.value = false
}

function selectClient(c: Client) {
  clearVehicle()
  selectedClientId.value = c.id
  clientSearch.value = ''
}

function clearClient() {
  clearVehicle()
  selectedClientId.value = null
  clientSearch.value = ''
  nextTick(() => searchInputRef.value?.focus())
}

async function submit() {
  if (saving.value || loading.value || dataError.value) return
  formError.value = ''
  if (!form.value.vehicle) {
    formError.value = 'Por favor buscá y seleccioná un vehículo / cliente titular.'
    return
  }

  if (!form.value.description.trim()) {
    formError.value = 'Ingresá el detalle del trabajo presupuestado.'
    return
  }

  // Validate items
  for (let i = 0; i < form.value.items.length; i++) {
    const it = form.value.items[i]
    if (it.partId === 'custom' && !it.customName.trim()) {
      formError.value = 'Por favor indicá el nombre del repuesto en la opción (Otro).'
      return
    }
    if (!it.partId) {
      formError.value = 'Por favor seleccioná un repuesto o eliminalo si no lo utilizás.'
      return
    }
  }

  const processedItems: BudgetItem[] = form.value.items.map((it) => {
    let name = it.customName
    const isCustom = it.partId === 'custom'
    if (!isCustom) {
      const p = parts.value.find((part) => part.id === Number(it.partId))
      if (p) name = p.brand ? `${p.name} (${p.brand})` : p.name
    }
    return {
      id: it.key,
      partId: isCustom ? null : Number(it.partId),
      name: name || 'Repuesto',
      quantity: Number(it.quantity) || 1,
      unitPrice: Number(it.unitPrice) || 0,
      total: Math.round(((Number(it.quantity) || 1) * (Number(it.unitPrice) || 0) + Number.EPSILON) * 100) / 100,
      isCustom,
    }
  })

  if (!form.value.serviceTypes.length) {
    formError.value = 'Por favor seleccioná al menos un tipo de trabajo a realizar.'
    return
  }

  if (!validMoney(form.value.labor, 8) || !validMoney(form.value.vat) || form.value.vat > 100
    || form.value.items.some(item => !validMoney(item.unitPrice)
      || !/^\d{1,12}(?:\.\d{1,3})?$/.test(String(item.quantity)) || item.quantity <= 0)) {
    formError.value = 'Formato inválido.'
    return
  }
  if (archivedParts.value.length) {
    formError.value = 'El presupuesto incluye repuestos archivados. Quitá o reemplazá esos renglones para poder guardar.'
    return
  }

  if (auth.authorization.value) {
    const vehicle = selectedVehicle.value
    if (!vehicle) return
    saving.value = true
    try {
      const id = props.budget?.id
      const result = await $fetch<PresupuestoApi>(id ? `/api/presupuestos/${id}` : '/api/presupuestos', {
        method: id ? 'PUT' : 'POST', headers: auth.authorization.value,
        body: {
          clienteId: selectedClientId.value ?? vehicle.client, vehiculoId: vehicle.id, descripcion: form.value.description.trim(),
          manoObra: form.value.labor, porcentajeIva: form.value.vat,
          tiposTrabajo: (Object.keys(tiposTrabajoPresupuesto) as TipoTrabajoPresupuesto[])
            .filter(tipo => form.value.serviceTypes.includes(tiposTrabajoPresupuesto[tipo])),
          items: form.value.items.map(item => ({
            repuestoId: item.partId === 'custom' ? null : Number(item.partId),
            descripcion: item.partId === 'custom' ? inicialMayuscula(item.customName.trim()) : null,
            cantidad: item.quantity, precioUnitario: item.unitPrice,
          })),
        },
      })
      const budget = adaptarPresupuesto(result)
      if (id) emit('updated', budget)
      else emit('created', budget)
    } catch (error) {
      formError.value = authError(error, 'No se pudo guardar el presupuesto.')
    } finally {
      saving.value = false
    }
    return
  }

  const id = Math.max(208, ...db.value.quotes.map((q) => q.id)) + 1
  const newBudget: Budget = {
    id,
    vehicle: Number(form.value.vehicle),
    description: form.value.description,
    labor: Number(form.value.labor) || 0,
    materials: partsSubtotal.value,
    items: processedItems,
    subtotal: netSubtotal.value,
    tax: vatAmount.value,
    total: grandTotal.value,
    status: 'Pendiente',
    date: new Date().toISOString().slice(0, 10),
    serviceTypes: [...form.value.serviceTypes],
  }

  db.value.quotes.unshift(newBudget)
  emit('created', newBudget)
}

function validMoney(value: unknown, integerDigits = 12) {
  return typeof value === 'number' && Number.isFinite(value) && new RegExp(`^\\d{1,${integerDigits}}(?:\\.\\d{1,2})?$`).test(String(value))
}

function onFormEnter(e: KeyboardEvent) {
  const target = e.target as HTMLElement | null
  if (!target || target.tagName === 'BUTTON' || target.closest('.parts-builder-section')) return
  e.preventDefault()
}
</script>

<template>
  <CommonFormPage v-if="open" class="budget-content">
    <div class="dialog-header">
      <div>
        <h2>{{ budget ? 'Editar presupuesto' : 'Nuevo presupuesto' }}</h2>
      </div>
      <button class="icon-button" aria-label="Cerrar" :disabled="saving" @click="close">
        <X :size="18" />
      </button>
    </div>

    <form @submit.prevent="submit" @keydown.enter="onFormEnter" class="entry-form">
      <div class="form-fields" :inert="saving || loading || !!dataError">
        <div class="form-grid selectores-titular">
          <div class="field-block">
            <label for="presupuesto-cliente" class="block-label">Cliente <span class="required-star">*</span></label>
            <div v-if="selectedClient" class="seleccion-titular">
              <strong>{{ selectedClient.name }}</strong>
              <button type="button" class="icon-button quitar-seleccion" aria-label="Quitar cliente" title="Quitar cliente" @click="clearClient"><X :size="16" /></button>
            </div>
            <div v-else class="target-search-container">
              <div class="target-search-box">
                <Search :size="16" class="search-icon" />
                <input id="presupuesto-cliente" ref="searchInputRef" v-model="clientSearch" autocomplete="off" placeholder="Buscá por nombre o documento…" />
              </div>
              <div v-if="clientSearch.trim()" class="target-results-list">
                <div class="resultados-encabezado"><span>Clientes</span><span>{{ filteredClients.length }}</span></div>
                <button v-for="c in filteredClients" :key="c.id" type="button" class="target-result-item" @click="selectClient(c)">
                  <span class="resultado-datos"><strong>{{ c.name }}</strong><small>{{ c.doc || 'Sin documento' }}</small></span>
                  <ChevronRight :size="15" class="resultado-flecha" aria-hidden="true" />
                </button>
                <p v-if="!filteredClients.length" class="muted">No se encontraron clientes.</p>
              </div>
            </div>
          </div>
          <div class="field-block">
            <label for="presupuesto-vehiculo" class="block-label">Vehículo <span class="required-star">*</span></label>
            <div v-if="selectedVehicle" class="seleccion-titular">
              <span class="plate small-plate">{{ selectedVehicle.plate }}</span>
              <strong>{{ vehicleLabel(selectedVehicle.brand, selectedVehicle.model, selectedVehicle.version) }}</strong>
              <button type="button" class="icon-button quitar-seleccion" aria-label="Quitar vehículo" title="Quitar vehículo" @click="clearVehicle"><X :size="16" /></button>
            </div>
            <div v-else class="target-search-container">
              <div class="target-search-box">
                <Search :size="16" class="search-icon" />
                <input id="presupuesto-vehiculo" v-model="vehicleSearch" autocomplete="off" placeholder="Buscá por patente, marca o modelo…" @focus="vehicleSearchOpen = true" />
              </div>
              <div v-if="vehicleSearchOpen && (selectedClient || vehicleSearch.trim())" class="target-results-list">
                <div class="resultados-encabezado"><span>{{ selectedClient ? 'Vehículos del cliente' : 'Vehículos' }}</span><span>{{ filteredVehicles.length }}</span></div>
                <button v-for="v in filteredVehicles" :key="v.id" type="button" class="target-result-item" @click="selectVehicle(v)">
                  <CommonBrandLogo :brand="v.brand" class="resultado-marca" />
                  <span class="resultado-datos">
                    <strong>{{ vehicleLabel(v.brand, v.model, v.version) }}</strong>
                    <small><span class="resultado-patente">{{ v.plate }}</span> · {{ client(v.client)?.name }}</small>
                  </span>
                  <ChevronRight :size="15" class="resultado-flecha" aria-hidden="true" />
                </button>
                <p v-if="!filteredVehicles.length && vehicleSearch.trim() === debouncedSearch.trim()" class="muted">No se encontraron vehículos.</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Selector de Tipos de Trabajo a Realizar -->
        <CommonDependentFields :ready="!!selectedVehicle">
        <div class="field-block work-types-container">
          <fieldset class="work-type-field">
            <legend>Trabajo a realizar <span class="required-star">*</span></legend>
            <div class="work-type-options">
              <button v-for="option in availableWorkTypes" :key="option.value" type="button" class="button" :class="{ primary: form.serviceTypes.includes(option.value) }" :aria-pressed="form.serviceTypes.includes(option.value)" @click="toggleWork(option.value)">{{ option.label }}</button>
            </div>
          </fieldset>

        </div>

        <div class="form-grid budget-main-fields">
        <label>
          Detalle del trabajo / Diagnóstico
          <input
            v-model="form.description"
            maxlength="2000"
            required
            placeholder="Ej. Cambio de pastillas de freno y rectificación de discos"
          />
        </label>
        </div>

        <p v-if="archivedParts.length" class="muted" role="status">Este presupuesto incluye repuestos archivados: {{ archivedParts.map(part => part.name).join(', ') }}. Quitá o reemplazá esos renglones para poder guardar.</p>
        <CommonEditorRepuestos v-model="form.items" :parts="parts" :min-quantity="0.001" />

        <div class="form-grid budget-main-fields labor-iva-fields">
          <label>
            Mano de obra ($)
            <input
              v-model.number="form.labor"
              type="number"
              min="0"
              max="99999999.99"
              step="0.01"
              :aria-invalid="!validMoney(form.labor, 8)"
              required
              placeholder="0"
            />
            <CommonFormatoInvalido v-if="!validMoney(form.labor, 8)" />
          </label>
          <label v-if="auth.authorization.value">IVA (%)
            <input v-model.number="form.vat" type="number" min="0" max="100" step="0.01" required :aria-invalid="!validMoney(form.vat) || form.vat > 100" />
            <CommonFormatoInvalido v-if="!validMoney(form.vat) || form.vat > 100" />
          </label>
        </div>

        <!-- Breakdown with VAT / IVA 21% -->
        <div class="quote-summary-card">
          <div class="summary-line">
            <span>Mano de obra</span>
            <strong>{{ money(form.labor || 0) }}</strong>
          </div>
          <div class="summary-line">
            <span>Repuestos e insumos</span>
            <strong>{{ money(partsSubtotal) }}</strong>
          </div>
          <div class="summary-line subtotal-line">
            <span>Subtotal Neto</span>
            <strong>{{ money(netSubtotal) }}</strong>
          </div>
          <div class="summary-line vat-line">
            <span style="display: flex; align-items: center; gap: 4px">
              IVA ({{ form.vat }}%)
            </span>
            <strong>{{ money(vatAmount) }}</strong>
          </div>
          <div class="summary-line total-line">
            <span>Total con IVA</span>
            <strong class="grand-total-display">{{ money(grandTotal) }}</strong>
          </div>
        </div>

        </CommonDependentFields>
        <p v-if="formError" class="error-message" role="alert">
          {{ formError }}
        </p>
      </div>
      <p v-if="loading" class="muted" role="status">Cargando datos del taller…</p>
      <p v-if="dataError" class="error-message" role="alert">{{ dataError }} <button type="button" class="button small" @click="dataRevision++">Reintentar</button></p>

      <footer class="dialog-footer modal-footer">
        <button type="button" class="button" :disabled="saving" @click="close">Cancelar</button>
        <button type="submit" class="button primary" :disabled="!selectedVehicle || saving || loading || !!dataError">
          <Check :size="16" /> {{ budget ? 'Guardar cambios' : 'Guardar y generar presupuesto' }}
        </button>
      </footer>
    </form>
  </CommonFormPage>
</template>

<style scoped>
.budget-content {
  width: 100%;
}
.budget-content .dialog-header { padding: 10px 16px; }
.budget-content .form-fields { padding: 12px 16px; gap: 8px; }
.budget-content .modal-footer { padding: 8px 16px; }
.budget-content .budget-main-fields { grid-template-columns: minmax(0, 2fr) minmax(0, 1fr); }
.budget-content .labor-iva-fields { grid-template-columns: repeat(2, minmax(0, 1fr)); }

.selectores-titular { grid-template-columns: repeat(2, minmax(0, 1fr)); align-items: start; }
.selectores-titular .field-block { min-width: 0; }
.seleccion-titular { display: flex; align-items: center; gap: 8px; height: 40px; box-sizing: border-box; padding: 0 10px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); font-size: 12px; }
.seleccion-titular strong { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.seleccion-titular .quitar-seleccion { margin-left: auto; flex-shrink: 0; border: 0; background: transparent; }
.target-search-container { position: relative; }
.buscador-deshabilitado { opacity: .55; }
.target-search-box { min-height: 40px; }
.target-search-box input { min-width: 0; height: 38px; color: inherit; }
.target-results-list { position: absolute; top: 100%; left: 0; right: 0; z-index: 50; display: grid; gap: 2px; margin-top: 8px; padding: 6px; max-height: 280px; overflow-y: auto; background: var(--surface); border: 1px solid var(--line); border-radius: 12px; box-shadow: 0 12px 32px #0002; }
.resultados-encabezado { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 6px 8px 10px; color: var(--muted); font-size: 10px; font-weight: 600; }
.resultados-encabezado > span:last-child { padding: 2px 6px; border-radius: 5px; background: var(--line); font-variant-numeric: tabular-nums; }
.target-result-item { width: 100%; display: flex; align-items: center; gap: 8px; padding: 6px 8px; border: 0; border-radius: 8px; background: transparent; color: inherit; text-align: left; cursor: pointer; }
.target-result-item:is(:hover, :focus-visible) { background: #eff6ff; outline: none; box-shadow: inset 0 0 0 1px #2563eb30; }
.resultado-marca { flex: 0 0 16px; width: 16px; height: 16px; }
.resultado-datos { display: grid; gap: 2px; min-width: 0; flex: 1; }
.resultado-datos strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; font-weight: 600; }
.resultado-datos small { color: var(--muted); font-size: 11px; }
.resultado-patente { letter-spacing: .06em; font-variant-numeric: tabular-nums; }
.resultado-flecha { flex-shrink: 0; color: var(--muted); opacity: .5; }
.target-result-item:is(:hover, :focus-visible) .resultado-flecha { opacity: 1; color: #2563eb; }
.target-results-list > p { margin: 0; padding: 12px 8px; font-size: 12px; }
:global(html.dark) .target-result-item:focus-visible { background: #0a84ff24; }

/* Summary Card with VAT */
.quote-summary-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 8px 12px;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.summary-line {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  font-size: 11px;
  line-height: 1.2;
  color: #475569;
}

.summary-line strong {
  color: #0f172a;
}

.subtotal-line {
  border-top: 1px solid #e2e8f0;
  padding-top: 6px;
  margin-top: 2px;
  font-weight: 600;
}

.vat-line {
  color: #2563eb;
  font-weight: 600;
}

.total-line {
  border-top: 2px solid #0f172a;
  padding-top: 8px;
  margin-top: 4px;
  font-size: 12.5px;
  font-weight: 800;
  color: #0f172a;
}

.grand-total-display {
  font-family: 'Public Sans', sans-serif;
  font-size: 16px;
  line-height: 1.2;
  font-weight: 800;
  color: #2563eb;
}

/* Work Types & Oil Spec */
.work-types-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.work-type-field { border: 0; padding: 0; margin: 0; min-width: 0; }
.work-type-field legend { margin-bottom: 8px; font-size: 13px; font-weight: 600; }
.work-type-options { display: flex; flex-wrap: wrap; gap: 8px; }

/* Dark Mode Overrides */
:global(html.dark .quote-summary-card) {
  background: #202023;
  border-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark .summary-line) {
  color: #a1a1a6;
}

:global(html.dark .summary-line strong) {
  color: #ffffff;
}

:global(html.dark .subtotal-line) {
  border-top-color: rgba(255, 255, 255, 0.08);
}

:global(html.dark .vat-line) {
  color: #64d2ff;
}

:global(html.dark .total-line) {
  border-top-color: rgba(255, 255, 255, 0.2);
  color: #ffffff;
}

:global(html.dark .grand-total-display) {
  color: #64d2ff;
}

.quote-summary-card .summary-line { border: 0; padding: 0; margin: 0; }
@media (max-width: 760px) {
  .selectores-titular { grid-template-columns: 1fr; }
}
@media (max-width: 540px) {
  .budget-content .budget-main-fields { grid-template-columns: 1fr; }
  .quote-summary-card { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .quote-summary-card .total-line { grid-column: 1 / -1; }
}

</style>
