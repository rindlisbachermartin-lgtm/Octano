<script setup lang="ts">
import {
  Smartphone,
  QrCode,
  Scan,
  Camera,
  ArrowRight,
  ExternalLink,
  RefreshCw,
  CheckCircle2,
  AlertCircle,
  Car,
  ShieldCheck,
  Sparkles,
  Copy,
  Check,
  Info,
  Eye,
  Play,
  ChevronRight,
  Layers,
  Droplets,
  RotateCcw,
  Maximize2
} from 'lucide-vue-next'
import QRCode from 'qrcode'
import type { Vehicle } from '~/types'

const route = useRoute()
const router = useRouter()
const { db, client, vehicleName } = useDatabase()
const { notify } = useWorkshopToast()

// List of vehicles with or without QR
const vehiclesWithQr = computed(() => {
  return db.value.vehicles.map((v) => {
    const owner = client(v.client)
    return {
      ...v,
      clientName: owner?.name || 'Cliente registrado',
    }
  })
})

const selectedVehicleId = ref<number>(
  Number(route.query.vehicle) && db.value.vehicles.some((v) => v.id === Number(route.query.vehicle))
    ? Number(route.query.vehicle)
    : (db.value.vehicles.find((v) => !!v.qrCode)?.id || 1)
)

const selectedVehicle = computed<Vehicle | undefined>(() =>
  db.value.vehicles.find((v) => v.id === selectedVehicleId.value)
)

const selectedQrCode = computed(() => selectedVehicle.value?.qrCode || 'OCT-2041')

// QR Code image generator for actual scanning with physical phone
const physicalQrImage = ref('')
const stickerQrImage = ref('')
const copied = ref(false)

async function generateQrCodes() {
  if (!import.meta.client) return
  try {
    const origin = window.location.origin
    const url = `${origin}/qr/${selectedQrCode.value}`
    physicalQrImage.value = await QRCode.toDataURL(url, {
      margin: 1,
      width: 220,
      color: { dark: '#0f172a', light: '#ffffff' }
    })
    stickerQrImage.value = await QRCode.toDataURL(url, {
      margin: 0,
      width: 130,
      color: { dark: '#090d16', light: '#ffffff' }
    })
  } catch (err) {
    console.error('Error generating QR', err)
  }
}

// Phone Simulator State
type SimulatorStep = 'camera' | 'detected' | 'sheet'
const simulatorStep = ref<SimulatorStep>('camera')
const autoRedirect = ref(true)
let autoTimer: any = null

function restartScanSimulation() {
  if (!import.meta.client) return
  if (autoTimer) clearTimeout(autoTimer)
  simulatorStep.value = 'camera'

  // Step 1 -> Step 2: Detection banner pops up after 1.1s
  autoTimer = setTimeout(() => {
    simulatorStep.value = 'detected'

    // Step 2 -> Step 3: If autoRedirect is true, open the sheet after another 1.4s
    if (autoRedirect.value) {
      autoTimer = setTimeout(() => {
        simulatorStep.value = 'sheet'
      }, 1400)
    }
  }, 1100)
}

function handleOpenSheetManually() {
  if (autoTimer) clearTimeout(autoTimer)
  simulatorStep.value = 'sheet'
}

watch(selectedQrCode, () => {
  generateQrCodes()
  restartScanSimulation()
})

function selectVehicle(id: number) {
  selectedVehicleId.value = id
}

function copyLiveUrl() {
  if (!import.meta.client) return
  const origin = window.location.origin
  const url = `${origin}/qr/${selectedQrCode.value}`
  navigator.clipboard.writeText(url)
  copied.value = true
  notify('Enlace copiado al portapapeles.')
  setTimeout(() => {
    copied.value = false
  }, 2200)
}

// Fullscreen direct view
function openStandaloneFicha() {
  if (selectedVehicle.value) {
    window.open(`/ficha/${selectedVehicle.value.id}`, '_blank')
  }
}

function openStandaloneQr() {
  window.open(`/qr/${selectedQrCode.value}`, '_blank')
}

onMounted(() => {
  generateQrCodes()
  restartScanSimulation()
})

onBeforeUnmount(() => {
  if (autoTimer) clearTimeout(autoTimer)
})
</script>

