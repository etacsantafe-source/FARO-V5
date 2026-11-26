# FARO V5 · W05-A2B1B0 · Writer Reality SQL14 con invariantes

Unidad: `S4_W05_A2B1B0_SQL14_RESERVATION_ATOMIC_APPLY_PORT`. Base c4d1901549005e7fdb990eb5b83aba62099eb01c. Estado **PREAUDITADO CONDICIONAL, NO_READY; requiere B7 predecesor y delta audit**. Evidencia B1–B5 de diseño, NO son resultados de tests. V2 productiva, stock real, pagos, Auto/Noche OFF. Moto G15 NOT_RUN.

## B1 — Evidencia

Entrada: A2B1A B7 certificado y SQL14 ACTIVE DEV. RealityHousehold13CompensationApplyPort y RealityHousehold12StockReceiveApplyPort usan RealityTransactionScope, CAS/receipt; SQL14 ledger no tiene writer de reservas y releases.

## B2 — V4.4 y V5

V4.4 idempotencia y Undo compensatorio, nunca borrar hechos; V5 RealityStore transacción única y receipts, SQLite schema14/triggers.

Matriz V4.4: `docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md`; reutilización: `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`. No copiar legado ni repetir B7 previo.

## B3 — Soluciones externas

ADAPTAR https://www.sqlite.org/lang_transaction.html y triggers https://www.sqlite.org/lang_createtrigger.html; REFERENCIA Grocy https://github.com/grocy/grocy/blob/master/grocy.openapi.json; DESCARTAR check de saldo afuera de misma transacción (TOCTOU), sumar contador en RAM.

Sin nuevas dependencias, servicios ni APIs pagas. Ante límites reales, retener control local y pruebas adversariales; los proyectos externos son patrones/referencia, no permisos.

## B4 — Diseño y límites

Port de apply interno en misma RealityTransactionScope: comprobar revisión, pointer y lease, fuente física realmente recibida y compensada, reservar <= físico neto menos reservas vigentes; liberar por `reservationRef` correspondiente sin sobreliberación, event intent único, grants revalidados antes COMMIT. Persistir solo RESERVE/RELEASE append-only + receipt/attempt kernel; no COMMIT independiente, no mutate Finance. Debe proteger Undo de recepción si provocaría físico menor que reservado.

**Rutas candidatas; reconfirmar tras B7 de dependencia, sin READY anticipado:**

1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold14ReservationApplyPort.kt`
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
3. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold14ReservationApplyPortTest.kt`
4. `.github/workflows/faro-v5-s4-w05-a2b1b0-atomic-apply.yml`

No main, V4.4, ni activación de stock productivo. Toda ruta adicional obliga a rehacer solo el delta del permiso.

## B5 — Pruebas negativas propuestas

- T01 Físico3, reserva2 => aceptada; segunda reserva2 => denegada.
- T02 RELEASE1+1 => saldo0, tercera release => denegada.
- T03 Doble tap y dos concurrentes con total>3 => solo válidos.
- T04 Crash antes COMMIT rollback completo, UNKNOWN receipt se reconcilia.
- T05 Undo recepción que rompería reserved<=physical => denegar; ABA/grant stale STOP.

Exigir guardia 7 bloques documental SUCCESS, evidencia blob SHA, permiso READY individual y preflight de **cada ruta** antes de B6; código `FARO-UNIT:S4_W05_A2B1B0_SQL14_RESERVATION_ATOMIC_APPLY_PORT` en línea propia, CI del SHA real, XML JUnit original tests>0 y failures=errors=skipped=0, B7 independiente, permiso consumido, writer CAS RELEASED. Para sucesores hay que reconfirmar HEAD y contrato real después del predecesor. Nunca declarar B7 desde este documento.

## Última revisión B1-B5, delta previo a Work (26/11/2026)

CROSS-OPERATION INVARIANT: if physicalNet=3 and reserved=2, N-016 Undo of a receipt reducing physicalNet to1 must be DENIED atomically and must not issue a successful Undo receipt. If SQL14 has no bound receipt/Undo route yet, leave it disabled until certified rather than violating reserved<=physicalNet.

Estado: PREAUDITADO_CONDICIONAL/NO_READY hasta B7 del antecesor, nueva guardia documental, permiso propio y preflight exacto. Este delta NO ejecuta B6/B7.

## Delta contra A2B1A

El payload Review validado contiene acción, principal, selección completa,
epoch, item/unidad, cantidad, intent, revisión y la referencia original
para RELEASE. Reality recibe esa representación tipada y usa el port de
posición del dominio para revalidar bajo el scope SQL vivo. Usar como
revisión observada max(estado de receipts, último evento14) y negar el
overflow Long. Recapturar físico menos compensaciones y reserva por item,
unidad y principal dentro del BEGIN IMMEDIATE. Rechazar intent/evento
repetido y no escribir ni emitir recibo cuando falte saldo o cambie la
admisión. Revalidar el fence también en el COMMIT de RealityStore.

Los modos existentes de recepción SQL12 y compensación SQL13 siguen cerrados
a SQL14. La capa de admisión SQL14 habilita exclusivamente su port de
reserve/release. Probar que la apertura SQL14 no puede vincular Undo de
recepción y que no quedan eventos ni recibos parciales ante rechazo. El
host B1B1 seguirá sin ofrecer N-016 hasta que se certifique el control
transaccional cruzado. Preservar las comprobaciones en el port SQL13.

En pruebas reutilizar el commit PREPARED→COMMITTED de Reality y consultar
el recibo existente después de reinicio. No agregar una tabla de recibos
ni un segundo COMMIT. Fuentes verificadas: https://sqlite.org/lang_transaction.html
y https://sqlite.org/wal.html. Se adapta el scope único de FARO, sin un
chequeo de stock fuera de la transacción como condición suficiente.
