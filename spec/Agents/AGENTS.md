# Guía del repositorio

## Estructura y organización de módulos

`frontend/` contiene la interfaz Vue 3/Vite. Mantén la estructura principal y la navegación en `src/App.vue`; coloca pantallas de mayor tamaño en `src/components/` (por ejemplo, `VehicleRegistration.vue`). Los estilos globales orientados a Tailwind están en `src/style.css`.

`backend/` es una API Spring Boot 3 con Kotlin. Organiza cada funcionalidad en `src/main/kotlin/mx/taller/api/<funcionalidad>/`; conserva la responsabilidad controlador → fachada → repositorio/dominio. La configuración y los manejadores de excepciones están en `config/`, y las reglas de seguridad en `security/`. Los cambios repetibles de esquema viven en `src/main/resources/db/migration/`. `backend/data/` contiene el archivo local de SEPOMEX. Docker y Nginx se configuran desde `docker-compose.yml` y `nginx/` en la raíz.

## Comandos de compilación, pruebas y desarrollo

- `docker compose up -d mysql` inicia MySQL 8.4 localmente.
- `cd backend && mvn spring-boot:run` ejecuta la API en el puerto 8080.
- `cd backend && mvn test -q` compila y ejecuta las pruebas del backend.
- `cd frontend && npm install && npm run dev` inicia Vite en el puerto 5174.
- `cd frontend && npm run build` genera el paquete de producción en `frontend/dist/`.
- `docker compose up -d --build api` reconstruye e inicia solo el contenedor de la API. Úsalo tras cambios de backend. No uses `down -v` salvo indicación explícita: elimina los datos locales de MySQL.

## Estilo de código y convenciones de nombres

Usa convenciones de Kotlin: indentación de 2 espacios, clases y data classes en `PascalCase`, funciones y propiedades en `camelCase`, y archivos breves por funcionalidad como `ClientFacade.kt`. Mantén validación y autorización en el backend, devolviendo errores JSON con un `message` claro.

Usa Composition API en Vue con indentación de 2 espacios. Los componentes usan `PascalCase.vue`; variables reactivas y manejadores, `camelCase`. Reutiliza el estilo morado, Tailwind y SweetAlert existentes en lugar de agregar bibliotecas de interfaz.

## Pruebas

Ejecuta `mvn test -q` tras cambios de backend y `npm run build` tras cambios de frontend. Agrega pruebas enfocadas en `backend/src/test/kotlin` cuando cambies reglas de negocio, validaciones, permisos o acceso a datos. Nombra las pruebas por comportamiento, por ejemplo `rechazaCorreoDuplicado()`.

## Commits y pull requests

Sigue el historial convencional y conciso: `feat: gestionar clientes`, `fix: publicar interfaz` o `docs: actualizar diagrama`. Mantén cada commit acotado. Los PR deben describir el cambio, validaciones realizadas, impacto de migraciones e incluir capturas para cambios visibles en Vue. No incluyas credenciales, volúmenes MySQL, datos semilla locales ni cambios no relacionados.

## Seguridad y datos

Mantén las credenciales en configuración de entorno, nunca en código. Conserva `utf8mb4` en MySQL. Usa el flujo Vue → Fachada → Repositorio → MySQL y valida los roles en los endpoints del backend, no solo en el menú.
## Skills
Para tareas que toquen backend y frontend, lee `spec/Orquestador/orquestador.md`.
El backend es Kotlin. Si una skill contradice este archivo, manda este archivo.
