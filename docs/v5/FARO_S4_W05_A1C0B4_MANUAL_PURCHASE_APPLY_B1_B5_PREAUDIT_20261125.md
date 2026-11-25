# FARO V5 · W05-A1 — auditorías anticipadas **CONDICIONALES** de unidades siguientes

Fecha 2026-11-25. Baseline comprobada `d28bfae7199cedce988ae2e7a0376ea6e09b7191`, último código funcional certificado A1C0B0 `d2f0a7a60be0ddf1cce9c02c80879ef0f79d5b27`. A1C0B1 es la **única unidad** con permiso READY hoy y B6 pendiente.

**IMPORTANTE:** Documento de preparación adelantada, no evidencia suficiente para permiso funcional de esta unidad. B1–B3 respaldados por código actual; B4 son rutas candidatas a congelar después del B7 predecesor, B5 matriz adversarial de futuros tests, NO resultados ejecutados. Tras B7 previo Work deberá (a) revalidar HEAD, fuentes y dependencias; (b) reconciliar el delta; (c) congelar rutas exactas reales; (d) completar auditoría B5 final y guard documental; (e) emitir READY propio y recién después B6. No usar este documento como permiso ni citarlo como B7.

## Unidad y precondición
**A1C0B4: Alta de compra manual explícita mediante N-036.**
Dependencia: A1C0B3 cutover SQL12 B7 y fuente durable de A1C0B2 B7.

## B1 — Evidencia disponible y alcance
- Catálogo oficial v5/spec/generated/contract-baseline.tsv blob 5653a9758f682f683a852dcf286211bae71c41d0: shopping.purchase.record N-036, household, REALITY_MUTATION, BINDABLE, MERGE; shopping.purchase.read READ_ONLY; stock.receive N-035 ajeno.
- Reality12 household_confirmed_purchase STAGED tabla STRICT y purchase_ref PK, quantity unit, confirmed_by, confirmed_at_ms, MANUAL_EXPLICIT y revision; no representa todavía un alta efectiva.
- El draft W04 CommercialCartDraft y ObservedCartBudget NO son compra: una declaración manual explícita del usuario se registra como provenance MANUAL_EXPLICIT (no verificación externa).

## B2 — Comparación V4.4 y reutilización real V5
- Reutilizar patrón S4W04HouseholdNeedApplyHost Review/Approve/Apply idempotencia, source principal/epoch/full pointer y receipts, sin reutilizar grants ni manipular necesidades.
- Usar HouseholdStockReceiveDevAdmission solo como lectura para recibos futuros, no como autoridad para registrar compra. N-036 precisa binding/ejecutor/grant propios.
Referencia histórica obligatoria: `docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md` y `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`; no copiar V4.4 ni inferir implementación desde documentos.

## B3 — Alternativas y decisión provisional
- A: nuevo Review/Apply de compra manual foreground sobre N-036 con plan inmutable, purchase_ref, item, unit, cantidad, origen MANUAL_EXPLICIT; CAS contra revisión/replay y receipt transaccional.
- B DESCARTADA: inventar purchase.confirm, ampliar 180 operaciones o activar shopping.payment.record al ingresar stock.
- C DESCARTADA: crear compras desde carrito/budget o desde backup restaurado; no autenticación presente del usuario.
- D DEPENDENCIA: Reality12 purchase_ref UNIQUE y revision por fila no certifican por sí solos idempotencia de Apply. Verificar receipt+intent key y transacción; si exige evolución SQL13, programarla/versionar backup antes de B6.
Revisar referencias técnicas ya citadas por los handoffs previos: SQLite transactions, WAL y versionado; Android testing cuando corresponda. No traer nuevas dependencias ni APIs pagas.

## B4 — Rutas CANDIDATAS: todavía NO exactas ni autorizadas
- v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdManualPurchaseReview.kt (nuevo, candidato)
- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold12PurchaseApplyPort.kt (nuevo, candidato)
- v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W05ManualPurchaseDevApplyHost.kt (nuevo, candidato)
- v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W05ManualPurchaseDevApplyHostTest.kt (nuevo, candidato)
- .github/workflows/faro-v5-s4-w05-a1c0b4-manual-purchase-apply.yml (nuevo, candidato)
**La lista B4 es un mapa de investigación, NO permiso funcional; puede cambiar según el código B7 previo.** Antes de B6, decidir el mínimo conjunto exacto sin incluir más rutas de las necesarias, fijar unit_id propio, evidencia SHA y parent-preflight.

## B5 — Matriz adversarial propuesta; pruebas todavía NO ejecutadas
- T01: compra manual 10 unidades CONFIRMED, nueva fila exacta, receipt único y origen explícito
- T02: replay del mismo intent -> 1 fila/1 COMMITTED receipt, no suma doble
- T03: misma reference con item/cantidad distintos -> conflicto DENIED sin reemplazo
- T04: carrito/precio/inferencia o consentimiento absent -> cero escrituras
- T05: principal/epoch/full pointer distinto, foreground revocado -> DENIED
- T06: fallo antes/después COMMIT -> PREPARED/UNKNOWN reconciliado antes de reintentar
- T07: read source de N-036 con compra confirmada, unidad revisada y revisiones durables después de reabrir
- T08: restauración FBS2v9 mantiene compra pero no rehidrata grant ni identidad de sesión
Todas las pruebas finales deben ejecutarse con JUnit XML original, 0 failures/errors/skips y guard por SHA; si B7 falla, RED_FIX acotado con permiso actualizado.

## Condición de cierre de la preparación y siguiente paso
La operación N-036 existe pero el binding/grant NO. B7 de cutover SQL12 + comprobación de receipt/idempotencia y scope real de grafo obligatorios antes de emitir READY.

## Exclusiones globales
Un solo writer CAS; no main ni V4.4, no autorización de stock productivo, Apply productivo, V2 productiva, pagos o Auto/Noche; no inferir compra desde carrito/presupuesto/backup. Usuario no autorizó ensayo físico Moto G15. **Status: PREAUDITED_CONDITIONAL — NOT_READY / B6_NOT_AUTHORIZED / B7_NOT_RUN.**
