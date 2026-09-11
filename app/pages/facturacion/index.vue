<script setup lang="ts">
import {
  Plus,
  Receipt,
  CreditCard,
  CircleCheck,
} from 'lucide-vue-next'
import type { Invoice } from '~/types'

const { db, revenue, unpaid, owner } = useDatabase()
const { money, statusClass } = useHelpers()
const { notify } = useToast()

const formModalOpen = ref(false)

function handleCreated(invoice: Invoice) {
  formModalOpen.value = false
  notify(`Comprobante #${invoice.id} emitido.`)
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          <span class="tiny-star">✳</span>
          TALLER CENTRAL / FACTURACIÓN
        </div>
        <h1>Las cuentas, al día.</h1>
      </div>
      <button class="button primary" @click="formModalOpen = true">
        <Plus :size="17" />Nuevo comprobante
      </button>
    </section>

    <div class="integration-notice">
      <Receipt :size="20" />
      <div>
        <strong>Facturación en modo demostración</strong>
        <p>Los comprobantes son de prueba, sin validez fiscal. La conexión con ARCA no está activa.</p>
      </div>
      <span class="badge neutral">SANDBOX</span>
    </div>

    <section class="stats-grid invoice-stats">
      <div class="stat-card">
        <div>Total cobrado</div>
        <strong>{{ money(revenue) }}</strong>
        <small>Septiembre 2026</small>
      </div>
      <div class="stat-card">
        <div>Pendiente de cobro</div>
        <strong>{{ money(unpaid.reduce((s, i) => s + i.total, 0)) }}</strong>
        <small>{{ unpaid.length }} comprobantes pendientes</small>
      </div>
    </section>

    <section class="panel table-scroll">
      <table>
        <thead>
          <tr>
            <th>COMPROBANTE</th>
            <th>CLIENTE / CONCEPTO</th>
            <th>FECHA</th>
            <th>IMPORTE</th>
            <th>ESTADO</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="i in db.invoices" :key="i.id">
            <td>
              <strong>Factura {{ i.type }}</strong>
              <small>DEMO-{{ String(i.id).padStart(6, '0') }}</small>
            </td>
            <td>
              {{ owner(i.vehicle)?.name }}
              <small>{{ i.description }}</small>
            </td>
            <td>{{ i.date }}</td>
            <td>
              <strong>{{ money(i.total) }}</strong>
            </td>
            <td>
              <span :class="['badge', statusClass(i.status)]">{{ i.status }}</span>
            </td>
            <td>
              <button
                v-if="i.status === 'Pendiente'"
                class="button small"
                @click="
                  i.status = 'Cobrado';
                  notify('Cobro registrado en la maqueta.');
                "
              >
                <CreditCard :size="14" />Registrar cobro
              </button>
              <CircleCheck v-else :size="18" class="success-icon" />
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <!-- Modal -->
    <FacturacionModalFactura
      :open="formModalOpen"
      @close="formModalOpen = false"
      @created="handleCreated"
    />
  </div>
</template>
