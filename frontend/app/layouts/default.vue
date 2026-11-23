<script setup lang="ts">
import { LayoutDashboard, CalendarDays, Wrench, CarFront, Package, QrCode, UsersRound, FileText, Receipt, Settings2, ArrowRight, Menu, X, PanelLeftClose, PanelLeftOpen, LogOut } from '@lucide/vue';

const route = useRoute()

const { isDark } = useTheme()
const api = useAuthApi()
const profileName = computed(() => api.user.value?.email || '')
const workshopName = computed(() => api.workshopName.value)
const roleLabel = computed(() => {
  if (api.user.value?.rol === 'DUENO') return 'Dueño del taller'
  if (api.user.value?.rol === 'ADMIN') return 'Administrador'
  if (api.user.value?.rol === 'RECEPCION') return 'Recepción'
  if (api.user.value?.rol === 'MECANICO') return 'Mecánico'
  return ''
})
const mobileNav = ref(false)
const helpOpen = ref(false)
const settingsOpen = ref(false)
const logoutPending = ref(false)
const logoutError = ref('')
const sidebarCollapsed = ref(false)

async function signOut() {
  logoutError.value = ''
  logoutPending.value = true
  try {
    await api.logout()
    settingsOpen.value = false
  } catch {
    logoutError.value = 'No pudimos cerrar la sesión. Intentá de nuevo.'
  } finally {
    logoutPending.value = false
  }
}

const adminNav = [
  { name: 'Dashboard', to: '/', icon: LayoutDashboard, group: 'TU TALLER' },
  { name: 'Turnos', to: '/agenda', icon: CalendarDays },
  { name: 'Órdenes de trabajo', to: '/ordenes', icon: Wrench },
  { name: 'Clientes', to: '/clientes', icon: UsersRound },
  { name: 'Vehículos', to: '/vehiculos', icon: CarFront },
  { name: 'Repuestos', to: '/inventario', icon: Package },
  { name: 'Códigos QR', to: '/codigos-qr', icon: QrCode },
  { name: 'Presupuestos', to: '/presupuestos', icon: FileText, group: 'ADMINISTRACIÓN' },
  { name: 'Facturación', to: '/facturacion', icon: Receipt },
]

const nav = computed(() => {
  if (api.user.value?.rol === 'MECANICO') {
    return [{ name: 'Mis órdenes de trabajo', to: api.homePath.value, icon: Wrench, group: 'ÁREA MECÁNICA' }]
  }
  if (api.puedeAdministrar.value) return [...adminNav, { name: 'Personal', to: '/personal', icon: UsersRound }]
  return adminNav
})

const currentTitle = computed(() => {
  if (route.path.startsWith('/ajustes/')) return 'Configuración'
  if (route.path === '/') return 'Panel general'
  const current = nav.value.find((item) => {
    if (item.to === '/') return route.path === '/'
    return route.path.startsWith(item.to)
  })
  return current?.name || 'Tu taller'
})

const isPublicRoute = computed(() => {
  return route.path.startsWith('/qr') || route.path.startsWith('/ficha')
})

// Close mobile nav on route change
watch(() => route.path, () => {
  mobileNav.value = false
})

function dismissMobile(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    mobileNav.value = false
    helpOpen.value = false
    settingsOpen.value = false
  }
}

onMounted(() => {
  window.addEventListener('keydown', dismissMobile)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', dismissMobile)
})
</script>

