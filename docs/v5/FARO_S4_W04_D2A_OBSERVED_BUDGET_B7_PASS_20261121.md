# FARO V5 — W04-D2A · Presupuesto comercial estimado observacional — B7 PASS

Fecha local 2026-11-21. Unidad `S4_W04_D2A_OBSERVED_BUDGET_DEV`. Rama `work/s3-night-20261102`. B1–B5 `docs/v5/FARO_S4_W04_D2A_OBSERVED_BUDGET_B1_B5_20261120.md` (blob `57f8a0bc4878c578706b48fbef9dae74b238f852`), permiso inicial READY exact3 y guardia de permiso `44856950863` SUCCESS. D0 y D1 ya B7 PASS; no repetir.

## B7

**PASS_SCOPED_DEV_JVM_OBSERVED_ONLY** luego de dos RED_FIX trazados, sin ocultar fallos.

**Código final** `98a764eae0879b1849d643a8955e33edcc0058f1`. Tres rutas aprobadas, publicadas originalmente juntas en `7410f26599fb55254a8524e7f1d1c6c39a36be25`: motor `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04ObservedCartBudget.kt` (blob final `8d1f81a75e919ccb1dd5dd217b9ce1c81870b740`); 12 tests JUnit5 `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04ObservedCartBudgetTest.kt` (blob `edda2dc6bfacd56592b067715e235cfd5cf7477b`); workflow `.github/workflows/faro-v5-s4-w04-d2a-observed-budget.yml` (blob final `dff9b2a1091b5718de8d633221c0033c7dec3b86`). 

**Trazabilidad RED_FIX:**
1. Código inicial `7410f26599fb55254a8524e7f1d1c6c39a36be25`, guardia `44899047034` SUCCESS, CI `44899047048` FAILURE por Kotlin `SMARTCAST_IMPOSSIBLE` en nullable entre módulos, JUnit/XML NOT_RUN. B7 RED_FIX documentado en `docs/v5/FARO_S4_W04_D2A_B7_RED_FIX_20261121.md`, checkpoint `e643e7626d4a25e002a2dd5671dad79181933bc4`.
2. Fix de solo motor `863c002f997a135b35e9e837fc20e0fc6160d5e1`; guardia `44899537465` SUCCESS, CI `44899537390` FAILURE: preflight PASS pero workflow exigía `count == 3` en RED_FIX de un archivo, JUnit/XML NOT_RUN. B7 RED_FIX2 en `docs/v5/FARO_S4_W04_D2A_B7_RED_FIX2_20261121.md`, checkpoint `099a78116459aef4990d1ac95e975c9ccbc6c1dd`, guardia documental `44899649308` SUCCESS.
3. Fix de solo workflow `98a764eae0879b1849d643a8955e33edcc0058f1`, subconjunto acotado 1..3, preflight de cada ruta contra parent READY y marcador FARO-UNIT intactos.

**Prueba final REAL por SHA exacto:**
- Guardia de protocolo `44899739792` COMPLETED SUCCESS, `seven-block-evidence`.
- CI dirigida `44899739776` COMPLETED SUCCESS, job `136832927798`, `:app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04ObservedCartBudgetTest`, **BUILD SUCCESSFUL in 4m 29s**.
- Paso `Require exactly twelve D2A JUnit XML tests zero failure error skip` SUCCESS; log `W04_D2A_OBSERVED_BUDGET_JVM_XML_PASS 12/12`; artefacto real `14705727077`, `w04-d2a-observed-budget-xml`.
- **Descarga e inspección independiente** del ZIP: un XML `TEST-ar.com.faro.v5.app.S4W04ObservedCartBudgetTest.xml`, atributos `tests=12, failures=0, errors=0, skipped=0`, **12 nombres de testcase distintos**, sin nodos failure/error/skipped. SHA-256 ZIP `05a144f6754a6d1a35f8c4ad0a15b1b973ad2455390728b0cab0e4399cdadf63`; SHA-256 XML `522e540d2648522e2907fb5cccb761e5aff459bb365776ca3ebc839753c2221a`.

**Alcance final:** selección explícita de observación D1 por línea, vigencia/frescura/base sin promo, packCount 1, aritmética Long con overflow fail-closed, subtotal por moneda+comercio+localidad+medio, faltantes como PARTIAL, sourceRef opaco y advertencia no precio vigente. Sin descuento automático, FX, pagos, Apply/receipts, Finance, SQL, Internet, stock ni UI. Evidence2 STAGED; Android G15 NOT_RUN; V2 productiva OFF. D2B promociones, D3 Review/Apply, D4 integración, W04-E UI y W04-F global siguen sin B6 READY.

**Resolución B7:** PASS_SCOPED_DEV_JVM. Consumir permiso READY→BLOCKED; writer CAS RELEASED tras checkpoint; la siguiente unidad D2B requiere B1–B5 propios y permiso independiente. No repetir D0/D1/D2A.
