# Backend de Octano

El backend utiliza PostgreSQL, JDBC y Flyway, sin ORM. Están implementados Taller, Condición de IVA, Usuarios con autenticación JWT, Mecánicos, Clientes, catálogo, Vehículos, Repuestos, Stock, Turnos y Presupuestos, separados en Controller, Service y Repository. Se trabaja un módulo por entrega; la conexión con el frontend queda para después de completar el backend interno.

## Preparación local

PostgreSQL debe estar iniciado en `localhost:5432`. Los siguientes pasos utilizan el usuario local `postgres`, confirmado para este entorno de desarrollo.

Desde `backend/`, copiar `.env.properties.example` a `.env.properties` si el archivo aún no existe y completar `DB_PASSWORD`. Está excluido de Git. En el formato properties, cada barra invertida de la contraseña debe duplicarse. No agregar comillas alrededor del valor.

```properties
DB_URL=jdbc:postgresql://localhost:5432/octano
DB_USERNAME=postgres
DB_PASSWORD=
TEST_DB_URL=jdbc:postgresql://localhost:5432/octano_test
JWT_SECRET=
```

También se pueden definir esas claves como variables de entorno, que tienen prioridad. Spring lee el archivo local desde el directorio de ejecución; ejecutar Maven desde `backend/`.

El script de preparación usa el cliente de PostgreSQL 18 instalado en Windows, crea las dos bases si no existen y genera una clave JWT aleatoria si aún no está configurada. La guarda en `.env.properties` sin mostrarla. No borra ni reinicia bases existentes. Conservar la clave entre reinicios y excluirla de Git:

```powershell
.\scripts\preparar-base.ps1
```

Como alternativa, ejecutar estas sentencias **por separado**, conectado a la base `postgres` desde pgAdmin o DBeaver, con autocommit habilitado:

```sql
CREATE DATABASE octano;
CREATE DATABASE octano_test;
```

La cuenta de desarrollo necesita permiso para crear bases y esquemas. Cuando se prepare un despliegue, se usarán cuentas propias con permisos acordes a cada tarea.

## Arrancar y comprobar

`JAVA_HOME` debe apuntar a la raíz del JDK, sin `/bin`:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-27'
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

La aplicación usa `octano` y el perfil de pruebas usa `octano_test`. Si se cambia `TEST_DB_URL`, debe apuntar a una base exclusiva de pruebas. Al arrancar, Flyway aplica las migraciones pendientes y registra sus versiones en `public.flyway_schema_history`:

- V1 crea el esquema `octano`.
- V2 crea `octano.condicion_iva` y `octano.taller`, con su clave foránea y restricciones. Carga las ocho condiciones de IVA que ya utiliza la maqueta; sus IDs son internos, no códigos de ARCA.
- V3 crea `octano.tipo_usuario`, `octano.usuario` y la tabla base `octano.mecanico`, necesaria para el vínculo del rol MECANICO. Su gestión utiliza esa misma tabla, sin otra migración.
- V4 crea `octano.cliente`, con documento único por taller y el usuario de la última modificación.
- V5 crea el catálogo propio de marca, modelo y versión, sin datos precargados.
- V6 crea los vehículos y el historial de titularidad, con claves foráneas que mantienen los vínculos dentro del mismo taller.
- V7 crea repuestos, compatibilidades y movimientos de stock con restricciones de saldo y relaciones por taller.
- V8 crea Turnos, con validación de intervalos, auditoría de superposiciones y un solo turno programado/confirmado por vehículo y día.
- V9 crea Presupuestos y sus detalles, y agrega la vinculación opcional de Turnos con un presupuesto del mismo taller.

Para comprobarlo desde pgAdmin o DBeaver, conectado a la base `octano` después de arrancar la aplicación:

```sql
SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'octano';
SELECT version, description, success FROM public.flyway_schema_history ORDER BY installed_rank;
SELECT * FROM octano.condicion_iva ORDER BY nombre;
SELECT * FROM octano.taller ORDER BY id;
```

Las pruebas de integración consultan PostgreSQL real: comprueban las migraciones y la conexión JDBC, recorren los endpoints, prueban validaciones y verifican la clave foránea. Las pruebas de alta y edición se revierten al terminar cada caso, dentro de `octano_test`. No modifican datos de la aplicación.

## Endpoints disponibles

Dirección local: `http://127.0.0.1:8080`. El servidor escucha solo en esta computadora. El catálogo de IVA y el registro/login son públicos; las demás operaciones requieren `Authorization: Bearer <tokenAcceso>`.

