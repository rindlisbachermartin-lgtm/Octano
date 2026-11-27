<script setup lang="ts">
import { avanzarConEnter } from '~/utils/formKeyboard'
import { X, Search, Check, Camera, ImagePlus, Trash2 } from '@lucide/vue'
import { prepareOrderPhoto } from '~/utils/orderPhoto'
import { authError } from '~/composables/useAuthApi'
import type { VehiculoApi } from '~/utils/vehiculoApi'
import { adaptarRepuesto, type RepuestoApi } from '~/utils/repuestoApi'
import { paginasPresupuestos } from '~/utils/presupuestoApi'
import type { Part } from '~/types'
import type { PartEditorItem } from '~/types/partEditor'
import { vehicleLabel } from '~/utils/vehicleDisplay'
import { etiquetaIva, valoresIva } from '~/utils/ivaApi'
import { tiposTrabajoOrden, type OrdenApi, type DetalleOrdenApi, type TipoTrabajoOrden, type PaginaOrdenes } from '~/utils/ordenApi'
import ResumenImportes from './ResumenImportes.vue'
import TablaRepuestos from './TablaRepuestos.vue'

const props = defineProps<{ orden: OrdenApi | null }>()
const emit = defineEmits<{ close: []; saved: [orden: OrdenApi] }>()
const { authorization, user } = useAuthApi()
const { aliquotas, error: errorIva } = useAliquotasIva()
const mecanico = computed(() => user.value?.rol === 'MECANICO')
const { money } = useHelpers()
const error = ref('')
const guardando = ref(false)
const preview = ref(false)
const puedePlanificar = computed(() => !mecanico.value && (!props.orden || (!props.orden.inicioFecha && props.orden.estado !== 'EN_PROCESO')))
const materiales = ref<PartEditorItem[]>([])
const catalogo = ref<Part[]>([])
const detalle = ref<DetalleOrdenApi | null>(null)
const cantidades = reactive<Record<number, number>>({})
const mensajeMateriales = ref('')
const cargandoMateriales = ref(false)
const errorMateriales = ref('')
const trabajo = reactive({ kilometraje: props.orden?.kilometraje ?? null as number | null | '', diagnostico: props.orden?.diagnostico || '', observacionesMecanico: props.orden?.observacionesMecanico || '' })
const cameraInput = ref<HTMLInputElement | null>(null)
const galleryInput = ref<HTMLInputElement | null>(null)
const sector = ref('Tablero / kilometraje')
const mensajeFotos = ref('')
const fotos = ref<Record<number, string>>({})
const cargando = ref(false)
const mecanicos = ref<{ id: number; nombre: string; apellido: string }[]>([])
const vehiculos = ref<VehiculoApi[]>([])
const seleccionado = ref<VehiculoApi | null>(null)
const busqueda = ref('')
const consulta = useDebouncedValue(busqueda)
const buscando = ref(false)
const errorBusqueda = ref('')
const paginaVehiculos = ref(0)
const paginasVehiculos = ref(0)
interface Presupuesto { id: number; descripcion: string; estado: string; ordenId: number | null }
interface DetallePresupuesto extends Presupuesto { turnoId: number | null; manoObra: number; porcentajeIva: number; tiposTrabajo: TipoTrabajoOrden[]; totalRepuestos: number }
const presupuestoDetalle = ref<DetallePresupuesto | null>(null)
const presupuestos = ref<Presupuesto[]>([])
const errorPresupuesto = ref('')
const cargandoPresupuesto = ref(false)
const form = reactive({
  vehiculoId: props.orden?.vehiculoId ?? 0,
  presupuestoId: props.orden?.presupuestoId ?? '',
  mecanicoId: props.orden?.mecanicoId ?? '',
  descripcion: props.orden?.descripcion ?? '',
  tiposTrabajo: [...(props.orden?.tiposTrabajo ?? [])] as TipoTrabajoOrden[],
  indicaciones: props.orden?.indicaciones ?? '',
  manoObra: props.orden?.manoObra ?? 0,
  porcentajeIva: props.orden?.porcentajeIva ?? 21,
})
const vinculado = computed(() => Boolean(form.presupuestoId))
const repuestosDisponibles = computed(() => catalogo.value.filter(p => !detalle.value?.items.some(i => i.repuestoId === p.id)))
const redondear = (importe: number) => Math.round((importe + Number.EPSILON) * 100) / 100
const totalRepuestos = computed(() => redondear((detalle.value?.totalRepuestos ?? presupuestoDetalle.value?.totalRepuestos ?? 0)
  + materiales.value.reduce((sum, i) => sum + redondear(Number(i.quantity || 0) * Number(i.unitPrice || 0)), 0)))
