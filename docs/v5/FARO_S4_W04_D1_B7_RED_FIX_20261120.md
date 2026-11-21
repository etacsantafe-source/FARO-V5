# FARO V5 — W04-D1 · B7 RED_FIX del primer B6 (fixture revocación y 12.º caso no descubierto)

Fecha local 2026-11-20. Unidad `S4_W04_D1_CART_OBSERVATION_COVERAGE_DEV`, B6 código `f3dbc768b9194608b51baf373260d2ecfa136d37`, READY exact3 `8850b4da593877cec9f64fac2db11c999c7a43ce`, preflight READY guard `44855027295` SUCCESS. B1–B5 ya certificados en `docs/v5/FARO_S4_W04_D1_CART_COVERAGE_B1_B5_20261120.md`, blob `c2c175202b1ec7617c15a42147c1b827013ab2ee`.

## B7

**RED_FIX, NO PASS.** Guardia del código `44855192484` SUCCESS, CI dirigida `44855192529` COMPLETED FAILURE, `:app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04CommercialCartCoverageTest` compiló, produjo XML `14687347304`, pero Gradle informó **11 tests completed, 1 failed**; falla `lateRevocationOfCommercialGrantSuppressesAllResults` en `S4W04CommercialCartCoverageTest.kt:263`. No se ejecutó la comprobación final del XML 12/12. No atribuir resultado PASS al B6 inicial.

**Causa 1:** en el fixture de revocación, `rejectAfterCommercialCheck=4` autoriza todas las cuatro revalidaciones de Commercial del servicio D1; el test espera rechazo, pero la revocación no ocurre durante la operación. Cambiar umbral test a **3** para que la cuarta verificación (justo antes de publicar) devuelva NOT_AUTHORIZED; no modificar la lógica productiva de seguridad.

**Causa 2:** el test fuente tiene **12 anotaciones @Test**, pero Gradle descubrió sólo 11. El caso `hostileSourceLiteralReadOnlyAndInvalidWindowFailClosed` usa cuerpo por expresión `= Fixture.use { ... }` terminado en `assertThrows`, que devuelve una excepción (tipo no Unit). JUnit5 requiere método de test de retorno `void`; agregar `Unit` explícito como última expresión del bloque para obligar retorno `Unit` y que el 12.º caso se ejecute. No borrar ni bajar la expectativa de 12 XML.

## B5 — Reparación exclusiva misma unidad (sin reabrir D0/C)
- R01 Cambiar sólo `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04CommercialCartCoverageTest.kt`: umbral 3, retorno Unit al final del test fuente hostil. Mantener las 12 anotaciones y el resto de tests.
- R02 Cambiar sólo `.github/workflows/faro-v5-s4-w04-d1-cart-coverage.yml`: para RED_FIX el diff tiene exactamente dos de tres rutas autorizadas; exigir 1..3 con preflight `--head HEAD^ --unit S4_W04_D1_CART_OBSERVATION_COVERAGE_DEV --path "$ROUTE"` sobre CADA ruta real, no abrir glob/otras rutas. El primer B6 sí pasó preflight EXACT3.
- R03 **No modificar** `S4W04CommercialCartCoverage.kt`, D0, H1/C2/C3, SQL, Finance ni ACTIVE. Si nuevo error funcional real, B7 RED_FIX adicional con evidencia antes de code.
- R04 Mantener en permiso `unit_id=S4_W04_D1_CART_OBSERVATION_COVERAGE_DEV`, `status=READY`, `B6=READY_TO_IMPLEMENT`, `B7=PENDING_FINAL_AUDIT`, `last_code_review={sha:f3dbc768b9194608b51baf373260d2ecfa136d37,result:RED_FIX,evidence:este doc ## B7 + blob SHA}`.
- R05 Guard documental del commit RED_FIX en SUCCESS antes del código; luego CI real `:app:testDebugUnitTest --tests ...CoverageTest`, XML 12/12 sin skip/failure/error, guard SHA y B7 PASS. No iniciar D2A hasta B7 PASS de D1. Writer RELEASED tras cada checkpoint.

**Nota externa:** el workflow histórico de empaquetado M5 `44855192507` falló por resolución de dependencias Maven/Gradle en tarea de APK aislada, sin relación probada con D1; no editarlo dentro del scope de reparación D1. V2 productiva, Android físico/G15 y egress NOT_RUN.
