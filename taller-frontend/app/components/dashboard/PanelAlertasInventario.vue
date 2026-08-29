<template>
  <div class="dashboard-panel inventario-panel">
    <div class="panel-header">
      <h3 class="panel-title flex-title">
        <span class="material-symbols-outlined icon-error">warning</span>
        <span>Alertas de Inventario</span>
      </h3>
    </div>

    <div class="panel-scroll-content alerts-list">
      <div v-for="alerta in alertas" :key="alerta.id" class="alert-item-card">
        <div class="alert-info">
          <p class="alert-item-name">{{ alerta.nombre }}</p>
          <p class="alert-item-stock">{{ alerta.stockTexto }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
export interface ItemAlertaStock {
  id: number
  nombre: string
  stockTexto: string
  unidadesRestantes: number
}

defineProps<{
  alertas: ItemAlertaStock[]
}>()
</script>

<style scoped>
.dashboard-panel {
  background-color: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--border-radius-md);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  height: 400px;
}

.panel-header {
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-subtle);
  background-color: #ffffff;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
}

.flex-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-error {
  color: #ba1a1a;
  font-size: 19px;
}

.panel-scroll-content {
  overflow-y: auto;
  flex: 1;
}

.alerts-list {
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.alert-item-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--border-radius-sm);
  background-color: #ffffff;
}

.alert-info {
  display: flex;
  flex-direction: column;
}

.alert-item-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
}

.alert-item-stock {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}
</style>
