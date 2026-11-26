# FARO V5 · W05-A2B1B1 · Host Review→Confirm→Apply y receipts

Unidad: `S4_W05_A2B1B1_STOCK_RESERVATION_REVIEW_APPLY_HOST`. Base c4d1901549005e7fdb990eb5b83aba62099eb01c. Estado **DELTA B1–B5 VERIFICABLE CONTRA B7 PREDECESOR; NO_READY hasta guardia y permiso propio**. Evidencia B1–B5 de diseño, NO son resultados de tests. V2 productiva, stock real, pagos, Auto/Noche OFF. Moto G15 NOT_RUN.

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

Estado: DELTA_B1_B5_DOCUMENTED/NO_READY hasta guardia documental SUCCESS y emisión de permiso exact4 independiente. Este delta NO ejecuta B6/B7.


## Delta auditado tras A2B1B0 B7 PASS (2026-11-26)

**Baseline exacto:** e8554aff688cec73eb439266ca2bd8b9da4ffd1a.
El predecesor A2B1B0 fue certificado por guardias 45837426645 y
45838252498 SUCCESS; CI 45837426749 y XML JUnit 5/5 originales,
documento FARO_S4_W05_A2B1B0_B7_PASS_SQL14_ATOMIC_RESERVATION_20261126.md
(blob 746cd52824815690437c10c81658bcaa32c44878).
No se repite ese B6/B7 ni A2B0* ni A2B1A.

### B1 — evidencia real y brecha

El nuevo port RealityHousehold14ReservationApplyPort implementa reserva y
liberación sobre una sola RealityStore.run BEGIN IMMEDIATE, readback y
commit fence; N-016 SQL13 permanece deshabilitado en SQL14.
Falta conexión desde Workspace Review COMMITTED/APPROVED y consentimiento
de primer plano mediante un host aislado con ticket de cutover SQL14 COMPLETED.
No confundir éxito del port con receipts de operación aprobada.

### B2 — adaptación con mínima duplicación

Reutilizar el coordinator ReviewedConversationApplyCoordinator, plan fingerprint,
PREPARED/COMMITTED, ApplyRetryReceiptGate, final commit gate, y patrón de
S4W05StockReceiveDevApplyHost / S4W05StockUndoDevApplyHost. Ambos son precedentes
de arquitectura, no autorizaciones transferibles. Conservar fuente Workspace
COMMITTED real y revalidar root lock, pointer y epoch antes y después de COMMIT.

### B3 — investigación y selección

Patrones ya documentados: Android Room Transaction
https://developer.android.com/reference/androidx/room/Transaction.html,
SQLite WAL https://www.sqlite.org/wal.html y offline-first
https://developer.android.com/topic/architecture/data-layer/offline-first.
ADAPTAR transacción SQL local y host explícito; DESCARTAR servidor externo,
automatización, consentimientos implícitos, grant de stock.receive reutilizado
y segundo COMMIT/recibo paralelo. Sin dependencias nuevas ni APIs pagas.

### B4 — interfaz y cuatro rutas congeladas

Rutas exactas:
1. v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt
2. v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W05StockReservationDevApplyHost.kt
3. v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W05StockReservationDevApplyHostTest.kt
4. .github/workflows/faro-v5-s4-w05-a2b1b1-dev-apply-host.yml

El grafo actualmente tiene lectura SQL14 aislada y hosts RW SQL12/SQL13,
pero NO un host de Apply ACTIVE14 con root lease. En la primera ruta se debe
admitir modalidad ACTIVE14 bajo selección COMPLETED y mismo fencing cinco
stores, sin modificar RealityStore (certificado en B1B0), ni habilitar
Undo N-016, consumo A2C o UI shipping. En la segunda ruta conectar port
tipado a un Review inerte, validación del catálogo N-035 por acción,
confirmación explícita separada RESERVE y RELEASE, lease de foreground,
principal/epoch, fingerprint, Applying, recibo COMMITTED y retry/reconcile
sin segundo efecto. No dar permisos del modelo.

### B5 — negativas de aceptación obligatorias

T01: Review no muta; confirmación explícita RESERVE2 produce un solo receipt.
T02: RELEASE1 exige nuevo Review y nuevo consentimiento; append-only íntegro.
T03: Reintento con misma identidad retorna receipt COMMITTED, sin duplicar.
T04: UNKNOWN/ABA/stale pointer/revocación/host cerrado => STOP o reconcile
     solo desde recibo real, nunca segunda mutación.
T05: Físico=3, reservado=2, una reserva adicional 2 se rechaza; N-016
     de SQL13 no puede abrir sobre SQL14.
T06: Workspace no COMMITTED, fingerprint/envelope alterados y grant cruzado
     entre reserve/release => STOP sin receipt.
T07: G15 NOT_RUN, no stock real, pagos OFF, available UNKNOWN hasta A2C.

Estado del delta: DOCUMENTED_ONLY, no código B6/B7, no permiso READY en este
commit. Emitir guardia documental SUCCESS primero, después permiso exact4 en
otro commit documental y ejecutar preflight de las cuatro rutas.
