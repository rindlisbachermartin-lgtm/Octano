<script setup lang="ts">
import { Download, Plus, RefreshCw, QrCode, ExternalLink } from 'lucide-vue-next'
import QRCode from 'qrcode'

const { db, availableQrs, generateQrBatch, vehicle } = useDatabase()
const { notify } = useWorkshopToast()

const qrImages = ref<Record<string, string>>({})
const isGenerating = ref(false)
const isDownloading = ref(false)
const filter = ref<'disponible' | 'asignado'>('disponible')
const selectedCodes = ref<string[]>([])
const generationError = ref('')
const assignmentCode = ref<string | null>(null)

const displayedQrs = computed(() => db.value.qrCodes.filter((item) => item.status === filter.value))
const selectedQrs = computed(() => displayedQrs.value.filter((item) => !item.printedAt && selectedCodes.value.includes(item.code)))
const canDownload = computed(() => selectedQrs.value.length === 16 && !isGenerating.value && !isDownloading.value)

function selectVisibleQrs() {
  selectedCodes.value = displayedQrs.value.filter((item) => !item.printedAt).slice(0, 16).map((item) => item.code)
}

function markPrinted() {
  if (!canDownload.value) return
  const items = [...selectedQrs.value]
  const printedAt = new Date().toISOString()
  for (const item of items) item.printedAt = printedAt
  selectedCodes.value = []
  notify(`${items.length} códigos QR marcados como impresos. No se pueden volver a descargar para imprimir.`)
}

watch(filter, selectVisibleQrs)

async function generateImages() {
  if (!import.meta.client) return
  isGenerating.value = true
  generationError.value = ''
  const origin = window.location.origin

  for (const item of db.value.qrCodes) {
    if (!qrImages.value[item.code]) {
      try {
        const url = `${origin}/qr/${item.code}`
        qrImages.value[item.code] = await QRCode.toDataURL(url, {
          margin: 4,
          width: 280,
          errorCorrectionLevel: 'M',
          color: { dark: '#000000', light: '#ffffff' }
        })
      } catch (e) {
        console.error('Error generating QR', e)
        generationError.value = 'No se pudieron preparar las vistas previas de algunos QR. Volvé a intentar.'
      }
    }
  }
  isGenerating.value = false
}

function handleGenerateBatch() {
  const newItems = generateQrBatch()
  filter.value = 'disponible'
  nextTick(() => { selectedCodes.value = newItems.map((item) => item.code) })
  notify(`Se generaron ${newItems.length} nuevos códigos QR disponibles.`)
}

async function handleDownload() {
  if (!import.meta.client || !canDownload.value) return
  const urls = selectedQrs.value.map((item) => `${window.location.origin}/qr/${item.code}`)
  isDownloading.value = true
  try {
    const [{ createQrTemplatePdf }, templateResponse] = await Promise.all([
      import('~/utils/qrTemplatePdf'),
      fetch('/graphics/qr-sticker-template.png'),
    ])
    if (!templateResponse.ok) throw new Error('No se pudo cargar el diseño del sticker.')
    const templateImage = new Uint8Array(await templateResponse.arrayBuffer())
    const doc = createQrTemplatePdf(urls, templateImage)
    doc.save('Octano-plantilla-QR.pdf')
  } catch (error) {
    console.error('Error downloading QR template', error)
    notify('No se pudo descargar el PDF. Volvé a intentar.')
  } finally {
    isDownloading.value = false
  }
}

watch(
  () => db.value.qrCodes.length,
  () => {
    generateImages()
  },
)

onMounted(() => {
  selectVisibleQrs()
  generateImages()
})
</script>

