<template>
  <div v-if="esVisible" class="modal-backdrop" @click.self="cerrarModal">
    <div class="modal-card">
      <div class="modal-header">
        <h3 class="modal-title">
          <span class="material-symbols-outlined icon-modal">{{ editandoId ? 'edit' : 'person_add' }}</span>
          {{ editandoId ? 'Editar Cliente' : 'Registrar Nuevo Cliente' }}
        </h3>
        <button class="btn-close-modal" @click="cerrarModal">
          <span class="material-symbols-outlined">close</span>
        </button>
      </div>

      <form @submit.prevent="ejecutarGuardado" class="modal-body">
        <div class="form-grid">
          <!-- Nombre / Razón Social -->
          <div class="form-group full-width">
            <label class="form-label">Nombre y Apellido / Razón Social *</label>
            <input 
              v-model="formulario.nombre" 
              type="text" 
              required 
              placeholder="Ej: Carlos Rodríguez o Empresa Logística S.A." 
              class="form-input"
            />
          </div>

          <!-- CUIT / CUIL / DNI -->
          <div class="form-group">
            <label class="form-label">DNI / CUIT / CUIL *</label>
            <input 
              v-model="formulario.cuit" 
              type="text" 
              required 
              placeholder="Ej: 20-35891234-9" 
              class="form-input"
            />
          </div>

          <!-- Teléfono -->
          <div class="form-group">
            <label class="form-label">Teléfono de Contacto (WhatsApp) *</label>
            <input 
              v-model="formulario.telefono" 
              type="tel" 
              required 
              placeholder="Ej: +54 9 2392 554433" 
              class="form-input"
            />
          </div>

          <!-- Email -->
          <div class="form-group">
            <label class="form-label">Correo Electrónico *</label>
            <input 
              v-model="formulario.email" 
              type="email" 
              required 
              placeholder="Ej: cliente@email.com" 
              class="form-input"
            />
          </div>
        </div>

        <!-- Sección de Vehículo Vinculado -->
        <div class="vehicle-section-header">
          <h4 class="section-subtitle">
            <span class="material-symbols-outlined">directions_car</span>
            Vehículo Vinculado (Opcional)
          </h4>
        </div>

        <div class="form-grid">
          <div class="form-group">
            <label class="form-label">Patente / Dominio</label>
            <input 
              v-model="formulario.patente" 
              type="text" 
              placeholder="Ej: AF 892 PL" 
              class="form-input uppercase-input"
            />
          </div>

          <div class="form-group">
            <label class="form-label">Marca y Modelo</label>
            <input 
              v-model="formulario.vehiculo" 
              type="text" 
              placeholder="Ej: Toyota Hilux 2.8 TDI" 
              class="form-input"
            />
          </div>

          <div class="form-group">
            <label class="form-label">Año</label>
            <input 
              v-model="formulario.anio" 
              type="number" 
              placeholder="Ej: 2022" 
              class="form-input"
            />
          </div>

          <div class="form-group">
            <label class="form-label">Kilometraje Actual</label>
            <input 
              v-model="formulario.kilometraje" 
              type="number" 
              placeholder="Ej: 85000" 
              class="form-input"
            />
          </div>
        </div>

        <div class="modal-footer">
          <button type="button" class="btn-secondary" @click="cerrarModal">
            Cancelar
          </button>
          <button type="submit" class="btn-primary-submit">
            {{ editandoId ? 'Guardar Cambios' : 'Registrar Cliente' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { FormularioCliente } from '~/types/cliente'

const props = defineProps<{
  esVisible: boolean
  editandoId: number | null
  formulario: FormularioCliente
}>()

const emit = defineEmits<{
  (e: 'update:esVisible', valor: boolean): void
  (e: 'guardar'): void
}>()

const cerrarModal = () => {
  emit('update:esVisible', false)
}

const ejecutarGuardado = () => {
  emit('guardar')
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
  max-width: 600px;
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

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.form-group.full-width {
  grid-column: span 2;
}

.form-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--on-surface-variant);
  margin-bottom: 6px;
}

.form-input {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--border-subtle);
  background-color: var(--surface-container-lowest);
  border-radius: 6px;
  font-size: 14px;
  font-family: inherit;
  color: var(--on-surface);
  outline: none;
}

.form-input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px rgba(0, 97, 153, 0.15);
}

.uppercase-input {
  text-transform: uppercase;
}

.vehicle-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid var(--border-subtle);
}

.section-subtitle {
  font-size: 14px;
  font-weight: 700;
  color: var(--on-surface);
  display: flex;
  align-items: center;
  gap: 6px;
}

.badge-optional {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  background-color: var(--secondary-container);
  color: var(--on-secondary-container);
  border-radius: 4px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 16px;
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

.btn-primary-submit {
  padding: 8px 18px;
  background-color: var(--primary-container);
  border: none;
  border-radius: 4px;
  color: var(--on-primary-container);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.btn-primary-submit:hover {
  opacity: 0.9;
}
</style>
