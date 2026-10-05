<script setup lang="ts">
import {
  LayoutDashboard,
  CalendarDays,
  ClipboardList,
  UsersRound,
  CarFront,
  Package,
  FileText,
  Receipt,
  Settings2,
  ArrowUpRight,
  ArrowRight,
  CircleHelp,
  Bell,
  ChevronDown,
  ChevronRight,
  Wrench,
  Menu,
  X,
  PanelLeftClose,
  PanelLeftOpen,
  LogOut,
  ShieldCheck,
} from 'lucide-vue-next'

const route = useRoute()
const { db, activeOrders, lowStock } = useDatabase()
const { notify } = useWorkshopToast()
const { isDark } = useTheme()
const auth = useOwnerAccount()
const profileName = computed(() => auth.owner.value?.name || 'Martín Rindlisbacher')
const workshopName = computed(() => auth.owner.value?.workshop || 'Taller Central')
watch(() => db.value.issuerVatCondition, (vat) => {
  if (auth.owner.value && auth.owner.value.vat !== vat) auth.updateFiscal({ vat, arcaStatus: 'pending' })
})

const mobileNav = ref(false)
const notificationsOpen = ref(false)
const helpOpen = ref(false)
const settingsOpen = ref(false)
const sidebarCollapsed = ref(false)

const nav = [
  { name: 'Dashboard', to: '/', icon: LayoutDashboard, group: 'TU TALLER' },
  { name: 'Agenda', to: '/agenda', icon: CalendarDays },
  { name: 'Órdenes de trabajo', to: '/ordenes', icon: ClipboardList },
  { name: 'Clientes', to: '/clientes', icon: UsersRound },
  { name: 'Vehículos', to: '/vehiculos', icon: CarFront },
  { name: 'Inventario', to: '/inventario', icon: Package },
  { name: 'Presupuestos', to: '/presupuestos', icon: FileText, group: 'ADMINISTRACIÓN' },
  { name: 'Facturación', to: '/facturacion', icon: Receipt },
]

