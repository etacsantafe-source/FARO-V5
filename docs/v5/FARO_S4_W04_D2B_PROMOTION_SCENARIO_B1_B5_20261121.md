# FARO V5 — W04-D2B Escenarios de promociones observadas — B1–B5

Fecha 2026-11-21. Unidad propuesta: S4_W04_D2B_PROMOTION_SCENARIO_DEV. BASELINE HEAD 542aa0a82a503282a6f9b7502d37ab2701ce957b; writer RELEASED epoch 1510 observado antes de esta preparación. **DOC_ONLY: NO READY/B6 antes de guardia documental y permiso nuevo exact3.**

## B1 — Evidencia y brecha
- D2A: docs/v5/FARO_S4_W04_D2A_OBSERVED_BUDGET_B7_PASS_20261121.md, blob 03cc2c65c6869fecfab0f81fab81bdda7e5306be, código final 98a764eae0879b1849d643a8955e33edcc0058f1, CI 44899739776 SUCCESS XML 12/12, guard 44899739792 SUCCESS. El Estimate es una estimación histórica seleccionada expresamente: cartFingerprint, sourceWatermark, asOf, líneas con needId/product/merchant/currency/locality/payment/observedAt/sourceRef/packCount/observedAmountMinor/estimatedMinor y grupos separados, PARTIAL posible. No cambia Finance.
- D1: CommercialCartCoverageView y CommercialCartCoverageLine en v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialCartCoverage.kt, blob 72d408ab9dfe81acf99a81711b6c733bb68d658e. Contiene history de CommercialObservedHistoryEntry por necesidad, fuente STAGED, fecha máxima y watermark; no autorización viva permanente.
- C2: v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialTypedPriceComparison.kt, blob 904e072b12d92e29a79056bc9b95881110fe2032. Status PROMOTION_NOT_BASE se clasifica sólo después de fecha/frescura/localidad/presentación/método/eligibilidad; no es una promoción cobrada ni precio vigente.
- C1B1: CommercialEvidenceTypedTerms.kt, blob f5078d1ebb9a6541fa673a9b4dc3d4903fdef2c1. DISCOUNT_PERCENT 1..10000 bps, FIXED_AMOUNT >0 minor units, promotionSourceRef literal, dates, locality, paymentMethod, eligibility, conditions y presentación. **No hay campo certificado para distinguir descuento inmediato, reintegro posterior, cuotas, límite por cliente o stacking.** D2B no puede certificar ningún ahorro real.
- D2 previo: docs/v5/FARO_S4_W04_D2_BUDGET_PROMOTIONS_DESIGN_20261120.md, blob de56aa44d98aa777f39cc909c8e96457a65ff0ae, DESIGN_ONLY. Brecha: no existe motor de simulaciones hipotéticas con enlace explícito D2A/D1. No modificar fuentes certificadas.

## B2 — Comparación con V4.4 y reúso
P02 docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md, blob 9a6388ae6be298eb6536a3dc81e1266bf4d42385, L11/L14/L15: SmartCompra legacy tenía carrito/estimación vs pagado y distinción entre beneficio esperado y cobrado. La función V5 hereda intención UX, no autoridad comercial. En V5 Reality (Household) mantiene necesidades; Evidence registra observaciones; Finanzas maneja dinero, no descuentos hipotéticos. Reusar D1 + D2A in-memory, nunca copiar gateways V4.4 ni sumar ARS/USD o inferir precios actuales.

