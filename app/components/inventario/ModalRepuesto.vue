<script setup lang="ts">
import { Check, X, Trash2 } from 'lucide-vue-next'
import type { Part } from '~/types'
import { partSalePrice, partMargin } from '~/utils/partPricing'

const props = defineProps<{
  open: boolean
  part?: Part | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created', part: Part): void
  (e: 'updated', part: Part): void
  (e: 'delete', part: Part): void
}>()

const { db } = useDatabase()
const formError = ref('')

useModalEscape(() => props.open, () => emit('close'))

const form = ref({
  name: '',
  brand: '',
  oem: '',
  cost: 0,
  margin: 0,
  price: 0,
  stock: 0,
  min: 2,
})

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      form.value = props.part ? { ...props.part, margin: Number((props.part.margin ?? partMargin(props.part.cost, props.part.price)).toFixed(2)) } : {
        name: '',
        brand: '',
        oem: '',
        cost: 0,
        margin: 0,
        price: 0,
        stock: 0,
        min: 2,
      }
      formError.value = ''
    }
  }
)

function updateSalePrice() {
  form.value.price = partSalePrice(form.value.cost, form.value.margin)
}

function updateMargin() {
  form.value.margin = Number(partMargin(form.value.cost, form.value.price).toFixed(2))
}

function submit() {
  formError.value = ''
  if (!form.value.name.trim() || !form.value.brand.trim() || !form.value.oem.trim()) {
    formError.value = 'Por favor completá los campos obligatorios.'
    return
  }
  if (![form.value.stock, form.value.min].every(value => typeof value === 'number' && Number.isInteger(value) && value >= 0)) {
    formError.value = 'El stock y el mínimo deben ser números enteros mayores o iguales a cero.'
    return
  }
  if (![form.value.cost, form.value.price, form.value.margin].every(value => typeof value === 'number' && Number.isFinite(value) && value >= 0)) {
    formError.value = 'Ingresá un costo, margen y precio válidos, mayores o iguales a cero.'
    return
  }

  const newPart: Part = {
    id: props.part?.id ?? Date.now(),
    name: form.value.name.trim(),
    brand: form.value.brand.trim(),
    oem: form.value.oem.trim(),
    cost: Number(form.value.cost),
    margin: Number(form.value.margin),
    price: Number(form.value.price),
    stock: Number(form.value.stock),
    min: Number(form.value.min),
    compatible: [...(props.part?.compatible ?? [])],
  }

  if (props.part) {
    Object.assign(props.part, newPart)
    emit('updated', props.part)
    return
  }
  db.value.parts.push(newPart)
  emit('created', newPart)
}
</script>

<template>
  <CommonFormPage v-if="open">
    <div class="dialog-header">
      <h2>{{ part ? 'Editar repuesto' : 'Nuevo repuesto' }}</h2>
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
              @input="updateSalePrice"
              type="number"
              min="0"
              step="0.01"
              required
            />
          </label>
          <label>
            Margen (%)
            <input v-model.number="form.margin" @input="updateSalePrice" type="number" min="0" step="any" required />
          </label>
          <label>
            Precio de venta ($)
            <input
              v-model.number="form.price"
              @input="updateMargin"
              type="number"
              min="0"
              step="0.01"
              required
            />
          </label>
          <label>
            {{ part ? 'Stock actual' : 'Stock inicial' }}
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

        <p class="muted">Precio de venta = costo + (costo × margen / 100).</p>

        <p v-if="formError" class="error-message" role="alert">
          {{ formError }}
        </p>
      </div>

      <footer class="modal-footer">
        <button v-if="part" type="button" class="text-button danger" style="margin-right: auto" @click="emit('delete', part)"><Trash2 :size="16" /> Eliminar repuesto</button>
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary">
          <Check :size="16" />Guardar repuesto
        </button>
      </footer>
    </form>
  </CommonFormPage>
</template>
