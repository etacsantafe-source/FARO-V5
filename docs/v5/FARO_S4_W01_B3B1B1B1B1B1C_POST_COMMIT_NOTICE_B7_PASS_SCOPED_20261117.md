# FARO V5 — S4 W01 B3B1B1B1B1B1C post-COMMIT READ policy notices, B7 PASS scoped
Fecha AR 2026-11-17. Unidad `S4_W01_B3B1B1B1B1B1C_POST_COMMIT_READ_POLICY_NOTICE_DEV`.
**B7 PASS_SCOPE DEV STAGED9 SQL typed transition + optional W01 private Android notice.**
NO confirma entrega ContentObserver entre dos PID Android, NO monta Activity/Compose, NO autentica humano, NO cierra W1 ni habilita W2/V2 productiva.

## B1 — Estado y preauditoría anterior
- Checkpoint previo `b698173e45b0f2c005f3815084cfe75a1bf301d5`, provider privado `w01FinanceDev` B7 PASS acotado; `v2CoreDev` continúa con CERO providers, B7 anterior `docs/v5/FARO_S4_W01_B3B1B1B1B1B1B_PRIVATE_INVALIDATION_B7_PASS_SCOPED_20261117.md` blob `b70ca72c892ffdaf6973531ba3b9952b5ea4908d`. NO repetir.
- B1–B5 preauditada `docs/v5/FARO_S4_W01_B3B1B1B1B1B1C_COMMITTED_POLICY_NOTICE_B1_B5_20261117.md` blob `51e652081c3cf4c41d8200e5e1599471bf7d9245`, SHA `98cd3f42f6c10ba292852fb66e4b9099b5ece3fd`, guard `44158528702` SUCCESS.
- B5 adversarial separado `docs/v5/FARO_S4_W01_B3B1B1B1B1B1C_COMMITTED_POLICY_NOTICE_INDEPENDENT_B5_20261117.md` blob `6ebc4b4bb2bcdd4c84d904de57e8506c5d9eefc1`, SHA `f2a19f8a9a9ffd9dfbd427d3b6786a9ce66bff63`, guard `44158861570` SUCCESS.
- READY individual EXACT7 en `282a0115e9436ae873aa3f2650795eb1d295f245`, guard `44159120606` SUCCESS. Preflight de ESE SHA padre confirmado `FARO_PROTOCOL_PREFLIGHT_PASS ... 282a0115e9436ae873aa3f2650795eb1d295f245` en run dirigida `44160015632`; la guardia 7 bloques del commit código `44160015459` SUCCESS.

## B2 — Diff funcional exact7
Commit `e68549b1b1ee2155aea37e27d4f8861661db1316`, parent READY `282a0115e9436ae873aa3f2650795eb1d295f245`, mensaje con `FARO-UNIT:S4_W01_B3B1B1B1B1B1C_POST_COMMIT_READ_POLICY_NOTICE_DEV`.
Compare Github confirma EXACT7:
1. `RealityFinance9Acl.kt`: método nuevo `revokeWithCommittedTransition` comparte misma BEGIN IMMEDIATE/COMMIT y devuelve epoch+changed; `revoke(key):Long?` LEGADO delega al nuevo método, sin cambiar retorno ni idempotencia.
2. `RealityStore.kt`: port nuevo STAGED9 READ fijo, usa issuer SQL existente después de COMMIT y revocador nuevo; material admission, stage9, lock y `activeTransactionId` conservados. No Android en Reality.
3. `RealityFinance9CommittedReadPolicyChangePort.kt`: dos métodos issue/revoke con resultado tipado mínimo `observedPolicyEpoch + changed`, sin saldo/principal/grant.
4. `S4W01FinanceReadPostCommitPublisher.kt`: adaptador DEV con factory restringida al packageId `ar.com.faro.alpha.w01financedev`, llama al puerto SQL y sólo luego al callback de `ContentResolver.notifyChange` existente; notificaciones sin payload, outcome `notified=false` si falla IPC. DORMANT: sin caller en Activity/Compose shipping.
5. `RealityFinance9CommittedReadPolicyChangeTest.kt`: 8 casos nuevos SQLite9 real.
6. `S4W01FinanceReadPostCommitPublisherTest.kt`: 8 casos nuevos JVM de entrega/no-op/fallos.
7. `.github/workflows/faro-v5-s4-w01-b3b1b1b1b1b1c-post-commit-policy-notice.yml`: preflight EXACT7, 8+8 JUnit XML, compile dos variantes, verificación providers y no wiring productivo.
NO toca AndroidManifest, Gradle, ledger, Apply, grants, receipts, FBS2v4, S3, V4.4/main, W2, product UI.

