<script setup lang="ts">
const isDark = defineModel<boolean>({ required: true })
const inputId = useId()
const maskId = useId()
</script>

<template>
  <!-- From Uiverse.io by Type-Delta (Josh Comeau Sun/Moon pure CSS clone). -->
  <label :for="inputId" class="themeToggle st-sunMoonThemeToggleBtn" title="Cambiar tema claro / oscuro">
    <input :id="inputId" v-model="isDark" type="checkbox" class="themeToggleInput" aria-label="Cambiar tema claro / oscuro" />
    <svg width="18" height="18" viewBox="0 0 20 20" fill="currentColor" stroke="none" aria-hidden="true">
      <mask :id="maskId">
        <rect x="0" y="0" width="20" height="20" fill="white" />
        <circle cx="11" cy="3" r="8" fill="black" />
      </mask>
      <circle class="sunMoon" cx="10" cy="10" r="8" :mask="`url(#${maskId})`" />
      <g>
        <circle class="sunRay sunRay1" cx="18" cy="10" r="1.5" />
        <circle class="sunRay sunRay2" cx="14" cy="16.928" r="1.5" />
        <circle class="sunRay sunRay3" cx="6" cy="16.928" r="1.5" />
        <circle class="sunRay sunRay4" cx="2" cy="10" r="1.5" />
        <circle class="sunRay sunRay5" cx="6" cy="3.1718" r="1.5" />
        <circle class="sunRay sunRay6" cx="14" cy="3.1718" r="1.5" />
      </g>
    </svg>
  </label>
</template>

<style scoped>
.themeToggle:has(input:focus-visible) { outline: 3px solid #3b82f6; outline-offset: 4px; border-radius: 6px; }
</style>
