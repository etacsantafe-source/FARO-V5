# FARO V5 — S3.A5.7 X08/X12 — B1–B5 diagnóstico UI sin contenido sensible

Unidad `S3_A5_7_X08_X12_BACKGROUND_SEARCH`. Baseline funcional `2b61c14b7295b1ce8498bb02e4009d9998d7625c`. Sólo un nuevo delta de test si está aprobado por guard.

## B1 — Falla reproducible e hipótesis

Run [42682116828](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42682116828), job `129379132266`: 1/1 FAILED en **primera** apertura Chat, 60s ComposeTimeoutException, nativo TextView vacío. Run previo [42679986787](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42679986787) falló en segunda apertura luego de recreate. No afirmar una sola causa. Objetivo: identificar al momento de timeout si hay Compose root, nodos esperados y estructura Android, preservando resultado rojo.

## B2 — Reuso del código real

`FaroV5SurfaceHost.install` usa `activity.setContent` y elimina el árbol TextView inicial. Reusar `nativeStatusSnapshot()` y helper `explicitlyOpenChat()` existentes, sin abrir nuevos tests ni repetir 47 PASS. Usar matchers existentes `onAllNodesWithText` y `hasSetTextAction` para inspección acotada.

## B3 — Diseño mínimo y alternativas

A USAR: capturar sólo **conteos** de `Buscar en esta conversación`, `Enviar`, `Buscar en FARO`, editores Compose; capturar **nombres de clase** de View raíz/hijos y flags de visibilidad, sin textos ni contenido sensible. `runCatching` solo en captura diagnóstica; el timeout original debe conservarse como causa de `AssertionError`. B RECHAZADA: `onRoot().printToString()` crudo, expone texto de chats en logs públicos. C RECHAZADA: esperar >60s/reintentar automáticamente o simular grant. D DIFERIR: cambiar host SQLite/Search sin diagnóstico. API de pruebas Compose: https://developer.android.com/develop/ui/compose/testing/common-patterns.

## B4 — Rutas, verificación y CI

Única ruta `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57SearchBackgroundRevokeUiTest.kt`; mantener `FARO-UNIT:S3_A5_7_X08_X12_BACKGROUND_SEARCH` en commit funcional. Captura nativa en UI thread limitada a tipos de View y `isShown`, Compose sólo conteos de nodos de texto fijo sin datos. Sin cambiar aserciones; CI dirigida exacta `OK (1 test)` y `INSTRUMENTATION_CODE: -1` para B7 PASS; si rojo, analizar indicadores para elegir root-cause.

## B5 — Control adversarial

No imprimir contenido de messages/queries/snippets, ni planos, credenciales o paths de datos. No ocultar ComposeTimeoutException dentro de `runCatching`: la captura parcial no cambia el resultado del test. No permiso nuevo, ni modificación del catálogo 172, main, V2 productiva, S4, Auto/Noche, G15. Writer único CAS; B1–B5 y B7 RED_FIX exacto en permit padre antes de siguiente modificación.