## B3 — Evidencia real del comportamiento
- El port de `RealityStore` retorna `changed=true` para issue RECOMMIT de nuevo epoch; primera revocación `changed=true`; segundo revoke retorna `changed=false` con mismo epoch y no emite callback. Ausente/foreign y rollback por trigger SQL devuelven null. SQLite mismo owner serializa la transición; `Finance9Acl.transaction` retorna luego del COMMIT o no retorna éxito en ROLLBACK.
- El sender Android DEV se llama *después* del resultado del port; `sendEmptyNotice` jamás desde `afterWriteBeforeCommit`. No se pasan principal ni epoch al URI. Si el aviso falla, la mutación ya comprometida permanece y el resultado diferencia `notified=false`.
- Los ocho tests de STORE usan Bundled SQLite9 real, segundo connection read, trigger rollback, idempotencia, reissue ABA, schema8, close y ausencia de grants/ledger/receipts. Los ocho tests de APP usan fake port para el orden de dispatch, false/no-op, fallos; APP fake NO es test de dos PID.
- Fuentes: Android https://developer.android.com/reference/android/content/ContentResolver y https://developer.android.com/reference/android/database/ContentObserver ; SQLite WAL https://www.sqlite.org/isolation.html . Ningún aviso equivale a autorización de render.

## B4 — CI y pruebas verificadas por SHA
- Directed workflow `44160015632`, job `134310985789`, SHA exacto `e68549b1b1ee2155aea37e27d4f8861661db1316`: **COMPLETED SUCCESS**.
- `:store:reality:test --tests RealityFinance9CommittedReadPolicyChangeTest`: `BUILD SUCCESSFUL` (2m45s); 8 casos nuevos STORE y 0 fallos/errors/skip.
- `:app:testDebugUnitTest --tests S4W01FinanceReadPostCommitPublisherTest`: `BUILD SUCCESSFUL` (1m44s); 8 casos nuevos APP y 0 fallos/errors/skip.
- `:app:compileW01FinanceDevKotlin`, `:app:compileV2CoreDevKotlin` y manifests packaged: `BUILD SUCCESSFUL` (42s). Validación del CI: V2 CORE=0 providers, W01 DEV=1 provider app-private export=false, sin INTERNET, sin montaje shipping.
- Log final `W01_POST_COMMIT_SQL_8_OF_8_AND_APP_8_OF_8_PASS` y `W01_POST_COMMIT_ANDROID_DEV_TWO_VARIANTS_PRIVATE_PROVIDER_ISOLATED`, seguido de `NO_ANDROID_TWO_PID_NOTICE_OR_HUMAN_AUTH_CLAIMS`.
- Artefacto JUnit XML 16 casos `faro-v5-w01-post-commit-read-policy-junit`, ID `14362872748`, 1666 bytes ZIP. XML verificado por CI, no afirmar inspección manual ZIP.
- Guardia del commit `44160015459` COMPLETED SUCCESS.
- En snapshot CI del SHA: 23 workflows ya SUCCESS, un workflow histórico de revocación `44160015639` FAIL porque intenta preflight del READY *de su antigua unidad* en el HEAD actual (no llegó a compilar sus tests). Cuatro workflows seguían en progreso en observación; NO afirmar ALL GREEN ni usar el fallo de preflight viejo como regresión funcional. Las pruebas SQL históricas previas no se re-certificaron por esta suite.
- Writer CAS del código epoch1136 RELEASED antes de iniciar CI. No se mantuvo HELD durante la compilación.

## B5 — Riesgos residuales y scope
- La fábrica DEV `forIsolatedW01DevOnly` existe, pero **NO está montada** en el host UI ni probada como entrega real entre PID Android. Los port synthetic tests de APP no sustituyen esa aceptación, ni test en Moto G15.
- Raw SQLite de otro proceso, revocación por proceso muerto, pérdida de observer o operación fuera de este NUEVO port pueden no emitir ContentResolver; mantener ALWAYS SQL catch-up ON_RESUME del B7 anterior. COMMIT posterior al último SELECT puede quedar fuera de observación antes del render: no display lease ni garantía 100%.
- W2 STOP, V2 productiva OFF, main/V4.4/S2/S3 intocables, sin API paga. Esta unidad NO representa pagos o transferencias reales.
- Próximo paso funcional requiere B1–B5/READY propios: montar el owner DEV de lectura y probar de verdad las señales ContentObserver entre dos procesos Android con tests de lifecycle, sin habilitar pantalla productiva por defecto. Módulo Finanzas W1 aún abierto.

## B6
**IMPLEMENTED** exact7 `e68549b1b1ee2155aea37e27d4f8861661db1316`, suite dirigida 16/16 + 2 compiles PASS, writer 1136 RELEASED.

## B7
**PASS_SCOPED_DEV_STAGED9_POST_COMMIT_READ_POLICY_CHANGE_AND_OPTIONAL_ANDROID_NOTICE_16_JVM_ANDROID_COMPILE**.
Permiso de la unidad actual consumido a BLOCKED con B7 evidencia de este archivo; checkpoint de docs y liberar writer. No declarar fin global de W1.
