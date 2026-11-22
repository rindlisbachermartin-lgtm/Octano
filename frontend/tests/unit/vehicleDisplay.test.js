import { vehicleLabel, vehicleVersion, vehicleFuel } from '../../app/utils/vehicleDisplay'
import { adaptarTurno } from '../../app/utils/turnoApi'

test('separa versión y combustible sin duplicarlos y capitaliza los combustibles', () => {
  expect(vehicleLabel('CITROEN', 'C3', '1.4 HDi DIESEL')).toBe('CITROEN C3 1.4 HDi')
  expect(vehicleFuel('1.4 HDi DIESEL')).toBe('Diesel')
  expect(vehicleFuel('1.6 NAFTA / GNC')).toBe('Nafta / GNC')
  expect(vehicleVersion('1.6 NAFTA / GNC')).toBe('1.6')
  expect(vehicleFuel('1.4 HDi Diésel')).toBe('Diésel')
  expect(vehicleLabel('Toyota', 'Hilux', null)).toBe('Toyota Hilux')
  expect(vehicleFuel(null)).toBe('')
  expect(vehicleVersion('1.6 16V')).toBe('1.6 16V')
})

test('los turnos conservan marca, modelo y versión en el nombre visible', () => {
  const turno = adaptarTurno({ id: 1, vehiculoId: 2, marca: 'Citroen', modelo: 'C3', version: '1.4 HDi DIESEL', horaInicio: '10:00', horaFin: null, estado: 'PROGRAMADO' })
  expect(turno.registro.vehiculo).toBe('Citroen C3 1.4 HDi')
})
