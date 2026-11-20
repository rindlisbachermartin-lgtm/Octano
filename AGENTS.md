# Guía para agentes — Octano

Estas instrucciones aplican a todo el repositorio. Respetar las instrucciones del usuario y cualquier `AGENTS.md` más específico dentro del módulo que se esté modificando.

## Proyecto y alcance

Octano es un sistema de gestión de talleres mecánicos. La implementación se entrega por módulos, con frontend y backend en el mismo repositorio.

- `frontend/`: Nuxt 4, Vue 3, TypeScript y Nuxt UI. Componentes en `app/components/`, páginas en `app/pages/`, estado y consultas en `app/composables/`, utilidades en `app/utils/`.
- `backend/`: Java 27, Spring Boot, Maven Wrapper, PostgreSQL, JDBC y Flyway. Paquete raíz: `com.octano.backend`.
- `docs/`: reglas de negocio, recorridos y documentación de los módulos.

Consultar `README.md`, los README de cada aplicación y la documentación del módulo antes de modificarlo. Las versiones y comandos efectivos están en `frontend/package.json` y `backend/pom.xml`. Algunos documentos describen objetivos pendientes: comprobar el código antes de asumir que una migración o integración ya existe.

## Forma de trabajar

- Comunicar avances y resultados en español, de forma breve y concreta.
- Revisar `git status` y la rama actual antes de editar. Conservar los cambios previos del usuario.
- Seguir el recorrido completo de los datos antes de elegir la solución: pantalla, petición, controlador, servicio, repositorio y persistencia.
- Aplicar ponytail: reutilizar el código existente, preferir funciones y herramientas nativas, evitar dependencias y abstracciones sin una necesidad concreta. Consultar `.agents/skills/ponytail/SKILL.md` cuando esté disponible.
- No simplificar validaciones, permisos, accesibilidad ni manejo de errores que evite pérdida de datos.
- Mantener el cambio dentro del módulo solicitado. No incorporar refactorizaciones generales ni migraciones de librerías como parte de una funcionalidad puntual.
- Usar la rama indicada por el usuario. La convención del proyecto para entregas es `feature/<modulo>`. No integrar ramas, hacer commit o push salvo que se solicite.
- No descartar cambios, reiniciar bases ni borrar datos para resolver un problema de desarrollo.

## Backend

Recorrido obligatorio: `Controller → Service → Repository → PostgreSQL`.

- Organizar por funcionalidad, con paquetes internos `controller`, `service`, `repository`, `dto` y `entity` según corresponda.
- Los controladores reciben y validan DTOs y delegan en servicios; no acceden directamente a repositorios.
- Los servicios contienen las reglas de negocio, reciben dependencias por constructor y delimitan operaciones indivisibles con `@Transactional`.
- Los repositorios usan `JdbcTemplate`, SQL explícito y conversión de filas. No introducir ORM ni JPA.
- Usar DTOs de respuesta y el manejo común de errores con `ProblemDetail`.
- Calcular importes en el servidor con `BigDecimal` y el criterio de redondeo del módulo. Conservar precios históricos.
- Aislar lecturas y escrituras por el taller de la sesión. No confiar en un `tallerId`, rol o mecánico enviado por el cliente para autorizar una operación.
- Respetar los permisos de `ADMIN`, `RECEPCION`, `MECANICO` y la representación de dueño existente. Un mecánico solo accede a sus órdenes asignadas.
- Confirmar juntos los cambios relacionados: consumo y stock, cierre y kilometraje, estados de turno y orden. Mantener las restricciones y bloqueos que evitan operaciones duplicadas o saldos inconsistentes.
- Agregar migraciones nuevas y consecutivas en `backend/src/main/resources/db/migration/`. No modificar migraciones ya aplicadas ni crear tablas manualmente fuera de Flyway.

## Frontend e integración

- Reutilizar componentes, composables, estilos y patrones de los módulos cercanos. Conservar la interfaz responsive, etiquetas accesibles y funcionamiento por teclado.
- Usar Composition API y `<script setup lang="ts">` siguiendo las convenciones existentes.
- Las peticiones del navegador usan `/api/...`. El proxy está en `frontend/server/api/[...path].ts`: habilitar explícitamente las rutas y métodos necesarios al conectar un endpoint.
- Obtener la autorización desde `useAuthApi`; no duplicar el almacenamiento o la restauración de sesiones.
- Distinguir la sesión real de la demo. `useDatabase` mantiene datos locales de demostración; no usar esos datos como reemplazo cuando una petición autenticada falle.
- Las cuentas guardadas por la demo anterior en el navegador no son cuentas de PostgreSQL. No crear usuarios ni cambiar contraseñas automáticamente para resolver un rechazo de credenciales.
- Mostrar estados de carga, errores accionables y resultados vacíos. Bloquear envíos duplicados, conservar los datos de formularios ante un error y descartar respuestas de búsquedas obsoletas.
- Tomar estados, totales y movimientos confirmados de la respuesta de la API. No simular localmente una escritura que el servidor rechazó.
- Descargar fotos privadas con autenticación; liberar las URLs de blobs al dejar de usarlas.
- No presentar como activas integraciones de ARCA o WhatsApp que sigan siendo de demostración.

## Entorno local

Usar PowerShell en Windows. Ejecutar los comandos desde la carpeta indicada.

Frontend, desde `frontend/`:

```powershell
npm install                  # Solo si faltan dependencias o cambió el lockfile
npm run dev                  # http://localhost:3000
```

Backend, desde `backend/`:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-27'
.\mvnw.cmd spring-boot:run    # http://127.0.0.1:8080
```

`JAVA_HOME` debe apuntar a la raíz del JDK, sin `/bin`. Usar Maven Wrapper. La base de la aplicación es `octano`; la de integración es `octano_test`. La preparación está en `backend/README.md`. El proxy usa `NUXT_BACKEND_URL` si se necesita otra dirección de backend.

Las credenciales se configuran en variables de entorno o en `backend/.env.properties`, excluido de Git. No imprimir, copiar a documentación ni versionar contraseñas, hashes de contraseñas, JWT, claves o archivos de credenciales.

## Verificación y entrega

Ejecutar las comprobaciones pertinentes al cambio y dejar una prueba reproducible para lógica de negocio nueva o corregida. No agregar pruebas que solo repitan detalles triviales de implementación.

Desde `frontend/`:

```powershell
npm test -- --runTestsByPath tests/unit/<archivo>.test.js
npm run build
npm run test:security        # Si se modifican dependencias o parches de vendor
```

Jest ejecuta `tests/unit/`. Los archivos de `tests/otros/` tienen ejecutores independientes; consultar cada uno antes de ejecutarlo. Las pruebas de componentes deben proporcionar los mocks de autoimports de Nuxt y autenticación que requieran.

Desde `backend/`, con el JDK configurado:

```powershell
.\mvnw.cmd '-Dtest=NombreDeLaClaseTests' test
.\mvnw.cmd verify           # Verificación completa cuando el alcance lo justifique
```

Las pruebas de integración requieren PostgreSQL y deben usar exclusivamente `octano_test`. No apuntarlas a la base de la aplicación. Los datos temporales deben quedar revertidos o limpiarse limitándose a los creados por la prueba.

Antes de entregar, revisar `git diff --check` y el diff de los archivos modificados. Actualizar la documentación del módulo si cambian su contrato o su recorrido. Informar qué se implementó, qué se verificó y cualquier limitación real. Si hay fallos previos o un impedimento del entorno, distinguirlos de las regresiones del cambio; no afirmar que una comprobación pasó si no se ejecutó correctamente.
