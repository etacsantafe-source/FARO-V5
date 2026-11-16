# FARO V5 — S4 W01-B1 · B7 PASS DEV CANONICAL WORKSPACE REVIEW
Fecha local: 2026-11-16. Unidad: S4_W01_B1_DEV_CANONICAL_FINANCE_REVIEW_CREATION.

## B1 — Fuente y cierre del defecto
B1–B5: docs/v5/FARO_S4_W01_B1_DEV_WORKSPACE_REVIEW_B1_B5_20261115.md.
Permiso individual desde 4dd93a3e47add934aed39c93ac61eca8fd67358e.
B6 inicial fad7545710800cc177fbcbb592bb6f764cb6223f; CI 43789125645
FAILURE 6/7, guard 43789125545 SUCCESS. La prueba
workspaceRevisionAndChatSelectionAreRechecked suponía un cambio de chat
cuando el chat estaba VACÍO; Workspace conserva la regla de reutilización
del chat vacío. RED_FIX previo documentado en
docs/v5/FARO_S4_W01_B1_RED_FIX_B7_20261115.md; doc commit
b124e57d82243a9220c4e1d9239009d183b930dd y guard 43789807280 SUCCESS.

## B2 — Delta exclusivo
Corrección de SOLO el test
v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B1FinanceReviewHostTest.kt
en SHA a7c670c7af7d8c7f41877fcbc0cdeb25e0e54ad5. Sembró un mensaje USER ACCEPTED DURABLE mediante
ConversationTimelineWriteRequest ANTES de crear/seleccionar otro chat
en ambos escenarios de cambio/carrera. Sin modificaciones a host,
Workspace, Reality o código productivo. W00 30/30, W01-A 10/10,
W01-B0 12/12 conservados sin repetición.

## B3 — Alternativa rechazada
No cambiar política V4.4 de chat vacío, no quitar el test ni permitir
host Apply por una Review visible. Con actividad real, probar selección
y generación persistentes en Workspace.

## B4 — Pruebas exactas
CI 43866046135 COMPLETED SUCCESS exact SHA a7c670c7af7d8c7f41877fcbc0cdeb25e0e54ad5, job
133294741928, Gradle BUILD SUCCESSFUL in 3m22s,
S4_W01_B1_WORKSPACE_PENDING_REVIEW_XML_PASS 7/7, 0 fail/error/skip.
Guard 43866046166 SUCCESS mismo SHA. Diff test-only.
Writer RELEASED epoch951 y checkpoint a7c670c7af7d8c7f41877fcbc0cdeb25e0e54ad5.

## B5 — Riesgo residual
Siete casos: PENDING durable, reapertura, sin Apply/receipt, foreground
negado, generación/host revocados, Finance snapshot stale, idempotencia,
conflicto de monto e identidad, Workspace CAS/selección stale.
Un callback DEV NO es autenticación humana Android; sin certificación de
Apply Finance, permisos owner, receipt, ni UI final. W01 global OPEN.
No cambia catálogo180, Reality7/8, V2 productiva, Auto/Noche, main,
V4.4, S2/S3 ni ejecuta pagos externos.

## B6
Implemented DEV JVM 7/7 XML scoped; consumir permiso READY de W01-B1
en estado BLOCKED antes de siguiente commit funcional.

## B7
PASS_SCOPED_DEV_WORKSPACE_PENDING_REVIEW_7_OF_7, no W01 global.
Debe verificar guard documental nuevo SHA SUCCESS, diff solo docs
y writer RELEASED epoch952 last_checkpoint=HEAD. Nunca autoanticipar.
