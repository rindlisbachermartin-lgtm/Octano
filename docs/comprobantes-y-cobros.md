# Comprobantes y cobros internos

El módulo `comprobantes` reúne Controller → Service → Repository con JDBC, sin ORM. V15 crea `comprobante`, `detalle_comprobante` y `cobro`; V22 permite varios cobros por comprobante y V23 agrega los datos de la factura simulada. La edición, emisión y cobranza se resuelven dentro de transacciones. ADMIN y RECEPCION operan exclusivamente en su taller; MECANICO no accede a facturación.

Los borradores comienzan como comprobantes internos tipo X. Al emitir, se asigna tipo A, B o C según las condiciones de IVA del taller y del receptor, un número interno y el CAE **simulado fijo `74000000000000`**. `esFiscal=false` siempre: el documento no tiene autorización de ARCA ni validez fiscal. El medio de pago registra lo recibido por el taller; no inicia transferencias ni cargos a tarjetas.

## Recorrido que utiliza el frontend

Se puede preparar un comprobante manual desde «Nuevo comprobante», con vehículo, concepto, materiales y mano de obra. El cliente se obtiene del vehículo y ambos deben estar activos. También se puede crear desde una orden FINALIZADO, con o sin presupuesto, copiando sus datos e importes. En ese caso se conserva el cliente que solicitó el trabajo, aunque el vehículo haya cambiado de titular.

Cada orden admite como máximo un comprobante, incluso si todavía es borrador. Las órdenes finalizadas que aún no tienen comprobante se consultan en `ordenes-pendientes`. El filtro conPresupuesto permite alimentar las dos opciones de la sección «Sin emitir» del frontend.

El borrador es editable. Si está vinculado a una orden, el vínculo y el vehículo deben corresponder a esa orden; no se cambia la orden mediante una edición. Un comprobante manual sin orden permite cambiar el vehículo mientras siga en borrador. No se admite total cero: para una orden sin importes, preparar manualmente el comprobante con su ordenId y completar los valores.

Desde un borrador se puede registrar directamente el cobro. En una sola transacción se asigna el número interno, se guardan los datos de emisión y se registra el pago del importe informado; si se omite `importe`, se cobra el saldo pendiente completo. También existe emisión interna explícita para dejar un documento numerado pendiente de cobro. Un emitido ya no se edita.

Se admiten pagos parciales: cada comprobante puede tener varios cobros mientras quede saldo. La API expone `pagado` y `saldo` en cada resumen y en la respuesta completa, junto con la lista `cobros` ordenada cronológicamente. El campo `cobro` se mantiene con el primer pago por compatibilidad.

Estados de la respuesta:

- `BORRADOR`: editable, sin número ni cobro.
- `EMITIDO`: documento interno numerado, sin cobro.
- `PAGADO_PARCIAL`: hay uno o más cobros, pero todavía queda saldo.
- `COBRADO`: la suma de cobros cubre el total del comprobante.

La base guarda BORRADOR/EMITIDO; los estados de cobro se derivan del historial. No se mantiene un booleano pagada que pueda contradecir los cobros. Emitir nuevamente conserva el número. Un nuevo cobro sobre un comprobante totalmente cobrado devuelve 400 y no modifica los cobros existentes.

La numeración interna comienza en 1 para cada taller y avanza al emitir; los borradores no consumen números. El punto de venta elegido sólo se usa para presentar el número simulado y no constituye numeración fiscal. No hay eliminación de comprobantes ni edición/borrado de cobros. Anticipos, anulaciones, devoluciones y notas de crédito quedan fuera del flujo actual; los pagos parciales sí están incluidos.

## Importes e historial

El servidor calcula con BigDecimal y PostgreSQL NUMERIC, sin aceptar totales enviados por el navegador:

1. Subtotal del renglón = cantidad × precio unitario, redondeado HALF_UP a dos decimales.
2. totalRepuestos = suma de esos subtotales.
3. subtotal = totalRepuestos + manoObra.
4. montoIva = subtotal × porcentajeIva / 100, redondeado a dos decimales.
5. total = subtotal + montoIva; debe ser mayor que cero.

Las cantidades admiten tres decimales y los precios dos. En el alta manual, porcentajeIva admite 0 a 100 y por defecto 21, como los presupuestos existentes. Desde una orden se conserva su porcentaje. El tipo simulado se determina al emitir; el porcentaje de IVA se mantiene como importe interno editable y no se recalcula a partir del tipo.

Un renglón puede referenciar un repuesto activo del catálogo del taller: copia descripción, código y precio público, salvo que se envíe otro precio negociado. Uno manual necesita descripción y precio. Los renglones de una orden finalizada se copian tal como quedaron, aunque el repuesto se haya archivado o cambiado de precio. Los materiales presupuestados y adicionales ya están en la orden: no se vuelven a sumar también desde el presupuesto.

Crear, emitir o cobrar no descuenta stock. El consumo corresponde a Órdenes y ya quedó registrado allí. El comprobante admite hasta 200 renglones. `items` contiene los materiales/conceptos; la mano de obra está separada en la cabecera. Al armar un PDF se puede agregar su línea descriptiva una sola vez, evitando duplicarla como material.

