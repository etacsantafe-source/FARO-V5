# FARO V5 — W04-D3A · Pre-revisión comercial de solo lectura

Fecha 2026-11-21. Unidad S4_W04_D3A_PRE_REVIEW_ENVELOPE_DEV. Rama work/s3-night-20261102.

## B7 — PASS_SCOPED_DEV_JVM_READ_ONLY

Código B6 46b96cc326d529744aadbd60e13efb71d53684ea. Permiso READY de d1367148c385fbfd529b8a259d0ce6ad3b568ce1 con guardia 44921265948 SUCCESS. Preflight local y en Actions para las tres rutas nuevas.

- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialPreReviewEnvelope.kt` (blob `45d8bfdb64bac7ab87c5011947db88185f56bc8b`).
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04CommercialPreReviewEnvelopeTest.kt` (blob `42efd62044b632421f05cdb978bd9de10e6e388d`).
- `.github/workflows/faro-v5-s4-w04-d3a-pre-review-envelope.yml` (blob `a40086c210ac5a478e612bf74ab6d1cb66d0d098`).

Guardia del código 44923205285 SUCCESS. CI dirigida 44923205445 SUCCESS, job 136903506268, Java 21 y Android SDK 37. Artefacto 14715075769 (w04-d3a-pre-review-envelope-xml).

XML del ZIP inspeccionado de forma independiente. Ocho casos distintos, 0 fallos, 0 errores y 0 omitidos.

- XML `testDebugUnitTest/TEST-ar.com.faro.v5.app.S4W04CommercialPreReviewEnvelopeTest.xml`.
- SHA-256 del ZIP `cdfaafd808380cf993d9854fddf6e57a35215e9a9bca4592ea6567ee226d2716`.
- SHA-256 del XML `ce9709100abdd28f96185ad66e61e87fd46a49c5ae436f192edf90671a546e42`.

- `t01MismatchedBlankAndInvalidFencesReject`.
- `t02ForgedAmountsKeysAndMembershipReject`.
- `t03DisclosuresMustMatchAllCommercialGroupsAndCoverage`.
- `t04WarningsStatusAndNoteBoundsPreserveUnverifiedProvenance`.
- `t05RowsAndNoticesHaveCanonicalOrderWithoutCombiningCurrencies`.
- `t06AmountsSourceAndLiteralBoundariesAffectTheDigest`.
- `t07PartialCoverageAndLineCapRemainReadOnly`.
- `t08SealDetachesCollectionsAndKeepsHostileSourceAsText`.

El sobre conserva importes históricos, hipótesis promocionales y notas declaradas sin verificar. Agrupa por moneda, comercio, localidad y medio de pago. Usa SHA-256 con codificación canónica y copia inmutable de las filas, las listas de necesidades y las notas. Requiere revalidación viva para cualquier paso posterior.

Alcance DEV de lectura. V2 productiva OFF. Android físico G15 NOT_RUN. D3B, D4, E y F fuera del alcance. Permiso consumido y writer a liberar en época 1542.
