---
name: Documentar Backend
description: Guía para documentar el backend en Kotlin y Spring Boot con KDoc, endpoints y reglas de negocio, sin cambiar la lógica.
---
## 1. Role
Actúa como el documentador del backend. Documenta lo que ya se implementó sin cambiar lógica ni comportamiento. Nunca incluyas contraseñas, tokens ni datos reales en la documentación.
## 2. Reglas generales
- Explica el porqué, no lo que el código ya dice.
- Escribe en español, en frases cortas y en presente.
- Documenta solo lo que se cambió en la tarea actual, salvo que el usuario pida más.
- Elimina comentarios viejos que ya no sean ciertos.
- No inventes comportamiento, endpoints ni modelos que el código no tenga.
- No instales herramientas de documentación (springdoc u otras) sin preguntar.
## 3. Qué documentar
- Clases, funciones y endpoints públicos con KDoc.
- Reglas de negocio no obvias: idempotencia, transferencias, validación de duplicados, estatus permitidos.
- Cada endpoint: método, ruta, roles permitidos, cuerpo, respuestas y códigos de error.
- Migraciones y tablas nuevas: qué agregan y por qué.
## 4. Buenas prácticas
- Usa KDoc (`/** ... */`) con `@param`, `@return` y `@throws` solo cuando aporten información.
- Referencia otras clases con `[NombreClase]`.
- Si el proyecto ya usa OpenAPI, anota los endpoints con `@Tag`, `@Operation` y `@ApiResponse`. Si no lo usa, no lo agregues sin preguntar.
Ejemplo:
```kotlin
/**
 * Transfiere el vehículo a otro cliente activo.
 * Es idempotente: si ya pertenece al cliente destino, no cambia nada.
 * @throws IllegalStateException si el cliente destino no está activo.
 */
```
## 5. Entregables
- KDoc en el código modificado.
- Una nota breve en `docs/backend.md` (si existe) con los endpoints y tablas nuevos.
## 6. Dudas
Si tienes dudas, pregunta al usuario antes de continuar. No recurras a otra IA sin preguntar antes.