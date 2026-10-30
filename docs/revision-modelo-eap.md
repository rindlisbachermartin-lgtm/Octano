# Revisión del modelo de Enterprise Architect

> Revisión histórica del EAP inicial. El código proporcionado después por el usuario está en [modelo-dominio.puml](modelo-dominio.puml) y es la base vigente: conserva el Usuario con credenciales, excluye FichaTecnica y conserva únicamente DetalleFactura con atributos. Los hallazgos e inventario siguientes corresponden al archivo inicial.

Fuente: ModeloDatosProyectoDS.EAP, leído sobre una copia de trabajo sin modificar el original.

## Contenido leído

El repositorio contiene un diagrama lógico llamado Domain Model, 36 clases (incluyendo nombres repetidos), 184 atributos y 66 conectores de asociación. No es todavía un esquema PostgreSQL listo para ejecutar. Los identificadores EA que aparecen debajo permiten distinguir objetos con el mismo nombre.

## Hallazgos antes de implementar

| Punto | Evidencia en el archivo | Propuesta para revisar |
| --- | --- | --- |
| Clases repetidas | Usuario: EA 28 y 31; FichaTecnica: EA 19 y 21; DetalleFactura: EA 25 y 26. DetalleFactura EA 25 no tiene atributos. | Acordar si son borradores o conceptos diferentes. No generar tablas duplicadas automáticamente. |
| Fechas como enteros | Vehiculo.FechaModif y FichaTecnica EA 21.FechaUlitmoServ / FechaUltimDistrib son int. | Usar tipos de fecha o fecha y hora según el significado del dato. |
| Montos con float | Precios, totales e IVA en Repuestos, OrdenTrabajo, Presupuesto, Factura y sus detalles. | Usar numeric con precisión y escala definidas en PostgreSQL y BigDecimal en Java. |
| Identificadores y teléfonos numéricos | Cliente.Telefono, Cliente.Dni/Cuil, Taller.Telefono, Taller.CuilCuit y Factura.NumeroCAE. | Tratar teléfonos, documentos y códigos como texto para preservar su formato. Separar tipo y número de documento. |
| Historial de titularidad | Titularidad solo contiene IdVehiculo e IdCliente. | Si se requiere historial, agregar vigencia o fecha del cambio y definir cómo se identifica el titular actual. |
| Datos derivados del catálogo | Vehiculo tiene IdVersion, IdModelo e IdMarca; Version pertenece a Modelo y Modelo pertenece a Marca. Compatibilidad tiene IdVersion e IdModelo. | Evitar combinaciones contradictorias: derivar modelo y marca desde la versión o definir restricciones explícitas si se guardan los tres. Acordar si existen compatibilidades generales por modelo. |
| Orden y presupuesto | OrdenTrabajo tiene IdPresupuesto; Presupuesto tiene IdOrdenTrabajo; la asociación indica una orden y cero o varios presupuestos. | Definir cuál guarda la relación y si hay revisiones o varias propuestas por orden. |
| Tipos de trabajo | OrdenTrabajo tiene un IdTipoTrabajo, pero la asociación con TipoTrabajo está marcada muchos a muchos. | Acordar si una orden admite uno o varios tipos de trabajo. |
| Cardinalidades incompletas | 15 conectores tienen al menos un extremo sin multiplicidad; uno usa 1.. en lugar de una multiplicidad completa. | Completar mínimos, máximos y nulabilidad antes de mapear relaciones JPA. |
| Orden sin estado explícito | OrdenTrabajo no tiene un atributo de estado ni fechas de apertura y cierre entre los atributos leídos. | Acordar cómo representar el ciclo de vida que utiliza el frontend. |
| Direcciones | Existen Calle, Ciudad, Provincia y Direccion; Direccion solo tiene IdDireccion. Cliente y Taller también tienen Calle y Altura. | Elegir una única representación y completar los vínculos necesarios. |
| TipoFactura | idTipoFactura está definido como char y TipoFactura como int. | Confirmar si es intencional o si los tipos quedaron intercambiados. |
| Aislamiento por taller | Varias clases contienen IdTaller. | Acordar unicidades por taller, pertenencia de usuarios y validación de que los registros relacionados pertenezcan al mismo taller. |
| Credenciales | Usuario EA 31 incluye Contraseña. | Al implementar autenticación, guardar un hash de contraseña y excluirlo de las respuestas de la API. |

