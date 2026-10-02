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
  () => [props.open, props.client] as const,
  ([isOpen, c]) => {
    if (!isOpen) return
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
  if (!f.name?.trim() || !f.doc?.trim() || !f.phone?.trim()) {
    formError.value = 'Por favor completá los campos obligatorios.'
    return
  }

  const doc = f.doc.trim()
  if (!/^(?:\d{7,8}|\d{1,2}\.\d{3}\.\d{3}|\d{2}-\d{2}\.\d{3}\.\d{3}-\d)$/.test(doc)) {
    formError.value = 'Ingresá un DNI de 7 u 8 dígitos o un CUIT/CUIL con formato 99-99.999.999-9.'
    return
  }
  const phone = f.phone?.trim() || ''
  const digits = phone.replace(/\D/g, '')
  if (!/^\+?\d[\d ()-]*\d$/.test(phone) || digits.length < 10 || digits.length > 15) {
    formError.value = 'Ingresá un teléfono de 10 a 15 dígitos; podés usar +, espacios, paréntesis y guiones.'
    return
  }
  const email = f.email?.trim() || ''
  if (email && !/^[^\s@]+@[^\s@.]+(?:\.[^\s@.]+)+$/.test(email)) {
    formError.value = 'Ingresá un correo válido, por ejemplo nombre@ejemplo.com.'
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

  emit('save', { ...f, name: f.name.trim(), doc, phone, email })
}
</script>

<template>
  <CommonFormPage v-if="open">
    <div class="dialog-header">
      <h2>{{ client ? 'Editar cliente' : 'Nuevo cliente' }}</h2>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <form @submit.prevent="submit" class="entry-form" novalidate>
      <div class="form-fields">
        <p class="muted">Los campos marcados con * son obligatorios.</p>
        <label>
          Nombre o razón social *
          <input
            v-model="form.name"
            aria-label="Nombre o razón social"
            required
            maxlength="90"
            autocomplete="name"
            placeholder="Ej. Lucía Fernández"
          />
        </label>

        <label>
          DNI / CUIT / CUIL *
          <input
            v-model="form.doc"
            aria-label="DNI / CUIT / CUIL"
            required
            placeholder="32.456.789 o 20-32.456.789-9"
          />
          <small class="muted">DNI: 7 u 8 dígitos. CUIT/CUIL: 99-99.999.999-9.</small>
        </label>

        <div class="form-grid">
          <label>
            Teléfono *
            <input
              v-model="form.phone"
              aria-label="Teléfono"
              type="tel"
              required
              autocomplete="tel"
              placeholder="2392 45-6789"
            />
            <small class="muted">10 a 15 dígitos, con código de área.</small>
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
  </CommonFormPage>
</template>
