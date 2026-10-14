<script setup lang="ts">
const { db } = useDatabase()
const unreadCount = computed(() => db.value.notifications.filter(item => !item.read).length)
function markAllRead() {
  db.value.notifications.forEach(item => { item.read = true })
}
</script>

<template>
  <div class="notification-content">
    <button type="button" class="text-button notification-read-all" :disabled="!unreadCount" @click="markAllRead">Marcar todas como leídas</button>
    <div v-if="db.notifications.length" class="notification-list">
      <article v-for="item in db.notifications" :key="item.id" class="notification-item" :class="{ unread: !item.read }">
        <strong>{{ item.title }}</strong>
        <p>{{ item.detail }}</p>
        <button v-if="!item.read" type="button" class="text-button" :aria-label="`Marcar como leída: ${item.title}`" @click="item.read = true">Marcar como leída</button>
        <small v-else class="muted">Leída</small>
      </article>
    </div>
    <p v-else class="muted">No tenés notificaciones.</p>
  </div>
</template>

<style scoped>
.notification-content { display: grid; gap: 12px; }
.notification-read-all { justify-self: start; }
.notification-list { display: grid; gap: 10px; max-height: 360px; overflow-y: auto; }
.notification-item { padding: 12px; border: 1px solid var(--line); border-radius: 8px; min-width: 0; overflow-wrap: anywhere; }
.notification-item.unread { background: color-mix(in srgb, var(--blue) 8%, transparent); }
.notification-item strong { display: block; font-size: 12px; }
.notification-item p { margin: 6px 0 10px; color: var(--muted); font-size: 11px; line-height: 1.5; }
.notification-item .text-button { font-size: 11px; }
</style>
