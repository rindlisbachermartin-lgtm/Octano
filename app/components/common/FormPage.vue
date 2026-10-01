<script setup lang="ts">
defineOptions({ inheritAttrs: false })
const panel = ref<HTMLElement | null>(null)
let previousFocus: HTMLElement | null = null
let previousScroll = 0
onMounted(() => {
  previousFocus = document.activeElement as HTMLElement | null
  previousScroll = window.scrollY
  nextTick(() => {
    window.scrollTo({ top: 0 })
    panel.value?.focus()
  })
})
onBeforeUnmount(() => {
  nextTick(() => {
    window.scrollTo({ top: previousScroll })
    previousFocus?.focus({ preventScroll: true })
  })
})
</script>

<template>
  <Teleport to=".page-content" defer>
    <section ref="panel" v-bind="$attrs" class="form-page panel" tabindex="-1" aria-label="Formulario">
      <slot />
    </section>
  </Teleport>
</template>
