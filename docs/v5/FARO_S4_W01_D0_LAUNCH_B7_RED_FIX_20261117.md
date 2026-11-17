# FARO V5 — S4 W01 D0 B7 RED_FIX (unit test task gating AGP9)
Fecha AR 2026-11-17. Unidad `S4_W01_D0_ISOLATED_DEV_LAUNCH_PACKAGE_ALLOWLIST`.
Estado **B7 RED_FIX SAME UNIT**, no PASS y no cambio funcional extra autorizado.
Código auditado `1f05ed3a28db2f66a86fcb8c394c83dcfe035323`, padres READY `7c1cf1d2d81e862c99918a182b3bf7ebf28ae3af`, diff EXACT3. Guardia `44164343858` `SUCCESS`, preflight correcto; ejecución dirigida `44164344121` `COMPLETED FAILURE`, job `134326271192`.

## B1 — Evidencia de error real
Log del job: `Cannot locate tasks that match ':app:testV2CoreDevUnitTest' as task 'testV2CoreDevUnitTest' not found in project ':app'`; Gradle 9.8.2 falla en selección de tareas antes de iniciar JUnit y compilación. XML de 8 tests no existe, artifact vacío. **No hubo fallo de assertions ni tests ejecutados**. No afirmar B7 PASS.

## B2 — Comparación en el mismo repositorio (reutilizar)
La ruta histórica `.github/workflows/faro-v5-v2-core-directed.yml` ejecuta este MISMO task usando `-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false`; comentario en esa CI: «AGP 9 defaults to only the debug UnitTest component. Explicitly enable non-debug unit tests for this isolated CI invocation, not production». No mover el test: `src/testV2CoreDev` es la ruta correcta, ya aprobada; mantener el control productivo y manifest intactos.

## B3 — Investigación / causa y alternativas
Fuente primaria Android AGP test build variants: https://developer.android.com/build/build-variants (test component depende de variante). Fuente Gradle de selección de task: https://docs.gradle.org/current/userguide/command_line_interface.html .
- OPCIÓN ESCOGIDA: idéntica property CI del workflow V2 ya certificada, `-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false`, sólo en comando `:app:testV2CoreDevUnitTest`, conservando JUnit XML exacto 8 y la compilación W01/V2.
- RECHAZADO: mover test a `src/test` donde no se compila `v2CoreDev` bajo debug, añadir fuentes a Gradle, quitar tests o inferir PASS de una simple compilación. No repetir el análisis de package identity.
- El flag afecta sólo invocación de CI; no cambia código ni variante productiva.

## B4 — Delta único RED_FIX
Permitir sólo modificación del archivo ya auditado `.github/workflows/faro-v5-s4-w01-d0-isolated-launch-allowlist.yml`: añadir `-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false` ANTES de `:app:testV2CoreDevUnitTest`. No tocar actividad/allowlist, JUnit, Gradle, manifest ni otros workflows. Los demás dos archivos del EXACT3 original quedan iguales.
Si falla la CI posterior, investigar log nuevo sin asumir que es el mismo problema.

## B5 — Auditoría adversarial
Prueba obligatoria en nuevo SHA: padre contiene permiso SAME UNIT `READY`, `last_code_review.result=RED_FIX` apuntando a commit `1f05ed3a...` y evidencia de ESTE B7; guard protocol success, workflow directed completed success, XML ocho JUnit sin errores ni skips, ambos Android DEV compilan, W01 un provider privado, V2 Core cero, no INTERNET. Prohibidos cambios de diseño, nuevo owner, finanzas productivas, dos PID declarados PASS, pagos, S3 y W2. La corrección NO cierra W1.

## B6
Intento inicial implementado código EXACT3, CI dirigido falló antes de la selección de test. RED_FIX mínimo pendiente.
## B7
**RED_FIX_SCOPED_TEST_VARIANT_TASK_NOT_ENABLED**, fallo reproducido de CI `44164344121`, guard `44164343858` PASS; sólo misma unidad D0 puede reintentar. No afirmar PASS hasta próximo CI.
