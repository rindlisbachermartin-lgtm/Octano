<script setup lang="ts">
import {
  ArrowDownToLine,
  ChartNoAxesCombined,
  ClipboardList,
  Receipt,
} from 'lucide-vue-next'

const { db, revenue, unpaid } = useDatabase()
const { money, statusClass } = useHelpers()
const { notify } = useToast()

function downloadReport() {
  const rows = [
    ['Indicador', 'Valor'],
    ['Ordenes del mes', db.value.orders.length],
    ['Facturas pendientes', unpaid.value.length],
    ['Ingresos cobrados', revenue.value],
  ]
  const url = URL.createObjectURL(
    new Blob(['\uFEFF' + rows.map((r) => r.join(';')).join('\r\n')], {
      type: 'text/csv;charset=utf-8;',
    })
  )
  const a = document.createElement('a')
  a.href = url
  a.download = 'octano-reporte-septiembre-2026.csv'
  a.click()
  URL.revokeObjectURL(url)
  notify('Reporte descargado.')
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          <span class="tiny-star">✳</span>
          TALLER CENTRAL / REPORTES
        </div>
        <h1>Tu taller, en perspectiva.</h1>
      </div>
      <button class="button primary" @click="downloadReport">
        <ArrowDownToLine :size="17" />Exportar reporte
      </button>
    </section>

    <section class="stats-grid report-stats">
      <div class="stat-card">
        <div><span>Órdenes del mes</span><ClipboardList :size="19" /></div>
        <strong>{{ db.orders.length }}</strong>
        <small>
          {{ db.orders.filter((o) => o.status === 'Finalizado').length }} finalizadas
          <span class="separator">/</span>
          {{ db.orders.filter((o) => o.status === 'En proceso').length }} en curso
        </small>
      </div>
      <div class="stat-card">
        <div><span>Facturas por cobrar</span><Receipt :size="19" /></div>
        <strong>{{ unpaid.length }}</strong>
        <small>{{ money(unpaid.reduce((s, i) => s + i.total, 0)) }} por ingresar</small>
      </div>
      <div class="stat-card revenue-card">
        <div><span>Ingresos cobrados</span><ChartNoAxesCombined :size="19" /></div>
        <strong>{{ money(revenue) }}</strong>
        <small>Comprobantes marcados como cobrados</small>
      </div>
    </section>

    <div class="dashboard-columns">
      <section class="panel report-chart">
        <h2>Ingresos por día</h2>
        <p class="muted">Importes cobrados · Septiembre 2026</p>
        <div
          class="chart"
          role="img"
          aria-label="Ingresos cobrados por día de septiembre 2026"
        >
          <div v-for="day in 7" :key="day" class="chart-column">
            <span>
              {{
                money(
                  db.invoices
                    .filter((i) => i.status === 'Cobrado' && Number(i.date.slice(-2)) === day)
                    .reduce((s, i) => s + i.total, 0)
                )
              }}
            </span>
            <i
              :style="{
                height: `${Math.min(
                  100,
                  db.invoices
                    .filter((i) => i.status === 'Cobrado' && Number(i.date.slice(-2)) === day)
                    .reduce((s, i) => s + i.total, 0) / 2000
                )}%`,
              }"
            ></i>
            <small>{{ day }} SEP</small>
          </div>
        </div>
      </section>

      <section class="panel report-summary">
        <span class="eyebrow">ASÍ VAMOS</span>
        <h2>Estado de órdenes</h2>
        <div
          v-for="s in ['En espera', 'En proceso', 'Finalizado']"
          :key="s"
          class="report-line"
        >
          <span :class="['badge', statusClass(s)]">{{ s }}</span>
          <strong>{{ db.orders.filter((o) => o.status === s).length }}</strong>
        </div>
        <p class="muted">
          Los indicadores se actualizan cuando registrás órdenes y cobros.
        </p>
      </section>
    </div>
  </div>
</template>
