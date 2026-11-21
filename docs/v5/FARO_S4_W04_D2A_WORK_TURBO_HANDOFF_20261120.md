# FARO V5 — W04-D2A · Work Turbo quick handoff — 2026-11-20

Alcance **D2A ESTIMACIÓN OBSERVADA (no precio vigente/no checkout)**. Repo `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`. Leer AGENTS.md, pointer, permit actual y `docs/v5/FARO_S4_W04_D2A_OBSERVED_BUDGET_B1_B5_20261120.md`. D0 y D1 B7 PASS DEV ya certificados; D1 CI final `44856347260` 12/12. No repetir C ni D0/D1. **Revalidar HEAD, writer RELEASED y guard READY del SHA exacto antes de escribir.**

## B6
Unidad `S4_W04_D2A_OBSERVED_BUDGET_DEV`, permiso READY exclusivamente a:
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04ObservedCartBudget.kt` nueva
2. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04ObservedCartBudgetTest.kt` nueva
3. `.github/workflows/faro-v5-s4-w04-d2a-observed-budget.yml` nueva

Implementar en Kotlin puro app un estimador por selección explícita de observación de D1, sin precio automático. `requestedPackCount × observedAmountMinor` sólo para Evidence2 CONOCIDA, base sin promo, presentación packCount==1, no expirado ni restricted, overflow fail-closed y subtotal agrupado por `currency+merchant+locality+paymentMethod`. Faltantes => parcial, nunca total confirmado. Datos sourceRef opacos; no FX, stock, recibos, Apply, Finance, SQL, red o V2 productiva. No modificar D1, D0, C2, app build.gradle ni otros workflows.

Agregar **12 JUnit5 tests realmente descubiertos** que cubran B5 y workflow de preflight EXACT3, Java21, SDK37, `:app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04ObservedCartBudgetTest`, XML (12,0,0,0) y artefacto. Cerrar B7 sólo con SHA/CI/guard y XML; si falla, documentar RED_FIX de la misma unidad antes de reparación. Consumir READY a BLOCKED y RELEASED writer.

Con créditos Work semanales limitados (10% en captura), **terminar sólo D2A**, no avanzar D2B/D3/D4/Android. Se puede posponer Work: GitHub doc queda recuperable.
