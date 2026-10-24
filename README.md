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

En otra terminal:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

El servidor utiliza el puerto `8080`. Todavía no tiene endpoints de negocio; abrir `/` devolverá 404.

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
| Presentación | `com.octano.backend.controller` | Recibir peticiones HTTP y devolver respuestas. |
| Negocio | `com.octano.backend.service` | Validar reglas del negocio y coordinar operaciones. |
| Acceso a datos | `com.octano.backend.repository` | Consultar y guardar los datos. |

Los controladores llaman a los servicios; los servicios llaman a los repositorios. Los controladores no acceden directamente a los repositorios. Las reglas del negocio no van en los controladores ni en los repositorios.

Por ejemplo, al registrar un vehículo: el controlador recibe sus datos, el servicio comprueba las reglas de registro y el repositorio lo guarda. Las clases concretas se agregarán al implementar cada funcionalidad.

El frontend conserva sus datos de demostración en `localStorage`. El backend tiene la base de Spring Boot y los paquetes de las tres capas preparados; todavía no hay conexión entre ambos ni una base de datos configurada.

La arquitectura objetivo y las funcionalidades pendientes están detalladas en [docs/arquitectura.md](docs/arquitectura.md).
