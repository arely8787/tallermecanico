# Fase 2 — Estado consolidado de la entrega

## Alcance vigente

El proyecto **Taller Morado** es una aplicación Vue 3 con TypeScript, API Spring Boot con Kotlin y MySQL. La interfaz se sirve mediante Nginx en el puerto 8088 y la API aplica autenticación Bearer, validación de roles y respuestas JSON con `message` para los errores.

La entrega ya incluye los módulos que evolucionaron a partir de la fase 2: autenticación y empleados, clientes, talleres, catálogo SEPOMEX local, catálogo de estatus, vehículos, transferencia de propietario y consulta de vehículo por VIN o placa. La arquitectura se conserva como Vue → controlador/fachada → repositorio → MySQL.

## Módulos implementados

| Módulo | Estado | Implementación vigente |
| --- | --- | --- |
| Autenticación y recuperación | Completo para desarrollo | Contraseñas con BCrypt, tokens Bearer en memoria, recuperación con token temporal y control de cuentas suspendidas. |
| Equipo y roles | Completo | El ADMIN administra empleados; los roles existentes incluyen ADMIN, GERENTE, AUXILIAR, MECANICO y RECEPCIONISTA. |
| Clientes | Completo | Alta, edición, foto, dirección SEPOMEX, taller, paginación, estatus y borrado suave. ADMIN y RECEPCIONISTA operan clientes según la política centralizada. |
| Talleres | Completo | Alta, edición, foto, RFC, dirección validada, estatus y baja lógica para ADMIN. |
| Códigos postales | Completo | El catálogo local SEPOMEX se carga desde `backend/data/CPdescarga.txt` solo cuando la tabla está vacía; el backend expone búsquedas de estado, municipio, colonia y CP. |
| Catálogo de estatus | Completo | Valores base ACTIVO, CANCELADO, SUSPENDIDO y ELIMINADO, con administración restringida a ADMIN. |
| Vehículos | Completo | Registro, edición, marcas/modelos con caché y vPIC, listado, búsqueda, baja lógica y asociación a clientes activos. |
| Propiedad de vehículo | Completo | La transferencia cambia el propietario vigente en una transacción y conserva historial de cliente anterior, cliente nuevo, fecha y usuario. |
| Consulta UC-CV-07 | Completo | Consulta de solo lectura por VIN o placa; devuelve vehículo, propietario actual y taller asociado, o `found: false` si no existe. |
| Interfaz y navegación | Completo | Barra lateral por rol, pantallas separadas de vehículos y estatus, SweetAlert para mensajes de éxito y error. |

## Datos gestionados

| Entidad | Datos principales |
| --- | --- |
| Usuario | Nombre, correo, hash de contraseña, rol y estatus. |
| Cliente | Datos personales, contacto, dirección, foto, taller, estatus y motivo de estatus. |
| Taller | Datos fiscales, contacto, dirección validada por SEPOMEX, foto y estatus. |
| Vehículo | VIN, número de serie, placa, marca, modelo, año, versión, color, kilometraje, cliente y estatus. |
| Historial de propietario | Vehículo, cliente anterior, cliente nuevo, fecha, usuario y estatus de la relación anterior. |
| Estatus | Valor único en mayúsculas y descripción. |

## Archivos y puntos de entrada relevantes

- `backend/src/main/kotlin/mx/taller/api/auth/AuthController.kt`: autenticación, recuperación y gestión de empleados.
- `backend/src/main/kotlin/mx/taller/api/client/Client.kt`, `ClientController.kt` y `ClientFacade.kt`: clientes y administración de clientes.
- `backend/src/main/kotlin/mx/taller/api/workshop/Workshop.kt`: talleres.
- `backend/src/main/kotlin/mx/taller/api/status/Status.kt`: catálogo de estatus.
- `backend/src/main/kotlin/mx/taller/api/vehicle/Vehicle.kt`: vehículos, consulta por VIN/placa y transferencia de propietario.
- `backend/src/main/kotlin/mx/taller/api/postal/PostalCode.kt`: catálogo SEPOMEX y carga local.
- `frontend/src/App.vue` y `frontend/src/components/`: navegación y pantallas Vue.
- `docker-compose.yml`: MySQL, Nginx y perfil de API.

## No incluido en esta entrega

| Elemento excluido de Git | Motivo |
| --- | --- |
| `.archify/` | Directorio de trabajo generado por Archify; no es código ni documentación de consumo del proyecto. |
| `docs/diagrama-componentes*.json` | Salidas de validación, entrega y finalización de Archify; el documento útil es el HTML del diagrama. |
| `tools/seed/__pycache__/` y `*.pyc` | Caché binaria generada por Python; se regenera localmente. |
| Archivos `.env` | Pueden contener secretos de ejecución y deben permanecer fuera del control de versiones. |
| `docker-compose.yml` | Queda modificado localmente, pero contiene contraseñas literales de desarrollo; se excluye hasta moverlas a `.env`. |
| `backend/src/main/resources/application.yml` | Queda modificado localmente, pero conserva una contraseña literal de desarrollo; se excluye hasta moverla a `.env`. |

`frontend/dist/` no se excluye en esta entrega: Nginx lo monta desde `docker-compose.yml` para servir la interfaz en localhost.

## Pendientes y verificaciones diferidas

| # | Pendiente | Motivo | Cómo se cierra | Estado |
| --- | --- | --- | --- | --- |
| 1 | Caso e de UC-CV-07: vehículo transferido devuelve al dueño actual | No se crearon datos de prueba de transferencia durante la verificación. | Transferir un vehículo existente y consultar por VIN y placa; comprobar propietario actual. | Pendiente |
| 2 | Caso f de UC-CV-07: vehículo ELIMINADO devuelve `found:false` | No se creó un vehículo eliminado para no alterar datos. | Marcar un vehículo como ELIMINADO y consultar por VIN y placa. | Pendiente |
| 3 | Tabla del listado de vehículos en ventanas angostas | El listado puede desbordarse horizontalmente en resoluciones pequeñas. | Revisar diseño responsive y aplicar una solución visual aprobada. | Pendiente |
| 4 | Decisión sobre `frontend/dist/` | Actualmente Nginx lo usa mediante un volumen, por lo que se mantiene rastreado. | Decidir si el build pasa a la imagen de Nginx; entonces retirar `dist/` del índice e ignorarlo. | Pendiente |
| 5 | Contraseñas por defecto de `docker-compose.yml` y `application.yml` | La configuración local conserva valores de desarrollo. | Mover valores a `.env`, agregar `.env.example` sin secretos y actualizar la configuración. | Pendiente |
| 6 | Diff real de UC-CV-07 con Git | El cambio se mezcla con trabajo previo sin seguimiento antes de esta entrega. | Comparar el commit de UC-CV-07 contra su padre y registrar archivos y líneas definitivos. | Pendiente |

## Pendientes técnicos generales

- Sustituir los tokens en memoria por sesiones o JWT revocables persistentes.
- Integrar un proveedor real de correo para recuperación de contraseña.
- Llevar las credenciales locales a variables de entorno antes de cualquier despliegue fuera de desarrollo.
