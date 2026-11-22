# FARO V5 — W04-D4 · Presentación de revisión comercial histórica

Fecha local 2026-11-22. Rama `work/s3-night-20261102`. Unidad `S4_W04_D4_HISTORICAL_REVIEW_PRESENTATION_DEV`.

## B7 — PASS_SCOPED_DEV_JVM_READ_ONLY

B6 `5fe51ae3286e92bf8b7575c93e9e77dd0d6afe7f`, sobre el checkpoint READY `b9fdcc51925ece6cad00399798d3c10a03ee53c6`. Guardia documental [45032723495](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45032723495) SUCCESS y preflight de las tres rutas. El diff de B6 se limita a estas tres adiciones.

- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialReviewPresentation.kt` (blob `b51335ad892a7389ce0d1ccaf7ac7a1800fe0713`).
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04CommercialReviewPresentationTest.kt` (blob `660ce3b95b19f97d47c0b8e5b8f75e45d6af8747`).
- `.github/workflows/faro-v5-s4-w04-d4-review-presentation.yml` (blob `a31b56ff7a94b23fea691a6bc3e8fd27a41afe4b`).

Guardia de código [45034045852](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45034045852) SUCCESS. CI dirigida [45034045914](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45034045914) SUCCESS, job 137243731925, Java 21 y Android SDK 37. Las dos corridas corresponden al SHA exacto de B6.

Se descargó el artefacto `14759051713` (`w04-d4-review-presentation-xml`) y se inspeccionó el XML por separado. Ocho JUnit5 distintos, cero fallos, cero errores y cero omisiones.

- XML `testDebugUnitTest/TEST-ar.com.faro.v5.app.S4W04CommercialReviewPresentationTest.xml`.
- SHA-256 ZIP `def146e5537d0ed48a8313e55973776861f080196a5ebb20a341e921d2ebf58a`.
- SHA-256 XML `31dd6f51a2a124f5346959b99c6a12b4afc57164b7780a2b6036b6248937b626`.

- `t01CanonicalReadOnlyPresentationPreservesTraceAndRecheck()`.
- `t02RejectSubstitutedObservationAndTamperedFences()`.
- `t03RejectSubstitutedGroupsDuplicateNeedsAndForgedSubtotals()`.
- `t04KeepDifferentCurrenciesAndMerchantsSeparateInCanonicalOrder()`.
- `t05SeparateWhatIfDiscountFromFutureCashbackAndBankConditions()`.
- `t06KeepPartialCoverageAndAbsentPromotionsExplicit()`.
- `t07KeepMarkupAndUrlsLiteralAndRejectMalformedOrControlText()`.
- `t08FreezeTwoHundredLinesAcrossReorderAndRejectOverflow()`.

D4 invoca D3C con las seis entradas originales y recompone el sello D3A para validar escenarios y notas. Comprueba subtotales de líneas observadas y modeladas con suma exacta. La salida inmutable conserva moneda, comercio, localidad, medio de pago, procedencia y tiempos. Separa importes históricos, escenario hipotético opcional, beneficios declarados sin verificar, faltantes y necesidades sin modelar. Los rechazos tipados descartan el resultado completo. La certificación cubre el modelo JVM DEV de solo lectura. W04-E, W04-F, Android UI, G15, Apply, pagos, Finance, stock y V2 productiva quedan fuera de alcance.

Este commit consume READY. Su SHA identifica el checkpoint documental en `writer.json:last_checkpoint` al liberar el writer por CAS en la época 1561. La guardia documental final se verifica sobre ese mismo SHA.
