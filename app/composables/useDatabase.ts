import type { Database, Client, Vehicle } from '~/types'

const seed: Database = {
  clients: [
    { id: 1, name: 'Lucía Fernández', doc: '32.456.789', phone: '2392 45-6789', email: 'lucia.fernandez@ejemplo.com', active: true },
    { id: 2, name: 'Martín González', doc: '28.391.450', phone: '2392 51-2345', email: 'martin.g@ejemplo.com', active: true },
    { id: 3, name: 'Sofía Martínez', doc: '35.721.604', phone: '2392 60-7890', email: 'sofia.m@ejemplo.com', active: true },
    { id: 4, name: 'Diego López', doc: '30.652.918', phone: '2392 42-1122', email: 'diego.l@ejemplo.com', active: true },
    { id: 5, name: 'Agustina Pérez', doc: '38.105.273', phone: '2392 55-3344', email: 'agustina.p@ejemplo.com', active: true },
  ],
  vehicles: [
    {
      id: 1, client: 1, brand: 'Volkswagen', model: 'Golf', year: 2018, engine: '1.4 TSI', plate: 'AC 284 FN', km: 68450, color: '#b9c3b8', qrCode: null,
      lastService: {
        date: '2026-03-12',
        km: 60000,
        oil: 'Castrol Edge 5W-30 Sintético',
        filters: ['Filtro de aceite MANN W 712/95', 'Filtro de aire FRAM CA 10751', 'Filtro de habitáculo'],
        notes: 'Service programado de los 60.000 km con reemplazo integral de fluidos.',
      },
      lastTimingBelt: {
        date: '2025-09-18',
        km: 50400,
        parts: ['Kit distribución SKF (correa dentada + tensor)', 'Bomba de agua Dolz', 'Refrigerante G12+'],
        notes: 'Reemplazo preventivo de kit de distribución y bomba de agua.',
      },
    },
    {
      id: 2, client: 2, brand: 'Toyota', model: 'Hilux', year: 2021, engine: '2.8 TDI', plate: 'AE 619 MR', km: 92400, color: '#bcc4cf', qrCode: null,
      lastService: {
        date: '2026-06-20',
        km: 85000,
        oil: 'Mobil Delvac 1 ESP 5W-30 Sintético',
        filters: ['Filtro de aceite original Toyota', 'Filtro de combustible', 'Filtro de aire'],
        notes: 'Service completo para trabajo pesado con purga de trampa de agua.',
      },
      lastTimingBelt: {
        date: '2025-11-10',
        km: 75000,
        parts: ['Correa dentada original Toyota', 'Tensores y poleas guía SKF'],
        notes: 'Sustitución de distribución según plan de mantenimiento preventivo.',
      },
    },
    {
      id: 3, client: 3, brand: 'Peugeot', model: '208', year: 2022, engine: '1.6 VTi', plate: 'AF 102 SL', km: 35200, color: '#ccb5a3', qrCode: null,
      lastService: {
        date: '2026-01-15',
        km: 30000,
        oil: 'Total Quartz Ineo First 0W-30',
        filters: ['Filtro de aceite Purflux', 'Filtro de aire'],
        notes: 'Service oficial de 30.000 km.',
      },
      lastTimingBelt: null,
    },
    {
      id: 4, client: 4, brand: 'Ford', model: 'Focus', year: 2017, engine: '2.0', plate: 'AB 457 CD', km: 112800, color: '#bac4d0', qrCode: null,
      lastService: {
        date: '2026-02-14',
        km: 105000,
        oil: 'Motul 8100 X-cess 5W-40',
        filters: ['Filtro de aceite Bosch', 'Filtro de aire Fram', 'Filtro de polen'],
        notes: 'Mantenimiento de los 105.000 km.',
      },
      lastTimingBelt: {
        date: '2024-10-05',
        km: 90000,
        parts: ['Kit distribución Gates PowerGrip', 'Bomba de agua SKF', 'Correa de accesorios'],
        notes: 'Cambio completo de kit de distribución y bomba a los 90.000 km.',
      },
    },
    {
      id: 5, client: 5, brand: 'Renault', model: 'Sandero', year: 2020, engine: '1.6', plate: 'AD 892 GP', km: 48100, color: '#c2b6c9', qrCode: null,
      lastService: {
        date: '2025-12-05',
        km: 40000,
        oil: 'Elf Evolution 700 10W-40 Semi-sintético',
        filters: ['Filtro de aceite Renault', 'Filtro de aire'],
        notes: 'Cambio periódico de aceite y filtros.',
      },
      lastTimingBelt: null,
    },
  ],
  orders: [
    {
      id: 1048, vehicle: 1, service: 'Service de los 70.000 km', status: 'En proceso', mechanic: 'Nicolás', bay: 1,
      date: '2026-09-07', time: '09:00', progress: 65,
      diagnosis: 'Mantenimiento preventivo. Revisar nivel de fluidos y sistema de frenado.',
      tasks: [{ name: 'Revisión general', done: true }, { name: 'Cambio de aceite y filtros', done: true }, { name: 'Control de frenos', done: false }],
      parts: [], notes: '', photos: [],
      serviceTypes: ['Service de mantenimiento'],
      oilSpec: 'Castrol Edge 5W-30 Sintético',
      replacedFilters: ['Filtro de aceite', 'Filtro de aire', 'Filtro de habitáculo'],
    },
    {
      id: 1047, vehicle: 2, service: 'Cambio de pastillas de freno', status: 'En proceso', mechanic: 'Santiago', bay: 2,
      date: '2026-09-07', time: '09:30', progress: 40,
      diagnosis: 'Desgaste de pastillas delanteras. Controlar discos.',
      tasks: [{ name: 'Diagnóstico de frenos', done: true }, { name: 'Reemplazo de pastillas', done: false }, { name: 'Prueba de frenado', done: false }],
      parts: [], notes: '', photos: [],
    },
    {
      id: 1046, vehicle: 3, service: 'Diagnóstico de motor', status: 'En espera', mechanic: 'Nicolás', bay: null,
      date: '2026-09-07', time: '10:00', progress: 0,
      diagnosis: 'Testigo de motor encendido. Realizar escaneo.',
      tasks: [{ name: 'Escaneo electrónico', done: false }],
      parts: [], notes: '', photos: [],
    },
    {
      id: 1045, vehicle: 4, service: 'Alineación y balanceo', status: 'Finalizado', mechanic: 'Santiago', bay: 3,
      date: '2026-09-07', time: '08:00', progress: 100,
      diagnosis: 'Vibración a velocidad de ruta.',
      tasks: [{ name: 'Alineación', done: true }, { name: 'Balanceo', done: true }],
      parts: [], notes: 'Control final realizado. Vehículo listo para retirar.', photos: [],
    },
  ],
  appointments: [
    { id: 1, vehicle: 5, date: '2026-09-07', time: '11:00', reason: 'Cambio de aceite y filtros', status: 'Programado' },
    { id: 2, vehicle: 2, date: '2026-09-07', time: '14:30', reason: 'Revisión de suspensión', status: 'Programado' },
    { id: 3, vehicle: 1, date: '2026-09-08', time: '09:30', reason: 'Control preventivo', status: 'Programado' },
    { id: 4, vehicle: 3, date: '2026-09-09', time: '10:00', reason: 'Diagnóstico check engine', status: 'Programado' },
    { id: 5, vehicle: 4, date: '2026-09-10', time: '15:00', reason: 'Alineación y balanceo', status: 'Programado' },
    { id: 6, vehicle: 2, date: '2026-09-11', time: '11:30', reason: 'Pastillas de freno', status: 'Programado' },
  ],
  parts: [
    { id: 1, name: 'Filtro de aceite', brand: 'MANN-FILTER', oem: 'W 712/95', stock: 3, min: 5, cost: 8500, price: 14500, compatible: [1, 4] },
    { id: 2, name: 'Pastillas de freno delanteras', brand: 'BOSCH', oem: 'BP 1628', stock: 2, min: 3, cost: 38000, price: 62000, compatible: [2] },
    { id: 3, name: 'Aceite sintético 5W-30 · 4L', brand: 'SHELL HELIX', oem: 'HX8 5W30', stock: 12, min: 4, cost: 28000, price: 46000, compatible: [1, 2, 3, 4, 5] },
    { id: 4, name: 'Filtro de aire', brand: 'FRAM', oem: 'CA 10751', stock: 8, min: 3, cost: 6500, price: 12000, compatible: [3, 5] },
    { id: 5, name: 'Kit de distribución', brand: 'SKF', oem: 'VKMA 03259', stock: 1, min: 2, cost: 92000, price: 138000, compatible: [3, 5] },
  ],
  quotes: [
    { id: 208, vehicle: 3, description: 'Diagnóstico electrónico y puesta a punto', labor: 45000, materials: 28000, status: 'Pendiente' },
    { id: 207, vehicle: 5, description: 'Service completo', labor: 35000, materials: 60500, status: 'Pendiente' },
  ],
  invoices: [
    { id: 126, vehicle: 4, description: 'Alineación y balanceo', total: 48000, type: 'B', status: 'Pendiente', date: '2026-09-07' },
    { id: 125, vehicle: 1, description: 'Mantenimiento preventivo', total: 96000, type: 'B', status: 'Cobrado', date: '2026-09-04' },
    { id: 124, vehicle: 2, description: 'Service de los 90.000 km', total: 185000, type: 'A', status: 'Cobrado', date: '2026-09-03' },
  ],
  notifications: [
    { id: 1, title: 'El Focus está listo para retirar', detail: 'OT #1045 · Aviso de WhatsApp simulado', read: false },
  ],
  qrCodes: [
    { code: 'OCT-2041', status: 'disponible', vehicleId: null, createdAt: '2026-09-01' },
    { code: 'OCT-2042', status: 'disponible', vehicleId: null, createdAt: '2026-09-01' },
    { code: 'OCT-2043', status: 'disponible', vehicleId: null, createdAt: '2026-09-01' },
    { code: 'OCT-2044', status: 'disponible', vehicleId: null, createdAt: '2026-09-01' },
    { code: 'OCT-2045', status: 'disponible', vehicleId: null, createdAt: '2026-09-01' },
    { code: 'OCT-2046', status: 'disponible', vehicleId: null, createdAt: '2026-09-01' },
    { code: 'OCT-2047', status: 'disponible', vehicleId: null, createdAt: '2026-09-01' },
    { code: 'OCT-2048', status: 'disponible', vehicleId: null, createdAt: '2026-09-01' },
    { code: 'OCT-2049', status: 'disponible', vehicleId: null, createdAt: '2026-09-01' },
    { code: 'OCT-2050', status: 'disponible', vehicleId: null, createdAt: '2026-09-01' },
    { code: 'OCT-2051', status: 'disponible', vehicleId: null, createdAt: '2026-09-01' },
    { code: 'OCT-2052', status: 'disponible', vehicleId: null, createdAt: '2026-09-01' },
  ],
}

