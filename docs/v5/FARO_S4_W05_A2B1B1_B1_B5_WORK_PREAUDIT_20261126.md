# FARO V5 · W05-A2B1B1 · Host Review→Confirm→Apply y receipts

Unidad: `S4_W05_A2B1B1_STOCK_RESERVATION_REVIEW_APPLY_HOST`. Base c4d1901549005e7fdb990eb5b83aba62099eb01c. Estado **PREAUDITADO CONDICIONAL, NO_READY; requiere B7 predecesor y delta audit**. Evidencia B1–B5 de diseño, NO son resultados de tests. V2 productiva, stock real, pagos, Auto/Noche OFF. Moto G15 NOT_RUN.

## B1 — Evidencia

Entrada: A2B1B0 B7 certificado y B1A Review exacto. S4W05StockReceiveDevApplyHost y S4W05StockUndoDevApplyHost ya integran Workspace COMMITTED, kernel apply y receipts bajo permisos específicos.

## B2 — V4.4 y V5

Reusar coordinator, retry receipt gate, plan fingerprint, final commit gate y source Workspace, V4.4 recuperación PREPARED/COMMITTED/UNKNOWN. No recrear coordinator ni conceder permiso desde chat.

Matriz V4.4: `docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md`; reutilización: `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`. No copiar legado ni repetir B7 previo.

## B3 — Soluciones externas

REFERENCIA https://developer.android.com/reference/androidx/room/Transaction.html, ADAPTAR https://www.sqlite.org/wal.html y offline-first https://developer.android.com/topic/architecture/data-layer/offline-first; DESCARTAR Apply background, reuso de N-035 stock.receive o autoridad del modelo.

Sin nuevas dependencias, servicios ni APIs pagas. Ante límites reales, retener control local y pruebas adversariales; los proyectos externos son patrones/referencia, no permisos.

## B4 — Diseño y límites

Host en DEV SQL14 con root lease, ticket COMPLETED, principal/epoch, Review inerte, confirmación humana para cada RESERVE y RELEASE; validar envelope y Workspace COMMITTED, crear Applying+receipt en transacción única con port B1B0. Release es evento nuevo, no DELETE; retry idéntico devuelve mismo receipt, UNKNOWN se reconcilia sin segundo efecto. No exponer UI shipping ni pagos.

**Rutas candidatas; reconfirmar tras B7 de dependencia, sin READY anticipado:**

1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
2. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W05StockReservationDevApplyHost.kt`
3. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W05StockReservationDevApplyHostTest.kt`
4. `.github/workflows/faro-v5-s4-w05-a2b1b1-dev-apply-host.yml`

No main, V4.4, ni activación de stock productivo. Toda ruta adicional obliga a rehacer solo el delta del permiso.

## B5 — Pruebas negativas propuestas

- T01 Review no muta, confirmación humana ejecuta RESERVE2 con receipt COMMITTED.
- T02 Release1 exige segundo consentimiento/receipt sin borrar origen.
- T03 Workspace fuente no COMMITTED, fingerprint/receipt equivocados STOP.
- T04 Crash antes/después COMMIT y retry no duplican evento.
- T05 ACL revocada/ABA/host cerrado/compra no recibida => STOP.

Exigir guardia 7 bloques documental SUCCESS, evidencia blob SHA, permiso READY individual y preflight de **cada ruta** antes de B6; código `FARO-UNIT:S4_W05_A2B1B1_STOCK_RESERVATION_REVIEW_APPLY_HOST` en línea propia, CI del SHA real, XML JUnit original tests>0 y failures=errors=skipped=0, B7 independiente, permiso consumido, writer CAS RELEASED. Para sucesores hay que reconfirmar HEAD y contrato real después del predecesor. Nunca declarar B7 desde este documento.

## Última revisión B1-B5, delta previo a Work (26/11/2026)

Each reserve/release needs distinct explicit Review, foreground confirmation and receipt even though both are N-035. A release is a typed compensating ledger event, not generic undo.execute; revalidate pointer and reconcile UNKNOWN without duplicate events.

Estado: PREAUDITADO_CONDICIONAL/NO_READY hasta B7 del antecesor, nueva guardia documental, permiso propio y preflight exacto. Este delta NO ejecuta B6/B7.
