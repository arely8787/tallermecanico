# UC-CV-07 — Consulta de vehículo por VIN o placa

## Objetivo

Permite a usuarios autorizados consultar un vehículo vigente mediante VIN o placa. La consulta muestra los datos necesarios del vehículo, su cliente propietario actual y el taller asociado a ese cliente. No crea, edita ni elimina registros.

## API

`GET /api/vehiculos/consulta`

La ruta requiere el mismo permiso de consulta y registro de vehículos ya aplicado al controlador: ADMIN o RECEPCIONISTA.

| Parámetro | Regla |
| --- | --- |
| `vin` | Se acepta como criterio único y se normaliza a mayúsculas. |
| `placa` | Se acepta como criterio único; se normaliza a mayúsculas y sin espacios ni guiones. |

Enviar ambos parámetros o ninguno devuelve `400` con un mensaje claro. Una coincidencia inexistente devuelve `200` con `found: false`, por lo que la interfaz no la trata como error. Los vehículos con estatus `ELIMINADO` no participan en la consulta.

La respuesta incluye vehículo, cliente actual y taller. El taller proviene de `clients.taller_id`; la relación se consulta desde `vehiculo.cliente_id` hacia `clients`, y después hacia `workshops`. Si el cliente no tiene taller, los campos del taller son `null`.

## Interfaz

En Administración de vehículos, el botón **Consultar vehículo** abre una pantalla de solo lectura. Permite elegir VIN o placa, muestra **No encontrado** cuando `found` es falso y conserva los errores de API en SweetAlert.

El nombre del cliente es texto informativo; no es un enlace ni desencadena navegación.

## Verificación realizada

| Caso | Estado |
| --- | --- |
| VIN existente con taller | Aprobado |
| Placa existente | Aprobado |
| Placa en minúsculas, con espacios y guion | Aprobado |
| VIN inexistente | Aprobado: `200` y `found: false` |
| Cliente sin taller | Aprobado: taller `null` |
| VIN y placa juntos, o ninguno | Aprobado: `400` |
| Consulta sin token | Aprobado: `403` |
| Vehículo transferido | Cerrado: el VIN `1HGCM82633A009001` devolvió al propietario actual después de la transferencia. |
| Vehículo con estatus `ELIMINADO` | Cerrado: el VIN `1HGCM82633A009001` respondió `found: false`. |

También se verificaron `GET /api/vehiculos`, `GET /api/vehiculos/{id}`, `GET /api/vehiculos/cliente/{id}` y `GET /api/vehiculos/marcas`.

## Cierre de validación

En el cierre actual, `npm run build` terminó correctamente. `mvn test -q` no alcanzó a ejecutar pruebas porque Maven no pudo abrir un bloqueo en `~/.m2/repository/.locks`; se registra como fallo de entorno, no como prueba aprobada.

## Transferencia UC-CV-11

La transferencia de propietario ya existía en backend, incluido el historial `vehiculo_historial_propietario` y la validación de destino activo. Esta entrega solo agrega la acción **Transferir** en la tabla principal de Administración de vehículos dentro de `VehicleRegistration.vue`; reutiliza el GET y el PATCH existentes.

Los casos pendientes y las verificaciones diferidas de esta historia se mantienen en la tabla **Pendientes y verificaciones diferidas** de [`fase2.md`](../fase2.md).
