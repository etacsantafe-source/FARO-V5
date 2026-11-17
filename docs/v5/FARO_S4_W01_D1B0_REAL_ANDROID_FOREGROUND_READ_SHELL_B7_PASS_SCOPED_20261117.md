# FARO V5 — W01 D1B0 Android real foreground read shell, B7 PASS SCOPED
Fecha: 2026-11-17. Unidad `S4_W01_D1B0_REAL_ANDROID_FOREGROUND_READ_SHELL_DEV`. **W1 sigue ABIERTO**. W2 STOP.

## B1
HEAD previo `42d4050a2cc0ffbff9b92f9e29c6f91dc8354c71` con permiso READY EXACT4, guard READY `44172772942` COMPLETED SUCCESS. Preauditoría `docs/v5/FARO_S4_W01_D1B0_REAL_ANDROID_FOREGROUND_READ_SHELL_B1_B5_20261117.md` blob `89bcd1590a0e80c06406d32ced1d76fbc62cea6f`; auditoría adversarial B5 `docs/v5/FARO_S4_W01_D1B0_REAL_ANDROID_FOREGROUND_READ_SHELL_INDEPENDENT_B5_20261117.md` blob `0eb0fac3e2b98cde4ed9955840767b06acb575eb`. Preflight EXACT4 run dirigido PASS.

## B2
Commit funcional `f2fa5f72814ec3e23d153241244c758b74205f0b` contiene únicamente EXACT4 rutas:
1. `v5/app/src/w01FinanceDev/kotlin/ar/com/faro/v5/app/FaroV5W01FinanceDevActivities.kt`: subclass del launcher W01 sin reemplazar Chat y Activity separada `FragmentActivity` para futura lectura Finance. Instala `S4W01FinanceReadForegroundLifecycleAdapter` y `S4W01FinanceReadPrivateInvalidation` junto con reconciler existente. `newObservedFence` rechaza siempre porque **no hay SQL/issuer/biometría confiable**. Ni saldos ni autorizaciones en memoria/UI.
2. `v5/app/src/w01FinanceDev/AndroidManifest.xml`: launcher W01 subclass y nueva lectura `exported=false`; no altera provider URI privado ni servicio D1A.
3. `v5/app/src/w01AndroidTest/kotlin/ar/com/faro/v5/app/S4W01FinanceForegroundOwnerAndroidTest.kt`: dos tests de Android real, manifest privado, launcher preservado, denegación, STOP/RESUME/DESTROY.
4. `.github/workflows/faro-v5-s4-w01-d1b0-foreground-owner.yml`: preflight parent y Android SDK37 compile, emulador API35 2 pruebas.
No cambio shipping, S2, S3, RealityStore, FBS2v4, Chat productivo, W2, V4.4, main ni V2 productiva. Sólo W01 DEV aislado.

## B3
Workflow dirigido `44173197061`, job `134357494122`, SHA `f2fa5f72814ec3e23d153241244c758b74205f0b`: COMPLETED **SUCCESS**.
- Preflight parent `FARO_PROTOCOL_PREFLIGHT_PASS S4_W01_D1B0_REAL_ANDROID_FOREGROUND_READ_SHELL_DEV 42d4050a2cc0ffbff9b92f9e29c6f91dc8354c71`.
- `:app:assembleW01FinanceDev`, `:app:assembleW01FinanceDevAndroidTest`, `:app:processV2CoreDevManifestForPackage`: `BUILD SUCCESSFUL in 5m 24s`.
- Manifest empaquetado: `W01_D1B0_PRIVATE_FRAGMENTACTIVITY_ISOLATED_MANIFEST_PASS`; no Internet, reader privado, V2 Core sin providers/services.
- Android emulador real API35: `t01PrivateFinanceReaderLaunchesDeniedAndChatLauncherPreserved` `INSTRUMENTATION_STATUS_CODE: 0`; `t02PauseStopResumeAndDestroyDoNotReviveFinanceAuthority` `CODE: 0`. `OK (2 tests)`, `INSTRUMENTATION_CODE: -1`, `W01_D1B0_ANDROID_API35_REAL_FOREGROUND_2_OF_2_PASS_NO_SQL_OR_BIOMETRIC`.
- Guardia protocol funcional `44173196782`: COMPLETED SUCCESS.
- Regresión separada S3 Home `44173196847`: COMPLETED SUCCESS.
- Workflows antiguos de D1A (`44173196849`) y private invalidation (`44173196751`) figuran FAILURE **en su propio preflight de permiso previo** al retrigger por cambio del manifest compartido; no llegaron a compilar/testear. No atribuirlos a pruebas funcionales rojas ni proclamar `CI global 100%`. La guardia obligatoria vigente PASS y la CI D1B0 pasó. Corrección de auto-trigger viejo debe llevar permiso propio si se decide intervenir.

## B4
D1B0 conecta un **ciclo de vida real Android** y el observer privado, pero su lector deniega siempre. La prueba no puede demostrar autenticación BIOMETRIC_STRONG, nuevo registro de política SQL, revocación SQL real inter-PID, eficacia durante pérdida de process/IPC, saldos mostrados, aceptación física G15 o cierre completo de W1. No se usó el `S4W01FinanceReadAuthHost` dormido ni se concedió una lectura fingida.

## B5
Sin fuga ni permisos implícitos en el diff: el botón está dentro de launcher W01 DEV; Activity no exportada; lifecycle ON_RESUME no otorga derecho; ON_PAUSE/STOP invalida cache, ON_DESTROY cierra; denegación por construcción incluso cuando se fuerza `observedAfterResumeForDevOnly()`. No se cambió escritor Reality o datos persistentes. Resto de cierre requiere D1B1 nuevo B1–B5 y permiso propio.

## B6
IMPLEMENTED `f2fa5f72814ec3e23d153241244c758b74205f0b` con writer epoch1152 RELEASED antes de CI. Android API35 2/2 PASS.

## B7
**PASS_SCOPED_REAL_ANDROID_API35_FOREGROUND_ACTIVITY_LIFECYCLE_2_OF_2_NO_FINANCE_DATA**. Consumir permiso D1B0 a BLOCKED, registrar B7 atado al SHA exacto, continuar D1B1 montaje SQL/biometría real con nuevo scope y no repetir D0/D1A/D1B0. W1 OPEN; W2 STOP.
