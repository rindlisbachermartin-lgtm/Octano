# Plan de entregas del backend

Primero se completa la operación interna del backend. Después se conecta el frontend. ARCA y WhatsApp quedan para una etapa posterior, según lo acordado.

## Un módulo por entrega

Se implementa y verifica un módulo, se deja listo para que el usuario haga commit y push, y se continúa con el siguiente después. No mezclar módulos pendientes en el mismo cambio.

- [x] Conexión PostgreSQL, JPA y Flyway.
- [x] Taller y Condición de IVA.
- [x] Usuarios y autenticación: registro, login, JWT, roles, cambio de contraseña y bajas.
- [ ] Gestión de Mecánicos.
- [ ] Clientes: alta, búsqueda, edición y bajas lógicas.
- [ ] Catálogo propio de Marca, Modelo y Versión.
- [ ] Vehículos e historial de titularidad.
- [ ] Repuestos, compatibilidades y movimientos de stock.
- [ ] Turnos y disponibilidad.
- [ ] Presupuestos y cálculo de importes.
- [ ] Órdenes de trabajo, tareas, consumos y cierre transaccional.
- [ ] Comprobantes y cobros internos, reportes y comisiones.
- [ ] QR y consulta pública del vehículo.
- [ ] Conexión del frontend, una vez terminada y probada la operación interna.

Los módulos que aún no se entregaron no forman parte de los endpoints actuales. El modelo de referencia es [modelo-dominio.puml](modelo-dominio.puml). Las rutas implementadas y la preparación local están en [backend/README.md](../backend/README.md).

## Criterios de implementación

Se aplica ponytail: código sencillo, reutilización de herramientas existentes y patrones cuando resuelven una necesidad real, manteniendo validaciones, seguridad y pruebas de negocio.

El recorrido es Controller → Service → Repository → PostgreSQL. Las entidades JPA no se exponen directamente: se utilizan DTOs. Los servicios reciben dependencias por constructor, aplican las reglas y delimitan las transacciones. Spring Data genera los repositorios.

Flyway administra las migraciones; Hibernate valida el esquema. No modificar migraciones ya aplicadas. Las credenciales y la clave JWT se mantienen fuera de Git. Las pruebas de integración usan una base separada.

Usuarios se guarda únicamente con el conjunto de atributos acordado, más los datos técnicos necesarios para el hash, estado y revocación de tokens. La tabla base de mecánicos permite validar el vínculo de las cuentas con ese rol; su CRUD pertenece a la siguiente entrega.

Los módulos se organizan por paquetes de funcionalidad, cada uno con sus capas internas. Ver la estructura y el listado completo de operaciones en [backend/README.md](../backend/README.md).
