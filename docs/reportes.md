# Reportes del taller

Módulo de consulta Controller → Service → Repository → PostgreSQL/JDBC. No usa ORM, tablas de totales ni dependencias nuevas. No modifica datos: no corresponde guardar un último editor para consultar un reporte. Las operaciones originales conservan su propia auditoría.

Solo ADMIN puede consultar. Todas las consultas toman el taller de la sesión JWT, nunca de un parámetro del navegador. RECEPCION y MECANICO reciben 403; sin sesión se devuelve 401.

## Endpoints

| Método | Ruta | Contenido |
| --- | --- | --- |
| GET | `/api/reportes/resumen` | Operación actual, comprobantes emitidos por cobrar con saldo por cliente, ingresos del período y cantidad de alertas de stock. |
| GET | `/api/reportes/ingresos` | Total cobrado, cantidad de cobros, promedio por cobro, serie diaria y desglose por medio de pago. |
| GET | `/api/reportes/trabajos` | Órdenes creadas por estado actual, finalizadas por tipo y mecánico, y horas transcurridas. |
| GET | `/api/reportes/repuestos` | Movimientos por repuesto y alertas de stock actual. |
| GET | `/api/reportes/stock-actual` | Existencias y stock mínimo por repuesto activo, incluidos los repuestos sin movimientos. |

Salvo `stock-actual`, las rutas aceptan `desde` y `hasta` en formato `yyyy-MM-dd`, ambos inclusive. Sin hasta se toma hoy en Argentina; sin desde se toma el primer día del mes de hasta. Sin parámetros se consulta el mes actual hasta hoy. Rango máximo: 366 días. Fechas invertidas, inválidas o rangos mayores devuelven 400. El stock actual acepta `pagina` desde 0 y `tamano` de 1 a 100 (20 por defecto).

Repuestos acepta además `limite`, entre 1 y 100, por defecto 20. Limita tanto el ranking de movimientos como la lista de alertas; `totalStockBajo` indica la cantidad completa de alertas. Las listas están ordenadas para permitir resultados estables. Los reportes completos se leen en transacciones de solo lectura REPEATABLE_READ: un cobro concurrente no deja el total y el gráfico de una misma respuesta en momentos distintos.

Ejemplo: `GET /api/reportes/ingresos?desde=2026-05-01&hasta=2026-05-31`, con Bearer de ADMIN. En Bruno está la carpeta **17 - Reportes**. Se pueden editar las fechas en las peticiones o quitarlas para probar el mes actual.

## Qué significa cada dato

### Resumen

`periodo` solo filtra los ingresos y cantidad de cobros de esta respuesta. `hoy` se calcula con America/Argentina/Buenos_Aires. Los campos llamados actuales reflejan la situación presente, sin filtro histórico:

- `vehiculosEnTaller`: vehículos distintos con OT EN_ESPERA o EN_PROCESO; dos órdenes del mismo vehículo no lo duplican.
- Cantidades de órdenes EN_ESPERA, EN_PROCESO y PENDIENTE_INGRESO.
- `ordenesFinalizadasSinComprobante`: finalizadas sin ningún comprobante, incluso borrador. No se denomina vehículos por entregar porque aún no existe registro de entrega física.
- `turnosHoy`: turnos de hoy no cancelados. Incluye NO_ASISTIO, como el contador actual del frontend.
- `comprobantesPorCobrar`: cantidad e importe de comprobantes EMITIDO sin cobro. `porCliente` agrupa el mismo saldo por ID de cliente e incluye nombre, cantidad e importe. Excluye borradores y órdenes todavía sin facturar.
- Alertas de repuestos activos con stock menor o igual al mínimo, incluyendo stock cero.

### Ingresos

La fuente es `cobro.fecha` e `importe`, no la fecha del borrador, del presupuesto ni de finalización de la orden. El total es dinero registrado como recibido, con IVA incluido cuando corresponda al comprobante. No representa ganancia, rentabilidad ni facturación fiscal de ARCA.

La serie diaria incluye los días sin cobros con cero, ordenados por fecha. Los límites se convierten a fecha argentina; un cobro a las 02:59 UTC corresponde al día anterior en Argentina. El promedio usa dos decimales HALF_UP y devuelve cero sin cobros. El desglose por medio solo incluye los medios con movimientos. No se suman conjuntamente cabecera y renglones: cada cobro se cuenta una vez.

### Trabajos

`creadas` y `creadasPorEstadoActual` usan fecha_creacion. Reflejan el estado actual de las OT creadas en ese rango; no reconstruyen cómo estaban en una fecha pasada. Incluyen canceladas explícitamente como CANCELADO.

`finalizadas` usa fin_fecha y estado FINALIZADO, aunque la OT se hubiera creado antes del período. `finalizadasPorTipo` cuenta cada OT una vez por tipo: SERVICE + DISTRIBUCION suman uno en ambos grupos, pero solo uno en el total de órdenes finalizadas. Por eso la suma por tipos puede superar el total. El detalle de repuestos no multiplica las órdenes.

`finalizadasPorMecanico` incluye ID, nombre, cantidad y horas transcurridas, también para mecánicos ahora inactivos. Se suman los intervalos desde el inicio hasta el cierre de las órdenes finalizadas en el período. `ordenesConDuracion` cuenta las órdenes con ambas marcas válidas y `horasRegistradas` es nulo cuando no hay ninguna. Los intervalos pueden incluir pausas y superponerse entre órdenes. Para medir horas efectivas se necesitarían registros de tiempo por mecánico.

### Repuestos

La fuente histórica es movimiento_stock.fecha, no la cantidad actual de los renglones editables de las OT. Se informan entradas, salidas, variación neta de ajustes y variación total. Devoluciones están en entradas. Las salidas incluyen todas las salidas registradas, no únicamente consumo de órdenes; no equivalen a ventas. Los ajustes no se presentan como ventas o consumo. El ranking se ordena por cantidad de salidas descendente y después ID. Incluye repuestos actualmente inactivos cuando tuvieron movimientos en el período.

Las alertas son actuales y solo incluyen activos, ordenadas por stock e ID; no reconstruyen stock histórico ni aplican el rango de movimientos. El código, descripción y marca de un movimiento corresponden al catálogo actual. No se calcula costo histórico ni utilidad con el precio de costo vigente.

## Correspondencia con el frontend

El administrador puede abrir **Reportes** en el menú. El rango de fechas filtra trabajos y movimientos. Los saldos pendientes y el stock muestran la situación actual del taller.

## Verificación

Pruebas con PostgreSQL separado: taller vacío, días en cero, promedio, límites del mes en Argentina, cobros frente a impagos/borradores, conteos de órdenes con varios tipos y renglones, mecánicos inactivos, movimientos con cantidades decimales y ajustes, historial de repuestos inactivos, stock igual al mínimo, límites de listas, rangos inválidos, permisos y aislamiento entre empresas.
