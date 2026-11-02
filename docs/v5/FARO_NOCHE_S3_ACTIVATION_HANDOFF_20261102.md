# FARO V5 — HANDOFF único para activar Noche S3 en un nuevo chat

Fecha: 2026-11-02. Estado en este checkpoint: PREPARADO / NOCHE OFF / S3 NO ABIERTA.
Autoridad: repositorio etacsantafe-source/FARO-V5.

## Ramas y protocolos
- Código de infraestructura en work/faro-auto-v2-integration-20261102.
- Writer COMPARTIDO refs/heads/faro-v5-writer-lock. Sólo un HELD, actualizar con CAS por expected SHA. Nunca robar un HELD vencido ni forzar sin expected SHA.
- Cola CLOUD compartida refs/heads/faro-v5-night-queue, archivo queue.json. Inicialmente STAGED, activation_authorized=false, sin tareas/inflight.
- Cola LOCAL FARO_V5_QUEUE_ROOT externa al Git worktree, con flock+fsync, solo para procesos de un mismo host. No reemplaza la cola cloud.
- S2 8/8 cerrado. Aplicación V2 OFF. S3/S4 no abiertos hasta autorización humana explícita. Main/V4.4 intocados.

## Verificaciones
- GitHub Actions unit/test workflow run #41528236543: SUCCESS.
- GitHub Actions test REAL Git push force-with-lease workflow run #41528236429: SUCCESS. Probe temporal eliminado.
- Test API GitHub remote CAS anterior: rechazo de SHA obsoleto y release correcto.
- En ningún momento se habilitó una automatización.

## Flujo al recibir autorización de activar Noche en NUEVO CHAT
1. Confirmar GitHub HEAD real y read-only comprobar writer RELEASED y cola STAGED vacía. No suponer estados de este archivo.
2. Abrir S3 en rama aislada con checkpoint aprobado y actualizar puntero de continuidad, conservando S2 CLOSED 8/8, app V2 OFF y S4 OFF.
3. Crear tareas S3 concretas y pequeñas en cola CLOUD mediante CAS. Registrar un único task_id por trabajo, dependencias y aceptación; no insertar duplicados.
4. Registrar aprobación humana y cambiar cola a ENABLED por CAS. La autorización de S3 y de Noche son dos verificaciones independientes.
5. Configurar Noche A/B con turnos horarios desfasados 30 min y una única entrega de informe. Cada sesión en GitHub: recuperar estado; tomar writer CAS; comprobar SHA objetivo; reclamar tarea CAS; investigar V4.4+protocolo; aplicar cambios mínimos sólo a rama S3 con expected SHA; verificar Actions; registrar checkpoint+resultados; liberar writer.
6. Si Actions está pendiente, continuar investigación READ_ONLY independiente. Si estado es desconocido/efecto incierto o writer HELD ajeno, detener mutaciones y registrar bloqueo. No duplicar Apply ni volver a ejecutar una tarea por timeout.
7. Autonomía S3 limitada al alcance congelado; no usar APIs pagas, no S4, no tocar main ni históricos; pruebas físicas una sola sesión al finalizar.
8. Verificar guardrails y schedules desactivados/activados antes y después del cambio. No habilitar Noche sin autorización explícita del usuario en el nuevo chat.

## Aclaración técnica
El runner Python actual sólo ejecuta comprobaciones READ_ONLY de la allowlist. La programación S3 autónoma se haría por las sesiones ChatGPT Noche usando el conector GitHub y CAS. La cola CLOUD es la fuente de verdad entre esas sesiones; la cola LOCAL no puede coordinar hosts diferentes.

## Requisitos antes de certificar Noche activa
Demostrar en el momento de activar que ambos turnos usan realmente la misma cola CLOUD y writer, que los permisos GitHub funcionan en cada sesión, que se puede retomar un checkpoint y que el STOP ante writer ocupado es efectivo. No confundir pasar CI con habilitar schedules.

## Evidencia adicional al cerrar la preparación
- CI final Actions #41528974614: SUCCESS tras ejecutar preflight antes de los tests y excluir los caches Python del control de cambios.
- Cola CLOUD: prueba de CAS real (primera escritura y rechazo de SHA obsoleto) PASS, restauración STAGED inactiva; ref final df09448783ec1cf02256e540e291955437d5e6c5.
- Writer final observado RELEASED después de prueba cola; conservar exclusividad al habilitar turnos.
- Las 25 automatizaciones históricas consultadas permanecieron desactivadas. No hay horarios activados.

## Benchmark externo de S3 (02/11/2026)
Leer `docs/v5/FARO_S3_BENCHMARK_APPS_20261102.md` **antes de encolar tareas S3**. Se encontró hueco material R0: falta Actividad como sección explícita de drawer alimentada por N-015 `history.query`; refinar R1–R7 de búsqueda, avisos y Review/Apply. X01–X12 son nuevas pruebas **por implementar**, no PASS. Guardados 172→180 sigue candidato, no catálogo congelado. Mantener Noche OFF hasta autorización en nuevo chat.