## B3 — Investigación externa comprobable (21/11/2026)
A. Square Order Discounts https://developer.squareup.com/docs/orders-api/discounts y Price Adjustments https://developer.squareup.com/docs/orders-api/price-adjustments: descuentos por línea/pedido, distintas reglas y redondeos. REFERENCIA para impedir aplicar una promoción de pedido a una línea, y para fijar redondeo explícito. No copiar motor de pagos ni afirmar paridad con Square.
B. Stripe reglas de promoción https://stripe.com/au/resources/more/what-is-a-promo-code y restricciones técnicas https://javadoc.io/static/com.stripe/stripe-java/25.13.0/com/stripe/model/PromotionCode.Restrictions.html: mínimos de compra, perfil de cliente, vencimientos y productos pueden cambiar elegibilidad. ADAPTAR política fail-closed: condiciones libres no verificables => rechazo de simulación de aplicabilidad. Ni Stripe ni la evidencia local confirman beneficios del usuario.
C. Joda-Money https://www.joda.org/joda-money/userguide.html y proyecto https://github.com/JodaOrg/joda-money (licencia Apache-2.0): tipos de moneda/valor y redondeo explícito. REFERENCIA; NO incorporar dependencia, costo/riesgo de migración mayor que el delta de aritmética simple Long existente.
D. Martin Fowler Money https://martinfowler.com/eaaCatalog/money.html: patrón para no perder precisión en montos y definir rounding. ADAPTAR minor units enteras y control de exceso.
ELEGIDA: motor Kotlin puramente local de simulación WHAT_IF por línea, sin efectos ni red. DESCARTADAS: aplicar ahorro automáticamente, mezclar métodos/localidades/monedas, cashback como descuento de contado, FX, HTTP/scraping/SDK, engine fiscal y combinar promos. HIPÓTESIS verificables: promoción stale/restringida jamás obtiene resultado; 10% de 24000 minor = 2400 minor hipotéticos; descuento fijo no se multiplica sin contrato.

## B4 — Contrato cerrado para B6, sólo JVM DEV
- Crear objeto interno S4W04ObservedPromotionScenario.simulate(coverageView, baseEstimate, explicitChoices), donde cada elección trae needId, productKey, un EXACTO CommercialObservedHistoryEntry incluido una sola vez en la history D1 de ese need, y cartFingerprint/sourceWatermark/asOf originales. 1..200 elecciones, cada need una vez, sin stacking ni elección automática de lowestObserved.
- Revalidar la identidad coverage/baseEstimate y recomponer D2A desde la misma vista: para cada línea base recuperar un registro histórico base UNÍVOCO por need, productKey, merchant, currency, locality, paymentMethod, sourceRef, observedAt, amountMinor y packCount, llamar S4W04ObservedCartBudget.estimate y exigir igualdad estructural del Estimate completo. Si D2A fue forjado o cambiado, rechazar el conjunto.
- Promo seleccionada exige status PROMOTION_NOT_BASE, no history truncado, fuente literal y promotionSourceRef presente, kind DISCOUNT_PERCENT o FIXED_AMOUNT, promoValue válido, issuedAt y observedAt conocidos/no futuros, observado dentro de maxAge, asOf dentro de validFrom/Until inclusivos, locality KNOWN, eligibility GENERAL, conditions==null, presentation quantity/unit completas, packCount==1, paymentMethod no UNKNOWN. No parsear texto conditions ni sourceRef. Aun con conditions==null, mecanismo real inmediato/cashback NO PROBADO: salida sólo WHAT_IF.
- La promo tiene que coincidir con la base elegida en needId, productKey, merchant, currencyCode, localityCode, paymentMethod, presentación cantidad+unidad y amountMinor. Rechazar si difiere, jamás convertir gramos/paquetes ni sumar divisas.
- Aritmética de ejemplo por línea: baseMinor = D2A.estimatedMinor. Porcentaje: (baseMinor / 10000) * bps + ((baseMinor % 10000) * bps) / 10000, rounding FLOOR a minor unit sin Double y sin overflow para baseMinor>=0, bps 1..10000. Fijo: min(baseMinor,promotionValue), aplicado exactamente UNA VEZ A LA LÍNEA (no por pack). scenarioAfterMinor=baseMinor-discountMinor >=0. Subtotales sólo de líneas con promo simulada, agrupados por (currency,merchant,locality,payment), sumas checked. No total de compra global.
- Resultado inmutable con identidad de vista, líneas con contexto/fuente/promo/fórmula/minor units, grupos y missingNeedIds/noModelados. Marcas: WHAT_IF_HISTORICAL_ONLY_NOT_ELIGIBILITY_OR_REDEMPTION y NOT_CURRENT_PRICE_OR_CASHBACK. Si D2A PARTIAL o promo faltante: PARTIAL. La ausencia de promoción no significa ahorro comprobado cero.
- Rechazos tipados INVALID_BASE, UNKNOWN_OR_DUPLICATE_SELECTION, MISMATCHED_FENCE, AMBIGUOUS_HISTORY, INELIGIBLE_PROMOTION, CONTEXT_MISMATCH, OVERFLOW; fail-closed atómico, no resultados parciales. Sin SQL, I/O, red, Apply, receipts, Finance, stock, UI, G15, nuevas dependencias o owner.
- EXACTAMENTE tres rutas nuevas tras permiso READY:
  1. v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04ObservedPromotionScenario.kt
  2. v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04ObservedPromotionScenarioTest.kt
  3. .github/workflows/faro-v5-s4-w04-d2b-promotion-scenario.yml
