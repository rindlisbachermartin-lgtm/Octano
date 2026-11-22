<script setup lang="ts">
definePageMeta({ layout: 'blank' })
const route = useRoute()
const code = computed(() => String(route.params.code || '').trim().toUpperCase())
const esDemo = computed(() => route.query.demo === '1')
</script>
<template>
  <QrResolucionDemo v-if="esDemo" />
  <QrFichaPublica v-else :codigo="code" />
</template>
