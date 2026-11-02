# FARO V5 — Handoff limpio S3 / preactivación Noche

Fecha documental: 02/11/2026 (Argentina). Estado: **HANDOFF CLEAN / READ_ONLY hasta autorización explícita de S3 y Noche en nuevo chat**.

## Autoridad y punto de recuperación
- Repo actual `etacsantafe-source/FARO-V5` (GitHub). Rama de integración y documentación `work/faro-auto-v2-integration-20261102`.
- HEAD de partida comprobado **14317bb8b10a44ea5d9e939f7e113f86b98f0de2** (este archivo se incorpora en un commit posterior; leer HEAD real en nuevo chat).
- NO usar `main` como fuente de trabajo S3: continúa en `9bae214541f466a62e149853931b96abb8d74ed9` al registrar este handoff.
- Referencia V4.4 histórica: repo `etacsantafe-source/FARO`, rama `work/faro-v5-design-clean-20261019`, exclusivamente consulta/reutilización semántica; no escribir V4.4.
- Punto de entrada: `docs/v5/FARO_CONTINUITY_POINTER.json` en la rama indicada; el presente archivo es el **handoff canónico**.

## Estado certificado versus pendiente
- `docs/s2-night/RUN_STATE.json`: S2 `CLOSED`, **8/8**; V2 de la aplicación **OFF**, S3 **NO ABIERTA**, S4 **NO ABIERTA**.
- Writer de desarrollo único `refs/heads/faro-v5-writer-lock`: consultado en `RELEASED`, epoch 8 antes de este cierre; consultar estado ACTUAL antes de cualquier escritura. No tomar posesión de un `HELD` ajeno ni por vencimiento.
- Cola cloud entre hosts: `refs/heads/faro-v5-night-queue`, archivo `queue.json`, estado observado `STAGED`, `activation_authorized=false`, sin tareas ni inflight. CAS remoto de cola probado; todavía NO equivale a orquestación productiva certificada.
- 25 automatizaciones registradas consultadas: **0 habilitadas**. No se creó ni activó ninguna ahora.
- Pruebas de infraestructura: Actions `#41528974614` `SUCCESS` (suite final herramientas); Actions `#41528236429` `SUCCESS` (push --force-with-lease remoto en ref aislada, probe borrado); prueba de CAS cloud queue pasada. Revalidar antes de activar.
- Runner Python admite actualmente solo tareas de verificación READ_ONLY por allowlist; el trabajo autónomo S3 requiere sesiones ChatGPT usando GitHub + CAS y checkpoints; esta integración de extremo a extremo todavía debe demostrarse al activar.

## Documentos de continuidad — lectura por orden
1. `docs/v5/FARO_DEVELOPMENT_UNIFIED_PROTOCOL_V2_20261102.md` — protocolo de desarrollo y ventanas Noche.
2. `docs/v5/FARO_NOCHE_S3_ACTIVATION_HANDOFF_20261102.md` — pasos y requisitos para activar Noche.
3. `docs/v5/FARO_S3_BENCHMARK_APPS_20261102.md` — comparación de apps externas, mejoras propuestas R0–R7 y pruebas X01–X12.
4. `docs/v5/V5_CAPABILITY_TAXONOMY.md` — 54 identidades oficiales.
5. `docs/v5/V5_OPERATION_CONTRACT_INVENTORY.md` — **172** contratos oficiales (no suponer aprobado 180).
6. `docs/s2-night/RUN_STATE.json` — S2 certificada sin reabrir.
7. `docs/v5/FARO_WRITER_REMOTE_CAS_EVIDENCE_20261102.md` — evidencia de writer y límites históricos.

## Decisiones S3 ya adoptadas
- Navegación exclusivamente por menú lateral; Chat-first, botón explícito `Consultar con FARO`; acciones mediante Chat.
- Autonomía dentro del alcance S3, búsqueda con degradación automática, límites adaptativos de recursos y recuperación silenciosa y segura (NO ocultar efectos inciertos).
- Una sola sesión de pruebas físicas al final, agrupadas; no requerir pruebas físicas por cada módulo.
- V2 de app OFF; S4 no abrir; main, S2, APK y repositorios históricos intactos.

## Diseño: hallazgos que NO se deben marcar falsamente como implementados/certificados
- R0/P0: falta **Actividad** como destino explícito del drawer. Recuperar N-015 `history.query`/`history.receipt.read` para pantalla READ_ONLY, sin duplicar autoridad.
- R1 búsqueda: resultado tipado, referencias estables, explicación/autoridad/fecha y cobertura parcial visible. R2 Guardados: `MessageRef(conversationId,messageId)` exacto, target eliminado/revocado. R3 confirmación: distinguir recibos auxiliares de COMMITTED de Reality.
- R4–R7: avisos por prioridad; SQLite/FTS español y offline; recuperación y UI accesible.
- H01–H04 del análisis adversarial: Undo auxiliar vs Reality, identidad referencial, borrado/restauración de Guardados y Review/Apply con receipts apropiados. Llevar a prueba de regresión.
- Propuesta Guardados N-018: **8 operaciones candidatas**, no aprobadas ni agregadas al catálogo. 172 oficiales; 180 es una hipótesis por bounded reopen, no un estado congelado.
- Pruebas adversariales X01–X12 son **por ejecutar**, no PASS. El benchmark es investigación de documentación, no prueba funcional de apps instaladas.
- Conservar el comportamiento V4.4 de chat pin/archivo/búsqueda sin copiar su persistencia. Revisar selección de archivados, search-in-chat, búsqueda global, navegación sin iconos duplicados.

## Entrada segura en nuevo chat
1. Recuperar este checkpoint con GitHub y verificar SHA de rama real, writer, cola, workflows pendientes, estado S2 y automatizaciones; NO ejecutar por la frase `Continuar FARO V5`.
2. Reconciliar y congelar los contratos S3 pendientes (incluidos Actividad, Guardados, autoridad, búsqueda, avisos) con el bucle de investigación V4.4 + benchmark + adversarial. No confundir propuesta con cierre.
3. Pedir/confirmar autorización explícita de apertura S3 y activación Noche con ventana horaria y límites; sin autorización, permanecer READ_ONLY.
4. Abrir rama de implementación aislada `work/s3-*` con HEAD esperado; escritor CAS global único; cola GitHub compartida con CAS y tareas independientes, con checkpoints y verificación del efecto antes de reintentar.
5. Validar en una prueba inocua que **cada turno** A/B usa la MISMA cola, writer y permisos GitHub, y que el stop al encontrar writer ocupado bloquea la mutación. No asumir que los tests CI bastan.
6. Una vez pasada esa verificación, programar turnos A/B horarios desfasados 30 minutos y un informe final; no activar tareas históricas; hora de corte absoluta, desactivar y verificar al finalizar.
7. Respetar `S3 only`, V2 app OFF, no S4, no API paga, pruebas físicas juntas al final, no auditorías repetidas sin evidencia nueva.

## Qué hizo y no hizo este cierre
- Solo documentación y puntero en rama de integración, writer adquirido/liberado mediante CAS esperado; ninguna modificación de producto o diseño funcional aplicada.
- Noche OFF, S3 no abierta, cola sin tareas, no Action lanzada por este checkpoint. Los estados remotos anteriores son instantáneas y deben comprobarse de nuevo.
