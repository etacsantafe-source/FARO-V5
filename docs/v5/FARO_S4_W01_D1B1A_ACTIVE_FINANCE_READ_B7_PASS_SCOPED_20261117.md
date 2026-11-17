# FARO V5 — W01 D1B1A ACTIVE FinanceSnapshot read-only fence — B7 PASS acotado
2026-11-17 · unidad `S4_W01_D1B1A_ACTIVE_FINANCE_SNAPSHOT_READ_FENCE_DEV`. W1 OPEN, W2 STOP, V2 productiva OFF. **NO** es UI Finanzas terminada, ni Finance9 ACTIVE, ni una prueba física/Android de autenticación.

## B1
Base del código: permiso READY EXACT3 en `113a705cc2c0e811f658e5f11fd74a28fddf33f8`, guard `44191246949` COMPLETED SUCCESS. Preauditoría `docs/v5/FARO_S4_W01_D1B1A_ACTIVE_FINANCE_READ_EXACT3_B1_B5_20261117.md` blob `d40bbe6ef50ea6521850f84f6499687ffab53b7e`, B5 independiente `docs/v5/FARO_S4_W01_D1B1A_ACTIVE_FINANCE_READ_INDEPENDENT_B5_20261117.md` blob `321f11e72e636bb748541008fb11fd1a3f21ce7c`. Decisión humana seguridad optativa OFF por defecto documentada antes; no se necesita biometría para el futuro acceso normal.
D0, D1A, D1B0 B7 ya certificados: NO repetidos.

## B2
Commit funcional `0900e75135c913cf11b70915b7e7561006ee87f1` con FARO-UNIT y **tres rutas exactas**, GitHub compare contra parent READY verificó `ahead_by=1`:
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01D1B1AActiveFinanceSnapshotRead.kt`, `internal` guard de FinanceSnapshot ACTIVE sin SQLite propia, sin almacenar datos ni montar Activity. UID del sandbox W01, pointer completo, epoch de host, ACL de sesión, foreground/ownerReady, dos muestras idénticas, verificación de ledger sin overflow y cancelación irrevocable. El DTO está marcado `ACTIVE_FINANCE_SNAPSHOT_NOT_FINANCE9`, y **NO** es un permiso para render.
2. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01D1B1AActiveFinanceSnapshotReadTest.kt`: diez JUnit JVM con fuentes falsas e inyección, positivos/negativos.
3. `.github/workflows/faro-v5-s4-w01-d1b1a-active-finance-read.yml`: CI dirigida, preflight parent exact3, JUnit y W01 Kotlin compile.
No se modificó el RealityStore, Finance9, migración, journal, ACL durable, llave biométrica, Chat, D1B0 Activity, V2 productiva, S2/S3, W2, V4.4/main o superficie UI.

## B3
Workflow dirigido `44191824683` job `134422931814` commit `0900e75135c913cf11b70915b7e7561006ee87f1`: COMPLETED **SUCCESS**.
- `FARO_PROTOCOL_PREFLIGHT_PASS S4_W01_D1B1A_ACTIVE_FINANCE_SNAPSHOT_READ_FENCE_DEV 113a705cc2c0e811f658e5f11fd74a28fddf33f8`.
- `:app:testDebugUnitTest --tests ...S4W01D1B1AActiveFinanceSnapshotReadTest` y `:app:compileW01FinanceDevKotlin`: `BUILD SUCCESSFUL in 3m 52s`.
- XML JUnit verificado exactamente `(tests,failures,errors,skipped)=(10,0,0,0)`, lista de 10 pruebas específica, marcadores `W01_D1B1A_ACTIVE_FINANCE_READ_10_OF_10_JVM_PASS`, `W01_D1B1A_ANDROID_W01_DEV_KOTLIN_COMPILE_PASS_NO_UI_MOUNT`, `W01_D1B1A_NO_BIOMETRIC_NO_STAGE9_ACTIVE_NO_SECOND_DB`.
- Guardia obligatoria funcional `44191824581`: COMPLETED SUCCESS. Diff EXACT3 verificado por GitHub compare READY→código; writer de código epoch1156 RELEASED antes de pruebas.

## B4
Alcance de pruebas:
- r01 no foreground ni owner vivo; r02 dos snapshots idénticos con saldos separados ARS/USD; r03 UID/paquete falsos; r04 pointer ABA; r05 host epoch drift; r06 revocación y regrant ACL; r07 revisión cambiada; r08 payload cambiado sin revision; r09 overflow ledger; r10 error del owner o close durante read.
- Son pruebas de **fences JVM con callbacks/fuentes sintéticas**. No se probaron Android OS UID real, SQLite de Finance activo, acceso real a usuario, revocación real cross-process, autenticación hardware, ciclo completo de pantalla, emulador o G15. No inferir que las cuentas reales ya aparecen.
- La variante W01 compiló; el puerto permanece sin montar, porque D1B1B debe resolver un único dueño de sesión y su lifecycle y comprobar hechos reales del Android Application. Finance9 sigue STAGED-only, D1B1C necesitará su propio cutover y permisos.

## B5
Auditoría final: todas las rutas nuevas están dentro del permiso EXACT3; no hay import ni invocación de `BiometricPrompt`, `S4W01FinanceReadAuthHost`, Finance9-staged ni segundo `RealityStore`. El tipo DTO observado no otorga ACL ni lease para una pantalla; el futuro host debe comprobar su propia fuente. Estado de usuario no modificado.
Ninguna prueba previa se rehizo por rutina. W2 STOP, no API paga ni transferencias bancarias reales.

## B6
`IMPLEMENTED` en `0900e75135c913cf11b70915b7e7561006ee87f1`; 10/10 JVM y W01 Kotlin compile SUCCESS.

## B7
**PASS_SCOPED_DEV_ACTIVE_FINANCE_SNAPSHOT_DOUBLE_READ_FENCE_10_OF_10_JVM_AND_W01_COMPILE.**
Consumir READY a BLOCKED, registrar B7 SHA, checkpoint y writer RELEASED.
Próxima unidad **D1B1B**: enlazar este read-only adapter a la ÚNICA Session ACTIVE real de W01 sin segundo grafo, comprobar UID/pointer/epoch reales y lifecycle antes de cualquier saldo UI; preparar B1–B5/READY propios. Luego D1B1C y W1 funcional; NO repetir D1B1A.
