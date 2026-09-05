import type { Cliente, FormularioCliente } from '~/types/cliente'

export const useClientes = () => {
  // Estado reactivo global de clientes
  const listaClientes = useState<Cliente[]>('global_clientes', () => [
    {
      id: 1,
      nombre: 'Carlos Rodríguez',
      cuit: '20-35891234-9',
      email: 'carlos.r@email.com',
      telefono: '+54 9 2392 411223',
      direccion: 'Av. Villegas 450, Trenque Lauquen',
      cantidadVehiculos: 1,
      vehiculoPrincipal: 'Toyota Hilux 2.8 TDI',
      patentePrincipal: 'ABC-123',
      ultimaVisita: '12 Oct 2023',
      estaActivo: true
    },
    {
      id: 2,
      nombre: 'María Gómez',
      cuit: '27-38192837-4',
      email: 'maria.g@email.com',
      telefono: '+54 9 2392 455667',
      direccion: 'San Martín 1220, Trenque Lauquen',
      cantidadVehiculos: 1,
      vehiculoPrincipal: 'Honda Civic EXL',
      patentePrincipal: 'XYZ-987',
      ultimaVisita: '05 Sep 2023',
      estaActivo: true
    },
    {
      id: 3,
      nombre: 'Empresa Logística S.A.',
      cuit: '30-71239845-1',
      email: 'flota@logistica.com',
      telefono: '+54 9 2392 499881',
      direccion: 'Ruta 5 Km 445',
      cantidadVehiculos: 4,
      vehiculoPrincipal: 'Ford Transit (Flota de 4)',
      patentePrincipal: 'AD 512 MP',
      ultimaVisita: '28 Ago 2023',
      estaActivo: true
    },
    {
      id: 4,
      nombre: 'Martín Rindlisbacher',
      cuit: '20-41892831-2',
      email: 'martin@email.com',
      telefono: '+54 9 2392 612345',
      direccion: 'Cuello 230, Trenque Lauquen',
      cantidadVehiculos: 2,
      vehiculoPrincipal: 'Volkswagen Gol Trend 1.6',
      patentePrincipal: 'AE 341 KC',
      ultimaVisita: '18 Feb 2026',
      estaActivo: true
    },
    {
      id: 5,
      nombre: 'Agropecuaria El Ombú S.A.',
      cuit: '30-68192341-8',
      email: 'contacto@elombu.com',
      telefono: '+54 9 2392 519922',
      direccion: 'Zona Rural Lote 12',
      cantidadVehiculos: 3,
      vehiculoPrincipal: 'Toyota Hilux 2.8 TDI (Flota)',
      patentePrincipal: 'AF 892 PL',
      ultimaVisita: '22 Feb 2026',
      estaActivo: true
    }
  ])

  const obtenerClientePorId = (id: number): Cliente | undefined => {
    return listaClientes.value.find(c => c.id === id)
  }

  const agregarCliente = (form: FormularioCliente): Cliente => {
    const tieneVehiculo = !!form.patente || !!form.vehiculo
    const nuevo: Cliente = {
      id: Date.now(),
      nombre: form.nombre,
      cuit: form.cuit,
      email: form.email,
      telefono: form.telefono,
      direccion: form.direccion || '',
      cantidadVehiculos: tieneVehiculo ? 1 : 0,
      vehiculoPrincipal: form.vehiculo || (tieneVehiculo ? 'Vehículo registrado' : 'Sin vehículos'),
      patentePrincipal: form.patente || 'S/P',
      ultimaVisita: 'Hoy',
      estaActivo: true
    }
    listaClientes.value.unshift(nuevo)
    return nuevo
  }

  const actualizarCliente = (id: number, form: FormularioCliente): boolean => {
    const idx = listaClientes.value.findIndex(c => c.id === id)
    if (idx === -1) return false

    const tieneVehiculo = !!form.patente || !!form.vehiculo
    listaClientes.value[idx] = {
      ...listaClientes.value[idx],
      nombre: form.nombre,
      cuit: form.cuit,
      email: form.email,
      telefono: form.telefono,
      direccion: form.direccion || listaClientes.value[idx].direccion,
      cantidadVehiculos: tieneVehiculo ? Math.max(1, listaClientes.value[idx].cantidadVehiculos) : listaClientes.value[idx].cantidadVehiculos,
      vehiculoPrincipal: form.vehiculo || listaClientes.value[idx].vehiculoPrincipal,
      patentePrincipal: form.patente || listaClientes.value[idx].patentePrincipal
    }
    return true
  }

  const darDeBajaCliente = (id: number) => {
    const cliente = listaClientes.value.find(c => c.id === id)
    if (cliente) {
      cliente.estaActivo = false
    }
  }

  return {
    listaClientes,
    obtenerClientePorId,
    agregarCliente,
    actualizarCliente,
    darDeBajaCliente
  }
}
