# FARO V5 — W01 B3B1B1B1B1B1C Aviso al COMMIT real de política READ (B1–B5)
Fecha Argentina 2026-11-17. BASE HEAD `b698173e45b0f2c005f3815084cfe75a1bf301d5`. Unidad propuesta `S4_W01_B3B1B1B1B1B1C_POST_COMMIT_READ_POLICY_NOTICE_DEV`.
**PREAUDITORÍA DOC_ONLY, NO READY, NO B6.** W1 permanece abierto, W2 STOP, V2 productiva OFF, S3 físico no se toca.

## B1 — Evidencia concreta y límites
- Continuidad W01 B3B1B1B1B1B1B B7 PASS scoped en `docs/v5/FARO_S4_W01_B3B1B1B1B1B1B_PRIVATE_INVALIDATION_B7_PASS_SCOPED_20261117.md` blob `b70ca72c892ffdaf6973531ba3b9952b5ea4908d`; código `6eece332b81436af3dbff955b2800557440045ff`, dirigidas `44154198775` SUCCESS 8/8 JVM y `44154198671` SUCCESS V2 Core 0 providers. No repetir. Provider PRIVADO sólo en `w01FinanceDev` `AndroidManifest.xml` blob `ef4b2e16d82cd9535fb4c1645fa9599341f739f1`; `v2CoreDev` mantiene 0 providers.
- `RealityFinance9Acl.kt` blob `0b7effb6d5d75c9cc511f957c34cd6ecd8ecf467`: `issueAfterTrustedStageAdmission` siempre incrementa epoch en BEGIN IMMEDIATE/COMMIT; `revoke` devuelve epoch incluso si `revoked=true`, sin forma de distinguir no-op; throws/ROLLBACK ante trigger de fallo. `afterWriteBeforeCommit` ya existe como costura de prueba; no meter Android en SQL.
- `RealityStore.kt` blob `e08a76d444b8a5756aaa5dccf7572938cd4a722f`: `finance9TrustedReadPolicyIssuerForDevOnly` (aprox líneas 822–861) y `finance9TrustedReadPolicyRevokerForDevOnly` (872–911), fixed READ scope `finance.snapshot.read/finance:all`, STAGED9 + material gate, DB bloqueada al cerrar o Apply activo. Port legado de revocación `RealityFinance9TrustedReadPolicyRevocationPort.kt` blob `fe1b295cf1711a5d7b2ffc2179f6e0b8930aeebd` prohíbe derivar transición del epoch: no romper compatibilidad.
- `S4W01FinanceReadPrivateInvalidation.kt` blob `ff022304228e77a9c0ed741f4163456b6a933d85`: ya provee `publishAfterVerifiedCommitForDevOnly(context)` y `ContentObserver`, pero SIN caller SQL. No enviar saldos, epoch, principal ni cuenta en URI/payload.
- Prueba SQLite ya certificada `RealityFinance9TrustedReadPolicyRevocationTest.kt` blob `b9b9b5e32e1ad4bf8c3a4b119081b4b8f40220d7`: 8/8 con revocación real, idempotencia, PID externo y rollback. Reutilizar sin repetirla como prueba nueva.

## B2 — Comparación y reutilización
V4.4 AtomicApply/receipts/Undo/recovery se conservan; una señal Android nunca sustituye el estado REAL SQLite. Reutilizar ACL serializada existente, ports DEV de issuer/revoker y provider aislado B7 PASS, en vez de agregar un segundo coordinador SQL, cronómetro, nuevo schema/tabla, file watcher o eventos de saldos. No tocar main, V4.4, manifests, Finance ledger, Apply, receipts, FBS2v4, S2/S3, W2, UI productiva.

## B3 — Alternativas verificadas
1. **SELECCIONADO**: devolver resultado tipado **luego** de `COMMIT TRANSACTION`, `changed=true` sólo cuando se escribió un epoch nuevo; separarlo de `revoke` legado que sigue retornando solo epoch. Android DEV propietario pasa resultado a adaptador `publishAfterVerifiedCommitForDevOnly`, que invoca `ContentResolver.notifyChange`. Documentación Android (provider válido desde Android O): https://developer.android.com/reference/android/content/ContentResolver ; observer es callback asíncrono: https://developer.android.com/reference/android/database/ContentObserver .
2. **CONSERVAR** doble SQL fence de lecturas en ON_RESUME. SQLite WAL snapshot isolation https://www.sqlite.org/isolation.html : aviso jamás autoriza render y los procesos raw `sqlite3` pueden NO enviar aviso.
3. **RECHAZAR**: llamar notifyChange antes del COMMIT o desde `afterWriteBeforeCommit`, emitir señal por no-op o `null`, inferir cambio al comparar epochs entre SELECTs separados (TOCTOU), notificar a partir del retorno del revoker legado (no distingue transición), instalar provider en V2 Core/product, polling, callbacks dentro de transacción, y escribir datos financieros al URI.
4. Kotlin/Android: mantener boundary `store:reality` libre de Android; adaptador en `app` específicamente aislado por applicationId W01. Evitar arreglar fallos S3 o Maven 429 ajenos a esta unidad.