const opcionesIva = computed(() => valoresIva(aliquotas.value, form.porcentajeIva))
const subtotal = computed(() => redondear(totalRepuestos.value + Number(form.manoObra || 0)))
const montoIva = computed(() => redondear(subtotal.value * Number(form.porcentajeIva || 0) / 100))
const total = computed(() => redondear(subtotal.value + montoIva.value))
function cerrar() { if (!guardando.value) emit('close') }
useModalEscape(() => true, cerrar)

function recibirDetalle(result: DetalleOrdenApi) {
  detalle.value = result
  for (const item of result.items) cantidades[item.id] = item.cantidad
}

async function modificarMaterial(id: number, method: 'PATCH' | 'DELETE') {
  const headers = authorization.value
  if (!headers || !props.orden || guardando.value) return
  const item = detalle.value?.items.find(i => i.id === id)
  if (method === 'DELETE' && mecanico.value) return
  if (method === 'PATCH' && (!item || !Number.isFinite(cantidades[id]) || cantidades[id] < (mecanico.value ? item.cantidad : 0.001) || cantidades[id] > 999999999999.999)) {
    error.value = 'Revisá la cantidad del repuesto.'
    return
  }
  guardando.value = true
  error.value = ''
  mensajeMateriales.value = ''
  try {
    const result = await $fetch<DetalleOrdenApi>(`/api/ordenes-trabajo/${props.orden.id}/repuestos/${id}`, { method, headers, body: method === 'PATCH' ? { cantidad: cantidades[id] } : undefined })
    if (authorization.value?.Authorization !== headers.Authorization) return
    recibirDetalle(result)
    mensajeMateriales.value = method === 'PATCH' ? 'Cantidad guardada.' : 'Repuesto eliminado.'
  } catch (err) { error.value = authError(err, 'No se pudo modificar el repuesto. Revisá la cantidad y el stock.') }
  finally { guardando.value = false }
}

watch([authorization, () => detalle.value?.fotos.map(f => f.id).join(',')], async ([headers], _, onCleanup) => {
  const controller = new AbortController()
  const urls: Record<number, string> = {}
  onCleanup(() => { controller.abort(); Object.values(urls).forEach(url => URL.revokeObjectURL(url)) })
  fotos.value = {}
  if (!headers || !props.orden || !detalle.value) return
  const results = await Promise.allSettled(detalle.value.fotos.map(async f => {
    const blob = await $fetch<Blob>(`/api/ordenes-trabajo/${props.orden!.id}/fotos/${f.id}`, { headers, signal: controller.signal, responseType: 'blob' })
    if (!controller.signal.aborted) urls[f.id] = URL.createObjectURL(blob)
  }))
  if (!controller.signal.aborted) {
    fotos.value = urls
    if (results.some(r => r.status === 'rejected')) mensajeFotos.value = 'No se pudieron descargar algunas fotos.'
  }
})

async function cargarFoto(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  const headers = authorization.value
  if (!file || !headers || !props.orden || guardando.value || (detalle.value?.fotos.length ?? 0) >= 8) return
  guardando.value = true
  mensajeFotos.value = ''
  try {
    const datos = await prepareOrderPhoto(file)
    if (authorization.value?.Authorization !== headers.Authorization) return
    const result = await $fetch<DetalleOrdenApi>(`/api/ordenes-trabajo/${props.orden.id}/fotos`, { method: 'POST', headers, body: { sector: sector.value, datos } })
    if (authorization.value?.Authorization === headers.Authorization) { detalle.value = result; mensajeFotos.value = 'Foto guardada.' }
  } catch (err) { mensajeFotos.value = authError(err, 'No se pudo guardar la foto.') }
  finally { guardando.value = false }
}

