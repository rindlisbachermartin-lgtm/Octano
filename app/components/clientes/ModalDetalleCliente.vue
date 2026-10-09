<script setup lang="ts">
import { X, ArrowUpRight, CarFront, Edit, Archive } from 'lucide-vue-next'
import type { Client } from '~/types'

const props = defineProps<{
  client: Client | null
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'edit', client: Client): void
  (e: 'archive', client: Client): void
  (e: 'assign-vehicle', client: Client): void
}>()

const { db } = useDatabase()


const clientVehicles = computed(() => {
  if (!props.client) return []
  return db.value.vehicles.filter((v) => v.client === props.client?.id)
})
</script>

<template>
  <CommonModalDialog v-if="open && client" class="dialog" @close="emit('close')">
    <div class="dialog-header">
      <h2>Ficha del cliente</h2>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <div class="detail-body">
      <div class="client-cell" style="margin-bottom: 1.5rem">
        <div>
          <h3 style="font-size: 1.25rem; margin: 0">{{ client.name }}</h3>
          <p class="muted" style="margin: 0.25rem 0 0">Doc: {{ client.doc }}</p>
          <p class="muted" style="margin: 0.25rem 0 0">{{ client.vatCondition || 'Consumidor Final' }}</p>
        </div>
      </div>

      <div class="form-grid" style="margin-bottom: 1.5rem">
        <div>
          <small class="muted" style="display: block; font-size: 0.75rem; text-transform: uppercase">Teléfono</small>
          <strong>{{ client.phone || 'Sin teléfono' }}</strong>
        </div>
        <div>
          <small class="muted" style="display: block; font-size: 0.75rem; text-transform: uppercase">Correo electrónico</small>
          <strong>{{ client.email || 'Sin correo' }}</strong>
        </div>
      </div>

      <h3 style="font-size: 1rem; margin-bottom: 0.75rem">Vehículos registrados ({{ clientVehicles.length }})</h3>
      <button type="button" class="button" style="margin-bottom: 0.75rem" @click="emit('assign-vehicle', client)"><CarFront :size="16" /> Asignar vehículo</button>
      <div v-if="clientVehicles.length" class="history-row" v-for="v in clientVehicles" :key="v.id" style="margin-bottom: 0.5rem">
        <CarFront :size="18" />
        <div>
          <strong>{{ v.brand }} {{ v.model }}</strong>
          <small>{{ v.plate }} · {{ v.year }} · {{ v.engine }}</small>
        </div>
        <NuxtLink :to="`/vehiculos`" class="icon-button" @click="emit('close')">
          <ArrowUpRight :size="15" />
        </NuxtLink>
      </div>
      <p v-else class="muted">No tiene vehículos asociados.</p>

      <div style="display: flex; gap: 0.75rem; margin-top: 1.5rem; justify-content: space-between">
        <button
          type="button"
          class="text-button danger"
          @click="emit('archive', client)"
        >
          <Archive :size="15" /> Archivar cliente
        </button>
        <button
          type="button"
          class="button primary"
          @click="emit('edit', client)"
        >
          <Edit :size="15" /> Editar datos
        </button>
      </div>
    </div>
  </CommonModalDialog>
</template>
