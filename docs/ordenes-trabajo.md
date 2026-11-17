# Órdenes de trabajo

Implementación JDBC, sin ORM, organizada en `ordenes/controller`, `service`, `repository`, `dto` y `entity`. Flyway crea las tablas con V12 y V13. Las operaciones se limitan al taller de la sesión y registran usuario y fecha de modificación; los cambios de estado conservan también su último usuario y fecha.

## Recorrido

Un turno genera automáticamente una orden `PENDIENTE_INGRESO`, sin mecánico obligatorio todavía. La respuesta del turno incluye `ordenId`. Reprogramar actualiza los datos vinculados mientras siga pendiente; cancelar o marcar NO_ASISTIO cancela su orden pendiente. Las reservas anteriores a este módulo también reciben su orden mediante la migración.

Registrar ingreso mediante Turnos o mediante la orden cambia el turno a `EN_TALLER` y la orden a `EN_ESPERA`. El ingreso anticipado requiere igualmente una acción explícita; no se ejecuta por el paso del tiempo. La orden admite asignación de un mecánico activo antes del inicio.

Una orden sin turno se crea directamente `EN_ESPERA`, para un vehículo y su titular activos. Requiere mecánico, descripción, tiposTrabajo, manoObra y porcentajeIva. No requiere presupuesto. El cliente se obtiene del vehículo; no se acepta un cliente arbitrario en el cuerpo.

`EN_ESPERA → EN_PROCESO → FINALIZADO`. También se puede cancelar una orden pendiente, en espera o en proceso, indicando motivo. No se reabre una orden finalizada ni cancelada. El inicio repetido no vuelve a consumir materiales. La planificación solo se modifica antes de iniciar; vehículo y presupuesto quedan vinculados desde la emisión. Para corregir esos vínculos, cancelar y crear otra orden. En órdenes provenientes de un turno, el vehículo y presupuesto se corrigen desde la reserva antes del ingreso.

Al finalizar, se exige kilometraje entero entre 0 y 9999999. Si incluye SERVICE, también se exige aceite; se pueden informar filtros `ACEITE`, `AIRE`, `HABITACULO` y `COMBUSTIBLE`. Para otros trabajos se envían aceite vacío y filtros vacíos. El cierre guarda el mantenimiento en la propia orden y actualiza el kilometraje y la auditoría del vehículo en la misma transacción. El turno vinculado pasa a `FINALIZADO`, liberando su disponibilidad; ese estado no se establece directamente desde la API de reservas.

El historial de mantenimiento se obtiene filtrando órdenes finalizadas por vehículo. No se crea una ficha técnica duplicada ni se emiten comprobantes al cerrar: facturación es el siguiente módulo. Las tareas determinan el progreso informativo; no es obligatorio marcar todas para cerrar, igual que en la maqueta.

## Repuestos, presupuestos y stock

La orden copia los renglones del presupuesto, conservando código, descripción, cantidad y precio. Conserva la mano de obra y el porcentaje de IVA presupuestados. El catálogo puede cambiar sin modificar esos precios históricos. Un presupuesto admite una sola orden no cancelada; no se puede editar mientras esté vinculado. Rechazar o anular cancela las órdenes pendientes de ingreso; se rechaza si la orden ya ingresó o finalizó.

Se admiten repuestos de inventario y renglones manuales. Los manuales necesitan descripción y precio, y no afectan stock. Cantidades admiten tres decimales; precios dos. Los totales se calculan en el servidor con BigDecimal y HALF_UP, con el mismo criterio de Presupuestos.

Cada repuesto de inventario tiene un solo renglón en la OT, igual que en Presupuestos. Un envío que lo repite o intenta agregarlo otra vez se rechaza sin guardar sus renglones. V16 agrega además un índice único en PostgreSQL. Antes de aplicar esta regla se comprobó que la base local no tenía duplicados; no se fusionan importes ni se modifica el historial automáticamente. Los conceptos manuales sin repuestoId no identifican un mismo artículo de inventario.

