<script setup lang="ts">
import { CalendarDays, Plus, ArrowRight } from 'lucide-vue-next'

const { db, vehicleName, owner } = useDatabase()
const { statusClass } = useHelpers()

const emit = defineEmits<{
  (e: 'new-appointment'): void
}>()

const selectedDate = ref('2026-09-07')

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

    <div class="week-strip">
      <button
        v-for="(day, index) in ['L', 'M', 'M', 'J', 'V', 'S', 'D']"
        :key="index"
        :class="{
          current: selectedDate === `2026-09-${String(7 + index).padStart(2, '0')}`,
        }"
        @click="selectedDate = `2026-09-${String(7 + index).padStart(2, '0')}`"
      >
        <span>{{ day }}</span>
        <strong>{{ 7 + index }}</strong>
        <i></i>
      </button>
    </div>

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
