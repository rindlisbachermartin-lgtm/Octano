<script setup lang="ts">
import {
  Wrench,
  Search,
  Camera,
  Gauge,
  Check,
  Save,
  X,
  Clock,
  CarFront,
  FileText,
  CheckCircle2,
} from 'lucide-vue-next'
import type { Order, Photo } from '~/types'

const { db, vehicle, vehicleName, owner } = useDatabase()
const { statusClass, matches } = useHelpers()
const { notify } = useToast()

const search = ref('')
const selectedMechanicFilter = ref('Todos')
const statusFilter = ref<'activas' | 'todas' | 'finalizadas'>('activas')

// Active order working state
const activeOrderId = ref<number | null>(null)
const selectedOrder = computed(() =>
  db.value.orders.find((o) => o.id === activeOrderId.value) || null
)

// Editable fields for the selected order (km, notes, diagnosis)
const editKm = ref<number | null>(null)
const editNotes = ref('')
const editDiagnosis = ref('')
const photoSector = ref('Tablero / kilometraje')

const currentVehicle = computed(() =>
  selectedOrder.value ? vehicle(selectedOrder.value.vehicle) : null
)

const currentOwner = computed(() =>
  selectedOrder.value ? owner(selectedOrder.value.vehicle) : null
)

// Filtered orders list
const filteredOrders = computed(() => {
  return db.value.orders.filter((o) => {
    // Status filter
    if (statusFilter.value === 'activas') {
      if (['Finalizado', 'Cancelado'].includes(o.status)) return false
    } else if (statusFilter.value === 'finalizadas') {
      if (o.status !== 'Finalizado') return false
    }

    // Mechanic filter
    if (
      selectedMechanicFilter.value !== 'Todos' &&
      o.mechanic !== selectedMechanicFilter.value
    ) {
      return false
    }

    // Search filter
    const v = vehicle(o.vehicle)
    return matches(
      search.value,
      o.id,
      v?.plate,
      v?.brand,
      v?.model,
      o.service,
      o.mechanic
    )
  })
})

function selectOrder(order: Order) {
  activeOrderId.value = order.id
  const v = vehicle(order.vehicle)
  editKm.value = order.km ?? v?.km ?? null
  editNotes.value = order.notes || ''
  editDiagnosis.value = order.diagnosis || ''
  photoSector.value = 'Tablero / kilometraje'
}

function closeOrder() {
  activeOrderId.value = null
}

function saveOrderChanges(showToast = true) {
  if (!selectedOrder.value) return
  const o = selectedOrder.value

  o.notes = editNotes.value
  o.diagnosis = editDiagnosis.value

  if (editKm.value !== null && editKm.value !== undefined && editKm.value !== '') {
    o.km = Number(editKm.value)
    const v = db.value.vehicles.find((item) => item.id === o.vehicle)
    if (v) {
      v.km = Number(editKm.value)
    }
  }

  if (showToast) {
    notify('Datos guardados correctamente.')
  }
}

function startOrder() {
  if (!selectedOrder.value) return
  selectedOrder.value.status = 'En proceso'
  notify('Orden puesta en proceso de trabajo.')
}

function finishMechanicOrder() {
  if (!selectedOrder.value) return
  saveOrderChanges(false)

  const o = selectedOrder.value
  o.status = 'Finalizado'
  o.bay = null

  // Register invoice automatically if not exists
  const invoiceTotal =
    75000 + (o.parts || []).reduce((sum, p) => sum + (p.price || 0), 0)
  const invoiceId = Math.max(126, ...db.value.invoices.map((i) => i.id)) + 1
  db.value.invoices.unshift({
    id: invoiceId,
    vehicle: o.vehicle,
    description: o.service,
    total: invoiceTotal,
    type: 'B',
    status: 'Pendiente',
    date: new Date().toISOString().slice(0, 10),
  })

  // Simulated notification
  db.value.notifications.unshift({
    id: Date.now(),
    title: `El ${vehicleName(o.vehicle)} está listo para retirar`,
    detail: `OT #${o.id} · Finalizado por mecánico ${o.mechanic}`,
    read: false,
  })

  notify(`Trabajo finalizado en ${vehicleName(o.vehicle)}. Orden #${o.id} lista para entrega.`)
  closeOrder()
}

