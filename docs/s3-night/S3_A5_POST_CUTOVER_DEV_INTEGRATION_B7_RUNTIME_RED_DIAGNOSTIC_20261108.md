# FARO V5 — S3.A5 post-cutover B7: runtime RED, diagnóstico JUnit obligatorio

Fecha 2026-11-08 AR. Unidad `S3_A5_POST_CUTOVER_DEV_INTEGRATION`. Código bajo prueba `3eda9c270478b6e2bae435d164664873d36665d5`, segundo intento tras corregir test-only suspend.

## B7 — Evidencia runtime real (no asumir PASS)
- GitHub Actions dirigido [#42536901243](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42536901243), job `128903097337`: **COMPLETED FAILURE**. `:app:compileV2CoreDevUnitTestKotlin` PASS, `:app:testV2CoreDevUnitTest` FAIL. Log: `4 tests completed, 2 failed`.
- Casos rojos: `oldSevenSearchHitAndContentCannotAppearInFreshEight()`: `AssertionFailedError` (método línea132); `fourRealOwnerSearchAndDurableChatWorkTogetherOnlyAfterFreshEight()`: `IllegalStateException` (método línea62). Los logs solo indican líneas de comienzo del método suspend, no la causa/stack XML detallados. No corregir datos ni código de producción con hipótesis.
- Quinto método `pointerAbaOfActiveEightRevokesChatSearchAndOldHit` tiene firma `= runBlocking { try { assertThrows(...) } finally { close() } }` con expresión final `assertThrows` no-Unit potencial, por lo que JUnit podría no descubrirlo (se observaron solo 4/5 tests). Hipótesis verificable: forzar retorno `Unit` y exigir XML exactamente 5. No asumir que está aprobado.
- Guard de protocolo `#42536901256` SUCCESS sobre mismo SHA; no sustituye XML. El CI V2Core anterior se ejecuta en paralelo; no repetirlo.

## Investigación/RED_FIX delimitado
1. **Único parche de observabilidad al workflow dirigido**: al fallo de Gradle, imprimir en log un resumen de cada testcase XML y el texto/stack de `<failure>` y `<error>` desde `v5/app/build/test-results/testV2CoreDevUnitTest`. Conservar exit code rojo sin disfrazar tests ni cambiar sus aserciones.
2. **Único parche de descubrimiento test**: declaración explícita `: Unit` para la función ABA que podía inferir un valor de retorno no-void. Verificar que se ejecutan 5 casos, no 4. Ningún cambio en runtime, schemas, permisos, UI o los otros cuatro oráculos.
3. Si siguen rojos, usar la NUEVA evidencia XML para categorizar defecto test vs producto; volver a B1/B5 solo ante hipótesis distinta y verificable; nunca disminuir el conteo esperado ni declarar PASS para cases omitidos.
4. S3.A5 OPEN; pruebas físicas del G15 al final; V2 shipping OFF, S4/Auto/Noche OFF, catálogo oficial 172 intacto.
