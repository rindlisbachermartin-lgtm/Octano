<script setup lang="ts">
import { Bell } from 'lucide-vue-next'
const { db } = useDatabase()
const unreadCount = computed(() => db.value.notifications.filter(item => !item.read).length)
</script>

<template>
  <section class="panel dashboard-notifications" aria-label="Notificaciones del taller">
    <div class="panel-top">
      <h2><Bell :size="18" /> Notificaciones <span class="count-bubble">{{ unreadCount }}</span></h2>
    </div>
    <CommonListaNotificaciones class="notification-body" />
  </section>
</template>

<style scoped>
h2 { display: flex; align-items: center; gap: 8px; }
.notification-body { padding: 0 22px 22px; }
</style>
