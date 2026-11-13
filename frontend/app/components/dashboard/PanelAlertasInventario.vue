<script setup lang="ts">
import { Package, ArrowRight } from '@lucide/vue'

const { lowStock } = useDatabase()
</script>

<template>
  <section class="panel alerts-panel">
    <div class="panel-top">
      <h2><Package :size="18" /> Stock bajo <span class="count-bubble">{{ lowStock.length }}</span></h2>
    </div>
    <div class="alerts-body">
      <section v-if="lowStock.length">
        <p class="muted">{{ lowStock.length }} repuestos necesitan reposición.</p>
        <ul class="alert-items">
          <li v-for="p in lowStock.slice(0, 2)" :key="p.id">
            <strong>{{ p.name }}</strong>
            <small>{{ p.stock }} disponibles · mínimo {{ p.min }}</small>
          </li>
        </ul>
        <p v-if="lowStock.length > 2" class="muted">Y {{ lowStock.length - 2 }} más en el inventario.</p>
        <NuxtLink to="/inventario" class="text-button">Revisar repuestos <ArrowRight :size="16" /></NuxtLink>
      </section>
      <p v-if="!lowStock.length" class="muted">No hay repuestos con stock bajo.</p>
    </div>
  </section>
</template>

<style scoped>
.alerts-body { padding: 0 22px 22px; display: grid; gap: 22px; }
h2, h3 { display: flex; align-items: center; gap: 8px; }
h3 { font-size: 13px; margin-bottom: 8px; }
.alert-items { list-style: none; padding: 0; margin: 12px 0; }
.alert-items li { padding: 10px 0; border-bottom: 1px solid var(--line); }
.alert-items strong, .alert-items small { display: block; }
.alert-items small { color: var(--muted); margin-top: 4px; }
</style>
