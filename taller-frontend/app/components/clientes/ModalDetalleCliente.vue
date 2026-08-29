<template>
  <div v-if="cliente" class="modal-backdrop" @click.self="cerrarModal">
    <div class="modal-card">
      <div class="modal-header">
        <h3 class="modal-title">
          <span class="material-symbols-outlined icon-modal">account_box</span>
          Ficha de Cliente: {{ cliente.nombre }}
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
            <span class="detail-label">Condición Fiscal ARCA</span>
            <span :class="['fiscal-badge', getFiscalBadgeClass(cliente.condicionFiscal)]">
              {{ cliente.condicionFiscal }}
            </span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Teléfono</span>
            <span class="detail-value">{{ cliente.telefono }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Email</span>
            <span class="detail-value">{{ cliente.email }}</span>
          </div>
        </div>

        <div class="vehicle-list-wrapper">
          <h4 class="section-subtitle">
            <span class="material-symbols-outlined">garage</span>
            Vehículos Asociados
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
              class="btn-view-qr"
            >
              <span class="material-symbols-outlined">qr_code_2</span>
              <span>Ficha QR</span>
            </NuxtLink>
          </div>
        </div>

        <div class="modal-footer">
          <button type="button" class="btn-secondary" @click="cerrarModal">
            Cerrar
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Cliente } from '~/types/cliente'

const props = defineProps<{
  cliente: Cliente | null
}>()

const emit = defineEmits<{
  (e: 'cerrar'): void
}>()

const cerrarModal = () => {
  emit('cerrar')
}

const getFiscalBadgeClass = (condicion: string) => {
  switch (condicion) {
    case 'Responsable Inscripto': return 'badge-ri'
    case 'Monotributo': return 'badge-mono'
    case 'Exento': return 'badge-exento'
    default: return 'badge-cf'
  }
}
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(15, 23, 42, 0.6);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  padding: 16px;
}

.modal-card {
  background-color: var(--surface-container-lowest);
  border-radius: 8px;
  width: 100%;
  max-width: 580px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: var(--shadow-md);
  display: flex;
  flex-direction: column;
}

.modal-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-subtle);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modal-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--on-surface);
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
  color: var(--on-surface-variant);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  border-radius: 4px;
}

.btn-close-modal:hover {
  background-color: var(--surface-container-low);
}

.modal-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.client-detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  background-color: var(--surface-container-low);
  padding: 14px;
  border-radius: 6px;
}

.detail-item {
  display: flex;
  flex-direction: column;
}

.detail-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--on-surface-variant);
}

.detail-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--on-surface);
  margin-top: 2px;
}

.fiscal-badge {
  font-size: 11px;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 4px;
  display: inline-block;
  width: fit-content;
  margin-top: 2px;
}

.badge-ri { background-color: #dbeafe; color: #1e40af; }
.badge-mono { background-color: #fef3c7; color: #92400e; }
.badge-cf { background-color: #e0f2fe; color: #0369a1; }
.badge-exento { background-color: #f3f4f6; color: #374151; }

.vehicle-list-wrapper {
  margin-top: 4px;
}

.section-subtitle {
  font-size: 13px;
  font-weight: 700;
  color: var(--on-surface);
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
  border-radius: 6px;
}

.vehicle-icon-box {
  width: 36px;
  height: 36px;
  border-radius: 6px;
  background-color: var(--secondary-container);
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
  color: var(--on-surface-variant);
  margin-top: 2px;
}

.btn-view-qr {
  padding: 6px 12px;
  background-color: var(--surface-container-low);
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  color: var(--primary);
  font-size: 12px;
  font-weight: 600;
  text-decoration: none;
  display: flex;
  align-items: center;
  gap: 6px;
}

.btn-view-qr:hover {
  background-color: var(--primary);
  color: var(--on-primary);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
  border-top: 1px solid var(--border-subtle);
}

.btn-secondary {
  padding: 8px 16px;
  background-color: transparent;
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  color: var(--on-surface);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
</style>
