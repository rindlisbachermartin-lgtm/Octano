<template>
  <div v-if="cliente" class="modal-backdrop" @click.self="cerrarModal">
    <div class="modal-card">
      <div class="modal-header">
        <h3 class="modal-title">
          <span class="material-symbols-outlined icon-modal">account_box</span>
          <span>Ficha de Cliente: {{ cliente.nombre }}</span>
        </h3>
        <button class="btn-close-modal" @click="cerrarModal">
          <span class="material-symbols-outlined">close</span>
        </button>
      </div>

      <div class="modal-body">
        <div class="client-detail-grid">
          <div class="detail-item">
            <span class="detail-label">CUIT / DNI</span>
            <span class="detail-value">{{ cliente.cuit }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Teléfono</span>
            <span class="detail-value">{{ cliente.telefono }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Email</span>
            <span class="detail-value">{{ cliente.email }}</span>
          </div>
          <div class="detail-item" v-if="cliente.direccion">
            <span class="detail-label">Dirección</span>
            <span class="detail-value">{{ cliente.direccion }}</span>
          </div>
        </div>

        <div class="vehicle-list-wrapper">
          <h4 class="section-subtitle">
            <span class="material-symbols-outlined">directions_car</span>
            <span>Vehículos Asociados</span>
          </h4>

          <div class="vehicle-card-item">
            <div class="vehicle-icon-box">
              <span class="material-symbols-outlined">directions_car</span>
            </div>
            <div class="vehicle-info">
              <div class="vh-model">{{ cliente.vehiculoPrincipal || 'Sin vehículo asignado' }}</div>
              <div class="vh-meta">
                Patente: <strong>{{ cliente.patentePrincipal || 'S/P' }}</strong> • Último servicio: {{ cliente.ultimaVisita || 'Sin registros' }}
              </div>
            </div>
            <NuxtLink 
              v-if="cliente.patentePrincipal && cliente.patentePrincipal !== 'S/P'" 
              :to="`/ficha/${cliente.patentePrincipal}`" 
              class="btn-outline-blue"
            >
              <span class="material-symbols-outlined text-[16px]">qr_code_2</span>
              <span>Ficha QR</span>
            </NuxtLink>
          </div>
        </div>

        <div class="modal-footer">
          <button type="button" class="btn-outline-secondary" @click="cerrarModal">
            <span>Cerrar</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Cliente } from '~/types/cliente'

defineProps<{
  cliente: Cliente | null
}>()

const emit = defineEmits<{
  (e: 'cerrar'): void
}>()

const cerrarModal = () => {
  emit('cerrar')
}
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(24, 28, 32, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  padding: 16px;
}

.modal-card {
  background-color: #ffffff;
  border-radius: var(--border-radius-md);
  border: 1px solid var(--border-subtle);
  width: 100%;
  max-width: 580px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
}

.modal-header {
  padding: 14px 20px;
  border-bottom: 1px solid var(--border-subtle);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #ffffff;
}

.modal-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-modal {
  color: var(--primary);
}

.btn-close-modal {
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  border-radius: var(--border-radius-sm);
}

.btn-close-modal:hover {
  background-color: var(--surface-low);
  color: var(--text-main);
}

.modal-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background-color: #ffffff;
}

.client-detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  background-color: var(--surface-low);
  padding: 14px;
  border-radius: var(--border-radius-sm);
  border: 1px solid var(--border-subtle);
}

.col-span-2 {
  grid-column: span 2;
}

.detail-item {
  display: flex;
  flex-direction: column;
}

.detail-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
}

.detail-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
  margin-top: 2px;
}

.vehicle-list-wrapper {
  margin-top: 4px;
}

.section-subtitle {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-main);
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.vehicle-card-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--border-radius-sm);
  background-color: #ffffff;
}

.vehicle-icon-box {
  width: 36px;
  height: 36px;
  border-radius: var(--border-radius-sm);
  background-color: var(--primary-light);
  color: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.vehicle-info {
  flex: 1;
}

.vh-model {
  font-size: 13px;
  font-weight: 600;
}

.vh-meta {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
  border-top: 1px solid var(--border-subtle);
}
</style>
