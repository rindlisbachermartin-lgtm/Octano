# Backend de Octano

El backend utiliza PostgreSQL, JPA y Flyway. Los primeros módulos son Taller y Condición de IVA, separados en Controller, Service y Repository. El frontend todavía no está conectado a estos endpoints.

## Preparación local

PostgreSQL debe estar iniciado en `localhost:5432`. Los siguientes pasos utilizan el usuario local `postgres`, confirmado para este entorno de desarrollo.

Desde `backend/`, copiar `.env.properties.example` a `.env.properties` si el archivo aún no existe y completar `DB_PASSWORD`. Está excluido de Git. En el formato properties, cada barra invertida de la contraseña debe duplicarse. No agregar comillas alrededor del valor.

```properties
DB_URL=jdbc:postgresql://localhost:5432/octano
DB_USERNAME=postgres
DB_PASSWORD=
TEST_DB_URL=jdbc:postgresql://localhost:5432/octano_test
```

También se pueden definir esas claves como variables de entorno, que tienen prioridad. Spring lee el archivo local desde el directorio de ejecución; ejecutar Maven desde `backend/`.

El script de preparación usa el cliente de PostgreSQL 18 instalado en Windows y crea las dos bases si no existen. No borra ni reinicia bases existentes:

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

Para comprobarlo desde pgAdmin o DBeaver, conectado a la base `octano` después de arrancar la aplicación:

```sql
SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'octano';
SELECT version, description, success FROM public.flyway_schema_history ORDER BY installed_rank;
SELECT * FROM octano.condicion_iva ORDER BY nombre;
SELECT * FROM octano.taller ORDER BY id;
```

Las pruebas de integración consultan PostgreSQL real: comprueban las migraciones y JPA, recorren los endpoints, prueban validaciones y verifican la clave foránea. Las pruebas de alta y edición se revierten al terminar cada caso, dentro de `octano_test`. No modifican datos de la aplicación.

## Endpoints disponibles

Dirección local: `http://127.0.0.1:8080`. La autenticación se implementará con el módulo de usuarios; por ahora el servidor escucha solo en esta computadora.

| Método | Ruta | Operación |
| --- | --- | --- |
| GET | `/api/condiciones-iva` | Consultar el catálogo ordenado por nombre. |
| GET | `/api/condiciones-iva/{id}` | Consultar una condición. |
| GET | `/api/talleres` | Listar los talleres por ID. |
| GET | `/api/talleres/{id}` | Consultar un taller. |
| POST | `/api/talleres` | Crear un taller; devuelve 201 y su dirección en Location. |
| PUT | `/api/talleres/{id}` | Actualizar todos sus datos. |

Ejemplo ficticio para POST o PUT; elegir `condicionIvaId` desde el catálogo:

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

`ingresosBrutos` es opcional; los demás campos son obligatorios. CUIT, teléfono e Ingresos Brutos se guardan como texto, preservando ceros iniciales. El CUIT se normaliza sin espacios ni guiones y se controla su dígito verificador; esto no consulta el padrón fiscal. `inicioActividades` no admite fechas futuras y `altura` admite cero pero no negativos ni decimales. La respuesta incluye una condición de IVA con `id` y `nombre`, sin exponer entidades JPA.

Los errores usan ProblemDetail: 400 para datos inválidos (con `errores` por campo cuando falla Bean Validation), 404 para recursos inexistentes y 409 para conflictos de integridad. El catálogo de IVA no tiene endpoints de escritura y no se agregó eliminación de talleres.

## Decisiones

- Flyway modifica el esquema; Hibernate usa `ddl-auto=validate`, sin crear o alterar tablas automáticamente.
- `open-in-view=false`: el acceso a datos de los futuros endpoints quedará dentro de los servicios.
- Las versiones de las dependencias quedan administradas por el BOM de Spring Boot para mantener compatibilidad.
- JPA no reemplaza las tres capas: Controller → Service → Repository → PostgreSQL.
- Después de aplicar una migración, no modificarla: crear una nueva versión.
- El puerto HTTP es `8080`. Abrir `/` devuelve 404; utilizar las rutas `/api/...` de la tabla anterior.

Referencias: [inicialización de bases en Spring Boot](https://docs.spring.io/spring-boot/how-to/data-initialization.html) y [plan del backend](../docs/inicio-backend.md).