<template>
  <div class="page-content print-wrapper qr-template-page">
    <!-- Screen Header (hidden when printing) -->
    <div class="no-print">

      <section class="page-heading">
        <div>
          <div class="eyebrow">
            TALLER CENTRAL / CÓDIGOS QR
          </div>
          <h1>Códigos QR</h1>
          <p class="muted">
            Generá etiquetas para imprimir. Al escanearlas, abrís la ficha del vehículo o lo vinculás por patente si el QR está libre.
          </p>
        </div>
        <div class="actions-group">
          <button class="button outlined" :disabled="isGenerating" @click="handleGenerateBatch">
            <Plus :size="16" /> Generar 16 QR
          </button>
          <button class="button outlined" :disabled="!canDownload" @click="handleDownload">
            <Download :size="16" /> {{ isDownloading ? 'Preparando PDF…' : 'Descargar PDF' }}
          </button>
          <button class="button outlined" :disabled="!canDownload" @click="markPrinted">Marcar como impresos</button>
        </div>
      </section>

      <div class="list-toolbar">
        <div class="segmented" role="group" aria-label="Estado de los códigos QR">
          <button :class="{ selected: filter === 'disponible' }" :aria-pressed="filter === 'disponible'" @click="filter = 'disponible'">Disponibles <span>{{ availableQrs.length }}</span></button>
          <button :class="{ selected: filter === 'asignado' }" :aria-pressed="filter === 'asignado'" @click="filter = 'asignado'">Asignados <span>{{ db.qrCodes.length - availableQrs.length }}</span></button>
        </div>
        <span class="muted">{{ selectedQrs.length }} / 16 etiquetas seleccionadas</span>
        <button class="text-button" :disabled="isDownloading" @click="selectVisibleQrs">Seleccionar 16 QR</button>
        <button class="text-button" :disabled="!selectedCodes.length" @click="selectedCodes = []">Limpiar selección</button>
        <span class="muted print-tip">
          <strong>A4 · 16 por hoja</strong> · Stickers de 5,25 × 7,42 cm con el diseño de Octano y un QR único. Imprimir el PDF al 100 %, sin ajustar a página.
        </span>
      </div>
      <p v-if="generationError" class="muted" role="alert">{{ generationError }} <button class="text-button" @click="generateImages">Reintentar</button></p>
      <p v-if="selectedQrs.length !== 16" class="muted">Seleccioná exactamente 16 QR sin imprimir para descargar una hoja completa.</p>
      <p class="muted">Después de imprimir el PDF, seleccioná sus códigos y marcá «Marcar como impresos». Quedarán bloqueados para nuevas impresiones.</p>
    </div>

    <!-- Printable Sheet of Stickers -->
    <div class="printable-sheet">
      <div v-if="!displayedQrs.length" class="empty-state no-print">
        <QrCode :size="36" />
        <h3>{{ filter === 'disponible' ? 'No hay códigos QR disponibles' : 'No hay códigos QR asignados' }}</h3>
        <p>{{ filter === 'disponible' ? 'Generá una plantilla para imprimir nuevas etiquetas.' : 'Asigná los códigos disponibles a un vehículo desde esta sección o desde Vehículos.' }}</p>
        <button class="button outlined" style="margin-top: 1rem" :disabled="isGenerating" @click="handleGenerateBatch">
          <Plus :size="16" /> Generar 16 QR
        </button>
      </div>

      <div class="sticker-grid">
        <div
          v-for="item in displayedQrs"
          :key="item.code"
          class="sticker-card"
          :class="{ 'is-assigned': item.status === 'asignado', 'is-selected': !item.printedAt && selectedCodes.includes(item.code) }"
        >
          <div class="sticker-header">
            <label class="sticker-selection no-print"><input v-model="selectedCodes" type="checkbox" :value="item.code" :disabled="!!item.printedAt || isDownloading || (selectedQrs.length >= 16 && !selectedCodes.includes(item.code))" :aria-label="item.printedAt ? `QR ${item.code} ya impreso` : `Seleccionar QR ${item.code} para descargar`" /></label>
            <span class="sticker-code">{{ item.code }}</span>
            <span class="sticker-tag" :class="item.status">
              {{ item.status === 'asignado' ? 'Asignado' : 'Disponible' }}
            </span>
          </div>

          <div class="sticker-preview">
            <img src="/graphics/qr-sticker-template.png" alt="" class="sticker-design" />
            <div class="sticker-preview-qr">
              <img
                v-if="qrImages[item.code]"
                :src="qrImages[item.code]"
                :alt="`QR ${item.code}`"
                class="sticker-qr-img"
              />
              <div v-else class="qr-placeholder">
                <RefreshCw :size="16" class="spin" />
              </div>
            </div>
          </div>

            <div class="sticker-info">
              <span v-if="item.printedAt" class="badge neutral">Impreso · No se puede reimprimir</span>

              <div v-if="item.vehicleId" class="assigned-vehicle-info">
                <span class="plate small-plate">{{ vehicle(item.vehicleId)?.plate }}</span>
                <small>{{ vehicle(item.vehicleId)?.brand }} {{ vehicle(item.vehicleId)?.model }} · {{ vehicle(item.vehicleId)?.year }}</small>
              </div>
              <div v-else class="unassigned-badge">
                <small>Sin vehículo asignado</small>
              </div>
            </div>
          <button v-if="item.status === 'disponible'" type="button" class="button small no-print" style="margin-top: 10px" @click="assignmentCode = item.code">Asignar vehículo</button>
          <NuxtLink :to="`/qr/${item.code}`" target="_blank" rel="noopener" class="text-button sticker-open no-print">Abrir QR <ExternalLink :size="13" /></NuxtLink>
        </div>
      </div>
    </div>
    <VehiculosModalVincularQr
      :open="!!assignmentCode"
      :preselected-code="assignmentCode"
      @close="assignmentCode = null"
    />
  </div>
