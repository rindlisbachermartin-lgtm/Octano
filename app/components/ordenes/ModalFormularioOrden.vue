<script setup lang="ts">
import {
  Check,
  X,
  Search,
  Plus,
  FileText,
  FilePlus,
  ArrowRight,
  User,
  Car,
  AlertCircle,
  Wrench,
  RotateCcw,
} from 'lucide-vue-next'
import type { Budget, Vehicle } from '~/types'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created', orderId: number): void
}>()

const { db, client, vehicle, vehicleName, owner, createOrder } = useDatabase()
const { money, statusClass, matches } = useHelpers()
const { notify } = useWorkshopToast()

const search = ref('')
const debouncedSearch = useDebouncedValue(search)
const selectedBudgetId = ref<number | null>(null)
const searchInputRef = ref<HTMLInputElement | null>(null)
const formError = ref('')

const form = ref({
  service: '',
  mechanic: 'Nicolás',
})

useModalEscape(() => props.open, () => emit('close'))

const selectedBudget = computed(() => {
  if (!selectedBudgetId.value) return null
  return db.value.quotes.find((q) => q.id === selectedBudgetId.value) || null
})

const selectedVehicle = computed(() => {
  if (!selectedBudget.value) return null
  return vehicle(selectedBudget.value.vehicle)
})

const selectedOwner = computed(() => {
  if (!selectedBudget.value) return null
  return owner(selectedBudget.value.vehicle)
})

function getBudgetTotal(q: Budget): number {
  return budgetAmounts(q).total
}

const availableBudgets = computed(() =>
  db.value.quotes.filter((q) => q.status === 'Pendiente' && !q.orderId)
)

// Filtered list
const filteredBudgets = computed(() => {
  const list = availableBudgets.value

  const qStr = debouncedSearch.value.trim()
  if (!qStr) return list

  return list.filter((q) => {
    const v = vehicle(q.vehicle)
    const o = owner(q.vehicle)
    return matches(
      qStr,
      q.id,
      q.description,
      v?.plate,
      v?.brand,
      v?.model,
      o?.name,
      o?.doc,
      o?.phone
    )
  })
})

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      search.value = ''
      selectedBudgetId.value = null
      formError.value = ''
      form.value = {
        service: '',
        mechanic: 'Nicolás',
      }
      nextTick(() => {
        searchInputRef.value?.focus()
      })
    }
  }
)

function selectBudget(b: Budget) {
  if (b.status !== 'Pendiente' || b.orderId) return
  selectedBudgetId.value = b.id
  form.value.service = b.description || ''
  formError.value = ''
}

function clearSelectedBudget() {
  selectedBudgetId.value = null
  form.value.service = ''
  formError.value = ''
  nextTick(() => {
    searchInputRef.value?.focus()
  })
}

function goToNewBudget() {
  emit('close')
  navigateTo('/presupuestos?nuevo=1')
}

function submit() {
  formError.value = ''

  if (!selectedBudget.value) {
    formError.value = 'Por favor buscá y seleccioná un presupuesto para generar la orden.'
    return
  }

  if (selectedBudget.value.status !== 'Pendiente' || selectedBudget.value.orderId) {
    formError.value = 'Seleccioná un presupuesto pendiente que todavía no tenga una orden de trabajo.'
    return
  }

  const serviceDesc = form.value.service.trim() || selectedBudget.value.description
  if (!serviceDesc) {
    formError.value = 'Por favor ingresá el trabajo a realizar.'
    return
  }

  const b = selectedBudget.value

  const initialParts = (b.items || [])
    .filter((it) => it.partId && it.partId !== 'custom')
    .map((it) => ({
      id: Number(it.partId),
      name: it.name,
      price: it.unitPrice,
    }))

  const orderId = createOrder(
    b.vehicle,
    serviceDesc,
    form.value.mechanic,
    null,
    initialParts,
    b.serviceTypes || [],
    b.oilSpec || ''
  )

  b.status = 'Convertido'
  b.orderId = orderId

  emit('created', orderId)
  emit('close')
}
</script>

