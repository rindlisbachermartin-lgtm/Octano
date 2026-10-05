<script setup lang="ts">
import { ArrowRight, ArrowLeft, Eye, EyeOff } from 'lucide-vue-next'
definePageMeta({ layout: 'auth' })
const auth = useOwnerAccount()
const { db } = useDatabase()
const step = ref(1)
const busy = ref(false)
const showPassword = ref(false)
const error = ref('')
const form = reactive({ name: '', email: '', phone: '', password: '', confirmation: '', workshop: '', legalName: '', cuit: '', address: '', city: '', province: '', vat: 'IVA Responsable Inscripto' as import('~/types').IssuerVatCondition })
const nameInput = ref<HTMLInputElement | null>(null)
const workshopInput = ref<HTMLInputElement | null>(null)
onMounted(() => { auth.initialize(); nameInput.value?.focus() })
async function submit() {
  error.value = ''
  if (step.value === 1) {
    if (!form.name.trim() || !/^[^\s@]+@[^\s@.]+\.[^\s@]+$/.test(form.email.trim())) { error.value = 'Completá tu nombre y un correo electrónico válido.'; return }
    if (form.phone.replace(/\D/g, '').length < 10 || form.phone.replace(/\D/g, '').length > 15) { error.value = 'Ingresá un teléfono con código de área, de 10 a 15 números.'; return }
    if (form.password.length < 8) { error.value = 'Usá una contraseña de al menos 8 caracteres.'; return }
    if (form.password !== form.confirmation) { error.value = 'Las contraseñas no coinciden.'; return }
    if (auth.account.value) { error.value = 'Ya hay una cuenta registrada en esta demo. Ingresá con ese correo.'; return }
    step.value = 2
    await nextTick(); workshopInput.value?.focus()
    return
  }
  if (!form.workshop.trim() || !form.legalName.trim()) { error.value = 'Completá el nombre del taller y la razón social.'; return }
  if (!isValidCuit(form.cuit)) { error.value = 'Revisá el CUIT: debe tener 11 números y un dígito verificador válido.'; return }
  busy.value = true
  try {
    await auth.register({ name: form.name.trim(), email: form.email, phone: form.phone.trim(), workshop: form.workshop.trim(), legalName: form.legalName.trim(), cuit: form.cuit, address: form.address.trim(), city: form.city.trim(), province: form.province.trim(), vat: form.vat, pointOfSale: 3 }, form.password)
    db.value.issuerVatCondition = form.vat
    await navigateTo('/')
  } catch (err) { error.value = err instanceof Error ? err.message : 'No pudimos guardar tu cuenta. Intentá de nuevo.' }
  finally { busy.value = false }
}
function back() { step.value = 1; error.value = '' }
</script>

<template>
  <div class="auth-form">
    <div class="auth-steps" aria-label="Progreso del registro"><span class="active"></span><span :class="{ active: step >= 2 }"></span></div>
    <span class="auth-eyebrow">PASO {{ step }} DE 2 · {{ step === 1 ? 'TU CUENTA' : 'TU TALLER' }}</span>
    <h2>{{ step === 1 ? 'Tu taller empieza acá.' : 'Dale nombre a tu taller.' }}</h2>
    <p class="auth-description">{{ step === 1 ? 'Creá tu cuenta como dueño del taller.' : 'Guardamos el CUIT en tu perfil. Después podés vincular ARCA desde Configuración > Facturación.' }}</p>
    <form @submit.prevent="submit">
      <div v-if="step === 1" class="auth-fields">
        <label class="auth-field">Tu nombre y apellido<input ref="nameInput" v-model="form.name" autocomplete="name" placeholder="Ej. Martín Pérez" required maxlength="90" /></label>
        <label class="auth-field">Correo electrónico<input v-model="form.email" type="email" autocomplete="email" placeholder="vos@tutaller.com" required /></label>
        <label class="auth-field">Teléfono<input v-model="form.phone" type="tel" autocomplete="tel" placeholder="Código de área + número" required /></label>
        <label class="auth-field">Contraseña<div class="auth-password"><input v-model="form.password" :type="showPassword ? 'text' : 'password'" autocomplete="new-password" minlength="8" placeholder="Al menos 8 caracteres" required /><button type="button" :aria-label="showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'" :aria-pressed="showPassword" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="17" /><Eye v-else :size="17" /></button></div></label>
        <label class="auth-field">Repetí tu contraseña<input v-model="form.confirmation" :type="showPassword ? 'text' : 'password'" autocomplete="new-password" placeholder="Una vez más" required /></label>
      </div>
      <div v-else class="auth-fields">
        <label class="auth-field">Nombre del taller<input ref="workshopInput" v-model="form.workshop" autocomplete="organization" placeholder="Ej. Taller Central" required maxlength="90" /></label>
        <label class="auth-field">Nombre o razón social<input v-model="form.legalName" placeholder="Como figura en la constancia de inscripción" required maxlength="90" /></label>
        <label class="auth-field">CUIT<input :value="form.cuit" @input="form.cuit = formatIdentityDocumentInput($event, form.cuit)" inputmode="numeric" placeholder="30-12345678-1" required /><small>El CUIT con el que el taller va a facturar.</small></label>
      </div>
      <p v-if="error" class="auth-error" role="alert">{{ error }}</p>
      <button class="auth-submit register-submit" type="submit" :disabled="busy">{{ busy ? 'Creando tu taller…' : step === 1 ? 'Continuar' : 'Crear cuenta e ingresar' }}<ArrowRight v-if="!busy" :size="16" /></button>
      <button v-if="step === 2" class="auth-back" type="button" :disabled="busy" @click="back"><ArrowLeft :size="14" />Volver a mi cuenta</button>
    </form>
    <p class="auth-switch">¿Ya tenés una cuenta? <NuxtLink to="/login" class="auth-link">Ingresá</NuxtLink></p>
    <p class="auth-disclosure">Registro de demostración en este navegador. La vinculación con ARCA se configura dentro del sistema.</p>
  </div>
</template>

<style scoped>
.register-submit { margin-top: 24px; }
.auth-back { display: flex; align-items: center; justify-content: center; gap: 6px; margin: 14px auto 0; padding: 5px; border: 0; background: transparent; font-size: 11px; color: var(--auth-muted); cursor: pointer; }
</style>
