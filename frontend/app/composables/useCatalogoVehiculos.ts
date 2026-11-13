interface OpcionCatalogo {
  id: number
  nombre: string
  padreId: number | null
}

export function useCatalogoVehiculos() {
  const auth = useAuthApi()
  const marcaId = ref<number | ''>('')
  const modeloId = ref<number | ''>('')
  const versionId = ref<number | ''>('')
  const marcas = ref<OpcionCatalogo[]>([])
  const modelos = ref<OpcionCatalogo[]>([])
  const versiones = ref<OpcionCatalogo[]>([])
  const niveles = ['marcas', 'modelos', 'versiones'] as const
  type Nivel = typeof niveles[number]
  const opciones = { marcas, modelos, versiones }
  const cargando = reactive({ marcas: false, modelos: false, versiones: false })
  const errores = reactive({ marcas: '', modelos: '', versiones: '' })
  const mensajeError = {
    marcas: 'No pudimos cargar las marcas.',
    modelos: 'No pudimos cargar los modelos.',
    versiones: 'No pudimos cargar las versiones.',
  }
  const consultas: Partial<Record<Nivel, AbortController>> = {}
  let inicializando = false
  let apertura = 0

  function limpiar(nivel: Nivel) {
    consultas[nivel]?.abort()
    delete consultas[nivel]
    opciones[nivel].value = []
    cargando[nivel] = false
    errores[nivel] = ''
  }

  async function cargar(nivel: Nivel) {
    const headers = auth.authorization.value
    const padre = nivel === 'modelos' ? marcaId.value : nivel === 'versiones' ? modeloId.value : null
    limpiar(nivel)
    if (!headers || padre === '') return
    const consulta = new AbortController()
    consultas[nivel] = consulta
    cargando[nivel] = true
    const query = nivel === 'modelos' ? { marcaId: padre } : nivel === 'versiones' ? { modeloId: padre } : undefined
    try {
      const respuesta = await $fetch<OpcionCatalogo[]>(`/api/catalogo/${nivel}`, { headers, query, signal: consulta.signal })
      if (consultas[nivel] === consulta) opciones[nivel].value = respuesta
    } catch (err) {
      if (consultas[nivel] === consulta) errores[nivel] = authError(err, mensajeError[nivel])
    } finally {
      if (consultas[nivel] === consulta) {
        delete consultas[nivel]
        cargando[nivel] = false
      }
    }
  }

  watch(marcaId, () => {
    if (inicializando) return
    modeloId.value = ''
    versionId.value = ''
    limpiar('versiones')
    void cargar('modelos')
  }, { flush: 'sync' })

  watch(modeloId, () => {
    if (inicializando) return
    versionId.value = ''
    void cargar('versiones')
  }, { flush: 'sync' })

  function cerrar() {
    apertura++
    inicializando = false
    niveles.forEach(limpiar)
  }

  async function abrir(registro?: { marcaId: number; modeloId: number; versionId: number | null }) {
    cerrar()
    const turno = apertura
    inicializando = true
    marcaId.value = registro?.marcaId ?? ''
    modeloId.value = registro?.modeloId ?? ''
    versionId.value = registro?.versionId ?? ''
    const solicitudes = [cargar('marcas')]
    if (registro) solicitudes.push(cargar('modelos'), cargar('versiones'))
    await Promise.all(solicitudes)
    if (turno === apertura) inicializando = false
  }

  onScopeDispose(cerrar)

  return { marcaId, modeloId, versionId, marcas, modelos, versiones, cargando, errores, cargar, abrir, cerrar }
}
