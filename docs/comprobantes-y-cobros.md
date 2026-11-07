# Comprobantes y cobros internos

El módulo `comprobantes` reúne Controller → Service → Repository con JDBC, sin ORM. V15 crea `comprobante`, `detalle_comprobante` y `cobro`. La edición, emisión y cobranza se resuelven dentro de transacciones. ADMIN y RECEPCION operan exclusivamente en su taller; MECANICO no accede a facturación.

Esta entrega registra comprobantes **internos tipo X**, siempre `esFiscal=false`. No genera CAE ni facturas A/B/C y no realiza llamadas a ARCA o a entidades de pago. El medio de pago registra lo recibido por el taller; no inicia transferencias ni cargos a tarjetas.

## Recorrido que utiliza el frontend

Se puede preparar un comprobante manual desde «Nueva factura», con vehículo, concepto, materiales y mano de obra. El cliente se obtiene del vehículo y ambos deben estar activos. También se puede crear desde una orden FINALIZADO, con o sin presupuesto, copiando sus datos e importes. En ese caso se conserva el cliente que solicitó el trabajo, aunque el vehículo haya cambiado de titular.

Cada orden admite como máximo un comprobante, incluso si todavía es borrador. Las órdenes finalizadas que aún no tienen comprobante se consultan en `ordenes-pendientes`. El filtro conPresupuesto permite alimentar las dos opciones de la sección «Sin emitir» del frontend.

El borrador es editable. Si está vinculado a una orden, el vínculo y el vehículo deben corresponder a esa orden; no se cambia la orden mediante una edición. Un comprobante manual sin orden permite cambiar el vehículo mientras siga en borrador. No se admite total cero: para una orden sin importes, preparar manualmente el comprobante con su ordenId y completar los valores.

Desde un borrador se puede registrar directamente el cobro, igual que con el botón «Registrar cobro» de la maqueta. En una sola transacción se asigna el número interno, se guardan los datos de emisión y se registra el pago completo. También existe emisión interna explícita para dejar un documento numerado pendiente de cobro. Un emitido ya no se edita.

Estados de la respuesta:

- `BORRADOR`: editable, sin número ni cobro.
- `EMITIDO`: documento interno numerado, sin cobro.
- `COBRADO`: documento emitido con un cobro registrado.

La base guarda BORRADOR/EMITIDO; COBRADO se obtiene de la existencia del cobro. No se mantiene además un booleano pagada que pueda contradecir el historial. Emitir nuevamente conserva el número. Cobrar nuevamente devuelve 400 y conserva el cobro original.

La numeración interna comienza en 1 para cada taller y avanza al emitir; los borradores no consumen números. No es un punto de venta ni numeración fiscal. No hay eliminación de comprobantes ni edición/borrado de cobros. Pagos parciales, anticipos, anulaciones, devoluciones y notas de crédito quedan fuera del flujo actual del frontend y de esta entrega.

## Importes e historial

El servidor calcula con BigDecimal y PostgreSQL NUMERIC, sin aceptar totales enviados por el navegador:

1. Subtotal del renglón = cantidad × precio unitario, redondeado HALF_UP a dos decimales.
2. totalRepuestos = suma de esos subtotales.
3. subtotal = totalRepuestos + manoObra.
4. montoIva = subtotal × porcentajeIva / 100, redondeado a dos decimales.
5. total = subtotal + montoIva; debe ser mayor que cero.

Las cantidades admiten tres decimales y los precios dos. En el alta manual, porcentajeIva admite 0 a 100 y por defecto 21, como los presupuestos existentes. Desde una orden se conserva su porcentaje. Esto es el cálculo interno configurado, sin determinar un tipo fiscal A/B/C por la condición del cliente.

Un renglón puede referenciar un repuesto activo del catálogo del taller: copia descripción, código y precio público, salvo que se envíe otro precio negociado. Uno manual necesita descripción y precio. Los renglones de una orden finalizada se copian tal como quedaron, aunque el repuesto se haya archivado o cambiado de precio. Los materiales presupuestados y adicionales ya están en la orden: no se vuelven a sumar también desde el presupuesto.

Crear, emitir o cobrar no descuenta stock. El consumo corresponde a Órdenes y ya quedó registrado allí. El comprobante admite hasta 200 renglones. `items` contiene los materiales/conceptos; la mano de obra está separada en la cabecera. Al armar un PDF se puede agregar su línea descriptiva una sola vez, evitando duplicarla como material.

Cada comprobante guarda copias del emisor, receptor y vehículo. En borrador se actualizan al guardar; al emitir se toman los datos vigentes de esos mismos registros. Después quedan congelados: editar el taller, el cliente, el catálogo o transferir el vehículo no modifica el documento emitido. Se registran usuario y fecha de creación, emisión y última modificación.

