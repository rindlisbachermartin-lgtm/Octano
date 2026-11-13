<script setup lang="ts">
import { ArrowRight, ArrowLeft, Eye, EyeOff } from '@lucide/vue'

definePageMeta({ layout: 'auth' })
const auth = useAuthApi()
const step = ref(1)
const busy = ref(false)
const showPassword = ref(false)
const error = ref('')
const emailInput = ref<HTMLInputElement | null>(null)
const workshopInput = ref<HTMLInputElement | null>(null)
const form = reactive({
  email: '', phone: '', password: '', confirmation: '', workshop: '',
  calle: '', altura: '' as number | '',
})

onMounted(() => emailInput.value?.focus())

async function submit() {
  error.value = ''
  if (step.value === 1) {
    if (form.password.length < 12 || new TextEncoder().encode(form.password).length > 72) {
      error.value = 'Usá una contraseña de al menos 12 caracteres y hasta 72 bytes.'
      return
    }
    if (form.password !== form.confirmation) {
      error.value = 'Las contraseñas no coinciden.'
      return
    }
    step.value = 2
    await nextTick()
    workshopInput.value?.focus()
    return
  }

  if (form.altura === '') return

  busy.value = true
  try {
    await auth.register(form.email, form.password, {
      nombre: inicialMayuscula(form.workshop),
      calle: inicialMayuscula(form.calle),
      altura: form.altura,
      telefono: form.phone.trim(),
    })
    await navigateTo(auth.homePath.value)
  } catch (err) {
    error.value = authError(err, 'No pudimos guardar tu cuenta. Intentá de nuevo.')
  } finally {
    busy.value = false
  }
}

function back() {
  step.value = 1
  error.value = ''
}
</script>

<template>
  <div class="auth-form">
    <div class="auth-steps" aria-label="Progreso del registro"><span class="active"></span><span :class="{ active: step >= 2 }"></span></div>
    <span class="auth-eyebrow">PASO {{ step }} DE 2 · {{ step === 1 ? 'TU CUENTA' : 'TU TALLER' }}</span>
    <h2>{{ step === 1 ? 'Tu taller empieza acá.' : 'Dale nombre a tu taller.' }}</h2>
    <p class="auth-description">{{ step === 1 ? 'Creá tu cuenta como dueño del taller.' : 'Completá los datos del taller para activar tu cuenta.' }}</p>
    <form @submit.prevent="submit">
      <div v-if="step === 1" class="auth-fields">
        <label class="auth-field">Correo electrónico<input ref="emailInput" v-model="form.email" type="email" autocomplete="email" placeholder="vos@tutaller.com" required /></label>
        <label class="auth-field">Teléfono del taller<input v-model="form.phone" type="tel" autocomplete="tel" placeholder="Código de área + número" minlength="6" maxlength="30" required /></label>
        <label class="auth-field">Contraseña<div class="auth-password"><input v-model="form.password" :type="showPassword ? 'text' : 'password'" autocomplete="new-password" minlength="12" maxlength="72" placeholder="Entre 12 y 72 caracteres" required /><button type="button" :aria-label="showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'" :aria-pressed="showPassword" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="17" /><Eye v-else :size="17" /></button></div></label>
        <label class="auth-field">Repetí tu contraseña<input v-model="form.confirmation" :type="showPassword ? 'text' : 'password'" autocomplete="new-password" placeholder="Una vez más" required /></label>
      </div>
      <div v-else class="auth-fields">
        <label class="auth-field">Nombre del taller<input ref="workshopInput" v-model="form.workshop" @blur="form.workshop = inicialMayuscula(form.workshop)" autocomplete="organization" placeholder="Ej. Taller Central" required maxlength="120" /></label>
        <div class="auth-two-fields">
          <label class="auth-field">Calle<input v-model="form.calle" @blur="form.calle = inicialMayuscula(form.calle)" autocomplete="address-line1" maxlength="150" required /></label>
          <label class="auth-field">Altura<input v-model.number="form.altura" type="number" min="0" max="2147483647" step="1" required /></label>
        </div>
      </div>
      <p v-if="error" class="auth-error" role="alert">{{ error }}</p>
      <button class="auth-submit register-submit" type="submit" :disabled="busy">{{ busy ? 'Creando tu taller…' : step === 1 ? 'Continuar' : 'Crear cuenta e ingresar' }}<ArrowRight v-if="!busy" :size="16" /></button>
      <button v-if="step === 2" class="auth-back" type="button" :disabled="busy" @click="back"><ArrowLeft :size="14" />Volver a mi cuenta</button>
    </form>
    <p class="auth-switch">¿Ya tenés una cuenta? <NuxtLink to="/login" class="auth-link">Ingresá</NuxtLink></p>
    <p class="auth-disclosure">Al crear el taller también se crea tu cuenta de administrador.</p>
  </div>
</template>

<style scoped>
.register-submit { margin-top: 24px; }
.auth-back { display: flex; align-items: center; justify-content: center; gap: 6px; margin: 14px auto 0; padding: 5px; border: 0; background: transparent; font-size: 11px; color: var(--auth-muted); cursor: pointer; }
</style>
