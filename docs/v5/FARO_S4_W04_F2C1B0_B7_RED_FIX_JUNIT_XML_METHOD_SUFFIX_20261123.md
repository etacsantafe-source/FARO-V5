# FARO V5 · W04-F2C1B0 · RED_FIX XML JUnit5 method suffix
Fecha: 2026-11-23 Argentina. Unidad `S4_W04_F2C1B0_SELECTED_ACTIVE_TYPED_PROJECTION_DEV`. Código con verificador rojo `9dad3185931124fbce13707bfff72ebb87dd8bb2`. Scope solo workflow de la MISMA unidad.

## B1 — Evidencia del fallo real
- Guardia obligatorio `45119730943` SUCCESS, run dirigido `45119730970`, job `137510509792` FAILURE únicamente en `Independently verify six JUnit XML testcases`; `:app:testDebugUnitTest` Gradle **BUILD SUCCESSFUL in 3m26s**.
- Artefacto descargado y analizado independientemente: `14793307477` (`w04-f2c1b0-selected-typed-projection-xml`), ZIP sha256 `ed53f7e9feff07a44725bed4cb7d9382300034cd77d1c426ea96d9526822e4a9` igual a digest GitHub; archivo XML sha256 `8562315c7fe6d0294c9280f377ead46b4ba834936afddcd4231caebca0bdb488`.
- Conteo comprobado del XML original: **6 tests, 0 failures, 0 errors, 0 skipped**. Los seis nombres únicos son exactamente T01–T06 pero con `()` final; por ejemplo `t01SameGenerationSelectedOpenNeedAndProductBindExactF1Contracts()`. El oráculo actual compara `names` con constantes sin `()`, produce `AssertionError`. No falla ningún caso funcional.

## B2 — Reutilización y no repetir
- El contrato de test / mapeo F2C1B0 es válido en este SHA según XML, y la guardia es verde. No cambiar fuente ni tests, ni repetir F2C0/F2C1A, D0–D4 o V4.4.
- Conservar verificación de nombres únicos, conteo, status XML y artefacto; no relajar pruebas.

## B3 — Alternativas y fuentes
- Gradle/JUnit XML report: https://docs.gradle.org/current/userguide/java_testing.html#test_reporting (nombre de método de JVM en XML no siempre coincide con firma sin paréntesis).
- Alternativa A borrar comparación de nombres: DESCARTAR, perdería evidencia de los seis contratos.
- Alternativa B hardcodear `()` en todas las constantes: válido para ejecución actual, menos robusto.
- Alternativa C normalizar **solo el sufijo `()`** al leer `name`, sin eliminar otros caracteres, verificar que los seis normalizados sean únicos y correspondan exactamente a T01–T06: ADOPTAR. Conserva exhaustividad; no confundir casos distintos.

## B4 — Delta exacto
Archivo único `.github/workflows/faro-v5-s4-w04-f2c1b0-selected-projection.yml`: cambiar `names={c.attrib["name"] for c in cases}` a `names={c.attrib["name"].removesuffix("()") for c in cases}`. No tocar permiso, scope, test code, suite ni su normalizador de failures/errors/skips. Mantener preflight de 1..3 rutas autorizado y 6 casos exactos.

## B5 — Aceptación adversarial
El XML original debe seguir teniendo 6 casos únicos, 0 failure/error/skipped y nombres equivalentes a la lista exacta tras quitar solo `()`. Un nombre ajeno o repetido tras normalización sigue rojo. Nueva CI/guardia SUCCESS del SHA corregido + artifact/XML descargado. No declarar PASS con el artefacto rojo anterior (aunque sus tests pasaran). Sin pagares, Apply, stock, egress, main, V4.4, G15, Auto/Noche ni V2 productiva.

## B7 — RED_FIX, NO PASS
`RED_FIX_JUNIT_XML_METHOD_SUFFIX_FALSE_ORACLE` para código `9dad3185931124fbce13707bfff72ebb87dd8bb2`. El directorio de tests del commit está PASS 6/6 observado, pero el workflow es FAILURE por comparar nombres con `()`; solo B7 tras nueva CI totalmente verde. Writer se libera después de este checkpoint.
