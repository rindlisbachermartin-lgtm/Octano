<script setup lang="ts">
const auth = useAuthApi()
</script>

<template>
  <OrdenesPanelApi v-if="auth.user.value" />
  <MecanicoPanelMecanico v-else mechanic="Nicolás" />
</template>
