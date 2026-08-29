<template>
  <div class="page-container">
    <!-- Header de la página -->
    <div class="page-header">
      <div>
        <h1 class="page-title">
          <i class="pi pi-th-large text-primary"></i>
          Panel de Control del Taller
        </h1>
        <p class="page-subtitle">
          Resumen operativo en tiempo real • Sistema de Gestión de Talleres Mecánicos
        </p>
      </div>
      <div class="header-actions">
        <NuxtLink to="/ordenes" class="btn-primary">
          <i class="pi pi-plus"></i>
          Nueva Orden de Trabajo
        </NuxtLink>
      </div>
    </div>

    <!-- Tarjetas de Métricas / KPIs -->
    <div class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-icon car-icon">
          <i class="pi pi-car"></i>
        </div>
        <div class="kpi-content">
          <span class="kpi-label">Vehículos en Taller</span>
          <span class="kpi-value">8</span>
          <span class="kpi-subtext positive"><i class="pi pi-arrow-up"></i> 2 ingresados hoy</span>
        </div>
      </div>

      <div class="kpi-card">
        <div class="kpi-icon calendar-icon">
          <i class="pi pi-calendar"></i>
        </div>
        <div class="kpi-content">
          <span class="kpi-label">Turnos del Día (RF-03)</span>
          <span class="kpi-value">5</span>
          <span class="kpi-subtext info"><i class="pi pi-clock"></i> 3 pendientes</span>
        </div>
      </div>

      <div class="kpi-card">
        <div class="kpi-icon wrench-icon">
          <i class="pi pi-cog"></i>
        </div>
        <div class="kpi-content">
          <span class="kpi-label">OT en Reparación (RF-07)</span>
          <span class="kpi-value">6</span>
          <span class="kpi-subtext warning"><i class="pi pi-spin pi-spinner"></i> En proceso</span>
        </div>
      </div>

      <div class="kpi-card">
        <div class="kpi-icon alert-icon">
          <i class="pi pi-exclamation-triangle"></i>
        </div>
        <div class="kpi-content">
          <span class="kpi-label">Stock Crítico (RF-05)</span>
          <span class="kpi-value">3</span>
          <span class="kpi-subtext danger"><i class="pi pi-box"></i> Punto de reposición</span>
        </div>
      </div>
    </div>

    <!-- Accesos Rápidos y Estado del Flujo Operativo -->
    <div class="dashboard-grid">
      <!-- Órdenes Activas Recientes -->
      <div class="card-custom main-card">
        <div class="card-header-flex">
          <div>
            <h3 class="card-title">Órdenes de Trabajo Recientes</h3>
            <p class="card-subtitle">Seguimiento en vivo de reparaciones en curso</p>
          </div>
          <NuxtLink to="/ordenes" class="link-action">Ver todas <i class="pi pi-arrow-right"></i></NuxtLink>
        </div>

        <div class="table-responsive">
          <table class="custom-table">
            <thead>
              <tr>
                <th>OT #</th>
                <th>Vehículo</th>
                <th>Cliente</th>
                <th>Mecánico</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="ot in ordenesRecientes" :key="ot.id">
                <td class="font-bold text-primary">#OT-{{ ot.id }}</td>
                <td>
                  <div class="car-pill">
                    <span class="plate">{{ ot.patente }}</span>
                    <span class="model">{{ ot.vehiculo }}</span>
                  </div>
                </td>
                <td>{{ ot.cliente }}</td>
                <td>
                  <div class="mechanic-pill">
                    <i class="pi pi-user text-muted"></i>
                    <span>{{ ot.mecanico }}</span>
                  </div>
                </td>
                <td>
                  <span :class="['status-tag', ot.estadoClass]">
                    {{ ot.estado }}
                  </span>
                </td>
                <td>
                  <div class="action-buttons">
                    <NuxtLink :to="`/peritaje?ot=${ot.id}`" class="action-btn" title="Ver Peritaje Fotográfico (RF-04)">
                      <i class="pi pi-camera"></i>
                    </NuxtLink>
                    <NuxtLink :to="`/ficha/${ot.patente}`" class="action-btn" title="Ver Ficha Pública QR (RF-09)">
                      <i class="pi pi-qrcode"></i>
                    </NuxtLink>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Accesos Rápidos a Módulos del SRS -->
      <div class="card-custom side-card">
        <h3 class="card-title">Módulos del Sistema</h3>
        <p class="card-subtitle">Acceso directo a funciones operativas</p>

        <div class="quick-links-list">
          <NuxtLink to="/clientes" class="quick-link-card">
            <div class="ql-icon"><i class="pi pi-users"></i></div>
            <div class="ql-text">
              <span class="ql-title">Clientes & ARCA (RF-01)</span>
              <span class="ql-desc">Condición fiscal, CUIT y altas</span>
            </div>
            <i class="pi pi-chevron-right ql-arrow"></i>
          </NuxtLink>

          <NuxtLink to="/vehiculos" class="quick-link-card">
            <div class="ql-icon"><i class="pi pi-car"></i></div>
            <div class="ql-text">
              <span class="ql-title">Vehículos (RF-02)</span>
              <span class="ql-desc">Patentes, modelos y kilometrajes</span>
            </div>
            <i class="pi pi-chevron-right ql-arrow"></i>
          </NuxtLink>

          <NuxtLink to="/repuestos" class="quick-link-card">
            <div class="ql-icon"><i class="pi pi-box"></i></div>
            <div class="ql-text">
              <span class="ql-title">Matriz de Repuestos (RF-05)</span>
              <span class="ql-desc">Compatibilidad por marca y motor</span>
            </div>
            <i class="pi pi-chevron-right ql-arrow"></i>
          </NuxtLink>

          <NuxtLink to="/facturacion" class="quick-link-card">
            <div class="ql-icon"><i class="pi pi-receipt"></i></div>
            <div class="ql-text">
              <span class="ql-title">Facturación ARCA (RF-10)</span>
              <span class="ql-desc">Emisión Facturas A, B, C con CAE</span>
            </div>
            <i class="pi pi-chevron-right ql-arrow"></i>
          </NuxtLink>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const ordenesRecientes = ref([
  {
    id: 1042,
    patente: 'AF 892 PL',
    vehiculo: 'Toyota Hilux 2.8 TDI',
    cliente: 'Agropecuaria El Ombú S.A.',
    mecanico: 'Carlos Gómez',
    estado: 'En Taller',
    estadoClass: 'status-progress'
  },
  {
    id: 1041,
    patente: 'AE 341 KC',
    vehiculo: 'Volkswagen Gol Trend 1.6',
    cliente: 'Martín Rindlisbacher',
    mecanico: 'Lucas Benítez',
    estado: 'Peritaje Listo',
    estadoClass: 'status-info'
  },
  {
    id: 1040,
    patente: 'AD 512 MP',
    vehiculo: 'Ford Ranger 3.2 4x4',
    cliente: 'Esteban Morales',
    mecanico: 'Carlos Gómez',
    estado: 'Finalizado',
    estadoClass: 'status-success'
  },
  {
    id: 1039,
    patente: 'AC 918 XT',
    vehiculo: 'Chevrolet Cruze 1.4T',
    cliente: 'Lucía Fernández',
    mecanico: 'Lucas Benítez',
    estado: 'Esperando Repuestos',
    estadoClass: 'status-warning'
  }
])
</script>

