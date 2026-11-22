# FARO V5 — W04-D3B · Revalidación de lectura comercial

Fecha 2026-11-22. Unidad S4_W04_D3B_LIVE_READ_RECHECK_DEV. Rama work/s3-night-20261102.

## B7 — PASS_SCOPED_DEV_JVM_READ_ONLY

Código B6 `c5fe25a5787100e1d1f434df62ae611da58e21ab`. El permiso READY de `be4f16a999f3748fd63540339f30c4d4c6955168` tiene guardia 45024048969 SUCCESS. Preflight local y en CI para las tres rutas.

- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialLiveReadRecheck.kt` (blob `fedc4cfe1326ef2fa1c3f1abb8ee479a7b250ec7`).
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04CommercialLiveReadRecheckTest.kt` (blob `19e8e03ddf22270e55846c45e5f50b5abf254665`).
- `.github/workflows/faro-v5-s4-w04-d3b-live-read-recheck.yml` (blob `4f44f22c713765acf5b34843a28cae39da12ae3c`).

Guardia del código 45024661867 SUCCESS. CI dirigida 45024661742 SUCCESS, job 137214946620, Java 21 y Android SDK 37. Artefacto 14755477381 (`w04-d3b-live-read-recheck-xml`).

Se descargó el ZIP y se verificó el XML de forma independiente. Ocho casos distintos, cero fallos, cero errores y cero omitidos.

- XML `testDebugUnitTest/TEST-ar.com.faro.v5.app.S4W04CommercialLiveReadRecheckTest.xml`.
- SHA-256 del ZIP `7d9ca2eee35a7dc913c9adc0b1b12dffc19eeb6b12ba2231bdc14d67170b5e21`.
- SHA-256 del XML `e20a7ba911af50655fef47b9df5c6701021f7b96f011173c83d862cde3ea5766`.

- `t01RejectMismatchedIdentityAndMalformedBaseline()`.
- `t02RevalidateHouseholdRevisionFenceAndBothAdmissions()`.
- `t03CompareEntireViewAndDetectChangesInsideD1()`.
- `t04MissingUnverifiedSourceAndCallbackExceptionsFailClosed()`.
- `t05RejectMissingInvalidReversedAndExpiredClock()`.
- `t06RejectAgedExpiredAndTruncatedSelectedHistory()`.
- `t07StableReadsReturnOnlyDisplayResultWithExplicitCoverage()`.
- `t08DetectMidRecheckChangesAndFinalAdmissionRevocation()`.

El coordinador verifica identidad y cobertura del carrito, reproyecta D1 dos veces al instante histórico y una vez con el reloj del host. Cada lectura utiliza los controles de permisos y vigencia de D1. Compara la vista histórica completa y los registros subyacentes de la lectura actual. D3A conserva el grupo comercial y omite la identidad exacta de la observación seleccionada. Si alguna observación compatible con el grupo pierde comparabilidad, se rechaza la pre-revisión.

El resultado positivo solo sirve para mostrar el chequeo observado en DEV. Las lecturas son independientes y pueden detectar cambios entre muestras. El alcance certificado utiliza un host inyectado. V2 productiva OFF y G15 NOT_RUN. D3C, D4, E y F quedan fuera de este cierre.

Permiso READY consumido. Release CAS previsto en época 1550 con destino al commit documental de cierre.