| Método | Ruta | Operación |
| --- | --- | --- |
| GET | `/api/condiciones-iva` | Consultar el catálogo ordenado por nombre. |
| GET | `/api/condiciones-iva/{id}` | Consultar una condición. |
| GET | `/api/talleres` | Consultar el taller del usuario conectado (ADMIN o RECEPCION). |
| GET | `/api/talleres/{id}` | Consultar únicamente el taller propio (ADMIN o RECEPCION). |
| POST | `/api/talleres` | Crear taller y administrador juntos; devuelve 201, sesión y Location. |
| PUT | `/api/talleres/{id}` | Actualizar el taller propio (ADMIN). |
| POST | `/api/auth/registro` | Ruta compatible para la misma alta conjunta de taller y administrador. |
| POST | `/api/auth/login` | Autenticarse con email y contraseña. |
| GET | `/api/auth/me` | Consultar el usuario conectado. |
| POST | `/api/auth/logout` | Revocar las sesiones del usuario conectado. |
| PUT | `/api/auth/password` | Cambiar la contraseña y recibir un nuevo token. |
| GET | `/api/usuarios` | Listar las cuentas del taller propio (ADMIN). |
| GET | `/api/usuarios/{id}` | Consultar una cuenta del taller propio (ADMIN). |
| GET | `/api/usuarios/roles` | Consultar ADMIN, RECEPCION y MECANICO (ADMIN). |
| POST | `/api/usuarios` | Crear una cuenta en el taller propio (ADMIN). |
| PATCH | `/api/usuarios/{id}/estado` | Activar o desactivar una cuenta propia (ADMIN). |
| GET | `/api/mecanicos` | Listar mecánicos propios, ordenados por apellido y nombre (ADMIN o RECEPCION). Filtro opcional `?activo=true` o `false`. |
| GET | `/api/mecanicos/{id}` | Consultar un mecánico propio (ADMIN o RECEPCION). |
| POST | `/api/mecanicos` | Crear un mecánico activo en el taller de la sesión (ADMIN). |
| PUT | `/api/mecanicos/{id}` | Editar sus datos (ADMIN). |
| PATCH | `/api/mecanicos/{id}/estado` | Activar o desactivar un mecánico propio (ADMIN). |
| GET | `/api/clientes` | Listar y buscar clientes propios, con paginación y filtro de estado (ADMIN o RECEPCION). |
| GET | `/api/clientes/{id}` | Consultar un cliente propio (ADMIN o RECEPCION). |
| POST | `/api/clientes` | Crear un cliente activo (ADMIN o RECEPCION). |
| PUT | `/api/clientes/{id}` | Editar un cliente propio (ADMIN o RECEPCION). |
| PATCH | `/api/clientes/{id}/estado` | Activar o desactivar un cliente propio (ADMIN o RECEPCION). |

Datos ficticios de taller para PUT o para el campo `taller` del registro; elegir `condicionIvaId` desde el catálogo:

```json
{
  "nombre": "Taller de ejemplo",
  "calle": "Calle de ejemplo",
  "altura": 100,
  "telefono": "02392 456789",
  "cuit": "20-12345678-6",
  "ingresosBrutos": "000123",
  "inicioActividades": "2020-01-01",
  "condicionIvaId": 2
}
```

`ingresosBrutos` es opcional; los demás campos son obligatorios. CUIT, teléfono e Ingresos Brutos se guardan como texto, preservando ceros iniciales. El CUIT se normaliza sin espacios ni guiones y se controla su dígito verificador; esto no consulta el padrón fiscal. `inicioActividades` no admite fechas futuras y `altura` admite cero pero no negativos ni decimales. La respuesta incluye una condición de IVA con `id` y `nombre`, mediante DTOs.

Los errores usan ProblemDetail: 400 para datos inválidos (con `errores` por campo cuando falla Bean Validation), 404 para recursos inexistentes y 409 para conflictos de integridad. El catálogo de IVA no tiene endpoints de escritura y no se agregó eliminación de talleres.

## Registro y acceso

El taller y su primer administrador se crean con **POST /api/talleres**, sin token previo. El cuerpo contiene los datos del taller dentro de `taller`, más el `email` y la `password` del administrador. La ruta `POST /api/auth/registro` se conserva como alternativa compatible. Ambas usan la misma operación transaccional; no crean talleres sin administrador. No hay usuarios ni claves predeterminados. La respuesta devuelve `tokenAcceso`, `tipo`, `expiraEnSegundos` y `usuario`.