watch(() => form.vehiculoId, () => { materiales.value = [] })
watch([authorization, () => form.vehiculoId], async ([headers, vehiculoId], _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  catalogo.value = []
  detalle.value = null
  errorMateriales.value = ''
  cargandoMateriales.value = !!(headers && vehiculoId)
  if (!headers || !vehiculoId) return
  try {
    const [parts, actual] = await Promise.all([
      paginasPresupuestos<RepuestoApi>('/api/repuestos', headers, controller.signal, { activo: true, vehiculoId }),
      props.orden ? $fetch<DetalleOrdenApi>(`/api/ordenes-trabajo/${props.orden.id}`, { headers, signal: controller.signal }) : Promise.resolve(null),
    ])
    if (!controller.signal.aborted) { catalogo.value = parts.map(adaptarRepuesto); if (actual) recibirDetalle(actual) }
  } catch (err) { if (!controller.signal.aborted) errorMateriales.value = authError(err, 'No se pudieron cargar los repuestos. Cerrá y volvé a abrir la orden.') }
  finally { if (!controller.signal.aborted) cargandoMateriales.value = false }
}, { immediate: true })

watch(authorization, async (headers, _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  mecanicos.value = []
  cargando.value = !!(headers && !mecanico.value)
  if (!headers || mecanico.value) return
  try {
    const result = await $fetch<typeof mecanicos.value>('/api/mecanicos', { headers, signal: controller.signal, query: { activo: true } })
    if (!controller.signal.aborted) mecanicos.value = result
  } catch (err) {
    if (!controller.signal.aborted) error.value = authError(err, 'No se pudieron cargar los mecánicos. Cerrá y volvé a abrir el formulario.')
  } finally { if (!controller.signal.aborted) cargando.value = false }
}, { immediate: true })

watch(consulta, () => { paginaVehiculos.value = 0 })
watch([authorization, consulta, paginaVehiculos], async ([headers, q], _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  vehiculos.value = []
  errorBusqueda.value = ''
  buscando.value = !!(headers && q.trim() && !props.orden)
  if (!buscando.value) return
  try {
    const result = await $fetch<PaginaOrdenes<VehiculoApi>>('/api/vehiculos', {
      headers, signal: controller.signal, query: { q: q.trim(), activo: true, pagina: paginaVehiculos.value, tamano: 20 },
    })
    if (!controller.signal.aborted) { vehiculos.value = result.contenido; paginasVehiculos.value = result.totalPaginas }
  } catch (err) {
    if (!controller.signal.aborted) errorBusqueda.value = authError(err, 'No se pudieron buscar vehículos.')
  } finally { if (!controller.signal.aborted) buscando.value = false }
})

watch([authorization, () => form.vehiculoId], async ([headers, vehiculoId], _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  presupuestos.value = []
  errorPresupuesto.value = ''
  if (!headers || !vehiculoId || props.orden) return
  try {
    let pagina = 0
    let totalPaginas: number
    const items: Presupuesto[] = []
    do {
      const result = await $fetch<PaginaOrdenes<Presupuesto>>('/api/presupuestos', {
        headers, signal: controller.signal, query: { vehiculoId, pagina, tamano: 100 },
      })
      items.push(...result.contenido)
      totalPaginas = result.totalPaginas
      pagina++
    } while (pagina < totalPaginas && !controller.signal.aborted)
    if (!controller.signal.aborted) presupuestos.value = items.filter(p => !p.ordenId && ['PENDIENTE', 'ACEPTADO'].includes(p.estado))
  } catch (err) {
    if (!controller.signal.aborted) errorPresupuesto.value = authError(err, 'No se pudieron cargar los presupuestos.')
  }
}, { immediate: true })

watch([authorization, () => form.presupuestoId], async ([headers, id], _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  presupuestoDetalle.value = null
  cargandoPresupuesto.value = !!(headers && id && !props.orden)
  if (!cargandoPresupuesto.value) return
  error.value = ''
  try {
    const p = await $fetch<DetallePresupuesto>(`/api/presupuestos/${id}`, { headers, signal: controller.signal })
    if (controller.signal.aborted) return
    if (p.turnoId) {
      form.presupuestoId = ''
      error.value = 'Este presupuesto tiene un turno. Utilizá la orden generada por esa reserva.'
      return
    }
    Object.assign(form, { descripcion: p.descripcion, manoObra: p.manoObra, porcentajeIva: p.porcentajeIva, tiposTrabajo: [...p.tiposTrabajo] })
    presupuestoDetalle.value = p
  } catch (err) {
    if (!controller.signal.aborted) {
      form.presupuestoId = ''
      error.value = authError(err, 'No se pudo cargar el presupuesto. Seleccionalo nuevamente.')
    }
  } finally { if (!controller.signal.aborted) cargandoPresupuesto.value = false }
})

