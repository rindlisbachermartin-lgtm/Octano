<script setup lang="ts">
const props = withDefaults(defineProps<{ paperWidth?: number }>(), { paperWidth: 794 })
const container = ref<HTMLElement | null>(null)
const paper = ref<HTMLElement | null>(null)
const scale = ref(1)
const paperHeight = ref(props.paperWidth * Math.SQRT2)
let observer: ResizeObserver | undefined
let measureFrame = 0

function measure() {
  if (!container.value || !paper.value) return
  const width = container.value.clientWidth
  const height = paper.value.offsetHeight
  // A child mounts before its native dialog opens. Hidden measurements must not collapse the preview.
  if (width <= 0 || height <= 0) return
  scale.value = Math.min(1, width / props.paperWidth)
  paperHeight.value = height
}

onMounted(async () => {
  await nextTick()
  if (!container.value || !paper.value) return
  measure()
  measureFrame = requestAnimationFrame(measure)
  observer = new ResizeObserver(measure)
  if (container.value) observer.observe(container.value)
  if (paper.value) observer.observe(paper.value)
})
onBeforeUnmount(() => {
  observer?.disconnect()
  cancelAnimationFrame(measureFrame)
})
</script>

<template>
  <div ref="container" class="document-preview">
    <div class="document-preview-frame" :style="{ width: `${paperWidth * scale}px`, height: `${paperHeight * scale}px` }">
      <div ref="paper" class="document-preview-paper" :style="{ width: `${paperWidth}px`, transform: `scale(${scale})` }">
        <slot />
      </div>
    </div>
  </div>
</template>

<style scoped>
.document-preview { width: 100%; min-width: 0; overflow: hidden; }
.document-preview-frame { position: relative; margin: 0 auto; }
.document-preview-paper { position: absolute; top: 0; left: 0; transform-origin: top left; }
@media print {
  .document-preview { overflow: visible; }
  .document-preview-frame { width: auto !important; height: auto !important; }
  .document-preview-paper { position: static; width: 100% !important; transform: none !important; }
}
</style>
