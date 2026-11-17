<script setup lang="ts">
import { Plus, Check, X, Search } from '@lucide/vue'
import type { AuthUser } from '~/composables/useAuthApi'

type Alta = 'ADMIN' | 'MECANICO'
interface Mecanico {
  id: number
  nombre: string
  apellido: string
  telefono: string
  fechaInicio: string
  activo: boolean
}

interface CuentaPersonal extends AuthUser {
  dueno: boolean
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
const enEdicion = ref<CuentaPersonal | null>(null)
const bajaPendiente = ref<CuentaPersonal | null>(null)
const errorBaja = ref('')
const search = ref('')
const form = reactive({ email: '', password: '', confirmacion: '', nombre: '', apellido: '', telefono: '', fechaInicio: '' })
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
  [{ clave: 'confirmacion', etiqueta: 'Repetí la contraseña', tipo: 'password', autocomplete: 'new-password', max: 72 }],
] as const
const gruposVisibles = computed(() => {
  if (!enEdicion.value) return gruposCampos
  if (enEdicion.value.rol === 'RECEPCION') return gruposCampos.slice(2, 3)
  return gruposCampos.slice(0, 3)
})
const cargando = ref(false)
const guardando = ref(false)
const errorLista = ref('')
const errorAlta = ref('')
const mensaje = ref('')
const nombres = computed(() => new Map(mecanicos.value.map(m => [m.id, `${m.nombre} ${m.apellido}`])))
const roles = { DUENO: 'Dueño del taller', ADMIN: 'Administrador', MECANICO: 'Mecánico', RECEPCION: 'Recepción' }
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
  enEdicion.value = null
  alta.value = tipo
  prepararFormulario()
}

function prepararFormulario() {
  editados.clear()
  intentoGuardar.value = false
  errorAlta.value = ''
  mensaje.value = ''
  Object.assign(form, { email: '', password: '', confirmacion: '', nombre: '', apellido: '', telefono: '', fechaInicio: today.value })
}

function editar(usuario: CuentaPersonal) {
  prepararFormulario()
  enEdicion.value = usuario
  form.email = usuario.email
  const datos = usuario.datosPersonales ?? mecanicos.value.find(m => m.id === usuario.mecanicoId)
  form.fechaInicio = datos?.fechaInicio ?? ''
  if (datos) {
    form.nombre = datos.nombre
    form.apellido = datos.apellido
    form.telefono = datos.telefono
  }
}

function puedeDarBaja(usuario: CuentaPersonal) {
  if (usuario.dueno || usuario.rol === 'DUENO') return false
  if (usuario.rol === 'ADMIN') return usuario.dueno === false && usuario.activo
  return usuario.rol === 'MECANICO'
    && (usuario.activo || mecanicos.value.some(m => m.id === usuario.mecanicoId && m.activo))
}

function pedirBaja(usuario: CuentaPersonal) {
  errorBaja.value = ''
  mensaje.value = ''
  bajaPendiente.value = usuario
}

function cancelarBaja() {
  if (!guardando.value) bajaPendiente.value = null
}

async function confirmarBaja() {
  const usuario = bajaPendiente.value
  const headers = auth.authorization.value
  if (!usuario || !puedeDarBaja(usuario) || !headers || guardando.value) return
  const recurso = usuario.mecanicoId === null ? `usuarios/${usuario.id}` : `mecanicos/${usuario.mecanicoId}`
  errorBaja.value = ''
  guardando.value = true
  try {
    await $fetch(`/api/${recurso}/estado`, { method: 'PATCH', headers, body: { activo: false } })
    bajaPendiente.value = null
    enEdicion.value = null
    if (usuario.id === auth.user.value?.id) {
      await auth.logout()
      await navigateTo('/login')
      return
    }
    mensaje.value = 'Baja registrada.'
    await cargarPersonal()
  } catch (err) {
    errorBaja.value = authError(err, 'No pudimos registrar la baja.')
  } finally {
    guardando.value = false
  }
}

function cerrar() {
  if (guardando.value) return
  form.password = ''
  form.confirmacion = ''
  alta.value = null
  enEdicion.value = null
}

