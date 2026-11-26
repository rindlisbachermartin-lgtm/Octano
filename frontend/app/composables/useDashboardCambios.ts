// Señal compartida: los módulos avisan acá cuando una escritura confirmada
// cambia los datos que muestra el dashboard.
const revision = ref(0)

export function notificarCambioDashboard() {
  revision.value++
}

export function useCambioDashboard() {
  return revision
}