<style scoped>
.header-actions {
  display: flex;
  gap: 0.75rem;
}

.btn-primary {
  background: #2563eb;
  color: #ffffff;
  padding: 0.65rem 1.25rem;
  border-radius: 8px;
  text-decoration: none;
  font-weight: 600;
  font-size: 0.9rem;
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  box-shadow: 0 4px 10px rgba(37, 99, 235, 0.25);
  transition: all 0.2s ease;
}

.btn-primary:hover {
  background: #1d4ed8;
  transform: translateY(-1px);
}

/* KPI Grid */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 1.25rem;
  margin-bottom: 2rem;
}

.kpi-card {
  background: var(--bg-card);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 1.25rem;
  display: flex;
  align-items: center;
  gap: 1.25rem;
  box-shadow: var(--shadow-sm);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.kpi-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.kpi-icon {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.4rem;
}

.car-icon {
  background: rgba(37, 99, 235, 0.12);
  color: #2563eb;
}

.calendar-icon {
  background: rgba(16, 185, 129, 0.12);
  color: #10b981;
}

.wrench-icon {
  background: rgba(245, 158, 11, 0.12);
  color: #f59e0b;
}

.alert-icon {
  background: rgba(239, 68, 68, 0.12);
  color: #ef4444;
}

.kpi-content {
  display: flex;
  flex-direction: column;
}

.kpi-label {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text-muted);
}

