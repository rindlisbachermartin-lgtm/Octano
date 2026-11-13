<script setup lang="ts">
const auth = useAuthApi()
</script>

<template>
  <section v-if="auth.user.value">
    <h1>Mis órdenes de trabajo</h1>
    <p>Las órdenes de tu taller estarán disponibles cuando se conecte el módulo de órdenes.</p>
  </section>
  <MecanicoPanelMecanico v-else mechanic="Nicolás" />
</template>