Ejemplo completo de alta conjunta (datos ficticios; elegir un correo nuevo):

```json
{
  "email": "admin@ejemplo.test",
  "password": "ClaveDePrueba123!",
  "taller": {
    "nombre": "Taller de ejemplo",
    "calle": "Calle de ejemplo",
    "altura": 100,
    "telefono": "02392 456789",
    "cuit": "20-12345678-6",
    "ingresosBrutos": "000123",
    "inicioActividades": "2020-01-01",
    "condicionIvaId": 2
  }
}
```

Enviar solamente el objeto de taller recibe 400 indicando que faltan `taller`, `email` y `password`. Usar el token devuelto para las consultas y ediciones posteriores. El encabezado Location de POST /api/talleres apunta al taller recién creado.

- Registro y alta de cuentas: contraseña de 12 a 72 caracteres y como máximo 72 bytes UTF-8, por el límite de BCrypt. El email se normaliza a minúsculas y es único.
- Login: `{"email":"tu-correo","password":"tu-clave"}`. El token dura 30 minutos; no se usan cookies ni sesiones HTTP.
- Cambio de clave: PUT /api/auth/password con `{"actual":"clave-actual","nueva":"nueva-clave"}`. Requiere Bearer, revoca tokens anteriores y devuelve un token nuevo.
- Logout: POST /api/auth/logout revoca todos los tokens del usuario, incluidos los de otros dispositivos.
- Alta por un administrador: `{"email":"otro-correo","password":"clave-segura","rol":"RECEPCION","mecanicoId":null}`. ADMIN y RECEPCION no se vinculan a un mecánico. MECANICO requiere el ID de un mecánico activo de ese mismo taller.
- No se puede desactivar al último administrador activo. Los cambios de estado se serializan por taller para mantener esa regla con peticiones simultáneas.
- Las consultas y las bajas de usuarios se filtran siempre por el taller de la sesión. El cliente no elige el taller de una cuenta.
- Los hashes nunca se incluyen en las respuestas. Firma y vencimiento se validan con Spring Security. Una baja o cambio de contraseña invalida inmediatamente los tokens anteriores.

Las pruebas cubren estos recorridos con tokens reales en PostgreSQL; también verifican firmas alteradas, vencimientos, permisos y aislamiento entre talleres. Cada prueba revierte sus datos de ejemplo.

## Decisiones

- Flyway modifica el esquema mediante migraciones SQL versionadas.
- `open-in-view=false`: el acceso a datos de los futuros endpoints quedará dentro de los servicios.
- Las versiones de las dependencias quedan administradas por el BOM de Spring Boot para mantener compatibilidad.
- Se mantienen las tres capas: Controller → Service → Repository → PostgreSQL. Los repositorios ejecutan SQL explícito con `JdbcTemplate` y convierten cada fila manualmente mediante `RowMapper`. Los servicios delimitan las transacciones con `@Transactional`. No se utiliza ORM ni guardado automático: cada cambio requiere un INSERT o UPDATE.
- Después de aplicar una migración, no modificarla: crear una nueva versión.
- El puerto HTTP es `8080`. Abrir `/` devuelve 404; utilizar las rutas `/api/...` de la tabla anterior.