<template>
  <div class="page-content demo-page-container">
    <!-- Top Header & Breadcrumb -->
    <header class="demo-heading">
      <div class="demo-badge-row">
        <span class="demo-pill">
          <Sparkles :size="13" /> SIMULADOR EN VIVO · EXPERIENCIA QR
        </span>
        <span class="live-indicator">
          <span class="pulse-dot"></span> Interactivo
        </span>
      </div>
      <h1 class="demo-title">Escaneo de QR con Vehículo Asignado</h1>
      <p class="demo-description">
        Descubrí exactamente qué ve un cliente o un mecánico al escanear con la cámara de su celular el sticker colocado en el vehículo.
        Sin descargar apps ni registrarse: una única tarjeta limpia con el último servicio, cambio de distribución y botón de "Ver detalle" para desplegar el aceite y filtros utilizados.
      </p>
    </header>

    <!-- Main Workspace Layout: Controls & Mockup -->
    <div class="demo-workspace">
      <!-- LEFT COLUMN: Simulator Controls, Vehicle Picker, Physical QR -->
      <section class="demo-sidebar-controls">
        <!-- Card 1: Selector de Vehículo -->
        <div class="control-card">
          <div class="control-card-header">
            <div>
              <h3>1. Seleccioná un vehículo</h3>
              <p class="control-subtitle">Cambiá de auto para probar distintos historiales y configuraciones.</p>
            </div>
          </div>

          <div class="vehicle-selector-grid">
            <button
              v-for="v in vehiclesWithQr"
              :key="v.id"
              class="vehicle-select-item"
              :class="{ active: selectedVehicleId === v.id }"
              @click="selectVehicle(v.id)"
            >
              <div class="v-select-top">
                <span class="plate small-plate">{{ v.plate }}</span>
                <span v-if="v.qrCode" class="qr-status-badge assigned">
                  <CheckCircle2 :size="11" /> {{ v.qrCode }}
                </span>
                <span v-else class="qr-status-badge unassigned">
                  Sin QR
                </span>
              </div>
              <strong class="v-select-model">{{ v.brand }} {{ v.model }}</strong>
              <div class="v-select-footer">
                <span>{{ v.year }} · {{ v.engine }}</span>
                <span class="v-km">{{ v.km?.toLocaleString('es-AR') }} km</span>
              </div>
            </button>
          </div>
        </div>

        <!-- Card 2: Controles del Simulador Móvil -->
        <div class="control-card">
          <div class="control-card-header">
            <div>
              <h3>2. Controles de la simulación</h3>
              <p class="control-subtitle">Interacting con el visor telefónico de la derecha.</p>
            </div>
          </div>

          <div class="demo-action-buttons">
            <button class="button primary" @click="restartScanSimulation">
              <RotateCcw :size="16" /> Re-iniciar escaneo con cámara
            </button>

            <button
              v-if="simulatorStep !== 'sheet'"
              class="button outlined"
              @click="handleOpenSheetManually"
            >
              <Eye :size="16" /> Ver Ficha Técnica directa
            </button>
            <button
              v-else
              class="button outlined"
              @click="simulatorStep = 'camera'"
            >
              <Camera :size="16" /> Volver a enfocar sticker
            </button>
          </div>

          <div class="toggle-option-row">
            <label class="toggle-label" for="auto-redirect-toggle">
              <input
                id="auto-redirect-toggle"
                v-model="autoRedirect"
                type="checkbox"
                class="checkbox-input"
              />
              <span>Abrir ficha automáticamente al detectar el QR (1.4s)</span>
            </label>
          </div>

          <!-- Summary info of selected vehicle -->
          <div v-if="selectedVehicle" class="vehicle-quick-summary">
            <div class="summary-line">
              <span class="summary-label">Vehículo activo:</span>
              <strong>{{ selectedVehicle.brand }} {{ selectedVehicle.model }} ({{ selectedVehicle.plate }})</strong>
            </div>
            <div class="summary-line">
              <span class="summary-label">Código QR asignado:</span>
              <span class="qr-pill-strong">{{ selectedQrCode }}</span>
            </div>
            <div class="summary-line">
              <span class="summary-label">Aceite homologado:</span>
              <span>{{ selectedVehicle.lastService?.oil || 'Sintético 5W-30' }}</span>
            </div>
            <div class="summary-line">
              <span class="summary-label">Próximo service:</span>
              <strong class="highlight-green">
                A los {{ ((selectedVehicle.lastService?.km || selectedVehicle.km) + 10000).toLocaleString('es-AR') }} km
              </strong>
            </div>
          </div>
        </div>

        <!-- Card 3: Escaneo con Teléfono Real (Físico) -->
        <div class="control-card physical-qr-card">
          <div class="control-card-header">
            <div>
              <div class="card-icon-tag"><Smartphone :size="15" /> OPCIONAL</div>
              <h3 style="margin-top: 4px">Probá con tu celular real</h3>
              <p class="control-subtitle">
                Apuntá la cámara nativa de tu teléfono a este código para abrir la ficha en tu pantalla:
              </p>
            </div>
          </div>

          <div class="physical-qr-box">
            <div class="qr-frame">
              <img
                v-if="physicalQrImage"
                :src="physicalQrImage"
                :alt="`QR para ${selectedQrCode}`"
                class="qr-img"
              />
              <div v-else class="qr-skeleton">Cargando QR…</div>
              <span class="qr-caption">{{ selectedQrCode }} · {{ selectedVehicle?.plate }}</span>
            </div>

            <div class="physical-qr-actions">
              <div class="url-display">
                <code>/qr/{{ selectedQrCode }}</code>
                <button
                  class="icon-btn-compact"
                  title="Copiar URL"
                  @click="copyLiveUrl"
                >
                  <Check v-if="copied" :size="14" style="color: #10b981" />
                  <Copy v-else :size="14" />
                </button>
              </div>

              <div class="button-links">
                <button class="button small outlined" @click="openStandaloneQr">
                  Abrir link de escaneo <ExternalLink :size="13" />
                </button>
                <button class="button small outlined" @click="openStandaloneFicha">
                  Ficha a pantalla completa <Maximize2 :size="13" />
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Card 4: Guía didáctica / Explicación del taller -->
        <div class="control-card info-card">
          <h4>¿Cómo funciona la experiencia en el mundo real?</h4>
          <ol class="workflow-steps">
            <li>
              <strong>1. El taller imprime el sticker:</strong>
              Genera la plantilla con QRs únicos desde la sección de Vehículos.
            </li>
            <li>
              <strong>2. Se pega en el marco de la puerta:</strong>
              El mecánico escanea el sticker una sola vez para vincular la patente del auto.
            </li>
            <li>
              <strong>3. Acceso instantáneo para siempre:</strong>
              El cliente o mecánico escanea el código en 1 segundo y accede a todo el historial sin llamar por teléfono al taller.
            </li>
          </ol>
        </div>
      </section>

      <!-- RIGHT COLUMN: Interactive Smartphone Mockup -->
      <section class="demo-mockup-area">
        <div class="phone-wrapper">
          <!-- Smartphone Outer Chassis -->
          <div class="phone-frame">
            <!-- Ambient phone glare reflection -->
            <div class="phone-glare"></div>

            <!-- Dynamic Island / Top Speaker -->
            <div class="phone-island">
              <span class="island-camera"></span>
            </div>

            <!-- Mobile Status Bar -->
            <div class="phone-status-bar">
              <span class="status-time">10:42</span>
              <div class="status-icons">
                <span class="status-net">5G</span>
                <span class="status-battery">
                  <span class="battery-level"></span>
                </span>
              </div>
            </div>

            <!-- Mobile In-Browser Mini Navigation Bar -->
            <div class="phone-browser-bar">
              <div class="browser-address">
                <ShieldCheck class="ssl-lock" :size="12" aria-hidden="true" />
                <span v-if="simulatorStep === 'sheet'" class="browser-url">
                  octano.taller/ficha/{{ selectedVehicle?.id }}
                </span>
                <span v-else class="browser-url">
                  octano.taller/qr/{{ selectedQrCode }}
                </span>
              </div>
              <button
                class="browser-reload-btn"
                title="Reiniciar escaneo"
                @click="restartScanSimulation"
              >
                <RotateCcw :size="12" />
              </button>
            </div>

            <!-- Phone Screen Viewport Area -->
            <div class="phone-screen">
              <ClientOnly>
                <!-- STATE 1: CAMERA & SCANNER VIEWFINDER -->
                <div
                  v-if="simulatorStep === 'camera' || simulatorStep === 'detected'"
                  class="camera-viewfinder"
                >
                  <!-- Camera top HUD -->
                  <div class="camera-hud">
                    <span class="hud-pill">
                      <Camera :size="12" /> CÁMARA
                    </span>
                    <span class="hud-text">Apuntá al sticker del auto</span>
                  </div>

                  <!-- Car door frame background simulation -->
                  <div class="car-sticker-stage">
                    <!-- The Physical Sticker Mockup on car door pillar -->
                    <div class="vehicle-sticker-card" :class="{ 'sticker-targeted': simulatorStep === 'detected' }">
                      <div class="sticker-top-row">
                        <div class="sticker-brand">
                          <span class="sticker-dot">●</span> TALLER CENTRAL
                        </div>
                        <span class="sticker-plate">{{ selectedVehicle?.plate }}</span>
                      </div>

                      <div class="sticker-main">
                        <div class="sticker-qr-wrap">
                          <img
                            v-if="stickerQrImage"
                            :src="stickerQrImage"
                            alt="QR Sticker"
                            class="sticker-qr-img"
                          />
                        </div>
                        <div class="sticker-details">
                          <strong class="sticker-code">{{ selectedQrCode }}</strong>
                          <p class="sticker-hint">
                            {{ selectedVehicle?.brand }} {{ selectedVehicle?.model }} · {{ selectedVehicle?.year }}
                          </p>
                          <span class="sticker-warranty-tag">HISTORIAL TALLER</span>
                        </div>
                      </div>

                      <div class="sticker-bottom">
                        <span>Mantenimiento Preventivo</span>
                        <span>sistema octano</span>
                      </div>
                    </div>

                    <!-- Scanning Reticle / Laser Viewfinder -->
                    <div class="scan-reticle" :class="{ detected: simulatorStep === 'detected' }">
                      <div class="reticle-corner top-left"></div>
                      <div class="reticle-corner top-right"></div>
                      <div class="reticle-corner bottom-left"></div>
                      <div class="reticle-corner bottom-right"></div>

                      <!-- Scanning animated laser line -->
                      <div class="scan-laser-line"></div>
                    </div>
                  </div>

                  <!-- Detection Alert Banner (Native iOS/Android camera notification style) -->
                  <Transition name="slide-up">
                  <div
                    v-if="simulatorStep === 'detected'"
                    class="detection-banner"
                    @click="handleOpenSheetManually"
                  >
                    <div class="banner-icon-bubble">
                      <QrCode :size="20" />
                    </div>
                    <div class="banner-content">
                      <span class="banner-origin">octano.app · Código detectado</span>
                      <strong class="banner-target">
                        Taller Central · {{ selectedVehicle?.brand }} {{ selectedVehicle?.model }} ({{ selectedVehicle?.plate }})
                      </strong>
                      <span class="banner-cta">
                        Tocar para abrir historial <ChevronRight :size="13" />
                      </span>
                    </div>
                  </div>
                </Transition>

                <!-- Camera bottom controls -->
                <div class="camera-footer">
                  <span class="scan-status-text">
                    <span v-if="simulatorStep === 'camera'" class="scanning-dots">
                      Escaneando código QR…
                    </span>
                    <span v-else class="detected-text">
                      <CheckCircle2 :size="14" /> ¡Código {{ selectedQrCode }} detectado!
                    </span>
                  </span>

                  <button
                    v-if="simulatorStep === 'detected'"
                    class="button small primary banner-open-btn"
                    @click="handleOpenSheetManually"
                  >
                    Abrir ficha técnica <ArrowRight :size="14" />
                  </button>
                </div>
              </div>

              <!-- STATE 2: LIVE DIGITAL VEHICLE SHEET (FICHA TÉCNICA) -->
              <div v-else class="sheet-viewport">
                <!-- Mini quick bar inside mobile view -->
                <div class="mobile-sheet-controls">
                  <button
                    class="mobile-back-btn"
                    title="Volver a escanear"
                    @click="simulatorStep = 'camera'"
                  >
                    ← Escanear de nuevo
                  </button>
                  <span class="mobile-vehicle-tag">{{ selectedVehicle?.plate }}</span>
                  <button
                    class="mobile-ext-btn"
                    title="Abrir en pestaña nueva"
                    @click="openStandaloneFicha"
                  >
                    <ExternalLink :size="13" />
                  </button>
                </div>

                <!-- Seamless interactive Iframe rendering the real /ficha/[id] page -->
                <div class="iframe-container">
                  <iframe
                    v-if="selectedVehicle"
                    :src="`/ficha/${selectedVehicle.id}?embedded=1`"
                    class="ficha-iframe"
                    title="Ficha digital del vehículo"
                  ></iframe>
                </div>
              </div>
              <template #fallback>
                <div class="camera-viewfinder" style="display: flex; align-items: center; justify-content: center;">
                  <span class="hud-pill"><Camera :size="14" /> Iniciando simulador…</span>
                </div>
              </template>
            </ClientOnly>
          </div>

            <!-- Mobile Home Bar Indicator -->
            <div class="phone-home-indicator" @click="simulatorStep = 'camera'"></div>
          </div>

          <!-- Bottom caption below smartphone frame -->
          <div class="phone-caption">
            <div class="caption-badge">
              <span class="badge-dot"></span> Visor Móvil Interactivo (390 x 844 px)
            </div>
            <p>
              Podés desplazarte verticalmente dentro de la pantalla del celular para ver todos los detalles del vehículo.
            </p>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.demo-page-container {
  max-width: 1320px;
  margin: 0 auto;
  padding: 24px 20px 60px;
}