</template>

<style scoped>
.sticker-preview { position: relative; aspect-ratio: 52.5 / 74.2; background: #18181b; overflow: hidden; }
.sticker-design { display: block; width: 100%; height: 100%; }
.sticker-preview-qr { position: absolute; top: 19.5418%; left: 50%; transform: translateX(-50%); width: 55.2381%; aspect-ratio: 1; background: #fff; display: grid; place-items: center; }
.sticker-qr-img { display: block; width: 100%; height: 100%; }
.sticker-info { padding-top: 8px; }
.actions-group { flex-wrap: wrap; }
.brand-mini { display: inline-flex; align-items: center; gap: 5px; }
.brand-mini :deep(.octano-logo) { width: 18px; height: 19px; }
.sticker-selection { display: flex; align-items: center; }
.sticker-selection input { width: 16px; height: 16px; }
.sticker-open { margin-top: 10px; justify-content: flex-start; }
:global(html.dark .qr-template-page .empty-state h3) { color: #fff; }
.actions-group {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.print-tip {
  font-size: 11px;
}

.sticker-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
  margin-top: 1rem;
}

.sticker-card {
  background: #ffffff;
  border: 1.5px dashed #cbd5e1;
  border-radius: 12px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  box-shadow: 0 1px 3px rgba(0,0,0,0.03);
  position: relative;
  page-break-inside: avoid;
  break-inside: avoid;
}

.sticker-card.is-assigned {
  border-color: #94a3b8;
  background: #fafafa;
}

.sticker-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #f1f5f9;
  padding-bottom: 8px;
  margin-bottom: 8px;
}

.brand-mini {
  font-family: Public Sans, sans-serif;
  font-weight: 800;
  font-size: 13px;
  letter-spacing: -0.5px;
  color: #0f172a;
}
.brand-mini span {
  color: #2563eb;
}

.sticker-tag {
  font-size: 9px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 2px 6px;
  border-radius: 4px;
}
.sticker-tag.disponible {
  background: #ecfdf5;
  color: #059669;
}
.sticker-tag.asignado {
  background: #eff6ff;
  color: #2563eb;
}

.sticker-body {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 4px 0 10px;
}

.qr-canvas-holder {
  width: 96px;
  height: 96px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.qr-img {
  width: 90px;
  height: 90px;
  object-fit: contain;
}

.sticker-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.sticker-code {
  font-family: monospace;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #0f172a;
  background: #f1f5f9;
  padding: 3px 6px;
  border-radius: 4px;
  width: fit-content;
}

.sticker-instruction {
  font-size: 10px;
  line-height: 1.3;
  color: #64748b;
  margin: 0;
}

.assigned-vehicle-info {
  margin-top: 4px;
}
.assigned-vehicle-info small {
  display: block;
  font-size: 10px;
  color: #334155;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.unassigned-badge small {
  font-size: 10px;
  color: #059669;
  font-weight: 500;
}

.sticker-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px dashed #e2e8f0;
  padding-top: 8px;
  margin-top: auto;
  font-size: 9px;
  color: #94a3b8;
}

.cut-guide {
  font-size: 9px;
  color: #94a3b8;
}

.small-plate {
  font-size: 9px;
  padding: 2px 5px;
}

:global(html.dark .sticker-card) {
  background: #252528;
  border-color: rgba(255, 255, 255, 0.15);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
}
:global(html.dark .sticker-card.is-assigned) {
  background: #202023;
  border-color: rgba(255, 255, 255, 0.1);
}
:global(html.dark .sticker-header) {
  border-bottom-color: rgba(255, 255, 255, 0.08);
}
:global(html.dark .brand-mini) {
  color: #ffffff;
}
:global(html.dark .sticker-footer) {
  border-top-color: rgba(255, 255, 255, 0.08);
}
:global(html.dark .assigned-vehicle-info strong) {
  color: #ffffff;
}
:global(html.dark .assigned-vehicle-info small) {
  color: #98989d;
}
:global(html.dark .qr-canvas-holder) {
  background: #2c2c2e;
  border-color: rgba(255, 255, 255, 0.12);
}
:global(html.dark .sticker-code) {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}
:global(html.dark .sticker-tag.disponible) {
  background: rgba(48, 209, 88, 0.15);
  color: #30d158;
}
:global(html.dark .sticker-tag.asignado) {
  background: rgba(10, 132, 255, 0.15);
  color: #64d2ff;
}
:global(html.dark .sticker-instruction) {
  color: #8e8e93;
}


</style>