export const useDatabase = () => {
  const db = useState<Database>('database', () => {
    if (import.meta.client) {
      try {
        const saved = JSON.parse(
          localStorage.getItem('octano-v1') ||
          localStorage.getItem('punto-motor-v1') ||
          'null'
        )
        if (saved && typeof saved === 'object') {
          // Backward compatibility check for qrCodes and vehicle qrCode
          if (!Array.isArray(saved.qrCodes)) {
            saved.qrCodes = structuredClone(seed.qrCodes)
          }
          if (Array.isArray(saved.vehicles)) {
            saved.vehicles.forEach((v: any) => {
              if (v.qrCode === undefined) v.qrCode = null
              const sv = seed.vehicles.find((s) => s.id === v.id)
              if (v.lastService === undefined && sv?.lastService) {
                v.lastService = structuredClone(sv.lastService)
              }
              if (v.lastTimingBelt === undefined && sv?.lastTimingBelt) {
                v.lastTimingBelt = structuredClone(sv.lastTimingBelt)
              }
            })
          }
          if (Object.keys(seed).every((k) => Array.isArray((saved as any)[k]))) {
            return saved as Database
          }
        }
      } catch { /* Use demo data when storage is unavailable. */ }
    }
    return structuredClone(seed)
  })

  const storageError = useState('storageError', () => false)

  // Persist to localStorage
  if (import.meta.client) {
    watch(db, (value) => {
      try {
        const json = JSON.stringify(value)
        localStorage.setItem('octano-v1', json)
        localStorage.setItem('punto-motor-v1', json)
        storageError.value = false
      } catch {
        storageError.value = true
      }
    }, { deep: true })
  }

  // Lookup helpers
  const vehicle = (id: number): Vehicle => db.value.vehicles.find((v) => v.id === Number(id)) || {} as Vehicle
  const client = (id: number): Client => db.value.clients.find((c) => c.id === Number(id)) || {} as Client
  const owner = (id: number) => client(vehicle(id).client)
  const vehicleName = (id: number) => `${vehicle(id).brand || ''} ${vehicle(id).model || ''}`

  // Computed
  const activeOrders = computed(() => db.value.orders.filter((o) => o.status !== 'Finalizado'))
  const finished = computed(() => db.value.orders.filter((o) => o.status === 'Finalizado'))
  const lowStock = computed(() => db.value.parts.filter((p) => p.stock <= p.min))
  const unpaid = computed(() => db.value.invoices.filter((i) => i.status === 'Pendiente'))
  const revenue = computed(() =>
    db.value.invoices.filter((i) => i.status === 'Cobrado').reduce((s, i) => s + i.total, 0)
  )

  // QR helpers
  const availableQrs = computed(() => db.value.qrCodes.filter((q) => q.status === 'disponible'))
  const assignedQrs = computed(() => db.value.qrCodes.filter((q) => q.status === 'asignado'))

  function assignQrToVehicle(code: string, vehicleId: number) {
    const v = db.value.vehicles.find((item) => item.id === Number(vehicleId))
    if (!v) return false

    // If another vehicle was previously linked to this code, clear it
    db.value.vehicles.forEach((other) => {
      if (other.id !== v.id && other.qrCode === code) {
        other.qrCode = null
      }
    })

    // If this vehicle already had a QR code, mark the old QR as unassigned
    if (v.qrCode && v.qrCode !== code) {
      const oldQr = db.value.qrCodes.find((q) => q.code === v.qrCode)
      if (oldQr) {
        oldQr.status = 'disponible'
        oldQr.vehicleId = null
      }
    }

    // Set new QR on vehicle
    v.qrCode = code

    // Update or insert QR in database
    let qr = db.value.qrCodes.find((q) => q.code === code)
    if (!qr) {
      qr = {
        code,
        status: 'asignado',
        vehicleId: v.id,
        createdAt: new Date().toISOString().slice(0, 10),
        assignedAt: new Date().toISOString().slice(0, 10),
      }
      db.value.qrCodes.push(qr)
    } else {
      qr.status = 'asignado'
      qr.vehicleId = v.id
      qr.assignedAt = new Date().toISOString().slice(0, 10)
    }

    return true
  }

  function unassignVehicleQr(vehicleId: number) {
    const v = db.value.vehicles.find((item) => item.id === Number(vehicleId))
    if (!v || !v.qrCode) return false

    const qr = db.value.qrCodes.find((q) => q.code === v.qrCode)
    if (qr) {
      qr.status = 'disponible'
      qr.vehicleId = null
      qr.assignedAt = null
    }

    v.qrCode = null
    return true
  }

  function generateQrBatch(count = 12) {
    const today = new Date().toISOString().slice(0, 10)
    const newItems: typeof seed.qrCodes = []
    
    // Find highest existing numeric index in OCT-XXXX or random
    const existingCodes = new Set(db.value.qrCodes.map((q) => q.code))
    let num = 2053
    while (newItems.length < count) {
      const code = `OCT-${num}`
      if (!existingCodes.has(code)) {
        newItems.push({
          code,
          status: 'disponible',
          vehicleId: null,
          createdAt: today,
        })
        existingCodes.add(code)
      }
      num++
    }

    db.value.qrCodes.push(...newItems)
    return newItems
  }

  function getVehicleByQr(code: string): Vehicle | undefined {
    return db.value.vehicles.find((v) => v.qrCode?.trim().toUpperCase() === code.trim().toUpperCase())
  }

  function createOrder(
    v: number,
    service: string,
    mechanic = 'Nicolás',
    bay: number | null = null,
    initialParts: OrderPart[] = [],
    serviceTypes: string[] = [],
    oilSpec = ''
  ) {
    const id = Math.max(1048, ...db.value.orders.map((o) => o.id)) + 1
    db.value.orders.unshift({
      id,
      vehicle: Number(v),
      service,
      status: 'En espera',
      mechanic,
      bay: bay ? Number(bay) : null,
      date: new Date().toISOString().slice(0, 10),
      time: '11:00',
      progress: 0,
      diagnosis: '',
      tasks: [{ name: service, done: false }],
      parts: initialParts || [],
      notes: '',
      photos: [],
      serviceTypes: serviceTypes || [],
      oilSpec: oilSpec || '',
    })
    return id
  }

  function recordOrderCompletion(o: Order) {
    const v = db.value.vehicles.find((item) => item.id === o.vehicle)
    if (!v) return

    const currentKm = o.km || v.km || 0
    if (o.km) {
      v.km = o.km
    }

    const types = o.serviceTypes || []
    const isService = types.some((t) => t.toLowerCase().includes('service')) || o.service.toLowerCase().includes('service') || o.service.toLowerCase().includes('aceite')
    const isTimingBelt = types.some((t) => t.toLowerCase().includes('distribuci')) || o.service.toLowerCase().includes('distribuci') || o.service.toLowerCase().includes('correa')

    if (isService) {
      const filterParts = (o.parts || [])
        .filter((p) => p.name.toLowerCase().includes('filtro'))
        .map((p) => p.name)
      const oilPart = (o.parts || []).find((p) => p.name.toLowerCase().includes('aceite'))

      // Priorizar los filtros que el mecánico marcó con clicks en la orden
      const resolvedFilters = (o.replacedFilters && o.replacedFilters.length > 0)
        ? o.replacedFilters
        : (filterParts.length ? filterParts : ['Filtro de aceite', 'Filtro de aire'])

      v.lastService = {
        date: o.date || new Date().toISOString().slice(0, 10),
        km: currentKm,
        oil: o.oilSpec || oilPart?.name || 'Sintético 5W-30',
        filters: resolvedFilters,
        notes: o.notes || o.service,
      }
    }

    if (isTimingBelt) {
      const timingParts = (o.parts || [])
        .filter((p) => p.name.toLowerCase().includes('distribuci') || p.name.toLowerCase().includes('correa') || p.name.toLowerCase().includes('bomba') || p.name.toLowerCase().includes('tensor'))
        .map((p) => p.name)

      v.lastTimingBelt = {
        date: o.date || new Date().toISOString().slice(0, 10),
        km: currentKm,
        parts: timingParts.length ? timingParts : ['Kit de distribución', 'Bomba de agua'],
        notes: o.notes || o.service,
      }
    }
  }

  function progress(o: { tasks: { done: boolean }[] }) {
    return o.tasks.length
      ? Math.round((o.tasks.filter((t) => t.done).length / o.tasks.length) * 100)
      : 0
  }

  return {
    db,
    storageError,
    vehicle,
    client,
    owner,
    vehicleName,
    activeOrders,
    finished,
    lowStock,
    unpaid,
    revenue,
    availableQrs,
    assignedQrs,
    assignQrToVehicle,
    unassignVehicleQr,
    generateQrBatch,
    getVehicleByQr,
    createOrder,
    recordOrderCompletion,
    progress,
  }
}
