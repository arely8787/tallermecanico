# Documentación objetiva del código existente

## Alcance

Esta documentación enumera las funciones y métodos creados actualmente en el repositorio. No describe código hipotético ni módulos que todavía no existen.

## Backend: arranque

### `main(args)` — `backend/src/main/kotlin/mx/taller/api/TallerApplication.kt`

Punto de entrada de la aplicación Spring Boot. Recibe los argumentos del proceso e inicia el contexto de `TallerApplication` mediante `runApplication`.

## Backend: autenticación y usuarios

### `AuthController.register(body)` — `auth/AuthController.kt`

Recibe nombre, correo y contraseña validados. Convierte el correo a minúsculas, rechaza el registro cuando ya existe un usuario con ese correo y guarda un nuevo `User` con contraseña codificada. Devuelve HTTP 201. El rol y estado que el código asigna actualmente son los valores por defecto de la entidad: `CLIENTE` y `ACTIVE`.

### `AuthController.login(body)`

Busca al usuario por correo, compara la contraseña recibida con `passwordHash`, valida que el estado sea `ACTIVE` y genera un token mediante `TokenService`. Devuelve el token y datos básicos del usuario; devuelve 401 para credenciales inválidas y 403 para una cuenta no activa.

### `AuthController.forgot(body)`

Busca la cuenta por correo. Si existe, genera un token aleatorio, lo guarda junto con una vigencia de 30 minutos y devuelve una respuesta genérica. En este entorno también devuelve `developmentToken`; no envía correo electrónico.

### `AuthController.reset(body)`

Busca el usuario por token de recuperación y comprueba su vencimiento. Si es válido, codifica la contraseña nueva, elimina el token y su fecha de vencimiento, guarda el usuario y devuelve confirmación.

### `AuthController.list()`

Obtiene todos los usuarios del repositorio y expone únicamente id, nombre, correo, rol y estado. Está restringido mediante `@PreAuthorize` a los roles `ADMIN` y `GERENTE`.

### `AuthController.assign(id, body)`

Busca un usuario por id. Si no existe devuelve 404; si existe actualiza su rol y estado con los valores solicitados. El acceso está restringido a `ADMIN` y `GERENTE`.

### `AuthController.unauthorized()`

Método privado que construye la respuesta HTTP 401 reutilizada para credenciales incorrectas.

### `AuthController.randomToken()`

Método privado que crea 32 bytes aleatorios con `SecureRandom` y los codifica en Base64 URL-safe sin relleno para el flujo de recuperación.

## Backend: seguridad

### `SecurityConfig.passwordEncoder()` — `security/SecurityConfig.kt`

Declara el bean `PasswordEncoder` que usa BCrypt con factor de trabajo 12 para crear y verificar hashes de contraseña.

### `SecurityConfig.filterChain(http)`

Construye la cadena de seguridad HTTP. Desactiva CSRF, habilita CORS, configura sesiones sin estado, permite las rutas `/api/auth/**`, exige autenticación para el resto y agrega `TokenFilter` antes del filtro de usuario/contraseña de Spring Security.

### `TokenService.create(userId, role)`

Genera un token aleatorio de 32 bytes, lo asocia en memoria con el id y rol del usuario y devuelve dicho token. Al estar en memoria, los tokens se pierden cuando se reinicia la API.

### `TokenService.lookup(token)`

Busca un token en el mapa concurrente y devuelve el par id/rol asociado, o `null` si no existe.

### `TokenFilter.doFilterInternal(req, res, chain)`

Lee el encabezado `Authorization`, extrae el token Bearer, lo consulta con `TokenService` y, si es válido, crea la autenticación de Spring Security con la autoridad `ROLE_<rol>`. Finalmente continúa la cadena de filtros.

## Backend: datos iniciales

### `SeedData.seed(users, encoder)` — `config/SeedData.kt`

Declara un `CommandLineRunner`. Al iniciar la aplicación, crea un gerente inicial únicamente si el correo configurado no está presente en la tabla `users`; la contraseña se persiste codificada con el `PasswordEncoder`.

## Backend: repositorio y entidad

### `UserRepository.findByEmail(email)` — `domain/User.kt`

Consulta derivada de Spring Data JPA que recupera un usuario por correo o devuelve `null`.

