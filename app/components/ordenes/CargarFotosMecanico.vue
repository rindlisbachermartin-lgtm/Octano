<script setup lang="ts">
import { Camera, ImagePlus } from 'lucide-vue-next'
import type { Order } from '~/types'
import { prepareOrderPhoto } from '~/utils/orderPhoto'

const props = defineProps<{ order: Order }>()
const auth = useOwnerAccount()
const { notify } = useWorkshopToast()
const cameraInput = ref<HTMLInputElement | null>(null)
const galleryInput = ref<HTMLInputElement | null>(null)
const sector = ref('Tablero / kilometraje')
const uploading = ref(false)
const error = ref('')
const allowed = computed(() => auth.isMechanic.value && props.order.mechanic === auth.mechanic.value && ['En espera', 'En proceso'].includes(props.order.status))
let batch = 0
watch(() => props.order.id, () => { batch++; uploading.value = false; error.value = '' })
onBeforeUnmount(() => { batch++ })

async function addPhotos(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!allowed.value || uploading.value || !files.length) return
  const order = props.order
  const photoSector = sector.value
  const currentBatch = ++batch
  uploading.value = true
  error.value = ''
  let added = 0
  try {
    for (const file of files) {
      if ((order.photos?.length || 0) >= 8) { error.value = 'Podés cargar hasta 8 fotos por orden.'; break }
      const data = await prepareOrderPhoto(file)
      if (currentBatch !== batch || !allowed.value) return
      order.photos ||= []
      order.photos.push({ sector: photoSector, data })
      added++
    }
  } catch (cause) {
    if (currentBatch === batch) error.value = cause instanceof Error ? cause.message : 'No se pudo cargar la foto. Intentá nuevamente.'
  } finally {
    if (currentBatch === batch) {
      uploading.value = false
      if (added) notify(added === 1 ? 'Foto guardada en la orden.' : `${added} fotos guardadas en la orden.`)
    }
  }
}
</script>

<template>
  <div v-if="allowed" class="mechanic-photo-upload">
    <label>Sector de la foto<select v-model="sector"><option>Tablero / kilometraje</option><option>Frente</option><option>Trasera</option><option>Lateral izquierdo</option><option>Lateral derecho</option><option>Detalle mecánico</option><option>Bajo chasis / motor</option></select></label>
    <div class="photo-actions">
      <button type="button" class="button" :disabled="uploading || (order.photos?.length || 0) >= 8" @click="cameraInput?.click()"><Camera :size="16" /> Sacar foto</button>
      <button type="button" class="button" :disabled="uploading || (order.photos?.length || 0) >= 8" @click="galleryInput?.click()"><ImagePlus :size="16" /> Elegir imágenes</button>
    </div>
    <input ref="cameraInput" class="sr-only" aria-label="Cámara del vehículo" type="file" accept="image/*" capture="environment" :disabled="uploading" @change="addPhotos" />
    <input ref="galleryInput" class="sr-only" aria-label="Imágenes del vehículo" type="file" accept="image/*" multiple :disabled="uploading" @change="addPhotos" />
    <p class="muted" aria-live="polite">{{ uploading ? 'Preparando imágenes…' : 'Las fotos se guardan automáticamente. Máximo 8 fotos.' }}</p>
    <p v-if="error" class="error-message" role="alert">{{ error }}</p>
  </div>
</template>

<style scoped>
.mechanic-photo-upload { display: grid; gap: 10px; margin: 12px 0; }
label { display: grid; gap: 6px; font-size: 13px; }
.photo-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.muted { font-size: 12px; margin: 0; }
</style>
