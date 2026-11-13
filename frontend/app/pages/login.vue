<script setup lang="ts">
import { ArrowRight, Eye, EyeOff } from '@lucide/vue'
definePageMeta({ layout: 'auth' })
const auth = useOwnerAccount()
const api = useAuthApi()
const form = reactive({ email: '', password: '' })
const mechanic = ref<'Nicolás' | 'Santiago'>('Nicolás')
const showPassword = ref(false)
const busy = ref(false)
const error = ref('')
const emailInput = ref<HTMLInputElement | null>(null)
onMounted(() => { auth.initialize(); emailInput.value?.focus() })

async function submit() {
  error.value = ''
  if (!form.email.trim() || !form.password) { error.value = 'Completá tu correo y tu contraseña.'; return }
  busy.value = true
  try {
    await api.login(form.email, form.password)
    await navigateTo(api.homePath.value)
  }
  catch (err) { error.value = authError(err, 'No pudimos iniciar sesión. Intentá de nuevo.') }
  finally { busy.value = false }
}
async function mechanicPreview() { auth.enterMechanicPreview(mechanic.value); await navigateTo(auth.homePath.value) }
async function demo() { auth.enterDemo(); await navigateTo('/') }
</script>

<template>
  <div class="auth-form">
    <span class="auth-eyebrow">BIENVENIDO A OCTANO</span>
    <h2>Volvé a tu taller.</h2>
    <p class="auth-description">Tus clientes, tus órdenes y tus cuentas.</p>
    <form @submit.prevent="submit">
      <div class="auth-fields">
        <label class="auth-field">Correo electrónico<input ref="emailInput" v-model="form.email" type="email" autocomplete="username" placeholder="vos@tutaller.com" required :disabled="busy" /></label>
        <label class="auth-field">Contraseña
          <div class="auth-password"><input v-model="form.password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="Tu contraseña" required :disabled="busy" /><button type="button" :aria-label="showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'" :aria-pressed="showPassword" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="17" /><Eye v-else :size="17" /></button></div>
        </label>
      </div>
      <p v-if="error" class="auth-error" role="alert">{{ error }}</p>
      <button class="auth-submit" type="submit" :disabled="busy">{{ busy ? 'Ingresando…' : 'Ingresar' }}<ArrowRight v-if="!busy" :size="16" /></button>
    </form>
    <p class="auth-switch">¿Todavía no tenés una cuenta? <NuxtLink to="/registro" class="auth-link">Creá tu taller</NuxtLink></p>
    <div class="auth-divider">CONOCÉ OCTANO</div>
    <button class="auth-secondary" type="button" :disabled="busy" @click="demo">Explorar la demo<ArrowRight :size="14" /></button>
    <details class="mechanic-preview">
      <summary>Vista de demostración del mecánico</summary>
      <label class="auth-field">Perfil de vista previa<select v-model="mechanic" aria-label="Perfil de vista previa"><option>Nicolás</option><option>Santiago</option></select></label>
      <button class="auth-secondary" type="button" :disabled="busy" @click="mechanicPreview">Vista previa de {{ mechanic }}<ArrowRight :size="14" /></button>
    </details>
  </div>
</template>

<style scoped>
.mechanic-preview { margin-top: 16px; font-size: 12px; }
.mechanic-preview summary { cursor: pointer; }
.mechanic-preview .auth-field { margin: 12px 0; }
.auth-field select { width: 100%; min-height: 44px; border-radius: 8px; border: 1px solid var(--line); padding: 10px 12px; background: var(--surface, white); color: inherit; font: inherit; }
:global(html.dark .auth-field select) { background: #242426; }
</style>
