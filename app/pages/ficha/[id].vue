<script setup lang="ts">
import { CircleCheck, ArrowLeft } from 'lucide-vue-next'

definePageMeta({
  layout: 'blank'
})

const route = useRoute()
const { db, vehicle, vehicleName } = useDatabase()

const vehicleId = computed(() => Number(route.params.id))
const currentVehicle = computed(() => vehicle(vehicleId.value))

const finishedOrders = computed(() =>
  db.value.orders.filter(
    (o) => o.vehicle === vehicleId.value && o.status === 'Finalizado'
  )
)
</script>

<template>
  <div class="public-page">
    <NuxtLink to="/" class="brand">
      <span class="brand-symbol">o<span>·</span></span>
      <span>octa<span class="brand-light">no</span></span>
    </NuxtLink>

    <template v-if="currentVehicle?.id">
      <span class="eyebrow">FICHA DIGITAL · DEMOSTRACIÓN</span>
      <h1>{{ vehicleName(vehicleId) }}</h1>
      <span class="plate">{{ currentVehicle.plate }}</span>

      <p class="vehicle-specs">
        {{ currentVehicle.year }} · {{ currentVehicle.engine }} ·
        {{ currentVehicle.km?.toLocaleString('es-AR') }} km
      </p>

      <h2>Historial de servicios</h2>

      <article
        v-for="o in finishedOrders"
        :key="o.id"
        class="public-service"
      >
        <CircleCheck :size="24" />
        <div>
          <small>{{ o.date }} · Octano</small>
          <h3>{{ o.service }}</h3>
          <p v-for="task in o.tasks" :key="task.name">{{ task.name }}</p>
          <p v-for="part in o.parts" :key="part.id">
            Repuesto: {{ part.name }}
          </p>
        </div>
      </article>

      <p v-if="!finishedOrders.length">
        Todavía no hay servicios finalizados para este vehículo.
      </p>

      <p class="muted">
        Vista de demostración. Los datos se guardan en este navegador.
      </p>

      <NuxtLink to="/vehiculos" class="button outlined" style="margin-top: 2rem">
        <ArrowLeft :size="16" /> Volver al taller
      </NuxtLink>
    </template>

    <template v-else>
      <h1>Ficha no disponible</h1>
      <p>Este vehículo no está registrado en este navegador de demostración.</p>
      <NuxtLink to="/" class="button primary" style="margin-top: 1.5rem">
        Ir al taller
      </NuxtLink>
    </template>
  </div>
</template>
