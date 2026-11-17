# FARO V5 — W01 D0: revisión B5 independiente y corrección de la preauditoría
Fecha 2026-11-17 AR. Unidad `S4_W01_D0_ISOLATED_DEV_LAUNCH_PACKAGE_ALLOWLIST`.
Estado: **GO CON CORRECCIONES DE DISEÑO, NO READY HASTA GUARD DOCUMENTAL**.

## B5 — Revisión adversarial independiente
Se releen (sin repetir CI histórica) el archivo `v5/app/build.gradle.kts` blob `06efa77d0b8bb09911b0a74fb200e8d883fe1464`, manifest W01 blob `ef4b2e16d82cd9535fb4c1645fa9599341f739f1`, launcher Kotlin blob `25ff91deb1d79013f7709c869eb7cd66d234fc0f`, protocolo gate `scripts/quality/faro-protocol-gate.py` blob `e1807934365637488625ae19a2db0434b3b4f747`, y cierre C código `e68549b1b1ee2155aea37e27d4f8861661db1316`.
Revisión externa primaria: https://developer.android.com/build/build-variants y https://developer.android.com/build/configure-app-module; `applicationIdSuffix` exige comparar el ID de aplicación final, no el namespace de Kotlin.

1. **RED corregido antes de READY**: B4 proponía prueba JUnit en `src/test` pero esa fuente NO incluye `src/v2CoreDev/kotlin` en variante debug. Se corrige el path a `src/testV2CoreDev/kotlin`, tarea `:app:testV2CoreDevUnitTest` y compilaciones W01/V2 Core. No ocultar el error cambiando la prueba para que no compile ni agregando dependencia Android a JVM.
2. **RED corregido antes de READY**: B5 anterior decía ambiguamente «s3/shipping deben fallar». El ID `ar.com.faro.alpha.s3integrateddev` ya estaba permitido y NO debe dejar de estarlo; W01 se agrega como CUARTO ID exacto. Rechazar `ar.com.faro.alpha.s3integrateddev.extra`, `ar.com.faro.alpha.x03aidev`, `ar.com.faro.alpha`, `ar.com.faro.alpha.v5dev`, `ar.com.faro.alpha.w01financedev.extra`, nombre vacío, variantes de mayúsculas.
3. Conservar el `onCreate` con `check(debuggable)` y `check(!V2_ACTIVE_ENABLED)`; no extraer ni relajar esos controles por la variante W01.
4. Criterios de autenticidad: compilar `compileW01FinanceDevKotlin` y `compileV2CoreDevKotlin`, `testV2CoreDevUnitTest` con XML exacto, analizar packaged manifests W01 (un provider privado) y V2 Core (cero), sin INTERNET ni cambio de launcher; no inventar éxito de inicio Android físico ni dos PID.
5. Exclusiones absolutas: Finance9 policy/grants/ledger, `ContentObserver`, provider, AndroidManifest, Gradle, S3, V4.4, main, S2, W2 y shipping UI. Sólo `FaroV5V2CoreDevActivity.kt`, NUEVO `S4W01DevLaunchPackagePolicyTest.kt` en testV2CoreDev, NUEVO workflow exacto, con firma `FARO-UNIT` del permiso.

## Dictamen
GO **sólo** para una unidad D0 exact3 con esos tres paths y evidencias B1–B5 tras pasar la guardia documental. La corrección de la preauditoría se publica en ESTE mismo checkpoint. Si la guardia falla: STOP sin READY. D1 dos PID+life-cycle requiere auditoría y permiso distintos; no declarar W1 cerrado.