/* Header */
.demo-heading {
  margin-bottom: 28px;
}

.demo-badge-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.demo-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.8px;
  color: #2563eb;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  padding: 4px 10px;
  border-radius: 9999px;
  text-transform: uppercase;
}

.live-indicator {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #10b981;
}

.pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #10b981;
  box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7);
  animation: pulse-green 1.8s infinite;
}

@keyframes pulse-green {
  0% {
    transform: scale(0.95);
    box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7);
  }
  70% {
    transform: scale(1);
    box-shadow: 0 0 0 8px rgba(16, 185, 129, 0);
  }
  100% {
    transform: scale(0.95);
    box-shadow: 0 0 0 0 rgba(16, 185, 129, 0);
  }
}

.demo-title {
  font-size: 32px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.6px;
  margin: 0 0 10px 0;
}

.demo-description {
  font-size: 15px;
  line-height: 1.55;
  color: #64748b;
  max-width: 880px;
  margin: 0;
}

/* 2-Column Workspace Grid */
.demo-workspace {
  display: grid;
  grid-template-columns: 1fr 440px;
  gap: 36px;
  align-items: start;
}

@media (max-width: 1080px) {
  .demo-workspace {
    grid-template-columns: 1fr;
  }
}

/* LEFT COLUMN: Controls */
.demo-sidebar-controls {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.control-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 22px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
}

