# Planeación técnica — Fase inicial

## Propósito y alcance comprobado

Este documento describe **solo el código implementado** de la fase inicial de Taller Morado. La fase cubre el acceso a la plataforma y la administración básica de usuarios; no implementa todavía la gestión de órdenes de reparación, vehículos, diagnósticos, inventario, pagos, agenda ni reportes.

La solución actual se compone de un frontend Vue 3, una API REST con Spring Boot/Kotlin y una base MySQL 8.4 ejecutable con Docker Compose. El frontend de desarrollo se comunica con la API mediante el proxy de Vite (`/api` hacia `http://localhost:8080`).

## Desglose de la fase 1

| Subfase | Estado | Entregable verificable |
| --- | --- | --- |
| 1.1 Base técnica y persistencia | Terminada | MySQL 8.4, servicio Docker, conexión JPA y creación/actualización del esquema. |
| 1.2 Identidad y acceso | Terminada | Registro, inicio de sesión, cierre visual de sesión, hash BCrypt y protección de rutas REST. |
| 1.3 Recuperación de acceso | Terminada para desarrollo | Generación de token temporal, validación de vencimiento y cambio de contraseña. |
| 1.4 Roles y administración de usuarios | Terminada | Roles, estados de cuenta, listado y asignación de rol restringidos a Gerente o Administrador. |
| 1.5 Interfaz web | Terminada | Vistas responsivas de acceso, registro y recuperación, en diseño lila. |

No existen subfases programadas en el código actual para órdenes de reparación u operación del taller; por tanto quedan fuera de esta planeación inicial.

## Módulos terminados y relación con el código

| Módulo | Descripción funcional | Implementación |
| --- | --- | --- |
| Autenticación | Valida correo y contraseña, verifica que la cuenta esté activa y entrega un token Bearer para acceder a recursos protegidos. | `backend/src/main/kotlin/mx/taller/api/auth/AuthController.kt` (`POST /api/auth/login`) y `security/SecurityConfig.kt`. |
| Registro | Crea cuentas con nombre, correo único y contraseña de al menos 8 caracteres. La cuenta nace activa con rol `CLIENTE`; después un gerente o administrador puede modificar su rol. | `AuthController.kt` (`POST /api/auth/register`) y `domain/User.kt`. |
| Protección de contraseñas | Nunca persiste la contraseña original: usa BCrypt con factor 12 para generar y comprobar el hash. | `security/SecurityConfig.kt`, bean `BCryptPasswordEncoder(12)`. |
| Recuperación de contraseña | Genera un token aleatorio de 32 bytes, válido 30 minutos; al restablecer se vuelve a guardar un hash BCrypt y se invalida el token. | `AuthController.kt` (`POST /api/auth/forgot-password` y `POST /api/auth/reset-password`). En desarrollo el token se devuelve a la interfaz; no hay envío de correo implementado. |
| Roles y usuarios | Expone listado de usuarios y cambio de rol/estado únicamente para `ADMIN` o `GERENTE`. | `AuthController.kt` (`GET /api/users`, `PATCH /api/users/{id}/role`) y anotaciones `@PreAuthorize`. |
| Cliente web | Muestra formularios para iniciar sesión, registrarse, recuperar/restablecer contraseña y una pantalla de acceso autorizado. | `frontend/src/App.vue`; la ruta `/api` se redirige a la API en `frontend/vite.config.js`. |
| Infraestructura | Define MySQL, perfil de API en contenedor y perfil de Nginx para servir el build estático. | `docker-compose.yml`, `backend/Dockerfile` y `nginx/default.conf`. |

## Datos administrados

La entidad persistida de esta fase es `users` y es administrada por JPA/Hibernate (`ddl-auto: update`).

| Campo | Tipo lógico | Uso |
| --- | --- | --- |
| `id` | Long autogenerado | Identificador del usuario. |
| `name` | Texto | Nombre visible. |
| `email` | Texto único | Identificador de acceso; se normaliza a minúsculas. |
| `passwordHash` | Texto | Hash BCrypt; nunca debe contener una contraseña en claro. |
| `role` | Enumeración | `ADMIN`, `GERENTE`, `MECANICO`, `CLIENTE`, `RECEPCIONISTA` o `ALMACENISTA`. |
| `status` | Enumeración | `ACTIVE`, `PENDING` o `DISABLED`; solo `ACTIVE` puede iniciar sesión. |
| `resetToken` | Texto opcional | Token temporal para recuperación en el entorno de desarrollo. |
| `resetExpiresAt` | Fecha/hora opcional | Fecha límite de uso del token de recuperación. |

La aplicación crea un usuario gerente inicial mediante `backend/src/main/kotlin/mx/taller/api/config/SeedData.kt`. Sus datos de acceso son únicamente de desarrollo y deben cambiarse antes de un despliegue real.

## Endpoints REST disponibles

| Método y ruta | Uso | Autorización |
| --- | --- | --- |
| `POST /api/auth/register` | Crear una cuenta de cliente. | Pública |
| `POST /api/auth/login` | Autenticar y obtener token. | Pública |
| `POST /api/auth/forgot-password` | Crear token de recuperación. | Pública |
| `POST /api/auth/reset-password` | Guardar una nueva contraseña. | Pública con token válido |
| `GET /api/users` | Consultar usuarios. | `ADMIN` o `GERENTE` |
| `PATCH /api/users/{id}/role` | Cambiar rol y estado. | `ADMIN` o `GERENTE` |

## Ubicación de credenciales y configuración de MySQL

Las credenciales de desarrollo no se copian en este documento para evitar que queden expuestas al publicar el repositorio. Están definidas en los siguientes archivos:

- `docker-compose.yml`, servicio `mysql`: variables `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD` y `MYSQL_ROOT_PASSWORD`.
- `backend/src/main/resources/application.yml`: propiedades `spring.datasource.url`, `spring.datasource.username` y `spring.datasource.password`, con soporte para las variables de entorno `DB_URL`, `DB_USER` y `DB_PASSWORD`.
- `docker-compose.yml`, servicio `api` (perfil `full`): inyecta `DB_URL`, `DB_USER` y `DB_PASSWORD` cuando la API se ejecuta en Docker.

Para producción, se debe sustituir cualquier valor por defecto por secretos de entorno o un gestor de secretos y conservarlos fuera de Git mediante un archivo `.env` ignorado. El archivo `.gitignore` ya ignora `.env`.

## Software recomendado

Para frontend: Node.js LTS y npm, Vue 3, Vite, Tailwind CSS, un navegador con DevTools y Visual Studio Code o JetBrains WebStorm. El proyecto ya incluye Vue, Vite y Tailwind en `frontend/package.json`.

Para backend y despliegue: IntelliJ IDEA con soporte Kotlin/Spring, JDK 21 (versión configurada en `backend/pom.xml`), Maven, Docker Engine o Docker Desktop y Docker Compose. Para un servidor, se puede desplegar la API como contenedor con el perfil `full` y publicar el build de Vue con Nginx usando el perfil `production`. Antes de exponerlo a Internet se requiere HTTPS, secretos de producción y un servicio de correo real para la recuperación de cuentas.

## Límite y siguiente etapa

La fase inicial deja una base de identidad funcional. El token de sesión actual se mantiene en memoria y el flujo de recuperación muestra el token solo para desarrollo; ambos deben endurecerse (por ejemplo JWT persistente/rotación y correo transaccional) antes de producción. La gestión de órdenes permanece como siguiente fase y no forma parte del código documentado aquí.
