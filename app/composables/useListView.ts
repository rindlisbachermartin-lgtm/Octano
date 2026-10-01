export function useListView<T extends string>(key: string, initial: T, choices: readonly T[]) {
  const view = useState<T>(`list-view:${key}`, () => initial)
  onMounted(() => {
    try {
      const saved = localStorage.getItem(`octano:view:${key}`)
      if (saved && choices.includes(saved as T)) view.value = saved as T
    } catch { /* The in-memory preference remains available. */ }
  })
  watch(view, (value) => {
    if (import.meta.client) {
      try { localStorage.setItem(`octano:view:${key}`, value) } catch { /* Storage may be unavailable. */ }
    }
  })
  return view
}
