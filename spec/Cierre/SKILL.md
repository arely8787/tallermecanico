---
name: Cierre
description: Úsalo al terminar una tarea que modificó código del taller (backend Kotlin o frontend Vue): ejecuta pruebas y compilación, reconstruye la API si corresponde e informa. Publicar en GitHub es opcional y solo si el usuario lo pide. No lo uses para planear, investigar ni cargar datos.


---

## Fase 1: Validar e informar (siempre)
1. Revisar cambios con `git status --short` (solo lectura).
2. Ejecutar cada comando UNA sola vez, solo si cambió su carpeta:
   - backend/ cambió: `cd backend && mvn test -q`
   - frontend/ cambió: `cd frontend && npm run build`
3. Reconstruir con `docker compose up -d --build api` solo si cambió algo en
   backend/ y el prompt no dice "sin reconstruir Docker".
   Nunca usar `docker compose down -v`.
4. Si un comando falla: corregir solo si el error menciona un archivo listado
   en `git status`, con un reintento máximo. Si no, o si persiste, reportarlo
   sin modificar nada más.
5. Informe: archivos cambiados, resultado de cada comando, si se reconstruyó
   la API, datos de prueba que el agente mismo creó (o "ninguno") y si cambió
   la arquitectura (endpoint, tabla, rol o flujo nuevo). Si cambió,
   recomendar $actualizar-diagramas sin ejecutarlo.
6. Terminar preguntando: "¿Quieres que publique en GitHub?", salvo que el prompt diga "no git"; en ese caso, omitir la pregunta.

## Fase 2: Publicar (solo si el usuario lo pidió o respondió que sí)
0. Si el prompt dice "no git", no ejecutar esta fase aunque el usuario lo pida
   después sin retirar esa instrucción: preguntar primero.
1. Mostrar `git status --short`.
2. Excluir siempre: credenciales, .env, volúmenes de MySQL, tools/seed/,
   frontend/dist/ y todo archivo que el usuario no haya aprobado.
3. Proponer mensaje convencional (ej.: `fix: guardar dirección al editar`)
   y esperar confirmación.
4. `git add <solo los archivos aprobados>`; nunca `git add .` ni `git add -A`.
   Luego commit.
5. Pedir confirmación explícita antes de `git push`. Nunca `--force`.
6. Informar resultado del commit y del push.