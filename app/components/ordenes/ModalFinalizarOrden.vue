<script setup lang="ts">
import { Check, X } from 'lucide-vue-next'
import type { Order } from '~/types'
import { orderWorkKinds } from '~/utils/orderWorkflow'
import type { OrderCompletionData } from '~/utils/orderWorkflow'

const props = defineProps<{ order: Order; error?: string }>()
const emit = defineEmits<{ close: []; confirm: [data: OrderCompletionData] }>()
const { vehicle } = useDatabase()
const isService = computed(() => orderWorkKinds(props.order).service)
const form = ref<OrderCompletionData>({ km: props.order.km ?? vehicle(props.order.vehicle)?.km ?? null, mechanicNotes: props.order.mechanicNotes || '', oilSpec: props.order.oilSpec || '', replacedFilters: [...(props.order.replacedFilters || [])] })
const filters = ['Filtro de aceite', 'Filtro de aire', 'Filtro de habitáculo', 'Filtro de combustible']
</script>

<template>
  <CommonModalDialog class="dialog completion-dialog" aria-labelledby="completion-title" @close="emit('close')">
    <div class="dialog-header">
      <div><h2 id="completion-title">Finalizar orden #{{ order.id }}</h2><p class="muted">Registrá los datos del trabajo antes de cerrarlo.</p></div>
      <button class="icon-button" aria-label="Cerrar" @click="emit('close')"><X :size="18" /></button>
    </div>
    <form @submit.prevent="emit('confirm', { ...form, replacedFilters: [...form.replacedFilters] })">
      <div class="form-fields completion-fields">
        <label>Kilometraje del vehículo<input v-model.number="form.km" type="number" inputmode="numeric" min="0" step="1" required autofocus /></label>
        <template v-if="isService">
          <label>Aceite utilizado<input v-model="form.oilSpec" placeholder="Ej. Castrol Edge 5W-30" maxlength="120" required /></label>
          <fieldset><legend>Filtros reemplazados</legend><div class="completion-filters"><label v-for="filter in filters" :key="filter"><input v-model="form.replacedFilters" type="checkbox" :value="filter" />{{ filter }}</label></div></fieldset>
        </template>
        <label>Observaciones del mecánico<textarea v-model="form.mechanicNotes" rows="3" placeholder="Observaciones del trabajo realizado" /></label>
        <p v-if="error" class="error-message" role="alert">{{ error }}</p>
      </div>
      <footer class="modal-footer"><button type="button" class="button" @click="emit('close')">Cancelar</button><button type="submit" class="button primary"><Check :size="16" /> Confirmar finalización</button></footer>
    </form>
  </CommonModalDialog>
</template>

<style scoped>
.completion-dialog { width: min(480px, calc(100vw - 24px)); }
.completion-fields { padding: 18px 24px; gap: 16px; }
fieldset { border: 0; padding: 0; margin: 0; min-width: 0; }
legend { font-size: 13px; font-weight: 600; margin-bottom: 10px; }
.completion-filters { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.completion-filters label { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.completion-filters input { width: auto; min-height: 0; }
@media (max-width: 380px) { .completion-filters { grid-template-columns: 1fr; } }
</style>
