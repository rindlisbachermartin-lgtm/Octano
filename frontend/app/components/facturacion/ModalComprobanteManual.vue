<script setup lang="ts">
import { avanzarConEnter } from '~/utils/formKeyboard'
import { Check, X, Search, ChevronRight } from '@lucide/vue'
import { authError } from '~/composables/useAuthApi'
import type { SolicitudComprobante } from '~/composables/useComprobantes'
import type { ComprobanteApi, OrdenPendienteApi } from '~/utils/comprobanteApi'
import type { Client, Part } from '~/types'
import type { PartEditorItem } from '~/types/partEditor'
import { paginasPresupuestos } from '~/utils/presupuestoApi'
import { adaptarRepuesto, type RepuestoApi } from '~/utils/repuestoApi'
import { adaptarTitular, type ClienteTitularApi } from '~/utils/vehiculoApi'
import { etiquetaIva, valoresIva } from '~/utils/ivaApi'

interface VehiculoOpcion {
  id: number
  clienteId: number
  patente: string
  marca: string
  modelo: string
  clienteNombre: string
}

const props = defineProps<{
  open: boolean
  orden?: OrdenPendienteApi | null
  comprobante?: ComprobanteApi | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'guardar', solicitud: SolicitudComprobante, id?: number): void
}>()

const auth = useAuthApi()
const { aliquotas, error: errorIva } = useAliquotasIva()
useModalEscape(() => props.open, () => emit('close'))

const vehiculoFijo = computed(() => props.comprobante?.vehiculoId ?? props.orden?.vehiculoId ?? null)
const vehiculoSeleccionado = ref<VehiculoOpcion | null>(null)
const vehiculoRepuestosId = computed(() => vehiculoFijo.value ?? vehiculoSeleccionado.value?.id ?? null)
const clienteSeleccionado = ref<Client | null>(null)
const titularCompleto = computed(() => !!vehiculoFijo.value || !!(clienteSeleccionado.value && vehiculoSeleccionado.value?.clienteId === clienteSeleccionado.value.id))
const clienteSearch = ref('')
const debouncedClienteSearch = useDebouncedValue(clienteSearch)
const busquedaClientes = useBusquedaTitulares(debouncedClienteSearch, () => props.open && !vehiculoFijo.value && !clienteSeleccionado.value)
const opciones = ref<VehiculoOpcion[]>([])
const vehiculoSearch = ref('')
const debouncedVehiculoSearch = useDebouncedValue(vehiculoSearch)
const vehiculoBusquedaAbierta = ref(false)
const { matches } = useHelpers()
const vehiculosFiltrados = computed(() => opciones.value.filter((v) => matches(vehiculoSearch.value, v.patente, v.marca, v.modelo)))
const condiciones = ref<{ id: number; nombre: string }[]>([])
const condicionIvaEmisorId = ref<number | null>(null)
const condicionIvaReceptorId = ref<number | null>(null)
const puntoVenta = ref(3)
const buscando = ref(false)
const errorBusqueda = ref('')
const formError = ref('')
const parts = ref<Part[]>([])
const repuestosCompatibles = ref<Part[]>([])
const cargandoRepuestos = ref(false)
const errorRepuestos = ref('')

const form = ref({
  descripcion: '',
  manoObra: 0,
  porcentajeIva: 21,
  items: [] as PartEditorItem[],
})

const opcionesIva = computed(() => valoresIva(aliquotas.value, form.value.porcentajeIva))
const repuestosArchivados = computed(() => parts.value.filter((part) => part.archived && form.value.items.some((item) => item.partId === part.id)))

const dinero = (valor: number) =>
  Number(valor || 0).toLocaleString('es-AR', { style: 'currency', currency: 'ARS', minimumFractionDigits: 2, maximumFractionDigits: 2 })

const materiales = computed(() => form.value.items.reduce((sum, item) =>
  sum + Number(item.cantidad || 0) * Number(item.precioUnitario || 0), 0))
