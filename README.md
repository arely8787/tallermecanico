# Taller Morado · Gestión de órdenes

## Arranque local

1. `docker compose up -d mysql`
2. `cd backend && ./mvnw spring-boot:run` (o `mvn spring-boot:run`)
3. `cd frontend && npm install && npm run dev`

La base de datos se publica en `localhost:3306`. El gerente inicial se crea al arrancar:

- correo: `gerente@taller.local`
- contraseña: `Gerente123!`

Cámbiala antes de usar un entorno real. Registro crea cuentas **pendientes** con rol Cliente; un gerente o administrador debe aprobarlas y asignar su rol. El flujo de recuperación devuelve un token únicamente para desarrollo: en producción sustitúyelo por envío de correo.
CONTEXTO:
- Proyecto "Taller Morado" (órdenes de reparación). Login y registro con BCrypt ya funcionan; no los modifiques.
- Sistema: EndeavourOS (Arch Linux). Java 21 y Maven 3.9.16 ya están instalados de forma permanente; no instales Maven ni uses /tmp.
- Stack: Spring Boot + Kotlin, Vue 3 + Tailwind, MySQL 8.4 en Docker (contenedor taller-mysql, puerto 3306).
- El frontend corre en http://127.0.0.1:5174 y la API en http://localhost:8080.
- Al registrarse, la cuenta queda activa como CLIENTE.
- El backend debe quedar corriendo de forma persistente, independiente de tu sesión (por ejemplo con Docker Compose), no como un proceso de esta sesión. Al terminar, verifica con curl que responde y dime cómo detenerlo y volver a arrancarlo.
- Actualiza el README para que los pasos de arranque coincidan con lo que realmente funciona (hoy dice `./mvnw`, que no existe).
