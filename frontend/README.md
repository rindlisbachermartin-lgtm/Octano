# Octano

Maqueta interactiva del sistema de gestión de talleres mecánicos. Frontend en **Nuxt 4 + Vue 3**, con datos de ejemplo persistidos en `localStorage`.

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

Los casos están comentados en `tests/unit/`. Jest ejecuta únicamente `tests/unit/**/*.test.js`. Los demás tests están juntos en `tests/otros/` y mantienen sus ejecutores independientes: Node para los archivos `.mjs` y Playwright para `workshop.spec.js` (todavía sin dependencias de ejecución configuradas).

## Recorrido

- Resumen con indicadores, plano interactivo de cuatro puestos, agenda y alertas de stock.
- Agenda diaria: crear, confirmar, cancelar y registrar ingreso de turnos; evita reservas simultáneas.
- Clientes: alta, edición, búsqueda y archivo lógico.
- Vehículos: registro, búsqueda, ficha técnica y QR con historial de servicios finalizados.
- Órdenes: creación, asignación de mecánico y puesto, checklist, diagnóstico, fotos, observaciones, consumo de repuestos compatibles, finalización y entrega.
- Inventario: búsqueda, compatibilidad por vehículo de ejemplo, alertas, altas y reposición rápida de cinco unidades.
- Presupuestos: cotización y conversión a orden.
- Facturación: comprobantes de demostración y registro de cobro.
- Reportes: indicadores y exportación CSV.

## Alcance

El registro, el inicio y el cierre de sesión usan la API de Octano. Para ingresar con una cuenta real, iniciá Spring Boot y PostgreSQL desde el checkout de `backend` y ejecutá `npm run dev` en este frontend. Nuxt llama al backend en `http://127.0.0.1:8080`. Si la dirección cambia, configurá `NUXT_BACKEND_URL` en el proceso de Nuxt. Para desplegar autenticación se necesita el servidor de Nuxt (`npm run build`), ya que reenvía las solicitudes a Spring Boot.

La opción «Explorar la demo» abre los módulos con datos locales. Las cuentas reales acceden a la navegación según su rol. Las pantallas de operación, ARCA y WhatsApp se conectarán en las siguientes entregas. Los comprobantes de demostración no tienen validez fiscal.

La fecha de referencia de la demostración es el 7 de septiembre de 2026. Los gráficos e indicadores corresponden al conjunto de datos de la demo. Las imágenes de peritaje se almacenan en el navegador (máximo seis de 2 MB por orden, sujeto a la cuota de almacenamiento). El QR necesita los datos del mismo navegador: compartir historiales entre dispositivos requiere un backend.

Para reiniciar los ejemplos, eliminar la clave `octano-v1` de localStorage desde las herramientas del navegador.

## Diseño

Identidad original con plano del taller, ilustraciones vectoriales propias de vehículos, tipografía Manrope / DM Sans, tonos slate modernos y acentos azul tech royal. Tipografías de Google Fonts, con respaldo sans-serif local. Iconos Lucide. Microinteracciones CSS, modales nativos con foco contenido y Escape, estados de pulsación, soporte de movimiento reducido y adaptación móvil.