<template>
  <CommonFormPage v-if="open" class="budget-order-content">
    <div class="dialog-header">
      <div>
        <h2>Nueva orden de trabajo</h2>
        <p class="dialog-subtitle">
          Toda orden proviene de un presupuesto. Seleccioná uno para iniciar el trabajo.
        </p>
      </div>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')">
        <X :size="18" />
      </button>
    </div>

    <form @submit.prevent="submit" class="entry-form">
      <div class="form-fields">
        <!-- Banner para crear nuevo presupuesto -->
        <div v-if="!selectedBudget" class="budget-prompt-banner">
          <div class="prompt-text">
            <div class="prompt-title">
              <FilePlus :size="18" class="prompt-icon" />
              <strong>¿Todavía no tenés un presupuesto para este trabajo?</strong>
            </div>
            <p>Podés crear un presupuesto rápido con repuestos y mano de obra en el apartado de presupuestos.</p>
          </div>
          <button
            type="button"
            class="button primary small prompt-btn"
            @click="goToNewBudget"
          >
            <Plus :size="15" />
            Crear nuevo presupuesto
          </button>
        </div>

        <!-- PASO 1: SELECCIONAR PRESUPUESTO (si no hay ninguno seleccionado) -->
        <div v-if="!selectedBudget" class="budget-selection-zone">
          <div class="selection-header">
            <label class="block-label">
              Seleccionar presupuesto pendiente <span class="required-star">*</span>
            </label>

            <span class="budget-date-sub">Disponibles para OT ({{ availableBudgets.length }})</span>
          </div>

          <!-- Buscador -->
          <div class="target-search-box search-box-full">
            <Search :size="16" class="search-icon" />
            <input
              ref="searchInputRef"
              v-model="search"
              type="text"
              placeholder="Buscá por N° de presupuesto, cliente, patente, modelo o trabajo…"
              autocomplete="off"
            />
            <button
              v-if="search"
              type="button"
              class="icon-button clear-icon-btn"
              @click="search = ''"
              aria-label="Limpiar búsqueda"
            >
              <X :size="14" />
            </button>
          </div>

          <!-- Lista de Presupuestos -->
          <div class="budget-cards-scroll">
            <div
              v-for="b in filteredBudgets"
              :key="b.id"
              class="budget-pick-card"
              @click="selectBudget(b)"
            >
              <div class="card-top-row">
                <div class="budget-id-group">
                  <span class="budget-num-badge">#{{ b.id }}</span>
                  <span v-if="b.date" class="budget-date-sub">{{ b.date }}</span>
                </div>
                <div class="card-status-badges">
                  <span class="badge" :class="b.status === 'Pendiente' ? 'neutral' : statusClass(b.status)">
                    {{ b.status }}
                  </span>
                </div>
              </div>

              <div class="card-middle-row">
                <div class="vehicle-client-info">
                  <div class="plate-and-model">
                    <span class="plate small-plate">{{ vehicle(b.vehicle)?.plate || '—' }}</span>
                    <strong class="vehicle-title">
                      {{ vehicle(b.vehicle)?.brand }} {{ vehicle(b.vehicle)?.model }}
                    </strong>
                  </div>
                  <span class="client-meta">
                    Cliente: <strong>{{ owner(b.vehicle)?.name || 'Sin titular' }}</strong>
                    <template v-if="owner(b.vehicle)?.phone">
                      · {{ owner(b.vehicle)?.phone }}
                    </template>
                  </span>
                </div>
                <div class="budget-amount-box">
                  <span class="budget-date-sub">Subtotal: {{ money(budgetAmounts(b).subtotal) }}</span>
                  <span class="budget-date-sub">IVA (21%): {{ money(budgetAmounts(b).tax) }}</span>
                  <span class="amount-label">Total</span>
                  <strong class="amount-val">{{ money(getBudgetTotal(b)) }}</strong>
                </div>
              </div>

              <div class="card-desc-row">
                <p class="service-desc-text">{{ b.description }}</p>
                <button type="button" class="select-budget-btn">
                  Seleccionar <ArrowRight :size="13" />
                </button>
              </div>
            </div>

            <!-- Estado Vacío -->
            <div v-if="!filteredBudgets.length" class="empty-budgets-state">
              <AlertCircle :size="32" class="empty-icon" />
              <p>
                No hay presupuestos pendientes disponibles para iniciar una orden
                <template v-if="search"> para "<strong>{{ search }}</strong>"</template>.
              </p>
              <div class="empty-actions">
                <button
                  type="button"
                  class="button primary small"
                  @click="goToNewBudget"
                >
                  <Plus :size="14" />
                  Crear nuevo presupuesto
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- PASO 2: PRESUPUESTO SELECCIONADO Y DETALLES DE LA ORDEN -->
        <div v-else class="selected-budget-section">
          <!-- Tarjeta del presupuesto seleccionado -->
          <div class="selected-budget-card">
            <div class="selected-card-header">
              <div class="header-left">
                <span class="badge blue mini-badge">Presupuesto #{{ selectedBudget.id }}</span>
                <span class="plate small-plate">{{ selectedVehicle?.plate }}</span>
                <strong>{{ selectedVehicle?.brand }} {{ selectedVehicle?.model }}</strong>
              </div>
              <button
                type="button"
                class="button small btn-change-budget"
                @click="clearSelectedBudget"
              >
                <RotateCcw :size="13" />
                Cambiar presupuesto
              </button>
            </div>

            <div class="selected-card-details">
              <div class="detail-item">
                <span class="detail-label">Titular:</span>
                <strong>{{ selectedOwner?.name }}</strong>
                <template v-if="selectedOwner?.phone">
                  <span class="detail-sub">({{ selectedOwner?.phone }})</span>
                </template>
              </div>
              <div class="detail-item">
                <span class="detail-label">Subtotal:</span>
                <strong>{{ money(budgetAmounts(selectedBudget).subtotal) }}</strong>
              </div>
              <div class="detail-item">
                <span class="detail-label">IVA (21%):</span>
                <strong>{{ money(budgetAmounts(selectedBudget).tax) }}</strong>
              </div>
              <div class="detail-item">
                <span class="detail-label">Total:</span>
                <strong class="detail-price">{{ money(getBudgetTotal(selectedBudget)) }}</strong>
                <span v-if="selectedBudget.items?.length" class="detail-sub">
                  ({{ selectedBudget.items.length }} ítems / repuestos)
                </span>
              </div>
            </div>
          </div>

          <!-- Campos complementarios de la Orden -->
          <div class="order-inputs-group">
            <label class="field-label">
              Trabajo a realizar
              <input
                v-model="form.service"
                required
                maxlength="120"
                placeholder="Trabajo descripto en el presupuesto"
              />
            </label>

            <label class="field-label">
              Mecánico asignado
              <select v-model="form.mechanic">
                <option>Nicolás</option>
                <option>Santiago</option>
              </select>
            </label>
          </div>

          <div class="info-tip-box">
            <Wrench :size="15" class="tip-icon" />
            <p>
              Al crear la orden, el presupuesto pasará a estado <strong>Convertido</strong> y se transferirán automáticamente los repuestos y tareas presupuestadas.
            </p>
          </div>
        </div>

        <p v-if="formError" class="error-message" role="alert">
          {{ formError }}
        </p>
      </div>

      <footer class="dialog-footer modal-footer">
        <button type="button" class="button" @click="emit('close')">Cancelar</button>
        <button
          type="submit"
          class="button primary"
          :disabled="!selectedBudget"
        >
          <Check :size="16" />
          Crear orden de trabajo
        </button>
      </footer>
    </form>
  </CommonFormPage>
