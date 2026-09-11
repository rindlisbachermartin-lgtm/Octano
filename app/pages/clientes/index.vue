<script setup lang="ts">
import {
  Plus,
  Search,
  ArrowUpRight,
} from 'lucide-vue-next'
import type { Client } from '~/types'

const { db } = useDatabase()
const { initials, matches } = useHelpers()
const { notify } = useToast()

const search = ref('')
const formModalOpen = ref(false)
const detailModalOpen = ref(false)
const selectedClient = ref<Client | null>(null)

const filteredClients = computed(() =>
  db.value.clients.filter(
    (c) => c.active && matches(search.value, c.name, c.doc, c.phone, c.email)
  )
)

function openDetail(c: Client) {
  selectedClient.value = c
  detailModalOpen.value = true
}

function openEdit(c: Client) {
  selectedClient.value = c
  detailModalOpen.value = false
  formModalOpen.value = true
}

function openNew() {
  selectedClient.value = null
  formModalOpen.value = true
}

function handleSave(clientData: Partial<Client>) {
  if (selectedClient.value) {
    Object.assign(selectedClient.value, clientData)
    notify('Datos del cliente actualizados.')
  } else {
    const newClient: Client = {
      id: Date.now(),
      name: clientData.name || '',
      doc: clientData.doc || '',
      phone: clientData.phone || '',
      email: clientData.email || '',
      active: true,
    }
    db.value.clients.push(newClient)
    notify('Cliente agregado con éxito.')
  }
  formModalOpen.value = false
}

function handleArchive(c: Client) {
  c.active = false
  detailModalOpen.value = false
  notify('Cliente archivado. Su historial se conserva.')
}
</script>

<template>
  <div class="page-content">
    <section class="page-heading">
      <div>
        <div class="eyebrow">
          <span class="tiny-star">✳</span>
          TALLER CENTRAL / CLIENTES
        </div>
        <h1>El motor son las personas.</h1>
      </div>
      <button class="button primary" @click="openNew">
        <Plus :size="17" />Nuevo cliente
      </button>
    </section>

    <div class="list-toolbar">
      <span class="muted">{{ filteredClients.length }} clientes activos</span>
      <label class="search-box">
        <Search :size="17" />
        <input
          v-model="search"
          placeholder="Nombre, documento o teléfono…"
          aria-label="Buscar clientes"
        />
      </label>
    </div>

    <section class="panel table-scroll">
      <table>
        <thead>
          <tr>
            <th>CLIENTE</th>
            <th>DNI / CUIT</th>
            <th>CONTACTO</th>
            <th>VEHÍCULOS</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in filteredClients" :key="c.id">
            <td>
              <div class="client-cell">
                <span class="avatar">{{ initials(c.name) }}</span>
                <strong>{{ c.name }}</strong>
              </div>
            </td>
            <td>{{ c.doc }}</td>
            <td>
              <span>{{ c.phone || 'Sin teléfono' }}</span>
              <small>{{ c.email || 'Sin correo' }}</small>
            </td>
            <td>
              <span class="badge neutral">
                {{ db.vehicles.filter((v) => v.client === c.id).length }} vehículo(s)
              </span>
            </td>
            <td>
              <button class="text-button" @click="openDetail(c)">
                Ver cliente <ArrowUpRight :size="15" />
              </button>
            </td>
          </tr>
          <tr v-if="!filteredClients.length">
            <td colspan="5" class="empty-state">
              No hay clientes que coincidan con la búsqueda.
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <!-- Modals -->
    <ClientesModalDetalleCliente
      :open="detailModalOpen"
      :client="selectedClient"
      @close="detailModalOpen = false"
      @edit="openEdit"
      @archive="handleArchive"
    />

    <ClientesModalFormularioCliente
      :open="formModalOpen"
      :client="selectedClient"
      @close="formModalOpen = false"
      @save="handleSave"
    />
  </div>
</template>
