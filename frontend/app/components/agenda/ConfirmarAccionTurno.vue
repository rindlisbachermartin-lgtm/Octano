<script setup lang="ts">
import type { Appointment } from '~/types'

const props = defineProps<{ appointment: Appointment; action: 'cancelar' | 'ingreso' }>()
const emit = defineEmits<{ close: []; confirm: [] }>()
const { vehicle, vehicleName } = useDatabase()
const { today } = useWorkshopDay()
const isFuture = computed(() => props.appointment.date > today.value)
const titleId = useId()
const descriptionId = useId()
const title = computed(() => props.action === 'cancelar'
  ? (isFuture.value ? '¿Cancelar un turno futuro?' : '¿Cancelar este turno?')
  : '¿Registrar ingreso antes de la fecha del turno?')
const scheduledDate = computed(() => new Intl.DateTimeFormat('es-AR', {
  day: 'numeric', month: 'long', year: 'numeric',
}).format(new Date(`${props.appointment.date}T12:00:00`)))
</script>

<template>
  <CommonModalDialog class="dialog" role="alertdialog" :aria-labelledby="titleId" :aria-describedby="descriptionId" @close="emit('close')">
    <div class="dialog-header"><h2 :id="titleId">{{ title }}</h2></div>
    <div class="confirmation-content">
      <p :id="descriptionId">El turno de {{ vehicleName(appointment.vehicle) }} ({{ vehicle(appointment.vehicle)?.plate }}) está programado para el {{ scheduledDate }} a las {{ appointment.time }} hs.</p>
      <p v-if="isFuture" class="future-warning" role="status">La fecha del turno es posterior a la actual. {{ action === 'ingreso' ? 'El vehículo todavía no debería ingresar. Si confirmás, se registrará su ingreso hoy y se habilitará la orden al mecánico.' : 'Si confirmás, se cancelará el turno y se dará de baja su orden pendiente de ingreso.' }}</p>
      <p class="muted">{{ appointment.reason }}</p>
    </div>
    <footer class="dialog-footer">
      <button type="button" class="button" autofocus @click="emit('close')">{{ action === 'cancelar' ? 'Mantener turno' : 'Volver' }}</button>
      <button type="button" :class="['button', action === 'cancelar' ? 'danger' : 'primary']" @click="emit('confirm')">{{ action === 'cancelar' ? (isFuture ? 'Cancelar igualmente' : 'Confirmar cancelación') : 'Registrar ingreso igualmente' }}</button>
    </footer>
  </CommonModalDialog>
</template>

<style scoped>
.confirmation-content { padding: 0 24px 20px; font-size: 13px; }
.future-warning { padding: 12px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); }
</style>
