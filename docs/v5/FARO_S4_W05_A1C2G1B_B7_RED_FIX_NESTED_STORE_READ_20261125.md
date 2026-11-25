# FARO V5 · W05-A1C2G1B B7 RED_FIX · 2026-11-25

**Unidad autorizada:** `S4_W05_A1C2G1B_TRUSTED_STOCK_UNDO_APPLY`.
**Código a corregir:** `84ceeda8d997ff4801a90ebb052e69fdd31ca197`.
**Resultado B7:** RED / NO PASS. Bloque B1–B5 de G1B permanece verificado; NO repetir G0 ni G1A.

## B7 — Fallo observado CI/XML original

- Guardia de código CI `45647661655`: **SUCCESS** exact SHA `84ceeda8d997ff4801a90ebb052e69fdd31ca197`. Preflight de tres rutas aprobado.
- CI G1B `45647661813`: **FAILURE**, compilación Kotlin/Android lograda, Gradle `:app:testDebugUnitTest` **BUILD FAILED**.
- Artefacto XML original `15039605527` (`w05-a1c2g1b-stock-undo-xml`). Suite dirigida `S4W05StockUndoDevApplyHostTest`: 9 casos, **5 fallos**, 0 errores, 0 omitidos. Regresión `S4W05StockReceiveDevApplyHostTest`: 8 casos **PASS**. Total 17 pruebas, **12 PASS / 5 FAIL**.
- Los cinco fallos son `AssertionFailedError: expected Committed but was RecoveryRequired`: `committedOriginalSevenOfTenCanUndoToThreeWithIndependentReceipt`, `oneReceiptCannotBeUndoneTwiceEvenWithNewUserIntent`, `rollbackOfG0CompensationCannotPersistPartialEffect`, `staleApprovedLedgerRevisionCannotApplyAnotherStockState`, `crashAfterRealityCommitReconcilesWithoutSecondUndo`. XML descargado y verificado, no asumir verde.

## B6 — RED_FIX limitado y causalidad técnica

- En `S4W05StockUndoDevApplyHost`, `UndoBinding.execute()` llama `ready(preparedOperation)`; `ready()` llama `HouseholdStockUndoDevAdmission`, cuya fuente `originalCommitted` lee Workspace COMMITTED y Reality receipt/attempt. Esa lectura ocurre durante `Reality.run`, **incompatible con el límite de origen previamente autorizado** y candidato fuerte a reentrada/bloqueo de stores. `finalCommitGate` vuelve a llamar `originalCommitted` dentro de la misma transacción; `authorization` consulta Workspace a través de `selected()`. La causa concreta de la excepción está ocultada por el contrato de `ApplyTransactionExecutor`, que devuelve `RecoveryRequired`, por lo que no adjudicar error SQL sin prueba.
- El `ReviewedConversationApplyCoordinator` incorpora `afterBeginApply` precisamente para promover evidencia humana/Workspace **antes** de `Reality.run`. La corrección restringida debe verificar fuente COMMITTED, admission G1A, fingerprint y saldo antes de comenzar; fijar a esa misma identidad y nodo una admisión tipada, sin entregar autorización por texto.
- Dentro de `Reality.run`, usar solo la evidencia **preautorizada de esa ejecución**, el fencing vivo y el consentimiento N-016, y delegar la revisión numérica/cantidad al CAS SQL13 de G0 bajo transactionScope. No llamar Workspace ni consultar attempts/receipts desde `UndoBinding.execute` o finalGate. El grant se consume en finalGate solo si nuevo receipt/plan/identity coinciden; rollback del efecto y nuevo receipt si no.
- `UndoBinding.revalidate` sigue usando `ready()` **fuera** de la transacción SQL. `afterBeginApply` vincula `ApplyAttemptIdentity` a revisión viva aprobada, origen COMMITTED exacto y `Ready` congelada, sin permitir un nuevo execute bajo otra identidad.
- Mantener denegación de revisión obsoleta (fijada en idempotencyKey `:rev:`), segundo Undo físico, saldo insuficiente, principal/epoch/puntero ABA, consentimiento N-016 independiente, confirmación, recibos/UNKNOWN/retry y recovery. No abrir puertos de producción.

## B4 — Rutas exactas invariables para la corrección

1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W05StockUndoDevApplyHost.kt`
2. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W05StockUndoDevApplyHostTest.kt`
3. `.github/workflows/faro-v5-s4-w05-a1c2g1b-stock-undo-apply.yml`

Cambio mínimo en ruta 1, tests dirigidos G1B+regresión N-035 sin ampliar ruta. CI debe demostrar **17/17** y XML original 0 failures/errors/skips, guard exact SHA. Si vuelve a fallar: documentar nuevo RED_FIX bajo mismo permiso exact3, no declarar B7.

No main, V4.4 ni producción: V2, stock real, pagos, Auto/Noche siguen OFF. Pruebas G15 NOT_RUN.