</template>

<style scoped>
.dialog-subtitle {
  font-size: 13px;
  color: #64748b;
  margin-top: 2px;
}

:global(html.dark .dialog-subtitle) {
  color: #94a3b8;
}

/* Banner superior para ir a nuevo presupuesto */
.budget-prompt-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  background: transparent;
  border: 0;
  border-radius: 10px;
  margin-bottom: 16px;
}

:global(html.dark .budget-prompt-banner) {
  background: transparent;
  border: 0;
}

.prompt-text {
  flex: 1;
}

.prompt-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #0369a1;
  font-size: 13.5px;
  margin-bottom: 2px;
}

:global(html.dark .prompt-title) {
  color: #64d2ff;
}

.prompt-icon {
  color: #0284c7;
  flex-shrink: 0;
}

:global(html.dark .prompt-icon) {
  color: #38bdf8;
}

.prompt-text p {
  font-size: 12.5px;
  color: #475569;
  margin: 0;
  line-height: 1.35;
}

:global(html.dark .prompt-text p) {
  color: #cbd5e1;
}

.prompt-btn {
  flex-shrink: 0;
  white-space: nowrap;
}

/* Zona de selección de presupuestos */
.budget-selection-zone {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.selection-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.search-box-full {
  width: 100%;
  margin-bottom: 4px;
}

.target-search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
}

:global(html.dark .target-search-box) {
  background: #202023;
  border-color: rgba(255, 255, 255, 0.15);
}

.target-search-box input {
  border: none;
  background: transparent;
  outline: none;
  font-size: 13.5px;
  width: 100%;
  color: inherit;
}

.budget-cards-scroll {
  max-height: 270px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 4px;
}

.budget-pick-card {
  padding: 10px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 9px;
  background: #ffffff;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: all 0.15s ease;
}

:global(html.dark .budget-pick-card) {
  background: #232326;
  border-color: rgba(255, 255, 255, 0.08);
}

.budget-pick-card:hover {
  border-color: #38bdf8;
  background: #f8fafc;
  transform: translateY(-1px);
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.04);
}

:global(html.dark .budget-pick-card:hover) {
  background: #28282c;
  border-color: rgba(10, 132, 255, 0.4);
}

