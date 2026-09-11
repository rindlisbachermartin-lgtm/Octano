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
  ChartNoAxesCombined,
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
} from 'lucide-vue-next'

const route = useRoute()
const { db, activeOrders, lowStock } = useDatabase()
const { notify } = useToast()
const { isDark } = useTheme()

const mobileNav = ref(false)
const notificationsOpen = ref(false)
const helpOpen = ref(false)
const settingsOpen = ref(false)

const nav = [
  { name: 'Resumen', to: '/', icon: LayoutDashboard, group: 'TU TALLER' },
  { name: 'Agenda', to: '/agenda', icon: CalendarDays },
  { name: 'Órdenes de trabajo', to: '/ordenes', icon: ClipboardList },
  { name: 'Clientes', to: '/clientes', icon: UsersRound },
  { name: 'Vehículos', to: '/vehiculos', icon: CarFront },
  { name: 'Inventario', to: '/inventario', icon: Package },
  { name: 'Presupuestos', to: '/presupuestos', icon: FileText, group: 'ADMINISTRACIÓN' },
  { name: 'Facturación', to: '/facturacion', icon: Receipt },
  { name: 'Reportes', to: '/reportes', icon: ChartNoAxesCombined },
]

const currentTitle = computed(() => {
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
  <div v-else class="app-shell">
    <!-- Backdrop for mobile drawer -->
    <div
      v-if="mobileNav"
      class="sidebar-backdrop"
      @click="mobileNav = false"
    ></div>

    <!-- Sidebar -->
    <aside class="sidebar" :class="{ 'is-open': mobileNav }">
      <NuxtLink to="/" class="brand">
        <span class="brand-symbol">o<span>·</span></span>
        <span>octa<span class="brand-light">no</span></span>
      </NuxtLink>



      <nav aria-label="Navegación principal">
        <template v-for="item in nav" :key="item.name">
          <p v-if="item.group" class="nav-label">{{ item.group }}</p>
          <NuxtLink
            :to="item.to"
            class="nav-item"
            :class="{ active: item.to === '/' ? route.path === '/' : route.path.startsWith(item.to) }"
            @click="mobileNav = false"
          >
            <component :is="item.icon" :size="18" />
            <span>{{ item.name }}</span>
            <span v-if="item.name === 'Órdenes de trabajo'" class="nav-count">{{ activeOrders.length }}</span>
            <span v-if="item.name === 'Inventario' && lowStock.length" class="nav-dot"></span>
          </NuxtLink>
        </template>
      </nav>

      <div class="sidebar-bottom">
        <button class="nav-item" @click="settingsOpen = true">
          <Settings2 :size="18" />Configuración
        </button>

        <button class="profile" @click="settingsOpen = true">
          <span class="avatar">MR</span>
          <span><strong>Martín Rindlisbacher</strong><small>Administrador</small></span>
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
          <span>Tu taller</span>
          <ChevronRight :size="13" />
          <strong>{{ currentTitle }}</strong>
        </div>

        <div class="top-actions">
          <!-- From Uiverse.io by Type-Delta (Josh Comeau Sun/Moon pure CSS clone) -->
          <label
            for="themeToggle"
            class="themeToggle st-sunMoonThemeToggleBtn"
            title="Cambiar tema claro / oscuro"
            aria-label="Cambiar tema claro / oscuro"
          >
            <input
              type="checkbox"
              id="themeToggle"
              class="themeToggleInput"
              v-model="isDark"
            />
            <svg
              width="18"
              height="18"
              viewBox="0 0 20 20"
              fill="currentColor"
              stroke="none"
            >
              <mask id="moon-mask">
                <rect x="0" y="0" width="20" height="20" fill="white"></rect>
                <circle cx="11" cy="3" r="8" fill="black"></circle>
              </mask>
              <circle
                class="sunMoon"
                cx="10"
                cy="10"
                r="8"
                mask="url(#moon-mask)"
              ></circle>
              <g>
                <circle class="sunRay sunRay1" cx="18" cy="10" r="1.5"></circle>
                <circle class="sunRay sunRay2" cx="14" cy="16.928" r="1.5"></circle>
                <circle class="sunRay sunRay3" cx="6" cy="16.928" r="1.5"></circle>
                <circle class="sunRay sunRay4" cx="2" cy="10" r="1.5"></circle>
                <circle class="sunRay sunRay5" cx="6" cy="3.1718" r="1.5"></circle>
                <circle class="sunRay sunRay6" cx="14" cy="3.1718" r="1.5"></circle>
              </g>
            </svg>
          </label>

       

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
    <dialog v-if="helpOpen" class="dialog" open>
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
    </dialog>

    <!-- Global Settings Modal -->
    <dialog v-if="settingsOpen" class="dialog" open>
      <div class="dialog-header">
        <h2>Tu espacio de trabajo</h2>
        <button class="icon-button" aria-label="Cerrar" @click="settingsOpen = false"><X :size="18" /></button>
      </div>
      <div class="detail-body">
        <div class="client-cell">
          <span class="avatar">MR</span>
          <div>
            <h3>Martín Rindlisbacher</h3>
            <p class="muted">Administrador · Taller Central</p>
          </div>
        </div>
        <h3>Integraciones</h3>
        <div class="settings-row">
          <span>ARCA · Facturación electrónica</span><span class="badge neutral">Demo</span>
        </div>
        <div class="settings-row">
          <span>WhatsApp · Avisos de retiro</span><span class="badge neutral">Demo</span>
        </div>
        <h3>Acerca de este espacio</h3>
        <p class="muted">
          Datos de ejemplo para explorar el flujo del taller. Esta maqueta no realiza envíos ni emite comprobantes fiscales.
        </p>
        <button class="button" @click="settingsOpen = false">Entendido</button>
      </div>
    </dialog>
  </div>
</template>
