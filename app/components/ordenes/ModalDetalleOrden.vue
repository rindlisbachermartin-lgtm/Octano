<script setup lang="ts">
import {
  X,
  Wrench,
  Check,
  ArrowRight,
  Plus,
  Camera,
} from 'lucide-vue-next'
import type { Order } from '~/types'

const props = defineProps<{
  orderId: number | null
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'updated'): void
}>()

const { db, vehicle, vehicleName, owner } = useDatabase()
const { money, statusClass } = useHelpers()
const { notify } = useToast()

useModalEscape(() => props.open, () => emit('close'))

const selectedOrder = computed(() =>
  db.value.orders.find((o) => o.id === props.orderId)
)

const newTask = ref('')
const compatiblePart = ref<number | ''>('')
const photoSector = ref('Frente')

const possibleParts = computed(() => {
  if (!selectedOrder.value) return []
  return db.value.parts.filter(
    (p) => p.compatible.includes(selectedOrder.value!.vehicle) && p.stock > 0
  )
})

function progress(o: Order) {
  return o.tasks.length
    ? Math.round((o.tasks.filter((t) => t.done).length / o.tasks.length) * 100)
    : 0
}

function finishOrder() {
  if (!selectedOrder.value) return
  const o = selectedOrder.value
  o.status = 'Finalizado'
  o.tasks.forEach((t) => (t.done = true))
  o.progress = 100

  // Register invoice automatically if not exists
  const invoiceTotal =
    75000 + o.parts.reduce((sum, p) => sum + (p.price || 0), 0)
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
    detail: `OT #${o.id} · Aviso de WhatsApp simulado`,
    read: false,
  })

  notify('Trabajo finalizado. Factura y aviso generados.')
  emit('updated')
}

function addPart() {
  if (!selectedOrder.value || !compatiblePart.value) return
  const part = db.value.parts.find((p) => p.id === Number(compatiblePart.value))
  if (!part || part.stock <= 0) return

  part.stock--
  selectedOrder.value.parts.push({
    id: part.id,
    name: part.name,
    price: part.price,
  })
  compatiblePart.value = ''
  notify('Repuesto imputado a la orden.')
}

function addPhotos(event: Event) {
  const input = event.target as HTMLInputElement
  if (!input.files || !selectedOrder.value) return
  Array.from(input.files).forEach((file) => {
    if (file.size > 2 * 1024 * 1024) {
      notify('La foto supera los 2 MB.')
      return
    }
    const reader = new FileReader()
    reader.onload = (e) => {
      if (selectedOrder.value && selectedOrder.value.photos.length < 6) {
        selectedOrder.value.photos.push({
          sector: photoSector.value,
          data: e.target?.result as string,
        })
        notify('Foto adjunta al peritaje.')
      }
    }
    reader.readAsDataURL(file)
  })
}

function addTask() {
  if (selectedOrder.value && newTask.value.trim()) {
    selectedOrder.value.tasks.push({ name: newTask.value.trim(), done: false })
    newTask.value = ''
    selectedOrder.value.progress = progress(selectedOrder.value)
  }
}

function startOrder() {
  if (!selectedOrder.value) return
  selectedOrder.value.status = 'En proceso'
  notify('Trabajo iniciado.')
}

function deliverOrder() {
  if (!selectedOrder.value) return
  selectedOrder.value.bay = null
  emit('close')
  notify('Vehículo entregado. Puesto liberado.')
}
</script>

