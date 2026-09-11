<script setup lang="ts">
import { Check, X } from 'lucide-vue-next'
import type { Client } from '~/types'

const props = defineProps<{
  client?: Client | null
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'save', data: Partial<Client>): void
}>()

const { db } = useDatabase()
const formError = ref('')

useModalEscape(() => props.open, () => emit('close'))

const form = ref<Partial<Client>>({
  name: '',
  doc: '',
  phone: '',
  email: '',
  active: true,
})

watch(
  () => props.client,
  (c) => {
    if (c) {
      form.value = { ...c }
    } else {
      form.value = {
        name: '',
        doc: '',
        phone: '',
        email: '',
        active: true,
      }
    }
    formError.value = ''
  },
  { immediate: true }
)

function submit() {
  formError.value = ''
  const f = form.value
  if (!f.name || !f.doc) {
    formError.value = 'Por favor completá los campos obligatorios.'
    return
  }

  // Check duplicate doc
  const cleanDoc = (f.doc || '').replace(/\D/g, '')
  const duplicate = db.value.clients.some(
    (c) => c.id !== props.client?.id && c.doc.replace(/\D/g, '') === cleanDoc
  )
  if (duplicate) {
    formError.value = 'Ya existe un cliente con este documento.'
    return
  }

  emit('save', form.value)
}
</script>

<template>
  <dialog v-if="open" class="dialog" open>
    <div class="dialog-header">
      <h2>{{ client ? 'Editar cliente' : 'Nuevo cliente' }}</h2>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <form @submit.prevent="submit" class="entry-form">
      <div class="form-fields">
        <label>
          Nombre o razón social
          <input
            v-model="form.name"
            required
            maxlength="90"
            autocomplete="name"
            placeholder="Ej. Lucía Fernández"
          />
        </label>

        <label>
          DNI / CUIT / CUIL
          <input
            v-model="form.doc"
            required
            pattern="[0-9.\- ]{7,15}"
            placeholder="32.456.789"
          />
        </label>

        <div class="form-grid">
          <label>
            Teléfono
            <input
              v-model="form.phone"
              type="tel"
              autocomplete="tel"
              placeholder="2392 45-6789"
            />
          </label>
          <label>
            Correo electrónico
            <input
              v-model="form.email"
              type="email"
              autocomplete="email"
              placeholder="nombre@ejemplo.com"
            />
          </label>
        </div>

        <p v-if="formError" class="error-message" role="alert">
          {{ formError }}
        </p>
      </div>

      <footer class="modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button type="submit" class="button primary">
          <Check :size="16" />Guardar
        </button>
      </footer>
    </form>
  </dialog>
</template>
