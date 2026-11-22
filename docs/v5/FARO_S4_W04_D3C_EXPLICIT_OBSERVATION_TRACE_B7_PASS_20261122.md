# FARO V5 — W04-D3C · Trazabilidad exacta de observaciones comerciales

Fecha local 2026-11-22. Rama `work/s3-night-20261102`. Unidad `S4_W04_D3C_EXPLICIT_OBSERVATION_TRACE_DEV`.

## B7 — PASS_SCOPED_DEV_JVM_READ_ONLY

Código B6 `b94912508fd6df0829bb047bc09f6ef865370c3d`. Permiso READY en `5f54352a74193dad0ba79553751b398db4779bf7`, con guardia 45027286802 SUCCESS y preflight por cada ruta. El commit B6 añadió exactamente las tres rutas autorizadas.

- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04ExplicitObservationTrace.kt` (blob `8d990e5993ea362c0b3857b7431e3ff78ddcafc2`).
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04ExplicitObservationTraceTest.kt` (blob `22b475097d47c4d34c57200c894bdd9c09a1b857`).
- `.github/workflows/faro-v5-s4-w04-d3c-observation-trace.yml` (blob `24d5ef56ac9b6591c98236d6ba58f43c6f35da8e`).

Guardia del código [45027864245](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45027864245) SUCCESS. CI dirigida [45027864372](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45027864372) SUCCESS, job 137224730022, Java 21 y Android SDK 37. Ambos runs corresponden al SHA de B6.

Se descargó el artefacto 14756159124 (`w04-d3c-observation-trace-xml`) y se comprobó independientemente su XML. Ocho JUnit5 distintos, cero fallos, cero errores y cero omitidos.

- XML `testDebugUnitTest/TEST-ar.com.faro.v5.app.S4W04ExplicitObservationTraceTest.xml`.
- SHA-256 ZIP `3381ff41c806a367e2f154ee66e1d689858f164490b4e64e5b2edca2de57f0e2`.
- SHA-256 XML `93b0e2d4515c84ad007c1bf89c82d5d166a238b4c78c655ee894213202c29113`.

- `t01RejectMismatchedFencesAndRetrogradeCheck()`.
- `t02RejectAbsentDuplicateAndSubstitutedHistoricalObservation()`.
- `t03RejectForgedEstimateAndCoverageLines()`.
- `t04RejectInconsistentPreviewGroupsSubtotalsAndMissingNeeds()`.
- `t05SameTraceAfterReorderingWithoutCurrencyAggregation()`.
- `t06BindLiteralSourceAndUnprojectedObservationIdentityIntoTrace()`.
- `t07PartialUnmodeledDisplayKeepsOnlyHistoricalAmounts()`.
- `t08FreezeOutputAcceptAtMost200AndFailClosedOnOverflow()`.

D3C reconstruye la estimación mediante D2A a partir de la elección explícita y la cobertura D1. Comprueba la correspondencia con el carrito, las filas y subtotales históricos de D3A y el contexto de D3B. La huella de la traza distingue observaciones con la misma proyección visible cuando cambia su procedencia, su instante de adquisición o sus términos. La elección explícita sigue siendo la entrada que identifica el registro histórico. La salida inmutable sirve para mostrar la trazabilidad histórica en DEV y carece de autoridad de compra. D4, E, F y operaciones Apply están fuera de este alcance.

Permiso READY consumido en este checkpoint. Release CAS previsto en época 1556 con el SHA documental como último checkpoint.
