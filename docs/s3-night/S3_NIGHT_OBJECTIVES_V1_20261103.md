# FARO V5 — Noche por objetivos V1 (preparada para piloto)

Fecha: 2026-11-03. Estado: PREPARED / NUEVO PILOTO OFF. Esto es especificación durable, NO autorización para iniciar turnos ni prolongar el piloto anterior.
Rama funcional: `work/s3-night-20261102`. Esta preparación vive en rama documental separada `docs/s3-night-goals-v1-20261103` para NO alterar el piloto S3 de las 10:31–11:31 AR. Antes de activar, recuperar el estado en vivo y la CI, no asumir que ese piloto terminó.

## Orden humana mínima
«Activar FARO Noche. Objetivo: terminar todo el desarrollo automatizable de S3 y que solo queden las pruebas físicas. Aplicar el protocolo completo: investigar, comparar V4.4 y fuentes pertinentes, cerrar huecos, programar, probar, corregir, certificar, guardar y reanudar sin repetir. Hacer un piloto inmediato de una hora con corte absoluto».
La autorización de un piloto define SOLO sus 60 minutos; NO autoriza extender a la noche completa ni ejecutar S4.

## Objetivo general persistente
- `G-S3-AUTO`: entregar S3 READY_FOR_SINGLE_PHYSICAL_CAMPAIGN, no declarar S3 CLOSED sin evidencia física. Completar las unidades S3-A1..A5 autorizadas y los gates automáticos X01..X12 requeridos, con código/test, SHA, CI conclusion y XML vinculados. Evitar pruebas físicas hasta la única campaña final.
- Las unidades tienen dependencia, criterio de aceptación binario y evidencia. Una unidad puede consumir múltiples turnos sin reiniciarse. `DONE_VERIFIED` exige evidencia, no una narración o build verde. Si se necesita decisión humana no delegada (ej. +8 Guardados), marcar WAIT_HUMAN, seguir con trabajo independiente y no aprobarla por cuenta propia.
- Próxima unidad se determina RELEYENDO GitHub al activar: S3-A1 parcial; turno B del piloto 10:31–11:31 corrigió compilación en `b716af557fae164c0b2185e039b0eaf12779e379`; Actions #41649873354 estaba IN_PROGRESS al preparar esta versión. Verificar resultado antes de reabrir o repetir. No confundir arreglo de readiness con evento real de revocación ACL/generación.

## Misión única idéntica para A y B
1. Validar hora real y corte, autorización NUEVA, cola `faro-v5-night-queue:queue.json` ENABLED + activation_authorized=true, branch/HEAD, owner/epoch del writer `faro-v5-writer-lock:writer.json`, estado A/B y checkpoint. Si falla, STOP READ_ONLY.
2. Reanudar primero el objetivo/subunidad IN_PROGRESS o WAIT_CI sin duplicar. Si no existe, elegir la primera READY que satisfaga dependencias, con archivos, prueba binaria, riesgos y efecto esperado.
3. Aplicar protocolo unificado sobre esa subunidad: investigación DIRIGIDA, comparación READ_ONLY con V4.4 y otras soluciones si aporta, pruebas adversariales de huecos, implementación real, tests, corrección de rojos y verificación. No reauditar todo S3 por rutina ni convertir una investigación repetida en resultado.
4. Para mutar: adquirir writer global PROPIO por CAS/expected SHA y revalidar HEAD. Commits exclusivamente en `work/s3-night-20261102`. Verificar CI real con run_id, SHA y XML; no reciclar PASS de SHA distinto. Si CI sigue en vuelo registrar WAIT_CI, liberar writer propio y aprovechar trabajo realmente independiente.
5. Persistir checkpoint por subunidad y objetivo (estado, evidencia, HEAD, próximos pasos, bloqueo) y liberar writer inmediatamente cuando no haya más escritura; relectura de RELEASED. Si queda ejecución y tiempo seguro, encadenar la siguiente subunidad. Si el sistema termina ejecución temprano, el próximo turno recupera checkpoint: no suponer 20 minutos garantizados.