const subtotal = computed(() => Number(form.value.manoObra || 0) + materiales.value)
const montoIva = computed(() => Math.round(subtotal.value * Number(form.value.porcentajeIva || 0) * 100) / 10000)
const total = computed(() => Math.round((subtotal.value + montoIva.value) * 100) / 100)
const tipoPrevisto = computed(() => {
  const emisor = condiciones.value.find((item) => item.id === condicionIvaEmisorId.value)?.nombre
  const receptor = condiciones.value.find((item) => item.id === condicionIvaReceptorId.value)?.nombre
  if (!emisor || !receptor) return '—'
  if (emisor !== 'IVA Responsable Inscripto') return 'C'
  return ['IVA Responsable Inscripto', 'Responsable Monotributo', 'Monotributista Social', 'Monotributo Trabajador Independiente Promovido'].includes(receptor) ? 'A' : 'B'
})

watch(() => [props.open, auth.authorization.value, vehiculoRepuestosId.value, props.comprobante?.id] as const, ([open, headers, vehiculoId], _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  parts.value = []
  repuestosCompatibles.value = []
  errorRepuestos.value = ''
  cargandoRepuestos.value = false
  if (!open || !headers || !vehiculoId) return
  cargandoRepuestos.value = true
  void (async () => {
    try {
      const actuales = await paginasPresupuestos<RepuestoApi>('/api/repuestos', headers, controller.signal, { activo: true, vehiculoId })
      const ids = [...new Set(form.value.items.map((item) => item.partId)
        .filter((id): id is number => typeof id === 'number' && !actuales.some((part) => part.id === id)))]
      const anteriores = await Promise.all(ids.map((id) => $fetch<RepuestoApi>(`/api/repuestos/${id}`, { headers, signal: controller.signal })))
      if (!controller.signal.aborted) {
        repuestosCompatibles.value = actuales.map(adaptarRepuesto)
        parts.value = [...actuales, ...anteriores].map(adaptarRepuesto)
      }
    } catch (err) {
      if (!controller.signal.aborted) errorRepuestos.value = authError(err, 'No pudimos cargar los repuestos.')
    } finally {
      if (!controller.signal.aborted) cargandoRepuestos.value = false
    }
  })()
})
watch(condicionIvaEmisorId, (id) => {
  if (!id || !props.open) return
  const nombre = condiciones.value.find((item) => item.id === id)?.nombre
  if (nombre && nombre !== 'IVA Responsable Inscripto') form.value.porcentajeIva = 0
  else if (nombre && Number(form.value.porcentajeIva) === 0) form.value.porcentajeIva = 21
})

async function cargarCondicionCliente(clienteId: number) {
  try {
    const cliente = await $fetch<{ condicionIvaId: number }>(`/api/clientes/${clienteId}`, { headers: auth.authorization.value ?? undefined })
    if (vehiculoFijo.value || clienteSeleccionado.value?.id === clienteId) condicionIvaReceptorId.value = cliente.condicionIvaId
  } catch (err) {
    errorBusqueda.value = authError(err, 'No pudimos cargar la condición de IVA del cliente.')
  }
}

onMounted(async () => {
  if (!auth.authorization.value) return
  try {
    const [lista, taller] = await Promise.all([
      $fetch<{ id: number; nombre: string }[]>('/api/condiciones-iva', { headers: auth.authorization.value }),
      $fetch<{ condicionIva: { id: number } | null }>(`/api/talleres/${auth.user.value?.tallerId}`, { headers: auth.authorization.value }),
    ])
    condiciones.value = lista
    condicionIvaEmisorId.value = condiciones.value.find((item) => item.nombre === props.comprobante?.emisor?.condicionIva)?.id ?? taller.condicionIva?.id ?? null
    if (props.comprobante) condicionIvaReceptorId.value = condiciones.value.find((item) => item.nombre === props.comprobante?.receptor?.condicionIva)?.id ?? null
  } catch (err) {
    errorBusqueda.value = authError(err, 'No pudimos cargar las condiciones de IVA.')
  }
})

watch(clienteSeleccionado, async (cliente, _, onCleanup) => {
  const consulta = new AbortController()
  onCleanup(() => consulta.abort())
  opciones.value = []
  if (vehiculoSeleccionado.value?.clienteId !== cliente?.id) vehiculoSeleccionado.value = null
  vehiculoSearch.value = ''
  vehiculoBusquedaAbierta.value = false
  if (!vehiculoFijo.value) condicionIvaReceptorId.value = null
  errorBusqueda.value = ''
  buscando.value = false
  const headers = auth.authorization.value
  if (!cliente || !headers || vehiculoFijo.value) return
  void cargarCondicionCliente(cliente.id)
  buscando.value = true
  try {
    const vehiculos = await paginasPresupuestos<VehiculoOpcion>('/api/vehiculos', headers, consulta.signal, { clienteId: cliente.id, activo: true })
    if (!consulta.signal.aborted) opciones.value = vehiculos
  } catch (err) {
    if (!consulta.signal.aborted) errorBusqueda.value = authError(err, 'No pudimos buscar vehículos.')
  } finally {
    if (!consulta.signal.aborted) buscando.value = false
  }
})

