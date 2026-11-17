export function useWorkshopDay() {
  const now = ref(new Date())
  let timer: ReturnType<typeof setInterval> | undefined
  onMounted(() => { timer = setInterval(() => { now.value = new Date() }, 60000) })
  onBeforeUnmount(() => { if (timer) clearInterval(timer) })
  const today = computed(() => {
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone: 'America/Argentina/Buenos_Aires', year: 'numeric', month: '2-digit', day: '2-digit',
    }).formatToParts(now.value)
    const part = (type: string) => parts.find((p) => p.type === type)?.value
    return `${part('year')}-${part('month')}-${part('day')}`
  })
  const label = computed(() => new Intl.DateTimeFormat('es-AR', {
    timeZone: 'America/Argentina/Buenos_Aires', weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
  }).format(now.value))
  return { today, label }
}