Estas son observaciones y propuestas; no se corrigió el EAP ni se crearon entidades o migraciones a partir de decisiones pendientes.

## Orden de implementación propuesto

El modelo incorpora Taller y Usuario como dependencias de Cliente. Primero hay que acordar esas relaciones y la condición de IVA. Luego implementar Clientes con Controller → Service → Repository → PostgreSQL y conectar ese módulo al frontend. Después avanzar con Marca, Modelo, Version y Vehiculo. El catálogo de vehículos será propio, sin consultas a FRAM.

Los cambios al esquema deben quedar en migraciones SQL. Este inventario conserva nombres y tipos originales; no implica que todos los atributos deban ser obligatorios o convertirse directamente en columnas.

## Inventario de clases y atributos

### Cliente (EA 9)

| Atributo | Tipo original |
| --- | --- |
| IdCliente | int |
| Nombre/RazonSocial | char |
| Telefono | numeric |
| Dni/Cuil | numeric |
| Email | char |
| IdCondIva | int |
| FechaModif | date |
| Estado | boolean |
| IdUsuario | int |
| IdTaller | int |
| Calle | char |
| Altura | int |

### CondIva (EA 27)

| Atributo | Tipo original |
| --- | --- |
| IdCondiva | int |
| CondIva | char |

### Usuario (EA 28)

| Atributo | Tipo original |
| --- | --- |
| IdUsuario | int |
| Nombre | char |
| Apellido | char |
| Email | char |
| Telefono | numeric |
| IdCondIva | int |
| Cuil | numeric |
| IdTaller | int |

### Vehiculo (EA 10)

| Atributo | Tipo original |
| --- | --- |
| IdVehiculo | int |
| Patente | char |
| IdVersion | int |
| Año | int |
| Kilometraje | int |
| IdCliente | int |
| IdQR | int |
| IdTaller | int |
| Estado | int |
| FechaModif | int |
| IdUsuario | int |
| IdModelo | int |
| IdMarca | int |

### OrdenTrabajo (EA 12)

| Atributo | Tipo original |
| --- | --- |
| IdOrdenTrabajo | int |
| IdTipoTrabajo | int |
| DescripcionDeTrabajo | char |
| IdMecanico | int |
| Observaciones | char |
| Precio | float |
| IdVehiculo | int |
| IdPresupuesto | int |
| IdCliente | int |
| IdTaller | int |

### Presupuesto (EA 13)

| Atributo | Tipo original |
| --- | --- |
| IdPresup | int |
| IdVehiculo | int |
| Fecha | date |
| Total | float |
| ManoDeObra | float |
| DescTrabajo | char |
| IdCliente | int |
| IdOrdenTrabajo | int |
| TotalRep | float |
| MontoIva | float |
| IdTaller | int |
| IdTipoIva | int |

### Turno (EA 14)

| Atributo | Tipo original |
| --- | --- |
| IdTurno | int |
| IdCliente | int |
| IdVehiculo | int |
| Fecha | date |
| HoraInicio | time |
| HoraFin | time |
| IdPresupuesto | int |
| MotivoVisita | char |
| IdTaller | int |

### Repuestos (EA 15)

| Atributo | Tipo original |
| --- | --- |
| IdRepuesto | int |
| Descripcion | char |
| PrecioCosto | float |
| PrecioPublico | float |
| Stock | float |
| StockMinimo | float |
| CodigoOEM | char |
| IdTaller | int |
| Margen | float |
| Marca | char |

### Titularidad (EA 16)

| Atributo | Tipo original |
| --- | --- |
| IdVehiculo | int |
| IdCliente | int |

