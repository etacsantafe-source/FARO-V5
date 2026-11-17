# FARO V5 — W01 B3B1B1B1B1B1C · B5 adversarial separado de aviso post-COMMIT
Fecha Argentina 2026-11-17. PRE-CODE, DOC_ONLY. Referencia de B1–B5 `docs/v5/FARO_S4_W01_B3B1B1B1B1B1C_COMMITTED_POLICY_NOTICE_B1_B5_20261117.md` blob `51e652081c3cf4c41d8200e5e1599471bf7d9245`, HEAD `98cd3f42f6c10ba292852fb66e4b9099b5ece3fd`, guard `44158528702` SUCCESS.
Unidad propuesta única `S4_W01_B3B1B1B1B1B1C_POST_COMMIT_READ_POLICY_NOTICE_DEV`. El B7 previo del provider `b70ca72c892ffdaf6973531ba3b9952b5ea4908d` ya PASS; no repetir ni modificarlo.

## B1 — Ataque al orden transaccional
`RealityFinance9Acl.transaction` ejecuta `afterWriteBeforeCommit` ANTES de `COMMIT`. Cualquier callback de Android ahí puede anunciar una escritura que después hizo ROLLBACK. **Prohibido** publicar desde esta costura o a partir de una comparación SQL externa al lock. El método `revoke` actual devuelve el MISMO epoch en el segundo intento, así que tampoco sirve como booleano de cambio. El resultado nuevo debe ser de la MISMA `BEGIN IMMEDIATE` y sólo llegar al caller DESPUÉS de COMMIT confirmado.

## B2 — Contractos y reutilización
Conservar `revoke(key):Long?` y su semántica; un nuevo `revokeWithCommittedTransition` tipado puede delegar el método viejo al nuevo resultado para no romper tests, grants ni wrappers. En `RealityStore` reutilizar sólo el port STAGED9 READ issuer y el nuevo ACL revoker bajo `withMaterialAdmission`, nunca extender el puerto de Apply o tratar la identidad enviada por caller como auténtica. Port de transición sólo DEV y no se monta en interfaz/Activity. V4.4 queda intocable.

## B3 — Amenazas de plataforma y fuentes
La API Android desde O exige URI con ContentProvider real (https://developer.android.com/reference/android/content/ContentResolver); la variante aislada `w01FinanceDev` ya tiene provider B7 PASS. El `ContentObserver` usa cola Handler y NO certifica sincronización (https://developer.android.com/reference/android/database/ContentObserver). SQLite WAL admite snapshots anteriores (https://www.sqlite.org/isolation.html). Rechazar eventos con principal/epoch en URI, FileObserver, envío preCOMMIT, segundo SQLite reader de comparación, y simular dos PID Android con JVM.

## B4 — Alcance EXACT7 y aceptación
Rutas únicas:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9Acl.kt`
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
3. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9CommittedReadPolicyChangePort.kt`
4. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadPostCommitPublisher.kt`
5. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9CommittedReadPolicyChangeTest.kt`
6. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01FinanceReadPostCommitPublisherTest.kt`
7. `.github/workflows/faro-v5-s4-w01-b3b1b1b1b1b1c-post-commit-policy-notice.yml`
El gate de protocolo debe preflight estos exact7 en el PARENT READY antes del commit; 8 JVM STORE + 8 JVM APP, Android `compileW01FinanceDevKotlin` y `compileV2CoreDevKotlin` y paquete V2 Core 0 providers. NO tocar `AndroidManifest`, `build.gradle.kts`, S3, W2 ni product.

## B5 — Matriz adversarial independiente
- C01 `issue` retorna changed=true tras COMMIT real; renovar READ crea un nuevo epoch (señal legítima).
- C02 `revoke` una sola transición changed=true; segundo revoke changed=false con epoch idéntico sin callback; null/foreign: ningún callback.
- C03 trigger SQLite RAISE(ABORT) revierte tanto epoch como revision; devuelve null y nunca llama publisher.
- C04 schema7/8, stage9 desactivado, closed store o Apply activo: no retorna cambio útil, no publicar.
- C05 callback `notifyChange` NO dentro de SQL `BEGIN IMMEDIATE` ni `withMaterialAdmission`; únicamente luego de retorno de port. Un callback que falla no cancela COMMIT: reportar `notified=false`, nunca fingir delivered.
- C06 jamás publicar saldos, principalHash ni epoch en URI; callback solo `Unit/Boolean` con URI fija. El resultado tipado no se convierte en permiso humano o lease UI.
- C07 paquete permitido `ar.com.faro.alpha.w01financedev`, otra aplicación no se debe activar; el package check no reemplaza firma/auth (DEV only). V2 Core 0 provider, W2 STOP y V2 productiva OFF.
- C08 No cubrir raw `sqlite3`/otros writers SQL fuera del nuevo port, procesos muertos ni race post-SELECT; mantener reconciliación ON_RESUME ya certificada; Android dos PID y G15 NOT_RUN. 
**Veredicto de revisión:** GO únicamente a READY exact7 y posterior B6 scoped, sujeto a protocolo y CI. Un test de APP sintético no prueba orden de COMMIT: exigir pruebas de STORE con DB real y rollback. No dar cierre W1 por esta unidad.

## B6
NOT_RUN.
## B7
PENDING, requiere pruebas específicas y guard por SHA.
