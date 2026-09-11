<script setup lang="ts">
import { Printer, Plus, ArrowLeft, RefreshCw, CheckCircle2, QrCode } from 'lucide-vue-next'
import QRCode from 'qrcode'
import type { QrItem } from '~/types'

const { db, availableQrs, generateQrBatch, vehicle } = useDatabase()
const { notify } = useToast()

const filterMode = ref<'disponibles' | 'todos'>('disponibles')
const qrImages = ref<Record<string, string>>({})
const isGenerating = ref(false)

const displayedQrs = computed(() => {
  if (filterMode.value === 'disponibles') {
    return db.value.qrCodes.filter((q) => q.status === 'disponible')
  }
  return db.value.qrCodes
})

async function generateImages() {
  if (!import.meta.client) return
  isGenerating.value = true
  const origin = window.location.origin
  
  for (const item of db.value.qrCodes) {
    if (!qrImages.value[item.code]) {
      try {
        const url = `${origin}/qr/${item.code}`
        qrImages.value[item.code] = await QRCode.toDataURL(url, {
          margin: 1,
          width: 140,
          color: { dark: '#0f172a', light: '#ffffff' }
        })
      } catch (e) {
        console.error('Error generating QR', e)
      }
    }
  }
  isGenerating.value = false
}

function handleGenerateBatch() {
  const newItems = generateQrBatch(12)
  notify(`Se generaron ${newItems.length} nuevos códigos QR disponibles.`)
  generateImages()
}

function handlePrint() {
  if (import.meta.client) {
    window.print()
  }
}

watch(
  () => db.value.qrCodes.length,
  () => {
    generateImages()
  },
  { immediate: true }
)

onMounted(() => {
  generateImages()
})
</script>

<template>
  <div class="page-content print-wrapper">
    <!-- Screen Header (hidden when printing) -->
    <div class="no-print">
      <div class="breadcrumb" style="margin-bottom: 1.5rem">
        <NuxtLink to="/vehiculos" class="text-button">
          <ArrowLeft :size="16" /> Volver a Vehículos
        </NuxtLink>
      </div>

      <section class="page-heading">
        <div>
          <div class="eyebrow">
            <span class="tiny-star">✳</span>
            TALLER CENTRAL / ETIQUETAS QR
          </div>
          <h1>Plantilla de códigos QR listos para imprimir</h1>
          <p class="muted">
            Imprimí estos stickers para pegarlos en los vehículos o llaveros. Al escanearlos podrás vincularlos directamente a una patente.
          </p>
        </div>
        <div class="actions-group">
          <button class="button outlined" @click="handleGenerateBatch">
            <Plus :size="16" /> Generar nuevo lote (+12)
          </button>
          <button class="button primary" @click="handlePrint">
            <Printer :size="16" /> Imprimir plantilla
          </button>
        </div>
      </section>

      <div class="list-toolbar">
        <div class="filter-tabs">
          <button
            :class="{ active: filterMode === 'disponibles' }"
            @click="filterMode = 'disponibles'"
          >
            Solo disponibles <span>{{ availableQrs.length }}</span>
          </button>
          <button
            :class="{ active: filterMode === 'todos' }"
            @click="filterMode = 'todos'"
          >
            Todos los generados <span>{{ db.qrCodes.length }}</span>
          </button>
        </div>
        <span class="muted print-tip">
          Consejo: Configurar la impresora en formato <strong>A4</strong> con márgenes normales o mínimos.
        </span>
      </div>
    </div>

    <!-- Printable Sheet of Stickers -->
    <div class="printable-sheet">
      <div v-if="!displayedQrs.length" class="empty-state no-print">
        <QrCode :size="36" />
        <h3>No hay códigos QR disponibles</h3>
        <p>Generá un nuevo lote para imprimir una nueva plantilla de stickers.</p>
        <button class="button primary" style="margin-top: 1rem" @click="handleGenerateBatch">
          <Plus :size="16" /> Generar lote (+12)
        </button>
      </div>

      <div class="sticker-grid">
        <div
          v-for="item in displayedQrs"
          :key="item.code"
          class="sticker-card"
          :class="{ 'is-assigned': item.status === 'asignado' }"
        >
          <div class="sticker-header">
            <span class="brand-mini">o<span>·</span> octano</span>
            <span class="sticker-tag" :class="item.status">
              {{ item.status === 'asignado' ? 'Asignado' : 'Disponible' }}
            </span>
          </div>

          <div class="sticker-body">
            <div class="qr-canvas-holder">
              <img
                v-if="qrImages[item.code]"
                :src="qrImages[item.code]"
                :alt="`QR ${item.code}`"
                class="qr-img"
              />
              <div v-else class="qr-placeholder">
                <RefreshCw :size="16" class="spin" />
              </div>
            </div>

            <div class="sticker-info">
              <span class="sticker-code">{{ item.code }}</span>
              <p class="sticker-instruction">
                Escaneá para historial o vinculación
              </p>
              
              <div v-if="item.vehicleId" class="assigned-vehicle-info">
                <span class="plate small-plate">{{ vehicle(item.vehicleId).plate }}</span>
                <small>{{ vehicle(item.vehicleId).brand }} {{ vehicle(item.vehicleId).model }}</small>
              </div>
              <div v-else class="unassigned-badge">
                <small>Sin vehículo asignado</small>
              </div>
            </div>
          </div>

          <div class="sticker-footer">
            <small>Taller Central · Gestión Digital</small>
            <span class="cut-guide">✄ Recortar</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
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
  font-family: Manrope, sans-serif;
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

:global(html.dark) .sticker-card {
  background: #252528;
  border-color: rgba(255, 255, 255, 0.15);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
}
:global(html.dark) .sticker-card.is-assigned {
  background: #202023;
  border-color: rgba(255, 255, 255, 0.1);
}
:global(html.dark) .sticker-header {
  border-bottom-color: rgba(255, 255, 255, 0.08);
}
:global(html.dark) .brand-mini {
  color: #ffffff;
}
:global(html.dark) .sticker-footer {
  border-top-color: rgba(255, 255, 255, 0.08);
}
:global(html.dark) .assigned-vehicle-info strong {
  color: #ffffff;
}
:global(html.dark) .assigned-vehicle-info small {
  color: #98989d;
}
:global(html.dark) .qr-canvas-holder {
  background: #2c2c2e;
  border-color: rgba(255, 255, 255, 0.12);
}
:global(html.dark) .sticker-code {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}
:global(html.dark) .sticker-tag.disponible {
  background: rgba(48, 209, 88, 0.15);
  color: #30d158;
}
:global(html.dark) .sticker-tag.asignado {
  background: rgba(10, 132, 255, 0.15);
  color: #64d2ff;
}
:global(html.dark) .sticker-instruction {
  color: #8e8e93;
}


/* PRINT STYLES */
@media print {
  body {
    background: #ffffff !important;
  }
  .no-print,
  :deep(.sidebar),
  :deep(.topbar),
  :deep(.sidebar-backdrop) {
    display: none !important;
  }
  :deep(.main) {
    margin-left: 0 !important;
    width: 100% !important;
  }
  .print-wrapper {
    padding: 0 !important;
    max-width: 100% !important;
  }
  .sticker-grid {
    grid-template-columns: repeat(3, 1fr) !important;
    gap: 12px !important;
    margin: 0 !important;
  }
  .sticker-card {
    border: 1.5px dashed #64748b !important;
    box-shadow: none !important;
    padding: 10px !important;
    background: #fff !important;
    break-inside: avoid !important;
  }
  .sticker-tag {
    border: 1px solid #cbd5e1;
  }
}
</style>
