<script setup lang="ts">
const modules = [
  { name: 'Panel general', image: 'dashboard' },
  { name: 'Agenda', image: 'agenda' },
  { name: 'Órdenes de trabajo', image: 'ordenes' },
  { name: 'Clientes', image: 'clientes' },
  { name: 'Vehículos', image: 'vehiculos' },
  { name: 'Inventario', image: 'inventario' },
  { name: 'Presupuestos', image: 'presupuestos' },
  { name: 'Facturación', image: 'facturacion' },
]
const current = ref(0)
const module = computed(() => modules[current.value]!)
let timer: ReturnType<typeof setInterval> | undefined

function updateTimer() {
  if (timer) clearInterval(timer)
  timer = undefined
  if (!document.hidden) {
    timer = setInterval(() => {
      current.value = (current.value + 1) % modules.length
    }, 5000)
  }
}

onMounted(() => {
  for (const item of modules) {
    const image = new Image()
    image.src = `/auth/${item.image}-preview.png`
  }
  updateTimer()
  document.addEventListener('visibilitychange', updateTimer)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  document.removeEventListener('visibilitychange', updateTimer)
})
</script>

<template>
  <figure class="auth-system-preview" aria-label="Capturas de los módulos de Octano" aria-live="off">
    <div class="preview-frame">
      <Transition name="module-preview">
        <img :key="module.image" :src="`/auth/${module.image}-preview.png`" :alt="`Captura real del módulo ${module.name} de Octano.`" width="1280" height="720" />
      </Transition>
    </div>
    <figcaption>
      <span>{{ module.name }}</span>
      <span class="preview-count">{{ current + 1 }} / {{ modules.length }}</span>
    </figcaption>
  </figure>
</template>

<style scoped>
.auth-system-preview { margin: 42px -18px 0; }
.preview-frame { position: relative; aspect-ratio: 16 / 9; overflow: hidden; border-radius: 12px; border: 1px solid #ffffff1a; box-shadow: 0 18px 45px #0003; background: #18181a; }
.preview-frame img { position: absolute; inset: 0; display: block; width: 100%; height: 100%; object-fit: cover; }
figcaption { display: flex; align-items: center; justify-content: space-between; color: #b3c1c9; font-size: 11px; margin: 13px 4px 0; }
.preview-count { color: #7e929f; font-size: 10px; font-variant-numeric: tabular-nums; }
.module-preview-enter-active, .module-preview-leave-active { transition: opacity 250ms var(--ease), transform 250ms var(--ease); }
.module-preview-enter-active { z-index: 1; }
.module-preview-enter-from { opacity: 0; transform: translateX(3%); }
.module-preview-leave-to { opacity: 0; transform: translateX(-3%); }
@media (prefers-reduced-motion: reduce) {
  .module-preview-enter-active, .module-preview-leave-active { transition: opacity 150ms var(--ease); }
  .module-preview-enter-from, .module-preview-leave-to { transform: none; }
}
</style>
