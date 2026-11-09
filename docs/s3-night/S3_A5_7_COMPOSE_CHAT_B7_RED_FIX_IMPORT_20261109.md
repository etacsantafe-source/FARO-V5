# FARO V5 — S3.A5.7 Compose Chat Android B7 RED_FIX (solo import Kotlin)
Fecha 2026-11-09 AR. Unidad S3_A5_7_COMPOSE_CHAT_REAL_ANDROID; A5 global OPEN.

## B7 — Diagnóstico independiente de CI roja
- Baseline A5.7 Compose original: B1–B5 docs/s3-night/S3_A5_7_COMPOSE_CHAT_REAL_ANDROID_B1_B5_20261109.md, blob 320d7895b4998f0e988266005bab9a647a169374; permiso READY en padre 4ec342c27d5472e9bf29c1968e079a44f4621a09.
- SHA código d0c61074cd9631fb3f82fa0cff25fcfd1498565d, solo DOS archivos NUEVOS: test Compose e instrumentación workflow. CI exacta https://github.com/etacsantafe-source/FARO-V5/actions/runs/42592348610, job 129076742903, COMPLETED FAILURE; guard B1–B7 https://github.com/etacsantafe-source/FARO-V5/actions/runs/42592348491 COMPLETED SUCCESS en mismo SHA.
- Log de CI en task `:app:compileV2CoreDevAndroidTestKotlin`: Kotlin compiler error `UNRESOLVED_IMPORT`, `Unresolved reference 'assertExists'`, `FaroV5S3A57ComposeChatUiTest.kt:9`; `BUILD FAILED in 5m 38s`. APK DEV build `:app:assembleV2CoreDev` fue completado, test APK no compiló. Instrumentación `OK (2 tests)` = NOT_RUN, Android emulado NO CERTIFICADO; no falso PASS.
- Causa concreta: `SemanticsNodeInteraction.assertExists()` es método invocable directamente; `import androidx.compose.ui.test.assertExists` no existe en la versión de librería. Remover únicamente ese import; NO alterar llamadas, afirmaciones, test count, lógica de Activity, Build/manifest ni workflow. No se detectó fallo del código productivo.
- Comparación externa y actual: docs https://developer.android.com/develop/ui/compose/testing y ejemplos Android instrumentación de FARO importan `onNodeWithText` y operan sobre objetos de interacción, no importan `assertExists`. Clasificación ADAPTAR import, sin nuevas dependencias.

## RED_FIX — Permiso mínimo, B5 y siguientes
- Cambiar SOLO `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57ComposeChatUiTest.kt`: suprimir línea `import androidx.compose.ui.test.assertExists`; diff de UNA línea. Preservar los dos tests completos.
- Se actualiza `last_code_review` RED_FIX en permiso para SHA d0c61074 (mismo unit_id), B1–B5 idénticos y VERIFIED; nueva ruta única permitida y trailer `FARO-UNIT:S3_A5_7_COMPOSE_CHAT_REAL_ANDROID`. No ejecutar suite histórica 33/33, 4/4, 2/2. G15 física final.
- Tras commit, esperar CI en SHA nuevo: APK, `OK (2 tests)`, `INSTRUMENTATION_CODE: -1` y protocolo guard. Si otra falla distinta, nuevo B7 y diagnóstico, no arreglos a ciegas. S3.A5 OPEN, A3 172 NO APROBADO, V2 productiva/S4/Auto/Noche OFF.
