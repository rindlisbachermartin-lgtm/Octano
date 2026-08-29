<template>
  <div class="app-layout-wrapper">
    <!-- SideNavBar -->
    <aside class="sidebar">
      <nav class="sidebar-nav-container">
        <ul class="nav-list">
          <li>
            <NuxtLink to="/" exact-active-class="nav-active" class="nav-item-link">
              <span class="material-symbols-outlined nav-icon">dashboard</span>
              <span>Dashboard</span>
            </NuxtLink>
          </li>
          <li>
            <NuxtLink to="/clientes" active-class="nav-active" class="nav-item-link">
              <span class="material-symbols-outlined nav-icon">group</span>
              <span>Clientes</span>
            </NuxtLink>
          </li>
          <li>
            <NuxtLink to="/vehiculos" active-class="nav-active" class="nav-item-link">
              <span class="material-symbols-outlined nav-icon">directions_car</span>
              <span>Vehículos</span>
            </NuxtLink>
          </li>
          <li>
            <NuxtLink to="/turnos" active-class="nav-active" class="nav-item-link">
              <span class="material-symbols-outlined nav-icon">calendar_month</span>
              <span>Turnos</span>
            </NuxtLink>
          </li>
          <li>
            <NuxtLink to="/presupuestos" active-class="nav-active" class="nav-item-link">
              <span class="material-symbols-outlined nav-icon">description</span>
              <span>Presupuestos</span>
            </NuxtLink>
          </li>
          <li>
            <NuxtLink to="/facturacion" active-class="nav-active" class="nav-item-link">
              <span class="material-symbols-outlined nav-icon">receipt_long</span>
              <span>Facturación</span>
            </NuxtLink>
          </li>
          <li>
            <NuxtLink to="/repuestos" active-class="nav-active" class="nav-item-link">
              <span class="material-symbols-outlined nav-icon">settings_suggest</span>
              <span>Repuestos</span>
            </NuxtLink>
          </li>
          <li>
            <NuxtLink to="/ordenes" active-class="nav-active" class="nav-item-link">
              <span class="material-symbols-outlined nav-icon">handyman</span>
              <span>Órdenes de Trabajo</span>
            </NuxtLink>
          </li>
        </ul>
      </nav>

      <!-- Sidebar Footer -->
      <div class="sidebar-footer">
        <ul class="nav-list">
          <li>
            <button class="nav-item-link footer-link btn-logout" @click="cerrarSesion">
              <span class="material-symbols-outlined nav-icon">logout</span>
              <span>Cerrar Sesión</span>
            </button>
          </li>
        </ul>
      </div>
    </aside>

    <!-- Main Content Area -->
    <div class="main-layout-area">
      <!-- TopAppBar -->
      <header class="top-app-bar">
        <div class="top-bar-left">
          <h2 class="system-heading">Sistema de Gestión</h2>
        </div>

        <div class="top-bar-right">
          <!-- Search Input -->
          <div class="search-wrapper">
            <span class="material-symbols-outlined search-icon-input">search</span>
            <input 
              v-model="busquedaGlobal" 
              type="text" 
              placeholder="Buscar..." 
              class="top-search-input"
            />
          </div>

          <!-- Actions -->
          <div class="header-actions-group">
            <button class="icon-round-btn" title="Notificaciones">
              <span class="material-symbols-outlined">notifications</span>
            </button>
            <NuxtLink to="/turnos" class="btn-solid-primary">
              <span class="material-symbols-outlined text-[16px]">add</span>
              <span>Nueva Cita</span>
            </NuxtLink>
          </div>
        </div>
      </header>

      <!-- Canvas Content -->
      <main class="canvas-content-wrapper">
        <slot />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
const { logout } = useAuth()
const busquedaGlobal = ref('')

const cerrarSesion = () => {
  if (confirm('¿Desea cerrar su sesión?')) {
    logout()
  }
}
</script>

<style scoped>
.app-layout-wrapper {
  background-color: var(--canvas-bg);
  color: var(--text-main);
  display: flex;
  min-height: 100vh;
}

/* SideNavBar */
.sidebar {
  background-color: var(--sidebar-bg);
  width: var(--sidebar-width);
  height: 100vh;
  position: fixed;
  left: 0;
  top: 0;
  border-right: 1px solid rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  padding: 16px 0 12px;
  z-index: 20;
}

.sidebar-nav-container {
  flex: 1;
  overflow-y: auto;
}

.nav-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.nav-item-link {
  display: flex;
  align-items: center;
  gap: 14px;
  color: var(--sidebar-text);
  padding: 11px 24px;
  font-size: 13px;
  font-weight: 500;
  text-decoration: none;
  transition: all 0.1s ease;
  width: 100%;
}

.nav-item-link:hover {
  color: #ffffff;
  background-color: var(--sidebar-hover);
}

.nav-item-link.nav-active {
  background-color: var(--sidebar-active);
  color: var(--sidebar-text-active);
  font-weight: 700;
}

.nav-item-link.nav-active .nav-icon {
  font-variation-settings: 'FILL' 1;
}

.nav-icon {
  font-size: 20px;
}

.sidebar-footer {
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.footer-link {
  font-size: 12px;
}

.btn-logout {
  background: transparent;
  border: none;
  width: 100%;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
}

/* Main Content Area */
.main-layout-area {
  flex: 1;
  margin-left: var(--sidebar-width);
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

/* TopAppBar */
.top-app-bar {
  background-color: var(--surface);
  height: var(--header-height);
  border-bottom: 1px solid var(--border-subtle);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 10;
  width: 100%;
}

.system-heading {
  font-size: 17px;
  font-weight: 800;
  color: var(--text-main);
  letter-spacing: -0.01em;
}

.top-bar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.search-wrapper {
  position: relative;
}

.search-icon-input {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-subtle);
  font-size: 17px;
}

.top-search-input {
  background-color: var(--surface-input);
  border: 1px solid transparent;
  border-radius: var(--border-radius-sm);
  padding: 7px 12px 7px 34px;
  font-size: 13px;
  font-family: inherit;
  color: var(--text-main);
  width: 220px;
  outline: none;
  transition: all 0.15s ease;
}

.top-search-input:focus {
  background-color: var(--surface);
  border-color: var(--border-focus);
}

.header-actions-group {
  display: flex;
  align-items: center;
  gap: 10px;
  border-left: 1px solid var(--border-subtle);
  padding-left: 16px;
}

.icon-round-btn {
  background: transparent;
  border: 1px solid transparent;
  color: var(--text-muted);
  padding: 6px;
  border-radius: var(--border-radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.12s ease;
}

.icon-round-btn:hover {
  background-color: var(--surface-low);
  border-color: var(--border-subtle);
  color: var(--text-main);
}

/* Canvas Wrapper */
.canvas-content-wrapper {
  flex: 1;
  padding: var(--page-padding);
  background-color: var(--canvas-bg);
}
</style>
