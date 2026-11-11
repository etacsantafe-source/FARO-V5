# FARO V5 — S3.A5.7 X04/X05 — B7 RED_FIX Android runtime (tercero)

Unidad: `S3_A5_7_X04_X05_ANDROID_GUARDADOS`. Commit auditado: `9a84febb0b39fbd465ac7ffd51d7ee3dc4cb6823`.
CI Android API35: [run 43075795652](https://github.com/etacsantafe-source/FARO-V5/actions/runs/43075795652), job `130715161808`, conclusión FAILURE. Guardia `seven-block-evidence`: run `43075795713` SUCCESS.

## B7 — RED_FIX de instrumentación real

- `assembleV2CoreDev` y `assembleV2CoreDevAndroidTest` PASS. Ambos APK fueron instalados en el emulador Android API35. Instrumentación ejecutó exactamente **2 tests**; ambos FAIL (no confundir `INSTRUMENTATION_CODE: -1` con PASS cuando aparece `FAILURES!!!`).
- X05, método `x05RealRemoveExplicitRecoveryRestoreAndReopenCanonicalSource`: `ComposeTimeoutException` 30 s en `confirmRealNativeReview` línea 173, esperando título `Confirmar Guardados` y mensaje `Quitar referencia de Guardados`. El log **no demuestra** si el diálogo fue suprimido por una admisión de lectura/autoridad, si no fue accesible mediante UiAutomation o si hubo desfase. No inventar causa.
- X04, método `x04RealSaveConfirmNativeReadAndOpenOriginalChat`: `AssertionError` línea 130: 0 nodos visibles a semántica para `Guardar / Quitar`, tras encontrar mensaje sintético. Posibles asincronías/lazy layout o falta de binding deben discriminarse mediante observaciones; no saltarse ese gesto.
- Brecha: la prueba previa X05 alcanzó el paso `Quitar referencia`, pero X04 no encontró la acción en ejecución posterior dentro del mismo proceso de instrumentación. Distinguir inter-test state y UI visibility, sin deducir que persistencia/owner está roto.
- Corrección permitida exclusivamente en `FaroV5S3A57BookmarksUiTest.kt`: esperar con límite y desplazar UI cuando sea necesario para hallar `Guardar / Quitar`; agregar diagnósticos **no sensibles** de conteos de semántica y estados nativos; confirmar el diálogo por la ventana Android real y registrar si la vista entra en estado `No disponible`. Si el control verdaderamente no está expuesto o el adaptador de producción falla, parar `NEEDS_PREP` y pedir permiso separado; nunca abrir el scope funcional.
- No silenciar fallos, no inventar X04/X05 PASS, no falsificar `OK (2 tests)`/XML, no aumentar timeout para maquillar defectos. La próxima CI debe registrar 2 tests realmente completos con logs, APKs, XML y guard SUCCESS en SHA exacto antes de B7 PASS.
- A3 M01–M05 y B1–B5 de X04/X05 conservan sus certificaciones; A5 global OPEN, Moto G15/S4/Auto/Noche/main/V2 productiva OFF/intactos.
