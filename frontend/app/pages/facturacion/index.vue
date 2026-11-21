<script setup lang="ts">
import {
  Plus,
  Receipt,
  CreditCard,
  FileText,
  Eye,
  Pencil,
  Loader2,
} from '@lucide/vue'
import { authError } from '~/composables/useAuthApi'
import { useComprobantes, type DatosCobro, type SolicitudComprobante } from '~/composables/useComprobantes'
import {
  estadosComprobante,
  numeroComprobante,
  type ComprobanteApi,
  type ComprobanteResumenApi,
  type OrdenPendienteApi,
} from '~/utils/comprobanteApi'

const auth = useAuthApi()
const { notify } = useWorkshopToast()
const route = useRoute()
const {
  cargando, error, todos, ordenesPendientes, cargandoOrdenes, errorOrdenes,
  recargar, obtener, crearDesdeOrden, guardar, emitir, cobrar,
} = useComprobantes()

type Seccion = 'pendientes' | 'cobrados' | 'ordenes'
const seccion = ref<Seccion>('pendientes')
const mes = ref('')
const clienteFiltro = ref('')
const debouncedCliente = useDebouncedValue(clienteFiltro)
const conPresupuesto = ref<'todos' | 'con' | 'sin'>('todos')

const visorOpen = ref(false)
const comprobanteVisor = ref<ComprobanteApi | null>(null)
const pagoOpen = ref(false)
const comprobantePago = ref<ComprobanteApi | null>(null)
const manualOpen = ref(false)
const ordenManual = ref<OrdenPendienteApi | null>(null)
const comprobanteManual = ref<ComprobanteApi | null>(null)
const ocupado = ref<number | 'nuevo' | null>(null)

const dinero = (valor: number) =>
  Number(valor || 0).toLocaleString('es-AR', { style: 'currency', currency: 'ARS', minimumFractionDigits: 2, maximumFractionDigits: 2 })

const desde = computed(() => mes.value ? `${mes.value}-01` : '')
const hasta = computed(() => {
  if (!mes.value) return ''
  const [anio, numeroMes] = mes.value.split('-').map(Number)
  const ultimo = new Date(anio, numeroMes, 0).getDate()
  return `${mes.value}-${String(ultimo).padStart(2, '0')}`
})
const filtros = computed(() => ({ q: debouncedCliente.value, desde: desde.value, hasta: hasta.value }))
const filtrosOrdenes = computed(() => ({
  q: debouncedCliente.value,
  conPresupuesto: conPresupuesto.value === 'todos' ? null : conPresupuesto.value === 'con',
}))

const pendientesDeCobro = computed(() => todos.value.filter((c) => c.estado !== 'COBRADO'))
const cobrados = computed(() => todos.value.filter((c) => c.estado === 'COBRADO'))
const totalCobrado = computed(() => todos.value.reduce((sum, c) => sum + Number(c.pagado || 0), 0))
const saldoPendiente = computed(() => todos.value.reduce((sum, c) => sum + Number(c.saldo || 0), 0))
const cantidadEmitidos = computed(() => todos.value.filter((c) => !!c.numero).length)
const ordenesVisibles = computed(() => ordenesPendientes.value)
const listaVacia = computed(() => {
  if (seccion.value === 'ordenes') return !ordenesVisibles.value.length
  if (seccion.value === 'cobrados') return !cobrados.value.length
  return !pendientesDeCobro.value.length
})

const secciones: { value: Seccion; label: string }[] = [
  { value: 'pendientes', label: 'Pendientes de cobro' },
  { value: 'cobrados', label: 'Cobrados' },
  { value: 'ordenes', label: 'Órdenes listas para facturar' },
]
const presupuestoFiltros = [
  { value: 'todos', label: 'Todos' },
  { value: 'con', label: 'Con presupuesto' },
  { value: 'sin', label: 'Sin presupuesto' },
] as const

function estadoDe(c: ComprobanteResumenApi) {
  return estadosComprobante[c.estado] ?? { label: c.estado, badge: 'neutral' }
}

function totalOrden(orden: OrdenPendienteApi) {
  return Number(orden.total || 0)
}