.control-card-header h3 {
  margin: 0 0 4px 0;
  font-size: 16px;
  font-weight: 750;
  color: #0f172a;
}

.control-subtitle {
  margin: 0 0 16px 0;
  font-size: 13px;
  color: #64748b;
}

/* Vehicle Selector Cards */
.vehicle-selector-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.vehicle-select-item {
  display: flex;
  flex-direction: column;
  text-align: left;
  background: #f8fafc;
  border: 1.5px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px 14px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.vehicle-select-item:hover {
  background: #f1f5f9;
  border-color: #cbd5e1;
  transform: translateY(-1px);
}

.vehicle-select-item.active {
  background: #eff6ff;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.v-select-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.small-plate {
  font-size: 11px;
  padding: 2px 6px;
}

.qr-status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
}

.qr-status-badge.assigned {
  background: #dcfce7;
  color: #15803d;
}

.qr-status-badge.unassigned {
  background: #f1f5f9;
  color: #64748b;
}

.v-select-model {
  font-size: 14px;
  color: #0f172a;
  margin-bottom: 6px;
}

.v-select-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #64748b;
}

.v-km {
  font-weight: 600;
  color: #334155;
}

/* Simulation Controls */
.demo-action-buttons {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.toggle-option-row {
  margin-bottom: 16px;
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.toggle-label {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  font-weight: 500;
  color: #334155;
  cursor: pointer;
}

.checkbox-input {
  width: 17px;
  height: 17px;
  accent-color: #2563eb;
  cursor: pointer;
}

/* Vehicle Quick Summary */
.vehicle-quick-summary {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #f8fafc;
  border-left: 3px solid #3b82f6;
  padding: 12px 14px;
  border-radius: 0 8px 8px 0;
  font-size: 13px;
}

.summary-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.summary-label {
  color: #64748b;
}

.qr-pill-strong {
  font-family: monospace;
  font-weight: 700;
  background: #e2e8f0;
  color: #0f172a;
  padding: 2px 7px;
  border-radius: 4px;
}

.highlight-green {
  color: #15803d;
}

/* Physical QR Card */
.card-icon-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.8px;
  color: #d97706;
  background: #fef3c7;
  padding: 2px 8px;
  border-radius: 4px;
  margin-bottom: 4px;
}

.physical-qr-box {
  display: flex;
  gap: 20px;
  align-items: center;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 16px;
}

@media (max-width: 600px) {
  .physical-qr-box {
    flex-direction: column;
    align-items: flex-start;
  }
}

.qr-frame {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #ffffff;
  padding: 10px;
  border-radius: 10px;
  border: 1px solid #cbd5e1;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
  flex-shrink: 0;
}

.qr-img {
  width: 140px;
  height: 140px;
  display: block;
}

.qr-caption {
  font-size: 11px;
  font-family: monospace;
  font-weight: 700;
  color: #334155;
  margin-top: 6px;
}

.physical-qr-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
}