Para agregar o quitar unidades de un repuesto existente, se modifica su cantidad mediante PATCH /{id}/repuestos/{itemId}. El precio unitario guardado se conserva y se recalculan subtotales y totales. Antes del inicio solo cambia la planificación. Si ya se consumió, aumentar registra una SALIDA por la diferencia y reducir una ENTRADA; si no alcanza el stock, se revierte toda la operación. Repetir la misma cantidad no genera movimientos. ADMIN/RECEPCION pueden aumentar o reducir; MECANICO solo puede aumentar en una orden propia, conservando la restricción de devoluciones. Finalizados y cancelados no se modifican.

La cantidad de un renglón presupuestado se puede ajustar en la OT sin editar el presupuesto original. El comprobante copia la cantidad real de la orden una sola vez. Si se cambia el presupuesto de una reserva y sus materiales repetirían un repuesto adicional ya cargado, se rechaza ese cambio hasta corregir el renglón existente.

Planificar materiales **no reserva ni descuenta stock**. Al iniciar, se comprueban y descuentan todos juntos. Si falta uno, no se inicia la orden ni se descuenta ninguno. Agregar materiales durante el trabajo descuenta en esa operación. Cada salida registra usuario, fecha y una referencia a la orden y al renglón en el motivo del movimiento. Los bloqueos de PostgreSQL evitan que dos órdenes consuman las mismas unidades.

Si el repuesto tiene compatibilidades cargadas, debe coincidir modelo y, cuando corresponda, versión del vehículo. Sin compatibilidades cargadas se considera de uso general. Los repuestos inactivos no se pueden agregar ni consumir.

Quitar un renglón consumido desde ADMIN o RECEPCION significa devolver ese material: genera una ENTRADA, incluso si el repuesto fue archivado después. La devolución y el borrado del renglón se confirman juntos; repetir el borrado devuelve 404 sin reponer de nuevo. Cancelar una orden iniciada **conserva el consumo**: los materiales utilizados no vuelven automáticamente a estar disponibles. Devolver explícitamente los materiales recuperados antes de cancelar. Los movimientos anteriores permanecen en el historial.

## Permisos

ADMIN y RECEPCION gestionan las órdenes del taller. MECANICO solo puede listar y consultar sus órdenes asignadas, iniciar el trabajo, registrar diagnóstico y observaciones, completar tareas, cargar/quitar fotos y finalizar. También puede agregar repuestos del catálogo a su precio vigente; no puede crear conceptos manuales, cambiar precios, reasignarse órdenes, eliminar materiales ni cancelar. Alterar mecanicoId en el filtro no permite ampliar su alcance. Si una sesión MECANICO no tiene mecanicoId, todas las operaciones de OT se rechazan con 403 antes de consultar órdenes; nunca se interpreta ese nulo como «sin filtro». La base también exige el vínculo al guardar usuarios MECANICO.

Todos los endpoints requieren Bearer JWT. Un recurso de otro taller o de otro mecánico no se revela: devuelve 404.

## Endpoints

Base: `/api/ordenes-trabajo`.

