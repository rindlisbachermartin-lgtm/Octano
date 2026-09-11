<script setup lang="ts">
import {
  Plus,
  Check,
  Printer,
  Share2,
  FileText,
  Search,
} from 'lucide-vue-next'
import type { Budget } from '~/types'

const { db, vehicle, vehicleName, owner, createOrder } = useDatabase()
const { money, statusClass, matches } = useHelpers()
const { notify } = useToast()

const search = ref('')
const formModalOpen = ref(false)
const shareModalOpen = ref(false)
const selectedBudget = ref<Budget | null>(null)

const filteredQuotes = computed(() =>
  db.value.quotes.filter((q) => {
    const v = vehicle(q.vehicle)
    const o = owner(q.vehicle)
    return matches(
      search.value,
      q.id,
      q.description,
      v?.plate,
      v?.brand,
      v?.model,
      o?.name
    )
  })
)

function getSubtotal(q: Budget): number {
  return Number(q.labor || 0) + Number(q.materials || 0)
}

function getTax(q: Budget): number {
  return Math.round(getSubtotal(q) * 0.21)
}

function getTotalWithTax(q: Budget): number {
  return getSubtotal(q) + getTax(q)
}

function convertQuote(q: Budget) {
  if (q.status === 'Convertido') return
  q.status = 'Convertido'
  const orderId = createOrder(q.vehicle, q.description)
  notify(`Presupuesto aprobado. Orden de trabajo #${orderId} creada.`)
}

function handleCreated(budget: Budget) {
  formModalOpen.value = false
  selectedBudget.value = budget
  shareModalOpen.value = true
  notify(`Presupuesto #${budget.id} generado exitosamente.`)
}

function openShare(q: Budget) {
  selectedBudget.value = q
  shareModalOpen.value = true
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          <span class="tiny-star">✳</span>
          TALLER CENTRAL / PRESUPUESTOS
        </div>
        <h1>Claridad antes de empezar.</h1>
      </div>
      <button class="button primary" @click="formModalOpen = true">
        <Plus :size="17" />Nuevo presupuesto
      </button>
    </section>

    <div class="list-toolbar">
      <span class="muted">{{ filteredQuotes.length }} presupuestos</span>
      <label class="search-box">
        <Search :size="17" />
        <input
          v-model="search"
          placeholder="Buscar por auto, cliente o trabajo…"
          aria-label="Buscar presupuestos"
        />
      </label>
    </div>

    <div class="quote-grid">
      <article
        v-for="q in filteredQuotes"
        :key="q.id"
        class="panel quote-card"
      >
        <div class="section-heading">
          <span class="eyebrow">PRESUPUESTO #{{ q.id }}</span>
          <span :class="['badge', statusClass(q.status)]">{{ q.status }}</span>
        </div>
        <h2>{{ vehicleName(q.vehicle) }}</h2>
        <p>{{ owner(q.vehicle)?.name }} · {{ vehicle(q.vehicle)?.plate }}</p>
        <h3>{{ q.description }}</h3>

        <!-- Financial Breakdown Lines -->
        <div class="quote-line">
          <span>Mano de obra</span>
          <strong>{{ money(q.labor) }}</strong>
        </div>
        <div class="quote-line">
          <span>
            Repuestos e insumos
            <small v-if="q.items && q.items.length" class="muted" style="font-size: 10px; display: block">
              ({{ q.items.length }} {{ q.items.length === 1 ? 'ítem' : 'ítems' }})
            </small>
          </span>
          <strong>{{ money(q.materials) }}</strong>
        </div>
        <div class="quote-line subtotal-line-clean">
          <span>Subtotal Neto</span>
          <strong>{{ money(getSubtotal(q)) }}</strong>
        </div>
        <div class="quote-line vat-line-clean">
          <span>IVA (21%)</span>
          <strong>{{ money(getTax(q)) }}</strong>
        </div>

        <div class="quote-total">
          <div>
            <span style="font-size: 10px; text-transform: uppercase; letter-spacing: 0.5px; color: #64748b">Total con IVA</span>
            <strong>{{ money(getTotalWithTax(q)) }}</strong>
          </div>
          <button
            class="icon-button"
            title="Compartir o imprimir presupuesto"
            aria-label="Compartir o imprimir presupuesto"
            @click="openShare(q)"
          >
            <Share2 :size="17" />
          </button>
        </div>

        <!-- Action Buttons -->
        <div class="quote-actions-row">
          <button
            class="button primary quote-approve-btn"
            :disabled="q.status === 'Convertido'"
            @click="convertQuote(q)"
          >
            <Check :size="16" />
            {{ q.status === 'Convertido' ? 'OT creada' : 'Aprobar OT' }}
          </button>
          <button
            class="button outlined quote-share-btn"
            title="Compartir / Imprimir"
            @click="openShare(q)"
          >
            <Printer :size="15" /> Imprimir / Enviar
          </button>
        </div>
      </article>
    </div>

    <div v-if="!filteredQuotes.length" class="empty-state">
      <FileText :size="38" class="muted" />
      <h3>No se encontraron presupuestos</h3>
      <p>Probá con otra búsqueda o creá uno nuevo.</p>
    </div>

    <!-- Create Modal -->
    <PresupuestosModalPresupuesto
      :open="formModalOpen"
      @close="formModalOpen = false"
      @created="handleCreated"
    />

    <!-- Share & Print Modal -->
    <PresupuestosModalCompartirPresupuesto
      :open="shareModalOpen"
      :budget="selectedBudget"
      @close="shareModalOpen = false"
    />
  </div>
</template>

<style scoped>
.quote-actions-row {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

.quote-approve-btn {
  flex: 1.2;
}

.quote-share-btn {
  flex: 1;
}

.subtotal-line-clean {
  color: #334155;
  font-weight: 550;
}

.vat-line-clean {
  color: #2563eb;
  font-weight: 550;
}

:global(html.dark) .subtotal-line-clean {
  color: #d1d1d6;
}

:global(html.dark) .vat-line-clean {
  color: #64d2ff;
}
</style>
