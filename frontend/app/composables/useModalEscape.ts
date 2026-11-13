export const useModalEscape = (
  isOpen: Ref<boolean> | (() => boolean),
  onClose: () => void
) => {
  if (import.meta.client) {
    const handler = (e: KeyboardEvent) => {
      // Let an open menu handle Escape before closing its parent form.
      if (e.defaultPrevented || (e.target instanceof Element && e.target.closest('[role="listbox"], [role="option"], [role="menu"], [role="menuitem"]'))) return
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
