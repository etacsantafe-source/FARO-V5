# FARO V5 — S3.A5 post-cutover integración: B7 RED_FIX test Kotlin
Fecha 2026-11-08 AR, unidad `S3_A5_POST_CUTOVER_DEV_INTEGRATION`.

## B7 — Fallo observado, sin falso PASS
- B1–B5 existentes `S3_A5_POST_CUTOVER_DEV_INTEGRATION_B1_B5_20261108.md` y permiso previo `5d0ba32c20c4926954b33a8e9b3e14f398e98d4f`.
- Código nuevo `03f5af2ae9e1382c7a2f0ff0b9d51049868e374d`, dos archivos test+workflow, sin alteraciones productivas.
- CI dirigida [#42536498612](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42536498612), job `128901843004`: **COMPLETED FAILURE** en `:app:compileV2CoreDevUnitTestKotlin` después de haber compilado `:app:compileV2CoreDevKotlin`. Error `ILLEGAL_SUSPEND_FUNCTION_CALL` archivo `FaroV5S3A5PostCutoverIntegrationTest.kt`: línea 101, `suspend fun search(query)` fuera de coroutine. JUnit XML **NO ejecutado**, 5/5 **NO** demostrado. Guard de protocolo `#42536498470`: COMPLETED SUCCESS en mismo SHA.
- La causa afecta exclusivamente a tres funciones de prueba que llaman `search` desde métodos `@Test` no suspend: `fourRealOwnerSearchAndDurableChatWorkTogetherOnlyAfterFreshEight`, `oldSevenSearchHitAndContentCannotAppearInFreshEight`, `pointerAbaOfActiveEightRevokesChatSearchAndOldHit`.

## RED_FIX delimitado
- Cambiar solo las tres funciones mencionadas a `= runBlocking { ... }`, patrón ya existente en `FaroV5G5ActiveFourOwnerSearchTest.kt` y `FaroV5V2CoreDevAuthorityTest.kt`; no cambiar asserts, fixture, datos, producción ni workflow.
- Subir UN commit test-only con el mismo trailer `FARO-UNIT:S3_A5_POST_CUTOVER_DEV_INTEGRATION`; correr la nueva CI auto y verificar XML exacto 5/5, código SHA y guard. Si nuevo fallo distinto, diagnosticarlo con evidencia antes de otro intento. Limitar a dos correcciones por hipótesis.
- S3.A5 OPEN; Android física del G15 final; V2 productiva/S4/Auto/Noche OFF, 172 operaciones oficiales sin cambio.