async function abrirDetalle(id: number) {
  try {
    comprobanteVisor.value = await obtener(id)
    visorOpen.value = true
  } catch (err) {
    notify(authError(err, 'No pudimos cargar el comprobante.'))
  }
}

function abrirManual(orden: OrdenPendienteApi | null = null, comprobante: ComprobanteApi | null = null) {
  ordenManual.value = orden
  comprobanteManual.value = comprobante
  manualOpen.value = true
}

async function generarDesdeOrden(orden: OrdenPendienteApi) {
  ocupado.value = orden.ordenId
  try {
    const comprobante = await crearDesdeOrden(orden.ordenId)
    notify(`Comprobante interno ${numeroComprobante(comprobante)} generado desde la orden #${orden.ordenId}.`)
    await abrirDetalle(comprobante.id)
  } catch (err) {
    notify(authError(err, 'No pudimos generar el comprobante.'))
  } finally {
    ocupado.value = null
  }
}

async function guardarComprobante(solicitud: SolicitudComprobante, id?: number) {
  ocupado.value = id ?? 'nuevo'
  try {
    const comprobante = await guardar(solicitud, id)
    manualOpen.value = false
    notify(`Comprobante ${numeroComprobante(comprobante)} guardado.`)
    await abrirDetalle(comprobante.id)
  } catch (err) {
    notify(authError(err, 'No pudimos guardar el comprobante.'))
  } finally {
    ocupado.value = null
  }
}

async function editarBorrador(c: ComprobanteResumenApi) {
  try {
    const detalle = await obtener(c.id)
    visorOpen.value = false
    abrirManual(null, detalle)
  } catch (err) {
    notify(authError(err, 'No pudimos cargar el borrador.'))
  }
}

async function abrirPago(c: ComprobanteResumenApi) {
  try {
    comprobantePago.value = await obtener(c.id)
    pagoOpen.value = true
  } catch (err) {
    notify(authError(err, 'No pudimos cargar el comprobante.'))
  }
}

async function confirmarPago(datos: DatosCobro) {
  if (!comprobantePago.value) return
  const id = comprobantePago.value.id
  ocupado.value = id
  try {
    const comprobante = await cobrar(id, datos)
    pagoOpen.value = false
    notify(`Pago registrado. El comprobante ${numeroComprobante(comprobante)} tiene un saldo de ${dinero(comprobante.saldo)}.`)
    comprobanteVisor.value = comprobante
    visorOpen.value = true
  } catch (err) {
    notify(authError(err, 'No pudimos registrar el pago.'))
  } finally {
    ocupado.value = null
  }
}

async function emitirComprobante(c: ComprobanteResumenApi) {
  ocupado.value = c.id
  try {
    const comprobante = await emitir(c.id)
    notify(`Comprobante interno ${numeroComprobante(comprobante)} emitido.`)
    comprobanteVisor.value = comprobante
    visorOpen.value = true
  } catch (err) {
    notify(authError(err, 'No pudimos emitir el comprobante.'))
  } finally {
    ocupado.value = null
  }
}

function limpiarFiltros() {
  mes.value = ''
  clienteFiltro.value = ''
  conPresupuesto.value = 'todos'
}

watch([filtros, filtrosOrdenes], () => { void recargar(filtros.value, filtrosOrdenes.value) })

