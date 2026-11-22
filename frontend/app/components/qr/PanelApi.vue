<script setup lang="ts">
import { Plus, Download, Search, ExternalLink } from '@lucide/vue'
import QRCode from 'qrcode'
import { createQrTemplatePdf } from '~/utils/qrTemplatePdf'
import { authError } from '~/composables/useAuthApi'
import { estadosQr, seleccionQr, type CodigoQrApi, type PaginaQr, type EstadoQr } from '~/utils/qrApi'

const { authorization, puedeAdministrar } = useAuthApi()
const filtro = ref<EstadoQr>('DISPONIBLE')
const busqueda = ref('')
const consulta = useDebouncedValue(busqueda)
const impresion = ref('')
const pagina = ref(0)
const paginas = ref(0)
const total = ref(0)
const revision = ref(0)
const codigos = ref<CodigoQrApi[]>([])
const seleccion = ref<string[]>([])
const imagenes = ref<Record<string, string>>({})
const cargando = ref(false)
const ocupado = ref(false)
const error = ref('')
const mensaje = ref('')
const asignando = ref<string | null>(null)
const baja = ref<CodigoQrApi | null>(null)
const confirmarImpresion = ref(false)
const elegibles = computed(() => codigos.value.filter(c => c.estado !== 'INUTILIZADO' && !c.impresoEn))
const listaCompleta = computed(() => seleccion.value.length === 16 && !cargando.value && !ocupado.value)
let vigente = true
onBeforeUnmount(() => { vigente = false })

watch([filtro, consulta, impresion], () => { pagina.value = 0 })
watch([authorization, filtro, consulta, impresion, pagina, revision], async ([headers], _, onCleanup) => {
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  codigos.value = []
  seleccion.value = []
  imagenes.value = {}
  error.value = ''
  cargando.value = !!headers
  if (!headers) return
  try {
    const result = await $fetch<PaginaQr<CodigoQrApi>>('/api/codigos-qr', {
      headers, signal: controller.signal, query: { estado: filtro.value, q: consulta.value.trim(), impreso: impresion.value || undefined, pagina: pagina.value, tamano: 16 },
    })
    if (controller.signal.aborted) return
    codigos.value = result.contenido
    total.value = result.totalElementos
    paginas.value = result.totalPaginas
    const vistas = await Promise.all(result.contenido.map(async c => [c.codigo, await QRCode.toDataURL(c.urlPublica, { width: 280, margin: 4, errorCorrectionLevel: 'M' })] as const))
    if (!controller.signal.aborted) imagenes.value = Object.fromEntries(vistas)
  } catch (err) { if (!controller.signal.aborted) error.value = authError(err, 'No se pudieron cargar los códigos QR.') }
  finally { if (!controller.signal.aborted) cargando.value = false }
}, { immediate: true })