watch([debouncedVehiculoSearch, clienteSeleccionado, () => props.open], async ([texto, cliente, open], _, onCleanup) => {
  const consulta = new AbortController()
  onCleanup(() => consulta.abort())
  if (cliente || vehiculoFijo.value) return
  opciones.value = []
  buscando.value = false
  const headers = auth.authorization.value
  const q = texto.trim()
  if (!open || !q || !vehiculoSearch.value.trim() || !headers) return
  errorBusqueda.value = ''
  buscando.value = true
  try {
    const respuesta = await $fetch<{ contenido: VehiculoOpcion[] }>('/api/vehiculos', {
      headers, query: { q, activo: true, tamano: 20 }, signal: consulta.signal,
    })
    if (!consulta.signal.aborted) opciones.value = respuesta.contenido
  } catch (err) {
    if (!consulta.signal.aborted) errorBusqueda.value = authError(err, 'No pudimos buscar vehículos.')
  } finally {
    if (!consulta.signal.aborted) buscando.value = false
  }
})

watch(() => [props.open, props.orden?.ordenId, props.comprobante?.id] as const, () => {
  if (!props.open) return
  const c = props.comprobante
  form.value = {
    descripcion: c?.descripcion ?? props.orden?.descripcion ?? '',
    manoObra: Number(c?.manoObra ?? 0),
    porcentajeIva: Number(c?.porcentajeIva ?? 21),
    items: (c?.items ?? []).map((item, index): PartEditorItem => ({
      key: index + 1,
      partId: item.repuestoId ?? 'custom',
      customName: item.descripcion,
      searchQuery: item.descripcion,
      quantity: Number(item.cantidad),
      unitPrice: Number(item.precioUnitario),
    })),
  }
  puntoVenta.value = c?.puntoVenta ?? 3
  condicionIvaReceptorId.value = condiciones.value.find((item) => item.nombre === c?.receptor?.condicionIva)?.id ?? null
  if (!c && props.orden?.clienteId) void cargarCondicionCliente(props.orden.clienteId)
  vehiculoSeleccionado.value = null
  clienteSeleccionado.value = null
  clienteSearch.value = ''
  vehiculoSearch.value = ''
  vehiculoBusquedaAbierta.value = false
  opciones.value = []
  formError.value = ''
  errorBusqueda.value = ''
})

async function seleccionarVehiculo(v: VehiculoOpcion) {
  if (!clienteSeleccionado.value) {
    try {
      const cliente = await $fetch<ClienteTitularApi>(`/api/clientes/${v.clienteId}`, { headers: auth.authorization.value ?? undefined })
      if (!props.open || clienteSeleccionado.value) return
      if (!cliente.activo) {
        errorBusqueda.value = 'El titular de este vehículo está inactivo. Seleccioná otro vehículo.'
        return
      }
      clienteSeleccionado.value = adaptarTitular(cliente)
      clienteSearch.value = ''
    } catch (err) {
      errorBusqueda.value = authError(err, 'No pudimos cargar el cliente del vehículo.')
      return
    }
  }
  if (clienteSeleccionado.value.id !== v.clienteId) return
  vehiculoSeleccionado.value = v
  vehiculoBusquedaAbierta.value = false
  vehiculoSearch.value = ''
}

