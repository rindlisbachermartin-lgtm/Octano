<script setup lang="ts">
import {
  Plus,
  Search,
  Package,
} from 'lucide-vue-next'
import type { Part } from '~/types'

const { db, lowStock } = useDatabase()
const { money, matches } = useHelpers()
const { notify } = useToast()

const search = ref('')
const filter = ref('Todos')
const inventoryVehicle = ref('')
const formModalOpen = ref(false)

const filteredParts = computed(() =>
  db.value.parts.filter(
    (p) =>
      matches(search.value, p.name, p.brand, p.oem) &&
      (filter.value !== 'Stock bajo' || p.stock <= p.min) &&
      (!inventoryVehicle.value || p.compatible.includes(Number(inventoryVehicle.value)))
  )
)

function restock(p: Part) {
  p.stock += 5
  notify(`Se agregaron 5 unidades a ${p.name}.`)
}

function handleCreated(p: Part) {
  formModalOpen.value = false
  notify(`Repuesto "${p.name}" agregado al inventario.`)
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          <span class="tiny-star">✳</span>
          TALLER CENTRAL / INVENTARIO
        </div>
        <h1>La pieza que necesitás.</h1>
      </div>
      <button class="button primary" @click="formModalOpen = true">
        <Plus :size="17" />Nuevo repuesto
      </button>
    </section>

    <div class="inventory-banner">
      <div>
        <Package :size="24" />
        <span>
          <strong>Compatibilidad de repuestos</strong>
          <small>Filtrá por vehículo para ver solo piezas compatibles.</small>
        </span>
      </div>
      <label>
        <span class="sr-only">Compatibilidad con vehículo</span>
        <select v-model="inventoryVehicle">
          <option value="">Todos los vehículos</option>
          <option v-for="v in db.vehicles" :key="v.id" :value="v.id">
            {{ v.brand }} {{ v.model }} · {{ v.year }} · {{ v.engine }}
          </option>
        </select>
      </label>
    </div>

    <div class="list-toolbar">
      <div class="filter-tabs">
        <button
          v-for="f in ['Todos', 'Stock bajo']"
          :key="f"
          :class="{ active: filter === f }"
          @click="filter = f"
        >
          {{ f }}
          <span v-if="f === 'Stock bajo'">{{ lowStock.length }}</span>
        </button>
      </div>
      <label class="search-box">
        <Search :size="17" />
        <input
          v-model="search"
          placeholder="Nombre o código OEM…"
          aria-label="Buscar repuestos"
        />
      </label>
    </div>

    <section class="panel table-scroll">
      <table>
        <thead>
          <tr>
            <th>REPUESTO</th>
            <th>MARCA / OEM</th>
            <th>COMPATIBILIDAD</th>
            <th>STOCK</th>
            <th>PRECIO</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in filteredParts" :key="p.id">
            <td>
              <strong>{{ p.name }}</strong>
            </td>
            <td>
              <span>{{ p.brand }}</span>
              <small class="muted">OEM: {{ p.oem }}</small>
            </td>
            <td>
              <span class="badge neutral">
                {{ p.compatible.length }} modelos
              </span>
            </td>
            <td>
              <span :class="['badge', p.stock <= p.min ? 'amber' : 'green']">
                {{ p.stock }} un. (mín {{ p.min }})
              </span>
            </td>
            <td>
              <strong>{{ money(p.price) }}</strong>
            </td>
            <td>
              <button class="button small" @click="restock(p)">
                +5 Stock
              </button>
            </td>
          </tr>
          <tr v-if="!filteredParts.length">
            <td colspan="6" class="empty-state">
              No hay repuestos que coincidan con la búsqueda.
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <!-- Modal -->
    <InventarioModalRepuesto
      :open="formModalOpen"
      @close="formModalOpen = false"
      @created="handleCreated"
    />
  </div>
</template>