function handleAddPhoto(event: Event) {
  const input = event.target as HTMLInputElement
  if (!input.files || !selectedOrder.value) return

  Array.from(input.files).forEach((file) => {
    if (file.size > 2 * 1024 * 1024) {
      notify('La foto supera el límite de 2 MB.')
      return
    }
    const reader = new FileReader()
    reader.onload = (e) => {
      if (selectedOrder.value && selectedOrder.value.photos.length < 8) {
        selectedOrder.value.photos.push({
          sector: photoSector.value,
          data: e.target?.result as string,
        })
        notify(`Foto (${photoSector.value}) adjunta a la orden.`)
      }
    }
    reader.readAsDataURL(file)
  })
}

function removePhoto(index: number) {
  if (selectedOrder.value && selectedOrder.value.photos) {
    selectedOrder.value.photos.splice(index, 1)
    notify('Foto eliminada.')
  }
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          <span class="tiny-star">✳</span>
          TALLER CENTRAL / ÁREA MECÁNICA
        </div>
        <h1>Panel del Mecánico</h1>
      </div>

      <!-- Quick Filter for Mechanic -->
      <div class="mechanic-selector-badge">
        <span>Mecánico:</span>
        <div class="filter-tabs">
          <button
            v-for="m in ['Todos', 'Nicolás', 'Santiago']"
            :key="m"
            :class="{ active: selectedMechanicFilter === m }"
            @click="selectedMechanicFilter = m"
          >
            {{ m }}
          </button>
        </div>
      </div>
    </section>

    <!-- Toolbar -->
    <div class="list-toolbar">
      <div class="filter-tabs">
        <button
          :class="{ active: statusFilter === 'activas' }"
          @click="statusFilter = 'activas'"
        >
          Órdenes en taller
        </button>
        <button
          :class="{ active: statusFilter === 'todas' }"
          @click="statusFilter = 'todas'"
        >
          Todas
        </button>
        <button
          :class="{ active: statusFilter === 'finalizadas' }"
          @click="statusFilter = 'finalizadas'"
        >
          Finalizadas
        </button>
      </div>

      <label class="search-box">
        <Search :size="17" />
        <input
          v-model="search"
          placeholder="Buscar por patente, auto o trabajo…"
          aria-label="Buscar órdenes para mecánico"
        />
      </label>
    </div>

    <!-- Orders Cards Grid for Mechanics -->
    <div class="mechanic-cards-grid">
      <article
        v-for="o in filteredOrders"
        :key="o.id"
        class="panel mechanic-order-card"
        :class="{ 'is-selected': activeOrderId === o.id }"
        @click="selectOrder(o)"
      >
        <div class="card-top-row">
          <span class="plate">{{ vehicle(o.vehicle)?.plate }}</span>
          <span :class="['badge', statusClass(o.status)]">{{ o.status }}</span>
        </div>

        <div class="vehicle-info-block">
          <h2>{{ vehicleName(o.vehicle) }}</h2>
          <small class="mechanic-tag">Mecánico: <strong>{{ o.mechanic }}</strong></small>
        </div>

        <!-- SOLAMENTE TRABAJO A REALIZAR -->
        <div class="service-block">
          <span class="service-label">Trabajo a realizar:</span>
          <strong class="service-work-text">{{ o.service }}</strong>
        </div>

        <button class="button primary btn-select-order">
          <Wrench :size="15" />
          {{ o.status === 'Finalizado' ? 'Ver orden' : 'Seleccionar orden' }}
        </button>
      </article>
    </div>

    <div v-if="!filteredOrders.length" class="empty-state">
      <Wrench :size="38" class="muted" />
      <h3>No hay órdenes con este criterio</h3>
      <p>Probá cambiando el filtro de mecánico o buscando otra patente.</p>
    </div>

    <!-- MECHANIC WORKPAD MODAL -->
    <dialog v-if="selectedOrder" class="dialog mechanic-modal" open>
      <div class="dialog-header">
        <div>
          <span class="eyebrow">ESTACIÓN DE TRABAJO / MECÁNICA</span>
          <h2>OT #{{ selectedOrder.id }} · {{ vehicleName(selectedOrder.vehicle) }}</h2>
        </div>
        <button class="icon-button" aria-label="Cerrar" @click="closeOrder">
          <X :size="18" />
        </button>
      </div>

      <div class="mechanic-modal-body">
        <!-- Vehicle Top Overview -->
        <div class="overview-strip">
          <div class="overview-item">
            <span class="strip-label">Patente</span>
            <span class="plate small-plate">{{ currentVehicle?.plate }}</span>
          </div>
          <div class="overview-item">
            <span class="strip-label">Vehículo</span>
            <strong>{{ vehicleName(selectedOrder.vehicle) }}</strong>
          </div>
          <div class="overview-item">
            <span class="strip-label">Trabajo a realizar</span>
            <strong class="service-text-blue">{{ selectedOrder.service }}</strong>
          </div>
          <div class="overview-item">
            <span class="strip-label">Mecánico asignado</span>
            <strong>{{ selectedOrder.mechanic }}</strong>
          </div>
        </div>

        <!-- 1. KILOMETRAJE -->
        <section class="workpad-section">
          <div class="section-title-row">
            <div class="title-with-icon">
              <Gauge :size="18" class="text-blue" />
              <h3>Kilometraje del vehículo</h3>
            </div>
            <span class="helper-hint">Ingresá o actualizá el kilometraje registrado en el tablero</span>
          </div>

          <div class="km-input-wrapper">
            <label class="km-label">
              <span>Kilómetros (km):</span>
              <input
                v-model.number="editKm"
                type="number"
                min="0"
                step="1"
                placeholder="Ej: 75200"
                class="km-input"
                :disabled="selectedOrder.status === 'Finalizado'"
              />
            </label>
            <span v-if="editKm" class="km-preview-badge">
              {{ editKm.toLocaleString('es-AR') }} km
            </span>
          </div>
        </section>

        <!-- 2. PERITAJE FOTOGRÁFICO (SACAR / SUBIR FOTOS) -->
        <section class="workpad-section">
          <div class="section-title-row">
            <div class="title-with-icon">
              <Camera :size="18" class="text-blue" />
              <h3>Peritaje fotográfico</h3>
            </div>
            <span class="helper-hint">Fotos del estado del vehículo, tablero y trabajo</span>
          </div>

          <!-- Photo upload controls -->
          <div v-if="selectedOrder.status !== 'Finalizado'" class="photo-controls-row">
            <label class="sector-select-label">
              <span>Sector de la foto:</span>
              <select v-model="photoSector">
                <option value="Tablero / kilometraje">Tablero / kilometraje</option>
                <option value="Frente">Frente</option>
                <option value="Trasera">Trasera</option>
                <option value="Lateral izquierdo">Lateral izquierdo</option>
                <option value="Lateral derecho">Lateral derecho</option>
                <option value="Detalle mecánico">Detalle mecánico</option>
                <option value="Bajo chasis / motor">Bajo chasis / motor</option>
              </select>
            </label>

            <label class="button primary btn-take-photo">
              <Camera :size="16" /> Sacar / Subir foto
              <input
                type="file"
                accept="image/*"
                capture="environment"
                @change="handleAddPhoto"
              />
            </label>
          </div>

          <!-- Photos Grid -->
          <div v-if="selectedOrder.photos && selectedOrder.photos.length" class="photos-masonry">
            <figure
              v-for="(photo, index) in selectedOrder.photos"
              :key="index"
              class="photo-card"
            >
              <img :src="photo.data" :alt="photo.sector" />
              <figcaption>
                <span>{{ photo.sector }}</span>
                <button
                  v-if="selectedOrder.status !== 'Finalizado'"
                  class="icon-button small-del-btn"
                  title="Eliminar foto"
                  @click="removePhoto(index)"
                >
                  <X :size="13" />
                </button>
              </figcaption>
            </figure>
          </div>
          <div v-else class="empty-photos-box">
            <Camera :size="24" class="muted" />
            <p>Aún no se tomaron fotos de este vehículo. Podés sacar fotos del tablero o del estado general.</p>
          </div>
        </section>

        <!-- 3. OBSERVACIONES Y DIAGNÓSTICO -->
        <section class="workpad-section">
          <div class="section-title-row">
            <div class="title-with-icon">
              <FileText :size="18" class="text-blue" />
              <h3>Observaciones</h3>
            </div>
            <span class="helper-hint">Notas técnicas, recomendaciones o estado de piezas</span>
          </div>

          <div class="notes-inputs-grid">
            <label>
              <span>Observaciones del mecánico:</span>
              <textarea
                v-model="editNotes"
                rows="3"
                placeholder="Anotá detalles observados, desgastes, o recomendaciones para el cliente…"
                :disabled="selectedOrder.status === 'Finalizado'"
              ></textarea>
            </label>

            <label>
              <span>Diagnóstico técnico ejecutado:</span>
              <textarea
                v-model="editDiagnosis"
                rows="2"
                placeholder="¿Qué reparación o ajuste específico se realizó?"
                :disabled="selectedOrder.status === 'Finalizado'"
              ></textarea>
            </label>
          </div>
        </section>
      </div>

      <!-- WORKPAD FOOTER -->
      <footer class="modal-footer mechanic-modal-footer">
        <button class="button" @click="closeOrder">Cerrar</button>

        <template v-if="selectedOrder.status !== 'Finalizado'">
          <!-- Si está en espera, opción de iniciar -->
          <button
            v-if="selectedOrder.status === 'En espera'"
            class="button outlined"
            @click="startOrder"
          >
            <Clock :size="15" /> Iniciar trabajo (En proceso)
          </button>

          <!-- Guardar cambios sin finalizar -->
          <button class="button outlined" @click="saveOrderChanges(true)">
            <Save :size="15" /> Guardar datos
          </button>

          <!-- Finalizar Orden -->
          <button class="button primary btn-finish-work" @click="finishMechanicOrder">
            <Check :size="17" /> Finalizar trabajo
          </button>
        </template>

        <span v-else class="badge green" style="padding: 8px 14px; font-size: 13px;">
          <CheckCircle2 :size="15" /> Trabajo finalizado
        </span>
      </footer>
    </dialog>
  </div>
</template>

<style scoped>
.mechanic-selector-badge {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #64748b;
  font-weight: 550;
}

:global(html.dark) .mechanic-selector-badge {
  color: #8e8e93;
}

.mechanic-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
  margin-top: 14px;
}

