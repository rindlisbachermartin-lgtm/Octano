<script setup lang="ts">
import type { Part } from '~/types'
import { Check, X, Archive, ArchiveRestore } from '@lucide/vue'

import { partSalePrice, partMargin } from '~/utils/partPricing'
import { notificarCambioDashboard } from '~/composables/useDashboardCambios'
import type { RepuestoApi } from '~/utils/repuestoApi'

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

const auth = useAuthApi()
const guardando = ref(false)
const formError = ref('')

function cerrar() {
  if (!guardando.value) emit('close')
}
useModalEscape(() => props.open, cerrar)

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

function numeroInvalido(valor: unknown, entero = false) {
  return typeof valor !== 'number' || !Number.isFinite(valor) || valor < 0
    || (entero && !Number.isInteger(valor))
}

const formatos = computed(() => ({
  cost: numeroInvalido(form.value.cost),
  margin: numeroInvalido(form.value.margin),
  price: numeroInvalido(form.value.price),
  stock: numeroInvalido(form.value.stock, true),
  min: numeroInvalido(form.value.min),
}))

async function submit() {
  if (guardando.value) return
  formError.value = ''
  if (!form.value.name.trim() || !form.value.brand.trim() || !form.value.oem.trim()) {
    formError.value = 'Por favor completá los campos obligatorios.'
    return
  }
  if (Object.values(formatos.value).some(Boolean)) return
  const descripcion = inicialMayuscula(form.value.name)
  const marca = inicialMayuscula(form.value.brand)

  const headers = auth.authorization.value
  if (!headers) return
  guardando.value = true
  try {
    const f = form.value
    const id = props.part?.id
    const respuesta = await $fetch<RepuestoApi>(id ? `/api/repuestos/${id}` : '/api/repuestos', {
      method: id ? 'PUT' : 'POST', headers,
      body: { codigoOem: f.oem.trim(), descripcion, marca,
        precioCosto: f.cost, margen: f.margin, stockMinimo: f.min },
    })
    notificarCambioDashboard()
    if (id) emit('updated', adaptarRepuesto(respuesta))
    else emit('created', adaptarRepuesto(respuesta))
  } catch (err) {
    formError.value = authError(err, 'No pudimos guardar el repuesto.')
  } finally {
    guardando.value = false
  }

}
</script>

<template>
  <CommonFormPage v-if="open">
    <div class="dialog-header">
      <h2>{{ part ? 'Editar repuesto' : 'Nuevo repuesto' }}</h2>
      <button class="icon-button" aria-label="Cerrar" :disabled="guardando" @click="cerrar">
        <X :size="18" />
      </button>
    </div>

    <form @submit.prevent="submit" class="entry-form" novalidate>
      <div class="form-fields">
        <label>
          Descripción
          <input
            v-model="form.name"
            @blur="form.name = inicialMayuscula(form.name)"
            maxlength="250"
            required
            placeholder="Filtro de aceite"
          />
        </label>

        <div class="form-grid">
          <label>
            Marca
            <input v-model="form.brand" @blur="form.brand = inicialMayuscula(form.brand)" required maxlength="80" placeholder="MANN-FILTER" />
          </label>
          <label>
            Código OEM
            <input v-model="form.oem" required maxlength="80" placeholder="W 712/95" />
          </label>
          <label>
            Costo de compra ($)
            <input
              v-model.number="form.cost"
              @input="updateSalePrice"
              type="number"
              min="0"
              step="0.01"
              required :aria-invalid="formatos.cost"
            />
            <CommonFormatoInvalido v-if="formatos.cost" />
          </label>
          <label>
            Margen (%)
            <input v-model.number="form.margin" @input="updateSalePrice" type="number" min="0" step="any" required :aria-invalid="formatos.margin" />
            <CommonFormatoInvalido v-if="formatos.margin" />
          </label>
          <label>
            Precio de venta ($)
            <input
              v-model.number="form.price"
              @input="updateMargin"
              type="number"
              min="0"
              step="0.01"
              required :aria-invalid="formatos.price"
            />
            <CommonFormatoInvalido v-if="formatos.price" />
          </label>

          <label>
            Stock mínimo
            <input
              v-model.number="form.min"
              :step="'0.001'"
              type="number"
              min="0"
              required :aria-invalid="formatos.min"
            />
            <CommonFormatoInvalido v-if="formatos.min" />
          </label>
        </div>

        <p class="muted">Precio de venta = costo + (costo × margen / 100).</p>
        <p  class="muted">Registrá las entradas, salidas y ajustes desde Movimientos.</p>

        <p v-if="formError" class="error-message" role="alert">
          {{ formError }}
        </p>
      </div>

      <footer class="modal-footer">
        <button v-if="part" type="button" class="text-button" :class="{ danger: !part.archived }" style="margin-right: auto" :disabled="guardando" @click="emit('delete', part)">
          <ArchiveRestore v-if="part.archived" :size="16" />
          <Archive v-else :size="16" />
          {{ part.archived ? 'Reactivar repuesto' : 'Archivar repuesto' }}
        </button>
        <button type="button" class="button" :disabled="guardando" @click="cerrar">Cancelar</button>
        <button type="submit" class="button primary" :disabled="guardando">
          <Check :size="16" />Guardar repuesto
        </button>
      </footer>
    </form>
  </CommonFormPage>
</template>