const currentTitle = computed(() => {
  if (route.path.startsWith('/ajustes/')) return 'Configuración'
  if (route.path === '/') return 'Panel general'
  const current = nav.find((item) => {
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
  notificationsOpen.value = false
})

function dismissMobile(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    mobileNav.value = false
    notificationsOpen.value = false
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
  <div v-else class="app-shell" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
    <!-- Backdrop for mobile drawer -->
    <div
      v-if="mobileNav"
      class="sidebar-backdrop"
      @click="mobileNav = false"
    ></div>

    <!-- Sidebar -->
    <aside class="sidebar" :class="{ 'is-open': mobileNav }">
      <div class="sidebar-header">
        <NuxtLink to="/" class="brand" aria-label="Octano, ir al inicio">
          <CommonOctanoLogo />
          <span class="brand-name">octa<span class="brand-light">no</span></span>
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
            <span v-if="item.name === 'Órdenes de trabajo'" class="nav-count">{{ activeOrders.length }}</span>
            <span v-if="item.name === 'Inventario' && lowStock.length" class="nav-dot"></span>
          </NuxtLink>
        </template>
      </nav>

      <div class="sidebar-bottom">
        <NuxtLink
          to="/mecanico"
          class="demo-mecanico-btn"
          title="Ver cómo ve la app el mecánico en el taller"
        >
          <span class="demo-tag">DEMO</span>
          <span>Vista Mecánico</span>
          <ArrowUpRight :size="14" />
        </NuxtLink>

        <button class="nav-item" title="Configuración" @click="settingsOpen = true">
          <Settings2 :size="18" /><span class="nav-item-name">Configuración</span>
        </button>

        <button class="profile" title="Perfil" @click="settingsOpen = true">
          <span class="avatar">{{ ownerInitials(profileName) }}</span>
          <span><strong>{{ profileName }}</strong><small>{{ auth.owner.value ? 'Dueño del taller' : 'Demo · Administrador' }}</small></span>
          <ChevronDown :size="14" />
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

       

          <div class="notification-anchor">
            <button
              class="icon-button notification-button"
              aria-label="Notificaciones"
              :aria-expanded="notificationsOpen"
              @click="notificationsOpen = !notificationsOpen"
            >
              <Bell :size="19" />
              <i v-if="db.notifications.some((n) => !n.read)"></i>
            </button>

            <div v-if="notificationsOpen" class="notifications">
              <div class="section-heading">
                <h3>Notificaciones</h3>
                <button
                  class="icon-button"
                  aria-label="Cerrar notificaciones"
                  @click="notificationsOpen = false"
                >
                  <X :size="16" />
                </button>
              </div>
              <article
                v-for="item in db.notifications"
                :key="item.id"
                class="notification-item"
                :class="{ unread: !item.read }"
                @click="item.read = true"
              >
                <strong>{{ item.title }}</strong>
                <p>{{ item.detail }}</p>
              </article>
              <p v-if="!db.notifications.length" class="muted">No tenés notificaciones pendientes.</p>
            </div>
          </div>
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
          Esta es una maqueta frontend adaptada a Nuxt. Los datos se guardan en tu navegador en localStorage.
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
            <p class="muted">{{ auth.owner.value ? 'Dueño' : 'Demo' }} · {{ workshopName }}</p>
            <p v-if="auth.owner.value" class="muted">{{ auth.owner.value.email }}</p>
          </div>
        </div>
        <h3>Facturación</h3>
        <label class="settings-row settings-vat-row">
          Condición de IVA del taller
          <select v-model="db.issuerVatCondition" aria-label="Condición de IVA del taller">
            <option v-for="condition in ISSUER_VAT_CONDITIONS" :key="condition" :value="condition">{{ condition }}</option>
          </select>
        </label>
        <h3>Integraciones</h3>
        <div class="settings-row">
          <span>ARCA · Facturación electrónica</span><span class="badge neutral">{{ auth.fiscalProfile.value?.arcaStatus === 'demo-verified' ? 'Verificada en maqueta' : 'Pendiente' }}</span>
        </div>
        <NuxtLink to="/ajustes/facturacion" class="button settings-arca-link" @click="settingsOpen = false"><ShieldCheck :size="15" />Configuración fiscal / ARCA</NuxtLink>
        <div class="settings-row">
          <span>WhatsApp · Avisos de retiro</span><span class="badge neutral">Demo</span>
        </div>
        <h3>Acerca de este espacio</h3>
        <p class="muted">
          Datos de ejemplo para explorar el flujo del taller. Esta maqueta no realiza envíos ni emite comprobantes fiscales.
        </p>
        <div class="settings-account-actions"><button class="button" @click="settingsOpen = false">Entendido</button><button class="button" @click="settingsOpen = false; auth.logout()"><LogOut :size="15" />Cerrar sesión</button></div>
      </div>
    </CommonModalDialog>
  </div>
</template>

<style scoped>
.settings-arca-link { margin-top: 12px; }
.settings-account-actions { display: flex; justify-content: space-between; gap: 12px; }
.settings-vat-row {
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
}

.demo-mecanico-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 8px;
  background: #f1f5f9;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #334155;
  text-decoration: none;
  transition: all 0.15s ease;
}

.demo-mecanico-btn:hover {
  background: #e2e8f0;
  border-color: #94a3b8;
  color: #0f172a;
}

.demo-tag {
  background: #0284c7;
  color: white;
  font-size: 9px;
  font-weight: 700;
  padding: 2px 5px;
  border-radius: 4px;
  letter-spacing: 0.5px;
}

:global(html.dark .demo-mecanico-btn) {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.15);
  color: #d1d1d6;
}

:global(html.dark .demo-mecanico-btn:hover) {
  background: rgba(255, 255, 255, 0.1);
  color: #ffffff;
}

:global(html.dark .demo-tag) {
  background: #0a84ff;
}
</style>
