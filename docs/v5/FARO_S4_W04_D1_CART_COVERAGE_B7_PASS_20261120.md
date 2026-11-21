# FARO V5 — W04-D1 · Cobertura tipada observacional del carrito — B7 PASS

2026-11-20. Unidad `S4_W04_D1_CART_OBSERVATION_COVERAGE_DEV`, B1–B5 `docs/v5/FARO_S4_W04_D1_CART_COVERAGE_B1_B5_20261120.md`, blob `c2c175202b1ec7617c15a42147c1b827013ab2ee`. READY exact3 `8850b4da593877cec9f64fac2db11c999c7a43ce`, guard permiso `44855027295` SUCCESS.

## B7

**PASS_SCOPED_STAGED_DEV_AFTER_TWO_TEST_ONLY_RED_FIX.** Lógica productiva `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialCartCoverage.kt` blob `72d408ab9dfe81acf99a81711b6c733bb68d658e` invariable desde B6 original `f3dbc768b9194608b51baf373260d2ecfa136d37`. Revalida D0 cart contra Household y fence, chequea grants Household+Commercial independientes, lee snapshot H1 STAGED con watermark C2, revisa igualdad del snapshot al final, y devuelve por línea estados `NO_DATA`, `HISTORICAL_PARTIAL` o `COMPARABLE_OBSERVED_ONLY` junto al aviso inequívoco `OBSERVED_ONLY_NOT_A_CURRENT_QUOTE_OR_BUDGET`. No sumas, FX, promociones inferidas, segundo store, SQL, pagos, stock o mutación Finance.

**Trazabilidad completa sin ocultar CI rojas:**
1. B6 inicial `f3dbc768b9194608b51baf373260d2ecfa136d37`, guard `44855192484` SUCCESS, CI `44855192529` FAILURE: 11 tests descubiertos, uno fallido por fixture ACL tardío; doc B7 RED_FIX `docs/v5/FARO_S4_W04_D1_B7_RED_FIX_20261120.md`, checkpoint `73758081e7d494922565a4e654f09fc01149bee9`, guard `44855693476` SUCCESS.
2. RED_FIX test+workflow `3db22a9362ab0eb7b2b6f516411ad0a19adc7770`, guard `44855793763` SUCCESS, CI `44855793778` FAILURE: test 12º descubierto y ejecutado, queda sólo assertion `commercialChecks >4` obsoleta; segundo doc B7 RED_FIX `docs/v5/FARO_S4_W04_D1_B7_RED_FIX2_20261120.md`, checkpoint `0e170dc389909e72a3e1953ccc0b1ff344518272`, guard `44856242308` SUCCESS.
3. RED_FIX final de **sólo test** `7c7aaa18acb9c3b9d084fbd0c02f0e1f6ee13d18`: aserción chequeo tardío `assertEquals(4,commercialChecks)`; product code y workflow funcional se mantienen intactos. Guardia `44856347218` COMPLETED SUCCESS, incluso preflight de subconjunto autorizado 1/3 rutas.
4. **CI final verificada** `44856347260` COMPLETED SUCCESS, job `136699352464`, SHA exacto `7c7aaa18acb9c3b9d084fbd0c02f0e1f6ee13d18`, `:app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04CommercialCartCoverageTest` **BUILD SUCCESSFUL in 4m 24s**; JUnit XML real dirigido `(12 tests,0 failures,0 errors,0 skipped)`, salida `W04_D1_CART_STAGED_COVERAGE_JVM_XML_PASS 12/12`. Artefacto `14687702076`, `w04-d1-cart-staged-coverage-xml`, 951 bytes, no expirado.

**Cierre y límites:** 12 casos de grants/revocación, ABA/revisión, sin precios, observedAt null, ARS/USD/localidad/presentación, caducidad/promos, dos comercios comparables observados, watermark/sourceRef corruptos, carrera al leer, mezcla de needs y fuente hostil literal. El servicio D1 opera sólo con snapshot in-memory **STAGED Evidence2**; no host ACTIVE de producción ni UI Android o G15, no garantía de precio actual, no compra/pago ni finanzas. D0 B7 permanece PASS. Los workflows históricos M5 de APK con problemas de Maven no constituyen la suite dirigida D1 y no se corrigen en esta unidad.

**Decisión B7: PASS_SCOPED_DEV_READ_ONLY.** Consumir READY→BLOCKED, writer RELEASED, guard final SHA del documento; W04-D sigue abierto (D2A/D2B/D3/D4 pendientes). El siguiente permiso debe ser nuevo e independiente.
