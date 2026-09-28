let toastTimer: ReturnType<typeof setTimeout> | undefined

export const useWorkshopToast = () => {
  const toast = useState('toast', () => '')

  function notify(message: string) {
    toast.value = message
    if (import.meta.client) {
      clearTimeout(toastTimer)
      toastTimer = setTimeout(() => (toast.value = ''), 4500)
    }
  }

  function dismissToast() {
    toast.value = ''
    if (import.meta.client) {
      clearTimeout(toastTimer)
    }
  }

  return {
    toast,
    notify,
    dismissToast,
  }
}
