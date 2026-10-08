<script setup lang="ts">
import { ArrowRight, Eye, EyeOff } from 'lucide-vue-next'
definePageMeta({ layout: 'auth' })
const auth = useOwnerAccount()
const { db } = useDatabase()
const form = reactive({ email: '', password: '', remember: false })
const role = ref<'owner' | 'mechanic'>('owner')
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
    if (role.value === 'mechanic') await auth.loginMechanic(form.email, form.password, form.remember)
    else await auth.login(form.email, form.password, form.remember)
    if (auth.owner.value) db.value.issuerVatCondition = auth.owner.value.vat
    await navigateTo(auth.homePath.value)
  }
  catch (err) { error.value = err instanceof Error ? err.message : 'No pudimos iniciar sesión. Intentá de nuevo.' }
  finally { busy.value = false }
}
async function mechanicPreview() { auth.enterMechanicPreview(mechanic.value); await navigateTo(auth.homePath.value) }
async function demo() { auth.enterDemo(); await navigateTo('/') }
</script>

<template>
  <div class="auth-form">
    <span class="auth-eyebrow">BIENVENIDO A OCTANO</span>
    <h2>Volvé a tu taller.</h2>
    <p class="auth-description">Tus clientes, tus órdenes y tus cuentas.<br />Justo donde las dejaste.</p>
    <div class="login-roles" role="group" aria-label="Tipo de acceso">
      <button type="button" :aria-pressed="role === 'owner'" :class="{ selected: role === 'owner' }" @click="role = 'owner'; error = ''; form.password = ''">Administración</button>
      <button type="button" :aria-pressed="role === 'mechanic'" :class="{ selected: role === 'mechanic' }" @click="role = 'mechanic'; error = ''; form.password = ''">Mecánico</button>
    </div>
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
    <p v-if="role === 'owner'" class="auth-switch">¿Todavía no tenés una cuenta? <NuxtLink to="/registro" class="auth-link">Creá tu taller</NuxtLink></p>
    <div v-if="role === 'owner'" class="auth-divider">CONOCÉ OCTANO</div>
    <button v-if="role === 'owner'" class="auth-secondary" type="button" @click="demo">Explorar la demo<ArrowRight :size="14" /></button>
    <template v-if="role === 'mechanic'">
      <div class="auth-divider">CONOCÉ LA VISTA DEL MECÁNICO</div>
      <label class="auth-field">Perfil de vista previa<select v-model="mechanic" aria-label="Perfil de vista previa"><option>Nicolás</option><option>Santiago</option></select></label>
      <button class="auth-secondary" type="button" :disabled="busy" @click="mechanicPreview">Vista previa de {{ mechanic }}<ArrowRight :size="14" /></button>
      <p class="auth-disclosure">Accesos de prueba: <strong>nicolas@taller.demo</strong> o <strong>santiago@taller.demo</strong>. Contraseña: <strong>Octano123</strong>.</p>
    </template>
    <p class="auth-disclosure">Cuenta local de demostración. No se envían correos y la conexión con ARCA es simulada.</p>
  </div>
</template>

<style scoped>
.login-roles { display: flex; gap: 4px; padding: 4px; border: 1px solid var(--line); border-radius: 10px; margin-bottom: 22px; }
.login-roles button { flex: 1; border: 0; border-radius: 7px; padding: 10px; background: transparent; color: inherit; cursor: pointer; font: inherit; font-size: 13px; }
.login-roles button.selected { background: #2563eb; color: white; }
.auth-field select { width: 100%; min-height: 44px; border-radius: 8px; border: 1px solid var(--line); padding: 10px 12px; background: var(--surface, white); color: inherit; font: inherit; }
:global(html.dark .auth-field select) { background: #242426; }
</style>
