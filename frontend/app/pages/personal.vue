<script setup lang="ts">
import { Plus, Check, X, Search } from '@lucide/vue'
import type { AuthUser } from '~/composables/useAuthApi'

type Alta = 'ADMIN' | 'MECANICO'
interface Mecanico {
  id: number
  nombre: string
  apellido: string
  activo: boolean
}

interface CuentaPersonal extends AuthUser {
  datosPersonales?: {
    nombre: string
    apellido: string
    telefono: string
    fechaInicio: string
  } | null
}

const auth = useAuthApi()
const { today } = useWorkshopDay()
const { matches } = useHelpers()
const usuarios = ref<CuentaPersonal[]>([])
const mecanicos = ref<Mecanico[]>([])
const alta = ref<Alta | null>(null)
const search = ref('')
const form = reactive({ email: '', password: '', nombre: '', apellido: '', telefono: '', fechaInicio: '' })
type Campo = keyof typeof form
const editados = reactive(new Set<Campo>())
const intentoGuardar = ref(false)
const gruposCampos = [
  [
    { clave: 'nombre', etiqueta: 'Nombre', tipo: 'text', autocomplete: 'given-name', max: 90 },
    { clave: 'apellido', etiqueta: 'Apellido', tipo: 'text', autocomplete: 'family-name', max: 90 },
  ],
  [
    { clave: 'telefono', etiqueta: 'Teléfono', tipo: 'tel', autocomplete: 'tel', max: 30 },
    { clave: 'fechaInicio', etiqueta: 'Fecha de inicio', tipo: 'date', autocomplete: 'off' },
  ],
  [{ clave: 'email', etiqueta: 'Correo electrónico', tipo: 'email', autocomplete: 'off', max: 254 }],
  [{ clave: 'password', etiqueta: 'Contraseña inicial', tipo: 'password', autocomplete: 'new-password', max: 72 }],
] as const
const cargando = ref(false)
const guardando = ref(false)
const errorLista = ref('')
const errorAlta = ref('')
const mensaje = ref('')
const nombres = computed(() => new Map(mecanicos.value.map(m => [m.id, `${m.nombre} ${m.apellido}`])))
const roles = { ADMIN: 'Administrador', MECANICO: 'Mecánico', RECEPCION: 'Recepción' }
const filtrados = computed(() => usuarios.value.filter(u => matches(search.value, u.email, roles[u.rol], nombreCompleto(u))))

function nombreCompleto(usuario: CuentaPersonal) {
  const datos = usuario.datosPersonales
  if (datos) return `${datos.nombre} ${datos.apellido}`
  return usuario.mecanicoId === null ? '' : nombres.value.get(usuario.mecanicoId) || ''
}