### DetalleRepuestosOrdenTrabajo (EA 17)

| Atributo | Tipo original |
| --- | --- |
| IdOrdenDeTrabajo | int |
| IdRepuesto | int |
| Cantidad | float |
| Subtotal | float |
| PrecioUnit | float |

### PeritajeFotografico (EA 18)

| Atributo | Tipo original |
| --- | --- |
| IdOrdenDeTrabajo | int |
| Foto | char |
| IdParteVehic | int |
| IdPeritaje | int |

### FichaTecnica (EA 19)

| Atributo | Tipo original |
| --- | --- |
| IdFichaTecnica | int |
| IdVehiculo | int |
| IdQR | int |

### DetallePresupuesto (EA 20)

| Atributo | Tipo original |
| --- | --- |
| IdRepuesto | int |
| Cantidad | float |
| Subtotal | float |
| IdPresupuesto | int |
| PrecioUnit | float |

### FichaTecnica (EA 21)

| Atributo | Tipo original |
| --- | --- |
| IdQR | int |
| IdVehiculo | int |
| KmUltimoServ | int |
| FechaUlitmoServ | int |
| KmUltimDistrib | int |
| FechaUltimDistrib | int |
| KmPrxService | int |

### Qr (EA 22)

| Atributo | Tipo original |
| --- | --- |
| IdCodQr | int |
| CodQr | char |
| Activo | boolean |
| Impreso | boolean |

### TipoTrabajo (EA 23)

| Atributo | Tipo original |
| --- | --- |
| IdTipoTrabajo | int |
| TipoDeTrabajo | char |

### Factura (EA 24)

| Atributo | Tipo original |
| --- | --- |
| IdFactura | int |
| FechaEmision | date |
| IdCliente | int |
| IdTipoFactura | int |
| IdOrdenDeTrabajo | int |
| NumeroCAE | int |
| Total | float |
| VencimientoCAE | date |
| IdCondVenta | int |
| Descuento/Recargo | float |
| IdVehiculo | int |
| Pagada | boolean |
| IdTipoIva | int |
| IdTaller | int |
| Neto | float |
| Iva | float |

### DetalleFactura (EA 25)

Sin atributos.

### DetalleFactura (EA 26)

| Atributo | Tipo original |
| --- | --- |
| IdFactura | int |
| IdRepuesto | int |
| Cantidad | float |
| PrecioUnitario | float |
| Descripcion | char |
| Importe | float |

### Mecanico (EA 29)

| Atributo | Tipo original |
| --- | --- |
| IdMecanico | int |
| Nombre | char |
| Apellido | char |
| Telefono | numeric |
| FechaInicio | date |
| IdTaller | int |
| Estado | boolean |

### Taller (EA 30)

| Atributo | Tipo original |
| --- | --- |
| IdTaller | int |
| Altura | int |
| IngresosBrutos | Numeric |
| InicioActiv | date |
| Taller | char |
| Telefono | int |
| Calle | char |
| IdCondIva | int |
| CuilCuit | int |

### Usuario (EA 31)

| Atributo | Tipo original |
| --- | --- |
| IdTaller | int |
| IdUsuario | int |
| IdTipoUsuario | int |
| Email | char |
| Contraseña | char |
| IdMecanico | int |

### TipoUsuario (EA 32)

| Atributo | Tipo original |
| --- | --- |
| IdTipoUsuario | int |
| TipoUsuario | char |

### Modelo (EA 33)

| Atributo | Tipo original |
| --- | --- |
| IdModelo | int |
| Modelo | char |
| IdMarca | int |

### Marca (EA 34)

| Atributo | Tipo original |
| --- | --- |
| IdMarca | int |
| Marca | char |

### Version (EA 35)

| Atributo | Tipo original |
| --- | --- |
| IdVersion | int |
| Version | char |
| IdModelo | int |

### ParteVehic (EA 36)

| Atributo | Tipo original |
| --- | --- |
| IdParteVehic | int |
| ParteVehic | char |

