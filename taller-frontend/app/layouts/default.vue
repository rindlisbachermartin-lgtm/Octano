<template>
  <div :class="['app-layout', { 'sidebar-collapsed': isCollapsed, 'app-dark': isDarkMode }]">
    <!-- Sidebar Lateral -->
    <aside class="app-sidebar">
      <div class="sidebar-header">
        <div class="brand">
          <div class="brand-icon">
            <i class="pi pi-wrench"></i>
          </div>
          <div v-if="!isCollapsed" class="brand-text">
            <span class="brand-title">AutoTaller PRO</span>
            <span class="brand-subtitle">Gestión Integral</span>
          </div>
        </div>
        <button class="toggle-btn" @click="isCollapsed = !isCollapsed" :title="isCollapsed ? 'Expandir menú' : 'Colapsar menú'">
          <i :class="isCollapsed ? 'pi pi-chevron-right' : 'pi pi-chevron-left'"></i>
        </button>
      </div>

      <nav class="sidebar-nav">
        <div class="nav-section-title" v-if="!isCollapsed">OPERACIONES PRINCIPALES</div>
        
        <NuxtLink to="/" class="nav-item" active-class="active" title="Dashboard">
          <i class="pi pi-th-large"></i>
          <span v-if="!isCollapsed">Panel Principal</span>
        </NuxtLink>

        <NuxtLink to="/clientes" class="nav-item" active-class="active" title="Clientes (RF-01)">
          <i class="pi pi-users"></i>
          <span v-if="!isCollapsed">Clientes</span>
        </NuxtLink>

        <NuxtLink to="/vehiculos" class="nav-item" active-class="active" title="Vehículos (RF-02)">
          <i class="pi pi-car"></i>
          <span v-if="!isCollapsed">Vehículos</span>
        </NuxtLink>

        <NuxtLink to="/turnos" class="nav-item" active-class="active" title="Turnos y Agenda (RF-03)">
          <i class="pi pi-calendar"></i>
          <span v-if="!isCollapsed">Turnos & Agenda</span>
        </NuxtLink>

        <div class="nav-section-title" v-if="!isCollapsed">TALLER & INVENTARIO</div>

        <NuxtLink to="/repuestos" class="nav-item" active-class="active" title="Stock y Compatibilidad (RF-05)">
          <i class="pi pi-box"></i>
          <span v-if="!isCollapsed">Repuestos & Stock</span>
        </NuxtLink>

        <NuxtLink to="/presupuestos" class="nav-item" active-class="active" title="Presupuestos (RF-06)">
          <i class="pi pi-file-edit"></i>
          <span v-if="!isCollapsed">Presupuestos</span>
        </NuxtLink>

        <NuxtLink to="/ordenes" class="nav-item" active-class="active" title="Órdenes de Trabajo (RF-07)">
          <i class="pi pi-cog"></i>
          <span v-if="!isCollapsed">Órdenes de Trabajo</span>
        </NuxtLink>

        <NuxtLink to="/peritaje" class="nav-item" active-class="active" title="Peritaje Fotográfico (RF-04)">
          <i class="pi pi-camera"></i>
          <span v-if="!isCollapsed">Peritaje (Check-in)</span>
        </NuxtLink>

        <div class="nav-section-title" v-if="!isCollapsed">FISCAL & PÚBLICO</div>

        <NuxtLink to="/facturacion" class="nav-item" active-class="active" title="Facturación ARCA (RF-10)">
          <i class="pi pi-receipt"></i>
          <span v-if="!isCollapsed">Facturación ARCA</span>
        </NuxtLink>

        <NuxtLink to="/ficha/AB123CD" class="nav-item" active-class="active" title="Ficha Técnica QR (RF-09)">
          <i class="pi pi-qrcode"></i>
          <span v-if="!isCollapsed">Ficha Pública QR</span>
        </NuxtLink>
      </nav>

      <div class="sidebar-footer">
        <div class="user-badge" v-if="!isCollapsed">
          <div class="user-avatar">
            <i class="pi pi-user"></i>
          </div>
          <div class="user-info">
            <span class="user-name">{{ user?.name || 'Administrador' }}</span>
            <span class="user-role">ADMINISTRADOR</span>
          </div>
        </div>
      </div>
    </aside>

    <!-- Contenido Principal y Header -->
    <div class="app-main-wrapper">
      <header class="app-header">
        <div class="header-left">
          <div class="global-status">
            <span class="status-indicator online"></span>
            <span class="status-text">Servidor Conectado</span>
          </div>
        </div>

        <div class="header-right">
          <!-- Dark mode toggle -->
          <button class="icon-action-btn" @click="toggleDarkMode" :title="isDarkMode ? 'Modo Claro' : 'Modo Oscuro'">
            <i :class="isDarkMode ? 'pi pi-sun' : 'pi pi-moon'"></i>
          </button>

          <!-- Notification indicator -->
          <div class="notification-btn-wrapper">
            <button class="icon-action-btn" title="Notificaciones del Taller">
              <i class="pi pi-bell"></i>
              <span class="badge-dot"></span>
            </button>
          </div>

          <!-- Perfil rápido -->
          <div class="header-user-profile">
            <div class="user-circle">
              <span>MR</span>
            </div>
          </div>
        </div>
      </header>

      <main class="app-content">
        <slot />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
