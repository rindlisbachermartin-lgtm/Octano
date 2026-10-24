import type { Appointment } from '../types/index'

export function timeMinutes(time: string): number {
  const [hours, minutes] = time.split(':').map(Number)
  return hours! * 60 + minutes!
}

// Legacy appointments without a finish use a dashed 30-minute display slot.
export function appointmentDisplayEnd(appointment: Appointment): number {
  const start = timeMinutes(appointment.time)
  return appointment.endTime ? timeMinutes(appointment.endTime) : Math.min(start + 30, 1440)
}

export function layoutAppointments(appointments: Appointment[]) {
  const sorted = [...appointments].sort((a, b) =>
    a.time.localeCompare(b.time) || appointmentDisplayEnd(b) - appointmentDisplayEnd(a) || a.id - b.id
  )
  const result: { appointment: Appointment; lane: number; columns: number }[] = []
  let group: typeof result = []
  let laneEnds: number[] = []
  let groupEnd = -1

  function finishGroup() {
    group.forEach((item) => { item.columns = laneEnds.length })
    result.push(...group)
    group = []
    laneEnds = []
  }

  for (const appointment of sorted) {
    const start = timeMinutes(appointment.time)
    const end = appointmentDisplayEnd(appointment)
    if (start >= groupEnd) {
      finishGroup()
      groupEnd = end
    }
    let lane = laneEnds.findIndex((previousEnd) => previousEnd <= start)
    if (lane === -1) lane = laneEnds.length
    laneEnds[lane] = end
    groupEnd = Math.max(groupEnd, end)
    group.push({ appointment, lane, columns: 1 })
  }
  finishGroup()
  return result
}