async function generar() {
  const headers = authorization.value
  if (!headers || !puedeAdministrar.value || ocupado.value) return
  ocupado.value = true
  error.value = ''
  try {
    await $fetch('/api/codigos-qr/lotes', { method: 'POST', headers })
    if (!vigente || authorization.value !== headers) return
    filtro.value = 'DISPONIBLE'; impresion.value = ''; busqueda.value = ''; pagina.value = 0; revision.value++
    mensaje.value = 'Se generaron 16 etiquetas. Seleccionalas para descargar la hoja.'
  } catch (err) { error.value = authError(err, 'No se pudo generar el lote.') }
  finally { ocupado.value = false }
}
async function descargar() {
  const headers = authorization.value
  if (!headers || !puedeAdministrar.value || !listaCompleta.value) return
  ocupado.value = true
  error.value = ''
  try {
    const etiquetas = await $fetch<CodigoQrApi[]>('/api/codigos-qr/plantillas', { method: 'POST', headers, body: seleccionQr(seleccion.value) })
    const response = await fetch('/graphics/qr-sticker-template.png')
    if (!response.ok) throw new Error('No se pudo cargar el diseño del sticker.')
    const imagen = new Uint8Array(await response.arrayBuffer())
    if (!vigente || authorization.value !== headers) return
    createQrTemplatePdf(etiquetas.map(c => c.urlPublica), imagen).save('Octano-plantilla-QR.pdf')
    mensaje.value = 'PDF descargado. Imprimilo al 100 % y luego marcá las etiquetas como impresas.'
  } catch (err) { error.value = authError(err, 'No se pudo preparar el PDF. La selección se conserva.') }
  finally { ocupado.value = false }
}
async function imprimir() {
  const headers = authorization.value
  if (!headers || !puedeAdministrar.value || !listaCompleta.value) return
  ocupado.value = true
  error.value = ''
  try {
    await $fetch('/api/codigos-qr/impresiones', { method: 'POST', headers, body: seleccionQr(seleccion.value) })
    if (!vigente || authorization.value !== headers) return
    confirmarImpresion.value = false
    revision.value++
    mensaje.value = 'Las 16 etiquetas quedaron marcadas como impresas.'
  } catch (err) { error.value = authError(err, 'No se pudo registrar la impresión.') }
  finally { ocupado.value = false }
}
async function desvincular() {
  const headers = authorization.value
  if (!headers || !baja.value || ocupado.value || !puedeAdministrar.value) return
  ocupado.value = true
  error.value = ''
  try {
    await $fetch(`/api/codigos-qr/${baja.value.codigo}/vehiculo`, { method: 'DELETE', headers })
    if (!vigente || authorization.value !== headers) return
    baja.value = null
    revision.value++
    mensaje.value = 'Etiqueta desvinculada e inutilizada definitivamente.'
  } catch (err) { error.value = authError(err, 'No se pudo desvincular la etiqueta.') }
  finally { ocupado.value = false }
}
</script>

