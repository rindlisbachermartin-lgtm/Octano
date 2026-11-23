<script setup lang="ts">
definePageMeta({ layout: 'blank' })
const route = useRoute()
const code = computed(() => String(route.params.code || '').trim().toUpperCase())
</script>
<template>
  <QrFichaPublica :codigo="code" />
</template>