.url-display {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 6px 10px;
}

.url-display code {
  font-size: 12px;
  color: #2563eb;
  font-weight: 600;
}

.icon-btn-compact {
  padding: 4px;
  border-radius: 4px;
  color: #64748b;
  display: flex;
  align-items: center;
}

.icon-btn-compact:hover {
  background: #f1f5f9;
  color: #0f172a;
}

.button-links {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

/* Didactic Workflow Card */
.info-card h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 750;
  color: #0f172a;
}

.workflow-steps {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.workflow-steps li {
  font-size: 13px;
  color: #475569;
  line-height: 1.5;
  padding-left: 0;
}

.workflow-steps strong {
  display: block;
  color: #0f172a;
  margin-bottom: 2px;
}

/* RIGHT COLUMN: Realistic Smartphone Mockup */
.demo-mockup-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: sticky;
  top: 24px;
}

.phone-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  max-width: 400px;
}

/* Outer chassis */
.phone-frame {
  position: relative;
  width: 390px;
  height: 780px;
  background: #090d16;
  border: 10px solid #1a2234;
  border-radius: 50px;
  box-shadow:
    0 25px 60px -15px rgba(15, 23, 42, 0.4),
    0 0 0 1px rgba(255, 255, 255, 0.1) inset,
    0 12px 24px rgba(0, 0, 0, 0.25);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  user-select: none;
}