<template>
  <div v-if="isPublicRoute" class="blank-page-wrapper">
    <slot />
  </div>
  <ClientOnly v-else>
  <div class="app-shell" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
    <!-- Backdrop for mobile drawer -->
    <div
      v-if="mobileNav"
      class="sidebar-backdrop"
      @click="mobileNav = false"
    ></div>

    <!-- Sidebar -->
    <aside class="sidebar" :class="{ 'is-open': mobileNav }">
      <div class="sidebar-header">
        <NuxtLink :to="api.homePath.value" class="brand" aria-label="Octano, ir al inicio">
          <CommonOctanoLogo />
          <span class="brand-wordmark">
            <span class="brand-name">octa<span class="brand-light">no</span></span>
            <span class="brand-tagline"><span>Sistema de gestión</span><span>de talleres mecánicos</span></span>
          </span>
        </NuxtLink>
        <button class="icon-button sidebar-mobile-close" aria-label="Cerrar menú" @click="mobileNav = false"><X :size="19" /></button>
      </div>

      <nav aria-label="Navegación principal">
        <template v-for="item in nav" :key="item.name">
          <p v-if="item.group" class="nav-label">{{ item.group }}</p>
          <NuxtLink
            :to="item.to"
            class="nav-item"
            :title="item.name"
            :class="{ active: item.to === '/' ? route.path === '/' : route.path.startsWith(item.to) }"
            @click="mobileNav = false"
          >
            <component :is="item.icon" :size="20" />
            <span class="nav-item-name">{{ item.name }}</span>
          </NuxtLink>
        </template>
      </nav>

      <div class="sidebar-bottom">
        <button class="profile" title="Perfil y configuración" aria-label="Abrir perfil y configuración" @click="settingsOpen = true">
          <span class="avatar">{{ ownerInitials(profileName) }}</span>
          <span><strong>{{ profileName }}</strong><small>{{ roleLabel }}</small></span>
          <Settings2 :size="16" />
        </button>
      </div>
    </aside>

    <!-- Main Content Area -->
    <main class="main">
      <header class="topbar">
        <div class="breadcrumb">
          <button
            class="icon-button mobile-toggle"
            aria-label="Abrir menú"
            @click="mobileNav = true"
          >
            <Menu :size="20" />
          </button>
          <button class="icon-button desktop-sidebar-toggle" :aria-label="sidebarCollapsed ? 'Expandir menú' : 'Contraer menú'" @click="sidebarCollapsed = !sidebarCollapsed">
            <PanelLeftOpen v-if="sidebarCollapsed" :size="19" />
            <PanelLeftClose v-else :size="19" />
          </button>
          <strong>{{ currentTitle }}</strong>
        </div>

        <div class="top-actions">
          <CommonThemeToggle v-model="isDark" />

        </div>
      </header>

      <!-- Routed Page Content -->

      <slot />
    </main>

    <!-- Global Help Modal -->
    <CommonModalDialog v-if="helpOpen" class="dialog" @close="helpOpen = false">
      <div class="dialog-header">
        <h2>Un taller conectado.</h2>
        <button class="icon-button" aria-label="Cerrar" @click="helpOpen = false"><X :size="18" /></button>
      </div>
      <div class="help-content">
        <span class="large-symbol">o·</span>
        <h2>Guía rápida</h2>
        <p>Flujo general para la gestión diaria de vehículos y trabajos.</p>
        <ol>
          <li><strong>Recibí.</strong> Registrá clientes, vehículos y turnos.</li>
          <li><strong>Resolvé.</strong> Creá órdenes, cargá fotos e imputá repuestos.</li>
          <li><strong>Entregá.</strong> Finalizá el trabajo y registrá el cobro.</li>
        </ol>
        <div class="integration-notice">
          Los datos del taller se guardan en el servidor. Cada usuario accede según los permisos de su cuenta.
        </div>
        <button class="button primary" @click="helpOpen = false">
          Vamos al taller <ArrowRight :size="17" />
        </button>
      </div>
    </CommonModalDialog>

    <!-- Global Settings Modal -->
    <CommonModalDialog v-if="settingsOpen" class="dialog" @close="settingsOpen = false">
      <div class="dialog-header">
        <h2>Tu espacio de trabajo</h2>
        <button class="icon-button" aria-label="Cerrar" @click="settingsOpen = false"><X :size="18" /></button>
      </div>
      <div class="detail-body">
        <div class="client-cell">
          <span class="avatar">{{ ownerInitials(profileName) }}</span>
          <div>
            <h3>{{ profileName }}</h3>
            <p class="muted">{{ roleLabel }}<template v-if="workshopName"> · {{ workshopName }}</template></p>
          </div>
        </div>

        <h3>Acerca de este espacio</h3>

        <p class="muted">Los datos y las operaciones corresponden al taller de tu cuenta.</p>
        <p v-if="logoutError" role="alert">{{ logoutError }}</p>
        <div class="settings-account-actions"><button class="button" @click="settingsOpen = false">Entendido</button><button class="button" :disabled="logoutPending" @click="signOut"><LogOut :size="15" />{{ logoutPending ? 'Cerrando sesión…' : 'Cerrar sesión' }}</button></div>
      </div>
    </CommonModalDialog>
  </div>
  </ClientOnly>
</template>

<style scoped>
.settings-account-actions { display: flex; justify-content: space-between; gap: 12px; }
.settings-vat-row {
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
}
</style>