| Método | Ruta relativa | Uso |
| --- | --- | --- |
| GET | `/` | Lista paginada, con nombres de cliente, vehículo y mecánico. |
| GET | `/{id}` | Cabecera en `orden`, tareas, items, fotos, progreso y totales. |
| POST | `/` | Alta sin turno; presupuesto opcional. Devuelve 201 y Location. |
| PUT | `/{id}` | Editar planificación antes del inicio. |
| POST | `/{id}/ingreso` | Ingresar una orden proveniente de turno. Sin cuerpo. |
| POST | `/{id}/inicio` | Iniciar y consumir materiales. Sin cuerpo. |
| PUT | `/{id}/trabajo` | Guardar diagnóstico, kilometraje y observaciones del mecánico. |
| POST | `/{id}/finalizacion` | Cerrar y actualizar el vehículo. |
| POST | `/{id}/cancelacion` | Cancelar con `motivo`. |
| POST | `/{id}/tareas` | Agregar tarea con `descripcion`. |
| PATCH | `/{id}/tareas/{tareaId}` | Marcar `completada: true` o `false`. |
| DELETE | `/{id}/tareas/{tareaId}` | Quitar tarea. |
| POST | `/{id}/repuestos` | Agregar una lista `items`. |
| PATCH | `/{id}/repuestos/{itemId}` | Cambiar `cantidad`, conservando el precio y ajustando el stock por la diferencia. |
| DELETE | `/{id}/repuestos/{itemId}` | Quitar el renglón y devolver stock consumido. |
| POST | `/{id}/fotos` | Adjuntar imagen con `sector` y `datos` como data URL base64. |
| GET | `/{id}/fotos/{fotoId}` | Descargar la imagen con autenticación. |
| DELETE | `/{id}/fotos/{fotoId}` | Quitar foto mientras la orden esté en espera o en proceso. |

La lista acepta `q` (ID, trabajo, cliente, patente, marca, modelo o mecánico), `estado`, `clienteId`, `vehiculoId`, `mecanicoId`, `desde`, `hasta`, `pagina` desde 0 y `tamano` entre 1 y 100. La búsqueda sigue las reglas de `useHelpers.matches`: ignora mayúsculas, acentos y separadores, y permite palabras en distinto orden; por ejemplo, jose encuentra José, ABC123 encuentra ABC-123 y cactus jose puede combinar modelo y cliente. El listado y su conteo usan los mismos filtros. Las fechas filtran la **creación de la orden** en Argentina. Sin estado incluye el historial completo; ordena por ID descendente. Para mantenimiento: `?vehiculoId=123&estado=FINALIZADO`; consultar el detalle para leer aceite y filtros. La fecha de cierre está en `finFecha`.

Hasta 200 tareas y 200 renglones por orden. Las fotos se guardan en PostgreSQL: máximo 8, JPEG/PNG, hasta 1 MiB y 16 megapíxeles cada una. Se verifica el contenido real, no solo la extensión o el MIME declarado. El detalle devuelve metadatos; los bytes se consultan aparte con JWT y sin caché. Al integrar el frontend, usar fetch autenticado y un blob para mostrar la imagen. Almacenamiento externo se evaluará si el volumen lo requiere.

## Probar en Bruno

Iniciar sesión y seleccionar un vehículo y mecánico activos. Abrir **13 - Ordenes de trabajo → Crear orden sin turno**. Guarda `ordenId`. Después:

1. Agregar tarea y/o repuestos. Tener stock cargado con la colección Stock.
2. Iniciar trabajo; consultar el saldo y sus movimientos.
3. Consultar orden, que guarda el primer `tareaId` e `itemOrdenId` disponibles.
   Para modificar unidades usar **Cambiar cantidad de repuesto**, con itemOrdenId. Agregar nuevamente el mismo repuesto devuelve 400.
4. Completar tarea y guardar diagnóstico.
5. Finalizar otro trabajo o finalizar service, según su tipo.
6. Consultar el historial del vehículo.

Para órdenes de turnos, Crear turno guarda también `ordenId`. Consultar y asignar mecánico con Editar planificación, conservando el presupuestoId de la cabecera si existe, registrar ingreso e iniciar. Si tiene presupuesto, sus importes se toman del presupuesto. Para fotos, completar una data URL real en el ejemplo de carga; Consultar orden permite ver los IDs.

Las pruebas usan `octano_test`. Las de órdenes confirman cada petición para comprobar transacciones reales y luego eliminan únicamente su taller y catálogo temporal. Verifican estados, materiales, rollback por stock insuficiente, dos inicios concurrentes, datos de cierre, precios históricos, vínculos, fotos y restricciones de acceso.
