# FARO V5 — W04-D3C · WORK TURBO · Cerrar trazabilidad de selección histórica

## MISIÓN ÚNICA
Terminar exclusivamente W04-D3C B6 y B7, dev JVM de SOLO LECTURA, 8 JUnit5. Repositorio etacsantafe-source/FARO-V5, rama work/s3-night-20261102. No repetir ninguna unidad B7 PASS: W1/W2/W3; W04-A/B/C; D0/D1/D2A/D2B/D2C0/D2C1/D3A/D3B.

Leer AGENTS.md, docs/v5/FARO_CONTINUITY_POINTER.json, docs/v5/FARO_PROTOCOL_WORK_PERMIT.json, docs/v5/FARO_S4_W04_D3C_EXPLICIT_OBSERVATION_TRACE_B1_B5_20261122.md (blob dce044319075cbc2f8321f5124d940357fa91262), y este handoff. B1–B5 y prueba de guardia documental 45027120964 SUCCESS ya realizados. Confirmar HEAD, writer RELEASED, permiso READY exact3, guardia READY SUCCESS y preflight por cada ruta antes de programar.

## B6 — TRES RUTAS EXACTAS
1. v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04ExplicitObservationTrace.kt
2. v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04ExplicitObservationTraceTest.kt
3. .github/workflows/faro-v5-s4-w04-d3c-observation-trace.yml

Contrato: función pura trace(draft D0, historicalCoverage D1, estimate D2A, explicitChoices D2A, preview D3A, rechecked D3B). Comprobar identidad exacta cartFingerprint/sourceWatermark/asOf/previewDigest y que rechecked NO es Apply. **Reutilizar el motor D2A.estimate** sobre las elecciones explícitas para exigir igualdad EXACTA de la observación histórica y toda la Estimate, sin sustituir por una observación de igual comercio/moneda. Contrastar needId/productKey/packCount del carrito y cobertura con la estimación, las filas D3A por GroupKey, subtotales históricos checked y necesidades faltantes. No recalcular descuentos hipotéticos, no mezclar monedas. Emitir solo TracedForDisplay inmutable, digest del dominio w04.d3c.selection-trace.v1, sourceRef literal, timestamp, imports históricos y warnings NO_CURRENT_QUOTE y NO_FINANCE_STOCK_APPLY. No permisos, grants, ApplyPlan, compras, receipts, pagos, cotización actual, stock, egress, DB, UI o APIs.

OCHO JUnit adversariales T01–T08 están detallados en B5, incluye misma tienda con observaciones distintas, selección falsificada, estimate/subtotal forjado, contextos y coverage, orden/digest/monedas, notas/completitud, inmutabilidad/overflow. CI dirigida Java21 SDK37:
:app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04ExplicitObservationTraceTest
Exigir XML tests=8 failures=0 errors=0 skipped=0 y 8 nombres distintos; guardar artifact XML y comprobar contenido descargado. Primera confirmación de código EXACT3; mensaje con FARO-UNIT:S4_W04_D3C_EXPLICIT_OBSERVATION_TRACE_DEV. Preflight del padre READY por cada ruta. Para RED_FIX, sólo subconjunto 1..3 rutas tras B7 RED_FIX documentado.

## B7
Writer único CAS y RELEASED durante CI. Verificar guardia real del código, CI real, XML y artefacto por SHA; sólo entonces B7 PASS documental con permit READY consumido a BLOCKED, writer RELEASED y guardia final SUCCESS. Si falla, registrar la evidencia y corregir lo probado exclusivamente. No iniciar D4/E/F ni una unidad de Apply; autorización de compra requeriría otro contrato.