## B4 — Diseño de nueva unidad exact7
1. MODIFICAR `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9Acl.kt` — añadir resultado tipado `revokeWithCommittedTransition` desde dentro de BEGIN IMMEDIATE y retornar sólo DESPUÉS de COMMIT; viejo `revoke` delega y mantiene epoch/idempotencia.
2. MODIFICAR `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt` — nuevo port DEV STAGED9 para `issue/revoke` de scope READ fijo; retornos null si no admitido, no auth humana, no Compose, no despacho Android dentro del lock.
3. CREAR `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9CommittedReadPolicyChangePort.kt` — resultado mínimo `epoch + changed` sin principal/saldo y request types existentes; factory no es grant.
4. CREAR `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadPostCommitPublisher.kt` — adaptador DEV opcional Reality9 port + callback notifyContent en paquete `ar.com.faro.alpha.w01financedev`; avisa sólo `changed=true` si la transacción retornó confirmada; devuelve `notified=false` si IPC falla, sin rollback de SQL ya comprometido.
5. CREAR `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9CommittedReadPolicyChangeTest.kt` — 8 tests NUEVOS SQL real (issue, revoke, absent, no-op, ABA, trigger abort, staged gate, no Apply/grants).
6. CREAR `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01FinanceReadPostCommitPublisherTest.kt` — 8 tests NUEVOS JVM (nunca preCOMMIT, only changed, no-op/absent, null, failed signal, exception, issue/reissue, clase no payload).
7. CREAR `.github/workflows/faro-v5-s4-w01-b3b1b1b1b1b1c-post-commit-policy-notice.yml` — preflight parent exact7, JVM 8+8 XML, `:app:compileW01FinanceDevKotlin` y `:app:compileV2CoreDevKotlin`, comprobación V2 Core cero providers y W01 uno privado, no UI shipping.
No añadir señales a otras rutas finance grants; no afirmar que *todos* los writers anuncian, solo el NUEVO port cooperante DEV cuando se invoque. Si el nuevo port no es consumido por Activity, documentarlo DORMANT.

## B5 — Auditoría adversarial PRE-CODE
- R01: issue crea nuevo epoch bajo SQL STAGED9, solo luego del COMMIT resultado changed=true; repetir issue es NUEVO epoch legítimo (no-op NO).
- R02: revocar por primera vez changed=true; segundo revoke changed=false SIN segundo aviso, epoch permanece igual; ausente/foreign devuelve null SIN aviso.
- R03: trigger SQL FAIL antes del COMMIT/rollback y stage7/8 denegada, no record y no aviso; `afterWriteBeforeCommit` inyectado nunca puede disparar el publicador.
- R04: callbacks no dentro del mutex, ContentResolver sólo Android app, sin ACL/Balance/Principal transmitido, no source de autoridad.
- R05: fallo notifyChange/IPC separa mutación COMMITTED de delivery `notified=false`, el caller nunca considera balance como válido; catch-up ON_RESUME sigue.
- R06: appID DEV W01 exacto, nadie registra proveedor en paquete V2 core, no Internet, no product UI, otros writers SQL no-cooperantes continúan NOT_COVERED.
- R07: dos procesos Android reales, entrega ContentObserver inter-PID y G15 NO verificados por JVM; no certificar 100% latencia ni tiempo real.
- R08: 16 testcases nuevos XML + compilación de variantes + regresiones de aislamiento, diff exact7 y B7 scoped; CI roja implica RED_FIX sin avanzar W2.
**DICTAMEN**: GO a B5 adversarial separado y, si pasa su guard, permiso READY INDIVIDUAL exact7. NO autoriza código todavía.

## B6
NOT_RUN; pendiente READY y CAS.
## B7
PENDING de esta unidad, no confundir con el B7 histórico de B1B1B1B1B1B.