onMounted(async () => {
  await recargar(filtros.value, filtrosOrdenes.value)
  const ordenId = Number(route.query.orden)
  if (ordenId) {
    const orden = ordenesPendientes.value.find((item) => item.ordenId === ordenId)
    if (orden) {
      seccion.value = 'ordenes'
      abrirManual(orden)
    }
  }
})
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">TALLER CENTRAL / FACTURACIÓN INTERNA</div>
        <h1>Las cuentas, al día.</h1>
        <p class="muted">Comprobantes internos vinculados a las órdenes de trabajo. Sin validez fiscal.</p>
      </div>
      <button class="button primary" @click="abrirManual()">
        <Plus :size="17" />Nuevo comprobante
      </button>
    </section>

    <section class="stats-grid summary-stats invoice-stats">
      <div class="stat-card summary-logo-card">
        <CommonOctanoLogo class="summary-logo" />
        <div>Total cobrado</div>
        <strong>{{ dinero(totalCobrado) }}</strong>
        <small>{{ cobrados.length }} comprobantes cobrados</small>
      </div>
      <div class="stat-card">
        <div>Saldo pendiente</div>
        <strong>{{ dinero(saldoPendiente) }}</strong>
        <small>{{ pendientesDeCobro.length }} comprobantes con saldo</small>
      </div>
      <div class="stat-card">
        <div>Comprobantes internos emitidos</div>
        <strong>{{ cantidadEmitidos }}</strong>
        <small>Numeración interna del taller</small>
      </div>
    </section>

    <section class="panel ready-orders-panel">
      <div class="billing-filters">
        <CommonDatePicker v-model="mes" label="Mes y año" type="month" year-selection compact />
        <label>
          Cliente
          <input v-model="clienteFiltro" type="search" placeholder="Nombre, documento o patente…" aria-label="Filtrar por cliente" />
        </label>
        <button v-if="mes || clienteFiltro || conPresupuesto !== 'todos'" class="button small outlined" @click="limpiarFiltros">Limpiar filtros</button>
      </div>

      <div class="segmented billing-order-filters" aria-label="Apartados de facturación">
        <button
          v-for="seccionOpcion in secciones"
          :key="seccionOpcion.value"
          type="button"
          :class="{ selected: seccion === seccionOpcion.value }"
          :aria-pressed="seccion === seccionOpcion.value"
          @click="seccion = seccionOpcion.value"
        >
          {{ seccionOpcion.label }} (
          {{ seccionOpcion.value === 'ordenes' ? ordenesVisibles.length : seccionOpcion.value === 'cobrados' ? cobrados.length : pendientesDeCobro.length }}
          )
        </button>
      </div>

      <div v-if="seccion === 'ordenes'" class="segmented status-filters billing-budget-filters" role="group" aria-label="Filtrar por presupuesto">
        <button v-for="filtro in presupuestoFiltros" :key="filtro.value" type="button" :class="{ selected: conPresupuesto === filtro.value }" @click="conPresupuesto = filtro.value">
          {{ filtro.label }}
        </button>
      </div>

      <p v-if="error" class="error-message" role="alert">{{ error }}</p>
      <p v-if="errorOrdenes" class="error-message" role="alert">{{ errorOrdenes }}</p>

      <div v-if="cargando || cargandoOrdenes" class="estado-carga"><Loader2 :size="18" class="spin" />Cargando comprobantes…</div>

      <div v-else class="ready-orders-grid">
        <article
          v-for="orden in ordenesVisibles"
          v-show="seccion === 'ordenes'"
          :key="`orden-${orden.ordenId}`"
          class="ready-order-card"
        >
          <div class="ready-card-top">
            <div>
              <span class="plate small-plate">{{ orden.patente }}</span>
              <h4>{{ orden.marca }} {{ orden.modelo }}</h4>
              <small class="muted">{{ orden.clienteNombre }} · OT #{{ orden.ordenId }}</small>
            </div>
            <span class="badge neutral">{{ orden.presupuestoId ? `Presupuesto #${orden.presupuestoId}` : 'Sin presupuesto' }}</span>
          </div>
          <p class="ready-service-desc">{{ orden.descripcion || 'Trabajo finalizado' }}</p>
          <div class="order-price-badge">
            <span class="price-lbl">Total a facturar</span>
            <strong class="price-num">{{ dinero(totalOrden(orden)) }}</strong>
          </div>
          <div class="ready-card-actions">
            <button type="button" class="button small primary" :disabled="ocupado === orden.ordenId" @click="generarDesdeOrden(orden)">
              <Receipt :size="14" />Generar comprobante
            </button>
            <button type="button" class="button small outlined" @click="abrirManual(orden)">
              <Pencil :size="14" />Armar manualmente
            </button>
          </div>
        </article>

        <article
          v-for="comprobante in [...pendientesDeCobro, ...cobrados]"
          v-show="seccion === (comprobante.estado === 'COBRADO' ? 'cobrados' : 'pendientes')"
          :key="`comprobante-${comprobante.id}`"
          class="ready-order-card"
        >
          <div class="ready-card-top">
            <div>
              <span class="plate small-plate">{{ comprobante.patente }}</span>
              <h4>{{ comprobante.marca }} {{ comprobante.modelo }}</h4>
              <small class="muted">{{ comprobante.clienteNombre }} · {{ numeroComprobante(comprobante) }}</small>
            </div>
            <span class="badge" :class="estadoDe(comprobante).badge">{{ estadoDe(comprobante).label }}</span>
          </div>
          <p class="ready-service-desc">{{ comprobante.descripcion || 'Comprobante interno' }}</p>
          <div class="saldos-linea">
            <span>Total <strong>{{ dinero(comprobante.total) }}</strong></span>
            <span>Pagado <strong>{{ dinero(comprobante.pagado) }}</strong></span>
            <span :class="{ 'saldo-abierto': Number(comprobante.saldo) > 0 }">Saldo <strong>{{ dinero(comprobante.saldo) }}</strong></span>
          </div>
          <div class="ready-card-actions">
            <button type="button" class="button small outlined" @click="abrirDetalle(comprobante.id)">
              <Eye :size="14" />Ver e imprimir
            </button>
            <button
              v-if="comprobante.estado === 'BORRADOR'"
              type="button"
              class="button small outlined"
              @click="editarBorrador(comprobante)"
            >
              <Pencil :size="14" />Editar borrador
            </button>
            <button v-if="!comprobante.numero && comprobante.estado === 'BORRADOR'" type="button" class="button small outlined" :disabled="ocupado === comprobante.id" @click="emitirComprobante(comprobante)">
              Emitir
            </button>
            <button v-if="Number(comprobante.saldo) > 0" type="button" class="button small primary" @click="abrirPago(comprobante)">
              <CreditCard :size="14" />Registrar cobro
            </button>
          </div>
        </article>
      </div>

      <div v-if="!cargando && !cargandoOrdenes && listaVacia" class="empty-state invoice-empty-state">
        <FileText :size="32" class="muted" />
        <h3>{{
          seccion === 'ordenes'
            ? 'No hay órdenes finalizadas pendientes de comprobante.'
            : seccion === 'cobrados'
              ? 'Todavía no hay comprobantes cobrados.'
              : 'No hay comprobantes pendientes de cobro.'
        }}</h3>
      </div>
    </section>

    <FacturacionModalComprobanteManual
      :open="manualOpen"
      :orden="ordenManual"
      :comprobante="comprobanteManual"
      @close="manualOpen = false"
      @guardar="guardarComprobante"
    />

    <FacturacionModalComprobantePago
      :open="pagoOpen"
      :comprobante="comprobantePago"
      :busy="ocupado !== null"
      @close="pagoOpen = false"
      @confirm="confirmarPago"
    />

    <FacturacionModalVisorComprobante
      :open="visorOpen"
      :comprobante="comprobanteVisor"
      @close="visorOpen = false"
    />
  </div>
