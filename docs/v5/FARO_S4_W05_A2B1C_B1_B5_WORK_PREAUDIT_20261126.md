# FARO V5 · W05-A2B1C · Aceptación global A2B DEV

Unidad: `S4_W05_A2B1C_GLOBAL_RESERVATION_DEV_ACCEPTANCE`. Base c4d1901549005e7fdb990eb5b83aba62099eb01c. Estado **PREAUDITADO CONDICIONAL, NO_READY; requiere B7 predecesor y delta audit**. Evidencia B1–B5 de diseño, NO son resultados de tests. V2 productiva, stock real, pagos, Auto/Noche OFF. Moto G15 NOT_RUN.

## B1 — Evidencia

Entrada: A2B1B1 B7 certificado. A1C4 B7 integró JVM+Android DEV, A2A stock físico5/5, A2B0A schema7/7. Falta cadena real FBS2v11→SQL14 ACTIVE→reservas/release→restore/restart.

## B2 — V4.4 y V5

Reusar fixture histórico compra10/recepción7+3/Undo7, cinco stores y prueba receipt. V4.4 consistencia AtomicApply; no repetir CI A1 sin brecha.

Matriz V4.4: `docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md`; reutilización: `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`. No copiar legado ni repetir B7 previo.

## B3 — Soluciones externas

ADAPTAR https://developer.android.com/training/testing y https://www.sqlite.org/isolation.html; REFERENCIA Android offline-first https://developer.android.com/topic/architecture/data-layer/offline-first. DESCARTAR aprobar por test mock o solo build sin XML.

Sin nuevas dependencias, servicios ni APIs pagas. Ante límites reales, retener control local y pruebas adversariales; los proyectos externos son patrones/referencia, no permisos.

## B4 — Diseño y límites

Suite de aceptación aislada JVM + Android emulador dirigido si viable, sobre SQLite real y FBS2v11 auténtico: origen SQL13 seleccionado, stage/restauración SQL14 y ticket, Review/Apply reserve2, release1, receipts/history/readback sin pagos. `physical=3`, `reserved=1`; campo `available` sigue UNKNOWN hasta A2C. No G15 físico ni producción.

**Rutas candidatas; reconfirmar tras B7 de dependencia, sin READY anticipado:**

1. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W05StockReservationGlobalDevAcceptanceTest.kt`
2. `.github/workflows/faro-v5-s4-w05-a2b1c-global-dev-acceptance.yml`

No main, V4.4, ni activación de stock productivo. Toda ruta adicional obliga a rehacer solo el delta del permiso.

## B5 — Pruebas negativas propuestas

- T01 Backup v11 5-store/restauración/cutover/reopen real.
- T02 Compra10/7+3/Undo7 físico3/reserva2/release1 reserved1.
- T03 Crash/ABA/colisiones idempotentes/oversubscription fail closed.
- T04 Finance, pagos, catálogo sin cambios, available no se muestra.
- T05 JUnit XML original SHA exacto, cero errors/failures/skipped, B7 independiente.

Exigir guardia 7 bloques documental SUCCESS, evidencia blob SHA, permiso READY individual y preflight de **cada ruta** antes de B6; código `FARO-UNIT:S4_W05_A2B1C_GLOBAL_RESERVATION_DEV_ACCEPTANCE` en línea propia, CI del SHA real, XML JUnit original tests>0 y failures=errors=skipped=0, B7 independiente, permiso consumido, writer CAS RELEASED. Para sucesores hay que reconfirmar HEAD y contrato real después del predecesor. Nunca declarar B7 desde este documento.