.mechanic-order-card {
  display: flex;
  flex-direction: column;
  padding: 18px;
  border-radius: 14px;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
  border: 1px solid var(--border-color, #e2e8f0);
}

.mechanic-order-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  border-color: #0284c7;
}

.mechanic-order-card.is-selected {
  border-color: #0284c7;
  background: #f0f9ff;
}

:global(html.dark) .mechanic-order-card.is-selected {
  background: rgba(10, 132, 255, 0.12);
  border-color: #0a84ff;
}

.card-top-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.vehicle-info-block h2 {
  font-size: 18px;
  margin: 0 0 2px 0;
  color: #0f172a;
}

:global(html.dark) .vehicle-info-block h2 {
  color: #f5f5f7;
}

.mechanic-tag {
  font-size: 12px;
  color: #64748b;
}

:global(html.dark) .mechanic-tag {
  color: #8e8e93;
}

.service-block {
  margin: 14px 0 16px 0;
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px solid #f1f5f9;
  border-radius: 10px;
}

:global(html.dark) .service-block {
  background: #252528;
  border-color: rgba(255, 255, 255, 0.06);
}

.service-label {
  display: block;
  font-size: 11px;
  text-transform: uppercase;
  color: #64748b;
  letter-spacing: 0.5px;
  font-weight: 650;
  margin-bottom: 4px;
}

