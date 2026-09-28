<template>
  <NuxtLayout>
    <NuxtPage />
  </NuxtLayout>
  <Transition name="toast">
    <div v-if="toast" class="toast-message" role="status">
      <span><Check :size="17" /></span>{{ toast }}
      <button aria-label="Cerrar aviso" @click="dismissToast">
        <X :size="15" />
      </button>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { Check, X } from 'lucide-vue-next'

const { toast, dismissToast } = useWorkshopToast()

// Keyboard vs pointer detection for animation preferences
const onKeydown = () => document.documentElement.setAttribute('data-keyboard', '')
const onPointerdown = () => document.documentElement.removeAttribute('data-keyboard')

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  document.addEventListener('pointerdown', onPointerdown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.removeEventListener('pointerdown', onPointerdown)
})
</script>