<template>
  <div class="page-content qr-page">
    <section class="page-heading"><div><div class="eyebrow">TU TALLER / CÓDIGOS QR</div><h1>Códigos QR</h1><p class="muted">Etiquetas y fichas digitales de mantenimiento de tus vehículos.</p></div>
      <div v-if="puedeAdministrar" class="actions-group"><button class="button" :disabled="ocupado" @click="generar"><Plus :size="16" /> Generar 16 QR</button><button class="button primary" :disabled="!listaCompleta" @click="descargar"><Download :size="16" /> Descargar PDF</button><button class="button" :disabled="!listaCompleta" @click="confirmarImpresion = true">Marcar como impresos</button></div>
    </section>
    <fieldset :disabled="ocupado" class="qr-toolbar">
      <div class="segmented" aria-label="Estado de los códigos QR"><button v-for="(label, key) in estadosQr" :key="key" :class="{ selected: filtro === key }" :aria-pressed="filtro === key" @click="filtro = key">{{ label }}</button></div>
      <label class="search-box"><Search :size="16" /><input v-model="busqueda" maxlength="250" aria-label="Buscar códigos QR" placeholder="Código, patente o vehículo…" /></label>
      <label>Impresión<select v-model="impresion"><option value="">Todas</option><option value="false">Sin imprimir</option><option value="true">Impresos</option></select></label>
    </fieldset>
    <div v-if="puedeAdministrar" class="selection-tools"><span>{{ seleccion.length }} / 16 seleccionados</span><button class="text-button" :disabled="ocupado || cargando" @click="seleccion = elegibles.map(c => c.codigo)">Seleccionar esta página</button><button class="text-button" :disabled="ocupado || !seleccion.length" @click="seleccion = []">Limpiar selección</button></div>
    <p class="muted">A4 · 16 stickers de 5,25 × 7,42 cm por hoja. Imprimir al 100 %, sin ajustar a página.</p>
    <p v-if="mensaje" role="status">{{ mensaje }}</p>
    <p v-if="ocupado" role="status">Procesando etiquetas…</p>
    <p v-if="error" role="alert">{{ error }} <button v-if="!ocupado" class="text-button" @click="revision++">Actualizar lista</button></p>
    <p v-if="cargando" role="status">Cargando etiquetas…</p>
    <div v-else-if="!codigos.length && !error" class="empty-state"><h3>No hay etiquetas para estos filtros</h3><p>Generá un lote o probá otra búsqueda.</p></div>
    <div class="sticker-grid">
      <article v-for="c in codigos" :key="c.codigo" class="sticker-card" :class="{ selected: seleccion.includes(c.codigo) }">
        <header><input v-if="puedeAdministrar" v-model="seleccion" type="checkbox" :value="c.codigo" :aria-label="`Seleccionar ${c.codigo}`" :disabled="ocupado || !!c.impresoEn || c.estado === 'INUTILIZADO'" /><span class="badge neutral">{{ estadosQr[c.estado] }}</span></header>
        <div class="sticker-preview"><img src="/graphics/qr-sticker-template.png" alt="Etiqueta Octano" class="sticker-design" /><div class="sticker-preview-qr"><img v-if="imagenes[c.codigo]" :src="imagenes[c.codigo]" :alt="`QR ${c.codigo}`" /></div></div>
        <code>{{ c.codigo }}</code><p v-if="c.patente"><span class="plate">{{ c.patente }}</span> {{ vehicleLabel(c.marca, c.modelo, c.version) }}</p><p v-else class="muted">Sin vehículo asignado</p>
        <small v-if="c.impresoEn" class="muted">Impreso · No se puede reimprimir</small>
        <div class="card-actions"><button v-if="puedeAdministrar && c.estado === 'DISPONIBLE'" class="button small" :disabled="ocupado" @click="asignando = c.codigo">Asignar vehículo</button><button v-if="puedeAdministrar && c.estado === 'ASIGNADO'" class="text-button" :disabled="ocupado" @click="baja = c">Desvincular</button><a :href="c.urlPublica" target="_blank" rel="noopener" class="text-button">Abrir ficha <ExternalLink :size="13" /></a></div>
      </article>
    </div>
    <div v-if="total" class="selection-tools"><span>{{ total }} etiquetas · Página {{ pagina + 1 }} de {{ paginas }}</span><button class="button small" :disabled="ocupado || cargando || pagina === 0" @click="pagina--">Anterior</button><button class="button small" :disabled="ocupado || cargando || pagina + 1 >= paginas" @click="pagina++">Siguiente</button></div>
    <QrVincularApi v-if="asignando" :codigo="asignando" @close="asignando = null" @saved="asignando = null; revision++" />
    <CommonModalDialog v-if="baja || confirmarImpresion" class="dialog" @close="!ocupado && (baja = null, confirmarImpresion = false)">
      <div class="dialog-header"><h2>{{ baja ? 'Desvincular etiqueta' : 'Confirmar impresión' }}</h2></div><div class="detail-body"><p>{{ baja ? 'Esta etiqueta dejará de mostrar la ficha del vehículo y quedará inutilizada definitivamente.' : 'Confirmá que ya imprimiste las 16 etiquetas seleccionadas. No podrán incluirse en otra hoja.' }}</p><p v-if="error" role="alert">{{ error }}</p></div><footer class="modal-footer"><button class="button" :disabled="ocupado" @click="baja = null; confirmarImpresion = false">Volver</button><button class="button primary" :disabled="ocupado" @click="baja ? desvincular() : imprimir()">{{ ocupado ? 'Guardando…' : 'Confirmar' }}</button></footer>
    </CommonModalDialog>
  </div>
</template>

<style scoped>
.actions-group, .selection-tools, .qr-toolbar, .card-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.qr-toolbar { border: 0; padding: 0; margin-bottom: 14px; }
.qr-toolbar .search-box { flex: 1; min-width: 180px; }
.selection-tools { margin: 16px 0; font-size: 12px; }
.sticker-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 20px; margin: 20px 0; }
.sticker-card { border: 1px solid var(--line); border-radius: 12px; padding: 12px; background: var(--surface, #fff); min-width: 0; }
:global(html.dark .sticker-card) { background: #252528; }
.sticker-card.selected { border-color: #3b82f6; box-shadow: 0 0 0 1px #3b82f6; }
.sticker-card header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.sticker-card code { display: block; font-size: 10px; overflow-wrap: anywhere; margin-top: 10px; }
.sticker-card p { font-size: 12px; margin: 10px 0; }
.sticker-preview { position: relative; aspect-ratio: 52.5 / 74.2; overflow: hidden; background: #18181b; }
.sticker-design { width: 100%; height: 100%; display: block; }
.sticker-preview-qr { position: absolute; top: 19.5418%; left: 50%; transform: translateX(-50%); width: 55.2381%; aspect-ratio: 1; background: white; }
.sticker-preview-qr img { width: 100%; height: 100%; display: block; }
.card-actions { margin-top: 12px; }
</style>
