# FARO V5 — S3.A5.7 X08/X12 — B1–B5 ComposeTimeoutException (09/11/2026)

Unidad de continuación EXACTA `S3_A5_7_X08_X12_BACKGROUND_SEARCH`. Base funcional `de7347c0266b837957e6a5e3b23fbe38f2616e62`. Se reusan todos los 47 casos PASS_SCOPED históricos sin repetir.

## B1 — Objetivo y fallo observado

Android API35 [run 42675954072](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42675954072), job `129357255582`, `COMPLETED FAILURE`; compiló/instaló bien, ejecutó 1 test con 1 fallo por timeout 60000ms en `explicitlyOpenChat` invocado **tras recreate línea 159**. `catch (AssertionError)` del helper NO capturó `ComposeTimeoutException`. Objetivo medible: **status nativo observable** si persiste timeout; prueba debe permanecer roja hasta satisfacer condición de montaje real.

## B2 — Reutilización y límites

Reusar `nativeStatusSnapshot()` ya implementado y `FaroV5V2CoreDevActivity.status` preexistente. No duplicar cobertura de Chat/Buscar anterior, no modificar timeout, no tocar Search grant, Reality, Workspace, callback o lifecycle sin causa nativa. Reusar el mismo workflow dirigido; 47 PASS no se ejecutan.

## B3 — Investigación y elección técnica

Documentación primaria Android https://developer.android.com/reference/kotlin/androidx/compose/ui/test/ComposeTimeoutException confirma `class ComposeTimeoutException : Throwable`. Alternativa A **ELEGIDA**: importar `ComposeTimeoutException` y capturar ese subtipo exacto con `catch (failure: ComposeTimeoutException)`, construir `AssertionError(status nativo, failure)`. B **DESCARTADA**: `catch (AssertionError)`, `catch (Exception)` no atrapan el subtipo según jerarquía probada. C **DESCARTADA**: `catch (Throwable)` capturaría también errores fatales y ampliaría diagnóstico innecesariamente. D **DESCARTADA**: doble timeout/retry/skip para hacer test verde. No API pagas ni dependencia nueva.

## B4 — Alcance mínimo y verificación

Única ruta funcional: `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57SearchBackgroundRevokeUiTest.kt`; agregar import, sustituir catch, reparar comentario. Mantener las mismas aserciones, 60000ms, clase test y estado productivo OFF. CI exacta X08/X12 en Android35 con `OK (1 test)` y `INSTRUMENTATION_CODE: -1` para PASS, más guard siete bloques SUCCESS. Si falla, obtener native status y B7 causal antes de tocar otra ruta.

## B5 — Riesgos, guardas y STOP

- No transformar fallo en éxito, no silenciar throwable, no alterar reglas de revocación de grants tras `onPause`.
- `AssertionError` que se lanza contiene estado nativo y `ComposeTimeoutException` original como causa. El cierre de UI puede impedir snapshot: esa falla también significa RED, nunca PASS.
- Writer CAS único; permiso READY con hashes exactos B1–B5 y B7 del código anterior, publicado en **commit documental previo al código**, target HEAD vigente. Sin `main`, S4, Auto/Noche, V2 productiva, G15 ni cambios en catálogo 172.
