# Fase 2 — Acceso administrativo y gestión de clientes

## Alcance implementado

La fase 2 incorpora el módulo de clientes y la administración de usuarios. El acceso inicial está restringido a cuentas activas con rol `ADMIN`; por ello, en la ejecución actual solo el administrador puede obtener una sesión. El administrador crea las cuentas de empleados y asigna uno de estos roles: `GERENTE`, `AUXILIAR`, `MECANICO` o `RECEPCIONISTA`.

El endpoint de clientes conserva autorización para `ADMIN` y `RECEPCIONISTA`, pero mientras continúe la regla de acceso inicial exclusiva de `ADMIN`, una cuenta de recepcionista no podrá iniciar sesión. Esta diferencia está documentada para que la habilitación futura del rol no sea accidental.

## Módulos terminados

| Módulo | Estado | Implementación |
| --- | --- | --- |
| Inicio de sesión administrativo | Completo | `AuthController.login` valida credenciales BCrypt, estado `ACTIVE` y rol `ADMIN`; devuelve un token Bearer temporal. |
| Recuperación de contraseña | Completo para desarrollo | Se genera un token aleatorio con vigencia de 30 minutos y se almacena en `users`; falta un proveedor real de correo. |
| Administración de usuarios | Completo | Solo `ADMIN` puede listar, crear o cambiar los roles de empleados permitidos. Los correos se normalizan y no se repiten. |
| Protección de rutas | Completo | Spring Security exige autenticación para la API fuera de `/api/auth/**`; el filtro vuelve a consultar el usuario y su estado antes de asignar el rol. |
| Alta y edición de clientes | Completo | API REST multipart, datos personales, contacto alternativo, trabajo, dirección desglosada, sucursal futura A/B y trazabilidad del usuario que creó o actualizó. |
| Validación y duplicados | Completo | Se valida edad contra fecha de nacimiento, teléfono, correo, código postal y sucursal; se bloquean duplicados por correo, teléfono o nombre normalizado más fecha de nacimiento. |
| Fotografía de cliente | Completo | Solo JPG o PNG mediante firma binaria, máximo 20 MB, persistida en MySQL y servida mediante endpoint autenticado. |
| Interfaz Vue | Completo | Login, recuperación, gestión de equipo, listado/alta/edición de clientes y confirmaciones SweetAlert con el tema lila. |
| Ejecución local | Completo | Vue compilado se sirve con Nginx en el puerto 8088; Spring Boot se comunica con MySQL 8.4 en Docker. |

## Flujo actual

1. El administrador inicia sesión desde Vue con correo y contraseña.
2. `AuthController` valida hash BCrypt, estado y rol, y entrega un token Bearer en memoria.
3. Vue usa ese token para consultar o crear empleados y para gestionar clientes.
4. Spring Security verifica el token y el estado actual del usuario.
5. El módulo de clientes delega en `ClientFacade`, que valida y consulta `ClientRepository`.
6. JPA persiste usuarios y clientes en MySQL.

## Datos gestionados

### Usuario empleado

- Nombre, correo, hash de contraseña, rol y estado.
- Roles que el administrador puede crear: gerente, auxiliar, mecánico y recepcionista.

### Cliente

- Nombre completo, contacto alternativo, edad, fecha de nacimiento, teléfono, trabajo y correo.
- Fotografía JPG/PNG de hasta 20 MB.
- Calle, colonia, municipio, estado y código postal.
- Sucursal futura opcional: A o B.
- Auditoría: usuario y fecha de creación/actualización.

## Archivos principales

- `backend/src/main/kotlin/mx/taller/api/auth/AuthController.kt`: login administrativo, recuperación y administración de usuarios.
- `backend/src/main/kotlin/mx/taller/api/security/SecurityConfig.kt`: BCrypt, política de rutas y filtro Bearer.
- `backend/src/main/kotlin/mx/taller/api/client/ClientController.kt`: endpoints REST del cliente.
- `backend/src/main/kotlin/mx/taller/api/client/ClientFacade.kt`: reglas de negocio, validación y duplicados.
- `frontend/src/App.vue`: interfaz Vue y alertas SweetAlert.
- `docker-compose.yml`: MySQL, API y Nginx para la ejecución local.
- `docs/diagrama-componentes.html`: diagrama Archify actualizado de los componentes reales.

## Pendientes conocidos

- Enviar el enlace de recuperación mediante un proveedor de correo; actualmente el token solo está disponible para desarrollo.
- Sustituir los tokens en memoria por sesiones o JWT revocables persistentes.
- Definir cuándo se habilitará el inicio de sesión de recepcionistas y demás empleados; el endpoint de clientes ya contempla a recepcionista, pero la regla vigente los bloquea en el login.
- Reemplazar las credenciales de desarrollo y llevar secretos a variables seguras antes de producción.
