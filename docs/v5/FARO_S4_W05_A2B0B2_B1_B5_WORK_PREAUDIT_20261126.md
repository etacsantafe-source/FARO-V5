# FARO V5 · W05-A2B0B2 · SQL14 rows/capture/restore STAGED

Unidad: `S4_W05_A2B0B2_SQL14_SEALED_RESERVATION_ROWS_DEV`. Base c4d1901549005e7fdb990eb5b83aba62099eb01c. Estado **PREAUDITADO CONDICIONAL, NO_READY; requiere B7 predecesor y delta audit**. Evidencia B1–B5 de diseño, NO son resultados de tests. V2 productiva, stock real, pagos, Auto/Noche OFF. Moto G15 NOT_RUN.

## B1 — Evidencia

Entrada: A2B0B1 B7 certificado. SQL14 ya contiene ledger STRICT append-only, pero capture/restore de RealityHousehold13CompensationRowsPort exige user_version=13; falta capturar y restituir filas 14 con histórico13 íntegro.

## B2 — V4.4 y V5

Reusar `RealityHousehold13CompensationRowsPort`, stage sellado NOFOLLOW, WAL FULL y transacciones. V4.4 restauración idempotente y sin saldos falsos.

Matriz V4.4: `docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md`; reutilización: `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`. No copiar legado ni repetir B7 previo.

## B3 — Soluciones externas

ADAPTAR https://www.sqlite.org/lang_transaction.html, https://www.sqlite.org/backup.html y https://www.sqlite.org/pragma.html; preferir restore transaccional de stage sellado y rechazo de divergir; DESCARTAR copiar archivo ACTIVE con WAL o insertar después de COMMIT sin fence.

Sin nuevas dependencias, servicios ni APIs pagas. Ante límites reales, retener control local y pruebas adversariales; los proyectos externos son patrones/referencia, no permisos.

## B4 — Diseño y límites

`RealityHousehold14ReservationRowsPort`: captura consistente SQL14+historia13, restore en stage sin otros datos o ya idéntico, exige bytes/versión/esquema correctos, locks y callback antes de COMMIT, INSERT ordenado por revision sujeto a triggers, nunca DELETE/UPDATE ni mezcla de dos archivos; comparar imagen 5-store fuera de puerto. Ajustar lectura histórica para versión14 sin debilitar validación13.

**Rutas candidatas; reconfirmar tras B7 de dependencia, sin READY anticipado:**

1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold14ReservationRowsPort.kt`
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold13CompensationRowsPort.kt`
3. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold14ReservationRowsPortTest.kt`
4. `.github/workflows/faro-v5-s4-w05-a2b0b2-reservation-rows.yml`

No main, V4.4, ni activación de stock productivo. Toda ruta adicional obliga a rehacer solo el delta del permiso.

## B5 — Pruebas negativas propuestas

- T01 Restitución exacta y reapertura SQL14, 10/7+3/Undo7/RESERVE2/RELEASE1.
- T02 Stage ya idéntica => idempotente; divergente => denegado.
- T03 Fallo/revocación justo antes COMMIT deja cero cambios parciales.
- T04 FK/DDL/alias/symlink/sidecars/8193 registros/corrupción/UTF8 inválido => STOP.
- T05 Fuente SQL13 o ACTIVE no se reescribe; cero permisos nuevos.

Exigir guardia 7 bloques documental SUCCESS, evidencia blob SHA, permiso READY individual y preflight de **cada ruta** antes de B6; código `FARO-UNIT:S4_W05_A2B0B2_SQL14_SEALED_RESERVATION_ROWS_DEV` en línea propia, CI del SHA real, XML JUnit original tests>0 y failures=errors=skipped=0, B7 independiente, permiso consumido, writer CAS RELEASED. Para sucesores hay que reconfirmar HEAD y contrato real después del predecesor. Nunca declarar B7 desde este documento.

## Última revisión B1-B5, delta previo a Work (26/11/2026)

RealityHousehold13CompensationRowsPort.captureConnection requires version13: build a version-aware historical reader for SQL14 without weakening SQL13. Test version13/14 separately.

Estado: PREAUDITADO_CONDICIONAL/NO_READY hasta B7 del antecesor, nueva guardia documental, permiso propio y preflight exacto. Este delta NO ejecuta B6/B7.
