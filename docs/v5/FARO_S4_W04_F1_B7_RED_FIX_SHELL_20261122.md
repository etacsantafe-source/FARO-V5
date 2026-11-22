# FARO V5 — W04-F1 · B7 RED_FIX del workflow JVM
Fecha 2026-11-22. Unidad SAME S4_W04_F1_AUTHORIZED_HISTORICAL_READ_COORDINATOR_DEV. Código inicial 03be898c99605b2c51abdc0dc8c540d165cebe75, permiso READY 8c7bc77fbff0ecd39067b3d9e4d3634033cd4b56, preaudit F1 blob ac70446ee71cd2d5a46dd556c006e72fc4677e1b, guardia código 45052666972 SUCCESS.

## B7 — RED_FIX, NO PASS
CI dirigida 45052667038 **COMPLETED FAILURE**, job 137301007397. El protocolo y preflight exact3 SUCCESS; Java21 y SDK37 SUCCESS. El paso «F1 JVM tests exact eight» emitió Gradle `BUILD SUCCESSFUL in 1m 41s` **solo para Task :help** porque el comando se cortó por salto físico de línea. Después el shell ejecutó la segunda línea `:app:testDebugUnitTest --tests ...` como programa independiente y devolvió exit code 127 (`:app:testDebugUnitTest: command not found`). **No se compilaron tests F1, no se ejecutó JUnit5, ningún XML y ningún PASS F1.** El marker de prueba no apareció. No implica bug funcional y no autoriza declarar ocho PASS.

## B5 — Delta exclusivo de reparación
- Cambiar exclusivamente `.github/workflows/faro-v5-s4-w04-f1-historical-read-coordinator.yml`: escribir el comando `./gradlew --no-daemon --max-workers=2 --no-parallel --no-configuration-cache :app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04HistoricalAuthorizedReadCoordinatorTest` como **una sola línea física de shell**. Conservar protocolo/preflight HEAD^, nombres de 8 tests, XML, Java21 SDK37, artefacto.
- **No cambiar Kotlin, fuentes D0–D4, E, Gradle app, owner, licencia, main, permisos productivos, Finance, Apply ni UX**. No repetir certificados.
- Una vez publicado este RED_FIX DOC_ONLY y guardia SUCCESS, actualizar permiso SAME UNIT READY exact3 con `last_code_review.result=RED_FIX` referenciando blob de este documento y B7. Solo entonces código correctivo en una ruta, marker FARO-UNIT igual, writer CAS.
- CI dirigida a SHA corregido; exigir 8/8 JUnit5 distintos XML, guardia protocolo SUCCESS y ZIP inspeccionado. Si compila/test falla por otra causa, registrar nueva RED_FIX; no improvisar nuevas rutas.
- W04-F2/F3 y global siguen abiertos. Sin stock, pagos, Apply, V2 productiva, físico Moto G15 ni red externa.
