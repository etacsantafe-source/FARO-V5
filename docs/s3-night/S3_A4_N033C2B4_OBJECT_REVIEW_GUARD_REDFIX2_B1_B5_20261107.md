# FARO V5 — S3.A4 N033C2B4 · B1–B5 segundo correctivo
Fecha 2026-11-07 AR. Unidad exacta `S3_A4_N033C2B4_OBJECT_REVIEW_GUARD`. Base `3a6d65a9a5db49e0459e9bf390d97c84b301ef58`, B7 anterior RED_FIX de SHA `205b5632bafe6121551f3923a7eacb6221c9c125` y expediente `docs/s3-night/S3_A4_N033C2B4_OBJECT_REVIEW_GUARD_B7_RED_FIX_2_20261107.md` blob `ea2d12301bbdd9a37191b28f8f1c37c012efd5e2`.

## B1 — Evidencia CI y dependencia exactas
- CI https://github.com/etacsantafe-source/FARO-V5/actions/runs/42395535331 sobre `205b5632bafe6121551f3923a7eacb6221c9c125` FAILURE en `:app:compileDebugUnitTestKotlin`, `INVISIBLE_REFERENCE` línea 91 de `FaroV5Planning8ApprovedObjectGateT2Test.kt`. `ws.draftSnapshot` pertenece a `:workspace` y es **internal**. Fuente principal compila, JUnit XML NO se ejecutó (26/26 NOT_RUN). Guard siete bloques #42395535340 SUCCESS (no sustituye prueba).
- `ConversationWorkspaceStore.readTimeline(conversationId)` es público suspend de `ConversationTimelinePort`, devuelve `ConversationTimelineSnapshot?` con `authorityToken` generado por Workspace. `ConversationWorkspaceStore.createPlanReview` es público y valida que request.authorityToken sea el token real. Los puertos públicos `createAndSelect` y `decide` ya se usan en la corrección anterior.
- Objetivo binario: recompilar y ejecutar la misma suite nueva de 14/14 y regresiones 10/10 + 2/2, manteniendo idéntico código principal y XML.

## B2 — Reuso V4.4 / fronteras
- V4.4 posee autorizaciones de interacción y estados canónicos de Chat, no se deben copiar datos/código. V5 ya publica `ConversationTimelinePort.readTimeline`, que provee la autoridad de la conversación desde Workspace, no UI. Reusar ese puerto público para el fixture preservando encapsulación del módulo. No convertirlo en ACL Planning.
- Alternativas: A ADAPTAR readTimeline público; B DESCARTAR hacer público `draftSnapshot`; C DESCARTAR colocar el test en `:workspace` porque la compuerta vive en `:app`; D DESCARTAR inventar token o leer SQLite directamente.

## B3 — Investigación externa, riesgos, licencias
- Kotlin visibility https://kotlinlang.org/docs/visibility-modifiers.html : `internal` es visible dentro de un módulo de compilación, no desde otra Gradle module. **USAR** contrato público; prueba negativa compile desde app. Sin bibliotecas nuevas.
- Android architecture data-layer https://developer.android.com/topic/architecture/data-layer : separar repositorios/data de presentación, conservar API de lectura tipada, no exponer implementaciones internas a fixtures ajenos; ADAPTAR, no copiar código.
- OWASP authorization https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html: comprobación de autoridad canónica y default-deny; token Workspace sólo sirve para el Review canónico, NUNCA como permiso Planning. Retener tests de ACL revocable y revalidación de Review posteriores. Costo 0, sin dependencia paga.
- Alternativa de parche corto `authorityToken="test"` RECHAZADA: genera pruebas falsas y false positives. `readTimeline(...).authorityToken` no conserva credenciales ajenas, es un snapshot explícito de Workspace.

## B4 — Delta mínimo y paths
Un solo path funcional permitido, el NUEVO test `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5Planning8ApprovedObjectGateT2Test.kt`. Reemplazar únicamente `ws.draftSnapshot(conversationId)!!.authorityToken` con `kotlinx.coroutines.runBlocking { ws.readTimeline(conversationId) }!!.authorityToken`. Sin tocar `FaroV5Planning8ApprovedObjectGate.kt`, `:workspace`, el workflow ni suites históricas. Funcional commit exacto debe incluir `FARO-UNIT:S3_A4_N033C2B4_OBJECT_REVIEW_GUARD` y permiso READY con este documento, blob y path exacto.

## B5 — Auditoría adversarial / STOP
- Rechazar si el test deriva token de memoria UI/constante; `readTimeline` es Workspace real, fail si null. `runBlocking` sólo en el fixture, nunca en UI ni adaptador productivo. Si fuente compiló pero CI funcional no, B7 nunca PASS.
- No tocar Review policy, ACL owner, SQL ni generation pointer. La revocación de objeto y descarte de Review durante ACL debe continuar probada. Al ejecutar nueva CI, XML de nueva clase 14, regresiones Search grant 10 y Chat/Apply 2, cero skipped/errors/failures. Guard mandatory del SHA nuevo SUCCESS.
- Aunque 26/26 verde, esto sólo concluye el guard interno `PASS_SCOPED`; el dueño ACL real y check in-transaction siguen faltando. V2 productiva, Auto/Noche y S4 OFF; S2 intacto y datos de prueba no borrados. S5 postergada.
**B5 READY_SCOPED** exclusivamente para corrección de fixture público tras segundo B7 RED_FIX.