- Workflow: Java21/Android SDK37, :app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04ObservedPromotionScenarioTest, 12 tests JUnit5 realmente descubiertos, XML exacto (12,0,0,0), artifact, guardia, FARO-UNIT:S4_W04_D2B_PROMOTION_SCENARIO_DEV. Verificar cada ruta cambiada contra permiso READY del padre; primer commit exact3, posteriores RED_FIX subconjunto 1..3 (lección D2A); workflow no fuerza count==3 para fixes.

## B5 — Matriz adversarial (TODAS NOT_RUN; doce casos para B6)
T01 base/view fingerprint, watermark o asOf distintos: MISMATCHED_FENCE sin resultados.
T02 need/product no existe, promo extra o dos promos para un need: UNKNOWN_OR_DUPLICATE_SELECTION; no stacking.
T03 base D2A falsificada o sourceRef/amount cambiado: INVALID_BASE, nunca confiar en subtotal proporcionado.
T04 historial sin entrada exacta, duplicada/ambigua o truncada: AMBIGUOUS_HISTORY / INELIGIBLE_PROMOTION.
T05 promo stale/observedAt null o futuro, issuedAt ausente, vencida, status distinto de PROMOTION_NOT_BASE: INELIGIBLE_PROMOTION.
T06 merchant, currency, locality, payment, presentation y amountMinor deben coincidir con D2A; si no, CONTEXT_MISMATCH sin FX.
T07 elegibilidad RESTRICTED/UNKNOWN, condiciones textuales, tipo NONE/UNKNOWN, medio UNKNOWN: rechazo; ningún cashback como cobrado.
T08 10% de base 24000 minor => descuento 2400, hipotético 21600; bps 1234 sobre monto pequeño = FLOOR estable.
T09 fixed discount mayor que línea: cap a base, net >=0; aplicado UNA vez aunque haya dos packs.
T10 combinaciones 2x descuento/tipo diferente, orden/stacking no autorizado: rechazo atómico.
T11 D2A parcial y múltiples comercios/monedas: grupos separados, no total/ahorro global, lista de no modelados.
T12 sourceRef hostil opaco sin ejecución, listas inmutables, no cambios D1/D2A/Evidence/Finance/Apply/DB/HTTP/stock, overflow checked, físico G15 NOT_RUN.

## Gate
**B1_B5_DOC_ONLY_NOT_READY**. Publicar primero este documento + checkpoint DOCUMENTAL bajo writer CAS; verificar guard documental SUCCESS SHA exacto, evaluar brechas B1–B5, emitir permiso READY independiente sólo para S4_W04_D2B_PROMOTION_SCENARIO_DEV/3 rutas en commit DOC_ONLY, exigir guard y preflight. B6/B7 sólo después. D2C/D3/D4/W04-E/F no están autorizadas, V2 productiva OFF, G15 NOT_RUN.
