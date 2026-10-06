<script setup lang="ts">
import { CalendarDays, Plus, ArrowRight } from 'lucide-vue-next'

const { db, vehicleName, owner } = useDatabase()
const { statusClass } = useHelpers()

const emit = defineEmits<{
  (e: 'new-appointment'): void
}>()

const { today, label } = useWorkshopDay()

const dailyAppointments = computed(() =>
  db.value.appointments
    .filter((a) => a.date === today.value)
    .sort((a, b) => a.time.localeCompare(b.time))
)
</script>

<template>
  <section class="panel agenda-panel">
    <div class="panel-top">
      <div>
        <h2>Turnos de hoy <span class="count-bubble">{{ dailyAppointments.length }}</span></h2>
        <p class="muted">{{ label }}</p>
      </div>
      <button
        class="icon-button outlined"
        aria-label="Agendar turno"
        @click="emit('new-appointment')"
      >
        <Plus :size="17" />
      </button>
    </div>


    <div class="agenda-items">
      <article v-for="a in dailyAppointments" :key="a.id">
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
      Ver turnos <ArrowRight :size="16" />
    </NuxtLink>
  </section>
</template>

<style scoped>
.panel-top .muted {
  margin-top: 6px;
  font-size: 11px;
}
</style>
