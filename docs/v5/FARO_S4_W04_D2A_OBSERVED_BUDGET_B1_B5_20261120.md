# FARO V5 — W04-D2A · Estimación observada del presupuesto comercial, sin promociones — B1–B5

Fecha 2026-11-20, baseline D1 final código `7c7aaa18acb9c3b9d084fbd0c02f0e1f6ee13d18`. Unidad siguiente `S4_W04_D2A_OBSERVED_BUDGET_DEV`. **B1–B5 DOC_ONLY: no READY/B6 hasta guard del documento, permiso exact3 y preflight.** D0 y D1 ya B7 PASS scoped DEV; no repetir.

## B1 — Evidencia y borde funcional
- D0 `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialCartDraft.kt` blob `eb5b528bf87311e053751f822b0cfe50466a30ef`, B7 `docs/v5/FARO_S4_W04_D0_CART_DRAFT_B7_PASS_20261120.md`, CI `44853703822` 12/12. El carrito entrega `requestedPackCount` explícito, `needQuantityThousandths`, unidad y `productKey` por línea; no equipara pack con necesidades.
- D1 `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialCartCoverage.kt` blob `72d408ab9dfe81acf99a81711b6c733bb68d658e`: `CommercialCartCoverageView(cartFingerprint,sourceWatermark,asOfEpochMillis,maxAgeMillis,lines,notice)` y `CommercialCartCoverageLine(needId,productKey,requestedPackCount,history,groups,status,truncated)`. B7 `docs/v5/FARO_S4_W04_D1_CART_COVERAGE_B7_PASS_20261120.md`, CI FINAL `44856347260` 12/12, guard `44856347218`. D1 valida grants y fuente H1 al producir read model, pero la salida no es token permanente ni garantía de precio.
- C2 `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialTypedPriceComparison.kt` blob `904e072b12d92e29a79056bc9b95881110fe2032` clasifica `CommercialObservationComparisonStatus.COMPARABLE_BASE_OBSERVED` sólo bajo condiciones tipadas, **no** `CURRENT_QUOTE`. Cada entrada `CommercialObservedHistoryEntry` contiene `PortableCommercialTypedObservation` con merchant/currencyCode/amountMinor/sourceRef/fecha/terms.
- C1B1 `CommercialEvidenceTypedTerms.kt` blob `f5078d1ebb9a6541fa673a9b4dc3d4903fdef2c1`: presentación quantity+unit+packCount, método, elegibilidad, localidad, promoción NONE/UNKNOWN/PERCENT/FIXED; sin fórmula de reintegro validada. `:app` ya depende de `:evidence`, kernel y JUnit sin cambios de Gradle.
- No existe un precio actual confirmado, checkout ni presupuesto de compra B7. `docs/v5/FARO_S4_W04_D2_BUDGET_PROMOTIONS_DESIGN_20261120.md` blob `de56aa44d98aa777f39cc909c8e96457a65ff0ae` dejó D2 design-only. D2A sólo crea **estimación observada** pura, sin promociones; D2B posterior separado.

## B2 — V4.4 y semántica
V4.4 SmartCompra ofrecía presupuesto orientativo, múltiples comercios y promociones. V5 debe rotular toda multiplicación de observación antigua como **estimación histórica no confirmada**, nunca ticket final, precio vigente o gasto Finance. Solicitar selección explícita del usuario por línea; no elegir automáticamente el precio mínimo de C2 (aunque dos comercios sean comparables). Cada fuente/marca/moneda/localidad/método permanece visible, sin convertir paquetes para satisfacer necesidades. Dinero real y pagos pertenecen sólo a Finanzas tras un futuro Apply autorizado, no a D2A.

## B3 — Decisión de alternativas
ELEGIDA: motor in-memory de aritmética de enteros con `Math.multiplyExact` y `Math.addExact`, agrupando subtotales por moneda+comercio+localidad+medio (contexto original), sin FX ni double. Reusar sólo los registros C2 clasificados `COMPARABLE_BASE_OBSERVED` dentro de cobertura D1 y selección explícita EXACTA; sin precios inventados, sin consulta de red ni nuevas tablas.
ADAPTAR: `cartFingerprint`, `sourceWatermark` y `asOf` de D1 como **identidad de estimación**, NO autorización de checkout (D3 revalida fuente/epoch).
DESCARTAR: best-price automático, sumar ARS/USD, subtotal de comercios distintos como presupuesto en un solo comercio, cashback futuro o promoción de condición incierta como descuento de contado, redondeos Double, multiplicar cantidad de necesidad por precio por pack, permitir bundle sin contrato, generar facturas/recibos, stock, reservas o saldos.