async function cargarPersonal() {
  const headers = auth.authorization.value
  if (!headers || cargando.value) return
  cargando.value = true
  errorLista.value = ''
  try {
    const [cuentas, fichas] = await Promise.all([
      $fetch<CuentaPersonal[]>('/api/usuarios', { headers }),
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
  editados.clear()
  intentoGuardar.value = false
  errorAlta.value = ''
  mensaje.value = ''
  Object.assign(form, { email: '', password: '', nombre: '', apellido: '', telefono: '', fechaInicio: today.value })
}

function cerrar() {
  if (guardando.value) return
  form.password = ''
  alta.value = null
}

useModalEscape(() => alta.value !== null, cerrar)

const errores = computed<Record<Campo, string>>(() => {
  const nombre = form.nombre.trim()
  const apellido = form.apellido.trim()
  const telefono = form.telefono.trim()
  const cantidadDigitos = telefono.replace(/\D/g, '').length
  const email = form.email.trim()
  const nombreValido = /^\p{L}[\p{L}\p{M}]*(?:[ .'’-]\p{L}[\p{L}\p{M}]*)*$/u
  const fecha = Date.parse(form.fechaInicio)
  const fechaValida = /^\d{4}-\d{2}-\d{2}$/.test(form.fechaInicio) && Number.isFinite(fecha)
    && new Date(fecha).toISOString().slice(0, 10) === form.fechaInicio && form.fechaInicio <= today.value

  return {
    nombre: nombre.length >= 2 && nombre.length <= 90 && nombreValido.test(nombre)
      ? '' : 'De 2 a 90 caracteres, sin números.',
    apellido: apellido.length <= 90 && nombreValido.test(apellido)
      ? '' : 'Ingresá un apellido sin números.',
    telefono: cantidadDigitos >= 10 && cantidadDigitos <= 15 && /^[+0-9() .-]{10,30}$/.test(telefono)
      ? '' : 'De 10 a 15 dígitos, sin letras.',
    fechaInicio: fechaValida ? '' : 'Usá una fecha válida hasta hoy.',
    email: /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) && email.length <= 254
      ? '' : 'Ingresá un correo válido.',
    password: form.password.trim() && form.password.length >= 12 && new TextEncoder().encode(form.password).length <= 72
      ? '' : 'Mínimo 12 caracteres, máximo 72 bytes.',
  }
})

function mostrarFormato(campo: Campo) {
  return intentoGuardar.value || editados.has(campo) || form[campo] !== ''
}

function alSalirDelCampo(campo: Campo) {
  if (campo === 'nombre' || campo === 'apellido') {
    form[campo] = inicialMayuscula(form[campo])
  }
  editados.add(campo)
}

async function guardar() {
  const headers = auth.authorization.value
  const tipo = alta.value
  if (!headers || !tipo || guardando.value) return
  errorAlta.value = ''
  intentoGuardar.value = true
  const campoInvalido = gruposCampos.flat().find(({ clave }) => errores.value[clave])
  if (campoInvalido) {
    await nextTick()
    document.getElementById(`personal-${campoInvalido.clave}`)?.focus()
    return
  }
  const datosPersonales = {
    nombre: inicialMayuscula(form.nombre),
    apellido: inicialMayuscula(form.apellido),
    telefono: form.telefono.trim(),
    fechaInicio: form.fechaInicio,
  }
  const credenciales = { email: form.email.trim(), password: form.password }
  guardando.value = true
  try {
    if (tipo === 'ADMIN') {
      await $fetch('/api/usuarios', { method: 'POST', headers, body: { ...credenciales, rol: tipo, datosPersonales } })
    } else {
      await $fetch('/api/mecanicos/con-cuenta', { method: 'POST', headers, body: { ...credenciales, mecanico: datosPersonales } })
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
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">TU TALLER / PERSONAL</div>
        <h1>Personal</h1>
      </div>
      <div class="personal-actions">
        <button type="button" class="button" :disabled="guardando" @click="abrir('ADMIN')"><Plus :size="17" />Agregar administrador</button>
        <button type="button" class="button primary" :disabled="guardando" @click="abrir('MECANICO')"><Plus :size="17" />Agregar mecánico</button>
      </div>
    </section>

    <div class="list-toolbar">
      <label class="search-box">
        <Search :size="17" />
        <input v-model="search" placeholder="Buscar por nombre, correo o rol" aria-label="Buscar personal">
      </label>
      <span class="muted">{{ filtrados.length }} cuenta(s)</span>
    </div>

    <p v-if="mensaje" role="status">{{ mensaje }}</p>
    <p v-if="cargando" class="muted" role="status">Cargando personal…</p>
    <div v-else-if="errorLista" class="panel">
      <p class="error-message" role="alert">{{ errorLista }}</p>
      <button type="button" class="button" @click="cargarPersonal">Reintentar</button>
    </div>
    <section v-else-if="filtrados.length" class="panel table-scroll">
      <table>
        <thead><tr><th>PERSONAL</th><th>CORREO</th><th>ROL</th><th>ESTADO</th></tr></thead>
        <tbody>
          <tr v-for="usuario in filtrados" :key="usuario.id">
            <td><div class="client-cell"><strong>{{ nombreCompleto(usuario) || '—' }}</strong></div></td>
            <td>{{ usuario.email }}</td>
            <td>{{ roles[usuario.rol] }}</td>
            <td><span class="badge" :class="usuario.activo ? 'success' : 'neutral'">{{ usuario.activo ? 'Activo' : 'Inactivo' }}</span></td>
          </tr>
        </tbody>
      </table>
    </section>
    <div v-else class="panel"><p class="muted">{{ search ? 'No se encontraron cuentas.' : 'No hay cuentas en el taller.' }}</p></div>

    <CommonFormPage v-if="alta">
      <div class="dialog-header">
        <h2>{{ alta === 'ADMIN' ? 'Nuevo administrador' : 'Nuevo mecánico' }}</h2>
        <button type="button" class="icon-button" aria-label="Cerrar" :disabled="guardando" @click="cerrar"><X :size="18" /></button>
      </div>
      <form class="entry-form" novalidate @submit.prevent="guardar">
        <div class="form-fields">
          <p class="muted">Los campos marcados con * son obligatorios.</p>
          <div v-for="grupo in gruposCampos" :key="grupo[0].clave" :class="{ 'form-grid': grupo.length > 1 }">
            <label v-for="campo in grupo" :key="campo.clave">
              {{ campo.etiqueta }} *
              <input
                :id="'personal-' + campo.clave"
                v-model="form[campo.clave]"
                :type="campo.tipo"
                :autocomplete="campo.autocomplete"
                :maxlength="'max' in campo ? campo.max : undefined"
                :max="campo.tipo === 'date' ? today : undefined"
                :aria-invalid="mostrarFormato(campo.clave) && !!errores[campo.clave]"
                :aria-describedby="mostrarFormato(campo.clave) && errores[campo.clave] ? 'formato-' + campo.clave : undefined"
                :disabled="guardando"
                required
                @blur="alSalirDelCampo(campo.clave)"
              >
              <CommonFormatoInvalido
                v-if="mostrarFormato(campo.clave) && errores[campo.clave]"
                :id="'formato-' + campo.clave"
              />
            </label>
          </div>
          <p v-if="errorAlta" class="personal-error" role="alert">{{ errorAlta }}</p>
        </div>
        <footer class="modal-footer">
          <button type="button" class="button" :disabled="guardando" @click="cerrar">Cancelar</button>
          <button type="submit" class="button primary" :disabled="guardando"><Check :size="16" />{{ guardando ? 'Guardando…' : 'Guardar' }}</button>
        </footer>
      </form>
    </CommonFormPage>
  </div>
</template>

<style scoped>
.personal-error {
  color: #b42318;
  font-size: 12px;
}

:global(html.dark) .personal-error {
  color: #ff766e;
}

.personal-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
</style>