### `UserRepository.findByResetToken(resetToken)`

Consulta derivada de Spring Data JPA que recupera un usuario por token de recuperación o devuelve `null`.

`User` es la entidad JPA que representa la tabla `users`; sus campos son `id`, `name`, `email`, `passwordHash`, `role`, `status`, `resetToken` y `resetExpiresAt`.

## Frontend: Vue

### `api(url, body, method)` — `frontend/src/App.vue`

Función asíncrona que ejecuta solicitudes `fetch` JSON. Analiza la respuesta, arroja un error con el mensaje del servidor cuando la respuesta no es exitosa y devuelve el cuerpo JSON cuando sí lo es.

### `clear()`

Limpia los mensajes reactivos de éxito y error de la pantalla.

### `submitLogin()`

Limpia mensajes, activa el indicador de carga, llama a `POST /api/auth/login` y muestra un mensaje de bienvenida; ante error muestra el mensaje recibido. Al completar la operación desactiva el indicador de carga.

### `submitRegister()`

Comprueba que las contraseñas coincidan, llama a `POST /api/auth/register` y vuelve a la pantalla de inicio de sesión si el registro tiene éxito. Muestra los errores de validación o servidor disponibles.

### `submitForgot()`

Solicita un token de recuperación con `POST /api/auth/forgot-password`. Cuando el backend devuelve el token de desarrollo, lo almacena y lo copia al formulario de restablecimiento.

### `submitReset()`

Comprueba coincidencia de contraseñas, solicita `POST /api/auth/reset-password` y dirige a inicio de sesión cuando la actualización es exitosa.

### `title` — propiedad computada

Convierte el estado actual de la pantalla (`login`, `register`, `forgot`, `reset` o `welcome`) en el título que se presenta al usuario.

## Configuración sin funciones propias

- `frontend/src/main.js` monta el componente raíz de Vue e importa los estilos globales.
- `frontend/src/style.css` define el tema lila y los estilos base con Tailwind CSS.
- `frontend/vite.config.js` configura los complementos Vue/Tailwind y redirige `/api` hacia `http://localhost:8080` en desarrollo.
- `backend/src/main/resources/application.yml` configura la conexión JPA/MySQL y permite sobrescribirla mediante variables de entorno.
- `docker-compose.yml` declara los servicios MySQL, API y Nginx.

## Backend: módulo de clientes

### `ClientRequest.toCommand()` — `client/ClientController.kt`

Convierte los campos enlazados desde un formulario multipart a un `ClientCommand`. Usa los valores validados de edad y fecha de nacimiento para invocar la fachada.

### `ClientController.create(request, photo, authentication)`

Atiende `POST /api/clientes`. Acepta datos multipart y una fotografía, obtiene el id del usuario autenticado y solicita a la fachada crear el cliente. El controlador exige los roles `ADMIN` o `RECEPCIONISTA` y devuelve HTTP 201.

### `ClientController.update(id, request, photo, authentication)`

Atiende `PUT /api/clientes/{id}`. Convierte el formulario a comando, permite una fotografía opcional y delega la actualización a la fachada. Conserva la fotografía anterior cuando no se adjunta otra.

### `ClientController.list()`

Atiende `GET /api/clientes` y delega la lista de clientes a la fachada. Requiere `ADMIN` o `RECEPCIONISTA`.

### `ClientController.photo(id)`

Atiende `GET /api/clientes/{id}/foto`, obtiene los bytes y tipo MIME desde la fachada y los responde como imagen autenticada.

### `ClientController.actorId(authentication)`

Obtiene el identificador numérico del usuario configurado por el filtro de seguridad. Interrumpe la operación si el principal no es válido.

### `ClientFacade.create(command, photo, actorId)` — `client/ClientFacade.kt`

Normaliza y valida los datos, rechaza coincidencias existentes, valida que la imagen sea JPG o PNG y persiste el cliente junto con el id del usuario que lo creó y actualizó.

### `ClientFacade.update(id, command, photo, actorId)`

Busca el cliente, valida los datos sin compararlo contra sí mismo, sustituye los campos editables y opcionalmente la fotografía, registra el usuario y fecha de actualización y guarda los cambios.

