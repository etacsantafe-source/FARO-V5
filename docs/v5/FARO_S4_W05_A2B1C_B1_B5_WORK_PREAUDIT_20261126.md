# FARO V5 · W05-A2B1C · Aceptación global A2B DEV

Unidad: `S4_W05_A2B1C_GLOBAL_RESERVATION_DEV_ACCEPTANCE`. Base c4d1901549005e7fdb990eb5b83aba62099eb01c. Estado **DELTA B1–B5 auditado tras B7 predecesor; NO_READY hasta guardia y permiso exact2**. Evidencia B1–B5 de diseño, NO son resultados de tests. V2 productiva, stock real, pagos, Auto/Noche OFF. Moto G15 NOT_RUN.

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

## Última revisión B1-B5, delta previo a Work (26/11/2026)

Global acceptance requires cutover C1 real, then ACTIVE14 reader C0 using its completed ticket, followed by reserve/release and v11 5-store restore. Include Undo-below-reserved negative test. No consumed/available claims before A2C.

Estado: PREAUDITADO_CONDICIONAL/NO_READY hasta B7 del antecesor, nueva guardia documental, permiso propio y preflight exacto. Este delta NO ejecuta B6/B7.


## Delta de B1–B5 posterior al cierre real A2B1B1 (2026-11-26)

Baseline exacto f75dbd0b717ae8c501ad06a93d50707cd3f0cff7.
A2B1B1 B7 PASS: código final 4f26fd97446bcdec113a8f724fbb8aed7b387384,
guardia 45864041608, CI 45864041512, XML original 5/5, ZIP artefacto
15139926525, cierre documental y guardia 45865091592 SUCCESS.
Prueba física G15 NOT_RUN. No repetir A2B0 ni A2B1A/A2B1B0/A2B1B1 B7.

### B1 — evidencia y límite

El nuevo host SQL14 auténtico ya verifica un ticket de cutover COMPLETED,
root lease, Review inerte, aprobación foreground, dos receipts separados,
retry/reconcile, ABA y revocación. El físico neto de la fixture FBS2v11
compra10/recibos7+3/Undo7 es 3; el reserve2 y release1 de la
restauración dejan reservado 1. Falta aceptación global sin volver a
cambiar el contrato de producción.

### B2 — reutilización del sistema durable

Reusar solo adaptadores YA certificados: FBS2v11, cutover SQL14 C1,
reader ACTIVE14 C0, port de reserva B1B0 y host Review/Apply B1B1.
La suite de aceptación debe asegurar el trayecto de cinco owners y
receipt COMMITTED tras reinicio; no duplicar la implementación.

### B3 — contraste de alternativas

Se conserva el enfoque local SQLite WAL, restauración autenticada y
JUnit XML original. Se descartan mocks de pago/stock, tests sin artefacto,
red, APIs pagas, activation shipping y cambios en main o V4.4.
Patrones documentados previamente: Android testing y SQLite isolation.
No se agregan dependencias.

### B4 — alcance congelado exact2

1. v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W05StockReservationGlobalDevAcceptanceTest.kt
2. .github/workflows/faro-v5-s4-w05-a2b1c-global-dev-acceptance.yml

Nuevas pruebas globales sobre un bootstrap y archivo FBS2v11 real:
cutover 12→13→14 y ticket COMPLETE, conservar historial, físico3,
reservar/liberar con comprobación de receipts, persistencia al reabrir,
lectura ACTIVE14 y efecto idempotente. Validar que no se concede
consentimiento desde la IA, stock shipping ni pagos.
CI compilará la aplicación V2 DEV aislada y preservará XML JUnit original.
No cambio funcional productivo ni Android G15.

### B5 — adversarial requerido

T01: restaurar FBS2v11 5 stores y seleccionar SQL14 con ticket real,
     conservar compra10, recibos7+3, Undo7, físicos3.
T02: operación Review→Confirm→Apply por acciones separadas y recibos
     independientes; liberar saldo hasta reservado1, reiniciar y leer
     el balance y el historial persistente sin duplicación.
T03: Review sin aprobación, permiso revocado y stale revision => no mutación.
T04: ABA de puntero/host cerrado o root lease ajeno => STOP sin Apply;
     sin consumo, available todavía UNKNOWN antes de A2C.
T05: CI SHA y artefacto JUnit XML original tests>0 y cero errores,
     fallos u omitidos; B7 independiente, writer RELEASED.

No introducir permisos SQL14 adicionales. Esta preparación documental NO
es una certificación de A2B1C: sigue NO_READY hasta guardia del delta
y emisión separada de permiso READY exact2.
