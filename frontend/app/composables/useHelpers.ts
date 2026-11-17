export const useHelpers = () => {
  const money = (value: number) =>
    new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency: 'ARS',
      maximumFractionDigits: 0,
    }).format(value)

  const initials = (name: string) =>
    (name || '')
      .split(' ')
      .map((n) => n[0])
      .slice(0, 2)
      .join('')

  const statusClass = (status: string) =>
    ({
      'En proceso': 'blue',
      'En espera': 'amber',
      'Pendiente de ingreso': 'amber',
      'No asistió': 'neutral',
      Finalizado: 'green',
      Confirmado: 'green',
      Pendiente: 'amber',
      Programado: 'blue',
      Cancelado: 'neutral',
      'En Taller': 'green',
      'En taller': 'green',
      'Con turno': 'blue',
      Cobrada: 'green',
      Emitida: 'amber',
      Convertido: 'green',
    } as Record<string, string>)[status] || 'neutral'

  function normalizeText(str: string): string {
    return str
      .toLocaleLowerCase('es')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
  }

  function cleanAlphanumeric(str: string): string {
    return normalizeText(str).replace(/[^a-z0-9]/g, '')
  }

  const matches = (searchTerm: string, ...values: (string | number | undefined)[]) => {
    const term = searchTerm.trim()
    if (!term) return true

    const normTerm = normalizeText(term)
    const rawJoined = values.map((v) => normalizeText(String(v ?? ''))).join(' ')

    // 1. Direct normalized match (case & accent insensitive)
    if (rawJoined.includes(normTerm)) return true

    // 2. Alphanumeric match ignoring spaces, dots, hyphens, slashes (e.g. DNI without dots/spaces, plates without spaces)
    const cleanTerm = cleanAlphanumeric(term)
    if (cleanTerm.length >= 2) {
      const anyValueMatches = values.some((val) => {
        if (!val) return false
        return cleanAlphanumeric(String(val)).includes(cleanTerm)
      })
      if (anyValueMatches) return true

      const cleanJoined = values.map((v) => cleanAlphanumeric(String(v ?? ''))).join(' ')
      if (cleanJoined.includes(cleanTerm)) return true
    }

    // 3. Multi-word search (every word matches somewhere)
    const words = normTerm.split(/\s+/).filter(Boolean)
    if (words.length > 1) {
      return words.every((word) => {
        if (rawJoined.includes(word)) return true
        const cleanWord = cleanAlphanumeric(word)
        if (cleanWord.length >= 2) {
          return values.some((val) => val && cleanAlphanumeric(String(val)).includes(cleanWord))
        }
        return false
      })
    }

    return false
  }

  return {
    money,
    initials,
    statusClass,
    matches,
  }
}
