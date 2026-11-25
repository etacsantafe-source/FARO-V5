# FARO V5 · W05-A1 — auditorías anticipadas **CONDICIONALES** de unidades siguientes

Fecha 2026-11-25. Baseline comprobada `d28bfae7199cedce988ae2e7a0376ea6e09b7191`, último código funcional certificado A1C0B0 `d2f0a7a60be0ddf1cce9c02c80879ef0f79d5b27`. A1C0B1 es la **única unidad** con permiso READY hoy y B6 pendiente.

**IMPORTANTE:** Documento de preparación adelantada, no evidencia suficiente para permiso funcional de esta unidad. B1–B3 respaldados por código actual; B4 son rutas candidatas a congelar después del B7 predecesor, B5 matriz adversarial de futuros tests, NO resultados ejecutados. Tras B7 previo Work deberá (a) revalidar HEAD, fuentes y dependencias; (b) reconciliar el delta; (c) congelar rutas exactas reales; (d) completar auditoría B5 final y guard documental; (e) emitir READY propio y recién después B6. No usar este documento como permiso ni citarlo como B7.

## Unidad y precondición
**A1C1: Recepción de stock parcial con Review y Apply DEV.**
Dependencia: A1C0B4 N-036 compra manual persistida B7 y A1C0A contrato inerte N-035 B7.

## B1 — Evidencia disponible y alcance
- stock.receive N-035 aparece en catálogo oficial household/REALITY_MUTATION/BINDABLE; el contrato A1C0A (HouseholdStockReceiveDevAdmission) solo devuelve ReviewOnly, no emite grant.
- Reality11/12 ledger de receipts dispone de household_stock_receipt_state revision y household_stock_receipt intent_key UNIQUE; no hay binding ACTIVE de recepciones todavía.
- La compra confirmada debe ser lectura confiable de generación vigente; ni precio/compra no confirmada ni la rehidratación de backup conceden permisos.

## B2 — Comparación V4.4 y reutilización real V5
- Reusar HouseholdStockReceiptPreview (validación 10 compradas, 7 recibidas, 3 pendientes), AtomicApply, MaterialWriteGate, plan/fingerprint y receipt idempotente.
- Separación de grants: shopping_need.* / shopping.purchase.record N-036 no implica stock.receive N-035; consentimiento fresco por recepción.
Referencia histórica obligatoria: `docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md` y `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`; no copiar V4.4 ni inferir implementación desde documentos.

## B3 — Alternativas y decisión provisional
- A: Review→Approve→Apply para recibir cantidad física contra compra confirmada N-036, transacción única Reality con full pointer, revision CAS, intent_key único y receipt durable.
- B DESCARTADA: usar cantidad comprada como stock físico automáticamente; requiere evento de recepción explícito.
- C DESCARTADA: lectura preview como mutación o reusar W04 Undo de necesidades.
- D RIESGO: aplicar 7 y luego 3 requiere chequear suma de receipts por compra/item/unidad de forma transaccional, con Long y sin overflow. No actualizar saldo como memoria fuera de DB.
Revisar referencias técnicas ya citadas por los handoffs previos: SQLite transactions, WAL y versionado; Android testing cuando corresponda. No traer nuevas dependencias ni APIs pagas.

## B4 — Rutas CANDIDATAS: todavía NO exactas ni autorizadas
- v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockReceiveReview.kt (nuevo, candidato)
- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold12StockReceiveApplyPort.kt (nuevo, candidato)
- v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W05StockReceiveDevApplyHost.kt (nuevo, candidato)
- v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W05StockReceiveDevApplyHostTest.kt (nuevo, candidato)
- .github/workflows/faro-v5-s4-w05-a1c1-stock-receive-dev.yml (nuevo, candidato)
**La lista B4 es un mapa de investigación, NO permiso funcional; puede cambiar según el código B7 previo.** Antes de B6, decidir el mínimo conjunto exacto sin incluir más rutas de las necesarias, fijar unit_id propio, evidencia SHA y parent-preflight.

## B5 — Matriz adversarial propuesta; pruebas todavía NO ejecutadas
- T01: comprado 10, recibido 7 -> físico +7, pendiente3
- T02: segunda recepción3 -> pendiente0; overreceive y unidades erróneas DENIED
- T03: reintento mismo intent idempotente; distinto payload misma clave rechazada
- T04: grant faltante o grant de compra/need -> DENIED
- T05: pointer ABA, cambio revisión o compra revocada/stale -> DENIED
- T06: dos recepciones simultáneas total >10: CAS deja solo válidas
- T07: crash entre SQL y receipt no crea 2 efectos tras recovery
- T08: backup SQL12/FBS2v9 preserva saldo y provenance; no pagos
Todas las pruebas finales deben ejecutarse con JUnit XML original, 0 failures/errors/skips y guard por SHA; si B7 falla, RED_FIX acotado con permiso actualizado.

## Condición de cierre de la preparación y siguiente paso
A1C0B4 B7 + fuente de compra de host aislado, SQL12 ACTIVE y puerto de Apply seguro antes de B6. No emitir READY anticipado.

## Exclusiones globales
Un solo writer CAS; no main ni V4.4, no autorización de stock productivo, Apply productivo, V2 productiva, pagos o Auto/Noche; no inferir compra desde carrito/presupuesto/backup. Usuario no autorizó ensayo físico Moto G15. **Status: PREAUDITED_CONDITIONAL — NOT_READY / B6_NOT_AUTHORIZED / B7_NOT_RUN.**
