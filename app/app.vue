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

const route = useRoute()
const { toast, dismissToast } = useToast()

// Keyboard vs pointer detection for animation preferences
if (import.meta.client) {
  document.addEventListener('keydown', () =>
    document.documentElement.setAttribute('data-keyboard', '')
  )
  document.addEventListener('pointerdown', () =>
    document.documentElement.removeAttribute('data-keyboard')
  )
}
</script>
