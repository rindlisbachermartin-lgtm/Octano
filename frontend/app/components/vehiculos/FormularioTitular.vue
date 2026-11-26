<script setup lang="ts">
import type { Vehicle } from '~/types'
import type { Client } from '~/types';
const props = defineProps<{ vehicle: Vehicle }>()
const emit = defineEmits<{ close: []; saved: [] }>()

const search = ref('')
const debouncedSearch = useDebouncedValue(search)

const auth = useAuthApi()
const busqueda = useBusquedaTitulares(debouncedSearch, () => true)
const guardando = ref(false)
const titularElegido = ref<Client | null>(null)
const candidates = computed(() => busqueda.resultados.value.filter(c => c.id !== props.vehicle.client))
const selectedOwner = computed(() => titularElegido.value)
const error = ref('')
function close() {
  if (!guardando.value) emit('close')
}

async function submit() {
  if (guardando.value) return
  const next = selectedOwner.value
  if (!next?.active || next.id === props.vehicle.client) {
    error.value = 'Seleccioná un titular activo diferente al actual.'
    return
  }

  const headers = auth.authorization.value
  if (!headers) return
  guardando.value = true
  error.value = ''
  try {
    await $fetch(`/api/vehiculos/${props.vehicle.id}/titular`, { method: 'PATCH', headers, body: { clienteId: next.id } })
    emit('saved')
  } catch (err) {
    error.value = authError(err, 'No pudimos cambiar el titular.')
  } finally {
    guardando.value = false
  }

}
useModalEscape(() => true, close)
</script>

<template>
  <CommonFormPage>
    <div class="dialog-header">
      <h2>Cambiar titular</h2>
      <button class="button" :disabled="guardando" @click="close">Volver al listado</button>
    </div>
    <form class="entry-form" novalidate @submit.prevent="submit">
      <div class="form-fields">
        <strong>{{ vehicle.plate }} · {{ vehicleLabel(vehicle.brand, vehicle.model, vehicle.version) }}</strong>
        <p>Titular actual: {{ vehicle.registro?.titular || 'Sin asignar' }}</p>
        <label>
          Buscar nuevo titular *
          <input v-model="search" maxlength="90" :disabled="guardando" placeholder="Nombre, documento o teléfono…" @input="titularElegido = null; error = ''" />
        </label>
        <p v-if="!search.trim()" class="muted search-hint">Empezá a escribir para buscar clientes.</p>
        <div v-else-if="!selectedOwner" class="selection-results">
          <button v-for="c in candidates" :key="c.id" type="button" class="button" @click="titularElegido = c; error = ''">{{ c.name }} · {{ c.doc }}</button>
          <p v-if="busqueda.error.value" role="alert" class="error-message">{{ busqueda.error.value }}</p>
          <p v-else-if="busqueda.cargando.value" role="status" class="muted">Buscando clientes…</p>
          <p v-else-if="!candidates.length && search.trim() === debouncedSearch.trim()" class="muted">No se encontraron clientes con esa búsqueda.</p>
        </div>
        <p v-if="selectedOwner"><strong>Nuevo titular: {{ selectedOwner.name }}</strong></p>
        <p class="muted">El vehículo conservará su historial de servicios, órdenes y código QR. El cambio queda registrado.</p>
        <p v-if="error" class="error-message" role="alert">{{ error }}</p>
      </div>
      <footer class="modal-footer">
        <button type="button" class="button" :disabled="guardando" @click="close">Cancelar</button>
        <button type="submit" class="button primary" :disabled="guardando">{{ guardando ? 'Guardando…' : 'Confirmar cambio de titular' }}</button>
      </footer>
    </form>
  </CommonFormPage>
</template>
