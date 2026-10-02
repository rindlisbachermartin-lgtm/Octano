<script setup lang="ts">
import type { Vehicle } from '~/types'
const props = defineProps<{ vehicle: Vehicle }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const { db, client } = useDatabase()
const newOwner = ref('')
const search = ref('')
const debouncedSearch = useDebouncedValue(search)
const { matches } = useHelpers()
const candidates = computed(() => debouncedSearch.value.trim()
  ? db.value.clients.filter((c) => c.active && c.id !== props.vehicle.client && matches(debouncedSearch.value, c.name, c.doc, c.phone, c.email))
  : [])
const selectedOwner = computed(() => db.value.clients.find((c) => c.id === Number(newOwner.value)))
const error = ref('')
function submit() {
  const next = db.value.clients.find((c) => c.id === Number(newOwner.value) && c.active)
  if (!next || next.id === props.vehicle.client) {
    error.value = 'Seleccioná un titular activo diferente al actual.'
    return
  }
  props.vehicle.ownershipHistory ??= []
  props.vehicle.ownershipHistory.push({ from: props.vehicle.client, to: next.id, date: new Date().toISOString() })
  props.vehicle.client = next.id
  emit('saved')
}
useModalEscape(() => true, () => emit('close'))
</script>

<template>
  <CommonFormPage>
    <div class="dialog-header">
      <h2>Cambiar titular</h2>
      <button class="button" @click="emit('close')">Volver al listado</button>
    </div>
    <form class="entry-form" novalidate @submit.prevent="submit">
      <div class="form-fields">
        <strong>{{ vehicle.plate }} · {{ vehicle.brand }} {{ vehicle.model }}</strong>
        <p>Titular actual: {{ client(vehicle.client)?.name || 'Sin asignar' }}</p>
        <label>
          Buscar nuevo titular *
          <input v-model="search" placeholder="Nombre, documento o teléfono…" @input="newOwner = ''; error = ''" />
        </label>
        <p v-if="!search.trim()" class="muted search-hint">Empezá a escribir para buscar clientes.</p>
        <div v-else-if="!selectedOwner" class="selection-results">
          <button v-for="c in candidates" :key="c.id" type="button" class="button" @click="newOwner = String(c.id); error = ''">{{ c.name }} · {{ c.doc }}</button>
          <p v-if="!candidates.length && search.trim() === debouncedSearch.trim()" class="muted">No se encontraron clientes con esa búsqueda.</p>
        </div>
        <p v-if="selectedOwner"><strong>Nuevo titular: {{ selectedOwner.name }}</strong></p>
        <p class="muted">El vehículo conservará su historial de servicios, órdenes y código QR. El cambio queda registrado.</p>
        <p v-if="error" class="error-message" role="alert">{{ error }}</p>
      </div>
      <footer class="modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary">Confirmar cambio de titular</button>
      </footer>
    </form>
  </CommonFormPage>
</template>
