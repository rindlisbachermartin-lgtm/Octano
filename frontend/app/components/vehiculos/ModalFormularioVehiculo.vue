<script setup lang="ts">
import { avanzarConEnter } from '~/utils/formKeyboard'
import type { Vehicle } from '~/types'
import { Check, X, Search } from '@lucide/vue'
import type { Client } from '~/types';
import type { VehiculoApi } from '~/utils/vehiculoApi'

const props = defineProps<{
  open: boolean
  vehicle?: Vehicle | null
  clientId?: number | null
  initialPlate?: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created', vehicle: Vehicle): void
  (e: 'updated', vehicle: Vehicle): void
  (e: 'transfer', vehicle: Vehicle): void
}>()

const formError = ref('')
const clientSearch = ref('')
const debouncedSearch = useDebouncedValue(clientSearch)
const searchInputRef = ref<HTMLInputElement | null>(null)
const auth = useAuthApi()

const guardando = ref(false)
const titularReal = ref<Client | null>(null)
const { marcaId, modeloId, versionId, marcas, modelos: modelosApi, versiones: versionesApi,
  cargando: cargandoCatalogo, errores: erroresCatalogo, cargar, abrir: abrirCatalogo, cerrar: cerrarCatalogo } = useCatalogoVehiculos()
const busqueda = useBusquedaTitulares(debouncedSearch, () => props.open)
const catalogoCargando = computed(() => Object.values(cargandoCatalogo).some(Boolean))

function cerrar() {
  if (!guardando.value) emit('close')
}
useModalEscape(() => props.open, cerrar)

const form = ref({
  client: '' as string | number,
  plate: '',
  brand: '',
  model: '',
  year: 2026,
  version: '',
  engine: '',
  km: 0 as number | '',
})

const selectedClient = computed(() =>
  titularReal.value
)

const filteredClients = computed(() => {
  return busqueda.resultados.value

})

watch(
  () => props.open,
  (isOpen) => {
    if (!isOpen) {
      cerrarCatalogo()
      return
    }
    if (isOpen) {
      form.value = props.vehicle ? {
        client: props.vehicle.client,
        plate: props.vehicle.plate,
        brand: props.vehicle.brand,
        model: props.vehicle.model,
        year: props.vehicle.year,
        version: props.vehicle.version || '',
        engine: props.vehicle.engine,
        km: props.vehicle.registro?.kilometraje === null ? '' : props.vehicle.km,
      } : {
        client: props.clientId ?? '',
        plate: props.initialPlate || '',
        brand: '',
        model: '',
        year: 2026,
        version: '',
        engine: '',
        km: '',
      }
      const registro = props.vehicle?.registro
      titularReal.value = props.vehicle && registro
        ? { id: props.vehicle.client, name: registro.titular, active: true, doc: '', phone: '', email: '' } : null
      void abrirCatalogo(registro)
      clientSearch.value = ''
      formError.value = ''
      nextTick(() => {
        searchInputRef.value?.focus()
      })
    }
  },
  { immediate: true }
)

function selectClient(client: Client) {
  titularReal.value = client
  form.value.client = client.id
  clientSearch.value = ''
  formError.value = ''
}

function clearClient() {
  titularReal.value = null
  form.value.client = ''
  clientSearch.value = ''
  nextTick(() => {
    searchInputRef.value?.focus()
  })
}

const formatosVehiculo = computed(() => ({
  plate: /^(?:[A-Z]{3}[- ]?\d{3}|[A-Z]{2}[- ]?\d{3}[- ]?[A-Z]{2})$/.test(form.value.plate.toUpperCase().trim()),
  year: Number.isInteger(Number(form.value.year)) && form.value.year >= 1900 && form.value.year <= new Date().getFullYear() + 1,
  km: form.value.km === '' || (Number.isInteger(form.value.km) && form.value.km >= 0 && form.value.km <= 9999999),
}))
const revisarFormato = ref(false)
watch(() => props.open, () => { revisarFormato.value = false })

async function submit() {
  if (guardando.value) return
  formError.value = ''
  if (!form.value.client) {
    formError.value = 'Por favor buscá y seleccioná un cliente titular.'
    return
  }

  const rawPlate = form.value.plate.toUpperCase().trim()
  revisarFormato.value = true
  if (!Object.values(formatosVehiculo.value).every(Boolean)) return
  const compactPlate = rawPlate.replace(/[- ]/g, '')
  const plate = compactPlate.length === 6
    ? `${compactPlate.slice(0, 3)}-${compactPlate.slice(3)}`
    : `${compactPlate.slice(0, 2)}-${compactPlate.slice(2, 5)}-${compactPlate.slice(5)}`

  const headers = auth.authorization.value
  if (!headers || catalogoCargando.value) return
  if (!marcaId.value || !modeloId.value) {
    formError.value = 'Seleccioná una marca y un modelo.'
    return
  }
  const id = props.vehicle?.id
  guardando.value = true
  try {
    const respuesta = await $fetch<VehiculoApi>(id ? `/api/vehiculos/${id}` : '/api/vehiculos', {
      method: id ? 'PUT' : 'POST', headers,
      body: {
        clienteId: Number(form.value.client), modeloId: modeloId.value, versionId: versionId.value || null,
        patente: plate, anio: form.value.year, kilometraje: form.value.km === '' ? null : form.value.km,
      },
    })
    const vehiculo = adaptarVehiculo(respuesta)
    if (id) emit('updated', vehiculo)
    else emit('created', vehiculo)
  } catch (err) {
    formError.value = authError(err, 'No pudimos guardar el vehículo.')
  } finally {
    guardando.value = false
  }

}
</script>

