<script setup lang="ts">
import { Trash2, X } from 'lucide-vue-next'
import type { Order, Photo } from '~/types'

const props = defineProps<{ order: Order }>()
const auth = useOwnerAccount()
const { notify } = useWorkshopToast()
const preview = ref<Photo | null>(null)
const canRemove = computed(() => auth.isMechanic.value && props.order.mechanic === auth.mechanic.value && ['En espera', 'En proceso'].includes(props.order.status))
watch(() => props.order.id, () => { preview.value = null })

function remove(photo: Photo) {
  if (!canRemove.value) return
  const index = props.order.photos.indexOf(photo)
  if (index < 0) return
  props.order.photos.splice(index, 1)
  if (preview.value === photo) preview.value = null
  notify('Foto eliminada de la orden.')
}
</script>

<template>
  <div v-if="order.photos?.length" class="photo-grid">
    <figure v-for="(photo, index) in order.photos" :key="index">
      <button type="button" class="photo-preview-button" :aria-label="`Ver foto: ${photo.sector}`" @click="preview = photo">
        <img :src="photo.data" :alt="photo.sector" />
      </button>
      <figcaption>{{ photo.sector }}</figcaption>
      <button v-if="canRemove" type="button" class="button small remove-photo" :aria-label="`Eliminar foto: ${photo.sector}`" @click="remove(photo)"><Trash2 :size="14" /> Eliminar</button>
    </figure>
  </div>
  <p v-else class="muted empty-photos">El mecánico aún no cargó fotografías para este vehículo.</p>

  <CommonModalDialog v-if="preview" class="dialog photo-viewer" aria-label="Vista ampliada de la foto" @close="preview = null">
    <div class="dialog-header"><h2>{{ preview.sector }}</h2><button type="button" class="icon-button" aria-label="Cerrar foto" @click="preview = null"><X :size="18" /></button></div>
    <img class="full-photo" :src="preview.data" :alt="preview.sector" />
    <footer class="modal-footer"><button v-if="canRemove" type="button" class="button" @click="remove(preview)"><Trash2 :size="16" /> Eliminar foto</button><button type="button" class="button" @click="preview = null">Cerrar</button></footer>
  </CommonModalDialog>
</template>

<style scoped>
.photo-preview-button { display: block; width: 100%; padding: 0; cursor: zoom-in; }
.remove-photo { margin: 0 8px 8px; }
.empty-photos { font-size: 13px; }
.photo-viewer { width: min(800px, calc(100vw - 24px)); }
.full-photo { display: block; width: 100%; max-height: 65dvh; object-fit: contain; background: #18181b; }
</style>