:global(html.dark) .service-label {
  color: #8e8e93;
}

.service-work-text {
  font-size: 15px;
  color: #0284c7;
  line-height: 1.4;
  display: block;
}

:global(html.dark) .service-work-text {
  color: #64d2ff;
}

.btn-select-order {
  margin-top: auto;
  width: 100%;
  display: inline-flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
}

/* Modal Styling */
.mechanic-modal {
  width: 95vw;
  max-width: 800px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
}

.mechanic-modal-body {
  padding: 20px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.overview-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px 14px;
}

:global(html.dark) .overview-strip {
  background: #1c1c1e;
  border-color: rgba(255, 255, 255, 0.08);
}

.overview-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.strip-label {
  font-size: 11px;
  text-transform: uppercase;
  color: #64748b;
  letter-spacing: 0.5px;
  font-weight: 600;
}

:global(html.dark) .strip-label {
  color: #8e8e93;
}

.service-text-blue {
  color: #0284c7;
}

:global(html.dark) .service-text-blue {
  color: #64d2ff;
}

.workpad-section {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 18px;
}

:global(html.dark) .workpad-section {
  background: #252528;
  border-color: rgba(255, 255, 255, 0.08);
}

.section-title-row {
  margin-bottom: 14px;
}

.title-with-icon {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-with-icon h3 {
  font-size: 16px;
  margin: 0;
}

.text-blue {
  color: #0284c7;
}

:global(html.dark) .text-blue {
  color: #0a84ff;
}

.helper-hint {
  font-size: 12px;
  color: #64748b;
  display: block;
  margin-top: 3px;
}

:global(html.dark) .helper-hint {
  color: #8e8e93;
}

.km-input-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
}

