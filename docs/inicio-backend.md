# Inicio del backend

El proyecto tiene Spring Boot, Maven Wrapper, los paquetes `controller`, `service` y `repository`, y la configuración de PostgreSQL, JPA y Flyway. Taller y Condición de IVA ya tienen tablas, entidades, servicios y endpoints, con pruebas de integración. La preparación, los comandos y las rutas están en [backend/README.md](../backend/README.md). El frontend sigue funcionando con datos locales de demostración.

## Criterios para implementar el backend

Se aplicará la skill ponytail: implementar lo necesario con las herramientas existentes, manteniendo validaciones, seguridad y pruebas de las reglas del negocio.

- Mantener las tres capas: Controller recibe HTTP, Service coordina reglas y transacciones, Repository consulta PostgreSQL.
- Utilizar el patrón Repository mediante Spring Data JPA. Spring genera la implementación del repositorio; no agregar un DAO que repita sus operaciones.
- Usar servicios concretos con dependencias recibidas por constructor. Crear interfaces propias cuando haya una necesidad real de intercambiar implementaciones.
- Separar las entidades JPA de los DTOs de entrada y salida. Empezar con conversiones sencillas, sin incorporar una librería de mapeo para unos pocos campos.
- Validar entradas y reforzar la integridad con claves foráneas y restricciones de la base. Delimitar con `@Transactional` las operaciones que deban completarse juntas.
- Mantener credenciales fuera del código, migraciones con Flyway y pruebas que verifiquen reglas y persistencia. Los servicios compartidos no deben guardar datos de una petición en campos de instancia.
- Incorporar Strategy solo si aparecen varias políticas reales para una misma operación, por ejemplo distintas formas de calcular comisiones. Mientras haya una sola regla, usar un método sencillo.

Estas decisiones no agregan capas a la arquitectura ni requieren implementar patrones adicionales para cada entidad.

## Primer objetivo: Clientes

Completar un módulo pequeño de punta a punta antes de implementar las órdenes de trabajo:

1. Revisar el modelo proporcionado por el usuario en [modelo-dominio.puml](modelo-dominio.puml), contrastarlo con `modelo-de-datos.md` y acordar los campos de Cliente y su relación con Vehículo. El PlantUML es un modelo de referencia; aún hay que definir tipos, claves y restricciones antes de crear las migraciones.
2. Configurar PostgreSQL para desarrollo con credenciales en variables de entorno. Incorporar Spring Data JPA, el controlador de PostgreSQL, Bean Validation y Flyway. Verificar versiones estables compatibles al agregarlos.
3. Crear la primera migración SQL, con claves y restricciones. Evitar que Hibernate modifique automáticamente el esquema; usarlo para validar las migraciones.
4. Implementar entidad, repositorio, servicio, DTOs y controlador de Clientes. El servicio aplica las reglas y las transacciones; el controlador recibe y responde JSON; el repositorio accede a PostgreSQL.
5. Agregar validaciones de entrada y respuestas de error consistentes: 400 para datos inválidos, 404 para registros inexistentes y 409 para conflictos de unicidad definidos en el modelo.
6. Probar las reglas del servicio y el guardado real en PostgreSQL. Confirmar que los datos permanecen al reiniciar el backend.
7. Conectar el formulario y el listado de Clientes del frontend a la API. Quitar el almacenamiento local de ese módulo cuando el recorrido completo esté verificado.

Endpoints propuestos:

| Método | Ruta | Operación |
| --- | --- | --- |
| GET | `/api/clientes` | Listado con búsqueda y paginación. |
| GET | `/api/clientes/{id}` | Consulta de un cliente. |
| POST | `/api/clientes` | Alta. |
| PUT | `/api/clientes/{id}` | Actualización. |
| PATCH | `/api/clientes/{id}/estado` | Activar o desactivar conservando el historial. |

## Recorrido de una operación

```text
Formulario Nuxt → ClienteController → ClienteService → ClienteRepository → PostgreSQL
```

Las tres capas son presentación, negocio y persistencia. Las entidades y los DTOs son clases de apoyo, no capas adicionales. Los controladores no llaman directamente a los repositorios y las entidades JPA no se devuelven directamente como respuestas.

## Orden de los módulos siguientes

Después de Clientes, implementar Vehículos con su titular, patente única y catálogo propio. Luego incorporar autenticación y roles con Spring Security antes de habilitar el acceso compartido. Continuar con Repuestos y stock, Turnos, Órdenes de trabajo y finalmente presupuestos y facturación. QR, WhatsApp y ARCA se incorporan cuando los módulos que los utilizan ya funcionan.

Cada módulo debe incluir base de datos, reglas, API, pruebas y conexión con el frontend. La primera entrega se considera terminada cuando se puede crear, listar, editar y desactivar un cliente desde la interfaz y los cambios sobreviven a un reinicio.
