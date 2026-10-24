import type { Appointment } from '../types/index'

export function validateAppointment(appointments: Appointment[], candidate: Pick<Appointment, 'vehicle' | 'date' | 'time' | 'endTime'>, excludeId?: number): string {
  if (!candidate.vehicle) return 'Seleccioná un vehículo.'
  if (!/^\d{4}-\d{2}-\d{2}$/.test(candidate.date)) return 'Seleccioná una fecha válida.'
  const validTime = /^(?:[01]\d|2[0-3]):[0-5]\d$/
  if (!validTime.test(candidate.time) || !validTime.test(candidate.endTime || '')) {
    return 'Ingresá la hora de inicio y de fin estimadas.'
  }
  if (candidate.endTime! <= candidate.time) return 'La hora de fin debe ser posterior a la hora de inicio.'
  if (appointments.some((a) => a.id !== excludeId && a.vehicle === candidate.vehicle && a.date === candidate.date && !['Cancelado', 'No asistió'].includes(a.status))) {
    return 'Este vehículo ya tiene un turno activo para ese día. Editá el turno existente o elegí otra fecha.'
  }
  return ''
}

export function appointmentsOverlap(a: Pick<Appointment, 'time' | 'endTime'>, b: Pick<Appointment, 'time' | 'endTime'>): boolean {
  if (!a.endTime || !b.endTime) return a.time === b.time
  return a.time < b.endTime && b.time < a.endTime
}

export function appointmentDuration(time: string, endTime?: string): string {
  if (!endTime) return 'Fin sin estimar'
  const minutes = (value: string) => Number(value.slice(0, 2)) * 60 + Number(value.slice(3))
  const duration = minutes(endTime) - minutes(time)
  return `${duration} min estimados`
}
