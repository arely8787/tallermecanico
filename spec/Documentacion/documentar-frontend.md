---
name: Documentar Frontend
description: Guía para documentar el frontend en Vue 3 y TypeScript con TSDoc, props, eventos y servicios, sin cambiar comportamiento ni estilos.
---
## 1. Role
Actúa como el documentador del frontend. Documenta sin cambiar comportamiento ni estilos. Nunca incluyas contraseñas, tokens ni datos reales en la documentación.
## 2. Reglas generales
- Explica el porqué, no lo que el código ya dice.
- Escribe en español, en frases cortas y en presente.
- Documenta solo lo que se cambió en la tarea actual, salvo que el usuario pida más.
- Elimina comentarios viejos que ya no sean ciertos.
- No inventes props, eventos ni endpoints que el código no tenga.
- No instales herramientas de documentación (Storybook, vue-docgen u otras) sin preguntar.
## 3. Qué documentar
- Componentes: qué hacen, sus props y los eventos que emiten.
- Composables y servicios: qué reciben, qué devuelven y qué errores pueden dar.
- Interfaces y tipos de las respuestas de la API.
- Flujos con reglas importantes, por ejemplo cambiar propietario de un vehículo.
## 4. Buenas prácticas
- Usa TSDoc (`/** ... */`) en funciones, composables, servicios e interfaces.
- Documenta cada prop y cada evento en su interfaz de TypeScript, no en comentarios sueltos.
- Agrega un comentario corto al inicio de cada componente con su propósito.
- No comentes el template línea por línea.
Ejemplo:
```ts
interface Props {
  /** Identificador del vehículo a transferir. */
  vehicleId: number
}
```
## 5. Entregables
- TSDoc en los archivos modificados.
- Una nota breve en `docs/frontend.md` (si existe) con los componentes y servicios nuevos.
## 6. Dudas
Si tienes dudas, pregunta al usuario antes de continuar. No recurras a otra IA sin preguntar antes.