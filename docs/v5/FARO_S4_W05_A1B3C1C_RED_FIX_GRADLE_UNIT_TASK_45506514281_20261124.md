# FARO V5 · W05-A1B3C1C · RED_FIX task selection · 2026-11-24

## B1 — Fallo comprobado en CI real
Código `39d458c84fcfddf12034d0265966a707c6c9b66e`, commit único de seis rutas. Guardia `45506514385` COMPLETED SUCCESS; CI dirigida `45506514281`, job `138843003609`, FAILURE. Preflight exact6 SUCCESS, Java21 y Android SDK37 instalados SUCCESS. La etapa Gradle terminó antes de ejecutar JUnit por selección de tarea: `Cannot locate tasks that match ':app:testV2CoreDevUnitTest' as task 'testV2CoreDevUnitTest' not found in project ':app'.` No XML de los tests nuevos; no se atribuye PASS funcional.

## B2 — Comparación, precedentes y reutilización
Workflow certificado de V2 DEV `.github/workflows/faro-v5-v2-core-directed.yml` ejecuta `:app:testV2CoreDevUnitTest` agregando `-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false`. También `.github/workflows/faro-v5-s3-a5-dev-host-reality8-directed.yml` usa el test de esa variante. El nuevo workflow C1C omitió la propiedad; el problema es de invocación Gradle, no se comprobó error de Kotlin, SQLite ni de las políticas de lectura. No tocar clases de C1B, Kotlin C1C o pipelines previos por inferencia.

## B3 — Opciones y decisión
A ADAPTAR: copiar la opción Gradle utilizada por el workflow V2 CORE dirigido, para habilitar unit test de custom build type y ejecutar la selección explícita de clases. B DESCARTAR: quitar `:app:testV2CoreDevUnitTest` o afirmar PASS con solo `:store:generation:test` dejaría Android sin prueba. C DESCARTAR: modificar `v5/app/build.gradle.kts` / sourceSet fuera de permiso sería expansión de alcance innecesaria. La investigación externa del B3 original C1C sigue vigente, el nuevo hecho es una diferencia del proyecto concreto y cuenta con precedente en repositorio.

## B4 — RED_FIX mínimo de UNA ruta
Única ruta de código permitida: `.github/workflows/faro-v5-s4-w05-a1b3c1c-android-sql11-reopen.yml`.
Agregar `-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false` antes de las tareas de Gradle, admitir preflight parent de seis rutas original O una ruta exacta para este RED_FIX y conservar el assert de XML original. No tocar seis archivos funcionales anteriores, ni main, manifiestos o las variantes históricas.

## B5 — Auditoría adversarial previa
R01 Gradle reconoce tarea V2 CORE y compila los fuentes de `src/v2CoreDev` y `src/testV2CoreDev` sin desactivar el build productivo.
R02 JVM del nuevo host SQL11 también ejecuta sus tests; XML real para ambas clases, ningún skipped/error/failure.
R03 Workflow valida uno o seis paths exactos, nunca wildcard ni expansión silenciosa; el permiso RED_FIX limita cambios a la ruta CI.
R04 `V2_ACTIVE_ENABLED` OFF, pagos/Auto/Noche/stock Apply OFF; sin reabrir C1B ni W1–W4.
R05 Si Kotlin/JUnit falla después de esta corrección, capturar logs y XML; B7 sigue NO PASS y requiere otra RED_FIX fundamentada, sin ensayo y error especulativo.

## B7 — Disposición del commit funcional anterior
`39d458c84fcfddf12034d0265966a707c6c9b66e` → **RED_FIX**, NO PASS. Causa concreta: task selection CI (sin XML). Guardia de protocolo previa verde. La modificación del workflow es necesaria antes de cualquier certificación de C1C. Evidence2/stock A1C siguen pendientes. Writer y permiso se gobiernan por puntero actual, no por este documento histórico.

## Siguiente
Después de guardia documental SUCCESS: permiso READY de la MISMA unidad con `last_code_review.result=RED_FIX`, evidencia este documento B7, una sola ruta workflow. Commit funcional RED_FIX exact1, guardia y CI/XML nuevos, B7 según resultados reales.
