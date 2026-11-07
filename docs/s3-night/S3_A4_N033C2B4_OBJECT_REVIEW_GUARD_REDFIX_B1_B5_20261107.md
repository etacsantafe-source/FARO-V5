# FARO V5 — S3.A4 N033C2B4 · B1–B5 correctivos tras B7 RED_FIX
Fecha 2026-11-07 AR. Misma unidad exacta `S3_A4_N033C2B4_OBJECT_REVIEW_GUARD`. Base `f35d18f21622653d05666ee77a6d2c91416b0042`; RED_FIX documentado en `docs/s3-night/S3_A4_N033C2B4_OBJECT_REVIEW_GUARD_B7_RED_FIX_20261107.md`, blob `a55de158712761ddef5e15eef12842034318b9d7`. No repetir implementación nueva ni reescribir código de otros componentes.

## B1 — Evidencia específica del error
En commit `d2ac220d58023d225a82f90f74cf44a4f11ed8f1`, el guard extrae el plan de `TrustedConversationApplyReviewPort.load(context)` una sola vez; comprueba estado APPROVED y objeto, reconsulta ACL dos veces, pointer y hostReady, **pero no reconsulta Workspace tras la ACL**. La persona puede descartar el Review desde el owner real de Workspace mientras se evalúa el ACL, por lo que el snapshot anterior ya no describe la autoridad vigente. `ConversationWorkspaceStore.transitionPlanReview` permite `APPROVED→DISCARDED` con revisión CAS. No hay efecto Reality, pero el Boolean de preflight sería stale. CI inicial #42394955364 se conserva con SHA exacto, se interpreta por separado.

## B2 — Referencia V4.4 y patrón V5 sin reimplementar
V4.4 usaba interacción explícita y estado canónico, no click/estado UI como permiso. V5 `ReviewedConversationApplyCoordinator` ya revalida el Review durable para Apply y distingue efectos desconocidos. Reutilizar el mismo `TrustedConversationApplyReviewPort.load(context)` con contexto exacto, sin alterar la máquina de estados ni el motor Apply. Mantener separados ACL del objeto, generación activa y Review humano.

## B3 — Investigación externa aplicable
- OWASP transaction authorization https://cheatsheetseries.owasp.org/cheatsheets/Transaction_Authorization_Cheat_Sheet.html: la decisión debe corresponder al contenido real en el instante de autorización, no a una vista previa editable; **ADAPTAR** segunda lectura canónica, no copiar código, coste cero.
- OWASP authorization https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html: check en cada uso y default deny; **APLICAR** prueba en la que el usuario revoca después del primer load. 
- OpenFGA OSS `Check` https://github.com/openfga/python-sdk/blob/main/docs/OpenFgaApi.md: señal de revisión/consistencia; **NO INTEGRAR** servicio externo ni caching. El resultado Boolean de esta unidad no es transaccional ni persistible.
- Alternativas: A **USAR** segundo load + comparación de snapshot y envelope bytes; B **RECHAZAR** comparar solo epoch ACL o puntero; C **NO** convertir aprobación local en capability duradero sin verificación.

## B4 — Corrección B6 delimitada
Cambiar **solo** estas tres rutas del permiso original (la tercera únicamente para el conteo):
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5Planning8ApprovedObjectGate.kt`: al terminar dos ACL reads añadir `workspace.load(context)` y exigir igualdad exacta de `review`, `state APPROVED`, `envelope` byte a byte, junto a pointer/hostReady/catalog/epoch. Null/excepción/revision distinta deniega.
2. `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5Planning8ApprovedObjectGateT2Test.kt`: nuevo test donde callback ACL descarta de forma REAL en `ConversationWorkspaceStore` el Review ya aprobado, manteniendo epoch ACL, exige false y snapshot DISCARDED. Total nueva suite 14.
3. `.github/workflows/faro-v5-s3-a4-n033c2b4-object-review-directed.yml`: XML exacto 14 nuevos + 10 grant histórico +2 flujo Apply histórico = **26/26**, sin skips/errors/failures. No tocar tests legados.
Exigir nueva guard protocol PASS y CI funcional SUCCESS exact SHA; no false-positive de Run verde para un commit distinto.

## B5 — Auditoría adversarial previa / decisión
- Verificar que la comparación se ejecuta **después** del callback ACL, sin asumir que `APPROVED` se mantiene. Cambio de generation pointer ABA, aclEpoch, catalog, hostReady o Workspace Review debe denegar.
- Test nuevo debe operar en Workspace REAL, no solo fixture de autoridad falsa: asegurar que se transitó a DISCARDED tras el primer load. Si callback ACL devuelve mismo epoch, final Review debe imponerse.
- Mantener `null` ACL = DENY, host real aún sin owner, no Apply/OS/scheduler, V2 shipping OFF, Undo/retry pendiente; no rutas de S2, S4, V4.4, main, schemas ni limpieza S5. CI rojo = B7 RED_FIX y reparación adicional.
**Dictamen B5: READY_SCOPED_FOR_SAME_UNIT_CORRECTION**, con permiso nuevo y B7 anterior RED_FIX; cierre final sujeto a CI y B7 independiente. La subunidad nunca equivale a permisos reales por objeto ni terminación N033C2B4.

## B1 — Evidencia adicional CI inicial (mismo RED_FIX)
La CI funcional inicial https://github.com/etacsantafe-source/FARO-V5/actions/runs/42394955364 terminó **FAILURE** sobre SHA `d2ac220d58023d225a82f90f74cf44a4f11ed8f1`, job `128470228587`. La fuente principal compiló; `:app:compileDebugUnitTestKotlin` falló `INVISIBLE_REFERENCE` en la nueva clase T2 (línea 87): `ConversationWorkspaceStore.create(WorkspaceCreateRequest)` es `internal` al módulo `:workspace` y la suite reside en `:app`. Solución mínima, sin ampliar paths: usar el puerto **público** `suspend createAndSelect(conversationId, atMillis, idempotencyKey)` bajo `runBlocking`; exigir `ConversationSelectionResult.Saved`. No hacer público el método interno ni modificar `:workspace`. Se mantienen los 13 casos existentes y se agrega el test 14 de revocación del Review. Este error pertenece a la primera ejecución y no se debe ocultar presentando la segunda como si fuese la única.

## B4 — Ajuste de frontera pública Kotlin
La inspección del código muestra también que `ConversationWorkspaceStore.transitionPlanReview` y `planReviewSnapshot` son `internal`. Para el fixture real de `:app`, reemplazar la aprobación y descarte de prueba por el puerto **público suspend** `ConversationReviewPort.decide(ConversationPlanReviewIntent.Approve/Discard, key)` con `runBlocking` y resultado `ConversationReviewDecision.Saved`, y leer estado final mediante `ConversationReviewPort.reviews(conversationId)` en `runBlocking`. Esta sustitución se limita al nuevo test. No exponer métodos internos ni modificar `:workspace`.
