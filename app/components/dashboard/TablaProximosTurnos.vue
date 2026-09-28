<script setup lang="ts">
import { CalendarDate } from '@internationalized/date'
import { CalendarDays, Plus, ArrowRight } from 'lucide-vue-next'

const { db, vehicleName, owner } = useDatabase()
const { statusClass } = useHelpers()

const emit = defineEmits<{
  (e: 'new-appointment'): void
}>()

const calendarDate = shallowRef(new CalendarDate(2026, 9, 7))
const selectedDate = computed(() => calendarDate.value.toString())

const dailyAppointments = computed(() =>
  db.value.appointments
    .filter((a) => a.date === selectedDate.value)
    .sort((a, b) => a.time.localeCompare(b.time))
)
</script>

<template>
  <section class="panel agenda-panel">
    <div class="panel-top">
      <div>
        <h2>En la agenda</h2>
      </div>
      <button
        class="icon-button outlined"
        aria-label="Agendar turno"
        @click="emit('new-appointment')"
      >
        <Plus :size="17" />
      </button>
    </div>

    <UCalendar v-model="calendarDate" class="octano-calendar dashboard-calendar" color="primary" locale="es-AR" :week-starts-on="0" weekday-format="narrow" :view-control="false" :year-controls="false" />

    <div class="agenda-items">
      <article v-for="a in dailyAppointments.slice(0, 3)" :key="a.id">
        <time>{{ a.time }}<small>HS</small></time>
        <div>
          <strong>{{ vehicleName(a.vehicle) }}</strong>
          <p>{{ a.reason }}</p>
          <small>{{ owner(a.vehicle).name }}</small>
          <span :class="['badge', statusClass(a.status)]"><i></i>{{ a.status }}</span>
        </div>
      </article>
      <div v-if="!dailyAppointments.length" class="small-empty">
        <CalendarDays :size="25" />
        <p>No hay turnos programados para este día.</p>
      </div>
    </div>

    <NuxtLink to="/agenda" class="agenda-link">
      Abrir agenda completa <ArrowRight :size="16" />
    </NuxtLink>
  </section>
</template>

<style scoped>
.dashboard-calendar { margin: 0 auto 14px; }
@media (min-width: 761px) and (max-width: 1020px) {
  .dashboard-calendar { grid-column: 1; grid-row: 2; }
}
</style>
