# FARO V5 — W04-D2B · B6/B7 handoff

Rama work/s3-night-20261102; unidad S4_W04_D2B_PROMOTION_SCENARIO_DEV; preaudit docs/v5/FARO_S4_W04_D2B_PROMOTION_SCENARIO_B1_B5_20261121.md (blob ab153ed4e2fc4ec6e8874b924cd9d39a1fca659e). D0/D1/D2A B7 PASS y no se repiten. Writer cas a verificar, READY exact3 sólo tras guard real del presente commit. El design anterior D2_BUDGET_PROMOTIONS_DESIGN es histórico y no permiso.

## B6
Crear exclusivamente:
- v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04ObservedPromotionScenario.kt
- v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04ObservedPromotionScenarioTest.kt
- .github/workflows/faro-v5-s4-w04-d2b-promotion-scenario.yml

Función pura de simulación de UNA promo explícita por línea D2A; reconstruir base D2A desde D1 para detectar manipulación, exigir promo única en history y status PROMOTION_NOT_BASE, same merchant/currency/locality/payment/presentation/amount, no condiciones textuales, fechas y eligibility GENERAL. No descontar de compra real ni inferir cashback/cuotas. Porcentaje bps con FLOOR integer split; fijo min(baseMinor,value) UNA vez por línea, no multiplicar packs. Outputs immutable WHAT_IF, grupos separados y partial; sourceRef literal inert. Sin SQL, money transfer, Finance, FX, egress, pagos, stock, V2 prod, Android G15 ni nuevas dependencias. No editar D0/D1/D2A, C1B1/C2, gradle, manifest o Work old.

JUnit5 exactamente 12 adversarios del B5, test dirigido :app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04ObservedPromotionScenarioTest, Java21 Android SDK37, XML (12,0,0,0), subir artefacto. Guardia de código y preflight de cada ruta en parent READY; primer commit EXACT3; para RED_FIX permitir 1..3 rutas sin quitar preflight. Mensaje contiene FARO-UNIT:S4_W04_D2B_PROMOTION_SCENARIO_DEV.

## B7
Verificar SHA/rutas, guard SUCCESS, CI run/job, XML real y artifact. Si hay rojo registrar B7 RED_FIX antes de correcciones de la misma unidad; no falsificar PASS. Cerrar B7 PASS solo después, consumir permiso a BLOCKED, writer RELEASED, checkpoint. D2C/D3/D4/E/F no tienen permiso. G15 NOT_RUN.
