# FARO V5 · W05-A1C2G1B B7 RED_FIX4 · Workspace BeginApply revision

## B7 — Diagnóstico causal certificado por JUnit XML

Fecha 2026-11-25. Código anterior `8f2a1700eed6b97f914be30a60c171f2a92e85d5`. Guardia `45665927865` SUCCESS. CI `45665927863` FAILURE, original JUnit XML artefacto `15047821751`: 17 pruebas, 5 FAIL en host Undo, 8/8 regresión N-035 PASS, 0 errors/skips.
En **todos** los fallos, `G1B_DIAG` imprimió:
`BEFORE_INITIAL_READY > INITIAL_READY > PREFLIGHT_ENTER > PREFLIGHT_ACCEPTED > AFTER_MASK_11110111 > AFTER_BEGIN_DENIED > RESULT_RecoveryRequired`; review `RECOVERING_UNKNOWN`, transacción asignada, recibo físico sin deshacer.
Máscara de ocho condiciones `afterBeginApply`: (1) `fresh.review==applying` TRUE; (2) envelope igual TRUE; (3) APPLYING TRUE; (4) tx igual TRUE; **(5) `applying.context()==context` FALSE**; (6) admisión G1A vigente TRUE; (7) selección foreground TRUE; (8) política N-016 TRUE. Ningún `UndoBinding.execute` ni SQL13 compensó.

Causa exacta en fuente `v5/kernel/contracts/src/main/kotlin/ar/com/faro/v5/kernel/contracts/ConversationPlanReview.kt`: `ConversationPlanReviewPolicy.evaluate()` termina cada transición aceptada con `next.copy(revision=current.revision+1)`. El `ConversationPlanReviewSnapshot.context()` incluye esa `revision` como `expectedRevision`. Por ello, después de `BeginApply`, el contexto de `applying` **debe cambiar de revisión**, aunque conserva su identidad e invariantes. Igualarlo al contexto APPROVED previo es incorrecto y deja un intento PREPARED/RECOVERING sin efecto.

## B6 — Corrección acotada y sin bypass

- Sustituir la comparación de contextos completos en `afterBeginApply` por comparación de los campos estables de identidad: `conversationId`, `reviewId`, `authorityToken`, `planFingerprint`. Exigir **incremento exacto de revisión CAS** `applying.revision == context.expectedRevision+1`, con protección contra overflow.
- Mantener obligatoriamente el nuevo `transactionId` idéntico a ApplyAttemptIdentity, Workspace `load` igual al `applying` observado, envelope byte-exacta, estado APPLYING, `ready() == initial`, `selected()`, policy N-016 independiente y consentimiento humano. No admitir un context stale sin CAS.
- Dentro de Reality.run, seguir usando promoción pre-transaction aprobada, fencing/consentimiento vivo y SQL13 CAS revision, sin lectura Workspace; `finalCommitGate` solo consume grant al coincidir plan/receipt/identity.
- Eliminar rastros de diagnósticos temporales `diagnosticStages`, `traceStage`, `G1B_DIAG` de host y tests. Restaurar 9 tests JUnit de host y 8 regresión N-035 SIN modificar sus expectativas.
- Ejecutar CI real `:app:testDebugUnitTest` **17/17**, XML originales 0 failures/errors/skips, artefacto, guard exact code SHA y B7 final; si falla nuevo test, no inventar PASS.

**Rutas permitidas:** solo (1) `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W05StockUndoDevApplyHost.kt` y (2) `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W05StockUndoDevApplyHostTest.kt`. Permanecen dentro del permiso G1B exact3; workflow no requiere cambios.

G0 y G1A B7 PASS se conservan. No activar V2 productiva, stock real, pagos, Auto/Noche ni físicos G15. No tocar main.
