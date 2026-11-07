# FARO V5 — S3.A4 N033C2B4 · segundo B7 RED_FIX
Unidad `S3_A4_N033C2B4_OBJECT_REVIEW_GUARD`. Fecha 2026-11-07 AR. Código auditado `205b5632bafe6121551f3923a7eacb6221c9c125`. No confundir con primera auditoría de `d2ac220d` ni ocultar la corrección previa.

## B7 — Resultado exacto y causa
CI dirigida https://github.com/etacsantafe-source/FARO-V5/actions/runs/42395535331 **COMPLETED FAILURE** sobre el SHA exacto `205b5632bafe6121551f3923a7eacb6221c9c125`, job `128471989355`. `:app:compileDebugKotlin` y Java compilados, pero `:app:compileDebugUnitTestKotlin FAILED` `INVISIBLE_REFERENCE` en `FaroV5Planning8ApprovedObjectGateT2Test.kt:91`: la prueba llama `ws.draftSnapshot(conversationId)`, que es `internal` de `:workspace` y no es visible en `:app`. No hay XML de las pruebas: **26/26 NOT_RUN**, no PASS. Guard obligatoria del SHA exacto #42395535340 **SUCCESS**, sin sustituir CI funcional.
- Falla restante solo en fixture nuevo. Contrato público verificado: `ConversationWorkspaceStore.readTimeline(conversationId)` es `override suspend` de `ConversationTimelinePort`, y `ConversationTimelineSnapshot.authorityToken` expone token canónico. Reemplazar `draftSnapshot` con `runBlocking { ws.readTimeline(conversationId) }!!.authorityToken`; no tocar owner ni ampliar visibilidad.
- El código de guard ya implementa relectura de Review post ACL, revisa contenido de envelope, puntero ID+secuencia y readiness; pendiente prueba JVM de la segunda CI tras fijar fixture.

## B7 — Dictamen
**RED_FIX**: exige otro B1–B5 correctivo y READY nuevo con este `last_code_review` antes de tocar código. Rutas exactas se limitan a test nuevo y si es necesario workflow/código nuevo del mismo unit, sin modificar suites históricas, ni exponer internals. Volver a ejecutar 14+10+2 XML exactos, más guard obligatoria en SHA final. N033C2B4 continúa OPEN sin ACL real ni Apply; V2 shipping OFF, S4 y Noche OFF, S5 postergada.