useModalEscape(() => !bajaPendiente.value && (alta.value !== null || enEdicion.value !== null), cerrar)

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
    confirmacion: form.confirmacion && form.confirmacion === form.password ? '' : 'Las contraseñas no coinciden.',
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
  const editado = enEdicion.value
  if (!headers || (!tipo && !editado) || guardando.value) return
  errorAlta.value = ''
  intentoGuardar.value = true
  const campoInvalido = gruposVisibles.value.flat().find(({ clave }) => errores.value[clave])
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
    if (editado) {
      const cuenta = await $fetch<CuentaPersonal>(`/api/usuarios/${editado.id}`, {
        method: 'PUT', headers,
        body: { email: credenciales.email, datosPersonales: editado.rol === 'RECEPCION' ? null : datosPersonales },
      })
      if (cuenta.id === auth.user.value?.id) auth.user.value = cuenta
    } else if (tipo === 'ADMIN') {
      await $fetch('/api/usuarios', { method: 'POST', headers, body: { ...credenciales, rol: tipo, datosPersonales } })
    } else {
      await $fetch('/api/mecanicos/con-cuenta', { method: 'POST', headers, body: { ...credenciales, mecanico: datosPersonales } })
    }
    form.password = ''
    form.confirmacion = ''
    alta.value = null
    enEdicion.value = null
    mensaje.value = editado ? 'Datos actualizados.' : tipo === 'ADMIN' ? 'Cuenta de administrador creada.' : 'Mecánico y cuenta de acceso creados.'
    await cargarPersonal()
  } catch (err) {
    errorAlta.value = authError(err, 'No pudimos guardar los datos.')
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
        <thead><tr><th>PERSONAL</th><th>CORREO</th><th>ROL</th><th>ESTADO</th><th>ACCIONES</th></tr></thead>
        <tbody>
          <tr v-for="usuario in filtrados" :key="usuario.id">
            <td><div class="client-cell"><strong>{{ nombreCompleto(usuario) || '—' }}</strong></div></td>
            <td>{{ usuario.email }}</td>
            <td>{{ usuario.dueno ? 'Dueño del taller' : roles[usuario.rol] }}</td>
            <td><span class="badge" :class="usuario.activo ? 'success' : 'neutral'">{{ usuario.activo ? 'Activo' : 'Inactivo' }}</span></td>
            <td>
              <div class="personal-actions">
                <button type="button" class="text-button" :disabled="guardando" @click="editar(usuario)">Editar</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
    <div v-else class="panel"><p class="muted">{{ search ? 'No se encontraron cuentas.' : 'No hay cuentas en el taller.' }}</p></div>

    <CommonModalDialog v-if="bajaPendiente" class="dialog" @close="cancelarBaja">
      <div class="dialog-header">
        <h2>Dar de baja</h2>
        <button type="button" class="icon-button" aria-label="Cerrar" :disabled="guardando" @click="cancelarBaja"><X :size="18" /></button>
      </div>
      <div class="detail-body">
        <p>¿Querés dar de baja a <strong>{{ nombreCompleto(bajaPendiente) || bajaPendiente.email }}</strong>?</p>
        <p class="muted">Se desactivará su acceso y se conservará su historial.</p>
        <p v-if="errorBaja" class="personal-error" role="alert">{{ errorBaja }}</p>
      </div>
      <footer class="modal-footer">
        <button type="button" class="button" :disabled="guardando" @click="cancelarBaja">Cancelar</button>
        <button type="button" class="button primary" :disabled="guardando" @click="confirmarBaja">Confirmar baja</button>
      </footer>
    </CommonModalDialog>

    <CommonFormPage v-if="alta || enEdicion">
      <div class="dialog-header">
        <h2>{{ enEdicion ? 'Editar datos' : alta === 'ADMIN' ? 'Nuevo administrador' : 'Nuevo mecánico' }}</h2>
        <button type="button" class="icon-button" aria-label="Cerrar" :disabled="guardando" @click="cerrar"><X :size="18" /></button>
      </div>
      <form class="entry-form" novalidate @submit.prevent="guardar">
        <div class="form-fields">
          <p class="muted">Los campos marcados con * son obligatorios.</p>
          <div v-for="grupo in gruposVisibles" :key="grupo[0].clave" :class="{ 'form-grid': grupo.length > 1 }">
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
                v-if="campo.clave !== 'confirmacion' && mostrarFormato(campo.clave) && errores[campo.clave]"
                :id="'formato-' + campo.clave"
              />
              <small v-else-if="campo.clave === 'confirmacion' && mostrarFormato(campo.clave) && errores.confirmacion"
                id="formato-confirmacion" class="personal-error">Las contraseñas no coinciden.</small>
            </label>
          </div>
          <p v-if="errorAlta" class="personal-error" role="alert">{{ errorAlta }}</p>
        </div>
        <footer class="modal-footer">
          <button v-if="enEdicion && puedeDarBaja(enEdicion)" type="button" class="text-button danger" style="margin-right: auto" :disabled="guardando" @click="pedirBaja(enEdicion)">Dar de baja</button>
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
