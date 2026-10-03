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
  vatCondition: 'Consumidor Final',
})

watch(
  () => [props.open, props.client] as const,
  ([isOpen, c]) => {
    if (!isOpen) return
    if (c) {
      form.value = { ...c, vatCondition: c.vatCondition || 'Consumidor Final' }
    } else {
      form.value = {
        name: '',
        doc: '',
        phone: '',
        email: '',
        active: true,
        vatCondition: 'Consumidor Final',
      }
    }
    formError.value = ''
  },
  { immediate: true }
)

function submit() {
  formError.value = ''
  const f = form.value
  const name = f.name?.trim() || ''
  const doc = f.doc?.trim() || ''
  const phone = f.phone?.trim() || ''
  const email = f.email?.trim() || ''
  if (!name || !doc || !phone) {
    formError.value = 'Por favor completá los campos obligatorios.'
    return
  }
  if (name.length < 2 || name.length > 90) {
    formError.value = 'El nombre debe tener entre 2 y 90 caracteres.'
    return
  }
  const docDigits = doc.replace(/\D/g, '')
  if (![7, 8, 11].includes(docDigits.length)) {
    formError.value = 'Ingresá un DNI (7 u 8 números) o CUIT/CUIL (11 números).'
    return
  }
  if (!VAT_CONDITIONS.includes(f.vatCondition!) || (requiresCuit(f.vatCondition!) && docDigits.length !== 11)) {
    formError.value = 'Seleccioná la condición de IVA e ingresá un CUIT de 11 números para esta condición.'
    return
  }
  const digits = phone.replace(/\D/g, '')
  if (digits.length < 10 || digits.length > 15) {
    formError.value = 'El teléfono debe tener entre 10 y 15 números, con código de área.'
    return
  }
  if (email && (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))) {
    formError.value = 'Ingresá un correo válido de hasta 254 caracteres.'
    return
  }

  // Check duplicate doc
  const duplicate = db.value.clients.some(
    (c) => c.id !== props.client?.id && c.doc.replace(/\D/g, '') === docDigits
  )
  if (duplicate) {
    formError.value = 'Ya existe un cliente con este documento.'
    return
  }

  emit('save', { ...f, name, doc, phone, email })
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
            minlength="2"
            maxlength="90"
            autocomplete="name"
          />
          <small class="muted">Entre 2 y 90 caracteres.</small>
        </label>

        <label>
          DNI / CUIT / CUIL *
          <input
            :value="form.doc"
            @input="form.doc = formatIdentityDocumentInput($event, form.doc)"
            inputmode="numeric"
            aria-label="DNI / CUIT / CUIL"
            required
            maxlength="13"
          />
          <small class="muted">DNI: 7 u 8 números. CUIT / CUIL: 11 números.</small>
        </label>

        <label>
          Condición de IVA *
          <select v-model="form.vatCondition" aria-label="Condición de IVA" required>
            <option v-for="condition in VAT_CONDITIONS" :key="condition" :value="condition">{{ condition }}</option>
          </select>
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
            />
            <small class="muted">Entre 10 y 15 números, con código de área.</small>
          </label>
          <label>
            Correo electrónico
            <input
              v-model="form.email"
              type="email"
              maxlength="254"
              autocomplete="email"
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
