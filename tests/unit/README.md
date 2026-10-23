# Pruebas unitarias de formularios

## Ejecutarlas

Desde la raíz del proyecto, después de `npm install`:

```sh
npm test
npm run test:watch
npm run test:coverage
npm test -- cliente
```

Los comandos ejecutan toda la suite, observan cambios, generan cobertura y ejecutan solo los archivos cuyo nombre contiene `cliente`, respectivamente. El reporte HTML de cobertura queda en `coverage/lcov-report/index.html`. No hace falta levantar el servidor Nuxt.

## Qué se prueba

| Archivo | Comportamientos |
| --- | --- |
| `cliente.test.js` | Campos obligatorios, nombre, documento, teléfono, email, duplicados, normalización, edición y cancelación. |
| `vehiculo.test.js` | Selección del titular, patente inválida o duplicada, año y kilometraje, alta, edición y selectores dependientes. |
| `titular.test.js` | Selección obligatoria, búsqueda que excluye titulares actuales e inactivos, cambio e historial conservando el QR. |
| `orden.test.js` | Datos obligatorios, mecánico, permisos, vista previa, confirmación y regreso a edición. |
| `repuesto.test.js` | Datos obligatorios, stock entero no negativo, costo, cálculo del precio por margen, alta y edición con compatibilidades. |
| `login.test.js` | Campos vacíos, credenciales enviadas, roles, errores, navegación y bloqueo durante una solicitud pendiente. |
| `registro.test.js` | Validaciones de cuenta, cuenta existente, registro en dos pasos, CUIT y errores al guardar. |

Esta primera suite cubre estos siete formularios principales. Todavía no cubre los formularios de agenda, presupuestos, facturación, ajustes y los auxiliares para agregar repuestos o compatibilidades.

## Cómo funciona una prueba

Jest encuentra los archivos indicados por `testMatch` en `jest.config.cjs`, ejecuta cada `test` y compara resultados mediante `expect`. `test.each` ejecuta el mismo caso con varios datos, por ejemplo diferentes campos inválidos.

Vue Test Utils monta el componente real; `jsdom` proporciona un DOM en memoria. Los transformadores convierten los archivos Vue y TypeScript para que Jest pueda ejecutarlos. No se abre un navegador real.

Ejemplo, tomado del formulario de clientes:

```js
const wrapper = mount(Cliente, { props: { open: true } })
await wrapper.get('form').trigger('submit')
expect(wrapper.get('[role="alert"]').text()).toContain('campos obligatorios')
expect(wrapper.emitted('save')).toBeUndefined()
```

1. **Preparar:** se monta el formulario abierto y vacío.
2. **Actuar:** `trigger('submit')` dispara el evento que atiende el formulario.
3. **Comprobar:** debe aparecer la validación y no debe emitirse `save`.

Si alguien elimina accidentalmente la validación, el caso falla. Para los casos válidos usamos `setValue` para completar los campos, enviamos el formulario y comprobamos los datos emitidos o el cambio en la base en memoria. Los `await` esperan las actualizaciones reactivas de Vue; `flushPromises` espera las promesas de autenticación. En las búsquedas se adelantan 300 ms con el reloj simulado de Jest para resolver el debounce sin esperar tiempo real.

## Qué se simula y por qué

`setup.js` proporciona los autoimports que normalmente inyecta Nuxt. Reemplaza el contenedor visual con Teleport por un contenedor simple y los editores hijos de repuestos por stubs. Los campos, las validaciones y los utilitarios de documentos, CUIT, catálogo y precios son reales.

`helpers.js` entrega una base reactiva nueva para cada caso. `useDatabase` y `useOwnerAccount` son mocks: controlamos sus respuestas mediante `jest.fn()`. Así podemos provocar un rechazo del login y comprobar cómo lo muestra el formulario. No se usa `localStorage`, ni se escriben cuentas o vehículos reales. `beforeEach` prepara esos datos, Jest limpia las llamadas de los mocks y Vue Test Utils desmonta los componentes al terminar cada caso.

El servicio que crea una orden está simulado: se comprueba cuándo lo llama el formulario y cómo usa la respuesta; esta suite no prueba internamente ese servicio. También se simulan la navegación y el cierre por Escape.

Estas son pruebas de componentes aislados. Enviar el evento `submit` directamente permite probar la validación JavaScript incluso si el navegador bloquearía antes el envío por `required` o `min`. No demuestra el comportamiento de la validación nativa, el foco, el diseño, Teleport ni la navegación real; esos puntos necesitan pruebas en navegador.

La cobertura mide qué líneas y ramas recorren estos casos; por sí sola no garantiza que todos los comportamientos sean correctos.

Referencias: [Jest: transformaciones](https://jestjs.io/docs/code-transformation), [vue-jest: compatibilidad y configuración](https://github.com/vuejs/vue-jest), [Vue Test Utils](https://test-utils.vuejs.org/).
