<script setup lang="ts">
import { Gauge, ShieldCheck, QrCode, Wrench } from '@lucide/vue'
import IconBidonAceite from '~/components/icons/IconBidonAceite.vue'
import IconCorreaDistribucion from '~/components/icons/IconCorreaDistribucion.vue'
import type { FichaQrApi } from '~/utils/qrApi'

const props = defineProps<{ codigo: string }>()
const { isDark } = useTheme()
// Nuxt espera esta consulta en SSR y reutiliza el payload al hidratar.
const codigo = computed(() => props.codigo.trim().toUpperCase())
const { data: ficha, status, error: consultaError, refresh } = useFetch<FichaQrApi>(
  () => `/api/publico/qr/${encodeURIComponent(codigo.value)}`,
  { server: true, cache: 'no-store', retry: 0, timeout: 10000 },
)
const cargando = computed(() => status.value === 'pending')
const noExiste = computed(() => [400, 404].includes(consultaError.value?.statusCode ?? 0))
const error = computed(() => consultaError.value && !noExiste.value
  ? 'No pudimos consultar la ficha. Intentá nuevamente en unos instantes.' : '')
const filtros: Record<string, string> = { ACEITE: 'Filtro de aceite', AIRE: 'Filtro de aire', HABITACULO: 'Filtro de habitáculo', COMBUSTIBLE: 'Filtro de combustible' }
const kilometraje = (valor: number | null) => valor === null ? 'Sin registrar' : `${valor.toLocaleString('es-AR')} km`

</script>

<template>
  <div class="sheet-background">
    <main class="public-page qr-sheet-page">
      <header class="sheet-top-bar">
        <NuxtLink to="/" class="brand" aria-label="Octano, ir al inicio">
          <CommonOctanoLogo />
          <span class="brand-wordmark">
            <span class="brand-name">octa<span class="brand-light">no</span></span>
            <span class="brand-tagline"><span>Sistema de gestión</span><span>de talleres mecánicos</span></span>
          </span>
        </NuxtLink>
        <CommonThemeToggle v-model="isDark" />
      </header>
      <p v-if="cargando" class="panel status-card" role="status">Consultando ficha de mantenimiento…</p>
      <div v-else-if="error" class="panel status-card" role="alert"><p>{{ error }}</p><button class="button" @click="refresh()">Reintentar</button></div>
      <div v-else-if="noExiste" class="panel status-card"><QrCode :size="40" /><h1>Código QR no registrado</h1><p>Consultá al taller para verificar la etiqueta.</p></div>
      <article v-else-if="ficha?.estado === 'ASIGNADO' && ficha.vehiculo" class="vehicle-single-card">
        <header class="card-header"><p class="workshop-name">{{ ficha.tallerNombre }}</p><span class="eyebrow-badge"><ShieldCheck :size="14" /> FICHA DIGITAL DE MANTENIMIENTO</span><div class="vehicle-main-row"><div><h1>{{ vehicleLabel(ficha.vehiculo.marca, ficha.vehiculo.modelo, ficha.vehiculo.version) }}</h1><p class="muted">Año {{ ficha.vehiculo.anio }}<template v-if="vehicleFuel(ficha.vehiculo.version)"> · {{ vehicleFuel(ficha.vehiculo.version) }}</template></p></div><span class="plate large-plate">{{ ficha.vehiculo.patente }}</span></div><div class="odometer-strip"><Gauge :size="16" /><span>Último kilometraje registrado</span><strong>{{ kilometraje(ficha.vehiculo.kilometraje) }}</strong></div></header>
        <section class="maintenance-block"><div class="maintenance-heading"><IconBidonAceite :size="19" /><h2>Último service</h2></div>
          <template v-if="ficha.ultimoService"><div class="maintenance-summary"><strong>{{ kilometraje(ficha.ultimoService.kilometraje) }}</strong><span>Realizado el {{ ficha.ultimoService.fecha.split('-').reverse().join('/') }}</span></div><details><summary>Ver detalle del service</summary><dl><div><dt>Aceite utilizado</dt><dd>{{ ficha.ultimoService.aceite || 'Sin registrar' }}</dd></div><div><dt>Filtros reemplazados</dt><dd><ul v-if="ficha.ultimoService.filtros.length"><li v-for="f in ficha.ultimoService.filtros" :key="f">{{ filtros[f] || f }}</li></ul><span v-else>No se informaron filtros reemplazados.</span></dd></div></dl></details></template><p v-else class="muted">Sin service finalizado registrado.</p>
        </section>
        <section class="maintenance-block"><div class="maintenance-heading"><IconCorreaDistribucion :size="19" /><h2>Última distribución</h2></div><template v-if="ficha.ultimaDistribucion"><div class="maintenance-summary"><strong>{{ kilometraje(ficha.ultimaDistribucion.kilometraje) }}</strong><span>Realizada el {{ ficha.ultimaDistribucion.fecha.split('-').reverse().join('/') }}</span></div><details><summary>Ver detalle de distribución</summary><ul v-if="ficha.ultimaDistribucion.repuestos.length"><li v-for="(r, index) in ficha.ultimaDistribucion.repuestos" :key="index">{{ r }}</li></ul><p v-else class="muted">Sin materiales informados para este mantenimiento.</p></details></template><p v-else class="muted">Sin distribución finalizada registrada.</p></section>
        <footer class="sheet-footer"><Wrench :size="15" /><span>Los datos corresponden a mantenimientos finalizados en {{ ficha.tallerNombre }}.</span></footer>
      </article>
      <div v-else-if="ficha" class="panel status-card"><QrCode :size="40" /><h1>{{ ficha.estado === 'INUTILIZADO' ? 'Código QR inutilizado' : 'QR pendiente de asignación' }}</h1><p>{{ ficha.estado === 'INUTILIZADO' ? 'Esta etiqueta fue dada de baja. Consultá al taller por el QR actual.' : 'El taller debe vincular esta etiqueta a un vehículo para habilitar su ficha digital.' }}</p><small>{{ ficha.tallerNombre }}</small></div>
    </main>
  </div>
