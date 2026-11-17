export const useTheme = () => {
  const isDark = useState<boolean>('isDark', () => false)

  const toggle = () => {
    isDark.value = !isDark.value
  }

  if (import.meta.client) {
    onMounted(() => {
      const saved = localStorage.getItem('octano-theme')
      if (saved) {
        isDark.value = saved === 'dark'
      } else if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
        isDark.value = true
      }
      document.documentElement.classList.toggle('dark', isDark.value)
    })

    watch(isDark, (val) => {
      document.documentElement.classList.toggle('dark', val)
      localStorage.setItem('octano-theme', val ? 'dark' : 'light')
    })
  }

  return {
    isDark,
    toggle,
  }
}
