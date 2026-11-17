<script setup lang="ts">
import { Plus, Trash2, X } from '@lucide/vue'
import type { Part } from '~/types'

interface Compatibilidad {
  id: number
  marca: string
  modelo: string
  version: string | null
}

const props = defineProps<{ part: Part; editable: boolean }>()
const emit = defineEmits<{ close: []; updated: [] }>()
const auth = useAuthApi()
const catalogo = useCatalogoVehiculos()
const { marcaId, modeloId, versionId, marcas, modelos, versiones } = catalogo
const filas = ref<Compatibilidad[]>([])
const error = ref('')
const errorLista = ref('')
const cargando = ref(false)
const guardando = ref(false)
const recurso = `/api/repuestos/${props.part.id}/compatibilidades`

async function cargar() {
  const headers = auth.authorization.value
  if (!headers) return
  errorLista.value = ''
  cargando.value = true
  try {
    filas.value = await $fetch<Compatibilidad[]>(recurso, { headers })
  } catch (err) {
    errorLista.value = authError(err, 'No pudimos cargar las compatibilidades.')
  } finally {
    cargando.value = false
  }
}

async function guardar(id?: number) {
  const headers = auth.authorization.value
  if (!props.editable || !headers || guardando.value || cargando.value || (id === undefined && !modeloId.value)) return
  guardando.value = true
  error.value = ''
  try {
    if (id === undefined) {
      const fila = await $fetch<Compatibilidad>(recurso, {
        method: 'POST', headers, body: { modeloId: modeloId.value, versionId: versionId.value || null },
      })
      filas.value = [...filas.value, fila]
      versionId.value = ''
    } else {
      await $fetch(`${recurso}/${id}`, { method: 'DELETE', headers })
      filas.value = filas.value.filter(fila => fila.id !== id)
    }
    emit('updated')
  } catch (err) {
    error.value = authError(err, 'No pudimos guardar el cambio de compatibilidad.')
  } finally {
    guardando.value = false
  }
}

function cerrar() {
  if (!guardando.value) emit('close')
}

useModalEscape(() => true, cerrar)
onMounted(() => {
  void cargar()
  if (props.editable) void catalogo.abrir()
})
</script>

<template>
  <CommonFormPage>
    <div class="dialog-header">
      <h2>Compatibilidad del repuesto</h2>
      <button type="button" class="icon-button" aria-label="Cerrar" :disabled="guardando" @click="cerrar"><X :size="18" /></button>
    </div>
    <form class="entry-form" @submit.prevent="guardar()">
      <div class="form-fields">
        <p><strong>{{ part.name }}</strong> · {{ part.brand }} · {{ part.oem }}</p>
        <template v-if="editable">
          <div class="form-grid">
            <label>Marca
              <select v-model="marcaId" :disabled="guardando || catalogo.cargando.marcas">
                <option value="">Seleccionar marca</option>
                <option v-for="marca in marcas" :key="marca.id" :value="marca.id">{{ marca.nombre }}</option>
              </select>
            </label>
            <label>Modelo
              <select v-model="modeloId" :disabled="guardando || !marcaId || catalogo.cargando.modelos">
                <option value="">Seleccionar modelo</option>
                <option v-for="modelo in modelos" :key="modelo.id" :value="modelo.id">{{ modelo.nombre }}</option>
              </select>
            </label>
            <label>Versión
              <select v-model="versionId" :disabled="guardando || !modeloId || catalogo.cargando.versiones">
                <option value="">Todas las versiones</option>
                <option v-for="version in versiones" :key="version.id" :value="version.id">{{ version.nombre }}</option>
              </select>
            </label>
          </div>
          <template v-for="(aviso, nivel) in catalogo.errores" :key="nivel">
            <p v-if="aviso" role="alert">{{ aviso }} <button type="button" class="button" @click="catalogo.cargar(nivel)">Reintentar</button></p>
          </template>
          <button type="submit" class="button" :disabled="!modeloId || guardando || cargando || !!errorLista || (versionId !== '' && (catalogo.cargando.versiones || !!catalogo.errores.versiones))"><Plus :size="16" /> Agregar compatibilidad</button>
        </template>
        <p v-if="error" role="alert">{{ error }}</p>
        <fieldset>
          <legend>Compatibilidades registradas</legend>
          <p v-if="cargando" role="status">Cargando compatibilidades…</p>
          <p v-else-if="errorLista" role="alert">{{ errorLista }} <button type="button" class="button" @click="cargar">Reintentar</button></p>
          <p v-else-if="!filas.length" class="muted">Uso general. Compatible con todas las marcas, modelos y versiones.</p>
          <div v-for="fila in filas" :key="fila.id" class="task-row">
            <span>{{ fila.marca }} · {{ fila.modelo }} · {{ fila.version || 'Todas las versiones' }}</span>
            <button v-if="editable" type="button" class="icon-button" :disabled="guardando || cargando" :aria-label="`Quitar compatibilidad con ${fila.marca} ${fila.modelo}`" @click="guardar(fila.id)"><Trash2 :size="16" /></button>
          </div>
        </fieldset>
      </div>
      <footer class="modal-footer"><button type="button" class="button" :disabled="guardando" @click="cerrar">Cerrar</button></footer>
    </form>
  </CommonFormPage>
</template>