<template>
  <CommonFormPage v-if="open">
    <div class="dialog-header">
      <h2>{{ vehicle ? 'Editar vehículo' : 'Registrar vehículo' }}</h2>
      <button class="icon-button" aria-label="Cerrar" :disabled="guardando" @click="cerrar">
        <X :size="18" />
      </button>
    </div>

    <form @keydown.enter="avanzarConEnter" @submit.prevent="submit" class="entry-form" novalidate>
      <div class="form-fields">
        <p class="muted">Los campos marcados con * son obligatorios.</p>
        <!-- Client Search Field -->
        <div class="field-block">
          <label class="block-label">
            Cliente titular <span class="required-star">*</span>
          </label>

          <!-- Selected Client Card -->
          <div v-if="selectedClient" class="selected-client-card">
            <div class="client-info">
              <strong class="client-title">{{ selectedClient.name }}</strong>
              <div class="client-sub">
                <span v-if="selectedClient.doc" class="client-meta">DNI {{ selectedClient.doc }}</span>
                <span v-if="selectedClient.doc && selectedClient.phone" class="target-dot">·</span>
                <span v-if="selectedClient.phone" class="client-meta">Tel. {{ selectedClient.phone }}</span>
              </div>
            </div>
            <button
              v-if="!vehicle"
              type="button"
              class="button small change-client-btn"
              :disabled="guardando"
              @click="clearClient"
            >
              Cambiar
            </button>
          </div>

          <!-- Client Search Input & Results -->
          <div v-else class="client-search-container">
            <div class="client-search-box">
              <Search :size="16" class="search-icon" />
              <input
                ref="searchInputRef"
                v-model="clientSearch"
                type="text"
                placeholder="Escribí para buscar por nombre, DNI o teléfono..."
                autocomplete="off"
                maxlength="90"
              />
              <button
                v-if="clientSearch"
                type="button"
                class="icon-button clear-icon-btn"
                @click="clientSearch = ''"
                aria-label="Limpiar búsqueda"
              >
                <X :size="14" />
              </button>
            </div>

            <!-- Client Results List -->
            <p v-if="!clientSearch.trim()" class="muted search-hint">Empezá a escribir para buscar clientes.</p>
            <div v-else class="client-results-list">
              <button
                v-for="c in filteredClients"
                :key="c.id"
                type="button"
                class="client-result-item"
                @click="selectClient(c)"
              >
                <div class="result-details">
                  <strong class="result-name">{{ c.name }}</strong>
                  <span class="result-meta">
                    {{ c.doc ? 'DNI ' + c.doc : '' }}
                    {{ c.phone ? ' · ' + c.phone : '' }}
                    {{ c.email ? ' · ' + c.email : '' }}
                  </span>
                </div>
              </button>

              <p v-if="busqueda.error.value" class="error-message" role="alert">{{ busqueda.error.value }}</p>
              <p v-else-if="busqueda.cargando.value" class="muted" role="status">Buscando clientes…</p>
              <div v-else-if="!filteredClients.length && clientSearch.trim() === debouncedSearch.trim()" class="client-empty-state">
                <p>No se encontraron clientes para "<strong>{{ clientSearch }}</strong>"</p>
                <small>Podés verificar el nombre o dar de alta al cliente en la sección Clientes.</small>
              </div>
            </div>
          </div>

        </div>

        <CommonDependentFields :ready="!!selectedClient" hint="Primero seleccioná el cliente para completar los datos del vehículo.">
        <label>
          Patente *
          <input
            v-model="form.plate"
            aria-label="Patente"
            :aria-invalid="(revisarFormato || !!form.plate) && !formatosVehiculo.plate"
            required
            maxlength="10"
            placeholder="ABC-123 o AB-123-CD"
          />
          <CommonFormatoInvalido v-if="(revisarFormato || form.plate) && !formatosVehiculo.plate" />
        </label>

        <div class="form-grid">

            <label>
              Marca *
              <select v-model="marcaId" :disabled="guardando || catalogoCargando" required>
                <option value="">{{ cargandoCatalogo.marcas ? 'Cargando marcas…' : 'Seleccionar marca' }}</option>
                <option v-for="opcion in marcas" :key="opcion.id" :value="opcion.id">{{ opcion.nombre }}</option>
              </select>
            </label>
            <label>
              Modelo *
              <select v-model="modeloId" :disabled="guardando || catalogoCargando || !marcaId" required>
                <option value="">{{ cargandoCatalogo.modelos ? 'Cargando modelos…' : 'Seleccionar modelo' }}</option>
                <option v-for="opcion in modelosApi" :key="opcion.id" :value="opcion.id">{{ opcion.nombre }}</option>
              </select>
            </label>
            <label>
              Versión
              <select v-model="versionId" :disabled="guardando || catalogoCargando || !modeloId">
                <option value="">{{ cargandoCatalogo.versiones ? 'Cargando versiones…' : 'Sin versión' }}</option>
                <option v-for="opcion in versionesApi" :key="opcion.id" :value="opcion.id">{{ opcion.nombre }}</option>
              </select>
            </label>

          <label>
            Año *
            <input
              v-model.number="form.year"
              aria-label="Año"
              required
              type="number"
              min="1900"
              :max="new Date().getFullYear() + 1"
              :aria-invalid="(revisarFormato || !!form.year) && !formatosVehiculo.year"
            />
            <CommonFormatoInvalido v-if="(revisarFormato || form.year) && !formatosVehiculo.year" />
          </label>
        </div>

          <div v-for="(error, nivel) in erroresCatalogo" :key="nivel">
            <template v-if="error">
              <p class="error-message" role="alert">{{ error }}</p>
              <button type="button" class="button" @click="cargar(nivel)">Reintentar</button>
            </template>
          </div>

        <label>
          Kilometraje
          <input
            v-model.number="form.km"
            type="number"
            min="0"
            max="9999999"
            :aria-invalid="!formatosVehiculo.km"
          />
          <CommonFormatoInvalido v-if="!formatosVehiculo.km" />
        </label>

        </CommonDependentFields>
        <p v-if="formError" class="error-message" role="alert">
          {{ formError }}
        </p>
      </div>

      <footer class="dialog-footer modal-footer">
        <button v-if="vehicle && vehicle.registro?.activo !== false" type="button" class="button" :disabled="guardando" @click="emit('transfer', vehicle)">Cambiar titular</button>
        <button type="button" class="button" :disabled="guardando" @click="cerrar">Cancelar</button>
        <button type="submit" class="button primary" :disabled="!selectedClient || guardando || catalogoCargando">
          <Check :size="16" />{{ guardando ? 'Guardando…' : 'Guardar vehículo' }}
        </button>
      </footer>
    </form>
  </CommonFormPage>