Referencias: [inicialización de bases en Spring Boot](https://docs.spring.io/spring-boot/how-to/data-initialization.html) y [plan del backend](../docs/inicio-backend.md).

## Organización por módulos

Dentro de `src/main/java/com/octano/backend/`, cada funcionalidad contiene sus propias capas:

```text
autenticacion/   controller/  dto/  service/  security/
usuarios/        controller/  dto/  entity/  repository/  service/
talleres/        controller/  dto/  entity/  repository/  service/
condicionesiva/  controller/  dto/  entity/  repository/  service/
mecanicos/       controller/  dto/  entity/  repository/  service/
clientes/        controller/  dto/  entity/  repository/  service/
vehiculos/       controller/  dto/  entity/  repository/  service/
repuestos/       controller/  dto/  entity/  repository/  service/
stock/           controller/  dto/  repository/  service/
turnos/          controller/  dto/  entity/  exception/  repository/  service/
presupuestos/    controller/  dto/  entity/  repository/  service/
catalogovehiculos/ controller/ dto/ repository/ service/
compartido/      exception/  validation/  web/
```

`compartido` reúne las excepciones y su traducción a respuestas HTTP. Las tres responsabilidades se mantienen: Controller → Service → Repository. Las pruebas están agrupadas por módulo en `src/test/java/com/octano/backend/`, con utilidades comunes en `soporte`.

La colección `bruno/Octano` incluye las operaciones de los módulos actuales. Registro, login y cambio de contraseña capturan automáticamente `token`, `tallerId` y `usuarioId`; crear un usuario actualiza `usuarioId` para consultar o cambiar el estado de esa nueva cuenta. Los IDs ya no están fijados en 1. Login selecciona nuevamente los datos de la cuenta que inicia sesión.


## Probar con Bruno

1. Abrir la colección `backend/bruno/Octano` y elegir el entorno **Local**.
2. Ejecutar **Crear taller** (o **Registro**, una sola de las dos) con un correo nuevo, o **Login** si la cuenta ya existe. Estas peticiones guardan automáticamente el token y los IDs.
3. Probar las consultas y ediciones con esa sesión. Las operaciones de usuarios y la edición de taller requieren ADMIN.
4. Ejecutar **Logout** al final. Si el token vence o se cierra la sesión, ejecutar Login nuevamente.

Un 400 informa un cuerpo inválido; 401 indica que falta una sesión válida y 403 indica permisos insuficientes. Un error `ECONNREFUSED` indica que no se pudo conectar al servidor en la dirección configurada. Para iniciar el backend, ejecutar desde `backend/`:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-27'
.\mvnw.cmd spring-boot:run
```

Mantener esa terminal abierta mientras se usa Bruno. Si el backend ya está escuchando en 8080, no iniciar otra instancia.


## Mecánicos

Los datos obligatorios son `nombre`, `apellido`, `telefono` y `fechaInicio` (AAAA-MM-DD, hasta hoy). `calle` y `cuit` son opcionales. Se quitan espacios de los extremos y el CUIT/CUIL se normaliza y valida con el mismo control que el taller. El taller se toma de la sesión; no se recibe en el cuerpo.

En Bruno, iniciar sesión como ADMIN y ejecutar **05 - Mecanicos → Crear mecanico**. La respuesta guarda `mecanicoId` para consultar, editar o cambiar el estado. La baja envía `{"activo":false}`; para reactivar, enviar `true`. No hay borrado físico.

Crear un mecánico no crea una cuenta de acceso. Para darle acceso, usar POST /api/usuarios con rol MECANICO y su `mecanicoId`. Desactivar al mecánico desactiva también su cuenta vinculada y revoca sus tokens dentro de la misma transacción. Reactivar el mecánico no reactiva automáticamente la cuenta; se habilita después desde Usuarios.

Las pruebas de Mecánicos verifican persistencia, filtros, datos inválidos, permisos, aislamiento entre talleres y baja de la cuenta vinculada. Todos los datos de prueba se revierten en `octano_test`.


## Clientes

En Bruno, iniciar sesión como ADMIN o RECEPCION y abrir **06 - Clientes → Crear cliente**. La respuesta guarda `clienteId` para consultar, editar y cambiar el estado. El DNI de ejemplo es único dentro del taller: repetir el alta devuelve 409; editar ese registro o elegir otro documento. La baja no libera el documento ni borra los datos; puede reactivarse el mismo cliente.

Campos obligatorios: `nombre` (nombre o razón social), `documento`, `telefono` y `condicionIvaId`. El documento admite DNI de 7 u 8 dígitos o CUIT/CUIL de 11 con dígito verificador válido; se quitan puntos, espacios y guiones, preservando ceros iniciales. `email`, `calle` y `altura` son opcionales. El email se guarda en minúsculas y la altura debe ser entera y no negativa.

El backend toma el taller y el usuario de la sesión. Cada alta, edición o cambio de estado registra `fechaModif` y `usuarioModificacionId`; esos datos no se reciben del cliente. Las respuestas no exponen datos de otros talleres.

GET /api/clientes acepta `q` (nombre, documento o email; hasta 90 caracteres), `activo` (opcional), `pagina` (desde 0) y `tamano` (1 a 100, por defecto 20). Sin filtro de estado incluye activos e inactivos. La respuesta contiene `contenido`, `pagina`, `tamano`, `totalElementos` y `totalPaginas`, ordenada por nombre e ID. La búsqueda usa SQL parametrizado y trata los símbolos como texto.

Las pruebas verifican altas y ediciones, búsqueda y paginación, validaciones, documento único por taller, bajas, auditoría, permisos y aislamiento. Se ejecutan en `octano_test` y revierten sus datos.


## Catálogo propio y Vehículos

El catálogo de marcas, modelos y versiones es común a los talleres; no contiene clientes ni vehículos. No se conecta con FRAM ni se carga información automáticamente. Un ADMIN crea sus opciones y cualquier usuario autenticado puede consultarlas. No se implementan ediciones ni borrados del catálogo en esta entrega.

| Método | Ruta | Operación |
| --- | --- | --- |
| GET | `/api/catalogo/marcas` | Listar marcas. |
| POST | `/api/catalogo/marcas` | Crear marca: `{"nombre":"Marca"}` (ADMIN). |
| GET | `/api/catalogo/modelos?marcaId=...` | Listar modelos de una marca. |
| POST | `/api/catalogo/modelos` | Crear modelo: `{"marcaId":1,"nombre":"Modelo"}` (ADMIN). |
| GET | `/api/catalogo/versiones?modeloId=...` | Listar versiones de un modelo. |
| POST | `/api/catalogo/versiones` | Crear versión: `{"modeloId":1,"nombre":"Version"}` (ADMIN). |
| GET | `/api/vehiculos` | Listar y buscar vehículos propios. |
| GET | `/api/vehiculos/{id}` | Consultar un vehículo propio. |
| POST | `/api/vehiculos` | Crear vehículo y registrar su primer titular. |
| PUT | `/api/vehiculos/{id}` | Editar; si cambia el cliente, registrar el cambio de titular. |
| PATCH | `/api/vehiculos/{id}/estado` | Baja lógica o reactivación: `{"activo":false}` o `true`. |
| PATCH | `/api/vehiculos/{id}/titular` | Cambiar titular: `{"clienteId":...}`. |
| GET | `/api/vehiculos/{id}/titularidad` | Consultar el historial propio, ordenado desde el alta. |

La gestión de vehículos requiere ADMIN o RECEPCION. El cuerpo de alta/edición contiene `clienteId`, `modeloId`, `versionId` (opcional), `patente`, `anio` y `kilometraje`. La marca se obtiene del modelo; no se guarda otra marca que pudiera contradecirlo. La versión debe corresponder al modelo. El cliente se busca dentro del taller de la sesión y debe estar activo para recibir un vehículo nuevo o transferido. Puede corregirse un vehículo existente conservando su titular inactivo.

Las patentes argentinas de auto se normalizan a `ABC-123` o `AB-123-CD`, aceptando minúsculas, espacios y guiones. Son únicas por taller, incluso después de una baja. Año desde 1900 hasta el próximo año; kilometraje entero entre 0 y 9999999. Cada cambio guarda `fechaModif` y `usuarioModificacionId` desde la sesión. El alta y los cambios de titular se registran en `historial_titularidad` dentro de la misma transacción. Repetir el mismo titular no duplica entradas. La baja conserva el vehículo y su historial.

GET /api/vehiculos acepta `clienteId`, `activo`, `patente` (búsqueda parcial sin guiones), `pagina` desde 0 y `tamano` entre 1 y 100 (por defecto 20). Devuelve `contenido`, `pagina`, `tamano`, `totalElementos` y `totalPaginas`. Todos los filtros y accesos al historial se limitan al taller de la sesión.

Para Bruno: iniciar sesión como ADMIN, crear un cliente con **06 - Clientes** y cargar marca, modelo y versión desde **07 - Catalogo vehiculos**. Las altas guardan sus IDs. Después ejecutar **08 - Vehiculos → Crear vehiculo**; guarda `vehiculoId`. Para probar una transferencia, crear otro cliente (actualiza `clienteId`) y ejecutar **Cambiar titular**, luego **Ver historial de titularidad**. Repetir una marca o patente existente devuelve 409; consultar la existente o usar otro nombre/patente. Para omitir la versión, enviar `"versionId":null`.

Las pruebas cubren catálogo y sus relaciones, altas, ediciones, búsqueda, paginación, validaciones, bajas, duplicados, titularidad, auditoría, permisos y aislamiento entre talleres. QR y ficha pública quedan para la entrega prevista en el plan.


## Repuestos y Stock

ADMIN gestiona repuestos, compatibilidades y movimientos manuales. ADMIN y RECEPCION consultan los datos, saldos e historial de su taller. MECANICO no accede a estas operaciones de inventario. El taller y el usuario se toman de la sesión.

| Método | Ruta | Operación |
| --- | --- | --- |
| GET | `/api/repuestos` | Listar, buscar y filtrar. |
| GET | `/api/repuestos/{id}` | Consultar repuesto y saldo. |
| POST | `/api/repuestos` | Crear un repuesto con stock cero. |
| PUT | `/api/repuestos/{id}` | Editar sus datos y precios. |
| PATCH | `/api/repuestos/{id}/estado` | Baja lógica o reactivación: `{"activo":false}` o `true`. |
| GET | `/api/repuestos/{id}/compatibilidades` | Consultar compatibilidades. |
| POST | `/api/repuestos/{id}/compatibilidades` | Agregar `modeloId` y `versionId` opcional. |
| DELETE | `/api/repuestos/{id}/compatibilidades/{compatibilidadId}` | Quitar una compatibilidad propia. |
| GET | `/api/repuestos/{id}/movimientos` | Consultar el historial paginado, más recientes primero. |
| GET | `/api/repuestos/{id}/movimientos/{movimientoId}` | Consultar un movimiento. |
| POST | `/api/repuestos/{id}/movimientos` | Registrar entrada, salida o ajuste de saldo. |

Alta/edición: `codigoOem`, `descripcion`, `marca`, `precioCosto`, `margen` y `stockMinimo`, todos obligatorios. El código se normaliza a mayúsculas y es único por taller, incluso si el repuesto está inactivo. El precio público se calcula como costo × (1 + margen/100), redondeado a dos decimales con HALF_UP. Importes y margen admiten dos decimales; existencias y cantidades admiten tres, usando BigDecimal y NUMERIC. Se permite costo y margen cero.

El alta comienza con stock cero. El saldo inicial se registra como ENTRADA o AJUSTE. Enviar un campo stock en la edición no modifica existencias; cada cambio requiere un movimiento con `tipo`, `cantidad` y `motivo`:

- ENTRADA: suma una cantidad mayor que cero.
- SALIDA: resta una cantidad mayor que cero y rechaza saldo insuficiente.
- AJUSTE: la cantidad es el **saldo final contado**, admite cero y registra la diferencia, positiva o negativa. Si coincide con el saldo actual devuelve 400.

Cada movimiento guarda cantidad, variación, saldo anterior, saldo posterior, motivo, usuario y fecha. El historial no se edita ni se borra mediante la API; una corrección requiere otro movimiento. La inserción del movimiento y la actualización del saldo comparten una transacción y un bloqueo de fila. PostgreSQL también impide stock negativo y exige que el saldo posterior coincida con la variación registrada.

Un repuesto inactivo conserva su saldo e historial y no admite movimientos hasta reactivarse. Crear, editar, cambiar estado y mover stock actualiza la fecha y el usuario de modificación del repuesto. Comisiones de mecánicos quedan fuera del alcance. El consumo asociado a órdenes se incorporará al implementar su cierre, reutilizando estas reglas.

GET /api/repuestos acepta `q` (código, descripción o marca), `activo`, `stockBajo`, `modeloId`, `versionId`, `pagina` desde 0 y `tamano` de 1 a 100. Stock bajo significa saldo menor o igual al mínimo. Una compatibilidad sin versión sirve para todas las versiones del modelo. Si se indica versión, debe pertenecer al modelo; el filtro por versión incluye compatibilidades generales y específicas. Los listados paginados devuelven contenido, pagina, tamano, totalElementos y totalPaginas.

En Bruno, iniciar sesión como ADMIN y ejecutar **09 - Repuestos → Crear repuesto** (guarda repuestoId). Luego **10 - Stock → Registrar entrada**, **Registrar salida** y **Listar movimientos**. Para compatibilidades, elegir IDs del catálogo local; `versionId:null` indica todo el modelo. La colección guarda compatibilidadId y movimientoId para consultar y quitar relaciones. No desactivar el repuesto antes de probar movimientos.

Las pruebas cubren precios y redondeo, cantidades decimales, filtros, compatibilidades, duplicados, validaciones, permisos y separación por taller. Una prueba con dos peticiones simultáneas verifica que solo una pueda retirar la última unidad. Las pruebas usan octano_test; las transaccionales revierten sus datos y la prueba concurrente elimina únicamente su taller temporal.


## Turnos y disponibilidad

ADMIN y RECEPCION pueden agendar, consultar, confirmar, reprogramar y cancelar turnos de su taller. Solo ADMIN puede autorizar superposiciones. Se respeta el flujo del frontend existente: no se exige seleccionar un mecánico o puesto; el administrador confirma manualmente que puede atender otro vehículo. No se presupone una capacidad fija del taller.

| Método | Ruta | Operación |
| --- | --- | --- |
| GET | `/api/turnos` | Listado paginado y filtros. |
| GET | `/api/turnos/{id}` | Consultar turno propio. |
| GET | `/api/turnos/disponibilidad` | Consultar conflictos para una fecha, horaInicio y horaFin futuras; excluirTurnoId opcional para edición. |
| POST | `/api/turnos` | Crear turno PROGRAMADO. |
| PUT | `/api/turnos/{id}` | Editar o reprogramar un turno PROGRAMADO o CONFIRMADO. |
| PATCH | `/api/turnos/{id}/estado` | Cambiar a CONFIRMADO, CANCELADO o NO_ASISTIO. |

El cuerpo de alta/edición contiene clienteId, vehiculoId, fecha (AAAA-MM-DD), horaInicio y horaFin (HH:mm), motivoVisita y confirmarSuperposicion (opcional, false por defecto). El cliente y el vehículo deben estar activos, pertenecer al mismo taller de la sesión y ser titular/vehículo correspondientes. Inicio y fin pertenecen al mismo día, fin debe superar inicio y ambos se ingresan sin segundos. El inicio debe ser futuro en America/Argentina/Buenos_Aires. Dos turnos consecutivos, por ejemplo 09:00–10:00 y 10:00–11:00, no se superponen.

Si hay superposición, se devuelve **409** con codigo SUPERPOSICION_TURNO, requiereConfirmacion, puedeConfirmar y la lista de conflictos (id, vehiculoId, horaInicio, horaFin). El frontend debe mostrar su aviso y, después de la aceptación de ADMIN, reenviar el cuerpo con confirmarSuperposicion:true. RECEPCION puede ver el aviso, pero enviar ese indicador devuelve 403. Nunca se reintenta automáticamente con autorización. La consulta de disponibilidad es informativa; el guardado vuelve a verificar dentro de una transacción con bloqueo por taller, incluyendo peticiones simultáneas.

Los turnos autorizados guardan superposicionAutorizada, autorizadaPor y autorizadaFecha. Si se reprograman, se verifica otra vez el nuevo horario y se registra la autorización correspondiente a ese horario. Todas las altas, ediciones y cambios de estado guardan fechaModif y usuarioModificacionId. Aunque ADMIN autorice superposiciones, el mismo vehículo no puede tener dos turnos PROGRAMADO/CONFIRMADO el mismo día, como ya valida el frontend.

Cancelar conserva el turno y libera el horario. NO_ASISTIO solo se admite después del fin estimado. Los turnos CANCELADO y NO_ASISTIO no se editan ni se reactivan: se crea uno nuevo si corresponde. Confirmar un turno no registra el ingreso al taller. La creación/sincronización de órdenes pendientes de ingreso, el ingreso real y la baja automática de ausentes se integrarán al implementar Órdenes. La vinculación con presupuestos ya está disponible mediante presupuestoId opcional.

GET /api/turnos acepta desde y hasta (ambos incluidos), clienteId, vehiculoId, estado, pagina desde 0 y tamano entre 1 y 100. Se ordena por fecha, hora de inicio e ID. Devuelve contenido, pagina, tamano, totalElementos y totalPaginas. Sin filtros incluye el historial de turnos cancelados y ausentes. La disponibilidad informa superposiciones de turnos PROGRAMADO/CONFIRMADO; no administra horarios de apertura, feriados ni recursos físicos.

En Bruno: iniciar sesión, elegir un cliente y vehículo activos con las colecciones existentes y abrir **11 - Turnos → Crear turno**. Si falta fechaTurno, el script la inicializa para mañana en Argentina; puede cambiarse manualmente. La respuesta guarda turnoId. Para probar una superposición, elegir otro vehículo y usar el mismo horario: primero Crear turno recibe el aviso; después un ADMIN ejecuta Confirmar turno simultaneo. Reprogramar usa 11:00–12:00; si ese horario está ocupado, también exige confirmarSuperposicion:true con ADMIN. Marcar no asistio devuelve 400 si todavía no terminó el horario.

Las pruebas verifican agenda, reprogramación, estados, datos inválidos, aislamiento, aprobación exclusiva de ADMIN y dos reservas simultáneas: sin autorización una recibe 409; con autorización se guardan ambas y queda registrado quién aceptó la superposición. Los datos se revierten en octano_test; las pruebas concurrentes limpian solo sus talleres y catálogo temporal.


## Presupuestos

ADMIN y RECEPCION gestionan los presupuestos de su taller. Las altas requieren clienteId y vehiculoId correspondientes al mismo titular, ambos activos, descripcion del trabajo, manoObra, tiposTrabajo y una lista items (puede estar vacía para presupuestar solo mano de obra). Los tipos son SERVICE, DISTRIBUCION y OTRO; puede seleccionarse más de uno. La fecha de emisión se toma del día actual en Argentina.

| Método | Ruta | Operación |
| --- | --- | --- |
| GET | `/api/presupuestos` | Buscar y listar resúmenes paginados. |
| GET | `/api/presupuestos/{id}` | Consultar importes, detalles y turno activo asociado. |
| POST | `/api/presupuestos` | Crear presupuesto PENDIENTE y devolver sus importes calculados. |
| PUT | `/api/presupuestos/{id}` | Editar un pendiente sin turno activo. |
| PATCH | `/api/presupuestos/{id}/estado` | Aceptar, rechazar o anular. |

Cada item contiene repuestoId opcional, descripcion, cantidad y precioUnitario. Si referencia un repuesto activo del taller, la descripción y el código se copian del catálogo; el precio usa precioPublico si no se envía uno negociado explícitamente. Para un renglón manual, omitir repuestoId y completar descripción y precio. No se admite repetir un repuesto de catálogo: aumentar la cantidad en su renglón. Hasta 200 renglones por presupuesto.

El código y la descripción, cantidades, precios unitarios y subtotales se guardan como una copia del presupuesto; cambiar el inventario después no altera un presupuesto existente. Una edición explícita de un pendiente vuelve a calcular sus renglones. Crear o aceptar presupuestos no comprueba disponibilidad, reserva ni descuenta stock: puede presupuestarse material aún no comprado. El consumo se integrará con el cierre de órdenes.

El backend calcula con BigDecimal y NUMERIC, sin confiar en totales del cliente:

- Subtotal de cada renglón = cantidad × precio unitario, redondeado a dos decimales con HALF_UP.
- totalRepuestos = suma de subtotales redondeados.
- subtotal = totalRepuestos + manoObra.
- montoIva = subtotal × porcentajeIva / 100, redondeado a dos decimales.
- total = subtotal + montoIva.

Importes admiten hasta dos decimales y cantidades hasta tres. porcentajeIva es configurable entre 0 y 100 y, si se omite o es null, toma 21 como la maqueta actual. Es el porcentaje de cálculo del presupuesto; la validación fiscal de ARCA pertenece a su integración posterior. El backend conserva centavos aunque la maqueta visual anterior redondee el IVA a pesos enteros.

Estados: PENDIENTE → ACEPTADO, RECHAZADO o ANULADO. Un aceptado o rechazado puede pasar a ANULADO. No se reabren estados finales ni se editan sus importes: crear otro presupuesto si corresponde. La anulación es lógica; conserva cabecera y detalles. Se registran usuario y fecha de última modificación y del último cambio de estado.

Para vincular un turno, enviar presupuestoId en el alta o edición de /api/turnos. Debe estar PENDIENTE o ACEPTADO y corresponder al mismo cliente y vehículo. Un presupuesto admite como máximo un turno PROGRAMADO/CONFIRMADO; la reserva no cambia automáticamente su aceptación. La consulta del presupuesto devuelve turnoId si hay uno activo. Mientras esté agendado no se edita: cancelar o desvincular el turno antes de modificar un pendiente. Rechazar o anular cancela sus turnos activos en la misma transacción, conservando los registros y sus vínculos históricos. La autorización de superposiciones sigue siendo exclusiva de ADMIN.

El listado acepta q (descripción, cliente, patente o ID), estado, clienteId, vehiculoId, desde, hasta, pagina desde 0 y tamano de 1 a 100. Devuelve resúmenes en contenido y los totales de paginación, ordenados por fecha e ID descendentes. El detalle completo se consulta por ID.

En Bruno: iniciar sesión como ADMIN o RECEPCION y abrir **12 - Presupuestos**. Elegir cliente, vehículo y repuesto activos con las colecciones existentes; Crear presupuesto guarda presupuestoId. Editar antes de aceptar o agendar. Para aceptar o rechazar, usar la operación correspondiente sobre un pendiente. Agendar presupuesto reutiliza sus IDs y guarda turnoId; fechaTurno se inicializa para mañana si falta. También hay un ejemplo de presupuesto solo de mano de obra, que no necesita repuestoId.

Las pruebas cubren cálculos y redondeos, renglones manuales, precios históricos, totales enviados por el cliente, validaciones, estados, auditoría, ausencia de consumo de stock, aislamiento por taller y vinculación/cancelación de turnos. Órdenes, comprobantes y envío real por WhatsApp siguen en sus entregas posteriores.