## B4 — Contrato D2A, solo DEV pura read-only
1. `S4W04ObservedCartBudget.estimate(coverageView,explicitChoices)`, donde cada elección referencia `needId`, `productKey` y **exactamente un** `CommercialObservedHistoryEntry` de la línea D1. No aceptar fuente de precio/currency/amount ingresada por texto; comprobar pertenencia estructural y unicidad del registro (si dos registros son idénticos y no hay ID estable, exigir aclaración, no elegir ambiguo).
2. Para cada línea elegida, validar `status==COMPARABLE_BASE_OBSERVED`, terms no nulos y coherentes, `eligibility==GENERAL`, `promotionKind==NONE`, `presentationQuantityThousandths` y `presentationUnit` completos, `packCount==1` para poder multiplicar packs sin adivinar bundles; `validFrom/Until` contiene `asOf`, observadoAt real no futuro/obsoleto. Sin evidencia completa: rechazo explícito de la línea, no transmutar a confirmado.
3. Validar `requestedPackCount` 1..10000 y `amountMinor>0`; estimación `quantityOfPacks × amountMinor` con overflow checked Long. No convertir gramos o kilos ni afirmar necesidad satisfecha. Subtotales por **currencyCode,merchant,localityCode,paymentMethod** originales. Una o más monedas => saldos por moneda, NUNCA un total cruzado.
4. Si hay líneas sin elección, reportar `coverage=PARTIAL` y `missingNeedIds`; los subtotales sólo suman observaciones elegidas y se etiquetan `OBSERVED_ESTIMATE_PARTIAL_NOT_A_QUOTE` (no "total de compra"). Con todas elegidas, `OBSERVED_ESTIMATE_ONLY_NOT_CURRENT` igualmente no confirmado. Elecciones duplicadas/extrañas → REJECTED sin output.
5. Salida inmutable con `cartFingerprint`, `sourceWatermark`, `asOfEpochMillis`, detalle por línea (need,product,merchant,currency,locality,payment,observedAt,sourceRef,packCount,amountMinor,estimatedMinor) y grupos de subtotal, advertencia de antigüedad/stock desconocido. Ningún port de DB, Receipt, Finance, HTTP ni permiso. D3 revisará estado original antes de operación real; D2A **no** la ejecuta.
6. Alcance de CI: 12 casos JUnit5 dirigidos `:app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04ObservedCartBudgetTest`, Android SDK37 configuración, XML **exacto 12/12** sin failures/errors/skips, artefacto y guard. Nunca tocar app build.gradle, D0/D1/C2/C3 o SQL.

**Tres rutas exactas propuestas D2A (no READY hasta guard):**
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04ObservedCartBudget.kt` (NUEVA)
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04ObservedCartBudgetTest.kt` (NUEVA)
- `.github/workflows/faro-v5-s4-w04-d2a-observed-budget.yml` (NUEVA)

## B5 — Matriz adversarial futura (NOT_RUN)
T01 sin selección: cobertura PARTIAL y subtotales ausentes; no confundir vacío con $0 de compra.
T02 needId/productKey no perteneciente a D1, fuente falsa, registro duplicado ambiguo: REJECTED.
T03 elección del precio `lowerObserved` automática prohibida; requiere record exacto explícito.
T04 ARS y USD separados por moneda, no FX; mismo currency pero dos merchant/localities/payment separados.
T05 × packs con monto positivo normal: 2×12000 minor=24000 minor, sin convertir gramos.
T06 `Math.multiplyExact` y `Math.addExact` overflow fail-closed, nunca wrap/clamp.
T07 promo UNKNOWN/PERCENT/FIXED, restricted eligibility o payment UNKNOWN => no descuento/cálculo D2A.
T08 snapshot con validez/frescura/observedAt null/expired/conditional => no estimación elegible.
T09 packCount terms 2 vs requested packCount 2 => no inferir 4 unidades: rechazado sin contrato.
T10 cambio sourceWatermark/cartFingerprint no permite asociar una selección de otro view.
T11 lista con línea sin elección => PARTIAL, no total global; fuente literal URL no ejecutable.
T12 no crea gastos Finance, receipts, ApplyPlan, stock, HTTP, egress, ni muta D1/Household; Android G15 NOT_RUN.

## Gate
`B1_B5_PREAUDIT_DOC_ONLY`. Primero guard documental SUCCESS del SHA que incluye D1 B7, luego READY individual **EXACT3 en commit sólo documental**, guard/preflight parent SUCCESS, recién B6/CI/B7 D2A. D2B promos, D3 Review, D4 integración, W04-E UI y W04-F global permanecen sin permiso. Prohibido activar V2 productiva.