function submit() {
  formError.value = ''
  if (!titularCompleto.value) {
    formError.value = 'Seleccioná el cliente y su vehículo para continuar.'
    return
  }
  if (cargandoRepuestos.value || errorRepuestos.value) {
    formError.value = errorRepuestos.value || 'Esperá a que se carguen los repuestos.'
    return
  }
  const vehiculoId = vehiculoFijo.value ?? vehiculoSeleccionado.value?.id
  if (!vehiculoId) {
    formError.value = 'Buscá y seleccioná el vehículo del cliente.'
    return
  }
  if (!form.value.descripcion.trim()) {
    formError.value = 'Ingresá el concepto del trabajo.'
    return
  }
  if (!Number.isFinite(total.value) || total.value <= 0) {
    formError.value = 'El total debe ser mayor a cero. Agregá mano de obra o repuestos.'
    return
  }
  if (form.value.items.some((item) => !item.partId || (item.partId === 'custom' && !item.customName.trim()) || !(Number(item.quantity) > 0) || !(Number(item.unitPrice) >= 0))) {
    formError.value = 'Revisá los renglones: descripción, cantidad y precio unitario deben ser válidos.'
    return
  }
  if (repuestosArchivados.value.length) {
    formError.value = 'La factura incluye repuestos archivados. Quitá o reemplazá esos renglones para poder guardar.'
    return
  }
  if (!condicionIvaEmisorId.value || !condicionIvaReceptorId.value || !Number.isInteger(puntoVenta.value) || puntoVenta.value < 1 || puntoVenta.value > 99999) {
    formError.value = 'Revisá las condiciones de IVA del taller y del cliente y el punto de venta.'
    return
  }

  const solicitud: SolicitudComprobante = {
    ordenId: props.orden?.ordenId ?? props.comprobante?.ordenId ?? null,
    vehiculoId,
    descripcion: form.value.descripcion.trim(),
    manoObra: Number(form.value.manoObra || 0),
    porcentajeIva: Number(form.value.porcentajeIva || 0),
    condicionIvaEmisorId: condicionIvaEmisorId.value,
    condicionIvaReceptorId: condicionIvaReceptorId.value,
    puntoVenta: puntoVenta.value,
    items: form.value.items.map((item) => ({
      ...(typeof item.partId === 'number' ? { repuestoId: item.partId } : {}),
      descripcion: item.partId === 'custom' ? item.customName.trim() : parts.value.find((part) => part.id === item.partId)?.name || item.searchQuery,
      cantidad: Number(item.quantity),
      precioUnitario: Number(item.precioUnitario),
    })),
  }
  emit('guardar', solicitud, props.comprobante?.id)
}

</script>

