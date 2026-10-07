import assert from 'node:assert/strict'
import { test } from 'node:test'
import { appointmentDisplayEnd, layoutAppointments, timeMinutes } from '../app/utils/appointmentLayout.ts'
import { validateAppointment } from '../app/utils/appointments.ts'

const appointment = (id, time, endTime) => ({ id, vehicle: id, date: '2026-10-08', time, endTime, status: 'Programado', reason: 'Service' })

test('simultaneous vehicles are allowed and drawn in separate columns', () => {
  const first = appointment(1, '09:00', '11:00')
  const second = appointment(2, '09:00', '10:00')
  assert.equal(validateAppointment([first], second), '')
  const layout = layoutAppointments([second, first])
  assert.deepEqual(layout.map(({ lane, columns }) => [lane, columns]), [[0, 2], [1, 2]])
  assert.equal(timeMinutes(first.endTime) - timeMinutes(first.time), 120)
})

test('nested and chained overlaps remain separate while adjacent turns reuse space', () => {
  const layout = layoutAppointments([
    appointment(1, '09:00', '12:00'),
    appointment(2, '09:30', '10:00'),
    appointment(3, '10:00', '11:00'),
    appointment(4, '10:30', '12:30'),
    appointment(5, '12:30', '13:00'),
  ])
  assert.deepEqual(layout.map(({ lane, columns }) => [lane, columns]), [[0, 3], [1, 3], [1, 3], [2, 3], [0, 1]])
  for (const a of layout) {
    for (const b of layout) {
      if (a === b || a.lane !== b.lane) continue
      const overlap = a.appointment.time < b.appointment.endTime && b.appointment.time < a.appointment.endTime
      assert.equal(overlap, false)
    }
  }
})

test('legacy records have a display slot without inventing a finish, including late bookings', () => {
  const legacy = appointment(1, '23:45', undefined)
  assert.equal(appointmentDisplayEnd(legacy), 1440)
  assert.equal(legacy.endTime, undefined)
  assert.equal(timeMinutes('24:00'), 1440)
  assert.deepEqual(layoutAppointments([]), [])
})
