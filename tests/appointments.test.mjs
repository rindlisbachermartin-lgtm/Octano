import assert from 'node:assert/strict'
import { test } from 'node:test'
import { validateAppointment, appointmentsOverlap, appointmentDuration } from '../app/utils/appointments.ts'

const existing = { id: 1, vehicle: 2, date: '2026-10-06', time: '09:00', endTime: '10:30', reason: 'Frenos', status: 'Programado' }
const candidate = { vehicle: 2, date: '2026-10-06', time: '14:00', endTime: '16:00' }

test('blocks another appointment for the same vehicle and day, even at a different time', () => {
  assert.match(validateAppointment([existing], candidate), /ya tiene un turno/)
  assert.match(validateAppointment([{ ...existing, status: 'En Taller' }], candidate), /ya tiene un turno/)
  assert.equal(validateAppointment([existing], { ...candidate, vehicle: 3 }), '')
  assert.equal(validateAppointment([existing], { ...candidate, date: '2026-10-07' }), '')
})

test('editing excludes the original record but still blocks another vehicle booking', () => {
  assert.equal(validateAppointment([existing], candidate, existing.id), '')
  const other = { ...existing, id: 2, vehicle: 3 }
  assert.match(validateAppointment([existing, other], { ...candidate, vehicle: 3 }, existing.id), /ya tiene un turno/)
})

test('cancelled appointments allow rebooking', () => {
  assert.equal(validateAppointment([{ ...existing, status: 'Cancelado' }], candidate), '')
  assert.equal(validateAppointment([{ ...existing, status: 'No asistió' }], candidate), '')
})

test('requires valid start and finish with positive duration', () => {
  for (const endTime of ['', undefined, '14:00', '13:59', '25:00']) {
    assert.notEqual(validateAppointment([], { ...candidate, endTime }), '')
  }
  assert.notEqual(validateAppointment([], { ...candidate, date: '' }), '')
  assert.equal(appointmentDuration(candidate.time, candidate.endTime), '120 min estimados')
  assert.equal(appointmentDuration('09:00'), 'Fin sin estimar')
})

test('detects interval overlap, allows adjacent appointments and supports legacy records', () => {
  assert.equal(appointmentsOverlap(existing, { time: '10:00', endTime: '11:00' }), true)
  assert.equal(appointmentsOverlap(existing, { time: '08:00', endTime: '11:00' }), true)
  assert.equal(appointmentsOverlap(existing, { time: '10:30', endTime: '11:00' }), false)
  assert.equal(appointmentsOverlap(existing, { time: '08:00', endTime: '09:00' }), false)
  assert.equal(appointmentsOverlap({ time: '09:00' }, existing), true)
})