.card-top-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.budget-id-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.budget-num-badge {
  font-weight: 700;
  font-size: 12.5px;
  color: #0369a1;
  background: #e0f2fe;
  padding: 2px 7px;
  border-radius: 5px;
}

:global(html.dark .budget-num-badge) {
  color: #64d2ff;
  background: rgba(10, 132, 255, 0.2);
}

.budget-date-sub {
  font-size: 12px;
  color: #64748b;
}

:global(html.dark .budget-date-sub) {
  color: #94a3b8;
}

.card-status-badges {
  display: flex;
  align-items: center;
  gap: 6px;
}

.mini-badge {
  font-size: 11px;
  padding: 1px 6px;
}

.card-middle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.vehicle-client-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.plate-and-model {
  display: flex;
  align-items: center;
  gap: 8px;
}

.vehicle-title {
  font-size: 13.5px;
  color: #1e293b;
}

:global(html.dark .vehicle-title) {
  color: #f1f5f9;
}

.client-meta {
  font-size: 12px;
  color: #64748b;
}

:global(html.dark .client-meta) {
  color: #94a3b8;
}

.budget-amount-box {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex-shrink: 0;
}

.amount-label {
  font-size: 10.5px;
  color: #64748b;
  text-transform: uppercase;
  font-weight: 600;
  letter-spacing: 0.4px;
}

:global(html.dark .amount-label) {
  color: #94a3b8;
}

.amount-val {
  font-size: 14.5px;
  color: #0f172a;
  font-weight: 700;
}

:global(html.dark .amount-val) {
  color: #ffffff;
}

.card-desc-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-top: 1px dashed #e2e8f0;
  padding-top: 6px;
  margin-top: 2px;
}

:global(html.dark .card-desc-row) {
  border-top-color: rgba(255, 255, 255, 0.08);
}

.service-desc-text {
  font-size: 12px;
  color: #475569;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 440px;
}

:global(html.dark .service-desc-text) {
  color: #cbd5e1;
}

.select-budget-btn {
  background: transparent;
  border: none;
  color: #0284c7;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  padding: 0;
  flex-shrink: 0;
}

:global(html.dark .select-budget-btn) {
  color: #38bdf8;
}

.budget-pick-card:hover .select-budget-btn {
  color: #0369a1;
  text-decoration: underline;
}

/* Empty state */
.empty-budgets-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 28px 16px;
  text-align: center;
  color: #64748b;
}

:global(html.dark .empty-budgets-state) {
  color: #94a3b8;
}

.empty-icon {
  color: #94a3b8;
  margin-bottom: 8px;
}

:global(html.dark .empty-icon) {
  color: #64748b;
}

.empty-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

/* PASO 2: Presupuesto seleccionado */
.selected-budget-section {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.selected-budget-card {
  border: 1.5px solid #0284c7;
  background: #f0f9ff;
  border-radius: 10px;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

:global(html.dark .selected-budget-card) {
  background: rgba(10, 132, 255, 0.12);
  border-color: #0a84ff;
}

.selected-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.header-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 13.5px;
  color: #0f172a;
  flex: 1;
  min-width: 240px;
}

:global(html.dark .header-left) {
  color: #ffffff;
}

.btn-change-budget {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  flex-shrink: 0;
}

.selected-card-details {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
  color: #334155;
  border-top: 1px solid #bae0fd;
  padding-top: 8px;
}

:global(html.dark .selected-card-details) {
  color: #cbd5e1;
  border-top-color: rgba(10, 132, 255, 0.25);
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.detail-label {
  color: #64748b;
  font-size: 12.5px;
}

:global(html.dark .detail-label) {
  color: #94a3b8;
}

.detail-sub {
  color: #64748b;
  font-size: 12px;
}

:global(html.dark .detail-sub) {
  color: #94a3b8;
}

.detail-price {
  color: #0369a1;
  font-weight: 700;
}

:global(html.dark .detail-price) {
  color: #64d2ff;
}

.order-inputs-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.field-label {
  display: flex;
  flex-direction: column;
  gap: 5px;
  font-size: 13px;
  font-weight: 600;
}

.info-tip-box {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 12px;
  color: #475569;
}

:global(html.dark .info-tip-box) {
  background: #202023;
  border-color: rgba(255, 255, 255, 0.08);
  color: #94a3b8;
}

.tip-icon {
  color: #0284c7;
  flex-shrink: 0;
  margin-top: 2px;
}

:global(html.dark .tip-icon) {
  color: #0a84ff;
}

.info-tip-box p {
  margin: 0;
  line-height: 1.4;
}
</style>
