<script setup lang="ts">
import type { DatosCobro } from '~/composables/useComprobantes'
import type { SolicitudComprobante } from '~/composables/useComprobantes'
import type { ComprobanteResumenApi } from '~/utils/comprobanteApi'
import {
  Plus,
  Receipt,
  CreditCard,
  FileText,
  Pencil,
  Loader2,
} from '@lucide/vue'
import { authError } from '~/composables/useAuthApi'
import { useComprobantes } from '~/composables/useComprobantes';
import { estadosComprobante, fechaLabel, numeroComprobante, type ComprobanteApi, type OrdenPendienteApi } from '~/utils/comprobanteApi';

const { today } = useWorkshopDay()
const { notify } = useWorkshopToast()
const { workshopName } = useAuthApi()
const route = useRoute()
const {
  cargando, error, todos, ordenesPendientes, cargandoOrdenes, errorOrdenes,
  recargar, obtener, crearDesdeOrden, guardar, emitir, cobrar,
} = useComprobantes()

type Seccion = 'pendientes' | 'cobrados' | 'ordenes'
const seccion = ref<Seccion>('pendientes')
const mes = ref('')
// El último día del mes actual mantiene habilitado todo el mes corriente, pero no los futuros.
const maxMes = computed(() => {
  const [anio, numeroMes] = today.value.split('-').map(Number)
  const ultimo = new Date(anio, numeroMes, 0).getDate()
  return `${today.value.slice(0, 7)}-${String(ultimo).padStart(2, '0')}`
})
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

// El selector de mes entrega YYYY-MM; por si queda un valor con día, lo normalizamos.
const mesFiltro = computed(() => mes.value.slice(0, 7))
const desde = computed(() => mesFiltro.value ? `${mesFiltro.value}-01` : '')
const hasta = computed(() => {
  if (!mesFiltro.value) return ''
  const [anio, numeroMes] = mesFiltro.value.split('-').map(Number)
  const ultimo = new Date(anio, numeroMes, 0).getDate()
  return `${mesFiltro.value}-${String(ultimo).padStart(2, '0')}`
})
const filtros = computed(() => ({ q: debouncedCliente.value, desde: desde.value, hasta: hasta.value }))
const filtrosOrdenes = computed(() => ({
  q: debouncedCliente.value,
  conPresupuesto: conPresupuesto.value === 'todos' ? null : conPresupuesto.value === 'con',
}))

const pendientesDeCobro = computed(() => todos.value.filter((c) => c.estado === 'EMITIDO' || c.estado === 'PAGADO_PARCIAL'))
const borradores = computed(() => todos.value.filter((c) => c.estado === 'BORRADOR'))
const cobrados = computed(() => todos.value.filter((c) => c.estado === 'COBRADO'))
const totalCobrado = computed(() => todos.value.reduce((sum, c) => sum + Number(c.pagado || 0), 0))
const saldoPendiente = computed(() => pendientesDeCobro.value.reduce((sum, c) => sum + Number(c.saldo || 0), 0))
const cantidadEmitidos = computed(() => todos.value.filter((c) => !!c.numero).length)
const ordenesVisibles = computed(() => ordenesPendientes.value)
const listaVacia = computed(() => {
  if (seccion.value === 'ordenes') return !ordenesVisibles.value.length && !borradores.value.length
  if (seccion.value === 'cobrados') return !cobrados.value.length
  return !pendientesDeCobro.value.length
})

