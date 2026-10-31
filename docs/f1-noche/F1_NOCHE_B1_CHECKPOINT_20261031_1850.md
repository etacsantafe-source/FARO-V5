# FARO V5 F1 — Noche B1 (18:50–19:20 Argentina)

- Inicio programado 18:50; ejecución observada desde 18:52:06; writer adquirido 18:52:23.825, epoch 2. Checkpoint/release 2026-10-31T22:15:22.070Z.
- Rama exclusiva `work/f1-noche-v14-20261031-1820`, último código `a95f8d1f0a048e3b945bb78aca7a5bfd528eddde`; **F1 OPEN**, S2.A6 3/8, V2 OFF. Sin cambios a main, histórico, F2/F3/F4/S3/S4.
- Tablero: Groq **PROBADO (sintético A1)**; Chat **IMPLEMENTADO**; Review/Atomic Apply **IMPLEMENTADO, CI NO PASS**; Delivery Android **IMPLEMENTADO**; integración final **EN DESARROLLO**.

## Código implementado
- Nuevo `FaroV5ModelReviewBridge.kt`: modelo JSON estrictamente limitado a `profile.update_declared`; requiere turno USER aceptado y respuesta FARO completada; rechaza campos inesperados y huellas falsificadas; el modelo NO controla contrato, stamps, idempotencia, fingerprint ni Apply.
- Preparación confiable desde Reality actual -> `PreparedPlanEnvelopeCodec` -> Workspace PENDING review; aprobación humana y Atomic Apply siguen separados; reintentos no duplican reviews.
- Callback posterior a respuesta persistida en dispatcher y host V2 **en cuarentena**, sin habilitar V2; compatibilidad del constructor Java recuperada con `@JvmOverloads`.
- Tests dirigidos para respuesta estructurada, propuesta persistida/reabierta, rechazo de metadatos falsificados, no Apply automático y no duplicación.
- Commits funcionales: `51d2fd9a`, `431c785d`, `180386a6`, `9dc7b01d`, `a95f8d1f`.

## CI / ROJOS
- [#41245668665](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41245668665) SHA `51d2fd9a` **FAILURE**: Kotlin test compile mismatch RealityStore vs ProfileReadApi; corrected in 431c785d. Wall 232s, cola proxy 4s, log runner 226s. XML NOT_RUN.
- [#41245867313](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41245867313) SHA `180386a6` **FAILURE**: Java CompositionRoot 3-arg constructor incompatible with new Kotlin callback; corrected by @JvmOverloads in a95f8d1f. Wall 341s, cola proxy 73s, log runner 267s. XML NOT_RUN.
- [#41246139989](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41246139989) SHA `9dc7b01d` **FAILURE**: Same old-SHA Java constructor mismatch; queued before correction, no identical rerun requested. Wall 386s, cola proxy 107s, log runner 278s. XML NOT_RUN.
- [#41246312730](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41246312730) SHA `a95f8d1f` **FAILURE**: Compilation PASS; 9 app tests executed, 1 failed at FaroV5ChatToReviewedApplyFlowT2Test.kt:215: stale expected plain text after reopen instead of new structured JSON; no final XML oracle. Wall 486s, cola proxy 267s, log runner 217s. XML APP_TESTS_9_EXECUTED_1_FAILED; FORMAL_XML_ORACLE_SKIPPED.
- El último SHA compiló; `9` tests de app ejecutados, `1` falló por aserción obsoleta en línea 215. **No hay PASS integral ni XML final**. Corrección de una línea queda para A2, por corte 19:10.
- Incidente writer: un commit de código partió de árbol antiguo y dejó estado `NONE` temporal; restaurado por CAS exacto en `0cd225ac`, revalidado `ACTIVE` epoch2, sin escritor competidor observado. Regla para A2: siempre crear árboles sobre el tree SHA del HEAD vigente, verificar writer tras cada push.

## Tiempo
- Wall observado hasta checkpoint: 1396s; writer retenido hasta checkpoint: 1378s. Distribución exclusiva investigación/código/pruebas/espera CI/coordinación/inactividad **NO_REGISTRADO**.
- Los tiempos de CI corrieron en paralelo y NO se suman como tiempo humano. Cola exacta NO_REGISTRADO; proxies basados en primer timestamp del log runner.
- Writer liberación CAS pendiente de lectura post-CAS; no afirmar verificado antes de comprobar.

## Próximo paso
A2 19:20: cambiar exclusivamente la aserción de texto tras reapertura en `FaroV5ChatToReviewedApplyFlowT2Test.kt:215` por el JSON estructurado persistido, correr CI exacto y XML; luego seguir integración F1 y ruta local Keystore/APK. Groq real y Android físico **WAIT_HUMAN**; APK aún no lista.