Cada comprobante guarda copias del emisor, receptor y vehículo. En borrador se actualizan al guardar; al emitir se toman los datos vigentes de esos mismos registros. Después quedan congelados: editar el taller, el cliente, el catálogo o transferir el vehículo no modifica el documento emitido. Se registran usuario y fecha de creación, emisión y última modificación.

Cada cobro guarda su importe, fecha/hora del servidor, medio de pago, observaciones y usuario. El cuerpo puede enviar `importe` (mayor que cero, máximo dos decimales); si no se envía, el servidor usa el saldo pendiente. El servidor rechaza con 400 un importe mayor al saldo. Los bloqueos por taller y del comprobante, más la validación del saldo dentro de la transacción, evitan duplicar números, comprobantes de una orden y cobros simultáneos que excedan el total. V22 eliminó la unicidad por comprobante de V15 para permitir el historial de pagos.

## Endpoints

Todos requieren `Authorization: Bearer <token>` de ADMIN o RECEPCION.

| Método | Ruta | Operación |
| --- | --- | --- |
| GET | `/api/comprobantes` | Buscar y listar comprobantes. |
| GET | `/api/comprobantes/{id}` | Cabecera, copias históricas, items, `pagado`, `saldo`, `cobros` y `cobro`. |
| POST | `/api/comprobantes` | Crear borrador manual, con ordenId opcional. Devuelve 201 y Location. |
| PUT | `/api/comprobantes/{id}` | Reemplazar los datos de un borrador. |
| POST | `/api/comprobantes/desde-orden/{ordenId}` | Copiar una orden finalizada a un borrador; sin cuerpo. |
| GET | `/api/comprobantes/ordenes-pendientes` | Órdenes finalizadas sin comprobante. |
| POST | `/api/comprobantes/{id}/emision` | Numerar y emitir una factura simulada con tipo y CAE fijo; sin cuerpo. |
| POST | `/api/comprobantes/{id}/cobro` | Registrar un pago parcial o total; si es borrador, emitir y cobrar juntos. |
| GET | `/api/comprobantes/{id}/cobro` | Consultar el primer cobro; 404 si aún no existe. |
| POST | `/api/comprobantes/desde-orden/{ordenId}/cobro` | Cobrar directamente una orden; crea el comprobante si falta o reutiliza el existente con saldo. |
| GET | `/api/cobros` | Historial paginado de cobros del taller. |

Alta/edición manual:

```json
{
  "ordenId": null,
  "vehiculoId": 123,
  "descripcion": "Reparación",
  "manoObra": 10000,
  "porcentajeIva": 21,
  "condicionIvaReceptorId": 2,
  "puntoVenta": 3,
  "items": [
    {"descripcion": "Insumos", "cantidad": 1.5, "precioUnitario": 500}
  ]
}
```

Cobro, tanto de un comprobante como directo desde una orden. `importe` es opcional: con él se registra un pago parcial o el resto exacto; sin él se cobra todo el saldo.

```json
{"medioPago":"EFECTIVO","importe":15000.50,"observaciones":"Primer pago en mostrador"}
```

```json
{"medioPago":"TRANSFERENCIA","observaciones":"Pago del saldo restante"}
```

| Selector del frontend | medioPago en la API |
| --- | --- |
| Efectivo | EFECTIVO |
| Transferencia bancaria | TRANSFERENCIA |
| Tarjeta de débito | TARJETA_DEBITO |
| Tarjeta de crédito | TARJETA_CREDITO |
| Mercado Pago / QR | MERCADO_PAGO |

`GET /api/comprobantes` admite q por ID/número/concepto/cliente/documento/patente/marca/modelo, estado, clienteId, vehiculoId, ordenId, desde, hasta, pagina desde 0 y tamano de 1 a 100. Las fechas filtran `fecha`, el día de creación del borrador o de emisión del comprobante en Argentina. La fecha/hora de emisión está en emitidoEn. Los resúmenes incluyen clienteNombre, patente, marca y modelo de sus copias guardadas, además de `pagado` y `saldo`. El filtro `estado` acepta también `PAGADO_PARCIAL`.

`ordenes-pendientes` admite q, conPresupuesto, desde/hasta por fecha de finalización y paginación. Devuelve datos del solicitante, vehículo, presupuestoId y total calculado de la orden. `GET /api/cobros` admite medioPago, desde/hasta por día efectivo de cobro en Argentina y paginación. Todos los listados devuelven contenido, pagina, tamano, totalElementos y totalPaginas.

## Integración y pruebas

La pantalla `/facturacion` consume esta API con el token de sesión. Muestra órdenes finalizadas sin comprobante, comprobantes pendientes y cobrados. Desde una orden se genera el borrador con los datos guardados y luego se elige la condición de IVA del receptor y el punto de venta. El formulario de cobro permite pagar todo, la mitad o un importe libre. El visor y el PDF recuperan el diseño anterior de factura, muestran el CAE simulado y la leyenda «sin validez fiscal», sin QR fiscal. Se construyen con las copias históricas de la respuesta.

