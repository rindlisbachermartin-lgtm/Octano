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
    <NuxtLink to="/ordenes" class="stat-card">
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