### `ClientFacade.list()`

Consulta todos los clientes y convierte cada entidad en una vista sin enviar los bytes de la fotografía.

### `ClientFacade.photo(id)`

Recupera la fotografía almacenada y su tipo MIME para el endpoint protegido de imágenes.

### `ClientFacade.assertNoDuplicate(data, ignoredId)`

Busca coincidencias por correo, teléfono normalizado o combinación de nombre normalizado y fecha de nacimiento. Rechaza la alta o edición con HTTP 409 cuando encuentra otro registro.

### `ClientFacade.validate(command)`

Comprueba que edad y fecha de nacimiento coincidan, normaliza teléfono y correo, valida teléfono, correo, código postal y el código opcional de sucursal A/B. Devuelve los valores normalizados que se persisten.

### `ClientFacade.validatePhoto(photo, required)`

Exige una fotografía en altas, permite omitirla en ediciones y valida un máximo de 5 MB. Inspecciona las firmas binarias PNG/JPEG y rechaza otros archivos.

### `ClientFacade.required(value, message)` y `normalize(value)`

El primero elimina espacios y rechaza texto vacío; el segundo normaliza espacios, mayúsculas y minúsculas para comparar nombres de forma consistente.

### `ClientFacade.duplicate()` y `notFound()`

Construyen respuestas HTTP 409 para posibles duplicados y HTTP 404 para un cliente inexistente.

### `Client.toView()`

Transforma la entidad persistida en `ClientView` y construye la ruta autenticada de su fotografía sin incluir el contenido binario en el listado.

### `TokenFilter.doFilterInternal(req, res, chain)` — cambio de fase 2

Además de validar el token, ahora consulta el usuario actual en el repositorio antes de establecer la autenticación. Con ello aplica el rol y estado vigentes de la cuenta, incluso si cambiaron después de emitir el token.

## Frontend: clientes

### `loadPhoto(url)` — `frontend/src/App.vue`

Solicita una fotografía con el encabezado Bearer y transforma la respuesta binaria en una URL local que la vista puede mostrar sin exponer el token en la etiqueta de imagen.

### `loadClients()`

Obtiene los clientes autorizados, carga sus fotografías y cambia a la vista de listado.

### `openClientForm(client)`

Prepara el formulario para alta cuando no recibe cliente o para edición cuando recibe un cliente existente. Conserva la vista previa de su fotografía actual.

### `selectPhoto(event)`

Guarda el archivo seleccionado y crea una vista previa local.

### `submitClient()`

Construye un `FormData`, exige fotografía en altas, llama al endpoint de creación o edición y muestra SweetAlert al guardar correctamente. Después recarga el listado.

### `logout()`

Elimina la sesión mantenida solo en memoria, limpia la lista y vuelve a inicio de sesión.

## Fase02

| Elemento de fase 2 | Estado actual | Pendiente para completarlo |
| --- | --- | --- |
| Modelo y repositorio de cliente | Completo | La entidad `Client`, reglas únicas y consultas de duplicados están implementadas. |
| Fachada de clientes | Completo | `ClientFacade` centraliza validación, duplicados, fotografía y persistencia. |
| API de alta y edición de cliente | Completo | Endpoints multipart de alta, edición, listado y fotografía protegida. |
| Duplicados | Completo | Correo, teléfono y nombre+fecha de nacimiento se impiden en fachada y base de datos. |
| Fotografía JPG/PNG | Completo | Se valida firma binaria, tamaño máximo de 5 MB y se persiste en MySQL. |
| Dirección y sucursal futura | Completo | Dirección desglosada y referencia opcional `A`/`B`; no existe aún catálogo de sucursales. |
| Vista Vue de clientes | Completo | Formulario, listado, edición y vistas previas autenticadas. |
| Alertas SweetAlert | Completo | SweetAlert2 confirma cada alta o edición exitosa. |
| Autorización de alta | Completo | Solo `ADMIN` y `RECEPCIONISTA` pueden usar `/api/clientes`. |
| Correo de recuperación real | Pendiente | El token se sigue devolviendo solo en modo desarrollo; falta proveedor de correo. |
| Persistencia de sesión | Pendiente | Los tokens viven en memoria y se invalidan al reiniciar la API. |
