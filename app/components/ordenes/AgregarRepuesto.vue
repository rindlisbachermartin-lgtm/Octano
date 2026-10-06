<script setup lang="ts">
import { Plus } from 'lucide-vue-next'
import type { Order, OrderPartSelection } from '~/types'
import { assignOrderParts, canEditIssuedOrder } from '~/utils/orderWorkflow'

const props = defineProps<{ order: Order }>()
const { db } = useDatabase()
const { notify } = useWorkshopToast()
const items = ref<OrderPartSelection[]>([])
const error = ref('')
watch(() => props.order.id, () => { items.value = []; error.value = '' })
function add() {
  if (!items.value.length) return
  error.value = assignOrderParts(db.value, props.order, items.value)
  if (error.value) return
  items.value = []
  notify('Repuesto agregado a la orden. Stock actualizado.')
}
</script>

<template>
  <form v-if="canEditIssuedOrder(order) || order.status === 'En proceso'" class="add-order-part" @submit.prevent="add">
    <OrdenesEditorRepuestos v-model="items" :vehicle-id="order.vehicle" />
    <p v-if="error" class="error-message" role="alert">{{ error }}</p>
    <button v-if="items.length" type="submit" class="button small primary"><Plus :size="14" /> Guardar repuestos en la orden</button>
  </form>
</template>

<style scoped>
.add-order-part { display: flex; flex-direction: column; gap: 12px; margin-top: 16px; }
.add-order-part > button { align-self: flex-start; }
</style>
