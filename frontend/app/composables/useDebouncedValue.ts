import { onScopeDispose, shallowRef, toValue, watch, type MaybeRefOrGetter } from 'vue'

export function useDebouncedValue<T>(source: MaybeRefOrGetter<T>, delay = 300) {
  const debounced = shallowRef<T>(toValue(source))
  let timer: ReturnType<typeof setTimeout> | undefined

  const cancel = () => {
    clearTimeout(timer)
    timer = undefined
  }

  watch(() => toValue(source), (value) => {
    cancel()
    // Clearing a search immediately restores the unfiltered list.
    if (typeof value === 'string' && !value.trim()) {
      debounced.value = value
      return
    }
    timer = setTimeout(() => {
      debounced.value = value
      timer = undefined
    }, delay)
  }, { flush: 'sync' })

  onScopeDispose(cancel)
  return debounced
}
