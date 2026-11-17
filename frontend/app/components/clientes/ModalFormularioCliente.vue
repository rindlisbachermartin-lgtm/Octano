<script setup lang="ts">
import { Check, X } from '@lucide/vue'
import type { Client, VatCondition } from '~/types'

const props = defineProps<{
  client?: Client | null
  open: boolean
  vatOptions?: VatCondition[]
  busy?: boolean
  error?: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'save', data: Partial<Client>): void
}>()

const { db } = useDatabase()
const formError = ref('')
type Campo = 'name' | 'doc' | 'vatCondition' | 'phone' | 'email' | 'address' | 'streetNumber'
const tocados = reactive(new Set<Campo>())
const enviado = ref(false)
const invalidos = computed<Record<Campo, boolean>>(() => {
  const f = form.value
  const nombre = inicialesNombre(f.name ?? '')
  const documento = (f.doc ?? '').replace(/[ .-]/g, '')
  const telefono = (f.phone ?? '').trim()
  const digitos = telefono.replace(/\D/g, '').length
  const correo = (f.email ?? '').trim()
  const altura = f.streetNumber
  return {
    name: nombre.length < 2 || nombre.length > 90,
    doc: !/^(?:[0-9]{7,8}|[0-9]{11})$/.test(documento) || (documento.length === 11 && !isValidCuit(documento)),
    vatCondition: !f.vatCondition || !(props.vatOptions ?? VAT_CONDITIONS).includes(f.vatCondition),
    phone: digitos < 10 || digitos > 15 || !/^[+0-9() .-]{10,30}$/.test(telefono),
    email: !!correo && (correo.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo)),
    address: (f.address ?? '').trim().length > 150,
    streetNumber: altura != null && String(altura) !== ''
      && (typeof altura !== 'number' || !Number.isInteger(altura) || altura < 0 || altura > 2147483647),
  }
})

function mostrarError(campo: Campo) {
  return invalidos.value[campo] && (enviado.value || tocados.has(campo) || Boolean(form.value[campo]))
}

function close() {
  if (!props.busy) emit('close')
}

useModalEscape(() => props.open, close)

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
    tocados.clear()
    enviado.value = false
  },
  { immediate: true }
)

function submit() {
  if (props.busy) return
  formError.value = ''
  const f = form.value
  const name = inicialesNombre(f.name ?? '')
  const doc = f.doc?.trim() || ''
  const phone = f.phone?.trim() || ''
  const email = f.email?.trim() || ''
  enviado.value = true
  if (Object.values(invalidos.value).some(Boolean)) return
  const docDigits = doc.replace(/\D/g, '')

  // Check duplicate doc
  const duplicate = !props.vatOptions && db.value.clients.some(
    (c) => c.id !== props.client?.id && c.doc.replace(/\D/g, '') === docDigits
  )
  if (duplicate) {
    formError.value = 'Ya existe un cliente con este documento.'
    return
  }

  emit('save', { ...f, name, doc, phone, email, address: f.address == null ? undefined : inicialMayuscula(f.address) })
}
</script>

<template>
  <CommonFormPage v-if="open">
    <div class="dialog-header">
      <h2>{{ client ? 'Editar cliente' : 'Nuevo cliente' }}</h2>
      <button class="icon-button" aria-label="Cerrar" :disabled="busy" @click="close">
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
            @blur="form.name = inicialesNombre(form.name ?? ''); tocados.add('name')"
            :aria-invalid="mostrarError('name')"
            aria-label="Nombre o razón social"
            required
            minlength="2"
            maxlength="90"
            autocomplete="name"
          />
          <CommonFormatoInvalido v-if="mostrarError('name')" />
        </label>

        <label>
          DNI / CUIT / CUIL *
          <input
            :value="form.doc"
            @input="form.doc = formatIdentityDocumentInput($event, form.doc)"
            inputmode="numeric"
            @blur="tocados.add('doc')"
            :aria-invalid="mostrarError('doc')"
            aria-label="DNI / CUIT / CUIL"
            required
            maxlength="13"
          />
          <CommonFormatoInvalido v-if="mostrarError('doc')" />
        </label>

        <label>
          Condición de IVA *
          <select v-model="form.vatCondition" @blur="tocados.add('vatCondition')" :aria-invalid="mostrarError('vatCondition')" aria-label="Condición de IVA" required>
            <option v-for="condition in vatOptions ?? VAT_CONDITIONS" :key="condition" :value="condition">{{ condition }}</option>
          </select>
          <CommonFormatoInvalido v-if="mostrarError('vatCondition')" />
          <small v-if="requiresCuit(form.vatCondition || '') && (form.doc || '').replace(/\D/g, '').length !== 11" class="muted">Podés guardar el cliente con DNI. Para emitir una factura con esta condición de IVA, deberás completar su CUIT de 11 números.</small>
        </label>

        <div class="form-grid">
          <label>
            Teléfono *
            <input
              v-model="form.phone"
              @blur="tocados.add('phone')"
              :aria-invalid="mostrarError('phone')"
              aria-label="Teléfono"
              type="tel"
              required
              autocomplete="tel"
            />
            <CommonFormatoInvalido v-if="mostrarError('phone')" />
          </label>
          <label>
            Correo electrónico
            <input
              v-model="form.email"
              @blur="tocados.add('email')"
              :aria-invalid="mostrarError('email')"
              type="email"
              maxlength="254"
              autocomplete="email"
            />
            <CommonFormatoInvalido v-if="mostrarError('email')" />
          </label>
        </div>

        <div v-if="vatOptions" class="form-grid">
          <label>Calle <input v-model="form.address" maxlength="150" autocomplete="address-line1" @blur="form.address = inicialMayuscula(form.address ?? ''); tocados.add('address')" :aria-invalid="mostrarError('address')"><CommonFormatoInvalido v-if="mostrarError('address')" /></label>
          <label>Altura <input v-model.number="form.streetNumber" type="number" min="0" max="2147483647" step="1" @blur="tocados.add('streetNumber')" :aria-invalid="mostrarError('streetNumber')"><CommonFormatoInvalido v-if="mostrarError('streetNumber')" /></label>
        </div>
        <p v-if="formError || error" class="error-message" role="alert">
          {{ formError || error }}
        </p>
      </div>

      <footer class="modal-footer">
        <button type="button" class="button" :disabled="busy" @click="close">Cancelar</button>
        <button type="submit" class="button primary" :disabled="busy">
          <Check :size="16" />{{ busy ? 'Guardando…' : 'Guardar' }}
        </button>
      </footer>
    </form>
  </CommonFormPage>
</template>