<template>
  <dialog v-if="open && selectedOrder" class="dialog detail-modal" open>
    <div class="dialog-header">
      <div>
        <span class="eyebrow">OCTANO / TALLER CENTRAL</span>
        <h2>Orden #{{ selectedOrder.id }}</h2>
      </div>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <div class="detail-intro">
      <div>
        <span :class="['badge', statusClass(selectedOrder.status)]">
          {{ selectedOrder.status }}
        </span>
        <h2>{{ vehicleName(selectedOrder.vehicle) }}</h2>
        <span class="plate">{{ vehicle(selectedOrder.vehicle).plate }}</span>
        <p>
          {{ owner(selectedOrder.vehicle).name }} ·
          {{ vehicle(selectedOrder.vehicle).km?.toLocaleString('es-AR') }} km
        </p>
      </div>
    </div>

    <div class="detail-body">
      <h3>{{ selectedOrder.service }}</h3>

      <div class="form-grid">
        <label>
          Mecánico asignado
          <select
            v-model="selectedOrder.mechanic"
            :disabled="selectedOrder.status === 'Finalizado'"
          >
            <option>Nicolás</option>
            <option>Santiago</option>
          </select>
        </label>
        <label>
          Puesto
          <select
            v-model="selectedOrder.bay"
            :disabled="selectedOrder.status === 'Finalizado'"
          >
            <option :value="null">Sin asignar</option>
            <option
              v-for="n in 4"
              :key="n"
              :value="n"
              :disabled="db.orders.some((o) => o.id !== selectedOrder?.id && o.bay === n)"
            >
              Puesto 0{{ n }}
            </option>
          </select>
        </label>
      </div>

      <label>
        Diagnóstico
        <textarea
          v-model="selectedOrder.diagnosis"
          :disabled="selectedOrder.status === 'Finalizado'"
          placeholder="¿Qué necesita este vehículo?"
          rows="2"
        ></textarea>
      </label>

      <div class="section-heading">
        <h3>Checklist de trabajo</h3>
        <span class="muted">{{ progress(selectedOrder) }}% completo</span>
      </div>

      <label
        v-for="(task, index) in selectedOrder.tasks"
        :key="index"
        class="task-row"
      >
        <input
          type="checkbox"
          v-model="task.done"
          :disabled="selectedOrder.status === 'Finalizado'"
          @change="selectedOrder.progress = progress(selectedOrder)"
        />
        <span :class="{ done: task.done }">{{ task.name }}</span>
      </label>

      <form
        v-if="selectedOrder.status !== 'Finalizado'"
        class="inline-form"
        @submit.prevent="addTask"
      >
        <input
          v-model="newTask"
          placeholder="Agregar tarea…"
          aria-label="Nueva tarea"
          required
        />
        <button class="button small" type="submit">
          <Plus :size="15" />Agregar
        </button>
      </form>

      <h3>Repuestos utilizados</h3>
      <div
        v-for="(part, index) in selectedOrder.parts"
        :key="index"
        class="quote-line"
      >
        <span>{{ part.name }}</span>
        <strong>{{ money(part.price) }}</strong>
      </div>
      <p v-if="!selectedOrder.parts.length" class="muted">
        Todavía no se imputaron repuestos.
      </p>

      <div v-if="selectedOrder.status !== 'Finalizado'" class="inline-form">
        <select v-model="compatiblePart" aria-label="Repuesto compatible">
          <option value="">Elegir repuesto compatible</option>
          <option v-for="p in possibleParts" :key="p.id" :value="p.id">
            {{ p.name }} · {{ p.stock }} un.
          </option>
        </select>
        <button
          class="button small"
          :disabled="!compatiblePart"
          type="button"
          @click="addPart"
        >
          Imputar
        </button>
      </div>

      <h3>Peritaje fotográfico</h3>
      <div class="inline-form">
        <select v-model="photoSector" aria-label="Sector de la foto">
          <option
            v-for="sector in [
              'Frente',
              'Lateral izquierdo',
              'Lateral derecho',
              'Trasera',
              'Tablero / kilometraje',
              'Detalles de chapa',
            ]"
            :key="sector"
          >
            {{ sector }}
          </option>
        </select>
        <label class="button small upload-label">
          <Camera :size="16" />Agregar foto
          <input
            type="file"
            accept="image/*"
            @change="addPhotos"
          />
        </label>
      </div>
      <small class="muted">Hasta 6 imágenes de 2 MB. Se guardan en este navegador.</small>

      <div class="photo-grid">
        <figure v-for="(photo, index) in selectedOrder.photos" :key="index">
          <img :src="photo.data" :alt="photo.sector" />
          <figcaption>
            {{ photo.sector }}
            <button
              class="icon-button"
              aria-label="Quitar foto"
              type="button"
              @click="selectedOrder.photos.splice(index, 1)"
            >
              <X :size="13" />
            </button>
          </figcaption>
        </figure>
      </div>

      <label>
        Observaciones
        <textarea
          v-model="selectedOrder.notes"
          rows="2"
          placeholder="Detalles para el equipo o la entrega…"
        ></textarea>
      </label>
    </div>

    <footer class="modal-footer">
      <button class="button" @click="emit('close')">Cerrar</button>
      <button
        v-if="selectedOrder.status === 'En espera'"
        class="button primary"
        @click="startOrder"
      >
        <Wrench :size="16" />Iniciar trabajo
      </button>
      <button
        v-else-if="selectedOrder.status === 'En proceso'"
        class="button primary"
        @click="finishOrder"
      >
        <Check :size="16" />Finalizar trabajo
      </button>
      <button
        v-else
        class="button primary"
        @click="deliverOrder"
      >
        Registrar entrega <ArrowRight :size="16" />
      </button>
    </footer>
  </dialog>
</template>
