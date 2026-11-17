<script setup lang="ts">
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement | null>(null)
let pointerStartedOutside = false

function isOutside(event: MouseEvent) {
  const element = dialog.value
  if (!element || event.target !== element) return false
  const rect = element.getBoundingClientRect()
  return event.clientX < rect.left || event.clientX > rect.right
    || event.clientY < rect.top || event.clientY > rect.bottom
}

function dismiss(event: MouseEvent) {
  if (pointerStartedOutside && isOutside(event)) emit('close')
  pointerStartedOutside = false
}

onMounted(() => dialog.value?.showModal())
onBeforeUnmount(() => dialog.value?.close())
</script>

<template>
  <dialog
    ref="dialog"
    @cancel.prevent.stop="emit('close')"
    @pointerdown="pointerStartedOutside = isOutside($event)"
    @click="dismiss"
  >
    <slot />
  </dialog>
</template>