### TipoIva (EA 37)

| Atributo | Tipo original |
| --- | --- |
| IdTipoIva | int |
| TipoIva | char |

### Compatibilidad (EA 38)

| Atributo | Tipo original |
| --- | --- |
| IdRepuesto | int |
| IdVersion | int |
| IdModelo | int |

### CondVenta (EA 39)

| Atributo | Tipo original |
| --- | --- |
| IdCondVenta | int |
| CondVenta | char |

### TipoFactura (EA 40)

| Atributo | Tipo original |
| --- | --- |
| idTipoFactura | char |
| TipoFactura | int |

### TipoCorresp (EA 41)

| Atributo | Tipo original |
| --- | --- |
| IdCondIva | int |
| IdTipoFactura | int |

### Calle (EA 42)

| Atributo | Tipo original |
| --- | --- |
| IdCalle | int |
| Calle | char |
| IdCiudad | int |

### Ciudad (EA 43)

| Atributo | Tipo original |
| --- | --- |
| IdCiudad | int |
| Ciudad | char |
| IdProv | int |

### Provincia (EA 44)

| Atributo | Tipo original |
| --- | --- |
| IdProv | int |
| Provincia | char |

### Direccion (EA 45)

| Atributo | Tipo original |
| --- | --- |
| IdDireccion | int |

## Relaciones registradas

Las multiplicidades se transcriben tal como están en EA; no se infieren las que faltan. La dirección de esta tabla identifica los extremos del conector, no la dirección de una clave foránea.

