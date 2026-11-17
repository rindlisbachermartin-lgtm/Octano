<script setup lang="ts">
import type { Client, Vehicle } from '~/types'
const props = defineProps<{ vehicle: Vehicle }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const { db, client } = useDatabase()
const newOwner = ref('')
const search = ref('')
const debouncedSearch = useDebouncedValue(search)
const { matches } = useHelpers()
const auth = useAuthApi()
const conectado = !!props.vehicle.registro
const busqueda = useBusquedaTitulares(debouncedSearch, () => conectado)
const guardando = ref(false)
const titularElegido = ref<Client | null>(null)
const candidates = computed(() => conectado
  ? busqueda.resultados.value.filter(c => c.id !== props.vehicle.client)
  : debouncedSearch.value.trim()
  ? db.value.clients.filter((c) => c.active && c.id !== props.vehicle.client && matches(debouncedSearch.value, c.name, c.doc, c.phone, c.email))
  : [])
const selectedOwner = computed(() => conectado ? titularElegido.value : db.value.clients.find((c) => c.id === Number(newOwner.value)))
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
  if (conectado) {
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
    return
  }
  props.vehicle.ownershipHistory ??= []
  props.vehicle.ownershipHistory.push({ from: props.vehicle.client, to: next.id, date: new Date().toISOString() })
  props.vehicle.client = next.id
  emit('saved')
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
        <strong>{{ vehicle.plate }} · {{ vehicle.brand }} {{ vehicle.model }}</strong>
        <p>Titular actual: {{ vehicle.registro?.titular || client(vehicle.client)?.name || 'Sin asignar' }}</p>
        <label>
          Buscar nuevo titular *
          <input v-model="search" maxlength="90" :disabled="guardando" placeholder="Nombre, documento o teléfono…" @input="newOwner = ''; titularElegido = null; error = ''" />
        </label>
        <p v-if="!search.trim()" class="muted search-hint">Empezá a escribir para buscar clientes.</p>
        <div v-else-if="!selectedOwner" class="selection-results">
          <button v-for="c in candidates" :key="c.id" type="button" class="button" @click="newOwner = String(c.id); titularElegido = c; error = ''">{{ c.name }} · {{ c.doc }}</button>
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