function seleccionar(v: VehiculoApi) {
  seleccionado.value = v
  form.vehiculoId = v.id
  form.presupuestoId = ''
  busqueda.value = ''
}

async function guardar() {
  const headers = authorization.value
  if (!headers || guardando.value || cargando.value || cargandoPresupuesto.value || cargandoMateriales.value) return
  error.value = ''
  if (puedePlanificar.value && (!form.vehiculoId || !mecanicos.value.some(m => m.id === Number(form.mecanicoId)) || !form.descripcion.trim() || !form.tiposTrabajo.length)) {
    error.value = 'Seleccioná vehículo, mecánico activo, tipo de trabajo y descripción.'
    return
  }
  if (errorMateriales.value) { error.value = errorMateriales.value; return }
  if (materiales.value.some(i => !Number.isFinite(i.quantity) || i.quantity < 0.001 || i.quantity > 999999999999.999 || !Number.isFinite(i.unitPrice) || i.unitPrice < 0 || i.unitPrice > 999999999999.99 || (i.partId === 'custom' && (!i.customName.trim() || i.customName.trim().length > 250)))) {
    error.value = 'Revisá las cantidades, precios y descripciones de los repuestos.'; return
  }
  for (const i of materiales.value) {
    if (typeof i.partId !== 'number') continue
    const p = catalogo.value.find(p => p.id === i.partId)
    if (!p || p.archived || !Number.isFinite(p.stock) || p.stock < i.quantity) {
      preview.value = false
      error.value = `No hay stock suficiente de ${p?.name || i.customName || 'ese repuesto'}. Disponible: ${p?.stock ?? 0}; solicitado: ${i.quantity}.`
      return
    }
  }
  if (!Number.isFinite(form.manoObra) || form.manoObra < 0 || !Number.isFinite(form.porcentajeIva) || form.porcentajeIva < 0 || form.porcentajeIva > 100) {
    error.value = 'Revisá la mano de obra y el porcentaje de IVA.'
    return
  }
  if (!props.orden && !preview.value) { preview.value = true; return }
  guardando.value = true
  try {
    const id = props.orden?.id
    const items = materiales.value.map(i => typeof i.partId === 'number' ? { repuestoId: i.partId, cantidad: i.quantity } : { repuestoId: null, descripcion: i.customName.trim(), cantidad: i.quantity, precioUnitario: i.unitPrice })
    let result = detalle.value
    if (puedePlanificar.value || props.orden?.estado === 'EN_PROCESO') result = await $fetch<DetalleOrdenApi>(`/api/ordenes-trabajo${id ? `/${id}` : ''}${puedePlanificar.value ? '' : '/trabajo'}`, {
      method: id ? 'PUT' : 'POST', headers,
      body: puedePlanificar.value ? { ...form, presupuestoId: form.presupuestoId ? Number(form.presupuestoId) : null, mecanicoId: Number(form.mecanicoId), descripcion: form.descripcion.trim(), indicaciones: form.indicaciones.trim(), items } : { ...trabajo, kilometraje: trabajo.kilometraje === '' ? null : trabajo.kilometraje },
    })
    if (!result) return
    if (authorization.value?.Authorization !== headers.Authorization) return
    if (items.length && !puedePlanificar.value) {
      result = await $fetch<DetalleOrdenApi>(`/api/ordenes-trabajo/${result.orden.id}/repuestos`, { method: 'POST', headers, body: { items } })
      if (authorization.value?.Authorization !== headers.Authorization) return
    }
    materiales.value = []
    detalle.value = result
    if (authorization.value?.Authorization === headers.Authorization) emit('saved', result.orden)
  } catch (err) { preview.value = false; error.value = authError(err, 'No se pudo guardar la orden. Tus datos siguen en el formulario.') }
  finally { guardando.value = false }
}
</script>