## Tiempo, superposición y continuidad
- Los objetivos NO tienen un tope de 20 minutos; sí lo tiene la fase de producción de CADA turno. Turno programado de 30 min: 0–20 trabajo, 20–25 terminar sin iniciar CI/cambio largo nuevo, 25–27 límite de checkpoint y liberación, 27–30 READ_ONLY. Si termina antes, checkpoint/release inmediato.
- Dos agendas horarias distintas A y B, normalmente separadas 30 min. Cada agenda nunca más frecuente que una vez por hora. Un único writer global incluye manual/Auto/A/B, no solo estos turnos.
- B NO considera libre el turno previo solo por el reloj ni porque venció el lease. Relee status, owner, epoch, branch, HEAD, actividad y operaciones en vuelo; si HELD/UNKNOWN, NO takeover, esperar útilmente READ_ONLY hasta 5 min/revalidar y continuar investigación independiente solo lectura. CAS fallido/efecto incierto: reconciliar, no replay ciego.
- Una ejecución demorada no extiende su ventana. Después de su propio cutoff NO inicia mutaciones. GitHub Actions QUEUED/IN_PROGRESS no equivale a writer HELD; no cancelar CI vigente por cerrar turno.
- Rojos: dos intentos dirigidos como máximo sobre misma hipótesis sin evidencia nueva; luego investigar nueva causa o STOP, nunca bucle circular.

## Estado durable por subobjetivo
`{goal_id,unit_id,dependencies,status,owner_session,session_slot,expected_head,modified_paths,acceptance,started_at,paused_at,commits,ci_run_ids,ci_sha,ci_conclusion,junit_xml,next_step,blocker,writer_release_epoch}`.
Estados permitidos: READY, IN_PROGRESS, WAIT_CI, WAIT_WRITER, WAIT_HUMAN, BLOCKED, DONE_VERIFIED, READY_FOR_SINGLE_PHYSICAL_CAMPAIGN. No pasar a DONE_VERIFIED con CI pendiente.

## Métricas objetivas
- Por sesión: `scheduled_for`, `started_at`, `completed_at`, último cambio, checkpoint, writer release, run IDs. Reportar runtime del agente observado, tiempo de espera de CI, y trabajo efectivo SOLO si existen marcas de actividad comparables. Si no: NO_REGISTRADO.
- Por objetivo: sesiones que participaron, tiempo de trabajo acreditable (intervalos observados no superpuestos), tiempo calendario hasta DONE, número de commits funcionales, pruebas ejecutadas, correcciones, bloqueos y reintentos. Las esperas de CI no son escritura; documentos/reportes no son producción.
- No confundir tiempo de agenda (20 min), duración del agente (p. ej. 4 min) y tiempo útil medido. El rendimiento se evalúa por unidades verificadas y ausencia de duplicaciones; ningún prompt puede garantizar ejecución persistente.

## Guardas y condiciones de salida
- S2 8/8 FROZEN; solo S3. S4 OFF, aplicación V2 OFF. `main`, histórico FARO, V4.4, catálogo oficial 172, credenciales, permisos, datos reales, firmas, pagos/APIs y teléfono físico intocables. Guardados +8 candidatas NO APROBADAS.
- Cierre por (a) todo desarrollo S3 automatizable VERIFICADO, únicamente campaña física pendiente, (b) corte absoluto, (c) STOP humano, (d) bloqueo genuino sin unidad segura alternativa. WAIT_HUMAN no cuenta como finalización.
- Al corte: deshabilitar A/B, cola STAGED y autorización false mediante writer PROPIO y CAS si aún es seguro y antes del cutoff; si no, dejar agendas OFF y reportar cola pendiente, sin robo de writer. Informe posterior READ_ONLY una sola vez.

## Preparación de piloto futuro — SOLO en nuevo chat
1. Verificar que el piloto previo 10:31–11:31 AR terminó, que A/B están OFF, la cola STAGED y el writer RELEASED, y que el CI asociado está reconciliado. No reutilizar fechas de permisos históricos.
2. Con aprobación nueva de 60 min, programar A inmediata y B +30 min, cutoff +60, watchdog previo al corte y reporte final posterior. Misma misión y cola; A/B solo difieren en identidad.
3. Prompts de A/B de UNA ORDEN CORTA apuntando EXCLUSIVAMENTE a este archivo de la rama documental y al código de la rama S3. No pegar todo este protocolo en los prompts.
4. Medir si se produjo código/test/CI NUEVOS y si B retomó el mismo objetivo sin repetir. Si falla, STOP; no repetir piloto idéntico ni declarar autonomía arreglada.

Identificadores A `6ae9072890248191b886a790cf456be6`, B `6ae9072a7b6c8191b9e107fe6d6c6940`, watchdog `6ae9b51b2ac88191833cb7db6f9f5ed0`, informe `6ae9073eccd08191b88aec776771ca9e`.
Fuentes: `docs/s3-night/S3_NIGHT_SIMPLIFIED_CHAIN_20261103.md`, `docs/s3-night/RUN_STATE.json`, `docs/s3-night/S3_BACKLOG_20261102.md`, `docs/v5/FARO_DEVELOPMENT_UNIFIED_PROTOCOL_V2_20261102.md` (en rama funcional). Prevalecen SIEMPRE cola, writer y HEAD vivos frente a snapshots.
