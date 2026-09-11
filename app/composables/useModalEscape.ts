export const useModalEscape = (
  isOpen: Ref<boolean> | (() => boolean),
  onClose: () => void
) => {
  if (import.meta.client) {
    const handler = (e: KeyboardEvent) => {
      const open = typeof isOpen === 'function' ? isOpen() : isOpen.value
      if (e.key === 'Escape' && open) {
        onClose()
      }
    }

    onMounted(() => {
      window.addEventListener('keydown', handler)
    })

    onBeforeUnmount(() => {
      window.removeEventListener('keydown', handler)
    })
  }
}
