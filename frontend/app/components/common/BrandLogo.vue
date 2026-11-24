<script setup lang="ts">
const props = defineProps<{ brand: string }>()
const logos: Record<string, string> = {
  volkswagen: 'volkswagen', vw: 'volkswagen', toyota: 'toyota', peugeot: 'peugeot',
  ford: 'ford', renault: 'renault', fiat: 'fiat', chevrolet: 'chevrolet', citroen: 'citroen',
  audi: 'audi', bmw: 'bmw', mercedes: 'mercedes', mercedesbenz: 'mercedes',
  honda: 'honda', hyundai: 'hyundai', kia: 'kia', nissan: 'nissan', suzuki: 'suzuki',
  jeep: 'jeep', ram: 'ram',
  alfaromeo: 'alfaromeo', baic: 'baic-logo.svg', chery: 'chery',
  chrysler: 'chrysler', daewoo: 'daewoo', daihatsu: 'daihatsu-logo.svg',
  dodge: 'dodge', ds: 'dsautomobiles', gmcchevette: 'gmc',
  hummer: 'hummer', isuzu: 'isuzu', iveco: 'iveco',
  landrover: 'landrover', mazda: 'mazda', mg: 'mg', mini: 'mini',
  mitsubishi: 'mitsubishi', opel: 'opel', porsche: 'porsche', rover: 'rover',
  scania: 'scania', seat: 'seat', smart: 'smart', subaru: 'subaru', volvo: 'volvo',
}
const logo = computed(() => {
  const key = props.brand.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]/g, '')
  const slug = logos[key]
  return slug ? `/icons/brands/${slug.includes('.') ? slug : `${slug}.svg`}` : null
})
</script>

<template>
  <span
    v-if="logo"
    class="brand-logo"
    role="img"
    :aria-label="`Logo de ${brand}`"
  >
    <img v-if="logo.includes('-logo.')" :src="logo" alt="" class="brand-logo-image">
    <span v-else class="brand-logo-symbol" :style="{ maskImage: `url(${logo})`, WebkitMaskImage: `url(${logo})` }" />
  </span>
</template>

<style scoped>
.brand-logo {
  display: block;
  flex: 0 0 56px;
  width: 56px;
  height: 48px;
  background: transparent;
}
.brand-logo-symbol {
  display: block;
  width: 100%;
  height: 100%;
  background: #64748b;
  mask-repeat: no-repeat;
  mask-position: center;
  mask-size: contain;
  -webkit-mask-repeat: no-repeat;
  -webkit-mask-position: center;
  -webkit-mask-size: contain;
}
.brand-logo-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: white;
  border-radius: 4px;
}
:global(html.dark .brand-logo-symbol) { background: #9ca3af; }
</style>