<template>
  <CommonFormPage>
    <div class="dialog-header"><div><h2>{{ orden ? `Editar orden #${orden.id}` : preview ? 'Vista previa de la orden de trabajo' : 'Nueva orden de trabajo' }}</h2><p class="muted">{{ orden ? 'Podés ajustar los datos antes de iniciar el trabajo.' : preview ? 'Revisá los datos antes de emitir la orden.' : 'El presupuesto es opcional. Podés armar la factura cuando finalice el trabajo.' }}</p></div><button class="icon-button" aria-label="Cerrar formulario" :disabled="guardando" @click="cerrar"><X :size="18" /></button></div>
    <form @keydown.enter="avanzarConEnter" class="entry-form" @submit.prevent="guardar">
      <div v-if="preview" class="form-fields order-preview">
        <div class="selected-target-card"><span class="plate">{{ seleccionado?.patente }}</span><div class="target-info"><strong>{{ seleccionado?.marca }} {{ seleccionado?.modelo }}</strong><p class="muted">{{ seleccionado?.clienteNombre }}</p></div></div>
        <dl class="preview-details">
          <div><dt>Trabajo a realizar</dt><dd>{{ form.descripcion }}</dd></div>
          <div><dt>Mecánico asignado</dt><dd>{{ mecanicos.find(m => m.id === Number(form.mecanicoId))?.nombre }} {{ mecanicos.find(m => m.id === Number(form.mecanicoId))?.apellido }}</dd></div>
          <div><dt>Presupuesto</dt><dd>{{ form.presupuestoId ? `#${form.presupuestoId}` : 'Sin presupuesto' }}</dd></div>
          <div><dt>Indicaciones del administrador para el mecánico</dt><dd>{{ form.indicaciones.trim() || 'Sin observaciones' }}</dd></div>
        </dl>
        <div v-if="materiales.length"><strong>Repuestos asignados</strong><ul><li v-for="i in materiales" :key="i.key">{{ i.quantity }} × {{ i.customName || i.searchQuery }} · {{ money(i.quantity * i.unitPrice) }}</li></ul></div>
        <ResumenImportes :repuestos="totalRepuestos" :mano-obra="Number(form.manoObra || 0)" :subtotal="subtotal" :porcentaje-iva="Number(form.porcentajeIva || 0)" :iva="montoIva" :total="total" estimado />
        <p class="muted">El mecánico registra y actualiza el kilometraje desde su vista.</p>
      </div>
      <fieldset v-show="!preview" class="form-fields" :disabled="guardando || preview">
        <div v-if="orden || seleccionado" class="selected-target-card"><span class="plate">{{ orden?.patente || seleccionado?.patente }}</span><div class="target-info"><strong>{{ vehicleLabel(orden?.marca || seleccionado?.marca, orden?.modelo || seleccionado?.modelo, orden?.version || seleccionado?.version) }}</strong><p class="muted">{{ orden?.clienteNombre || seleccionado?.clienteNombre }}</p></div><button v-if="!orden" type="button" class="button small clear-vehicle" aria-label="Quitar vehículo seleccionado" title="Quitar vehículo" @click="seleccionado = null; form.vehiculoId = 0; form.presupuestoId = ''">Cambiar vehículo</button></div>
        <template v-else>
          <label class="search-box"><Search :size="16" /><input v-model="busqueda" maxlength="250" aria-label="Buscar vehículo para la orden" placeholder="Buscá por patente, marca o modelo…" /></label>
          <p v-if="buscando" role="status">Buscando vehículos…</p><p v-if="errorBusqueda" role="alert">{{ errorBusqueda }}</p>
          <div v-if="consulta.trim() && consulta === busqueda" class="vehicle-results">
            <button v-for="v in vehiculos" :key="v.id" class="vehicle-result" type="button" @click="seleccionar(v)">
              <span class="plate">{{ v.patente }}</span>
              <span class="vehicle-result-info"><strong>{{ v.marca }} {{ v.modelo }}</strong><small>{{ v.clienteNombre }}</small></span>
            </button>
            <p v-if="!buscando && !vehiculos.length && !errorBusqueda" class="muted">No se encontraron vehículos activos.</p>
            <div v-if="paginasVehiculos > 1"><button type="button" class="button small" :disabled="paginaVehiculos === 0 || buscando" @click="paginaVehiculos--">Anterior</button><button type="button" class="button small" :disabled="paginaVehiculos + 1 >= paginasVehiculos || buscando" @click="paginaVehiculos++">Siguiente</button></div>
          </div>
        </template>
        <CommonDependentFields :ready="!!form.vehiculoId" hint="Primero seleccioná un vehículo para completar los demás datos.">
        <fieldset class="planning-fields" :disabled="!puedePlanificar">
        <p v-if="errorPresupuesto" role="alert">{{ errorPresupuesto }}</p>
        <label>Presupuesto (opcional)<select v-model="form.presupuestoId" :disabled="!!orden || !form.vehiculoId || cargandoPresupuesto"><option value="">Sin presupuesto</option><option v-if="orden?.presupuestoId" :value="orden.presupuestoId">#{{ orden.presupuestoId }} · Presupuesto vinculado</option><option v-for="p in presupuestos" :key="p.id" :value="p.id">#{{ p.id }} · {{ p.descripcion }}</option></select></label>
        <p v-if="cargandoPresupuesto" role="status">Cargando presupuesto…</p>
        <fieldset class="work-type-field">
          <legend>Trabajo a realizar</legend>
          <div class="work-type-options">
            <button v-for="(label, key) in tiposTrabajoOrden" :key="key" type="button" class="button"
              :class="{ primary: form.tiposTrabajo.includes(key) }"
              :aria-pressed="form.tiposTrabajo.includes(key)"
              @click="form.tiposTrabajo = form.tiposTrabajo.includes(key) ? form.tiposTrabajo.filter(t => t !== key) : [...form.tiposTrabajo, key]">
              {{ label }}
            </button>
          </div>
          <p class="muted">Service y distribución se registran en la cartilla digital al finalizar la orden.</p>
        </fieldset>
        <label>Detalle del trabajo<input v-model="form.descripcion" required maxlength="2000" placeholder="Describí el trabajo solicitado" /></label>
        <label>Mecánico asignado<select v-model="form.mecanicoId" required :disabled="cargando"><option value="" disabled>Seleccionar mecánico</option><option v-for="m in mecanicos" :key="m.id" :value="m.id">{{ m.nombre }} {{ m.apellido }}</option></select></label>
        <p v-if="!mecanico && !cargando && !mecanicos.length" class="muted">Necesitás un mecánico activo. Podés darlo de alta desde Personal.</p>
        </fieldset>
        <CommonEditorRepuestos :key="form.vehiculoId" v-model="materiales" :parts="repuestosDisponibles" :min-quantity="0.001" :allow-custom="!mecanico" catalog-prices-readonly validate-stock />
        <p class="muted">Los repuestos agregados se guardan al confirmar los cambios.</p>
        <div v-if="detalle?.items.length" class="assigned-parts">
          <strong>Repuestos asignados</strong>
          <TablaRepuestos :items="detalle.items" :show-prices="!mecanico">
            <template #quantity="{ item }"><div class="assigned-part-controls"><input v-model.number="cantidades[item.id]" :aria-label="`Cantidad de ${item.descripcion}`" type="number" :min="mecanico ? item.cantidad : 0.001" max="999999999999.999" step="0.001" required @change="modificarMaterial(item.id, 'PATCH')" /></div></template>
            <template v-if="!mecanico" #actions="{ item }"><button type="button" class="icon-button" :aria-label="`Eliminar repuesto ${item.descripcion}`" title="Quitar repuesto y devolver al stock" @click="modificarMaterial(item.id, 'DELETE')"><Trash2 :size="15" /></button></template>
          </TablaRepuestos>
          <p v-if="mensajeMateriales" role="status">{{ mensajeMateriales }}</p>
        </div>
        <p v-if="cargandoMateriales" role="status">Cargando repuestos…</p>
        <p v-if="errorMateriales" role="alert">{{ errorMateriales }}</p>
        <fieldset class="planning-fields" :disabled="!puedePlanificar">
        <div v-if="!mecanico" class="form-grid"><label>Mano de obra<input v-model.number="form.manoObra" type="number" min="0" max="999999999999.99" step="0.01" required :readonly="vinculado" /></label><label>IVA (%)<select v-model.number="form.porcentajeIva" required :disabled="vinculado"><option v-for="porcentaje in opcionesIva" :key="porcentaje" :value="porcentaje">{{ etiquetaIva(porcentaje) }}</option></select><small v-if="errorIva" role="alert">{{ errorIva }}</small></label></div>
        <p v-if="vinculado && !mecanico" class="muted">Los materiales y precios se conservan desde el presupuesto. Mano de obra: {{ money(form.manoObra) }}.</p>
        <label>Indicaciones del administrador para el mecánico<textarea v-model="form.indicaciones" maxlength="4000" rows="3" placeholder="Indicaciones o aclaraciones para el mecánico" /></label>
        </fieldset>
        <template v-if="orden?.estado === 'EN_PROCESO'">
          <div class="form-grid"><label>Kilometraje<input v-model.number="trabajo.kilometraje" type="number" min="0" max="9999999" step="1" /></label></div>
          <label>Diagnóstico<textarea v-model="trabajo.diagnostico" maxlength="4000" rows="3" /></label>
          <label>Observaciones del mecánico<textarea v-model="trabajo.observacionesMecanico" maxlength="4000" rows="3" /></label>
        </template>
        <div v-if="orden && ['EN_ESPERA', 'EN_PROCESO'].includes(orden.estado)" class="order-photo-section">
          <strong>Fotos del vehículo</strong>
          <label>Sector de la foto<select v-model="sector"><option>Tablero / kilometraje</option><option>Frente</option><option>Trasera</option><option>Lateral izquierdo</option><option>Lateral derecho</option><option>Detalle mecánico</option><option>Bajo chasis / motor</option></select></label>
          <div class="photo-actions"><button type="button" class="button" :disabled="cargandoMateriales || (detalle?.fotos.length ?? 0) >= 8" @click="cameraInput?.click()"><Camera :size="17" /> Sacar foto</button><button type="button" class="button" :disabled="cargandoMateriales || (detalle?.fotos.length ?? 0) >= 8" @click="galleryInput?.click()"><ImagePlus :size="17" /> Elegir imagen</button></div>
          <input ref="cameraInput" class="sr-only" aria-label="Cámara del vehículo" type="file" accept="image/*" capture="environment" @change="cargarFoto" />
          <input ref="galleryInput" class="sr-only" aria-label="Imágenes del vehículo" type="file" accept="image/*" @change="cargarFoto" />
          <p class="muted">{{ detalle?.fotos.length ?? 0 }} de 8 fotos. Las fotos se guardan al subirlas.</p>
          <p v-if="mensajeFotos" role="status">{{ mensajeFotos }}</p>
          <div class="order-photos"><figure v-for="f in detalle?.fotos || []" :key="f.id"><a v-if="fotos[f.id]" :href="fotos[f.id]" target="_blank" rel="noopener"><img :src="fotos[f.id]" :alt="f.sector" /></a><figcaption>{{ f.sector }}</figcaption></figure></div>
        </div>
        </CommonDependentFields>
      </fieldset>
      <p v-if="error" role="alert">{{ error }}</p>
      <footer class="dialog-footer modal-footer"><button type="button" class="button" :disabled="guardando" @click="cerrar">Cancelar</button><button v-if="preview" type="button" class="button" :disabled="guardando" @click="preview = false">Volver a editar</button><button type="submit" class="button primary" :disabled="!form.vehiculoId || guardando || cargando || cargandoPresupuesto || cargandoMateriales || !!errorMateriales"><Check :size="16" /> {{ guardando ? 'Guardando…' : orden ? 'Guardar cambios' : preview ? 'Crear orden de trabajo' : 'Ver vista previa' }}</button></footer>
    </form>
  </CommonFormPage>
</template>

<style scoped>
fieldset.form-fields { border: 0; min-width: 0; }
.planning-fields { border: 0; padding: 0; margin: 0; min-width: 0; display: grid; gap: 16px; }
.assigned-part-controls { display: flex; align-items: center; justify-content: center; }
.assigned-part-controls input { display: block; width: 80px; min-width: 0; margin-inline: auto; text-align: center; }
.order-photo-section { display: grid; gap: 12px; }
.photo-actions, .order-photos { display: flex; flex-wrap: wrap; gap: 12px; }
.order-photos figure { margin: 0; width: 140px; }
.order-photos img { width: 140px; height: 100px; object-fit: cover; border-radius: 8px; }
.order-photos figcaption { font-size: 12px; margin: 4px 0; }
.clear-vehicle { margin-left: auto; flex-shrink: 0; }

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
</style>
