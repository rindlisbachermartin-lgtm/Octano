# Octano

Maqueta interactiva del sistema de gestión de talleres mecánicos. Frontend en **Nuxt 4 + Vue 3**, con datos de ejemplo persistidos en `localStorage`.

## Iniciar

```sh
npm install
npm run dev
```

```sh
npm run build
npm run preview
```

## Verificación

La compilación se comprueba con `npm run build`. El archivo `tests/workshop.spec.js` pertenece a una versión anterior y todavía no tiene dependencias ni un script de ejecución configurados.

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

Sin backend, autenticación real, integración ARCA ni envíos de WhatsApp. Los comprobantes no tienen validez fiscal. Los avisos son simulados. La matriz de compatibilidad se representa con los vehículos de ejemplo, no como un catálogo automotor completo. Los presupuestos usan importes agregados de mano de obra y materiales. No se generan PDFs fiscales.

La fecha de referencia de la demostración es el 7 de septiembre de 2026. Los gráficos e indicadores corresponden al conjunto de datos de la demo. Las imágenes de peritaje se almacenan en el navegador (máximo seis de 2 MB por orden, sujeto a la cuota de almacenamiento). El QR necesita los datos del mismo navegador: compartir historiales entre dispositivos requiere un backend.

Para reiniciar los ejemplos, eliminar la clave `octano-v1` de localStorage desde las herramientas del navegador.

## Diseño

Identidad original con plano del taller, ilustraciones vectoriales propias de vehículos, tipografía Manrope / DM Sans, tonos slate modernos y acentos azul tech royal. Tipografías de Google Fonts, con respaldo sans-serif local. Iconos Lucide. Microinteracciones CSS, modales nativos con foco contenido y Escape, estados de pulsación, soporte de movimiento reducido y adaptación móvil.