@media (max-width: 440px) {
  .phone-frame {
    width: 320px;
    height: 640px;
    border-radius: 36px;
    border-width: 8px;
  }
}

/* Subtle corner glare */
.phone-glare {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 140px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.08) 0%, rgba(255, 255, 255, 0) 60%);
  pointer-events: none;
  z-index: 40;
}

/* Dynamic Island */
.phone-island {
  position: absolute;
  top: 11px;
  left: 50%;
  transform: translateX(-50%);
  width: 96px;
  height: 24px;
  background: #000000;
  border-radius: 20px;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-right: 12px;
}

.island-camera {
  width: 10px;
  height: 10px;
  background: #111827;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 50%;
}

/* Mobile Status Bar */
.phone-status-bar {
  height: 44px;
  padding: 10px 24px 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  font-weight: 600;
  color: #f8fafc;
  z-index: 45;
  background: transparent;
}

.status-icons {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-net {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.status-battery {
  width: 20px;
  height: 10px;
  border: 1.5px solid #f8fafc;
  border-radius: 3px;
  padding: 1px;
  display: flex;
  align-items: center;
}

.battery-level {
  width: 80%;
  height: 100%;
  background: #f8fafc;
  border-radius: 1px;
}

/* Mini in-phone browser bar */
.phone-browser-bar {
  background: #1e293b;
  border-bottom: 1px solid #334155;
  padding: 6px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  z-index: 35;
}

.browser-address {
  display: flex;
  align-items: center;
  gap: 6px;
  background: #0f172a;
  padding: 4px 10px;
  border-radius: 8px;
  flex: 1;
  overflow: hidden;
}

.ssl-lock {
  font-size: 11px;
}

.browser-url {
  font-size: 11px;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: monospace;
}

.browser-reload-btn {
  color: #94a3b8;
  padding: 4px;
  border-radius: 4px;
  display: flex;
  align-items: center;
}

.browser-reload-btn:hover {
  color: #ffffff;
}

/* Screen Viewport */
.phone-screen {
  flex: 1;
  background: #f8fafc;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* STATE 1: CAMERA VIEWFINDER */
.camera-viewfinder {
  flex: 1;
  background: radial-gradient(circle at center, #1e293b 0%, #090d16 100%);
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 16px;
  overflow: hidden;
}

.camera-hud {
  display: flex;
  justify-content: space-between;
  align-items: center;
  z-index: 20;
}

.hud-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(0, 0, 0, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: #ffffff;
  font-size: 10px;
  font-weight: 700;
  padding: 4px 8px;
  border-radius: 6px;
  letter-spacing: 0.5px;
}

.hud-text {
  font-size: 11px;
  color: #cbd5e1;
  font-weight: 500;
}

/* Stage where the sticker is aimed */
.car-sticker-stage {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px 0;
  z-index: 10;
}

/* The Sticker Mockup */
.vehicle-sticker-card {
  width: 250px;
  background: #ffffff;
  border: 2px solid #cbd5e1;
  border-radius: 12px;
  padding: 12px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.4);
  transition: transform 0.3s ease, border-color 0.3s ease;
}

.vehicle-sticker-card.sticker-targeted {
  border-color: #38bdf8;
  transform: scale(1.02);
  box-shadow: 0 0 20px rgba(56, 189, 248, 0.3);
}

.sticker-top-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px dashed #e2e8f0;
}

.sticker-brand {
  font-size: 11px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: 0.5px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.sticker-dot {
  color: #2563eb;
  font-size: 9px;
}

.sticker-plate {
  font-size: 11px;
  font-family: monospace;
  font-weight: 800;
  background: #0f172a;
  color: #ffffff;
  padding: 1px 6px;
  border-radius: 3px;
}

.sticker-main {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 8px;
}

.sticker-qr-wrap {
  width: 76px;
  height: 76px;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  padding: 2px;
  border-radius: 6px;
  flex-shrink: 0;
}

.sticker-qr-img {
  width: 100%;
  height: 100%;
  display: block;
}

.sticker-details {
  display: flex;
  flex-direction: column;
}

.sticker-code {
  font-size: 15px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.3px;
}

.sticker-hint {
  font-size: 9.5px;
  color: #64748b;
  margin: 2px 0 6px 0;
  line-height: 1.3;
}

.sticker-warranty-tag {
  font-size: 8.5px;
  font-weight: 800;
  color: #15803d;
  background: #dcfce7;
  padding: 2px 5px;
  border-radius: 3px;
  width: fit-content;
}

.sticker-bottom {
  display: flex;
  justify-content: space-between;
  font-size: 8.5px;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding-top: 4px;
  border-top: 1px solid #f1f5f9;
}

/* Scanner Reticle & Laser */
.scan-reticle {
  position: absolute;
  width: 270px;
  height: 200px;
  pointer-events: none;
  transition: all 0.3s ease;
}

.reticle-corner {
  position: absolute;
  width: 24px;
  height: 24px;
  border-color: #38bdf8;
  border-style: solid;
  border-width: 0;
}

.scan-reticle.detected .reticle-corner {
  border-color: #10b981;
}

.reticle-corner.top-left {
  top: 0;
  left: 0;
  border-top-width: 3px;
  border-left-width: 3px;
  border-top-left-radius: 8px;
}

.reticle-corner.top-right {
  top: 0;
  right: 0;
  border-top-width: 3px;
  border-right-width: 3px;
  border-top-right-radius: 8px;
}

.reticle-corner.bottom-left {
  bottom: 0;
  left: 0;
  border-bottom-width: 3px;
  border-left-width: 3px;
  border-bottom-left-radius: 8px;
}

.reticle-corner.bottom-right {
  bottom: 0;
  right: 0;
  border-bottom-width: 3px;
  border-right-width: 3px;
  border-bottom-right-radius: 8px;
}

/* Laser sweep animation */
.scan-laser-line {
  position: absolute;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent 0%, #38bdf8 50%, transparent 100%);
  box-shadow: 0 0 12px 2px #38bdf8;
  animation: laser-sweep 2s ease-in-out infinite alternate;
}

@keyframes laser-sweep {
  0% {
    top: 10px;
    opacity: 0.8;
  }
  100% {
    top: 190px;
    opacity: 0.8;
  }
}

/* Detection Banner (iOS / Android style alert) */
.detection-banner {
  background: rgba(15, 23, 42, 0.94);
  backdrop-filter: blur(16px);
  border: 1px solid rgba(56, 189, 248, 0.4);
  border-radius: 14px;
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.5);
  z-index: 30;
  transition: transform 0.15s ease, background 0.15s ease;
}

.detection-banner:hover {
  background: rgba(30, 41, 59, 0.98);
  transform: translateY(-2px);
}

.banner-icon-bubble {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #2563eb;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.banner-content {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.banner-origin {
  font-size: 10px;
  color: #38bdf8;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.banner-target {
  font-size: 13px;
  color: #ffffff;
  margin: 1px 0;
}

.banner-cta {
  font-size: 11px;
  color: #94a3b8;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

/* Slide Up Transition */
.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.slide-up-enter-from {
  opacity: 0;
  transform: translateY(20px);
}

.slide-up-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* Camera Footer */
.camera-footer {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  z-index: 20;
}

.scan-status-text {
  font-size: 12px;
  color: #94a3b8;
}

.detected-text {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #10b981;
  font-weight: 700;
}

.banner-open-btn {
  width: 100%;
}

/* STATE 2: LIVE SHEET VIEWPORT */
.sheet-viewport {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #ffffff;
}

.mobile-sheet-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f1f5f9;
  border-bottom: 1px solid #e2e8f0;
  font-size: 11px;
}

.mobile-back-btn {
  font-size: 11px;
  font-weight: 600;
  color: #2563eb;
  padding: 4px 6px;
  border-radius: 4px;
}

.mobile-back-btn:hover {
  background: #e2e8f0;
}

.mobile-vehicle-tag {
  font-weight: 800;
  font-family: monospace;
  background: #0f172a;
  color: #ffffff;
  padding: 1px 6px;
  border-radius: 3px;
}

.mobile-ext-btn {
  color: #64748b;
  padding: 4px;
  border-radius: 4px;
}

.mobile-ext-btn:hover {
  color: #0f172a;
  background: #e2e8f0;
}

.iframe-container {
  flex: 1;
  height: 100%;
  position: relative;
  overflow: hidden;
}

.ficha-iframe {
  width: 100%;
  height: 100%;
  border: 0;
  background: #ffffff;
  display: block;
}

/* Home Indicator Bar */
.phone-home-indicator {
  position: absolute;
  bottom: 8px;
  left: 50%;
  transform: translateX(-50%);
  width: 130px;
  height: 4px;
  background: #94a3b8;
  border-radius: 4px;
  z-index: 50;
  cursor: pointer;
  transition: background 0.15s ease;
}

.phone-home-indicator:hover {
  background: #ffffff;
}

/* Phone Caption */
.phone-caption {
  margin-top: 14px;
  text-align: center;
}

.caption-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 700;
  color: #475569;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  padding: 4px 10px;
  border-radius: 9999px;
  margin-bottom: 6px;
}

.badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #2563eb;
}

