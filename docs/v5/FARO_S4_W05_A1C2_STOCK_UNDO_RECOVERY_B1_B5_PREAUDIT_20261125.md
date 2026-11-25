# FARO V5 · W05-A1 — auditorías anticipadas **CONDICIONALES** de unidades siguientes

Fecha 2026-11-25. Baseline comprobada `d28bfae7199cedce988ae2e7a0376ea6e09b7191`, último código funcional certificado A1C0B0 `d2f0a7a60be0ddf1cce9c02c80879ef0f79d5b27`. A1C0B1 es la **única unidad** con permiso READY hoy y B6 pendiente.

**IMPORTANTE:** Documento de preparación adelantada, no evidencia suficiente para permiso funcional de esta unidad. B1–B3 respaldados por código actual; B4 son rutas candidatas a congelar después del B7 predecesor, B5 matriz adversarial de futuros tests, NO resultados ejecutados. Tras B7 previo Work deberá (a) revalidar HEAD, fuentes y dependencias; (b) reconciliar el delta; (c) congelar rutas exactas reales; (d) completar auditoría B5 final y guard documental; (e) emitir READY propio y recién después B6. No usar este documento como permiso ni citarlo como B7.

## Unidad y precondición
**A1C2: Reconciliación y Undo compensatorio de recepción.**
Dependencia: A1C1 recepción real Review/Apply con receipts B7.

## B1 — Evidencia disponible y alcance
- Los recibos H11R y el journal Apply son durables y se pueden consultar; la tabla de receipts original no posee expresamente un campo de compensación que pruebe Undo de stock.
- No basta revertir borrando filas; debe conservarse historia y receipt original junto al evento inverso, y auditar el saldo remanente.
- UNKNOWN no se resuelve repitiendo a ciegas la operación: buscar receipt COMMITTED y usar coordinator de recovery.

## B2 — Comparación V4.4 y reutilización real V5
- Reusar ApplyRecoveryResolver, ApplyRetryReceiptGate, FileBootstrapJournal, ReceiptHistory, y patrón HouseholdNeedUndo únicamente como referencia de revisión, sin heredar su operación ni grant.
- Conservar FBS2v9/cutover de compras y receipts, y WAL/ABA validado de C2C0/C2C1.
Referencia histórica obligatoria: `docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md` y `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`; no copiar V4.4 ni inferir implementación desde documentos.

## B3 — Alternativas y decisión provisional
- A: operación Undo de stock con compensación append-only, parent receipt y nuevo idempotency key, commit/receipt únicos; full pointer, generation, revisión de stock y consentimiento foreground.
- B DESCARTADA: DELETE/UPDATE de receipt histórico o recálculo informal de cantidades.
- C DEPENDENCIA: verificar descriptor de operación Undo compatible en catálogo180; no adjudicar a stock.receive permiso de deshacer si un contrato distinto es obligatorio.
- D DEPENDENCIA ESQUEMA: si SQL12 no expresa compensation, diseñar SQL13 y nueva FBS2 compatible ANTES de emitir READY o escribir SQLite.
Revisar referencias técnicas ya citadas por los handoffs previos: SQLite transactions, WAL y versionado; Android testing cuando corresponda. No traer nuevas dependencias ni APIs pagas.

## B4 — Rutas CANDIDATAS: todavía NO exactas ni autorizadas
- v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockReceiveUndo.kt (nuevo, candidato)
- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHouseholdStockCompensationPort.kt (nuevo, candidato)
- v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W05StockUndoRecoveryHost.kt (nuevo, candidato)
- v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W05StockUndoRecoveryHostTest.kt (nuevo, candidato)
- .github/workflows/faro-v5-s4-w05-a1c2-stock-undo-recovery.yml (nuevo, candidato)
**La lista B4 es un mapa de investigación, NO permiso funcional; puede cambiar según el código B7 previo.** Antes de B6, decidir el mínimo conjunto exacto sin incluir más rutas de las necesarias, fijar unit_id propio, evidencia SHA y parent-preflight.

## B5 — Matriz adversarial propuesta; pruebas todavía NO ejecutadas
- T01: Undo de receipt 7 revierte saldo con evento compensatorio y deja historial original
- T02: doble Undo del mismo receipt -> una compensación / segundo DENIED
- T03: Undo con stock consumido o saldo insuficiente -> DENIED sin negativo
- T04: fallo PREPARED no compromete receipt; recovery determinista
- T05: COMMITTED sin confirmación Workspace -> reconciliar receipt existente sin duplicar
- T06: UNKNOWN sin receipt -> conservar incertidumbre, no replay inseguro
- T07: puntero ABA, revocación, principal y approval obsoletos -> DENIED
- T08: backup/restore y reinicio preservan vínculo original/compensación sin borrar historia
Todas las pruebas finales deben ejecutarse con JUnit XML original, 0 failures/errors/skips y guard por SHA; si B7 falla, RED_FIX acotado con permiso actualizado.

## Condición de cierre de la preparación y siguiente paso
A1C1 B7 obligatorio. Auditar primero catálogo de Undo de stock y capacidad SQL12 de evento compensatorio: si exige nueva migración/versionado, dividir trabajo seguro y permisos. No prometer que v9 existente soporta un futuro SQL13 sin cambio.

## Exclusiones globales
Un solo writer CAS; no main ni V4.4, no autorización de stock productivo, Apply productivo, V2 productiva, pagos o Auto/Noche; no inferir compra desde carrito/presupuesto/backup. Usuario no autorizó ensayo físico Moto G15. **Status: PREAUDITED_CONDITIONAL — NOT_READY / B6_NOT_AUTHORIZED / B7_NOT_RUN.**
