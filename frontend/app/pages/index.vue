<script setup lang="ts">
import { Plus, CalendarDays, LogIn, Package, Wrench, Wallet, Receipt, CheckCircle2 } from '@lucide/vue'
import { useDashboard } from '~/composables/useDashboard'
import { type OrdenApi } from '~/utils/ordenApi'
import { vehicleLabel } from '~/utils/vehicleDisplay'

const { workshopName, puedeAdministrar } = useAuthApi()
const { money } = useHelpers()
const { ordenes, turnos, stock, resumen, errores, cargando, actualizado, recargar, today, label } = useDashboard()
const estado = ref<'proceso' | 'espera'>('proceso')
const trabajos = computed(() => ordenes.value?.[estado.value])
const seleccionada = ref<number | null>(null)
const formulario = ref(false)
const editando = ref<OrdenApi | null>(null)
const tarjetas = computed(() => [
  { titulo: 'Órdenes en taller', valor: ordenes.value ? ordenes.value.proceso.totalElementos + ordenes.value.espera.totalElementos : null, detalle: 'En espera y en proceso', icono: Wrench, to: ordenes.value?.proceso.totalElementos ? '/ordenes?estado=EN_PROCESO' : '/ordenes?estado=EN_ESPERA', error: errores.ordenes },
  { titulo: 'Turnos de hoy', valor: turnos.value?.total ?? null, detalle: 'Pendientes de ingreso hoy', icono: CalendarDays, to: `/agenda?fecha=${today.value}`, error: errores.turnos },
  { titulo: 'Órdenes por ingresar', valor: ordenes.value?.porIngresar ?? null, detalle: 'Reservas pendientes de ingreso', icono: LogIn, to: '/ordenes?estado=PENDIENTE_INGRESO', error: errores.ordenes },
  { titulo: 'Stock bajo', valor: stock.value?.totalElementos ?? null, detalle: 'Repuestos en mínimo o por debajo', icono: Package, to: '/inventario?stockBajo=1', error: errores.stock },
])
const horaActualizacion = computed(() => actualizado.value?.toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' }))
const comparativoIngresos = computed(() => {
  const desglose = resumen.value?.ingresosPorConcepto
  if (!desglose) return []
  const total = resumen.value?.ingresos ?? 0
  const repuestos = Number(desglose.repuestos)
  const trabajos = Number(desglose.trabajos)
  const porcentaje = (valor: number) => total > 0 ? Math.round(valor / total * 100) : 0
  return [
    { titulo: 'Venta de repuestos', valor: repuestos, porcentaje: porcentaje(repuestos), icono: Package },
    { titulo: 'Trabajos', valor: trabajos, porcentaje: porcentaje(trabajos), icono: Wrench },
  ]
})

function nuevaOrden() { editando.value = null; formulario.value = true }
function editarOrden(orden: OrdenApi) { seleccionada.value = null; editando.value = orden; formulario.value = true }
function guardada() { formulario.value = false; editando.value = null; recargar() }
</script>

<template>
  <div class="page-content dashboard">
    <section class="page-heading dashboard-heading">
      <div>
        <div class="eyebrow">{{ workshopName || 'TU TALLER' }}</div>
        <h1>Tu taller, de un vistazo.</h1>
        <p class="dashboard-date">{{ label }}</p>
      </div>
      <div class="dashboard-actions">
        <button class="button primary" @click="nuevaOrden"><Plus :size="16" />Nueva orden</button>
      </div>
    </section>

    <div class="metrics-grid" aria-label="Resumen operativo" :aria-busy="cargando">
      <NuxtLink v-for="tarjeta in tarjetas" :key="tarjeta.titulo" :to="tarjeta.to" class="metric-card">
        <div class="metric-label"><span>{{ tarjeta.titulo }}</span><component :is="tarjeta.icono" :size="18" aria-hidden="true" /></div>
        <strong class="metric-value">{{ tarjeta.valor === null ? '—' : tarjeta.valor.toLocaleString('es-AR') }}</strong>
        <span class="metric-detail" :class="{ 'metric-error': tarjeta.error }">{{ tarjeta.error ? 'No disponible. Reintentá la consulta.' : tarjeta.detalle }}</span>
      </NuxtLink>
    </div>

    <div class="dashboard-columns">
      <section class="panel work-panel" aria-labelledby="trabajos-heading">
        <div class="dashboard-panel-heading">
          <div><h2 id="trabajos-heading">Trabajos en marcha</h2><p>El estado actual de tus órdenes.</p></div>
          <NuxtLink to="/ordenes" class="text-button">Ver órdenes</NuxtLink>
        </div>
        <div class="work-tabs" role="group" aria-label="Estado de las órdenes">
          <button v-for="opcion in (['proceso', 'espera'] as const)" :key="opcion" type="button" :aria-pressed="estado === opcion" :class="{ selected: estado === opcion }" @click="estado = opcion">
            {{ opcion === 'proceso' ? 'En proceso' : 'En espera' }}<span>{{ ordenes?.[opcion].totalElementos ?? '—' }}</span>
          </button>
        </div>
        <div v-if="errores.ordenes" class="dashboard-message" role="alert"><p>{{ errores.ordenes }}</p><button class="button small" :disabled="cargando" @click="recargar">Reintentar órdenes</button></div>
        <div v-else-if="!trabajos" class="dashboard-message" role="status">Cargando trabajos…</div>
        <div v-else-if="!trabajos.contenido.length" class="dashboard-message dashboard-empty"><Wrench :size="28" /><h3>{{ estado === 'proceso' ? 'No hay trabajos en proceso' : 'No hay órdenes en espera' }}</h3><p>Las órdenes aparecerán acá cuando estén en este estado.</p></div>
        <div v-else class="table-scroll">
          <table class="dashboard-table">
            <thead><tr><th scope="col">Trabajo</th><th scope="col">Vehículo</th><th scope="col">Cliente</th><th scope="col">Mecánico</th></tr></thead>
            <tbody><tr v-for="orden in trabajos.contenido" :key="orden.id">
              <td>
                <button type="button" class="work-link" :aria-label="`Ver orden ${orden.id}, ${orden.patente}`" @click="seleccionada = orden.id">
                  <strong class="work-description">#{{ orden.id }} · {{ orden.descripcion }}</strong>
                </button>
              </td>
              <td class="vehicle-cell">{{ vehicleLabel(orden.marca, orden.modelo, orden.version) }} <span class="work-plate">{{ orden.patente }}</span></td>
              <td class="client-cell">{{ orden.clienteNombre }}</td>
              <td class="mechanic-cell">{{ orden.mecanicoNombre || 'Sin asignar' }}</td>
            </tr></tbody>
          </table>
          <p class="panel-footnote">Mostrando {{ trabajos.contenido.length }} de {{ trabajos.totalElementos }} órdenes. <NuxtLink :to="`/ordenes?estado=${estado === 'proceso' ? 'EN_PROCESO' : 'EN_ESPERA'}`">Ver listado completo</NuxtLink></p>
        </div>
      </section>

      <div class="dashboard-aside">
        <section class="panel" aria-labelledby="agenda-heading">
          <div class="dashboard-panel-heading"><div><h2 id="agenda-heading">La agenda de hoy</h2><p>Turnos pendientes de ingreso.</p></div><NuxtLink :to="`/agenda?fecha=${today}`" class="text-button">Ver agenda<span v-if="turnos && turnos.total > turnos.contenido.length" class="panel-extra">+{{ turnos.total - turnos.contenido.length }}</span></NuxtLink></div>
          <div v-if="errores.turnos" class="dashboard-message" role="alert"><p>{{ errores.turnos }}</p><button class="button small" :disabled="cargando" @click="recargar">Reintentar agenda</button></div>
          <div v-else-if="!turnos" class="dashboard-message" role="status">Cargando turnos…</div>
          <div v-else-if="!turnos.contenido.length" class="dashboard-message dashboard-empty"><CheckCircle2 :size="25" /><p>No quedan turnos pendientes para hoy.</p></div>
          <ul v-else class="appointment-list"><li v-for="turno in turnos.contenido" :key="turno.id"><NuxtLink :to="`/agenda?fecha=${today}`" class="appointment-link"><time :datetime="`${turno.fecha}T${turno.horaInicio}`">{{ turno.horaInicio.slice(0, 5) }}</time><div><strong>{{ turno.motivoVisita }}</strong><p>{{ vehicleLabel(turno.marca, turno.modelo, turno.version) }} <span class="appointment-client">· {{ turno.clienteNombre }}</span> · <span class="appointment-patente">{{ turno.patente }}</span></p></div></NuxtLink></li></ul>
        </section>

        <section class="panel" aria-labelledby="stock-heading">
          <div class="dashboard-panel-heading"><div><h2 id="stock-heading">Atención al stock</h2><p>Repuestos que necesitan reposición.</p></div><NuxtLink to="/inventario?stockBajo=1" class="text-button">Ver stock<span v-if="stock && stock.totalElementos > stock.contenido.length" class="panel-extra">+{{ stock.totalElementos - stock.contenido.length }}</span></NuxtLink></div>
          <div v-if="errores.stock" class="dashboard-message" role="alert"><p>{{ errores.stock }}</p><button class="button small" :disabled="cargando" @click="recargar">Reintentar stock</button></div>
          <div v-else-if="!stock" class="dashboard-message" role="status">Cargando stock…</div>
          <div v-else-if="!stock.contenido.length" class="dashboard-message dashboard-empty"><Package :size="25" /><p>Sin repuestos por debajo del mínimo.</p></div>
          <ul v-else class="stock-list"><li v-for="repuesto in stock.contenido" :key="repuesto.id"><div><strong>{{ repuesto.descripcion }}</strong><small>{{ repuesto.marca }} · {{ repuesto.codigoOem }}</small></div><span class="stock-quantity" :class="{ depleted: Number(repuesto.stock) <= 0 }"><template v-if="Number(repuesto.stock) <= 0">Sin stock</template><template v-else>{{ Number(repuesto.stock).toLocaleString('es-AR') }} en stock</template><small>Mín. {{ Number(repuesto.stockMinimo).toLocaleString('es-AR') }}</small></span></li></ul>
        </section>
      </div>
    </div>

    <section v-if="puedeAdministrar" class="panel finance-panel" aria-labelledby="finanzas-heading">
      <div class="dashboard-panel-heading"><div><h2 id="finanzas-heading">Administración</h2><p>Cobros del mes y saldos actuales.</p></div><NuxtLink to="/facturacion" class="text-button">Ver facturación</NuxtLink></div>
      <div v-if="errores.resumen" class="dashboard-message" role="alert"><p>{{ errores.resumen }}</p><button class="button small" :disabled="cargando" @click="recargar">Reintentar resumen</button></div>
      <div v-else-if="!resumen" class="dashboard-message" role="status">Cargando resumen…</div>
      <div v-else>
        <div class="finance-grid">
          <div>
            <span><Wallet :size="15" />Cobrado este mes</span>
            <strong>{{ money(resumen.ingresos) }}</strong>
            <template v-if="resumen.ingresosPorConcepto">
              <small>Trabajos: {{ money(resumen.ingresosPorConcepto.trabajos) }}</small>
              <small>Repuestos: {{ money(resumen.ingresosPorConcepto.repuestos) }}</small>
            </template>
            <small v-else>Desglose no disponible</small>
            <small>{{ resumen.cantidadCobros }} {{ resumen.cantidadCobros === 1 ? 'cobro registrado' : 'cobros registrados' }}</small>
          </div>
          <div><span><Receipt :size="15" />Saldo por cobrar</span><strong>{{ money(resumen.comprobantesPorCobrar.importe) }}</strong><small>{{ resumen.comprobantesPorCobrar.cantidad }} {{ resumen.comprobantesPorCobrar.cantidad === 1 ? 'comprobante con saldo pendiente' : 'comprobantes con saldo pendiente' }}</small></div>
        </div>
        <div v-if="comparativoIngresos.length" class="finance-comparison">
          <div class="comparison-heading"><h3>Ingresos por concepto</h3><span>Este mes</span></div>
          <div v-for="concepto in comparativoIngresos" :key="concepto.titulo" class="comparison-row">
            <div class="comparison-label"><component :is="concepto.icono" :size="15" aria-hidden="true" /><span>{{ concepto.titulo }}</span></div>
            <div class="comparison-bar" aria-hidden="true"><span :style="{ width: `${concepto.porcentaje}%` }" /></div>
            <strong>{{ money(concepto.valor) }}</strong>
            <small>{{ concepto.porcentaje }}%</small>
          </div>
        </div>
      </div>
    </section>
    <p v-if="horaActualizacion" class="dashboard-updated">{{ Object.values(errores).some(Boolean) ? 'Consulta completada con información pendiente' : 'Última actualización' }} · {{ horaActualizacion }}</p>

    <OrdenesDetalleApi v-if="seleccionada !== null" :id="seleccionada" @close="seleccionada = null" @edit="editarOrden" @updated="recargar" />
    <OrdenesFormularioApi v-if="formulario" :orden="editando" @close="formulario = false" @saved="guardada" />
  </div>
</template>

<style scoped>
.dashboard { --dash-surface: #fff; --dash-soft: #fafafa; --dash-text: #18181b; --dash-muted: #71717a; --dash-line: #e4e4e7; color: var(--dash-text); }
.dashboard-heading { gap: 20px; }
.dashboard-heading h1 { color: var(--dash-text); }
.dashboard-date { margin: 8px 0 0; color: var(--dash-muted); font-size: 13px; }
.dashboard-actions { display: flex; gap: 8px; flex-shrink: 0; }
.metrics-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-bottom: 22px; }
.metric-card { display: flex; flex-direction: column; gap: 14px; padding: 20px; border: 1px solid var(--dash-line); border-radius: 12px; background: var(--dash-surface); color: inherit; text-decoration: none; }
.metric-card:hover { border-color: var(--blue); }
.metric-label { display: flex; justify-content: space-between; align-items: center; gap: 12px; font-size: 12px; font-weight: 600; }
.metric-label svg { flex-shrink: 0; color: var(--dash-muted); }
.metric-value { font-size: 34px; font-weight: 600; letter-spacing: -1.2px; line-height: 1; font-variant-numeric: tabular-nums; }
.metric-detail { font-size: 11px; color: var(--dash-muted); line-height: 1.5; }
.metric-error { color: #b45309; }
.dashboard-columns { display: grid; grid-template-columns: minmax(0, 1.65fr) minmax(300px, 1fr); gap: 20px; align-items: start; }
.work-panel { align-self: stretch; }
.dashboard .panel { padding: 0; overflow: hidden; border: 1px solid var(--dash-line); background: var(--dash-surface); box-shadow: none; border-radius: 12px; }
.dashboard-panel-heading { display: flex; justify-content: space-between; align-items: start; gap: 12px; padding: 20px; }
.dashboard-panel-heading h2 { margin: 0; font-size: 15px; font-weight: 600; letter-spacing: -.25px; }
.dashboard-panel-heading p { margin: 6px 0 0; font-size: 12px; color: var(--dash-muted); }
.dashboard-panel-heading .text-button { white-space: nowrap; font-size: 12px; }
.dashboard-aside { display: grid; gap: 20px; }
.work-tabs { display: flex; gap: 6px; margin: 0 20px 16px; }
.work-tabs button { display: flex; align-items: center; gap: 8px; padding: 7px 10px; border: 1px solid transparent; border-radius: 7px; font-size: 12px; color: var(--dash-muted); background: transparent; }
.work-tabs button.selected { border-color: var(--dash-line); color: var(--dash-text); background: var(--dash-soft); }
.work-tabs button span { font-size: 10px; font-variant-numeric: tabular-nums; }
.dashboard-table { width: 100%; min-width: 560px; border-collapse: collapse; }
.dashboard-table th { padding: 11px 16px; text-align: left; font-size: 10px; font-weight: 500; text-transform: uppercase; letter-spacing: .5px; color: var(--dash-muted); background: var(--dash-soft); border-block: 1px solid var(--dash-line); }
.dashboard-table td { padding: 18px 16px; text-align: left; vertical-align: middle; border-bottom: 1px solid var(--dash-line); }
.work-link { display: block; width: 100%; padding: 0; background: transparent; text-align: left; color: var(--dash-text); }
.work-link:hover strong { color: var(--blue); }
.work-plate { color: var(--dash-muted); font-weight: 500; }
.work-description { font-size: 13px; font-weight: 600; line-height: 18px; overflow-wrap: anywhere; }
.vehicle-cell, .client-cell, .mechanic-cell { font-size: 11px; }
.panel-footnote { padding: 14px 20px; margin: 0; color: var(--dash-muted); font-size: 11px; line-height: 1.7; }
.panel-footnote a { color: var(--blue); }
.dashboard-message { padding: 28px 20px; color: var(--dash-muted); font-size: 13px; line-height: 1.7; }
.dashboard-message p { margin: 0 0 12px; }
.dashboard-empty { display: grid; justify-items: center; gap: 12px; text-align: center; padding-block: 32px; }
.dashboard-empty h3 { margin: 0; color: var(--dash-text); font-size: 14px; font-weight: 500; }
.dashboard-empty p { margin: 0; max-width: 280px; font-size: 12px; }
.appointment-list, .stock-list { list-style: none; margin: 0; padding: 0 20px; }
.appointment-list li + li, .stock-list li + li { border-top: 1px solid var(--dash-line); }
.appointment-link { display: flex; align-items: baseline; gap: 12px; padding: 9px 0; color: inherit; text-decoration: none; }
.appointment-link time { font-size: 12px; font-weight: 600; color: var(--blue); font-variant-numeric: tabular-nums; }
.appointment-link > div { min-width: 0; }
.appointment-link strong { font-size: 13px; font-weight: 600; line-height: 18px; overflow-wrap: anywhere; }
.appointment-link p { margin: 3px 0 0; font-size: 11px; line-height: 15px; color: var(--dash-text); }
.appointment-client { color: var(--dash-text); }
.appointment-patente { color: var(--dash-muted); font-weight: 500; }
.panel-extra { margin-left: 6px; padding: 1px 6px; border: 1px solid var(--dash-line); border-radius: 999px; background: var(--dash-soft); color: var(--dash-muted); font-size: 10px; font-variant-numeric: tabular-nums; }
.stock-list small { display: block; color: var(--dash-muted); font-size: 11px; }
.stock-list li { display: flex; justify-content: space-between; align-items: baseline; gap: 14px; padding: 9px 0; }
.stock-list strong { font-size: 12px; font-weight: 500; line-height: 18px; }
.stock-list small { margin-top: 3px; line-height: 15px; }
.stock-quantity { text-align: right; font-size: 11px; font-weight: 600; line-height: 18px; font-variant-numeric: tabular-nums; white-space: nowrap; }
.stock-quantity.depleted { color: #dc2626; }
.stock-quantity small { font-weight: 400; }
.finance-panel { margin-top: 20px; }
.finance-grid { display: grid; grid-template-columns: 1fr 1fr; border-top: 1px solid var(--dash-line); }
.finance-grid > div { display: grid; gap: 10px; padding: 22px; }
.finance-grid > div + div { border-left: 1px solid var(--dash-line); }
.finance-grid span { display: flex; align-items: center; gap: 8px; color: var(--dash-muted); font-size: 12px; }
.finance-grid strong { font-size: 27px; font-weight: 500; letter-spacing: -.6px; font-variant-numeric: tabular-nums; }
.finance-grid small { color: var(--dash-muted); font-size: 11px; }
.finance-comparison { display: grid; gap: 14px; padding: 20px 22px; border-top: 1px solid var(--dash-line); }
.comparison-heading { display: flex; justify-content: space-between; align-items: center; }
.comparison-heading h3 { margin: 0; font-size: 12px; font-weight: 600; }
.comparison-heading span { font-size: 11px; color: var(--dash-muted); }
.comparison-row { display: grid; grid-template-columns: 160px minmax(0, 1fr) 90px 38px; align-items: center; gap: 12px; }
.comparison-label { display: flex; align-items: center; gap: 8px; font-size: 12px; }
.comparison-label svg { color: var(--dash-muted); }
.comparison-bar { height: 8px; border-radius: 999px; background: var(--dash-soft); overflow: hidden; }
.comparison-bar > span { display: block; height: 100%; border-radius: inherit; background: var(--blue); }
.comparison-row + .comparison-row .comparison-bar > span { background: #16a34a; }
.comparison-row strong { font-size: 13px; font-weight: 600; text-align: right; font-variant-numeric: tabular-nums; }
.comparison-row small { color: var(--dash-muted); font-size: 11px; text-align: right; font-variant-numeric: tabular-nums; }
.dashboard-updated { margin: 16px 0 0; text-align: right; color: var(--dash-muted); font-size: 10px; }
:global(html.dark .dashboard) { --dash-surface: #1c1c1e; --dash-soft: #242426; --dash-text: #f5f5f7; --dash-muted: #a1a1aa; --dash-line: #38383a; }
:global(html.dark .dashboard .stock-quantity.depleted) { color: #f87171; }
:global(html.dark .dashboard .metric-error) { color: #fbbf24; }
@media (max-width: 1200px) { .dashboard-columns { grid-template-columns: minmax(0, 1fr); } .dashboard-aside { grid-template-columns: 1fr 1fr; } }
@media (max-width: 760px) { .metrics-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; } .metric-card { padding: 16px; } .dashboard-heading { align-items: start; flex-direction: column; } .dashboard-actions { width: 100%; } .dashboard-aside { grid-template-columns: 1fr; } .finance-grid { grid-template-columns: 1fr; } .finance-grid > div + div { border-left: 0; border-top: 1px solid var(--dash-line); } .comparison-row { grid-template-columns: minmax(0, 1fr) auto auto; } .comparison-bar { grid-column: 1 / -1; grid-row: 2; } }
@media (max-width: 400px) { .metrics-grid { grid-template-columns: 1fr; } .dashboard-actions { flex-wrap: wrap; } }
</style>