.km-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
}

.km-input {
  width: 180px;
  font-size: 16px;
  font-weight: 700;
}

.km-preview-badge {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #1e40af;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
}

:global(html.dark) .km-preview-badge {
  background: rgba(10, 132, 255, 0.15);
  border-color: rgba(10, 132, 255, 0.3);
  color: #64d2ff;
}

.photo-controls-row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 14px;
}

.sector-select-label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
}

.btn-take-photo {
  position: relative;
  overflow: hidden;
  cursor: pointer;
}

.btn-take-photo input {
  position: absolute;
  top: 0;
  left: 0;
  opacity: 0;
  width: 100%;
  height: 100%;
  cursor: pointer;
}

.photos-masonry {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}

.photo-card {
  position: relative;
  margin: 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  background: #000;
}

:global(html.dark) .photo-card {
  border-color: rgba(255, 255, 255, 0.1);
}

.photo-card img {
  width: 100%;
  height: 110px;
  object-fit: cover;
  display: block;
}

.photo-card figcaption {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 8px;
  background: #f8fafc;
  font-size: 10px;
  font-weight: 600;
  color: #334155;
}

:global(html.dark) .photo-card figcaption {
  background: #1c1c1e;
  color: #d1d1d6;
}

.small-del-btn {
  padding: 2px;
  color: #ef4444;
}

.empty-photos-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  text-align: center;
  gap: 6px;
}

:global(html.dark) .empty-photos-box {
  background: #1c1c1e;
  border-color: rgba(255, 255, 255, 0.1);
}

.empty-photos-box p {
  font-size: 12px;
  color: #64748b;
  margin: 0;
}

.notes-inputs-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
}

.notes-inputs-grid label span {
  display: block;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 4px;
  color: #334155;
}

:global(html.dark) .notes-inputs-grid label span {
  color: #d1d1d6;
}

.btn-finish-work {
  background: #10b981;
  color: white;
}

.btn-finish-work:hover {
  background: #059669;
}
</style>
