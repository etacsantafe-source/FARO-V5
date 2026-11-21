# FARO V5 — W04-D2B · Escenarios hipotéticos de promociones observadas — B7 PASS

Fecha 2026-11-21. Unidad S4_W04_D2B_PROMOTION_SCENARIO_DEV. Rama work/s3-night-20261102. B1–B5: docs/v5/FARO_S4_W04_D2B_PROMOTION_SCENARIO_B1_B5_20261121.md, blob ab153ed4e2fc4ec6e8874b924cd9d39a1fca659e. Baseline previo D2A B7 PASS (CI 44899739776, XML 12/12), NO REPETIR.

## B7 — Certificación independiente

**PASS_SCOPED_DEV_JVM_WHAT_IF**. Commit funcional exacto 783760d8a36419f4f6e41eddcbb2a613df9f4814, tres rutas nuevas de permiso individual READY:
- Motor v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04ObservedPromotionScenario.kt, blob f8c654d84856a5dd5dffdd867359aa9752ce5b3e.
- Tests v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04ObservedPromotionScenarioTest.kt, blob 35dde3d8043cbd229292b79a7985385c44b6b602.
- CI .github/workflows/faro-v5-s4-w04-d2b-promotion-scenario.yml, blob ae12105127469bebd05e0fb6c30f062f86f6064b.

**Guardia código** 44902037906 COMPLETED SUCCESS, job seven-block-evidence; permiso READY parent guard 44901692841 SUCCESS y preauditoría doc guard 44901600570 SUCCESS. Workflow CI ejecutó preflight de cada una de las 3 rutas (3 líneas FARO_PROTOCOL_PREFLIGHT_PASS en log), no se alteraron otros owners ni documentos de control junto a código.

**CI dirigida real** 44902037919 COMPLETED SUCCESS, job 136839764393 d2b-promotion-scenario-dev, SHA exacto 783760d8a36419f4f6e41eddcbb2a613df9f4814. Gradle :app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04ObservedPromotionScenarioTest, Java21/Android SDK37, BUILD SUCCESSFUL in 3m 49s. Paso XML SUCCESS: W04_D2B_PROMOTION_WHAT_IF_JVM_XML_PASS 12/12.

**Evidencia XML independiente**: artefacto 14706477108 w04-d2b-promotion-what-if-xml, descargado de GitHub; ZIP con único archivo testDebugUnitTest/TEST-ar.com.faro.v5.app.S4W04ObservedPromotionScenarioTest.xml, 12 testcases distintos, tests=12 failures=0 errors=0 skipped=0, sin nodos failure/error/skipped. SHA-256 ZIP f90408e50f7ee1d64fb8395cc45be30681c7a8278dd32ffe53f81e88bdcb2b50; SHA-256 XML 42a4cb6ef31f44a26a28c723e4d7ddbf747af6f8b93ae79fb166d5c89f4c3b85. Tests T01–T12 cubren seals/fingerprints, selección, base forjada, history ambigua, stale/issuedAt/validity, merchant/currency/locality/payment/presentation, restrictions/conditions, 10% y rounding FLOOR, fijo cap una vez, no stacking, grupos/partial y sourceRef opaco/inmutabilidad. CERO RED_FIX para D2B.

**Límites y riesgos conservados**: El resultado es sólo WHAT_IF de evidencia histórica STAGED; no demuestra precio vigente, elegibilidad real, descuento aplicado, crédito, cashback, ahorro efectivo o gasto. No une monedas ni comercios, no ejecuta fuente URL, no incorpora una promoción condicionada y no altera Reality, Evidence, Finance, Apply/receipts, DB, red, stock o pago. No hay nueva pantalla ni prueba Android/G15 (NOT_RUN). V2 productiva, Auto y Noche OFF. Los límites de C1B1 respecto de mecanismo inmediato/reintegro siguen abiertos para futuros D2C/D3 con preauditoría independiente.

**Resolución B7**: PASS_SCOPED_DEV_JVM_WHAT_IF. Consumir READY a BLOCKED, guardar este documento con SHA real, actualizar checkpoint/continuidad y liberar writer global CAS (RELEASED). W04-D2C/D3/D4/E/F NO tienen autorización READY. No recertificar D0/D1/D2A/D2B.