Cada cobro guarda su importe completo, fecha/hora del servidor, medio de pago, observaciones y usuario. El importe se lee del comprobante, no se recibe en el cuerpo. Los bloqueos por taller, el bloqueo del comprobante y la unicidad en PostgreSQL evitan duplicar números, comprobantes de una orden y cobros simultáneos.

## Endpoints

Todos requieren `Authorization: Bearer <token>` de ADMIN o RECEPCION.

| Método | Ruta | Operación |
| --- | --- | --- |
| GET | `/api/comprobantes` | Buscar y listar comprobantes. |
| GET | `/api/comprobantes/{id}` | Cabecera, copias históricas, items y cobro si existe. |
| POST | `/api/comprobantes` | Crear borrador manual, con ordenId opcional. Devuelve 201 y Location. |
| PUT | `/api/comprobantes/{id}` | Reemplazar los datos de un borrador. |
| POST | `/api/comprobantes/desde-orden/{ordenId}` | Copiar una orden finalizada a un borrador; sin cuerpo. |
| GET | `/api/comprobantes/ordenes-pendientes` | Órdenes finalizadas sin comprobante. |
| POST | `/api/comprobantes/{id}/emision` | Numerar y emitir un documento interno; sin cuerpo. |
| POST | `/api/comprobantes/{id}/cobro` | Registrar el cobro completo; si es borrador, emitir y cobrar juntos. |
| GET | `/api/comprobantes/{id}/cobro` | Consultar el cobro; 404 si aún no existe. |
| POST | `/api/comprobantes/desde-orden/{ordenId}/cobro` | Cobrar directamente una orden; crea el comprobante si falta o utiliza el existente. |
| GET | `/api/cobros` | Historial paginado de cobros del taller. |

Alta/edición manual:

```json
{
  "ordenId": null,
  "vehiculoId": 123,
  "descripcion": "Reparación",
  "manoObra": 10000,
  "porcentajeIva": 21,
  "items": [
    {"descripcion": "Insumos", "cantidad": 1.5, "precioUnitario": 500}
  ]
}
```

Cobro, tanto de un comprobante como directo desde una orden:

```json
{"medioPago":"EFECTIVO","observaciones":"Pago completo recibido"}
```

| Selector del frontend | medioPago en la API |
| --- | --- |
| Efectivo | EFECTIVO |
| Transferencia bancaria | TRANSFERENCIA |
| Tarjeta de débito | TARJETA_DEBITO |
| Tarjeta de crédito | TARJETA_CREDITO |
| Mercado Pago / QR | MERCADO_PAGO |

`GET /api/comprobantes` admite q por ID/número/concepto/cliente/documento/patente/marca/modelo, estado, clienteId, vehiculoId, ordenId, desde, hasta, pagina desde 0 y tamano de 1 a 100. Las fechas filtran `fecha`, el día de creación del comprobante en Argentina. La fecha/hora de emisión está en emitidoEn. Los resúmenes incluyen clienteNombre, patente, marca y modelo de sus copias guardadas.

`ordenes-pendientes` admite q, conPresupuesto, desde/hasta por fecha de finalización y paginación. Devuelve datos del solicitante, vehículo, presupuestoId y total calculado de la orden. `GET /api/cobros` admite medioPago, desde/hasta por día efectivo de cobro en Argentina y paginación. Todos los listados devuelven contenido, pagina, tamano, totalElementos y totalPaginas.

## Integración y pruebas

El frontend sigue para la etapa de integración acordada. Al conectar sus pantallas, «Para armar» corresponde a BORRADOR y «Cobrada» a COBRADO. Los comprobantes internos siempre son X; el botón de ARCA se conectará cuando se implemente esa integración. El paso EMITIDO de esta API representa emisión **interna**, independiente de la sección de facturas fiscales de la maqueta. El PDF puede construirse con la respuesta y las copias emisor/receptor/vehiculo; no debe resolver esos datos nuevamente desde perfiles actuales.

En Bruno: iniciar sesión, elegir vehículo y repuesto, abrir **15 - Comprobantes → Crear borrador manual** o **Crear desde orden finalizada**; se guarda comprobanteId. Consultar o editar mientras sea borrador. Después usar **16 - Cobros → Registrar cobro**. La respuesta incluye el cobro y su usuario. Para el flujo directo, elegir ordenId finalizada y ejecutar **Cobrar orden directamente**. Repetir el cobro debe devolver 400.

Las pruebas usan octano_test con transacciones reales por petición y limpieza limitada a los talleres temporales. Cubren cálculos, redondeo, copias históricas, transferencias, stock, estados, validaciones, permisos y dos solicitudes simultáneas tanto para cobrar como para numerar.