| Conector EA | Extremo inicial | Multiplicidad inicial | Extremo final | Multiplicidad final |
| --- | --- | --- | --- | --- |
| 4 | Cliente (EA 9) | Sin definir | Titularidad (EA 16) | Sin definir |
| 5 | Vehiculo (EA 10) | Sin definir | Titularidad (EA 16) | Sin definir |
| 6 | OrdenTrabajo (EA 12) | 1 | DetalleRepuestosOrdenTrabajo (EA 17) | * |
| 7 | Repuestos (EA 15) | 1 | DetalleRepuestosOrdenTrabajo (EA 17) | * |
| 34 | CondIva (EA 27) | 1 | Cliente (EA 9) | * |
| 35 | Usuario (EA 28) | 1 | Mecanico (EA 29) | * |
| 8 | OrdenTrabajo (EA 12) | * | Vehiculo (EA 10) | 1 |
| 11 | OrdenTrabajo (EA 12) | 1 | Presupuesto (EA 13) | 0..* |
| 12 | Presupuesto (EA 13) | * | Cliente (EA 9) | 1 |
| 80 | Ciudad (EA 43) | Sin definir | Taller (EA 30) | Sin definir |
| 14 | Turno (EA 14) | 0..1 | OrdenTrabajo (EA 12) | 1 |
| 15 | Turno (EA 14) | 1 | Presupuesto (EA 13) | 0..1 |
| 16 | PeritajeFotografico (EA 18) | * | OrdenTrabajo (EA 12) | 1 |
| 17 | Presupuesto (EA 13) | 1 | DetallePresupuesto (EA 20) | * |
| 18 | DetallePresupuesto (EA 20) | * | Repuestos (EA 15) | 1.. |
| 37 | Taller (EA 30) | 1 | Usuario (EA 31) | * |
| 19 | Vehiculo (EA 10) | 1 | Turno (EA 14) | * |
| 20 | Vehiculo (EA 10) | * | Cliente (EA 9) | 1 |
| 21 | FichaTecnica (EA 19) | Sin definir | OrdenTrabajo (EA 12) | Sin definir |
| 23 | Qr (EA 22) | 1 | FichaTecnica (EA 21) | 1 |
| 38 | Usuario (EA 31) | 1 | OrdenTrabajo (EA 12) | * |
| 39 | TipoUsuario (EA 32) | Sin definir | Usuario (EA 31) | Sin definir |
| 25 | FichaTecnica (EA 21) | Sin definir | TipoTrabajo (EA 23) | Sin definir |
| 26 | TipoTrabajo (EA 23) | * | OrdenTrabajo (EA 12) | * |
| 27 | Qr (EA 22) | 1 | Vehiculo (EA 10) | 1 |
| 28 | OrdenTrabajo (EA 12) | 1 | Factura (EA 24) | 1 |
| 29 | Factura (EA 24) | Sin definir | DetalleFactura (EA 25) | Sin definir |
| 40 | Mecanico (EA 29) | 0..1 | OrdenTrabajo (EA 12) | * |
| 32 | Factura (EA 24) | 1 | DetalleFactura (EA 26) | * |
| 33 | DetalleFactura (EA 26) | * | Repuestos (EA 15) | 1 |
| 41 | Taller (EA 30) | * | CondIva (EA 27) | 1 |
| 44 | TipoTrabajo (EA 23) | 1 | Presupuesto (EA 13) | * |
| 81 | Ciudad (EA 43) | Sin definir | Cliente (EA 9) | Sin definir |
| 82 | Mecanico (EA 29) | Sin definir | Usuario (EA 31) | Sin definir |
| 45 | Version (EA 35) | 1 | Vehiculo (EA 10) | * |
| 46 | Modelo (EA 33) | 1 | Version (EA 35) | * |
| 47 | Marca (EA 34) | 1 | Modelo (EA 33) | * |
| 48 | Usuario (EA 31) | 1 | Vehiculo (EA 10) | * |
| 50 | Usuario (EA 31) | 1 | Turno (EA 14) | * |
| 51 | Usuario (EA 31) | 1 | Factura (EA 24) | * |
| 52 | Usuario (EA 31) | 1 | Cliente (EA 9) | * |
| 53 | Usuario (EA 31) | 1 | Presupuesto (EA 13) | * |
| 54 | Usuario (EA 31) | 1 | Repuestos (EA 15) | * |
| 55 | ParteVehic (EA 36) | 1 | PeritajeFotografico (EA 18) | * |
| 58 | Factura (EA 24) | * | TipoIva (EA 37) | 1 |
| 59 | Repuestos (EA 15) | 1 | Compatibilidad (EA 38) | * |
| 60 | Version (EA 35) | 1 | Compatibilidad (EA 38) | * |
| 61 | Taller (EA 30) | 1 | Mecanico (EA 29) | * |
| 62 | Taller (EA 30) | 1 | OrdenTrabajo (EA 12) | * |
| 63 | Taller (EA 30) | Sin definir | Cliente (EA 9) | Sin definir |
| 64 | Taller (EA 30) | 1 | Factura (EA 24) | * |
| 65 | Taller (EA 30) | 1 | Presupuesto (EA 13) | * |
| 66 | Taller (EA 30) | 1 | Vehiculo (EA 10) | * |
| 67 | Taller (EA 30) | 1 | Turno (EA 14) | * |
| 68 | Taller (EA 30) | 1 | Repuestos (EA 15) | * |
| 69 | Presupuesto (EA 13) | * | TipoIva (EA 37) | 1 |
| 70 | CondVenta (EA 39) | 1 | Factura (EA 24) | * |
| 71 | CondIva (EA 27) | 1 | TipoCorresp (EA 41) | * |
| 72 | TipoCorresp (EA 41) | * | TipoFactura (EA 40) | 1 |
| 73 | TipoFactura (EA 40) | Sin definir | Factura (EA 24) | Sin definir |
| 74 | Provincia (EA 44) | 1 | Ciudad (EA 43) | * |
| 75 | Ciudad (EA 43) | 1 | Calle (EA 42) | * |
| 76 | Calle (EA 42) | Sin definir | Direccion (EA 45) | Sin definir |
| 77 | Direccion (EA 45) | Sin definir | Taller (EA 30) | Sin definir |
| 78 | Calle (EA 42) | Sin definir | Taller (EA 30) | Sin definir |
| 79 | Calle (EA 42) | Sin definir | Cliente (EA 9) | Sin definir |
