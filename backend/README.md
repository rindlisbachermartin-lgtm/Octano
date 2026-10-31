# Backend de Octano

El backend utiliza PostgreSQL, JDBC y Flyway, sin ORM. Están implementados Taller, Condición de IVA, Usuarios con autenticación JWT, Mecánicos, Clientes, catálogo y Vehículos, separados en Controller, Service y Repository. Se trabaja un módulo por entrega; la conexión con el frontend queda para después de completar el backend interno.

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
