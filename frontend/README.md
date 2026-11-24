# Octano

Frontend del sistema de gestión de talleres mecánicos en **Nuxt 4 + Vue 3**, conectado a la API de Octano.

## Iniciar

Ejecutar los siguientes comandos desde la carpeta `frontend/`.

```sh
npm install
npm run dev
```

```sh
npm run build
npm run preview
```

## Verificación

La compilación se comprueba con `npm run build`. Las pruebas unitarias del frontend usan Jest y Vue Test Utils:

```sh
npm test
npm run test:watch
npm run test:coverage
```

Jest ejecuta `tests/unit/**/*.test.js`. Los tests de `tests/otros/` se ejecutan con Node. Las pruebas de la antigua base local fueron retiradas; los fixtures que necesitan las pruebas API se mantienen únicamente dentro de `tests/`.

## Recorrido

- Dashboard con totales operativos, órdenes en proceso y espera, turnos pendientes del día y alertas de stock. Administración también consulta cobros del mes y saldos pendientes.
- Agenda diaria y semanal: crear, editar, cancelar y registrar ingreso de turnos, con validación de disponibilidad en el servidor.
- Clientes: alta, edición, búsqueda y archivo lógico.
- Vehículos: registro, búsqueda, ficha técnica y QR con historial de servicios finalizados.
- Órdenes: creación, asignación de mecánico y puesto, checklist, diagnóstico, fotos, observaciones, consumo de repuestos compatibles, finalización y entrega.
- Inventario: búsqueda, compatibilidad según el catálogo real, filtros de stock, altas y movimientos registrados en el servidor.
- Presupuestos: cotización y conversión a orden.
- Facturación: comprobantes internos conectados a la API, pagos parciales o totales, saldos y PDF sin validez fiscal.

## Alcance

El registro, el inicio y el cierre de sesión usan la API de Octano. Para ingresar con una cuenta real, iniciá PostgreSQL y ejecutá `npm run backend` desde la raíz de este repositorio. En otra terminal, ejecutá `npm run dev` en `frontend/`. Nuxt llama al backend en `http://127.0.0.1:8080`. Si la dirección cambia, configurá `NUXT_BACKEND_URL` en el proceso de Nuxt. Para desplegar autenticación se necesita el servidor de Nuxt (`npm run build`), ya que reenvía las solicitudes a Spring Boot.

El ingreso es exclusivamente con una cuenta real; el acceso con datos locales de demostración fue eliminado. La navegación se muestra según el rol del usuario. Facturación usa los endpoints `/api/comprobantes` y `/api/cobros` del backend: genera facturas A/B/C simuladas desde órdenes finalizadas, permite pagos parciales y consulta saldos. El CAE es fijo y simulado; los documentos no tienen validez fiscal ni se integran con ARCA.

Órdenes de trabajo usa la API para listar, crear, planificar, registrar ingreso, iniciar, gestionar tareas, materiales y fotos, finalizar y cancelar. El mecánico consulta y trabaja sobre sus órdenes desde `/mecanico`. Ver [el recorrido y sus pruebas](../docs/ordenes-trabajo.md).

No hay base local, usuarios de demostración ni alternativas de guardado en el navegador. Los módulos consultan y modifican datos exclusivamente mediante la API. Las fotos se envían al servidor y se consultan con autorización. El dashboard usa los totales de paginación del servidor, consulta las secciones en paralelo y distingue errores de respuestas vacías. Se actualiza automáticamente cuando se confirman cambios en órdenes, turnos, stock o cobros, al volver a la ventana y al cambiar el día del taller.

El porcentaje de IVA ya no se escribe a mano: se elige de `GET /api/aliquotas-iva`, que lee la tabla `octano.aliquota_iva` (0%, 10,5% y 21%). El mismo selector se usa en órdenes, presupuestos y comprobantes; si un registro histórico tuviera otro valor, se conserva como opción adicional sin permitir cargar uno nuevo.

El resumen financiero usa `GET /api/reportes/resumen`, habilitado en el proxy únicamente para lectura. Incluye `ingresosPorConcepto`, el desglose de lo efectivamente cobrado entre repuestos y trabajos (mano de obra con IVA prorrateado), que el dashboard compara con barras y porcentajes. Solo se solicita para administración; recepción ve la operación sin consultar reportes privados. El filtro de stock del dashboard abre `/inventario?stockBajo=1`; los repuestos sin existencias se muestran como “Sin stock” en rojo, sin un cero ni un badge. Las listas iniciales muestran hasta cinco elementos por estado, con enlaces a los módulos completos. Pruebas: `npm test -- --runTestsByPath tests/unit/dashboard.test.js tests/unit/ordenProxy.test.js`.

Con una cuenta real, Códigos QR está conectado a la API: genera lotes de 16 etiquetas, prepara el PDF, registra impresiones y permite vincular o desvincular vehículos. Desde Vehículos también se puede asignar o reemplazar un QR. La ficha `/qr/{codigo}` consulta datos públicos del servidor sin iniciar sesión y funciona entre dispositivos cuando `PUBLIC_FRONTEND_URL` está configurada con una dirección accesible. Recepción tiene acceso de consulta. Ver [el recorrido de QR y sus pruebas](../docs/codigos-qr.md).

El almacenamiento local se limita a preferencias de tema y vista; la sesión API utiliza `sessionStorage`. Los datos de prueba viven exclusivamente en `tests/`.

## Diseño

Identidad original con plano del taller, ilustraciones vectoriales propias de vehículos, tipografía Manrope / DM Sans, tonos slate modernos y acentos azul tech royal. Tipografías de Google Fonts, con respaldo sans-serif local. Iconos Lucide. Microinteracciones CSS, modales nativos con foco contenido y Escape, estados de pulsación, soporte de movimiento reducido y adaptación móvil.
