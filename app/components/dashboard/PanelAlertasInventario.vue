<script setup lang="ts">
import { Bell, Package, ArrowRight } from 'lucide-vue-next'

const { db, lowStock } = useDatabase()
const notices = computed(() => db.value.notifications.filter((n) => !n.read))
const count = computed(() => lowStock.value.length + notices.value.length)
</script>

<template>
  <section class="panel alerts-panel">
    <div class="panel-top">
      <h2><Bell :size="18" /> Alertas <span class="count-bubble">{{ count }}</span></h2>
    </div>
    <div class="alerts-body">
      <section v-if="lowStock.length">
        <h3><Package :size="16" /> Stock bajo</h3>
        <p class="muted">{{ lowStock.length }} repuestos necesitan reposición.</p>
        <ul class="alert-items">
          <li v-for="p in lowStock.slice(0, 2)" :key="p.id">
            <strong>{{ p.name }}</strong>
            <small>{{ p.stock }} disponibles · mínimo {{ p.min }}</small>
          </li>
        </ul>
        <p v-if="lowStock.length > 2" class="muted">Y {{ lowStock.length - 2 }} más en el inventario.</p>
        <NuxtLink to="/inventario" class="text-button">Revisar inventario <ArrowRight :size="16" /></NuxtLink>
      </section>
      <section v-if="notices.length">
        <h3>Avisos del taller</h3>
        <ul class="alert-items">
          <li v-for="n in notices.slice(0, 1)" :key="n.id">
            <strong>{{ n.title }}</strong>
            <small>{{ n.detail }}</small>
            <button class="text-button" @click="n.read = true">Marcar como leído</button>
          </li>
        </ul>
        <p v-if="notices.length > 1" class="muted">{{ notices.length - 1 }} avisos más en Notificaciones.</p>
      </section>
      <p v-if="!count" class="muted">No hay alertas pendientes.</p>
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