const { user } = useAuth()
const isCollapsed = ref(false)
const isDarkMode = ref(false)

const toggleDarkMode = () => {
  isDarkMode.value = !isDarkMode.value
  if (import.meta.client) {
    if (isDarkMode.value) {
      document.documentElement.classList.add('app-dark')
    } else {
      document.documentElement.classList.remove('app-dark')
    }
  }
}

onMounted(() => {
  if (import.meta.client) {
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    if (prefersDark) {
      isDarkMode.value = true
      document.documentElement.classList.add('app-dark')
    }
  }
})
</script>

<style scoped>
.app-layout {
  display: flex;
  min-height: 100vh;
  background-color: var(--bg-app);
}

/* Sidebar */
.app-sidebar {
  width: var(--sidebar-width);
  background-color: var(--bg-sidebar);
  color: #f8fafc;
  display: flex;
  flex-direction: column;
  transition: width 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  z-index: 50;
  overflow-y: auto;
  overflow-x: hidden;
  border-right: 1px solid rgba(255, 255, 255, 0.08);
}

.sidebar-collapsed .app-sidebar {
  width: var(--sidebar-collapsed-width);
}

.sidebar-header {
  height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 1.25rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.brand-icon {
  width: 38px;
  height: 38px;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 1.1rem;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.4);
}

.brand-text {
  display: flex;
  flex-direction: column;
}

.brand-title {
  font-weight: 800;
  font-size: 1rem;
  letter-spacing: -0.02em;
  color: #ffffff;
}

.brand-subtitle {
  font-size: 0.65rem;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.toggle-btn {
  background: transparent;
  border: none;
  color: #94a3b8;
  cursor: pointer;
  padding: 0.4rem;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.toggle-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #ffffff;
}

.sidebar-nav {
  flex: 1;
  padding: 1rem 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.nav-section-title {
  font-size: 0.65rem;
  font-weight: 700;
  color: #64748b;
  letter-spacing: 0.08em;
  padding: 0.75rem 0.65rem 0.35rem;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  padding: 0.7rem 0.85rem;
  color: #cbd5e1;
  text-decoration: none;
  border-radius: 8px;
  font-size: 0.88rem;
  font-weight: 500;
  transition: all 0.18s ease;
  white-space: nowrap;
}

.nav-item i {
  font-size: 1.1rem;
  min-width: 1.25rem;
  color: #94a3b8;
  transition: color 0.18s ease;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.07);
  color: #ffffff;
}

.nav-item:hover i {
  color: #60a5fa;
}

.nav-item.active {
  background: #2563eb;
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.3);
}

.nav-item.active i {
  color: #ffffff;
}

.sidebar-footer {
  padding: 1rem;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.user-badge {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  background: rgba(255, 255, 255, 0.05);
  padding: 0.6rem 0.75rem;
  border-radius: 8px;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #334155;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 0.9rem;
}

.user-info {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.user-name {
  font-size: 0.8rem;
  font-weight: 600;
  color: #f8fafc;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
}

.user-role {
  font-size: 0.65rem;
  color: #60a5fa;
  font-weight: 700;
}

/* Main Content Area */
.app-main-wrapper {
  flex: 1;
  margin-left: var(--sidebar-width);
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  transition: margin-left 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.sidebar-collapsed .app-main-wrapper {
  margin-left: var(--sidebar-collapsed-width);
}

.app-header {
  height: var(--header-height);
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-subtle);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2rem;
  position: sticky;
  top: 0;
  z-index: 40;
  backdrop-filter: blur(8px);
}

.global-status {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-muted);
}

.status-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-indicator.online {
  background: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.2);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.icon-action-btn {
  background: var(--bg-card);
  border: 1px solid var(--border-subtle);
  color: var(--text-muted);
  width: 38px;
  height: 38px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
}

.icon-action-btn:hover {
  background: var(--bg-card-hover);
  color: var(--text-main);
  border-color: var(--border-focus);
}

.notification-btn-wrapper {
  position: relative;
}

.badge-dot {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 7px;
  height: 7px;
  background: #ef4444;
  border-radius: 50%;
}

.user-circle {
  width: 38px;
  height: 38px;
  border-radius: 8px;
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
  color: #fff;
  font-weight: 700;
  font-size: 0.85rem;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: var(--shadow-sm);
}

.app-content {
  flex: 1;
}

@media (max-width: 768px) {
  .app-sidebar {
    width: var(--sidebar-collapsed-width);
  }
  .app-main-wrapper {
    margin-left: var(--sidebar-collapsed-width);
  }
  .brand-text, .nav-section-title, .user-badge {
    display: none;
  }
}
</style>
