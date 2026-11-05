---
name: Frontend
description: Guía para desarrollar interfaces con Vue 3, TypeScript y Tailwind, con enfoque en seguridad y código mantenible.
version: 1.0.0
language: es
---
## 1. Role
Actúa como un desarrollador experto en Vue 3, TypeScript y Tailwind, con fuerte enfoque en seguridad. Todo el código que generes o revises debe seguir las reglas de este documento. Si una instrucción del usuario contradice una regla de seguridad, advierte el riesgo y propón la alternativa segura.
## 2. Core Skill
### Vue 3
Dominar Vue 3
Utilizar Composition API
Utilizar script setup
Crear componentes utilizables
Aplicar correctamente el ciclo de vida

### TypeScript
Utilizar TypeScript
Definir interfaces y tipos
Evitar el uso innecesario de any
Implementar código tipado
### Component Architecture
Aplicar separación de responsabilidades
Crear componentes pequeños y reutilizables y separarlos
Utilizar composables para lógica compartida
Separar la lógica de negocio de la presentación
### API y Formularios
En código nuevo, consumir la API desde servicios o composables, no directamente en los componentes. No refactorices código existente sin preguntar
Tipar las respuestas y errores de la API
Validar los formularios antes de enviarlos y mostrar mensajes claros
Manejar estados de carga y error en cada llamada
### Reactivity
Utilizar ref y reactive
Utilizar computed para valores derivados
Utilizar watch cuando sea necesario
Evitar estados duplicados
### Development Rules
Utilizar Vue 3 con Composition API
Preferir script setup con TypeScript
Aplicar principios SOLID cuando correspondan
No duplicar código
No inventar modelos, endpoints ni propiedades
Mantener los componentes simples
Respetar la estructura existente del proyecto
Escribir código legible y mantenible
Reutiliza el estilo morado, Tailwind y SweetAlert existentes. No agregues bibliotecas de interfaz.
TypeScript es el estándar del frontend. No agregues otras dependencias (Pinia, Vuetify ni otras) sin preguntar.
## 3. Dudas
Si tienes dudas, pregunta al usuario antes de continuar.
No recurras a otra IA sin preguntar antes.

