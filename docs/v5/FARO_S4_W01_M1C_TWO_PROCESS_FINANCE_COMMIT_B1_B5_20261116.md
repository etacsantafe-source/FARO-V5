# FARO V5 — W01 M1C · Finance9 cross-process COMMIT fence B1–B5
**Fecha local:** 2026-11-16. **HEAD auditado:** `bc94fdd61482527ea80e56ea6d24ed03e0883a60`. **Unidad:** `S4_W01_M1C_FINANCE9_TWO_PROCESS_COMMIT_FENCE`. **DOC_ONLY**, sin permiso READY ni cambios funcionales. La CI M1B sigue RUNNING/NOT_B7 al abrir este frente. W2 STOP.

## B1 — Evidencia y brecha estructural en el código real
- `RealityStore.run(transactionId)` abre `BEGIN IMMEDIATE TRANSACTION` en una única `reality.db`, llama al bloque Apply y emite COMMIT/ROLLBACK. No consulta `finance_acl_policy` en el mismo connection/transaction justo antes de COMMIT.
- `ApplyTransactionExecutor` revisa `ApplyTransactionAuthorizationPort` antes del binding y tras él, pero por contrato su callback externo puede consultar una política fuera de la transacción. La ventana final check→COMMIT no es prueba cross-process.
- M1A `RealityFinance9Acl.kt` B7 PASS scoped (7/7 y 3 regresiones), emite/revoca epoch monotónico bajo su propio `BEGIN IMMEDIATE`, expone `observedCurrentEpochOrNull` **FUERA** del Apply; invocarlo desde `RealityStore.run` anidaría transacción o usaría otro connection, **PROHIBIDO**.
- SQLite9 `finance_acl_state`, `finance_acl_policy`, `finance_apply_grant` ya existen en STAGED9 por M0A. `finance_apply_grant` tiene FK hacia `execution_receipt`, `policy_epoch`, `generation_id`, `pointer_sequence`, `fingerprint`, `intent_key`, UNIQUE. Excluir ACL de FBS2v4 M0C2B2B sigue B7 PASS; no repetir.
- M1B código dormant `bc94fdd61482527ea80e56ea6d24ed03e0883a60`: `S4W01FinanceLocalPrincipal` y `S4W01FinanceAuthHost` dependen de AndroidKeyStore+BiometricPrompt; **CI de M1B RUNNING**, sin prueba física Android ni consentimiento financiero productivo; no usar como grant antes de B7 propio.

## B2 — Reutilización / comparación con V4.4
- Reusar `RealityStore` BEGIN IMMEDIATE, recibos PREPARED/COMMITTED, `ApplyTransactionExecutor` y el propietario durable ACL M1A. V4.4 no contiene esta barrera SQL Finance ni mecanismo real de revocación entre procesos; preservar UX sólo en W01-B.
- No crear SQLite secundaria, segundo writer, transacción distribuida ni puerto de autorización en Compose. El modelo autoritativo financiero es Reality9: la política que valida la ejecución DEBE ser leída y consumida en el **mismo** transaction scope que el efecto y receipt.
- Frontera: M1C0 `STAGED9` pruebas de dos **procesos del SO** sin humano real, luego M1C1 host bridge Android Review+principal/consentimiento y habilitación DEV sólo con permiso nuevo. No confundir SUCCESS M1C0 con autorización productiva.

## B3 — Investigación y alternativas comprobables
- SQLite https://www.sqlite.org/lang_transaction.html, https://www.sqlite.org/isolation.html, https://www.sqlite.org/wal.html: **ADOPTAR** `BEGIN IMMEDIATE` y lectura de policy en esa transacción; dos conexiones/procesos contendiendo por escritura se serializan. En WAL, lectores previos mantienen snapshot y NO pueden sustituir una lectura bajo la misma escritura transaccional; testear busy/retry de forma acotada.
- `PRAGMA foreign_keys=ON` y FK SQLite https://www.sqlite.org/foreignkeys.html: **ADAPTAR** para grant/receipt con unique fingerprint+intent y rollback.
- Alternativas **RECHAZADAS**: synchronized JVM, in-process `MaterialWriteGate` como exclusión global, callback leyendo policy en segundo SQLite connection, `ATTACH` multisqlite WAL como atomicidad, revoke luego del effect, permisos desde Workspace APPROVED, firma M1B sola como Apply token, hook al final de UI que nunca toca SQL.

