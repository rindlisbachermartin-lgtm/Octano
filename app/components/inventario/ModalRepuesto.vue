<script setup lang="ts">
import { Check, X } from 'lucide-vue-next'
import type { Part } from '~/types'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created', part: Part): void
}>()

const { db } = useDatabase()
const { matches } = useHelpers()
const vehicleSearch = ref('')
const debouncedSearch = useDebouncedValue(vehicleSearch)
const compatibleCandidates = computed(() => debouncedSearch.value.trim()
  ? db.value.vehicles.filter((v) => matches(debouncedSearch.value, v.plate, v.brand, v.model, v.year, v.engine))
  : [])
const formError = ref('')

useModalEscape(() => props.open, () => emit('close'))

const form = ref({
  name: '',
  brand: '',
  oem: '',
  cost: 0,
  price: 0,
  stock: 0,
  min: 2,
  compatible: [] as number[],
})

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      vehicleSearch.value = ''
      form.value = {
        name: '',
        brand: '',
        oem: '',
        cost: 0,
        price: 0,
        stock: 0,
        min: 2,
        compatible: [],
      }
      formError.value = ''
    }
  }
)

function submit() {
  formError.value = ''
  if (!form.value.name || !form.value.brand || !form.value.oem) {
    formError.value = 'Por favor completá los campos obligatorios.'
    return
  }

  const newPart: Part = {
    id: Date.now(),
    name: form.value.name,
    brand: form.value.brand,
    oem: form.value.oem,
    cost: Number(form.value.cost),
    price: Number(form.value.price),
    stock: Number(form.value.stock),
    min: Number(form.value.min),
    compatible: form.value.compatible.map(Number),
  }

  db.value.parts.push(newPart)
  emit('created', newPart)
}
</script>

<template>
  <CommonFormPage v-if="open">
    <div class="dialog-header">
      <h2>Nuevo repuesto</h2>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <form @submit.prevent="submit" class="entry-form">
      <div class="form-fields">
        <label>
          Descripción
          <input
            v-model="form.name"
            required
            placeholder="Filtro de aceite"
          />
        </label>

        <div class="form-grid">
          <label>
            Marca
            <input v-model="form.brand" required placeholder="MANN-FILTER" />
          </label>
          <label>
            Código OEM
            <input v-model="form.oem" required placeholder="W 712/95" />
          </label>
          <label>
            Costo de compra ($)
            <input
              v-model.number="form.cost"
              type="number"
              min="0"
              required
            />
          </label>
          <label>
            Precio de venta ($)
            <input
              v-model.number="form.price"
              type="number"
              min="0"
              required
            />
          </label>
          <label>
            Stock inicial
            <input
              v-model.number="form.stock"
              type="number"
              min="0"
              required
            />
          </label>
          <label>
            Stock mínimo
            <input
              v-model.number="form.min"
              type="number"
              min="0"
              required
            />
          </label>
        </div>

        <fieldset>
          <legend>Compatibilidad con vehículos</legend>
          <label>
            Buscar vehículo compatible
            <input v-model="vehicleSearch" placeholder="Patente, marca o modelo…" />
          </label>
          <p v-if="!vehicleSearch.trim()" class="muted search-hint">Empezá a escribir para buscar vehículos.</p>
          <p v-else-if="!compatibleCandidates.length && vehicleSearch.trim() === debouncedSearch.trim()" class="muted">No se encontraron vehículos con esa búsqueda.</p>
          <label v-for="v in compatibleCandidates" :key="v.id" class="task-row">
            <input
              type="checkbox"
              v-model="form.compatible"
              :value="v.id"
            />
            {{ v.brand }} {{ v.model }} · {{ v.year }} · {{ v.engine }}
          </label>
          <p v-if="form.compatible.length" class="muted">{{ form.compatible.length }} vehículos seleccionados.</p>
        </fieldset>

        <p v-if="formError" class="error-message" role="alert">
          {{ formError }}
        </p>
      </div>

      <footer class="modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary">
          <Check :size="16" />Guardar repuesto
        </button>
      </footer>
    </form>
  </CommonFormPage>
</template>