Los documentos emitidos son facturas simuladas A/B/C con `esFiscal=false`. El proyecto no implementará ARCA ni solicitará un CAE real. Las rutas antiguas de configuración fiscal llevan a `/facturacion`.

En Bruno: iniciar sesión, elegir vehículo y repuesto, abrir **15 - Comprobantes → Crear borrador manual** o **Crear desde orden finalizada**; se guarda comprobanteId. Consultar o editar mientras sea borrador. Después usar **16 - Cobros → Registrar cobro** con un `importe` menor al total: la respuesta debe indicar `PAGADO_PARCIAL`, el `pagado` y el `saldo`. Repetir el cobro con el saldo restante debe llegar a `COBRADO` con `saldo=0`; un tercer cobro debe devolver 400. Sin `importe`, la petición cobra el saldo completo. Para el flujo directo, elegir ordenId finalizada y ejecutar **Cobrar orden directamente**.

Las pruebas usan octano_test con transacciones reales por petición y limpieza limitada a los talleres temporales. Cubren cálculos, redondeo, copias históricas, transferencias, stock, estados, validaciones, permisos, pagos parciales y saldos, y dos solicitudes simultáneas tanto para cobrar como para numerar.

## Revisión contra el PDF de referencia (6 de noviembre de 2026)

La revisión compara los campos impresos de la factura A 0003-00000133 con la persistencia real de V15. No convierte el módulo interno en facturación fiscal ni incorpora datos personales de la muestra al código o a la base.

| Datos del documento | Situación actual |
| --- | --- |
| Emisor: nombre, CUIT, domicilio, condición IVA y teléfono | Copia propia en comprobante; no se lee del perfil actual después de emitir. |
| Receptor: nombre, documento, domicilio, condición IVA y teléfono | Copia propia en comprobante; no cambia al editar el cliente. |
| Código, descripción, cantidad, precio unitario e importe | Detalle guardado con código OEM opcional y renglón. Un código de presentación como 0001 se puede obtener del renglón sin guardar otra columna. |
| Subtotal, porcentaje/monto IVA y total | Guardados y calculados en servidor. La muestra 61983.47 + 13016.53 = 75000.00 es consistente con redondeo a dos decimales. |
| Vehículo, marca, modelo y patente | Copia histórica; los IDs enlazan la operación con el sistema. Versión y año también se conservan aunque este diseño de PDF no los imprima. |
| Presupuesto de origen | Se puede seguir comprobante.orden_id → orden_trabajo.presupuesto_id. El detalle de la API aún no expone presupuestoId directamente. |
| Fecha del comprobante | Se guarda `fecha`, el día de emisión en Argentina; `creado_en` y `emitido_en` conservan la auditoría. El PDF muestra `fecha`. |
| Ingresos Brutos e inicio de actividades | Existen en taller, pero faltan sus copias históricas en comprobante. Consultar el perfil actual no permite reconstruir fielmente una emisión anterior. |
| Localidad y provincia, de emisor y receptor | No están modeladas en Taller/Cliente del backend ni en las copias del comprobante. No completar con datos ficticios. |
| Condición de venta Contado | Falta como dato comercial. No es equivalente a medioPago (efectivo, transferencia, tarjeta, etc.). |
| Descuento/recargo | No implementado; el frontend imprime cero fijo. No hace falta persistir una constante. Si se habilita un importe variable, se debe guardar y ajustar el cálculo. |
| Tipo A/B/C, punto de venta y CAE | Se guardan para la simulación. El número es interno y el CAE fijo no tiene autorización fiscal. Vencimiento y QR fiscal quedan fuera del alcance. |
| ORIGINAL, FACTURA, rótulos, leyendas fijas y marca del software | Presentación; no se necesitan columnas para cada texto ni guardar una imagen QR que se puede generar de datos autorizados. |

Los campos internos de aislamiento por taller, relaciones, estado y auditoría no sobran por no aparecer impresos. Los importes derivados y las copias de emisor/receptor conservan la integridad e historial de la operación. No se eliminaron columnas a partir de una única plantilla.

`usuarioModificacionId` y `fechaModif` se guardan al crear, editar, emitir y cobrar. El usuario proviene de la sesión autenticada; no se confía en un ID enviado desde el frontend. `creadoPor`, `emitidoPor` y `cobro.usuarioId` conservan la autoría de cada etapa. Una lectura o una edición rechazada no cambia la auditoría. La prueba `registraAlUltimoUsuarioQueModificoElComprobante` comprueba estas reglas entre administración y recepción, incluyendo el valor persistido en PostgreSQL.

Los borradores se muestran y exportan con título y marca de agua BORRADOR en todas las páginas del PDF. Si el taller no tiene condición de IVA, el formulario permite seleccionarla explícitamente para el comprobante, sin modificar el perfil del taller. La emisión, incluida la realizada al cobrar, actualiza `fecha` al día de emisión en Argentina.
