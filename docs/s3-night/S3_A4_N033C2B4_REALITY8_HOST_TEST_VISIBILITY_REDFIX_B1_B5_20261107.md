# FARO V5 S3.A4 Reality8 host · B7 RED_FIX y B1–B5 test-only
Fecha local AR 2026-11-07. Último código `8c6110ecf25db3a510bcead29bad273cd48d62b7`, misma unidad `S3_A4_N033C2B4_REALITY8_TRANSACTION_HOST`.

## B1 — Evidencia exacta, causa del rojo
- Preauditoría del host `docs/s3-night/S3_A4_N033C2B4_REALITY8_TRANSACTION_HOST_B1_B5_20261107.md`, B7 previo RED_FIX sobre `7b136601` por Kotlin UNSAFE_CALL de callback. Redfix correctivo posterior `8c6110ecf25db3a510bcead29bad273cd48d62b7` cambió sólo `ReviewedConversationApplyCoordinator.kt`, guard #42435471080 SUCCESS.
- CI dirigida host #42435471097 job #128593247377: el código `:kernel:apply`, `:store:reality` y `:app:compileDebugKotlin` compiló correctamente; falla SOLO `:app:compileDebugUnitTestKotlin`, error Kotlin `INVISIBLE_REFERENCE` en `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5Planning8ReviewedApplyHostT2Test.kt:230`: `Cannot access fun integrityCheck():String: it is internal in RealityStore`. Kernel y SQL tests no llegaron a ejecutarse. No atribuir fallo de integración/SQL.
- La prueba de rollback ya comprueba `RecoveryRequired`, `reminders.rules.isEmpty()`, `executionReceiptLookup.find(tx)==null`, estado `RECOVERING_UNKNOWN`. Un `integrityCheck()` interno desde otra unidad es comprobación redundante que viola el aislamiento modular que exige FARO.

## B2 — Reutilización y alcance
- Conservar métodos propios `RealityStore.integrityCheck` internos para tests de módulo `:store:reality`, que ya los cubren; no hacer público, no crear fachada ni cambiar `RealityStore`.
- Conservar `FaroV5Planning8ReviewedApplyHostT2Test` y sus seis casos con SQLite real, sin alterar su ejercicio de recibo y rollback. Eliminar sólo la aserción redundante en test; NO tocar implementación, schema8, políticas, catálogo172, V4.4 ni S2.

## B3 — Investigación/hipótesis
- Kotlin visibility https://kotlinlang.org/docs/visibility-modifiers.html : `internal` significa accesible solo dentro del módulo, no del test de otro módulo; conservar encapsulación.
- Arquitectura Gradle multi-módulo https://docs.gradle.org/current/userguide/multi_project_builds.html : `:app:testDebugUnitTest` compila contra API del módulo `:store:reality`, no sus internos.
- Hipótesis: sin esa invocación, compileDebugUnitTestKotlin pasa; nuevo XML host 6/6 y `:kernel:apply:test` completo dejan 0 errors/failures/skips; si no, diagnosticar nueva evidencia y mismo unit RED_FIX.

## B4 — Cambio exacto
- Un solo archivo `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5Planning8ReviewedApplyHostT2Test.kt`, eliminar literalmente `assertEquals("ok", f.reality.integrityCheck())` de `receiptFailureRollsBackObjectAndLeavesNoFalseReceipt`; quedan assertions sobre ausencia de objeto/recibo y Review. No relajar workflow, conteos ni pruebas.
- Mensaje de código con `FARO-UNIT:S3_A4_N033C2B4_REALITY8_TRANSACTION_HOST`; permit READY para este único path y B7 pendiente, solo tras checkout de docs padre.

## B5 — Auditoría adversarial
- Los falsos positivos de ausencia de efecto se evitan comprobando directamente snapshot de Reality8 y receipt null; una prueba de integridad SQL de módulo distinto NO reemplaza esas pruebas.
- Nunca aumentar visibilidad de `integrityCheck` por este test ni suprimir test completo; ninguna activación Shipping V2, S4, Noche/Auto ni física G15.
- B5 **READY_TO_IMPLEMENT** solo test one-line; no work permit para nueva función. Futuro B7 requiere evidencia CI exact SHA y guard SUCCESS.

## B7 — Dictamen del código anterior
- `8c6110ecf25db3a510bcead29bad273cd48d62b7` **RED_FIX**, compilación principal OK pero suite test falló `INVISIBLE_REFERENCE` antes de ejecutarse. No se puede certificar host ni A4 con ese SHA. Este archivo es evidencia independiente previa a la corrección, usada por el protocolo.