</template>

<style scoped>
.billing-filters { display: flex; gap: 12px; align-items: end; flex-wrap: wrap; margin-bottom: 16px; }
.billing-filters label { display: grid; gap: 7px; font-size: 12px; color: var(--muted); }
.billing-filters input { height: 40px; min-height: 40px; box-sizing: border-box; border: 1px solid var(--line); border-radius: 8px; padding: 6px 10px; background: var(--surface); color: inherit; }
.billing-budget-filters { margin-bottom: 18px; flex-wrap: wrap; }
.billing-order-filters { margin-bottom: 16px; flex-wrap: wrap; }
.invoice-stats { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.ready-card-top h4 { margin: 4px 0 2px; }
.ready-service-desc { min-height: 34px; }
.saldos-linea { display: flex; flex-wrap: wrap; gap: 10px; font-size: 11px; color: var(--muted); }
.saldos-linea strong { color: inherit; display: block; font-size: 13px; }
.saldos-linea .saldo-abierto strong { color: #2563eb; }
.estado-carga { display: flex; align-items: center; gap: 8px; padding: 20px 0; color: var(--muted); font-size: 13px; }
.spin { animation: girar 1s linear infinite; }
@keyframes girar { to { transform: rotate(360deg); } }
@media (max-width: 640px) {
  .invoice-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .billing-filters > label { width: 100%; min-width: 0; }
  .ready-card-actions > .button { width: 100%; white-space: normal; }
}
.ready-orders-panel { padding: 18px 20px; }
</style>
