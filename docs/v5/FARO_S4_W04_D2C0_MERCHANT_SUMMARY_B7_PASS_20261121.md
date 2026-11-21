# FARO V5 — W04-D2C0 · Resumen comercial por comercio

## B7 — PASS acotado a JVM DEV

Fecha local 2026-11-21. Unidad S4_W04_D2C0_MERCHANT_WHATIF_SUMMARY_DEV. Rama work/s3-night-20261102.

El commit funcional c0a575f2312714b4abcc62c83eb30bb4c6311951 agregó las tres rutas del permiso READY:

- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04MerchantWhatIfSummary.kt`, blob `435ff82101e17bd5ec4065b54e2476fb5f6efc11`.
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04MerchantWhatIfSummaryTest.kt`, blob `6824b1577b4a361367a044f290ba94769e6f3a1e`.
- `.github/workflows/faro-v5-s4-w04-d2c0-merchant-summary.yml`, blob `41bf512962352645c36d975e539b0c1152da4558`.

La guardia READY 44916612951 y la guardia de código 44917138983 finalizaron con SUCCESS. La CI dirigida 44917139054, job 136885061773, verificó las tres rutas contra el padre READY y ejecutó `:app:testDebugUnitTest` sobre el SHA funcional con Java 21 y Android SDK 37. Gradle terminó con BUILD SUCCESSFUL.

Se descargó e inspeccionó el artefacto 14712660055. El ZIP contiene `testDebugUnitTest/TEST-ar.com.faro.v5.app.S4W04MerchantWhatIfSummaryTest.xml`. El XML registra ocho testcases distintos, cero fallos, cero errores y cero omisiones.

- SHA-256 ZIP `10f05b81f94bb5836d6506b7271668de2cb3bc4ca677cdb3afbc9d0a9bab11e1`.
- SHA-256 XML `47384ea86d614491853553d645e8fa0bb992df6ad009e96559b325103755c9c8`.

La proyección verifica los importes y la cobertura por moneda, comercio, localidad y medio de pago. Presenta los subtotales históricos y los importes hipotéticos sobre el subconjunto modelado. El marcador de reintegro es `UNVERIFIED_NOT_APPLIED` y carece de importe. El alcance de la certificación es la proyección JVM de solo lectura. G15 queda en NOT_RUN.

El cierre consume el permiso READY de D2C0. El writer se libera por CAS para la guardia del checkpoint documental, con epoch esperado 1526.
