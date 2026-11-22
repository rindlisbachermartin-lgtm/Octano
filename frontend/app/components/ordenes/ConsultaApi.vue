<script setup lang="ts">
import { X } from '@lucide/vue'
import { authError } from '~/composables/useAuthApi'

const props = defineProps<{ id: number }>()
const emit = defineEmits<{ close: [] }>()
const { authorization } = useAuthApi()
const { money } = useHelpers()
interface ConsultaOrden {
  orden: { id: number; descripcion: string; marca: string; modelo: string; version: string | null; patente: string; clienteNombre: string; mecanicoNombre: string | null; kilometraje: number | null; finFecha: string | null; fechaCreacion: string; diagnostico: string; indicaciones: string; observacionesMecanico: string; aceite: string; filtros: string[]; manoObra: number; porcentajeIva: number }
  tareas: { id: number; descripcion: string; completada: boolean }[]
  items: { id: number; descripcion: string; cantidad: number; precioUnitario: number; subtotal: number }[]
  fotos: { id: number; sector: string }[]
  totalRepuestos: number
  montoIva: number
  total: number
}
const datos = ref<ConsultaOrden | null>(null)
const error = ref('')
const errorFotos = ref('')
const cargando = ref(false)
const imagenes = ref<Record<number, string>>({})
const revision = ref(0)
const filtros: Record<string, string> = { ACEITE: 'Aceite', AIRE: 'Aire', HABITACULO: 'Habitáculo', COMBUSTIBLE: 'Combustible' }
watch([authorization, () => props.id, revision], async ([headers, id], _, onCleanup) => {
  const controller = new AbortController()
  const urls: Record<number, string> = {}
  onCleanup(() => { controller.abort(); Object.values(urls).forEach(url => URL.revokeObjectURL(url)) })
  datos.value = null
  imagenes.value = {}
  error.value = ''
  errorFotos.value = ''
  cargando.value = !!headers
  if (!headers) return
  try {
    const result = await $fetch<ConsultaOrden>(`/api/ordenes-trabajo/${id}`, { headers, signal: controller.signal })
    if (controller.signal.aborted) return
    datos.value = result
    const fotos = await Promise.allSettled(result.fotos.map(async foto => {
      const blob = await $fetch<Blob>(`/api/ordenes-trabajo/${id}/fotos/${foto.id}`, { headers, signal: controller.signal, responseType: 'blob' })
      if (!controller.signal.aborted) urls[foto.id] = URL.createObjectURL(blob)
    }))
    if (controller.signal.aborted) return
    imagenes.value = urls
    if (fotos.some(f => f.status === 'rejected')) errorFotos.value = 'No se pudieron cargar algunas fotos.'
  } catch (err) { if (!controller.signal.aborted) error.value = authError(err, 'No se pudo consultar la orden de trabajo.') }
  finally { if (!controller.signal.aborted) cargando.value = false }
}, { immediate: true })
</script>

