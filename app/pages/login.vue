<script setup lang="ts">
import { ArrowRight, Eye, EyeOff } from 'lucide-vue-next'
definePageMeta({ layout: 'auth' })
const auth = useOwnerAccount()
const { db } = useDatabase()
const form = reactive({ email: '', password: '', remember: false })
const showPassword = ref(false)
const busy = ref(false)
const error = ref('')
const emailInput = ref<HTMLInputElement | null>(null)
onMounted(() => { auth.initialize(); emailInput.value?.focus() })

async function submit() {
  error.value = ''
  if (!form.email.trim() || !form.password) { error.value = 'Completá tu correo y tu contraseña.'; return }
  busy.value = true
  try { await auth.login(form.email, form.password, form.remember); if (auth.owner.value) db.value.issuerVatCondition = auth.owner.value.vat; await navigateTo('/') }
  catch (err) { error.value = err instanceof Error ? err.message : 'No pudimos iniciar sesión. Intentá de nuevo.' }
  finally { busy.value = false }
}
async function demo() { auth.enterDemo(); await navigateTo('/') }
</script>

<template>
  <div class="auth-form">
    <span class="auth-eyebrow">BIENVENIDO A OCTANO</span>
    <h2>Volvé a tu taller.</h2>
    <p class="auth-description">Tus clientes, tus órdenes y tus cuentas.<br />Justo donde las dejaste.</p>
    <form @submit.prevent="submit">
      <div class="auth-fields">
        <label class="auth-field">Correo electrónico<input ref="emailInput" v-model="form.email" type="email" autocomplete="username" placeholder="vos@tutaller.com" required :disabled="busy" /></label>
        <label class="auth-field">Contraseña
          <div class="auth-password"><input v-model="form.password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="Tu contraseña" required :disabled="busy" /><button type="button" :aria-label="showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'" :aria-pressed="showPassword" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="17" /><Eye v-else :size="17" /></button></div>
        </label>
      </div>
      <div class="auth-options"><label class="auth-checkbox"><input v-model="form.remember" type="checkbox" /> Mantener sesión en este equipo</label></div>
      <p v-if="error" class="auth-error" role="alert">{{ error }}</p>
      <button class="auth-submit" type="submit" :disabled="busy">{{ busy ? 'Ingresando…' : 'Ingresar' }}<ArrowRight v-if="!busy" :size="16" /></button>
    </form>
    <p class="auth-switch">¿Todavía no tenés una cuenta? <NuxtLink to="/registro" class="auth-link">Creá tu taller</NuxtLink></p>
    <div class="auth-divider">CONOCÉ OCTANO</div>
    <button class="auth-secondary" type="button" @click="demo">Explorar la demo<ArrowRight :size="14" /></button>
    <p class="auth-disclosure">Cuenta local de demostración. No se envían correos y la conexión con ARCA es simulada.</p>
  </div>
</template>