</template>

<style scoped>
.sheet-background { min-height: 100dvh; background: var(--surface, #f1f5f9); }
.qr-sheet-page { max-width: 760px; margin: 0 auto; padding: 28px 18px 48px; }
.sheet-top-bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 26px; }
.sheet-top-bar > .brand { margin: 0; padding: 0; gap: 7px; font-size: 29px; font-weight: 700; letter-spacing: -.5px; line-height: 1; }
.sheet-top-bar .brand > .octano-logo { width: 44px; height: 46px; }
.sheet-top-bar .brand-wordmark { display: flex; flex-direction: column; align-items: center; gap: 3px; transform: translateY(-3px); }
.sheet-top-bar .brand-tagline { display: flex; flex-direction: column; align-items: center; font-size: 6.5px; font-weight: 800; letter-spacing: 0; line-height: 1.35; text-transform: uppercase; white-space: nowrap; transform: translateY(-2px); }
.vehicle-single-card { background: #fff; color: var(--ink); border: 1px solid var(--line); border-radius: 20px; overflow: hidden; box-shadow: 0 12px 40px #0f172a0a; }
.card-header { padding: 28px; }
.workshop-name { font-size: 14px; font-weight: 700; margin-bottom: 20px; }
.eyebrow-badge { display: inline-flex; align-items: center; gap: 7px; color: #2563eb; font-size: 10px; font-weight: 700; letter-spacing: .06em; }
.vehicle-main-row { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px; margin: 20px 0; }
.vehicle-main-row h1 { font-size: 26px; letter-spacing: -.8px; }
.large-plate { font-size: 17px; }
.odometer-strip { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; padding: 13px 16px; border: 1px solid var(--line); border-radius: 10px; font-size: 12px; }
.odometer-strip strong { margin-left: auto; }
.maintenance-block { padding: 24px 28px; border-top: 1px solid var(--line); }
.maintenance-heading { display: flex; align-items: center; gap: 10px; color: #3b82f6; margin-bottom: 16px; }
.maintenance-heading h2 { font-size: 13px; text-transform: uppercase; letter-spacing: .03em; }
.maintenance-summary { display: flex; flex-direction: column; gap: 5px; margin-bottom: 16px; }
.maintenance-summary strong { font-size: 20px; }
.maintenance-summary span, .sheet-footer { font-size: 12px; color: var(--muted); }
details { padding: 12px 14px; border: 1px solid var(--line); border-radius: 10px; font-size: 13px; }
summary { cursor: pointer; font-weight: 600; }
details dl, details > ul { margin-top: 14px; }
dt { color: var(--muted); margin-bottom: 6px; }
dd { margin: 0 0 14px; }
ul { padding-left: 18px; }
li { margin: 5px 0; }
.sheet-footer { display: flex; align-items: center; gap: 8px; padding: 20px 28px; border-top: 1px solid var(--line); }
.status-card { padding: 28px; }
.status-card h1 { font-size: 22px; margin: 16px 0; }
.status-card .button { margin-top: 16px; }
:global(html.dark .sheet-background) { background: #1c1c1e; }
:global(html.dark .vehicle-single-card) { background: #252528; }
@media (max-width: 480px) { .card-header, .maintenance-block { padding: 20px; } .vehicle-main-row h1 { font-size: 22px; } }
</style>