<template>
  <CommonModalDialog class="dialog detail-modal" @close="emit('close')">
    <div class="dialog-header"><h2>Orden de trabajo #{{ id }}</h2><button class="icon-button" aria-label="Cerrar orden" @click="emit('close')"><X :size="18" /></button></div>
    <div class="detail-body">
      <p v-if="cargando" role="status">Cargando orden…</p><p v-if="error" role="alert">{{ error }} <button class="text-button" @click="revision++">Reintentar</button></p>
      <template v-if="datos">
        <div class="order-heading"><div><h3>{{ vehicleLabel(datos.orden.marca, datos.orden.modelo, datos.orden.version) }}</h3><span class="plate">{{ datos.orden.patente }}</span></div><CommonBrandLogo :brand="datos.orden.marca" class="order-brand" /></div>
        <h3>{{ datos.orden.descripcion }}</h3>
        <dl class="order-info"><div><dt>Cliente al emitir la orden</dt><dd>{{ datos.orden.clienteNombre }}</dd></div><div><dt>Mecánico</dt><dd>{{ datos.orden.mecanicoNombre || 'Sin registrar' }}</dd></div><div><dt>Fecha de cierre</dt><dd>{{ datos.orden.finFecha ? new Date(datos.orden.finFecha).toLocaleString('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' }) : 'Sin registrar' }}</dd></div><div><dt>Kilometraje</dt><dd>{{ datos.orden.kilometraje === null ? 'Sin registrar' : `${datos.orden.kilometraje.toLocaleString('es-AR')} km` }}</dd></div></dl>
        <section v-if="datos.orden.indicaciones"><h3>Indicaciones</h3><p>{{ datos.orden.indicaciones }}</p></section>
        <section v-if="datos.orden.diagnostico"><h3>Diagnóstico</h3><p>{{ datos.orden.diagnostico }}</p></section>
        <section v-if="datos.orden.observacionesMecanico"><h3>Observaciones del mecánico</h3><p>{{ datos.orden.observacionesMecanico }}</p></section>
        <section v-if="datos.orden.aceite || datos.orden.filtros.length"><h3>Mantenimiento</h3><p v-if="datos.orden.aceite">Aceite: {{ datos.orden.aceite }}</p><p v-if="datos.orden.filtros.length">Filtros: {{ datos.orden.filtros.map(f => filtros[f] || f).join(', ') }}</p></section>
        <section><h3>Tareas</h3><ul><li v-for="t in datos.tareas" :key="t.id">{{ t.completada ? '✓' : '○' }} {{ t.descripcion }}</li></ul></section>
        <section><h3>Repuestos y materiales</h3><div v-if="datos.items.length" class="table-scroll"><table><thead><tr><th>DESCRIPCIÓN</th><th>CANTIDAD</th><th>PRECIO</th><th>SUBTOTAL</th></tr></thead><tbody><tr v-for="i in datos.items" :key="i.id"><td>{{ i.descripcion }}</td><td>{{ i.cantidad }}</td><td>{{ money(i.precioUnitario) }}</td><td>{{ money(i.subtotal) }}</td></tr></tbody></table></div><p v-else class="muted">Sin materiales registrados.</p></section>
        <dl class="order-info"><div><dt>Repuestos</dt><dd>{{ money(datos.totalRepuestos) }}</dd></div><div><dt>Mano de obra</dt><dd>{{ money(datos.orden.manoObra) }}</dd></div><div><dt>IVA ({{ datos.orden.porcentajeIva }}%)</dt><dd>{{ money(datos.montoIva) }}</dd></div><div><dt>Total</dt><dd><strong>{{ money(datos.total) }}</strong></dd></div></dl>
        <section v-if="datos.fotos.length"><h3>Fotos</h3><p v-if="errorFotos" role="alert">{{ errorFotos }}</p><div class="order-photos"><figure v-for="f in datos.fotos" :key="f.id"><a v-if="imagenes[f.id]" :href="imagenes[f.id]" target="_blank" rel="noopener"><img :src="imagenes[f.id]" :alt="f.sector" /></a><figcaption>{{ f.sector }}</figcaption></figure></div></section>
      </template>
    </div>
    <footer class="modal-footer"><button class="button" @click="emit('close')">Volver al vehículo</button></footer>
  </CommonModalDialog>
</template>

<style scoped>
.order-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.order-brand { width: 72px; height: 56px; }
.order-info { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; font-size: 12px; }
dt { color: var(--muted); margin-bottom: 4px; }
dd { margin: 0; }
section h3 { margin-bottom: 8px; }
section p { white-space: pre-wrap; overflow-wrap: anywhere; font-size: 12px; line-height: 1.6; }
.order-photos { display: flex; flex-wrap: wrap; gap: 12px; }
figure { margin: 0; width: 160px; font-size: 11px; }
figure img { width: 100%; height: 120px; object-fit: cover; border-radius: 8px; }
</style>
