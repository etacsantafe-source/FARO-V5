# FARO V5 — S3.A5 Reality8 V2CoreDev host — B7 red/fix por task Gradle no habilitada

Fecha 2026-11-08 AR. Unidad `S3_A5_REALITY8_DEV_HOST_MOUNT`. Commit código auditado `60250bcede299c9ddb3028bb0b948ac1b700bcdd`.

## B7 — Evidencia de falla exacta
- Workflow nuevo `#42534451918`, job `128895549206`, **COMPLETED FAILURE** para SHA `60250bce`. `Android SDK configuration` PASS; el paso `Compile real isolated V2CoreDev variant and eight fresh JVM cases` FAIL en resolución Gradle antes de compilación.
- Error en log de las 23:19:55Z: `Cannot locate tasks that match ':app:testV2CoreDevUnitTest' as task 'testV2CoreDevUnitTest' not found in project ':app'.` No hay Kotlin compile, JUnit XML, ni ejecución de pruebas de host. No afirmar 8/8 ni regresión PASS.
- El workflow **ya existente** `.github/workflows/faro-v5-v2-core-directed.yml` documenta expresamente: “AGP 9 defaults to only the debug UnitTest component”, y habilita en CI `-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false` para ejecutar `:app:testV2CoreDevUnitTest`. Esta es evidencia directa en el mismo repo, no una hipótesis inventada.
- El guard obligatorio `#42534452161` **SUCCESS** para el mismo SHA; verifica protocolo, NO prueba funcional. Los otros pipelines existentes no sustituyen XML dirigido.

## RED_FIX acotado, sin repetir B1–B5
- Modificar **solo** `.github/workflows/faro-v5-s3-a5-dev-host-reality8-directed.yml`, agregando exactamente la propiedad AGP9 `-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false` al `./gradlew` que ejecuta compile+test. No modificar código Kotlin, Android Manifest, CI históricos ni ninguna clase certificada.
- Reejecutar CI SOLO por el nuevo push, verificar compilación y `TEST-...FaroV5V2Planning8DevHostTest.xml` con 8/8 (0 fails/errors/skipped). Si ahora hay error de Kotlin, nueva causa, tratar como RED_FIX bajo evidencia, jamás inferir PASS.
- V2 productiva OFF; no G15, no aplicación activa en dispositivo, no S4/Auto/Noche. S3.A5 abierto. B7 final PENDING hasta CI terminal.
