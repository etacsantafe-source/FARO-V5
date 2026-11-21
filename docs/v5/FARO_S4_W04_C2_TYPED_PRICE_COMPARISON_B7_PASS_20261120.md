# FARO V5 — W04-C2 · Comparación tipada de precios observados STAGED — B7 PASS

2026-11-20. Unidad `S4_W04_C2_STAGED_TYPED_PRICE_COMPARISON_DEV`. B1–B5 en `docs/v5/FARO_S4_W04_C2_TYPED_PRICE_COMPARISON_B1_B5_20261120.md`, blob `7f942889f144677b8cd7adb8fe1bafd90e0a500c`. Preauditoría documental guard `44850398451` SUCCESS. READY EXACT3 en checkpoint `137447d99896ecb065e36883d629f6f75cb9349d`, guard `44850499681` SUCCESS.

## B7

**PASS_SCOPED_STAGED_DEV_READ_ONLY_C2.** Código `d208213f01c4e5b290cee0e23afe1472dfa3cdf1` sobre exactamente TRES rutas:
- `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialTypedPriceComparison.kt` (nueva): read-model de Evidence2 tipado, validación total del watermark H1, orden canónico, cortes asOf/maxAge, history completo dentro límite, grupos por currency/locality/qty/unit/pack/payment; no mezclas/FX; por comercio toma registro más reciente sin borrar antiguos; mínimo observado sólo con dos comercios distintos y sin truncamiento; sólo precio HISTÓRICO, no vigencia/stock real garantizados.
- `v5/evidence/src/test/kotlin/ar/com/faro/v5/evidence/CommercialTypedPriceComparisonTest.kt` (nueva): 14 pruebas.
- `.github/workflows/faro-v5-s4-w04-c2-typed-price-comparison.yml` (nueva): preflight EXACT3 y pruebas Evidence JVM con XML.

**Evidencia verificada:** protocolo guard código `44850685830` COMPLETED SUCCESS. CI `44850685841` COMPLETED SUCCESS SHA exacto; job `136681058308`; `:evidence:test` **BUILD SUCCESSFUL in 3m 1s**, JUnit XML C2 `(tests=14,failures=0,errors=0,skipped=0)`, marca `W04_C2_STAGED_TYPED_PRICE_COMPARISON_JVM_XML_PASS 14/14`. Artefacto `14684613913`, `w04-c2-staged-typed-price-comparison-xml`, 9372 bytes, `expired=false`. Regresión `:evidence:test` completa.

**Auditoría de casos:** vacíos y límites, observedAt=null distinto acquiredAt, terms físicos ausentes vs UNKNOWN, ARS/USD/localidades/pack/cantidad/unidad y payment separados, expirado/futuro/stale, promo/restricción sin descuento automático, comercio duplicado con observación vieja preservada, truncamiento sin mínimo falso, fuente hostil literal sin ejecución ni leak de idempotencia, hash/orden alterado FAIL-CLOSED y SQLite STAGED real sin mutación en relectura.

**Límites:** No se modificaron Evidence SQL, historial, grafo ACTIVE, Finance/Household, C1B2 certificado, Android ni exportación; no hay nuevo host de permisos ni interfaz, sólo proyección pura que requiere snapshot H1 STAGED ya autorizado. No stock/cotización comercial confirmada. Android/G15 NOT_RUN, V2 productiva OFF. C3 refresh mutable separado con su propio B1–B7; W04-C global aún OPEN.

**Decisión:** C2 B7 PASS scoped DEV. Consumir READY C2→BLOCKED y avanzar a C3 sin heredar permiso. Writer RELEASED tras checkpoint documental.
