<script setup lang="ts">
import { Check, X } from 'lucide-vue-next'
import type { Part } from '~/types'

const props = defineProps<{ part: Part }>()
const emit = defineEmits<{
  (e: 'close'): void
  (e: 'updated', part: Part): void
}>()

const { db } = useDatabase()
const { matches } = useHelpers()
const search = ref('')
const debouncedSearch = useDebouncedValue(search)
const selectedVehicles = ref([...props.part.compatible])
const assignedVehicles = computed(() => db.value.vehicles.filter(vehicle => selectedVehicles.value.includes(vehicle.id)))
const candidates = computed(() => debouncedSearch.value.trim()
  ? db.value.vehicles.filter(vehicle => matches(debouncedSearch.value, vehicle.plate, vehicle.brand, vehicle.model, vehicle.year, vehicle.engine))
  : [])

useModalEscape(() => true, () => emit('close'))

function save() {
  props.part.compatible = [...selectedVehicles.value]
  emit('updated', props.part)
}
</script>

<template>
  <CommonFormPage>
    <div class="dialog-header">
      <h2>Compatibilidad del repuesto</h2>
      <button type="button" class="icon-button" aria-label="Cerrar" @click="emit('close')"><X :size="18" /></button>
    </div>
    <form class="entry-form" @submit.prevent="save">
      <div class="form-fields">
        <p><strong>{{ part.name }}</strong> · {{ part.brand }} · {{ part.oem }}</p>
        <label>
          Buscar vehículo compatible
          <input v-model="search" placeholder="Patente, marca o modelo…" />
        </label>
        <p v-if="!search.trim()" class="muted">Empezá a escribir para buscar vehículos.</p>
        <p v-else-if="!candidates.length && search.trim() === debouncedSearch.trim()" class="muted">No se encontraron vehículos con esa búsqueda.</p>
        <label v-for="vehicle in candidates" :key="vehicle.id" class="task-row">
          <input v-model="selectedVehicles" type="checkbox" :value="vehicle.id" />
          {{ vehicle.plate }} · {{ vehicle.brand }} {{ vehicle.model }} · {{ vehicle.year }} · {{ vehicle.engine }}
        </label>
        <fieldset>
          <legend>Vehículos compatibles ({{ selectedVehicles.length }})</legend>
          <p v-if="!selectedVehicles.length" class="muted">No hay vehículos asociados.</p>
          <label v-for="vehicle in assignedVehicles" :key="vehicle.id" class="task-row">
            <input v-model="selectedVehicles" type="checkbox" :value="vehicle.id" />
            {{ vehicle.plate }} · {{ vehicle.brand }} {{ vehicle.model }} · {{ vehicle.year }} · {{ vehicle.engine }}
          </label>
        </fieldset>
      </div>
      <footer class="modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary"><Check :size="16" /> Guardar compatibilidad</button>
      </footer>
    </form>
  </CommonFormPage>
</template>
