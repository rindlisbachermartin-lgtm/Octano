# Octano

Sistema de gestión de talleres mecánicos, separado en dos proyectos:

```text
frontend/   Nuxt 4 + Vue 3, recursos, dependencias y pruebas del frontend
backend/    Java 27 + Spring Boot 4.1.2, con Maven Wrapper
docs/       Documentación y modelo de datos
```

## Frontend

```powershell
cd frontend
npm install
npm run dev
```

Disponible en `http://localhost:3000`. Desde esa misma carpeta:

```powershell
npm test
npm run test:security
npm run build
```

Los tests de Jest están en `frontend/tests/unit/`. Los demás están en `frontend/tests/otros/`.
Más información en [frontend/README.md](frontend/README.md).

## Backend

Requiere un JDK 27. Maven Wrapper descarga la versión de Maven definida por el proyecto, por lo que no hace falta instalar Maven por separado. `JAVA_HOME` debe apuntar al JDK.

Antes de arrancar, configurar la contraseña local y crear las bases PostgreSQL siguiendo [backend/README.md](backend/README.md). La aplicación utiliza `octano`; las pruebas de integración utilizan `octano_test`. JPA y Flyway ya están incorporados.

En otra terminal:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

El servidor utiliza el puerto `8080` en `127.0.0.1`. Ya están disponibles `/api/talleres`, `/api/condiciones-iva`, `/api/auth` y `/api/usuarios`; abrir `/` devolverá 404. Los métodos y ejemplos están en [backend/README.md](backend/README.md).

Para ejecutar la prueba de arranque y generar el JAR:

```powershell
.\mvnw.cmd verify
```

En Linux o macOS se utiliza `./mvnw` en lugar de `.\mvnw.cmd`.

## Arquitectura de tres capas

El backend sigue este recorrido:

```text
Frontend → Controller → Service → Repository → Base de datos
```

| Capa | Paquete Java | Responsabilidad |
| --- | --- | --- |
| Presentación | `com.octano.backend.<modulo>.controller` | Recibir peticiones HTTP y devolver respuestas. |
| Negocio | `com.octano.backend.<modulo>.service` | Validar reglas del negocio y coordinar operaciones. |
| Acceso a datos | `com.octano.backend.<modulo>.repository` | Consultar y guardar los datos. |

Los controladores llaman a los servicios; los servicios llaman a los repositorios. Los controladores no acceden directamente a los repositorios. Las reglas del negocio no van en los controladores ni en los repositorios.

Los paquetes se agrupan por funcionalidad: `talleres`, `usuarios`, `autenticacion` y `condicionesiva`. Por ejemplo, `talleres/controller`, `talleres/service` y `talleres/repository` reúnen las capas de Taller. Las respuestas utilizan DTOs. POST `/api/talleres` crea el taller y su administrador mediante el servicio de registro.

El frontend conserva sus datos de demostración en `localStorage`. Los selectores de vehículos usan el catálogo local de la maqueta. Los módulos todavía no están conectados al backend. El backend tiene conexión a PostgreSQL y migraciones para Taller, Condición de IVA y Usuarios. El acceso usa JWT y roles, con aislamiento por taller. Las entregas se realizan de un módulo a la vez.

El plan para empezar la API está en [docs/inicio-backend.md](docs/inicio-backend.md).

La arquitectura objetivo y las funcionalidades pendientes están detalladas en [docs/arquitectura.md](docs/arquitectura.md).
