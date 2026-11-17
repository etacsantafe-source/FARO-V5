# FARO V5 — S4 W01 B3B1B1B1B1B0 DEV READ lifecycle, B7 PASS acotado
Fecha AR: 2026-11-17. Unidad `S4_W01_B3B1B1B1B1B0_DEV_READ_HOST_LIFECYCLE_ABORT`. No G15 y no UI productiva.

## B1 — Preauditoría y control antes de código
Preaudit B1–B5 `docs/v5/FARO_S4_W01_B3B1B1B1B1B_ANDROID_INVALIDATION_LIFECYCLE_B1_B5_20261117.md`, guard `44128012958` SUCCESS. Independiente B5 `docs/v5/FARO_S4_W01_B3B1B1B1B1B0_LIFECYCLE_INDEPENDENT_B5_READY_20261117.md` blob `d5179b845504f51c803b25f602b22cac3e9152fe`, guard `44128251832` SUCCESS. Permiso exact4 READY `c52db3c85bc97c2a39c5cd0ec7f6cc2b2aec7916`, checkpoint parent `6fd53e19bb2abb9ac1387c291aa1eb1123cc4667`, guard `44128691044` SUCCESS; no repetir previos B7 de SQL revoker ni BiometricPrompt consent.

## B2 — Diff exacto, scope DEV y no contaminación
Código único `fce49a3cee5b1836f7088f5cd484e12e702b0254`, marcador `FARO-UNIT:S4_W01_B3B1B1B1B1B0_DEV_READ_HOST_LIFECYCLE_ABORT`. GitHub compare confirma SOLO cuatro archivos:
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadSessionLifecycle.kt` nuevo.
2. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadAuthHost.kt` modificado.
3. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01FinanceReadSessionLifecycleTest.kt` nuevo.
4. `.github/workflows/faro-v5-s4-w01-b3b1b1b1b1b0-lifecycle-directed.yml` nuevo.
Sin modificaciones de manifest, providers, SQL ACL, Finance ledger, Compose surface, shipping CompositionRoot, rutas V4.4/main/S2/S3/FBS2v4, Apply, receipts ni W2. V2 productiva OFF.

## B3 — Resultado verificable de código
- El control Kotlin `S4W01FinanceReadSessionLifecycle` es irreversible mediante AtomicBoolean; `onStop/onDestroy` invocan terminación exact once, `onResume` NO reactiva; `close()` explícito silencia callbacks posteriores.
- El host READ DEV DORMANT registra `LifecycleEventObserver` en hilo principal, cierra/cancela al recibir `ON_STOP` o `ON_DESTROY`, impide aceptar callbacks tardíos tras pérdida foreground y elimina observer en `finish`. El cierre desde otro hilo invalida inmediatamente el fence y agenda el cleanup Android en hilo principal.
- No se emite policy READ nueva sin acción explícita biométrica posterior. Esta unidad NO hace que un DTO o saldo ya mostrado se elimine de Compose; la pantalla no está montada y necesita otro owner de readPort.

## B4 — CI exacta y artefacto
- Guardia protocolo `44129165327` completed SUCCESS para SHA de código, incluido script de preflight.
- Workflow dirigido `44129165643`, job `134201146059`, SHA exacto `fce49a3cee5b1836f7088f5cd484e12e702b0254`, COMPLETED SUCCESS. Preflight oficial sobre `HEAD^`: `FARO_PROTOCOL_PREFLIGHT_PASS S4_W01_B3B1B1B1B1B0_DEV_READ_HOST_LIFECYCLE_ABORT 6fd53e19bb2abb9ac1387c291aa1eb1123cc4667`.
- Gradle `:app:testDebugUnitTest` con 8/8 JUnit nuevos, cero failures/errors/skips por verificador XML (nombres exactos R01–R08), `:app:compileV2CoreDevKotlin` BUILD SUCCESSFUL. Logs: primer BUILD SUCCESSFUL en 4m; segundo BUILD SUCCESSFUL en 29s.
- Verificador de wiring del host Android/DORMANT y no product mount SUCCESS; mensajes:
  `W01_B3B1B1B1B1B0_LIFECYCLE_JVM_8_OF_8_PASS`,
  `W01_B3B1B1B1B1B0_DEV_ANDROID_COMPILE_AND_WIRING_PASS`,
  `NO_ANDROID_G15_BIOMETRIC_OR_UI_REVOCATION_ATTESTED`.
- Artefacto XML `faro-v5-w01-b3b1b1b1b1b0-lifecycle-junit` ID `14349691349` existe, 691 bytes (ZIP no inspeccionado aparte, datos exactos sustentados por step XML). Kotlin reportó avisos no fatales REDUNDANT_ELSE_IN_WHEN; no ERROR de compilación.
- Al evaluar SHA funcional, cuatro de seis workflows globales estaban COMPLETED SUCCESS y dos en curso; no atribuirles PASS global ni invocarlos como condición del B7 acotado. No repetir certificaciones.

## B5 — Dictamen residual adversarial
PASS: ocho casos JVM STOP/DESTROY exact once, close silencioso, repetición, resume sin resurrección, race 16 hilos, excepción terminal fail-closed, late stop; wiring `LifecycleEventObserver` en host compilado Android DEV.
NO PASS: pruebas físicas de lifecycle Android, UI Compose con cuentas reales, ContentProvider/notifyChange cross-process, existencia de publisher para escritores SQLite crudos, señales perdidas y revalidación duradera al RESUME, TTL en pantalla, BiometricPrompt hardware G15, consentimiento real/producto. Un callback runnable o proceso externo aún podría haber revocado después del último SELECT; no se promete evitar la ventana de render. `B3B1B1B1B1B1` publisher+readPort y `B3B2` G15 continúan OPEN/NOT_RUN con B1–B5 propio, sin READY.
Riesgo de callback de Android fuera de JVM solo acotado por compilación estática, NO certificado Android físico.

## B6
IMPLEMENTED commit `fce49a3cee5b1836f7088f5cd484e12e702b0254`, preflight GitHub, 8/8 XML y Android DEV compile SUCCESS, writer RELEASED epoch1118 antes de CI.

## B7
**PASS_SCOPED_DEV_READ_HOST_LIFECYCLE_8_JVM_ANDROID_COMPILE**. Consumir permiso READY a BLOCKED, registrar SHA y proof B7, checkpoint final, guard documental y writer RELEASED. NO marcar W1 closed, W2 sigue STOP, V2 productiva OFF; S3 DEV previo 5/5 con H07 excepción humana, no PASS.
