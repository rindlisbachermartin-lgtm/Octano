import { mount } from '@vue/test-utils'
import Detalle from '../../app/components/ordenes/DetalleRepuestos.vue'
import Editor from '../../app/components/ordenes/EditorRepuestos.vue'
import { assignOrderParts, budgetOrderParts, orderPartIds, orderDisplayParts, orderBillingParts, orderBillingTotal, setOrderPartQuantity } from '../../app/utils/orderWorkflow'
import { prepararBaseDePrueba, buscar } from './helpers'

describe('Repuestos de una orden', () => {
  let db, orden, repuesto

  beforeEach(() => {
    db = prepararBaseDePrueba().db
    db.value.parts.push({ id: 2, name: 'Filtro', brand: 'Prueba', oem: 'F-2', price: 120, stock: 5,
      compatible: [{ brand: 'Toyota', model: 'Hilux' }] })
    db.value.orders.push({ id: 10, vehicle: 10, status: 'En proceso', parts: [] })
    orden = db.value.orders[0]
    repuesto = db.value.parts[0]
  })

  test('no agrega el mismo repuesto en dos renglones', () => {
    // Repetirlo en un mismo envío no debe guardar nada ni consumir stock.
    expect(assignOrderParts(db.value, orden, [{ partId: 2, quantity: 1 }, { partId: 2, quantity: 2 }])).toContain('ya está')
    expect(orden.parts).toHaveLength(0)
    expect(repuesto.stock).toBe(5)

    // Tampoco se puede repetir después de haberlo guardado.
    expect(assignOrderParts(db.value, orden, [{ partId: 2, quantity: 1 }])).toBe('')
    expect(assignOrderParts(db.value, orden, [{ partId: 2, quantity: 1 }])).toContain('Modificá la cantidad')
    expect(orden.parts).toHaveLength(1)
    expect(repuesto.stock).toBe(4)
  })

  test('cambiar cantidad ajusta el stock y conserva el precio original', () => {
    // Pasamos de dos unidades a tres y después a una; solo cambia la diferencia.
    assignOrderParts(db.value, orden, [{ partId: 2, quantity: 2 }])
    const renglon = orden.parts[0]
    repuesto.price = 999
    expect(setOrderPartQuantity(db.value, orden, renglon, 3)).toBe('')
    expect(repuesto.stock).toBe(2)
    expect(renglon.price).toBe(360)
    expect(renglon.unitPrice).toBe(120)
    expect(setOrderPartQuantity(db.value, orden, renglon, 1)).toBe('')
    expect(repuesto.stock).toBe(4)
    expect(renglon.price).toBe(120)
  })

  test('un aumento sin stock o una cantidad inválida no cambia nada', () => {
    // Un error conserva tanto el renglón como el inventario.
    assignOrderParts(db.value, orden, [{ partId: 2, quantity: 2 }])
    const renglon = orden.parts[0]
    for (const cantidad of [6, 0, -1, 1.5, NaN]) {
      expect(setOrderPartQuantity(db.value, orden, renglon, cantidad)).not.toBe('')
      expect(renglon.quantity).toBe(2)
      expect(repuesto.stock).toBe(3)
    }
    expect(setOrderPartQuantity(db.value, orden, renglon, 1, false)).toContain('administración')
    orden.status = 'Finalizado'
    expect(setOrderPartQuantity(db.value, orden, renglon, 3)).not.toBe('')
  })

  test('la cantidad presupuestada se modifica en la OT sin cambiar el presupuesto', () => {
    // El presupuesto original queda igual; la facturación utiliza la cantidad actual de la orden.
    const presupuesto = { id: 1, orderId: 10, labor: 0, materials: 240, subtotal: 240, tax: 50.4, total: 290.4,
      items: [{ partId: 2, name: 'Filtro', quantity: 2, unitPrice: 120, total: 240 }] }
    db.value.quotes.push(presupuesto)
    orden.budgetId = 1
    orden.parts = budgetOrderParts(presupuesto)
    expect(assignOrderParts(db.value, orden, [{ partId: 2, quantity: 1 }])).toContain('ya está')
    expect(setOrderPartQuantity(db.value, orden, orden.parts[0], 3)).toBe('')
    expect(orderBillingParts(orden, presupuesto)).toHaveLength(1)
    expect(orderBillingParts(orden, presupuesto)[0].quantity).toBe(3)
    expect(orderBillingTotal(orden, presupuesto)).toBeCloseTo(435.6)
    expect(presupuesto.items[0].quantity).toBe(2)
    expect(orden.parts).toHaveLength(1)
  })

  test('el detalle permite escribir la cantidad del renglón existente', async () => {
    // El campo de cantidad llama al cambio de la misma fila, sin agregar otra.
    assignOrderParts(db.value, orden, [{ partId: 2, quantity: 1 }])
    const detalle = mount(Detalle, { props: {
      parts: orderDisplayParts(orden, null, db.value.parts), quantityEditable: true,
      onQuantity: (parte, cantidad) => setOrderPartQuantity(db.value, orden, parte, cantidad),
    } })
    await detalle.get('[aria-label="Cantidad de Filtro"]').setValue(3)
    expect(orden.parts).toHaveLength(1)
    expect(orden.parts[0].quantity).toBe(3)
    expect(repuesto.stock).toBe(2)
  })

  test('el selector no ofrece repuestos que ya están asignados', async () => {
    // Para agregar más unidades se usa el detalle, no una nueva fila del selector.
    assignOrderParts(db.value, orden, [{ partId: 2, quantity: 1 }])
    const editor = mount(Editor, { props: { vehicleId: 10, modelValue: [{ partId: -1, name: '', quantity: 1 }],
      assignedPartIds: orderPartIds(orden) } })
    await buscar(editor, '[role="combobox"]', 'Filtro')
    expect(editor.findAll('.part-dropdown-item')).toHaveLength(0)
    expect(editor.text()).toContain('cambiar su cantidad')
  })
})
