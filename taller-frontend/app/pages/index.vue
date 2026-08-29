<template>
  <div class="dashboard-container">
    <!-- Tarjetas Superiores de Métricas y KPIs -->
    <TarjetasResumen
      :turnosHoy="12"
      :nuevosTurnos="2"
      :ingresosMes="14250"
      :porcentajeCrecimiento="8.5"
      :facturasPendientesCantidad="5"
      :facturasPendientesMonto="3100"
      :repuestosStockBajo="8"
    />

    <!-- Grilla Principal: Próximos Turnos y Alertas de Stock -->
    <div class="main-dashboard-grid">
      <TablaProximosTurnos :turnos="listaProximosTurnos" />
      <PanelAlertasInventario :alertas="listaAlertasStock" />
    </div>
  </div>
</template>

<script setup lang="ts">
import TarjetasResumen from '~/components/dashboard/TarjetasResumen.vue'
import TablaProximosTurnos, { type TurnoResumen } from '~/components/dashboard/TablaProximosTurnos.vue'
import PanelAlertasInventario, { type ItemAlertaStock } from '~/components/dashboard/PanelAlertasInventario.vue'

const listaProximosTurnos = ref<TurnoResumen[]>([
  { id: 1, hora: '08:00', cliente: 'Carlos Mendoza', vehiculo: 'VW Golf 2019', servicio: 'Cambio de Aceite', estado: 'En Proceso' },
  { id: 2, hora: '09:30', cliente: 'Ana García', vehiculo: 'Ford Focus 2021', servicio: 'Revisión Frenos', estado: 'Espera' },
  { id: 3, hora: '11:00', cliente: 'Transportes Sur', vehiculo: 'MB Sprinter', servicio: 'Mantenimiento Flota', estado: 'Espera' },
  { id: 4, hora: '14:00', cliente: 'Luis Torres', vehiculo: 'Toyota Hilux', servicio: 'Alineación', estado: 'Confirmado' },
  { id: 5, hora: '16:30', cliente: 'Elena Rojas', vehiculo: 'Honda Civic', servicio: 'Diagnóstico Motor', estado: 'Confirmado' }
])

const listaAlertasStock = ref<ItemAlertaStock[]>([
  { id: 1, nombre: 'Filtros de Aceite 5W30', stockTexto: 'Quedan 2 unidades', unidadesRestantes: 2 },
  { id: 2, nombre: 'Pastillas Freno Del. B20', stockTexto: 'Quedan 0 unidades', unidadesRestantes: 0 },
  { id: 3, nombre: 'Baterías 12V 70Ah', stockTexto: 'Quedan 1 unidad', unidadesRestantes: 1 },
  { id: 4, nombre: 'Líquido de Frenos DOT4', stockTexto: 'Quedan 2 unidades', unidadesRestantes: 2 }
])
</script>

<style scoped>
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1280px;
}

.main-dashboard-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 20px;
}

@media (max-width: 1024px) {
  .main-dashboard-grid {
    grid-template-columns: 1fr;
  }
}
</style>
