<script setup lang="ts">
defineProps<{ ready: boolean; hint?: string }>()
</script>

<template>
  <p v-if="!ready" class="muted" role="status">{{ hint || 'Primero seleccioná el cliente y su vehículo para completar los demás datos.' }}</p>
  <fieldset class="dependent-fields" :disabled="!ready" :inert="!ready" aria-label="Datos del formulario">
    <slot />
  </fieldset>
</template>

<style scoped>
.dependent-fields { display: contents; }
.dependent-fields:disabled :deep(> *) { opacity: .55; }
</style>
