<template>
  <div class="dashboard-panel turnos-panel">
    <div class="panel-header">
      <h3 class="panel-title">Próximos Turnos</h3>
      <NuxtLink to="/turnos" class="link-calendar">Ver Calendario</NuxtLink>
    </div>

    <div class="panel-scroll-content">
      <table class="turnos-table">
        <thead class="table-header-sticky">
          <tr>
            <th class="th-turno">Hora</th>
            <th class="th-turno">Cliente</th>
            <th class="th-turno">Vehículo</th>
            <th class="th-turno">Servicio</th>
            <th class="th-turno text-right">Estado</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="turno in turnos" :key="turno.id" class="tr-turno">
            <td class="td-turno font-semibold">{{ turno.hora }}</td>
            <td class="td-turno text-dark">{{ turno.cliente }}</td>
            <td class="td-turno text-muted">{{ turno.vehiculo }}</td>
            <td class="td-turno text-dark">{{ turno.servicio }}</td>
            <td class="td-turno text-right">
              <span :class="['tag-pill', obtenerClasePill(turno.estado)]">
                {{ turno.estado }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
export interface TurnoResumen {
  id: number
  hora: string
  cliente: string
  vehiculo: string
  servicio: string
  estado: 'En Proceso' | 'Espera' | 'Confirmado'
}

defineProps<{
  turnos: TurnoResumen[]
}>()

const obtenerClasePill = (estado: string): string => {
  switch (estado) {
    case 'En Proceso': return 'pill-en-proceso'
    case 'Confirmado': return 'pill-confirmado'
    default: return 'pill-espera'
  }
}
</script>

<style scoped>
.dashboard-panel {
  background-color: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--border-radius-md);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  height: 400px;
}

.panel-header {
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-subtle);
  background-color: #ffffff;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
}

.link-calendar {
  color: var(--primary);
  font-size: 12px;
  font-weight: 600;
  text-decoration: none;
}

.link-calendar:hover {
  text-decoration: underline;
}

.panel-scroll-content {
  overflow-y: auto;
  flex: 1;
}

.turnos-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
}

.table-header-sticky {
  background-color: var(--surface-low);
  position: sticky;
  top: 0;
  z-index: 10;
  border-bottom: 1px solid var(--border-subtle);
}

.th-turno {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  color: var(--text-muted);
  padding: 10px 14px;
}

.tr-turno {
  border-bottom: 1px solid var(--border-subtle);
  transition: background-color 0.1s ease;
}

.tr-turno:hover {
  background-color: var(--surface-low);
}

.td-turno {
  padding: 11px 14px;
  font-size: 13px;
}

.font-semibold {
  font-weight: 600;
  color: var(--text-main);
  white-space: nowrap;
}

.text-dark { color: var(--text-main); }
.text-muted { color: var(--text-muted); }
.text-right { text-align: right; }

.tag-pill {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 9px;
  border-radius: 9999px;
  display: inline-block;
  white-space: nowrap;
}

.pill-en-proceso {
  background-color: var(--status-blue);
  color: #ffffff;
}

.pill-espera {
  background-color: #e2e6ea;
  color: #495057;
}

.pill-confirmado {
  background-color: #d1fae5;
  color: #065f46;
}
</style>
