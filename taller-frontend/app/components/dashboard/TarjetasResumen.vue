<template>
  <div class="summary-cards-grid">
    <!-- Tarjeta 1: Turnos de Hoy -->
    <div class="summary-card">
      <div class="card-top-row">
        <span class="card-label">Turnos de Hoy</span>
        <span class="material-symbols-outlined icon-primary">calendar_today</span>
      </div>
      <div class="card-bottom-row">
        <span class="card-number">{{ turnosHoy }}</span>
        <span class="badge-status-blue">+{{ nuevosTurnos }} nuevos</span>
      </div>
    </div>

    <!-- Tarjeta 2: Ingresos del Mes -->
    <div class="summary-card">
      <div class="card-top-row">
        <span class="card-label">Ingresos del Mes</span>
        <span class="material-symbols-outlined icon-primary">payments</span>
      </div>
      <div class="card-bottom-row">
        <span class="card-number">${{ ingresosMes.toLocaleString('es-AR') }}</span>
        <span class="text-trend-green">
          <span class="material-symbols-outlined icon-trend">trending_up</span>
          {{ porcentajeCrecimiento }}%
        </span>
      </div>
    </div>

    <!-- Tarjeta 3: Facturas Pendientes -->
    <div class="summary-card">
      <div class="card-top-row">
        <span class="card-label">Facturas Pendientes</span>
        <span class="material-symbols-outlined icon-tertiary">receipt_long</span>
      </div>
      <div class="card-bottom-row">
        <span class="card-number">{{ facturasPendientesCantidad }}</span>
        <span class="text-amount-tertiary">${{ facturasPendientesMonto.toLocaleString('es-AR') }}</span>
      </div>
    </div>

    <!-- Tarjeta 4: Stock Bajo -->
    <div class="summary-card">
      <div class="card-top-row">
        <span class="card-label">Stock Bajo</span>
        <span class="material-symbols-outlined icon-error">warning</span>
      </div>
      <div class="card-bottom-row">
        <span class="card-number">{{ repuestosStockBajo }}</span>
        <span class="badge-stock-danger">Revisar</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  turnosHoy?: number
  nuevosTurnos?: number
  ingresosMes?: number
  porcentajeCrecimiento?: number
  facturasPendientesCantidad?: number
  facturasPendientesMonto?: number
  repuestosStockBajo?: number
}

withDefaults(defineProps<Props>(), {
  turnosHoy: 12,
  nuevosTurnos: 2,
  ingresosMes: 14250,
  porcentajeCrecimiento: 8.5,
  facturasPendientesCantidad: 5,
  facturasPendientesMonto: 3100,
  repuestosStockBajo: 8
})
</script>

<style scoped>
.summary-cards-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

@media (max-width: 1024px) {
  .summary-cards-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .summary-cards-grid {
    grid-template-columns: 1fr;
  }
}

.summary-card {
  background-color: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--border-radius-md);
  padding: 16px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 112px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.card-top-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.card-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.icon-primary { color: var(--primary); font-size: 20px; }
.icon-tertiary { color: #b16101; font-size: 20px; }
.icon-error { color: #ba1a1a; font-size: 20px; }

.card-bottom-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
}

.card-number {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1;
}

.badge-status-blue {
  font-size: 12px;
  font-weight: 600;
  color: var(--status-blue);
  background-color: rgba(206, 229, 255, 0.35);
  padding: 2px 8px;
  border-radius: var(--border-radius-sm);
}

.text-trend-green {
  font-size: 12px;
  font-weight: 600;
  color: var(--status-green);
  display: flex;
  align-items: center;
  gap: 2px;
}

.icon-trend { font-size: 16px; }

.text-amount-tertiary {
  font-size: 12px;
  font-weight: 600;
  color: #b16101;
}

.badge-stock-danger {
  font-size: 12px;
  font-weight: 600;
  color: #ba1a1a;
  background-color: rgba(255, 218, 214, 0.6);
  padding: 2px 8px;
  border-radius: var(--border-radius-sm);
}
</style>
