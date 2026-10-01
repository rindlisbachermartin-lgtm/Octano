<script setup lang="ts">
const props = defineProps<{ brand: string }>()
const logos: Record<string, string> = {
  volkswagen: 'volkswagen', vw: 'volkswagen', toyota: 'toyota', peugeot: 'peugeot',
  ford: 'ford', renault: 'renault', fiat: 'fiat', chevrolet: 'chevrolet', citroen: 'citroen',
  audi: 'audi', bmw: 'bmw', mercedes: 'mercedes', mercedesbenz: 'mercedes',
  honda: 'honda', hyundai: 'hyundai', kia: 'kia', nissan: 'nissan', suzuki: 'suzuki',
  jeep: 'jeep', ram: 'ram',
}
const logo = computed(() => {
  const key = props.brand.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]/g, '')
  const slug = logos[key]
  return slug ? `/icons/brands/${slug}.svg` : null
})
</script>

<template>
  <span
    v-if="logo"
    class="brand-logo"
    role="img"
    :aria-label="`Logo de ${brand}`"
    :style="{ maskImage: `url(${logo})`, WebkitMaskImage: `url(${logo})` }"
  />
</template>

<style scoped>
.brand-logo {
  display: block;
  flex: 0 0 56px;
  width: 56px;
  height: 48px;
  background: #9ca3af;
  mask-repeat: no-repeat;
  mask-position: center;
  mask-size: contain;
  -webkit-mask-repeat: no-repeat;
  -webkit-mask-position: center;
  -webkit-mask-size: contain;
}
:global(html.dark .brand-logo) { background: #71717a; }
</style>
