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

  const matches = (searchTerm: string, ...values: (string | number | undefined)[]) =>
    values
      .join(' ')
      .toLocaleLowerCase('es')
      .includes(searchTerm.toLocaleLowerCase('es'))

  return {
    money,
    initials,
    statusClass,
    matches,
  }
}