<template>
  <CommonFormPage v-if="open" class="comprobante-manual-form budget-content">
    <div class="dialog-header">
      <div>
        <span class="eyebrow">FACTURACIÓN INTERNA</span>
        <h2>
          {{ comprobante ? `Editar borrador #${comprobante.id}` : orden ? `Nuevo comprobante · OT #${orden.ordenId}` : 'Nuevo comprobante manual' }}
        </h2>
      </div>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')"><X :size="18" /></button>
    </div>

    <form @keydown.enter="avanzarConEnter" class="entry-form" @submit.prevent="submit">
      <div class="form-fields">
        <div class="form-grid selectores-titular">
          <div class="field-block">
            <label for="factura-cliente" class="block-label">Cliente <span class="required-star">*</span></label>
            <div v-if="vehiculoFijo" class="seleccion-titular"><strong>{{ orden?.clienteNombre ?? comprobante?.receptor?.nombre }}</strong></div>
            <div v-else-if="clienteSeleccionado" class="seleccion-titular">
              <strong>{{ clienteSeleccionado.name }}</strong>
              <button type="button" class="icon-button quitar-seleccion" aria-label="Quitar cliente" title="Quitar cliente" @click="clienteSeleccionado = null; clienteSearch = ''"><X :size="16" /></button>
            </div>
            <div v-else class="target-search-container">
              <div class="target-search-box">
                <Search :size="16" class="search-icon" />
                <input id="factura-cliente" v-model="clienteSearch" type="text" autocomplete="off" placeholder="Buscá por nombre o documento…" />
              </div>
              <div v-if="clienteSearch.trim()" class="target-results-list">
                <div class="resultados-encabezado"><span>Clientes</span><span>{{ busquedaClientes.resultados.value.length }}</span></div>
                <button v-for="cliente in busquedaClientes.resultados.value" :key="cliente.id" type="button" class="target-result-item" @click="clienteSeleccionado = cliente">
                  <span class="resultado-datos"><strong>{{ cliente.name }}</strong><small>{{ cliente.doc || 'Sin documento' }}</small></span>
                  <ChevronRight :size="15" class="resultado-flecha" aria-hidden="true" />
                </button>
                <p v-if="busquedaClientes.cargando.value" class="muted" role="status">Buscando clientes…</p>
                <p v-else-if="!busquedaClientes.resultados.value.length && clienteSearch.trim() === debouncedClienteSearch.trim() && !busquedaClientes.error.value" class="muted">No se encontraron clientes.</p>
                <p v-if="busquedaClientes.error.value" class="error-message" role="alert">{{ busquedaClientes.error.value }}</p>
              </div>
            </div>
          </div>
          <div class="field-block">
            <label for="factura-vehiculo" class="block-label">Vehículo <span class="required-star">*</span></label>
            <div v-if="vehiculoFijo" class="seleccion-titular">
              <span class="plate small-plate">{{ orden?.patente ?? comprobante?.vehiculo?.patente }}</span>
              <strong>{{ orden ? `${orden.marca} ${orden.modelo}` : `${comprobante?.vehiculo?.marca ?? ''} ${comprobante?.vehiculo?.modelo ?? ''}` }}</strong>
            </div>
            <div v-else-if="vehiculoSeleccionado" class="seleccion-titular">
              <span class="plate small-plate">{{ vehiculoSeleccionado.patente }}</span>
              <strong>{{ vehiculoSeleccionado.marca }} {{ vehiculoSeleccionado.modelo }}</strong>
              <button type="button" class="icon-button quitar-seleccion" aria-label="Quitar vehículo" title="Quitar vehículo" @click="vehiculoSeleccionado = null"><X :size="16" /></button>
            </div>
            <div v-else class="target-search-container">
              <div class="target-search-box">
                <Search :size="16" class="search-icon" />
                <input id="factura-vehiculo" v-model="vehiculoSearch" type="text" autocomplete="off" placeholder="Buscá por patente, marca o modelo…" @focus="vehiculoBusquedaAbierta = true" />
              </div>
              <div v-if="vehiculoBusquedaAbierta && (clienteSeleccionado || vehiculoSearch.trim())" class="target-results-list">
                <div class="resultados-encabezado"><span>{{ clienteSeleccionado ? 'Vehículos del cliente' : 'Vehículos' }}</span><span>{{ vehiculosFiltrados.length }}</span></div>
                <button v-for="v in vehiculosFiltrados" :key="v.id" type="button" class="target-result-item" @click="seleccionarVehiculo(v)">
                  <CommonBrandLogo :brand="v.marca" class="resultado-marca" />
                  <span class="resultado-datos"><strong>{{ v.marca }} {{ v.modelo }}</strong><small><span class="resultado-patente">{{ v.patente }}</span> · {{ v.clienteNombre }}</small></span>
                  <ChevronRight :size="15" class="resultado-flecha" aria-hidden="true" />
                </button>
                <p v-if="buscando" class="muted" role="status">Buscando vehículos…</p>
                <p v-else-if="!vehiculosFiltrados.length && !errorBusqueda && (clienteSeleccionado || vehiculoSearch.trim() === debouncedVehiculoSearch.trim())" class="muted">No se encontraron vehículos.</p>
              </div>
            </div>
          </div>
        </div>
        <p v-if="errorBusqueda" class="error-message" role="alert">{{ errorBusqueda }}</p>

        <CommonDependentFields :ready="titularCompleto">
        <div class="form-grid campos-principales">
          <label class="ancho">
            <span>Concepto <span class="required-star">*</span></span>
            <input v-model="form.descripcion" required placeholder="Ej. Service de 50.000 km y cambio de aceite" />
          </label>
          <label>
            Mano de obra ($)
            <input v-model.number="form.manoObra" type="number" min="0" step="0.01" />
          </label>
          <label>
            Porcentaje de IVA (%)
            <select v-model.number="form.porcentajeIva" required><option v-for="porcentaje in opcionesIva" :key="porcentaje" :value="porcentaje">{{ etiquetaIva(porcentaje) }}</option></select>
            <small v-if="errorIva" role="alert">{{ errorIva }}</small>
          </label>
        </div>
        <div class="form-grid campos-principales">
          <label>Condición de IVA del cliente
            <select v-model.number="condicionIvaReceptorId" required>
              <option :value="null" disabled>Seleccionar condición</option>
              <option v-for="condicion in condiciones" :key="condicion.id" :value="condicion.id">{{ condicion.nombre }}</option>
            </select>
          </label>
          <label>Condición de IVA del taller
            <select v-model.number="condicionIvaEmisorId" required>
              <option :value="null" disabled>Seleccionar condición</option>
              <option v-for="condicion in condiciones.filter((item) => ['IVA Responsable Inscripto', 'Responsable Monotributo', 'IVA Sujeto Exento'].includes(item.nombre))" :key="condicion.id" :value="condicion.id">{{ condicion.nombre }}</option>
            </select>
          </label>
          <label>Punto de venta simulado
            <input v-model.number="puntoVenta" type="number" min="1" max="99999" step="1" required />
          </label>
        </div>
        <p class="muted">Tipo de factura simulado: <strong>{{ tipoPrevisto }}</strong> · CAE fijo al emitir: 74000000000000</p>

        <p v-if="cargandoRepuestos" class="muted" role="status">Cargando repuestos…</p>
        <p v-if="errorRepuestos" class="error-message" role="alert">{{ errorRepuestos }}</p>
        <p v-if="repuestosArchivados.length" class="muted" role="status">Esta factura incluye repuestos archivados: {{ repuestosArchivados.map((part) => part.name).join(', ') }}. Quitá o reemplazá esos renglones para poder guardar.</p>
        <div :inert="cargandoRepuestos || !!errorRepuestos">
          <CommonEditorRepuestos :key="vehiculoRepuestosId" v-model="form.items" :parts="parts" :search-parts="repuestosCompatibles" :min-quantity="0.001" />
        </div>

        <div class="totales">
          <div><span>Repuestos</span><strong>{{ dinero(materiales) }}</strong></div>
          <div><span>Mano de obra</span><strong>{{ dinero(form.manoObra) }}</strong></div>
          <div><span>IVA {{ Number(form.porcentajeIva).toLocaleString('es-AR') }}%</span><strong>{{ dinero(montoIva) }}</strong></div>
          <div class="total"><span>Total</span><strong>{{ dinero(total) }}</strong></div>
        </div>
        </CommonDependentFields>

        <p v-if="formError" class="error-message" role="alert">{{ formError }}</p>
      </div>

      <footer class="dialog-footer modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary" :disabled="!titularCompleto || cargandoRepuestos || !!errorRepuestos"><Check :size="16" />Guardar comprobante</button>
      </footer>
    </form>
  </CommonFormPage>
