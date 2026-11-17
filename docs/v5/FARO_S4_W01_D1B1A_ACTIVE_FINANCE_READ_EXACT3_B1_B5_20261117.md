# FARO V5 — D1B1A ACTIVE Finance snapshot bounded read — B1–B5
2026-11-17. Unit `S4_W01_D1B1A_ACTIVE_FINANCE_SNAPSHOT_READ_FENCE_DEV`. DOCUMENTAL ONLY: NO READY, NO B6/B7. W1 OPEN W2 STOP.

## B1
Base HEAD `640c5ea883608bebab2502c0f9d9a29a818adf3a`; writer 1155 RELEASED; protocol guard `44188954584` SUCCESS. Decisión explícita de UX en `docs/v5/FARO_S4_W01_SECURITY_OPTIONAL_DEFAULT_OFF_USER_DECISION_20261117.md` blob `ac773266ae4fc34c79c53cbdf5558fc8259299b4`: seguridad opcional apagada por defecto; sin PIN/huella obligatoria. D1B0 real FragmentActivity B7 scoped 2/2 Android API35, no repetir. D1B1 ruta B1–B5 previa `docs/v5/FARO_S4_W01_D1B1_ACTIVE_FINANCE_LOCAL_READ_B1_B5_20261117.md` blob `a983776a78275defa3e5598ca7961f7e78755b5c`, B5 anterior blob `f4aed7a85bc91c9761e795bac1c62819e7089d24`.
Fuentes exactas: `FinanceModel.kt` blob `22f9444f931aff356836bf61bffb10e9975142b7` define `FinanceSnapshotReadApi` con `revision`, cuentas, asientos y `ledgerMinor`. `FaroV5V2CoreDevAuthority.kt` blob `78a394142ad68760d9449bca2dc5dc0de8f00cd6` usa el único dueño ACTIVE y `FinanceSnapshotReadApi { graph.reality.financePersistence.read() }` con autorización por sesión, y cierra `graph` en `Session.close()`. `FaroV5AndroidSandboxPrincipal.kt` blob `38890c7e851a3556fa358a7bed7f864a2c059c84` controla package/UID/active pointer/host epoch. `FaroV5GenerationStoreGraph.kt` blob `e6578207046f3a3c9288d8d6a67ea4575890e606` prohíbe STAGED Finance9 como ACTIVE; no llamar Finance9-stage.
## B2
Reusar API de lectura Finance de dueño existente y `AndroidSandboxTechnicalEvidence` sin nueva SQLite ni staging/migración. V4.4 AtomicApply/receipts/Undo/recovery intactos. No tocar shipping, main, V2 productiva, S3, W2, ui, Activity ni manifests.
## B3
Se descartan abrir STAGED9 desde Activity (rompe invariante), fingir firma biométrica (rompe decisión de usuario), saltar ACL con `true` o crear otro GraphOwner (dos writers). Se elige un **contrato observacional DEV** con entrada proporcionada por la única sesión ACTIVE, sandbox verificado por futuro host Android real, guardias antes/entre/después de doble lectura. No equivale a datos certificados Finance9.
## B4
Funcional EXACT3:
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01D1B1AActiveFinanceSnapshotRead.kt`
2. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01D1B1AActiveFinanceSnapshotReadTest.kt`
3. `.github/workflows/faro-v5-s4-w01-d1b1a-active-finance-read.yml`
1 es Kotlin `internal` guard de lectura informativa, sin estado persistente ni Apply. Inyecta `FinanceSnapshotReadApi`, pointer completo, host epoch, session ACL epoch, estado foreground/ownerReady, UID W01 y evidencia técnica del sandbox. Dos snapshots idénticos con revisión coherente, límite 100 cuentas/5000 asientos, cálculo ledger con `Math.addExact` para descartar overflow. Cualquier contradicción revoca sesión irrevocablemente, sin caches. Devuelve DTO observado marcado ACTIVE_FINANCE_SNAPSHOT_NOT_FINANCE9, no lease de UI. 2 contiene 10 JUnit con falsos inyectados y casos adversariales, NO prueba Android. 3 workflow CI nuevo: parent preflight READY exact3, SDK37, Gradle JUnit + compile W01 y comprobar XML 10/10, no shipping/biometría.
## B5
Auditoría adversarial separada `docs/v5/FARO_S4_W01_D1B1A_ACTIVE_FINANCE_READ_INDEPENDENT_B5_20261117.md`: falsificación por inyección JVM no equivale a autorización real Android. El código NO se monta en pantalla; sólo la posterior D1B1B puede enlazar el único dueño ACTIVE. Los campos `FinanceSnapshot` NO son permiso para render. Rechazar ABA, drift ACL/host/UID y regrant tardío, cambios silenciosos de contenido con misma revision, overflow, close durante lectura, monedas mezcladas.
## B6
NOT_RUN, requiere B5 + guard + permiso READY individual.
## B7
NOT_RUN. No afirmar que W1 o D1B1 finalizó.