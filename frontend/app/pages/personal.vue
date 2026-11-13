<script setup lang="ts">
import type { AuthUser } from '~/composables/useAuthApi'

type Alta = 'ADMIN' | 'MECANICO'
interface Mecanico {
  id: number
  nombre: string
  apellido: string
  activo: boolean
}

const auth = useAuthApi()
const { today } = useWorkshopDay()
const usuarios = ref<AuthUser[]>([])
const mecanicos = ref<Mecanico[]>([])
const alta = ref<Alta | null>(null)
const form = reactive({ email: '', password: '', nombre: '', apellido: '', telefono: '', fechaInicio: '' })
const cargando = ref(false)
const guardando = ref(false)
const errorLista = ref('')
const errorAlta = ref('')
const mensaje = ref('')
const nombres = computed(() => new Map(mecanicos.value.map(m => [m.id, `${m.nombre} ${m.apellido}`])))
const roles = { ADMIN: 'Administrador', MECANICO: 'Mecánico', RECEPCION: 'Recepción' }

async function cargarPersonal() {
  const headers = auth.authorization.value
  if (!headers || cargando.value) return
  cargando.value = true
  errorLista.value = ''
  try {
    const [cuentas, fichas] = await Promise.all([
      $fetch<AuthUser[]>('/api/usuarios', { headers }),
      $fetch<Mecanico[]>('/api/mecanicos', { headers }),
    ])
    usuarios.value = cuentas
    mecanicos.value = fichas
  } catch (err) {
    errorLista.value = authError(err, 'No pudimos cargar el personal.')
  } finally {
    cargando.value = false
  }
}

function abrir(tipo: Alta) {
  alta.value = tipo
  errorAlta.value = ''
  mensaje.value = ''
  Object.assign(form, { email: '', password: '', nombre: '', apellido: '', telefono: '', fechaInicio: today.value })
}

async function guardar() {
  const headers = auth.authorization.value
  const tipo = alta.value
  if (!headers || !tipo || guardando.value) return
  errorAlta.value = ''
  if (form.password.length < 12 || new TextEncoder().encode(form.password).length > 72) {
    errorAlta.value = 'Usá una contraseña de al menos 12 caracteres y hasta 72 bytes.'
    return
  }
  const credenciales = { email: form.email.trim(), password: form.password }
  guardando.value = true
  try {
    if (tipo === 'ADMIN') {
      await $fetch('/api/usuarios', { method: 'POST', headers, body: { ...credenciales, rol: tipo } })
    } else {
      const mecanico = {
        nombre: form.nombre.trim(),
        apellido: form.apellido.trim(),
        telefono: form.telefono.trim(),
        fechaInicio: form.fechaInicio,
      }
      await $fetch('/api/mecanicos/con-cuenta', { method: 'POST', headers, body: { ...credenciales, mecanico } })
    }
    form.password = ''
    alta.value = null
    mensaje.value = tipo === 'ADMIN' ? 'Cuenta de administrador creada.' : 'Mecánico y cuenta de acceso creados.'
    await cargarPersonal()
  } catch (err) {
    errorAlta.value = authError(err, 'No pudimos guardar el alta.')
  } finally {
    guardando.value = false
  }
}

onMounted(cargarPersonal)
</script>

<template>
  <div class="personal">
    <div class="acciones">
      <h1>Personal</h1>
      <button type="button" class="button primary" :disabled="guardando || cargando" @click="abrir('MECANICO')">Agregar mecánico</button>
      <button type="button" class="button" :disabled="guardando || cargando" @click="abrir('ADMIN')">Agregar administrador</button>
    </div>

    <section v-if="alta" class="panel">
      <h2>{{ alta === 'ADMIN' ? 'Nuevo administrador' : 'Nuevo mecánico' }}</h2>
      <form @submit.prevent="guardar">
        <fieldset :disabled="guardando" class="campos">
          <template v-if="alta === 'MECANICO'">
            <label>Nombre<input v-model="form.nombre" required maxlength="90" autocomplete="off"></label>
            <label>Apellido<input v-model="form.apellido" required maxlength="90" autocomplete="off"></label>
            <label>Teléfono<input v-model="form.telefono" required type="tel" minlength="6" maxlength="30" autocomplete="off"></label>
            <label>Fecha de inicio<input v-model="form.fechaInicio" required type="date" :max="today"></label>
          </template>
          <label>Correo electrónico<input v-model="form.email" required type="email" maxlength="254" autocomplete="off"></label>
          <label>Contraseña inicial<input v-model="form.password" required type="password" minlength="12" maxlength="72" autocomplete="new-password"></label>
          <div class="acciones">
            <button class="button primary">{{ guardando ? 'Guardando…' : 'Crear cuenta' }}</button>
            <button type="button" class="button" @click="form.password = ''; alta = null">Cancelar</button>
          </div>
        </fieldset>
      </form>
      <p v-if="errorAlta" role="alert">{{ errorAlta }}</p>
    </section>

    <p v-if="mensaje" role="status">{{ mensaje }}</p>
    <section class="panel">
      <h2>Cuentas del taller</h2>
      <p v-if="cargando" role="status">Cargando personal…</p>
      <div v-else-if="errorLista">
        <p role="alert">{{ errorLista }}</p>
        <button type="button" class="button" @click="cargarPersonal">Reintentar</button>
      </div>
      <div v-else-if="usuarios.length" class="tabla">
        <table>
          <thead><tr><th scope="col">Correo</th><th scope="col">Rol</th><th scope="col">Mecánico vinculado</th><th scope="col">Estado</th></tr></thead>
          <tbody>
            <tr v-for="usuario in usuarios" :key="usuario.id">
              <th scope="row">{{ usuario.email }}</th>
              <td>{{ roles[usuario.rol] }}</td>
              <td>{{ usuario.mecanicoId === null ? '—' : nombres.get(usuario.mecanicoId) || '—' }}</td>
              <td>{{ usuario.activo ? 'Activa' : 'Inactiva' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else>No hay cuentas en el taller.</p>
    </section>
  </div>
</template>

<style scoped>
.personal { display: grid; gap: 16px; }
.panel { padding: 20px; }
.acciones { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.acciones h1 { margin-right: auto; }
.campos { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 220px), 1fr)); gap: 14px; padding: 0; border: 0; }
.campos label { display: grid; gap: 6px; }
.campos .acciones { grid-column: 1 / -1; }
.tabla { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; }
th, td { padding: 10px; text-align: left; border-bottom: 1px solid var(--line); }
</style>
