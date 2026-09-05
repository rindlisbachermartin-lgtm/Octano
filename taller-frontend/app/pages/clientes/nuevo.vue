<template>
  <div class="client-form-page">
    <!-- Top Action Bar (Estilo Bosch OfficeOn) -->
    <div class="context-top-bar">
      <div class="bar-left">
        <NuxtLink to="/clientes" class="btn-back">
          <span class="material-symbols-outlined">arrow_back</span>
        </NuxtLink>
        <h2 class="page-main-heading">Nuevo Cliente</h2>
        <span class="status-pill blue">Nuevo</span>
      </div>

      <div class="bar-right">
        <NuxtLink to="/clientes" class="btn-outline-secondary">
          <span>Cancelar</span>
        </NuxtLink>
        <button type="button" class="btn-solid-primary" @click="ejecutarGuardado">
          <span>Guardar Cliente</span>
        </button>
      </div>
    </div>

    <!-- Form Surface Container (Fondo Blanco Sólido) -->
    <div class="form-surface-card">
      <form @submit.prevent="ejecutarGuardado" class="form-content">
        <!-- Sección 1: Datos de Contacto y Fiscales -->
        <div class="form-section">
          <h3 class="section-title">Datos del Cliente</h3>
          
          <div class="inputs-grid">
            <!-- Nombre / Razón Social -->
            <div class="input-field col-span-2">
              <label class="field-label">Nombre y Apellido / Razón Social *</label>
              <input 
                v-model="formulario.nombre" 
                type="text" 
                required 
                placeholder="Ej: Carlos Rodríguez o Empresa Logística S.A." 
                class="form-control"
              />
            </div>

            <!-- CUIT / CUIL / DNI -->
            <div class="input-field">
              <label class="field-label">DNI / CUIT / CUIL *</label>
              <input 
                v-model="formulario.cuit" 
                type="text" 
                required 
                placeholder="Ej: 20-35891234-9" 
                class="form-control"
              />
            </div>

            <!-- Teléfono -->
            <div class="input-field">
              <label class="field-label">Teléfono de Contacto (WhatsApp) *</label>
              <input 
                v-model="formulario.telefono" 
                type="tel" 
                required 
                placeholder="Ej: +54 9 2392 554433" 
                class="form-control"
              />
            </div>

            <!-- Email -->
            <div class="input-field">
              <label class="field-label">Correo Electrónico *</label>
              <input 
                v-model="formulario.email" 
                type="email" 
                required 
                placeholder="Ej: cliente@email.com" 
                class="form-control"
              />
            </div>

            <!-- Dirección -->
            <div class="input-field">
              <label class="field-label">Dirección / Localidad</label>
              <input 
                v-model="formulario.direccion" 
                type="text" 
                placeholder="Ej: Av. Villegas 450, Trenque Lauquen" 
                class="form-control"
              />
            </div>
          </div>
        </div>

        <div class="divider-line"></div>

        <!-- Sección 2: Vehículo Vinculado -->
        <div class="form-section">
          <div class="section-header-flex">
            <h3 class="section-title">Vehículo Vinculado</h3>
            <span class="tag-optional">Opcional</span>
          </div>

          <div class="inputs-grid">
            <!-- Patente -->
            <div class="input-field">
              <label class="field-label">Patente / Dominio</label>
              <input 
                v-model="formulario.patente" 
                type="text" 
                placeholder="Ej: AF 892 PL" 
                class="form-control uppercase-text"
              />
            </div>

            <!-- Marca y Modelo -->
            <div class="input-field">
              <label class="field-label">Marca y Modelo</label>
              <input 
                v-model="formulario.vehiculo" 
                type="text" 
                placeholder="Ej: Toyota Hilux 2.8 TDI" 
                class="form-control"
              />
            </div>

            <!-- Año -->
            <div class="input-field">
              <label class="field-label">Año</label>
              <input 
                v-model="formulario.anio" 
                type="number" 
                placeholder="Ej: 2022" 
                class="form-control"
              />
            </div>

            <!-- Kilometraje -->
            <div class="input-field">
              <label class="field-label">Kilometraje Actual</label>
              <input 
                v-model="formulario.kilometraje" 
                type="number" 
                placeholder="Ej: 85000" 
                class="form-control"
              />
            </div>
          </div>
        </div>

        <!-- Botones de Acción al pie -->
        <div class="form-bottom-actions">
          <NuxtLink to="/clientes" class="btn-outline-secondary">
            <span>Cancelar</span>
          </NuxtLink>
          <button type="submit" class="btn-solid-primary">
            <span>Guardar Cliente</span>
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { FormularioCliente } from '~/types/cliente'

const { agregarCliente } = useClientes()

const formulario = reactive<FormularioCliente>({
  nombre: '',
  cuit: '',
  email: '',
  telefono: '',
  direccion: '',
  patente: '',
  vehiculo: '',
  anio: 2022,
  kilometraje: 0
})

const ejecutarGuardado = () => {
  if (!formulario.nombre || !formulario.cuit || !formulario.email || !formulario.telefono) {
    alert('Por favor complete todos los campos obligatorios (*).')
    return
  }

  agregarCliente(formulario)
  navigateTo('/clientes')
}
</script>

<style scoped>
.client-form-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1100px;
}

/* Context Top Bar */
.context-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-subtle);
}

.bar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.btn-back {
  background: transparent;
  border: none;
  color: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 6px;
  border-radius: var(--border-radius-sm);
  text-decoration: none;
}

.btn-back:hover {
  background-color: var(--surface-low);
}

.page-main-heading {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-main);
}

.bar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* Form Surface Card (Fondo blanco sólido, sin transparencias) */
.form-surface-card {
  background-color: #ffffff;
  border: 1px solid var(--border-subtle);
  border-radius: var(--border-radius-md);
  padding: 28px 32px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.form-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-header-flex {
  display: flex;
  align-items: center;
  gap: 10px;
}

.section-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
}

.tag-optional {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  background-color: var(--surface-input);
  padding: 2px 8px;
  border-radius: var(--border-radius-sm);
}

.inputs-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px 20px;
}

.col-span-2 {
  grid-column: span 2;
}

@media (max-width: 768px) {
  .inputs-grid {
    grid-template-columns: 1fr;
  }
  .col-span-2 {
    grid-column: span 1;
  }
}

.input-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
}

.form-control {
  background-color: var(--surface-input);
  border: 1px solid transparent;
  border-radius: var(--border-radius-sm);
  padding: 9px 12px;
  font-size: 14px;
  font-family: inherit;
  color: var(--text-main);
  outline: none;
  transition: all 0.15s ease;
}

.form-control:focus {
  background-color: #ffffff;
  border-color: var(--border-focus);
}

.uppercase-text {
  text-transform: uppercase;
}

.divider-line {
  height: 1px;
  background-color: var(--border-subtle);
  margin: 4px 0;
}

.form-bottom-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid var(--border-subtle);
}
</style>