</template>

<style scoped>
.comprobante-manual-form { max-width: 840px; }
.comprobante-manual-form .dialog-header { padding: 10px 16px; }
.comprobante-manual-form .form-fields { display: grid; padding: 12px 16px; gap: 8px; }
.comprobante-manual-form .modal-footer { padding: 8px 16px; }
.selectores-titular { grid-template-columns: repeat(2, minmax(0, 1fr)); align-items: start; }
.selectores-titular .field-block { min-width: 0; }
.seleccion-titular { display: flex; align-items: center; gap: 8px; height: 40px; box-sizing: border-box; padding: 0 10px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); font-size: 12px; }
.seleccion-titular strong { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.seleccion-titular .quitar-seleccion { margin-left: auto; flex-shrink: 0; border: 0; background: transparent; }
.target-search-container { position: relative; }
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
.campos-principales { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.campos-principales .ancho { grid-column: 1 / -1; }
.campos-principales label { display: flex; flex-direction: column; justify-content: flex-end; gap: 6px; min-width: 0; font-size: 12px; font-weight: 700; }
.campos-principales :is(input, select) { width: 100%; min-width: 0; height: 40px; margin-top: 0; box-sizing: border-box; padding: 0 10px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); color: inherit; }
.totales { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; padding: 8px 12px; border: 1px solid var(--line); border-radius: 10px; background: #f8fafc; }
:global(html.dark .totales) { background: #202023; }
.totales > div { display: flex; flex-direction: column; gap: 4px; min-width: 0; font-size: 11px; }
.totales > div strong { font-size: 13px; }
.totales .total strong { font-size: 16px; color: #2563eb; }
:global(html.dark .totales .total strong) { color: #64d2ff; }
@media (max-width: 620px) {
  .selectores-titular { grid-template-columns: 1fr; }
  .campos-principales { grid-template-columns: 1fr; }
  .totales { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