.kpi-value {
  font-size: 1.75rem;
  font-weight: 800;
  color: var(--text-main);
  line-height: 1.2;
  margin: 0.2rem 0;
}

.kpi-subtext {
  font-size: 0.75rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 0.3rem;
}

.kpi-subtext.positive { color: #10b981; }
.kpi-subtext.info { color: #3b82f6; }
.kpi-subtext.warning { color: #f59e0b; }
.kpi-subtext.danger { color: #ef4444; }

/* Dashboard layout grid */
.dashboard-grid {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 1.5rem;
}

@media (max-width: 1024px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}

.card-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1.25rem;
}

.card-title {
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--text-main);
}

.card-subtitle {
  font-size: 0.82rem;
  color: var(--text-muted);
  margin-top: 0.15rem;
}

.link-action {
  color: #2563eb;
  text-decoration: none;
  font-size: 0.85rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.link-action:hover {
  text-decoration: underline;
}

/* Custom Table */
.table-responsive {
  overflow-x: auto;
}

.custom-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
}

.custom-table th {
  padding: 0.75rem 1rem;
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--text-muted);
  border-bottom: 1px solid var(--border-subtle);
}

.custom-table td {
  padding: 0.9rem 1rem;
  font-size: 0.88rem;
  color: var(--text-main);
  border-bottom: 1px solid var(--border-subtle);
}

.car-pill {
  display: flex;
  flex-direction: column;
}

.plate {
  font-family: monospace;
  font-weight: 700;
  font-size: 0.82rem;
  background: var(--bg-card-hover);
  padding: 0.15rem 0.4rem;
  border-radius: 4px;
  width: fit-content;
  border: 1px solid var(--border-subtle);
}

.model {
  font-size: 0.82rem;
  color: var(--text-muted);
  margin-top: 0.15rem;
}

.mechanic-pill {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.82rem;
}

.status-tag {
  font-size: 0.72rem;
  font-weight: 700;
  padding: 0.25rem 0.65rem;
  border-radius: 6px;
  display: inline-block;
}

.status-progress { background: rgba(37, 99, 235, 0.12); color: #2563eb; }
.status-info { background: rgba(14, 165, 233, 0.12); color: #0284c7; }
.status-success { background: rgba(16, 185, 129, 0.12); color: #059669; }
.status-warning { background: rgba(245, 158, 11, 0.12); color: #d97706; }

.action-buttons {
  display: flex;
  gap: 0.5rem;
}

.action-btn {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: var(--bg-card-hover);
  border: 1px solid var(--border-subtle);
  color: var(--text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  text-decoration: none;
  font-size: 0.85rem;
  transition: all 0.2s ease;
}

.action-btn:hover {
  background: #2563eb;
  color: #ffffff;
  border-color: #2563eb;
}

/* Quick links */
.quick-links-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-top: 1rem;
}

.quick-link-card {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  padding: 0.85rem 1rem;
  border-radius: 10px;
  border: 1px solid var(--border-subtle);
  background: var(--bg-card-hover);
  text-decoration: none;
  color: var(--text-main);
  transition: all 0.2s ease;
}

.quick-link-card:hover {
  border-color: #2563eb;
  transform: translateX(3px);
}

.ql-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #2563eb;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1rem;
}

.ql-text {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.ql-title {
  font-weight: 700;
  font-size: 0.85rem;
}

.ql-desc {
  font-size: 0.72rem;
  color: var(--text-muted);
}

.ql-arrow {
  color: var(--text-muted);
  font-size: 0.8rem;
}
</style>