## B4 — Matriz de ejecución M1C, CERO READY actual
**M1C0** futura unidad B6 stage-only exact-5 candidatas:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9Acl.kt` (agregar lectura/revalidación **inside-existing-tx**, sin `BEGIN` anidado).
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt` (seam stage9 owner, antes del COMMIT, receipts del mismo connection; schema7/8 no cambia).
3. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9AclCrossProcessTest.kt` (ProcessBuilder JVM + sqlite DB compartida, tests con dos PID independientes y timeout).
4. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9AclProcessWorker.kt` (worker independiente con barreras observables y crash inyectado).
5. `.github/workflows/faro-v5-s4-w01-m1c-process-fence-directed.yml` (CI XML exact y verificación de dos PID/locks).
**M1C1** separado si procede: `S4W01FinanceAuthHost.kt`, `S4W01B2C0FinanceDevApplyHost.kt`, `FaroV5ActiveReviewedApplySession.kt` y sus tests/Android manifiestos según la integración real. Requiere B1–B5 propios, READY nuevo, prueba física Android e identidad/consentimiento con Review persistente; NO incluido en M1C0.
Criterios C01–C08: policy presente, scope/generation/pointer exactos; revoke B antes de BEGIN A→DENY y ninguna entry/receipt; A BEGIN primero→revocación B se serializa **después** de COMMIT, no se cuela entre comprobación y commit; revoke entre Review y Apply→DENY; crash antes del COMMIT→0 receipts/0 efectos; grant un solo uso y receipt exacto; epoch revoke/regrant y pointer ABA DENY; schema7/8, sidecars/foreign owner DENY. Aceptación por JUnit XML + logs con PID/orden.
**No** se permite C0 mero test de dos conexiones en el mismo proceso como prueba sustituta de 2 OS PID.

## B5 — Auditoría adversarial
- Concurrencia real: sincronizar proceso A y B con barreras exactas, timeout finito, archivos de señas sin secretos, no usar sleeps como certificación de orden. Capturar PID distintos, ver `SQLITE_BUSY` o espera y resolución cerrada; no marcar PASS por éxito de un callback solo.
- En `RealityStore.run`, no ejecutar llamadas a Workspace/Android/Keystore desde el mutex Reality ni desde `BEGIN IMMEDIATE`: pasar evidencia prevalidada, revalidar **dentro** SQL exclusivamente fingerprint/policy/grant ligado a intento; posible fraude si token autogenerado sin material firmado: M1B físico y host pendiente.
- Un error/timeout o process death debe fallar cerrado sin duplicar receipts, sin reusar grant. `UNKNOWN` posterior a COMMIT requiere consultar receipt antes de un segundo Apply; no inventar un pago.
- Política no debe resembrar epochs ni importarse desde backup, preserva schema7/8 y M0C2B2B FBS2v4.
- No habilitar Apply productivo, no crear dinero real, no modificar main/S2/S3/V4.4/catálogo180/Android habitual, mantener W2 STOP.

## B6
NOT_RUN / NO_READY. Completar B7 de M1B código dormido y auditoría física por separado, revalidar HEAD+writer, verificar guard de este documento, emitir READY limitado para M1C0 **sólo después** de auditoría de colisiones de código y pruebas de proceso; no programar M1C1 prematuramente.

## B7
PENDING. B7 M1C0 solo cuando 2 OS PID+XML+guard exact SHA hayan pasado; el cierre global de M1 requiere M1B humano real G15 y M1C1 Apply hasta COMMIT con consentimiento, no JVM-only. W1 B–F siguen OPEN; W2 STOP.
