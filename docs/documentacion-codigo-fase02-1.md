# Documentación objetiva del código — Fase 02.1

## Alcance

Esta documentación cubre únicamente el código de **Administración de clientes**: la pantalla Vue unificada, el alta desde esa pantalla sin requerir un taller seleccionado previamente y los métodos backend que la atienden. No describe comportamiento no presente en el código.

## Vigencia

El flujo permanece integrado en el proyecto, pero evolucionó después de esta fase: usa el catálogo `status`, conserva las eliminaciones como borrado suave y permite a ADMIN y RECEPCIONISTA operar clientes conforme a la política centralizada. El resumen consolidado y los pendientes vigentes están en [`fase2.md`](../fase2.md).

## Frontend: estado y pantalla unificada

### `adminClients`, `adminClientPage`, `adminClientTotalPages` y `adminClientTotal` — `frontend/src/App.vue`

Referencias reactivas que guardan, respectivamente, los clientes recibidos para la pantalla administrativa, la página actual, el total de páginas y el total de registros informado por el servidor.

### `adminClientFilter` — `frontend/src/App.vue`

Objeto reactivo con `workshopId` y `order`. El selector de la pantalla lo utiliza como filtro de taller y como dirección de orden de los nombres.

### `title` — propiedad computada — `frontend/src/App.vue`

Convierte el estado `adminClients` en el título visible **Administración de clientes**. También mantiene los títulos de las demás pantallas del componente.

### Bloque de plantilla `screen === 'adminClients'` — `frontend/src/App.vue`

Es la única vista de listado de clientes. Muestra los controles Taller, Orden y Nuevo cliente; presenta las filas devueltas por `adminClients`, las acciones de edición, cambio de estatus y eliminación, y los botones de paginación. El botón Nuevo cliente no tiene condición `disabled` asociada al filtro de taller.

### `openClientAdministration()` — `frontend/src/App.vue`

Limpia mensajes, carga los talleres con `loadWorkshops()`, cambia la vista a `adminClients` y reinicia el filtro, los datos y los metadatos de paginación. No consulta clientes hasta que el usuario elige un valor en el selector.

### `loadAdminClients(page = 0)` — `frontend/src/App.vue`

Comprueba que `adminClientFilter.workshopId` tenga un valor. Si lo tiene, llama a `GET /api/administracion/clientes` con taller, página, tamaño `10`, orden y el campo `fullName`; carga las fotos con `loadPhoto()` y actualiza lista y metadatos. Ante un error muestra `showAddressError()`.

### `openClientForm(client = null)` — `frontend/src/App.vue`

Prepara el formulario para alta si no recibe cliente o para edición si lo recibe. En alta, si el filtro administrativo contiene un id de taller distinto de `SIN_TALLER`, asigna ese id a `clientForm.workshopId`; el campo sigue ligado mediante `v-model` y puede cambiarse. Si no hay filtro, o si el filtro es `SIN_TALLER`, el taller inicia vacío. Finalmente prepara la dirección y carga los talleres activos para el selector del formulario.

### `submitClient()` — `frontend/src/App.vue`

Construye un `FormData` con los campos de `clientForm` y la fotografía elegida. Usa `POST /api/clientes` para altas y `PUT /api/clientes/{id}` para ediciones. Tras una respuesta exitosa muestra SweetAlert y vuelve a cargar la página administrativa mediante `loadAdminClients()`.

### `toggleAdminClientStatus(client)` — `frontend/src/App.vue`

Solicita confirmación con SweetAlert. Según el estatus recibido, presenta la acción Suspender o Reactivar y llama a `PATCH /api/administracion/clientes/{id}/estatus`. Al finalizar recarga la página actual.

### `deleteAdminClient(client)` — `frontend/src/App.vue`

Solicita confirmación de borrado suave. Si el usuario confirma, llama a `DELETE /api/administracion/clientes/{id}`, que conserva el registro con estatus `ELIMINADO`, y carga una página válida a partir de los metadatos actuales.

## Backend: controlador administrativo

### `ClientAdministrationController.list(tallerId, page, size, order, sort)` — `backend/src/main/kotlin/mx/taller/api/client/ClientController.kt`

Atiende `GET /api/administracion/clientes`. Está restringido con `@PreAuthorize("hasRole('ADMIN')")`, recibe los parámetros de filtro y paginación y delega su procesamiento a `ClientFacade.listForAdministration()`.

