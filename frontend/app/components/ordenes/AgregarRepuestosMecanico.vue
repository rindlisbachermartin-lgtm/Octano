<script setup lang="ts">
import type { Order, OrderPartSelection } from '~/types'
import { assignOrderParts } from '~/utils/orderWorkflow'

const props = defineProps<{ order: Order }>()
const emit = defineEmits<{ (e: 'updated', orderId: number): void }>()
const auth = useOwnerAccount()
const { db } = useDatabase()
const { notify } = useWorkshopToast()
const items = ref<OrderPartSelection[]>([])
const error = ref('')
const allowed = computed(() => auth.isMechanic.value && props.order.mechanic === auth.mechanic.value && ['En espera', 'En proceso'].includes(props.order.status))
watch(() => [props.order.id, allowed.value], () => { items.value = []; error.value = '' })

function save() {
  const order = db.value.orders.find(order => order.id === props.order.id)
  if (!allowed.value || !order || order.mechanic !== auth.mechanic.value || !['En espera', 'En proceso'].includes(order.status) || !items.value.length) return
  // El precio de inventario se resuelve al guardar; el mecánico no lo edita.
  const selections = items.value.map(item => ({
    partId: item.partId, name: item.name, quantity: item.quantity,
    ...(item.partId === 0 ? { unitPrice: 0 } : {}),
  }))
  error.value = assignOrderParts(db.value, order, selections)
  if (error.value) return
  items.value = []
  notify('Repuestos agregados a la orden.')
  emit('updated', order.id)
}
</script>

<template>
  <form v-if="allowed" class="mechanic-parts-form" @submit.prevent="save">
    <OrdenesEditorRepuestos v-model="items" :vehicle-id="order.vehicle" :show-prices="false" />
    <p v-if="error" class="error-message" role="alert">{{ error }}</p>
    <button v-if="items.length" type="submit" class="button primary">Guardar repuestos</button>
  </form>
</template>

<style scoped>
.mechanic-parts-form { display: grid; gap: 12px; margin-top: 12px; }
.mechanic-parts-form > button { justify-self: end; }
</style>
