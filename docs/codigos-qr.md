# Códigos QR y ficha pública

El backend respeta el recorrido de `codigos-qr.vue`, `ModalVincularQr.vue` y `/qr/[code]` del frontend: generar 16 etiquetas, preparar una hoja, marcarla como impresa y vincular cada código a un vehículo. Se organiza en `codigosqr/controller`, `service`, `repository` y `dto`, con PostgreSQL/JDBC. V14 crea `octano.codigo_qr`. La generación de imágenes se aísla en `integraciones/qr/GeneradorQr`, usando [ZXing 3.5.4](https://github.com/zxing/zxing/releases/tag/zxing-3.5.4).

## Reglas que coinciden con el frontend

- Cada lote contiene exactamente 16 códigos disponibles y sin vehículo asignado.
- Escribir un código en la asignación solo busca uno existente; no crea códigos nuevos.
- Un código asignado no se puede asignar nuevamente, ni siquiera al mismo vehículo.
- Cada vehículo tiene como máximo un QR. Al asignarle uno nuevo, el anterior queda inutilizado y pierde su fecha de asignación. Conserva la impresión previa.
- Desvincular inutiliza la etiqueta definitivamente; no borra el código ni la marca de impresión.
- La hoja requiere exactamente 16 códigos distintos sin imprimir. Prepararla no los marca automáticamente; se marcan después de imprimir, mediante una operación explícita.
- Un código marcado como impreso no puede volver a incluirse en una plantilla. Las imágenes PNG siguen disponibles para vista previa; esta regla no impide copiar una imagen o un PDF ya descargados.
- Los estados Disponible/Asignado/Inutilizado se derivan del vínculo y de la fecha de baja definitiva.
- Un QR libre muestra pendiente de asignación. Uno inexistente devuelve 404. Uno asignado permite consultar su ficha sin iniciar sesión.

El backend genera identificadores aleatorios `OCT-` + 32 caracteres hexadecimales, dentro del formato de 3 a 40 caracteres admitido por el formulario. Se reemplaza la numeración correlativa de la maqueta para que conocer una etiqueta no permita adivinar el enlace de otro vehículo. Los códigos se normalizan a mayúsculas al consultarlos o asignarlos.

La gestión corresponde a ADMIN, como al administrador del frontend. RECEPCION puede listar, consultar y obtener vistas previas. MECANICO no administra etiquetas. La asignación requiere un vehículo activo del mismo taller. Un vehículo inactivo que ya tenía QR conserva su ficha e historial; una transferencia mantiene el QR asociado al vehículo.

La relación tiene una sola fuente: `codigo_qr.vehiculo_id`. Vehículos devuelve `codigoQr` calculado con un JOIN, tanto en la lista como en el detalle. Asignar, reemplazar o desvincular guarda el usuario y la fecha de modificación también en el vehículo. La impresión registra su propio usuario y fecha.

## Enlace que se graba

Configurar `PUBLIC_FRONTEND_URL` en `backend/.env.properties` o en el entorno, antes de imprimir:

```properties
PUBLIC_FRONTEND_URL=http://localhost:3000
```

El QR contiene `PUBLIC_FRONTEND_URL/qr/{codigo}`, la misma ruta que usa el frontend. El valor se obtiene de configuración y no del encabezado Host de una petición. Se permite HTTP/HTTPS sin credenciales, query ni fragmento. En un teléfono, localhost apunta al teléfono: para un despliegue se configura la dirección accesible del frontend y su API antes de imprimir etiquetas.

La conexión del frontend con los datos del backend sigue siendo una entrega posterior, según el plan del proyecto. La pantalla actual lee localStorage. En esa integración, `/qr/[code]` debe consultar la API pública y mostrar sus datos; no redirigir a una API pública por ID numérico. El diseño de la ficha y de la hoja A4 de 16 stickers se conserva. El PDF lo sigue armando `qrTemplatePdf.ts`; el backend valida la selección y devuelve los enlaces que debe codificar.

## Ficha pública

`GET /api/publico/qr/{codigo}` no requiere JWT. Devuelve:

- `codigo`, `estado` y `tallerNombre` (nombre del taller propietario de la etiqueta).
- `vehiculo`: patente, marca, modelo, versión, año y último kilometraje registrado; null si está disponible o inutilizado.
- `ultimoService` y `ultimaDistribucion`, o null cuando no existen cierres de ese tipo.

Los mantenimientos provienen exclusivamente de órdenes FINALIZADO, ordenadas por fecha real de cierre y luego ID. No se elige la fecha de creación o del turno. Se devuelve la fecha del cierre en Argentina, el kilometraje de ese cierre. Los comentarios del cierre quedan privados. Service incluye el aceite y filtros informados al finalizar. Distribución incluye los materiales consumidos cuyos nombres contienen distribución/correa/bomba/tensor, igual que el criterio de la ficha del frontend.

No se inventan aceite, filtros ni kits cuando falten datos. Se conserva kilometraje cero y una selección de filtros vacía. No se publica la descripción de planificación, indicaciones del administrador, diagnóstico, fotos, datos del cliente, teléfonos, documentos, cuentas de usuario, IDs internos ni precios. Tampoco se devuelven observaciones del cierre.

No se crea otra tabla FichaTecnica: los cierres de las órdenes son la fuente del mantenimiento. Los intervalos sugeridos que muestra la maqueta pertenecen a su presentación; el backend no agrega intervalos universales ni antecedentes ficticios. La ficha se sirve con `Cache-Control: no-store`, para que una desvinculación no deje una respuesta pública cacheada.

## Endpoints

Todas las rutas privadas requieren `Authorization: Bearer <token>` y se limitan al taller de la sesión.

| Método | Ruta | Operación |
| --- | --- | --- |
| GET | `/api/codigos-qr` | Lista paginada; por defecto DISPONIBLE. |
| GET | `/api/codigos-qr/{codigo}` | Datos, vínculo, impresión y URL pública. |
| POST | `/api/codigos-qr/lotes` | Crear 16 códigos; sin cuerpo. Devuelve 201 con una lista. |
| PUT | `/api/codigos-qr/{codigo}/vehiculo` | Vincular con `{"vehiculoId":123}`. |
| DELETE | `/api/codigos-qr/{codigo}/vehiculo` | Desvincular. |
| POST | `/api/codigos-qr/plantillas` | Validar 16 códigos sin imprimir y devolverlos con sus enlaces. |
| POST | `/api/codigos-qr/impresiones` | Marcar 16 códigos como impresos. |
| GET | `/api/codigos-qr/{codigo}/imagen` | PNG para vista previa; `tamano` de 200 a 1024, por defecto 280. |
| GET | `/api/publico/qr/{codigo}` | Resolver el QR y consultar su ficha sin autenticación. |

Listado: `estado=DISPONIBLE|ASIGNADO|INUTILIZADO`, `impreso=true|false`, `vehiculoId`, `q` por código/patente/marca/modelo, `pagina` desde 0 y `tamano` entre 1 y 100. Al buscar el QR de un vehículo por este listado, enviar `estado=ASIGNADO`. La respuesta tiene contenido y totales de paginación.

Plantillas e impresiones reciben `{"codigos":[...]}` con exactamente 16 códigos distintos. Todas las etiquetas deben pertenecer al taller y estar sin imprimir; pueden estar disponibles o asignadas. Se valida la selección completa antes de modificarla. Si una falla, ninguna queda marcada. Un bloqueo por taller y restricciones de PostgreSQL protegen impresiones y asignaciones simultáneas.

Correspondencia de datos al conectar el frontend:

| Maqueta | API |
| --- | --- |
| `code` / `qrCode` | `codigo` en QR, `codigoQr` en Vehículos |
| `status: disponible/asignado/inutilizado` | `estado: DISPONIBLE/ASIGNADO/INUTILIZADO` |
| `vehicleId` | `vehiculoId` |
| `createdAt` / `assignedAt` / `printedAt` | `creadoEn` / `asignadoEn` / `impresoEn` |
| URL construida con `window.location.origin` | `urlPublica` |
| `lastService` / `lastTimingBelt` | `ultimoService` / `ultimaDistribucion` |
| `date` / `km` / `oil` | `fecha` / `kilometraje` / `aceite` |

## Probar en Bruno

Abrir **14 - Codigos QR** después de iniciar sesión como ADMIN:

1. Generar lote: guarda `codigoQr`, `otroCodigoQr` y `codigosQr`.
2. Consultar ficha pública: debe estar disponible y sin vehículo.
3. Vincular vehículo usando `vehiculoId`; volver a consultar la ficha, sin token.
4. Ver imagen QR; contiene la ruta `/qr/{codigo}`.
5. Preparar plantilla y luego marcar como impresos. Repetir preparar debe devolver 400.
6. Reemplazar usando otroCodigoQr: el anterior queda inutilizado. Desvincular también lo inutiliza definitivamente; intentar reasignarlo devuelve 400.

Para comprobar mantenimiento, finalizar una orden SERVICE/DISTRIBUCION con las colecciones anteriores y consultar otra vez la ficha. Las pruebas usan octano_test y cubren lectura real del PNG, privacidad, reglas de la hoja, reemplazos y asignaciones simultáneas. Sus datos temporales se eliminan al terminar.

## Privacidad y uso único

La ficha pública incluye `tallerNombre` del taller propietario del QR. No devuelve observaciones del mecánico ni comentarios internos. Al reemplazar o desvincular una etiqueta pasa a `INUTILIZADO`: no expone el vehículo, no se puede reasignar ni imprimir. Se conservan auditoría e impresión. V17 protege la baja definitiva también en PostgreSQL.
