# Arquitectura del sistema

Octano sigue una arquitectura de tres capas, separada mediante un modelo cliente-servidor. El frontend se comunicará con el backend mediante una API REST con respuestas JSON.

## Presentación

El frontend vive en `frontend/` y utiliza Nuxt y Vue. Se usarán las últimas versiones estables compatibles, sin limitar Nuxt a la versión 3.

El objetivo es una SPA modular, con PrimeVue para los componentes, Pinia para el estado compartido y `$fetch` para las consultas HTTP al backend. La interfaz existente utiliza Nuxt UI; la migración de componentes a PrimeVue y del estado a Pinia todavía no está realizada.

En el backend, los paquetes `controller` representan la entrada HTTP. Reciben DTOs, validan el formato de los datos y devuelven respuestas. Delegan las operaciones en los servicios.

## Negocio

El backend vive en `backend/` y utiliza Java 27, Spring Boot 4.1.2 y Spring Web. Se priorizan las últimas versiones estables frente a las versiones Java 21 y Spring Boot 3 del texto inicial.

El paquete `service` concentrará las reglas de órdenes, turnos, presupuestos, mano de obra, comisiones, stock y facturación. Las operaciones indivisibles se delimitarán mediante `@Transactional`, por ejemplo para guardar el consumo de repuestos junto con el descuento de stock.

La autenticación prevista utiliza Spring Security y JWT, con los roles `ADMIN`, `RECEPCION` y `MECANICO`. Las integraciones con QR mediante ZXing, WhatsApp y ARCA se aislarán en un paquete de integraciones, sin colocar llamadas externas en los controladores o repositorios.

## Persistencia

El paquete `repository` se encargará del acceso a datos mediante Spring Data JPA e Hibernate. PostgreSQL será el motor de base de datos. Las entidades representarán el modelo persistente; los DTOs serán los datos de entrada y salida de la API.

Los repositorios no recibirán peticiones HTTP ni contendrán las reglas del taller. Las restricciones de la base y las transacciones del servicio se complementarán para mantener la consistencia.

## Dependencias entre capas

```text
Frontend → Controller → Service → Repository → PostgreSQL
```

Los controladores no accederán directamente a los repositorios. La cantidad de carpetas auxiliares, como DTOs o entidades, no cambia las tres responsabilidades principales.

## Estado actual

Está realizada la separación en `frontend/` y `backend/`, la base ejecutable de Spring Boot y los paquetes de las tres capas. El frontend conserva la maqueta y sus datos locales.

No hay integraciones externas activas. Los selectores de vehículos utilizan el catálogo local de demostración. El plan de implementación está en [inicio-backend.md](inicio-backend.md).

El backend incorpora PostgreSQL, JPA y Flyway, con Hibernate en modo de validación y migraciones para el esquema `octano`, Taller y Condición de IVA. Estos dos módulos ya recorren Controller → Service → Repository, usan DTOs y manejan errores con ProblemDetail. Las credenciales se configuran localmente y las pruebas de integración utilizan una base separada. Los pasos y endpoints están en [backend/README.md](../backend/README.md).

Los módulos de Clientes, Usuarios y Vehículos y el resto de la operación del taller, JWT, PrimeVue, Pinia y las integraciones de QR, WhatsApp y ARCA siguen pendientes. Las versiones de las dependencias se fijan mediante el BOM de Spring Boot cuando corresponde y se verifican al incorporarlas.
