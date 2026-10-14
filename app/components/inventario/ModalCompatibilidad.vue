<script setup lang="ts">
import { Check, Plus, Trash2, X } from 'lucide-vue-next'
import type { Part } from '~/types'

const props = defineProps<{ part: Part }>()
const emit = defineEmits<{
  (e: 'close'): void
  (e: 'updated', part: Part): void
}>()

const { db } = useDatabase()
const brand = ref('')
const model = ref('')
const version = ref('')
const error = ref('')
const selectedModels = ref(migratePartCompatibility(props.part.compatible, []))
const catalog = computed(() => [
  ...db.value.vehicleCatalog,
  ...db.value.parts.flatMap(part => part.compatible.map(entry => ({ ...entry, engine: '' }))),
  ...selectedModels.value.map(entry => ({ ...entry, engine: '' })),
])
const brands = computed(() => vehicleCatalogOptions(catalog.value, 'brand'))
const models = computed(() => vehicleCatalogOptions(catalog.value, 'model', brand.value))
const versions = computed(() => vehicleCatalogOptions(catalog.value, 'version', brand.value, model.value))
watch(brand, () => { model.value = ''; version.value = '' })
watch(model, () => { version.value = '' })

function closeSearchMenu(event: KeyboardEvent) {
  event.stopPropagation()
}

function add() {
  const entry = { brand: brand.value.trim().replace(/\s+/g, ' '), model: model.value.trim().replace(/\s+/g, ' '), version: version.value.trim().replace(/\s+/g, ' ') }
  if (!entry.brand || !entry.model || !entry.version) { error.value = 'Completá la marca, el modelo y la versión.'; return }
  if (selectedModels.value.some(other => matchesPartCompatibility(other, entry.brand, entry.model) && catalogKey(other.version || '') === catalogKey(entry.version))) {
    error.value = 'Esta marca, modelo y versión ya están agregados.'
    return
  }
  selectedModels.value.push(entry)
  brand.value = ''
  model.value = ''
  version.value = ''
  error.value = ''
}

useModalEscape(() => true, () => emit('close'))

function save() {
  if (brand.value.trim() || model.value.trim() || version.value.trim()) {
    add()
    if (error.value) return
  }
  props.part.compatible = selectedModels.value.map(entry => ({ ...entry }))
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
        <p class="muted">Agregá las marcas, modelos y versiones compatibles, aunque todavía no haya vehículos registrados.</p>
        <label>
          Marca
          <USelectMenu v-model="brand" :items="brands" :search-input="{ placeholder: 'Buscar marca…' }" :content="{ onEscapeKeyDown: closeSearchMenu }" placeholder="Seleccionar marca…" aria-label="Marca" class="compatibility-select" />
        </label>
        <label>
          Modelo
          <USelectMenu v-model="model" :items="models" :search-input="{ placeholder: 'Buscar modelo…' }" :content="{ onEscapeKeyDown: closeSearchMenu }" :placeholder="brand ? 'Seleccionar modelo…' : 'Primero seleccioná una marca'" :disabled="!brand" aria-label="Modelo" class="compatibility-select" />
        </label>
        <label>
          Versión
          <USelectMenu v-model="version" :items="versions" :search-input="{ placeholder: 'Buscar versión…' }" :content="{ onEscapeKeyDown: closeSearchMenu }" :placeholder="model ? 'Seleccionar versión…' : 'Primero seleccioná un modelo'" :disabled="!model" aria-label="Versión" class="compatibility-select" />
        </label>
        <button type="button" class="button" @click="add"><Plus :size="16" /> Agregar compatibilidad</button>
        <p v-if="error" role="alert">{{ error }}</p>
        <fieldset>
          <legend>Marcas, modelos y versiones compatibles ({{ selectedModels.length }})</legend>
          <p v-if="!selectedModels.length" class="muted">Sin restricciones de marca, modelo y versión.</p>
          <div v-for="(entry, index) in selectedModels" :key="index" class="task-row">
            <span>{{ entry.brand && entry.model ? `${entry.brand} · ${entry.model} · ${entry.version || 'Todas las versiones'}` : 'Compatibilidad anterior sin datos: quitá esta entrada y agregá la marca, el modelo y la versión.' }}</span>
            <button type="button" class="icon-button" :aria-label="`Quitar compatibilidad ${entry.brand} ${entry.model} ${entry.version || ''}`" @click="selectedModels.splice(index, 1)"><Trash2 :size="16" /></button>
          </div>
        </fieldset>
      </div>
      <footer class="modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary"><Check :size="16" /> Guardar compatibilidad</button>
      </footer>
    </form>
  </CommonFormPage>
</template>

<style scoped>
.compatibility-select { width: 100%; min-height: 38px; }
.task-row > span { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.task-row > .icon-button { flex-shrink: 0; }
</style>