### `ClientAdministrationController.toggleStatus(id)` — `backend/src/main/kotlin/mx/taller/api/client/ClientController.kt`

Atiende `PATCH /api/administracion/clientes/{id}/estatus`. Está dentro del mismo controlador restringido a ADMIN y delega el cambio de estatus a la fachada.

### `ClientAdministrationController.delete(id)` — `backend/src/main/kotlin/mx/taller/api/client/ClientController.kt`

Atiende `DELETE /api/administracion/clientes/{id}`. Delega la eliminación a la fachada y responde HTTP 204 si termina correctamente.

## Backend: fachada y repositorio

### `ClientFacade.listForAdministration(workshopFilter, page, size, direction, sortField)` — `backend/src/main/kotlin/mx/taller/api/client/ClientFacade.kt`

Valida que la página sea no negativa, que el tamaño esté entre 1 y 10, que el orden sea `asc` o `desc` y que el campo permitido sea `fullName`. Para `SIN_TALLER` usa los clientes sin `workshopId`; en otro caso convierte y valida el id del taller con `workshops.requireExists()`. Construye el `PageRequest`, consulta el repositorio y devuelve datos, página, total de páginas y total de registros en `ClientPageView`.

### `ClientFacade.create(command, photo, actorId)` — `backend/src/main/kotlin/mx/taller/api/client/ClientFacade.kt`

Exige que `workshopId` exista y que el taller esté activo mediante `workshops.requireActive()`. Después valida, revisa duplicados, valida la foto y persiste el cliente con el taller indicado. El taller que llega puede provenir del valor precargado o de una selección distinta hecha en el formulario.

### `ClientFacade.update(id, command, photo, actorId)` — `backend/src/main/kotlin/mx/taller/api/client/ClientFacade.kt`

Busca el cliente. Si el formulario incluye `workshopId`, exige que el destino esté activo; valida el resto de los campos, evita duplicar otro cliente y guarda los valores actualizados, incluida la asignación de taller.

### `ClientFacade.toggleStatus(id)` — `backend/src/main/kotlin/mx/taller/api/client/ClientFacade.kt`

Busca el cliente y alterna su campo `status` entre `ACTIVO` y `SUSPENDIDO`; guarda y devuelve la vista del cliente.

### `ClientFacade.delete(id)` — `backend/src/main/kotlin/mx/taller/api/client/ClientFacade.kt`

Busca el cliente y aplica la transición al estatus `ELIMINADO`; conserva la entidad y su fotografía. Si el id no existe, utiliza la respuesta de cliente no encontrado.

### `ClientRepository.findAllByWorkshopId(workshopId, pageable)` — `backend/src/main/kotlin/mx/taller/api/client/Client.kt`

Consulta derivada de Spring Data que devuelve una página de clientes asociados a un taller determinado.

### `ClientRepository.findAllByWorkshopIdIsNull(pageable)` — `backend/src/main/kotlin/mx/taller/api/client/Client.kt`

Consulta derivada de Spring Data que devuelve una página de clientes cuyo `workshopId` es nulo. Es la consulta usada cuando el filtro administrativo es `SIN_TALLER`.

## 2.1

| Elemento de fase 2.1 | Estado actual | Pendiente para completarlo |
| --- | --- | --- |
| Pantalla única Administración de clientes | Completo | El componente Vue solo conserva la vista `adminClients` para el listado administrativo. |
| Acceso visible para roles autorizados | Completo | La política `canManageClients` permite ADMIN y RECEPCIONISTA, tanto en la interfaz como en backend. |
| Nuevo cliente sin filtro previo | Completo | El botón abre el formulario aunque el selector administrativo esté vacío. |
| Taller precargado y editable | Completo | Si el filtro contiene un taller real, se copia al formulario; el selector del formulario permite cambiarlo. |
| Taller obligatorio al guardar | Completo | La creación exige `workshopId` en `ClientFacade.create()`. |
| Filtro, orden y paginación | Completo | El backend limita el tamaño a 10 y permite ordenar solo por `fullName`. |
| Alta y edición desde administración | Completo | La vista usa los endpoints multipart existentes y recarga la lista administrativa al guardar. |
| Cambio de estatus y eliminación | Completo | El cambio de estatus respeta transiciones válidas; la eliminación es lógica y se reserva para ADMIN. |
| Pruebas automatizadas específicas de interfaz | Pendiente | El repositorio no contiene pruebas de componentes Vue para este flujo. |