const secciones: { value: Seccion; label: string }[] = [
  { value: 'pendientes', label: 'Emitidas' },
  { value: 'cobrados', label: 'Cobradas' },
  { value: 'ordenes', label: 'Sin emitir' },
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
    notify(`Borrador ${numeroComprobante(comprobante)} generado desde la orden #${orden.ordenId}.`)
    abrirManual(null, comprobante)
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
    notify(`Factura ${comprobante.tipo} simulada ${numeroComprobante(comprobante)} emitida. CAE simulado: ${comprobante.caeSimulado}.`)
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
        <div class="eyebrow">{{ workshopName }} / FACTURACIÓN SIMULADA</div>
        <h1>Las cuentas, al día.</h1>
      </div>
      <button class="button primary" @click="abrirManual()">
        <Plus :size="17" />Nueva factura manual
      </button>
    </section>

    <section class="stats-grid summary-stats invoice-stats">
      <div class="stat-card summary-logo-card">
        <CommonOctanoLogo class="summary-logo" />
        <div>Total cobrado</div>
        <strong>{{ dinero(totalCobrado) }}</strong>
        <small>{{ cobrados.length }} {{ cobrados.length === 1 ? 'factura cobrada' : 'facturas cobradas' }}</small>
      </div>
      <div class="stat-card">
        <div>Pendiente de cobro</div>
        <strong>{{ dinero(saldoPendiente) }}</strong>
        <small>{{ pendientesDeCobro.length }} {{ pendientesDeCobro.length === 1 ? 'factura emitida' : 'facturas emitidas' }}</small>
      </div>
      <div class="stat-card">
        <div>Facturas simuladas emitidas</div>
        <strong>{{ cantidadEmitidos }}</strong>
        <small>Numeración simulada</small>
      </div>
    </section>

    <section class="panel ready-orders-panel">
      <div class="billing-filters">
        <CommonDatePicker v-model="mes" label="Mes y año" type="month" year-selection compact :max-date="maxMes" />
        <label>
          Cliente
          <input v-model="clienteFiltro" type="search" placeholder="Nombre, documento o patente…" aria-label="Filtrar por cliente" />
        </label>
        <div v-if="mes || clienteFiltro || conPresupuesto !== 'todos'" class="filtro-limpiar">
          <span aria-hidden="true"></span>
          <button class="button small outlined" @click="limpiarFiltros">Limpiar filtros</button>
        </div>
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
          {{ seccionOpcion.value === 'ordenes' ? ordenesVisibles.length + borradores.length : seccionOpcion.value === 'cobrados' ? cobrados.length : pendientesDeCobro.length }}
          )
        </button>
      </div>

      <p v-if="seccion === 'ordenes'" class="billing-section-description muted">Armá la factura desde la orden de trabajo con su presupuesto asociado. Si no tiene presupuesto, agregá los repuestos y la mano de obra.</p>
      <div v-if="seccion === 'ordenes'" class="segmented status-filters billing-budget-filters" role="group" aria-label="Filtrar por presupuesto">
        <button v-for="filtro in presupuestoFiltros" :key="filtro.value" type="button" :class="{ selected: conPresupuesto === filtro.value }" @click="conPresupuesto = filtro.value">
          {{ filtro.label }} ({{ filtro.value === 'todos' ? ordenesPendientes.length + borradores.length : ordenesPendientes.filter((orden) => !!orden.presupuestoId === (filtro.value === 'con')).length }})
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
          tabindex="0"
          :aria-label="`Orden de trabajo #${orden.ordenId}. Click para armar factura`"
          @click="generarDesdeOrden(orden)"
          @keydown.enter.self="generarDesdeOrden(orden)"
          @keydown.space.self.prevent="generarDesdeOrden(orden)"
        >
          <div class="ready-card-top">
            <div>
              <span class="plate small-plate">{{ orden.patente }}</span>
              <h4>{{ orden.marca }} {{ orden.modelo }}</h4>
              <small class="muted">{{ orden.clienteNombre }} · OT #{{ orden.ordenId }}</small>
            </div>
            <span v-if="orden.presupuestoId" class="badge neutral budget-origin">De presupuesto #{{ orden.presupuestoId }}</span>
          </div>
          <p class="ready-service-desc">{{ orden.descripcion || 'Trabajo finalizado' }}</p>
          <div class="billing-card-status"><span class="badge neutral">Sin emitir</span><span v-if="!orden.presupuestoId" class="badge neutral">Sin presupuesto</span></div>
          <small class="muted">{{ fechaLabel(orden.finalizadaEn) }}</small>
          <div class="order-price-badge">
            <span class="price-lbl">{{ orden.presupuestoId ? 'Total presupuesto' : 'Total a emitir' }}</span>
            <strong class="price-num">{{ dinero(totalOrden(orden)) }}</strong>
          </div>
          <span class="budget-detail-hint">Click para armar factura</span>
          <div class="ready-card-actions">
            <button type="button" class="button small primary" :disabled="ocupado === orden.ordenId" @click.stop="generarDesdeOrden(orden)">
              <Receipt :size="14" />Emitir factura
            </button>
            <button type="button" class="button small btn-cobro" @click.stop="abrirManual(orden)">
              <Pencil :size="14" />Armar manualmente
            </button>
          </div>
        </article>

        <article
          v-for="comprobante in [...todos]"
          v-show="seccion === (comprobante.estado === 'COBRADO' ? 'cobrados' : comprobante.estado === 'BORRADOR' ? 'ordenes' : 'pendientes')"
          :key="`comprobante-${comprobante.id}`"
          class="ready-order-card"
          tabindex="0"
          :aria-label="`${comprobante.numero ? 'Factura' : 'Borrador'} #${comprobante.id}. Click para ${comprobante.estado === 'BORRADOR' ? 'editar' : 'ver detalle'}`"
          @click="comprobante.estado === 'BORRADOR' ? editarBorrador(comprobante) : abrirDetalle(comprobante.id)"
          @keydown.enter.self="comprobante.estado === 'BORRADOR' ? editarBorrador(comprobante) : abrirDetalle(comprobante.id)"
          @keydown.space.self.prevent="comprobante.estado === 'BORRADOR' ? editarBorrador(comprobante) : abrirDetalle(comprobante.id)"
        >
          <div class="ready-card-top">
            <div>
              <span class="plate small-plate">{{ comprobante.patente }}</span>
              <h4>{{ comprobante.marca }} {{ comprobante.modelo }}</h4>
              <small class="muted">{{ comprobante.clienteNombre }} · {{ comprobante.numero ? `Factura #${comprobante.id}` : `Borrador #${comprobante.id}` }}</small>
            </div>
          </div>
          <p class="ready-service-desc">{{ comprobante.descripcion || 'Factura simulada' }}</p>
          <div class="billing-card-status">
            <span class="badge" :class="comprobante.estado === 'COBRADO' ? 'green' : 'neutral'">{{ comprobante.estado === 'BORRADOR' ? 'Sin emitir' : estadoDe(comprobante).label }}</span>
            <span v-if="comprobante.estado === 'BORRADOR'" class="badge neutral">Borrador guardado</span>
            <span v-else class="badge neutral">Factura {{ comprobante.tipo }} simulada</span>
          </div>
          <small class="muted">{{ fechaLabel(comprobante.fecha) }}</small>
          <div class="order-price-badge"><span class="price-lbl">{{ comprobante.estado === 'BORRADOR' ? 'Total a emitir' : 'Total factura' }}</span><strong class="price-num">{{ dinero(comprobante.total) }}</strong></div>
          <small v-if="comprobante.pagado" class="muted">Pagado: {{ dinero(comprobante.pagado) }} · Saldo: {{ dinero(comprobante.saldo) }}</small>
          <span class="budget-detail-hint">{{ comprobante.estado === 'BORRADOR' ? 'Click para editar borrador' : 'Click para ver detalle' }}</span>
          <div class="ready-card-actions">
            <button v-if="comprobante.estado === 'BORRADOR'" type="button" class="button small primary" :disabled="ocupado === comprobante.id" @click.stop="emitirComprobante(comprobante)">
              <Receipt :size="14" />Emitir factura
            </button>
            <button v-if="Number(comprobante.saldo) > 0" type="button" class="button small btn-cobro" @click.stop="abrirPago(comprobante)">
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
.billing-filters :deep(.date-picker-trigger) { height: 40px; min-height: 40px; box-sizing: border-box; padding: 6px 10px; }
.billing-filters label input { width: 240px; max-width: 100%; }
.billing-filters .filtro-limpiar { display: grid; gap: 7px; }
.billing-filters .filtro-limpiar > span { font-size: 12px; line-height: normal; }
.billing-filters .filtro-limpiar button { height: 40px; padding: 0 12px; white-space: nowrap; }
.billing-budget-filters { margin-bottom: 18px; flex-wrap: wrap; }
.billing-order-filters { margin-bottom: 16px; flex-wrap: wrap; }
.billing-card-status { display: flex; flex-wrap: wrap; gap: 6px; }
.billing-section-description { margin: 0 0 16px; font-size: 13px; }
.invoice-stats { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.ready-card-top h4 { margin: 4px 0 2px; }
.estado-carga { display: flex; align-items: center; gap: 8px; padding: 20px 0; color: var(--muted); font-size: 13px; }
.spin { animation: girar 1s linear infinite; }
@keyframes girar { to { transform: rotate(360deg); } }
.ready-orders-panel { padding: 18px 20px; margin-bottom: 22px; background: #ffffff; border: 1.5px solid #dbeafe; }
:global(html.dark .ready-orders-panel) { background: #252528; border-color: rgba(255, 255, 255, 0.08); }
.ready-orders-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(290px, 100%), 1fr)); gap: 14px; }
.ready-order-card { cursor: pointer; background: #f8fafc; border: 1px solid var(--line); border-radius: 12px; padding: 14px; display: flex; flex-direction: column; justify-content: space-between; gap: 10px; transition: all 0.15s ease; }
:global(html.dark .ready-order-card) { background: #2c2c2e; border-color: rgba(255, 255, 255, 0.1); }
.ready-order-card:hover { border-color: #bfdbfe; transform: translateY(-1px); }
:global(html.dark .ready-order-card:hover) { border-color: rgba(255, 255, 255, 0.22); }
.ready-order-card:focus-visible { outline: 2px solid currentColor; outline-offset: 3px; }
.ready-card-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; }
.ready-card-top > div { min-width: 0; }
.budget-origin { flex-shrink: 0; margin-left: auto; }
.order-price-badge { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-top: auto; padding-top: 10px; border-top: 1px solid var(--line); }
.price-lbl { display: block; font-size: 12px; color: #64748b; }
.price-num { font-size: 18px; color: #0f172a; font-weight: 850; }
:global(html.dark .order-price-badge .price-num) { color: #f5f5f7; }
.ready-service-desc { font-size: 12.5px; color: #475569; margin: 0; line-height: 1.4; }
:global(html.dark .ready-service-desc) { color: #cbd5e1; }
.budget-detail-hint { font-size: 13px; font-weight: 700; color: #334155; }
:global(html.dark .budget-detail-hint) { color: #e2e8f0; }
.ready-card-actions { display: flex; gap: 8px; margin-top: 6px; flex-wrap: wrap; }
.button.btn-cobro { background: #ffffff !important; border-color: #cbd5e1 !important; color: #0f172a !important; }
.button.btn-cobro:hover { background: #f8fafc !important; border-color: #94a3b8 !important; }
:global(html.dark .ready-card-actions .button.btn-cobro) { background: #3a3a3c !important; border-color: #52525b !important; color: #f5f5f7 !important; }
:global(html.dark .ready-card-actions .button.btn-cobro:hover) { background: #48484a !important; border-color: #71717a !important; }
@media (max-width: 640px) { .invoice-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); } .billing-filters > label { width: 100%; min-width: 0; } .billing-filters label input { width: 100%; } }
@media (max-width: 760px) { .invoice-stats .stat-card { padding: 12px; } .invoice-stats .stat-card > strong { font-size: clamp(18px, 5vw, 24px); } .ready-orders-panel { padding: 12px; } .ready-orders-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; } .ready-order-card { min-width: 0; padding: 10px; gap: 8px; overflow-wrap: anywhere; } .ready-card-top { flex-direction: column; } .ready-card-top h4 { font-size: 13px; } .budget-origin { margin-left: 0; white-space: normal; } .order-price-badge { flex-direction: column; align-items: flex-start; gap: 4px; } .price-num { font-size: 16px; } .budget-detail-hint { font-size: 11px; } .ready-card-actions { flex-direction: column; } .ready-card-actions > .button { width: 100%; padding: 8px; font-size: 10px; white-space: normal; } .billing-card-status .badge { white-space: normal; } }
</style>
