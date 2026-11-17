export function useListView<T extends string>(key: string, initial: T, choices: readonly T[]) {
  const view = useState<T>(`list-view:${key}`, () => initial)
  const mobile = ref(false)
  let mediaQuery: MediaQueryList | undefined
  const updateMobile = () => { mobile.value = !!mediaQuery?.matches }
  onMounted(() => {
    if (choices.some(choice => choice === 'cards')) {
      mediaQuery = window.matchMedia('(max-width: 760px)')
      updateMobile()
      mediaQuery.addEventListener('change', updateMobile)
    }
    try {
      const saved = localStorage.getItem(`octano:view:${key}`)
      if (saved && choices.includes(saved as T)) view.value = saved as T
    } catch { /* The in-memory preference remains available. */ }
  })
  onBeforeUnmount(() => mediaQuery?.removeEventListener('change', updateMobile))
  watch(view, (value) => {
    if (import.meta.client) {
      try { localStorage.setItem(`octano:view:${key}`, value) } catch { /* Storage may be unavailable. */ }
    }
  })
  return computed({
    get: () => mobile.value ? 'cards' as T : view.value,
    set: (value: T) => { if (!mobile.value) view.value = value },
  })
}
