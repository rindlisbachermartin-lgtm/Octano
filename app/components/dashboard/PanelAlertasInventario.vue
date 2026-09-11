<script setup lang="ts">
import { Package, ArrowRight } from 'lucide-vue-next'

const { lowStock } = useDatabase()
</script>

<template>
  <section class="stock-card">
    <div class="stock-top">
      <span class="stock-icon"><Package :size="20" /></span>
      <span class="eyebrow">UN VISTAZO AL STOCK</span>
      <span class="count-bubble">{{ lowStock.length }}</span>
    </div>
    <h2>Stock bajo</h2>
    <p>
      {{ lowStock.length }} repuestos necesitan reposición.
    </p>
    <div class="stock-chips">
      <span v-for="p in lowStock" :key="p.id">
        {{ p.name }} ({{ p.stock }}/{{ p.min }})
      </span>
    </div>
    <NuxtLink to="/inventario" class="button white">
      Revisar inventario <ArrowRight :size="16" />
    </NuxtLink>
  </section>
</template>
