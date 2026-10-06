<script setup lang="ts">
import {
  CarFront,
  CalendarDays,
  CircleCheck,
  ChartNoAxesCombined,
  ArrowUpRight,
  Clock3,
} from 'lucide-vue-next'

const { db, activeOrders, finished, unpaid, revenue } = useDatabase()
const { money } = useHelpers()

const nextAppointmentTime = computed(() => {
  const next = db.value.appointments
    .filter((a) => a.date === '2026-09-07' && a.status === 'Confirmado')
    .sort((a, b) => a.time.localeCompare(b.time))[0]
  return next?.time || '—'
})
</script>

<template>
  <section class="stats-grid" aria-label="Resumen del día">
    <NuxtLink to="/ordenes" class="stat-card vehicles-stat-card">
      <div><span>Vehículos en taller</span><CarFront :size="19" /></div>
      <strong>
        {{ String(activeOrders.length).padStart(2, '0') }}
        <span class="stat-caption">en buenas manos</span>
      </strong>
      <small>
        <span class="green-dot"></span>
        {{ db.orders.filter((o) => o.status === 'En proceso').length }} en proceso
        <span class="separator">/</span>
        {{ db.orders.filter((o) => o.status === 'En espera').length }} en espera
      </small>
      <CommonOctanoLogo class="vehicles-stat-logo" />
    </NuxtLink>

    <NuxtLink to="/agenda" class="stat-card">
      <div><span>Turnos de hoy</span><CalendarDays :size="19" /></div>
      <strong>
        {{
          String(
            db.appointments.filter((a) => a.date === '2026-09-07' && a.status !== 'Cancelado').length
          ).padStart(2, '0')
        }}
        <span class="mini-spark"><i></i><i></i><i></i><i></i><i></i><i></i><i></i></span>
      </strong>
      <small>
        <Clock3 :size="13" />Próximo turno a las {{ nextAppointmentTime }}
      </small>
    </NuxtLink>

    <NuxtLink to="/ordenes" class="stat-card">
      <div><span>Listos para entregar</span><CircleCheck :size="19" /></div>
      <strong>
        {{ String(finished.length).padStart(2, '0') }}
        <span class="stat-arrow"><ArrowUpRight :size="23" /></span>
      </strong>
      <small>
        <span class="green-dot"></span>
        {{ finished.length ? 'Un trabajo bien hecho.' : 'Cada detalle cuenta.' }}
      </small>
    </NuxtLink>

    <NuxtLink to="/facturacion" class="stat-card revenue-card">
      <div><span>Ingresos del mes</span><ChartNoAxesCombined :size="19" /></div>
      <strong>{{ money(revenue) }}</strong>
      <small>
        <span class="revenue-label">SEPTIEMBRE</span>
        {{ unpaid.length }} comprobante{{ unpaid.length === 1 ? '' : 's' }} por cobrar
      </small>
    </NuxtLink>
  </section>
</template>

<style scoped>
.stats-grid {
  gap: 12px;
  margin-bottom: 18px;
  max-width: 964px;
}

.stat-card {
  min-width: 0;
  padding: 16px 18px;
}

.stat-card > div:first-child { font-size: 12px; }
.stat-card > strong { font-size: 28px; margin: 10px 0; }
.stat-card > small { font-size: 10px; }
.stat-caption { margin-top: 0; }

.vehicles-stat-card { position: relative; overflow: hidden; }
.vehicles-stat-card .vehicles-stat-logo {
  position: absolute;
  top: 50%;
  right: 16px;
  width: 160px;
  height: 160px;
  transform: translate(50%, -50%);
  opacity: 0.35;
  color: #9ca3af;
  pointer-events: none;
}
.vehicles-stat-card > :not(.vehicles-stat-logo) { position: relative; z-index: 1; }
:global(html.dark .vehicles-stat-logo) { color: #71717a; }

@media (max-width: 600px) {
  .stats-grid { gap: 8px; }
  .stat-card { padding: 12px 10px; }
  .stat-card > strong { font-size: 20px; overflow-wrap: anywhere; }
  .vehicles-stat-card .vehicles-stat-logo { width: 110px; height: 110px; }
}
</style>
