import type { Appointment } from '../types'

export const isScheduledAppointment = (appointment: Appointment) => ['Programado', 'Confirmado'].includes(appointment.status)
