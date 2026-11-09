# FARO V5 — S3.A5.7 X08/X12 — Error de captura en primer Chat; B1–B5

Fecha: 2026-11-09 AR. **Misma unidad** `S3_A5_7_X08_X12_BACKGROUND_SEARCH`, `RED_FIX` del código `0d5bba653ceb3fed1d2eb6195b2a41d32721e63d`. A3 permanece sin código; 47 PASS históricos no se repiten.

## B1 — Evidencia exacta

Run Android [42653795115](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42653795115), job `129286788765`: 1/1 FAILED tras 60 segundos en **primera** invocación `explicitlyOpenChat()` (test línea 105), no tras recreate. El helper utilizó `catch(Exception)` y no emitió estado nativo; el mensaje original de timeout llegó al runner sin envolver. B7 anterior: `docs/s3-night/S3_A5_7_X08_X12_REOPEN_DIAGNOSTIC_B7_RED_FIX_20261109.md`. Objetivo: detectar fallo real sin falso PASS.

## B2 — Reuso de alcance probado

Reutilizar `nativeStatusSnapshot()` introducida en el mismo test (lee todos los TextViews del Activity DEV); conservar la ruta `explicitlyOpenChat()`, la apertura nativa y `rule.waitUntil(60_000L)`. `FaroV5V2CoreDevActivity` ya muestra `No se abrió: <tipo>` / `Verificando journal...`; no tocar DEV app, generación ni SQLite sin ese dato. Los 47 casos antiguos siguen congelados por SHA.

## B3 — Alternativas e investigación acotada

A: capturar `AssertionError` que también alcanza `ComposeTimeoutException` y adjuntar estado nativo, conservando causa; **ELEGIDA**. B: capturar `Exception` actual; **DESCARTADA** porque la evidencia muestra escape del timeout. C: elevar timeout/reintentar/reducir condición; **DESCARTADA** por ocultar regresiones. D: cambiar permisos Search, host, app o Activity sin causa confirmada; **DIFERIDA**. API de pruebas Compose: `AndroidComposeTestRule.waitUntil`; estructura base ya comprobada con tests Compose anteriores, sin simular éxito.

## B4 — Contrato y alcance autorizado

Modificar exclusivamente `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57SearchBackgroundRevokeUiTest.kt`: sustituir la captura de `Exception` por `AssertionError` en el helper. Sin cambios de assertions, tiempo, API productiva, catálogos, main, V2 productiva, S4, Auto/Noche o datos G15. La CI Android dedicada debe generar `OK (1 test)` + `INSTRUMENTATION_CODE: -1` + marcador `1/1`; si vuelve a fallar leer el estado nativo para tomar siguiente decisión.

## B5 — Auditoría adversarial y STOP

La captura no puede devolver éxito ni silenciar el timeout: convertirlo en `AssertionError(message, original)`; el fallo original queda como causa. Evitar capturar `Throwable` genérico y consumir errores fatales; solo fallo de aserción de Compose. Si `nativeStatusSnapshot()` falla, conservar fallo de CI, no declarar PASS. No alterar grant revocado, ni permitir búsquedas sin autorización después de onPause. No reejecutar 47 PASS antiguos. Antes de código: permiso READY documental por esta unidad, B7 del código `0d5bba65`, writer CAS epoch propio, preflight guard; después de código CI y B7.