</template>

<style scoped>

.field-block {
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.block-label {
  font-size: 11px;
  font-weight: 500;
  color: var(--ink);
}
.required-star {
  color: #ef4444;
}

.client-search-container {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.client-search-box {
  display: flex;
  align-items: center;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #ffffff;
  padding: 0 10px;
  gap: 8px;
  transition: border-color 150ms ease, box-shadow 150ms ease;
}
.client-search-box:focus-within {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
}
.search-icon {
  color: #64748b;
  flex-shrink: 0;
}
.client-search-box input {
  border: none !important;
  box-shadow: none !important;
  padding: 10px 0 !important;
  margin: 0 !important;
  font-size: 11px;
  flex: 1;
  background: transparent !important;
}
.clear-icon-btn {
  width: 20px;
  height: 20px;
  color: #94a3b8;
}
.client-results-list {
  max-height: 185px;
  overflow-y: auto;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.05);
}
.client-result-item {
  display: flex;
  width: 100%;
  text-align: left;
  background: transparent;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: background 120ms ease;
}
.client-result-item:last-child {
  border-bottom: none;
}
.client-result-item:hover {
  background: #eff6ff;
}
.result-details {
  flex: 1;
  min-width: 0;
}
.result-name {
  display: block;
  font-size: 11px;
  color: #0f172a;
}
.result-meta {
  display: block;
  font-size: 9px;
  color: #64748b;
  margin-top: 2px;
}
.client-empty-state {
  padding: 16px;
  text-align: center;
  font-size: 11px;
  color: #64748b;
}
.client-empty-state p {
  margin-bottom: 4px;
  color: #475569;
}

:global(html.dark .client-search-box) {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(255, 255, 255, 0.12);
}
:global(html.dark .client-search-box:focus-within) {
  border-color: #0a84ff;
  box-shadow: 0 0 0 3px rgba(10, 132, 255, 0.25);
}
:global(html.dark .client-results-list) {
  background: #252528;
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.4);
}
:global(html.dark .client-result-item) {
  border-bottom-color: rgba(255, 255, 255, 0.06);
}
:global(html.dark .client-result-item:hover) {
  background: rgba(10, 132, 255, 0.12);
}
:global(html.dark .result-name) {
  color: #ffffff;
}

</style>