.phone-caption p {
  font-size: 12px;
  color: #94a3b8;
  margin: 0;
}

:global(html.dark .demo-title),
:global(html.dark .control-card-header h3),
:global(html.dark .v-select-model),
:global(html.dark .info-card h4),
:global(html.dark .workflow-steps strong) {
  color: #f5f5f7;
}

:global(html.dark .demo-description),
:global(html.dark .control-subtitle),
:global(html.dark .v-select-footer),
:global(html.dark .summary-label),
:global(html.dark .workflow-steps li),
:global(html.dark .phone-caption),
:global(html.dark .v-km) {
  color: #a1a1a6;
}

:global(html.dark .control-card) {
  background: #252528;
  border-color: #3a3a3c;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
}

:global(html.dark .vehicle-select-item),
:global(html.dark .toggle-option-row),
:global(html.dark .vehicle-quick-summary),
:global(html.dark .physical-qr-box),
:global(html.dark .url-display) {
  background: #202023;
  border-color: #3a3a3c;
  color: #f5f5f7;
}

:global(html.dark .vehicle-select-item:hover) {
  background: #303034;
  border-color: #636366;
}

:global(html.dark .vehicle-select-item.active) {
  background: rgba(10, 132, 255, 0.16);
  border-color: #0a84ff;
}

:global(html.dark .toggle-label),
:global(html.dark .qr-pill-strong) {
  color: #d1d1d6;
}

:global(html.dark .qr-pill-strong),
:global(html.dark .qr-status-badge.unassigned),
:global(html.dark .caption-badge) {
  background: #3a3a3c;
  border-color: #48484a;
  color: #d1d1d6;
}

:global(html.dark .qr-status-badge.assigned) {
  background: rgba(48, 209, 88, 0.16);
  color: #8ee6a7;
}

:global(html.dark .url-display code) {
  color: #64d2ff;
}

:global(html.dark .icon-btn-compact) {
  color: #a1a1a6;
}

:global(html.dark .icon-btn-compact:hover) {
  background: #3a3a3c;
  color: #f5f5f7;
}

/* The QR sticker and phone preview stay light so the code remains scannable. */
</style>
