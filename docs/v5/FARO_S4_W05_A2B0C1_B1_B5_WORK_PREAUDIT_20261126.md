# FARO V5 · W05-A2B0C1 · Cutover/recovery SQL13→14 DEV

Unidad: `S4_W05_A2B0C1_SQL14_EVIDENCE2_JOURNALED_CUTOVER_DEV`. Base c4d1901549005e7fdb990eb5b83aba62099eb01c. Estado **PREAUDITADO CONDICIONAL, NO_READY; requiere B7 predecesor y delta audit**. Evidencia B1–B5 de diseño, NO son resultados de tests. V2 productiva, stock real, pagos, Auto/Noche OFF. Moto G15 NOT_RUN.

## B1 — Evidencia

Entrada: A2B0C0 B7 certificado y SQL13 origen con ticket COMPLETED. FaroV5Household13Evidence2DevJournaledCutover certificó ticket/rollback SQL12→13; falta transición SQL14 con archivo autenticado FBS2v11.

## B2 — V4.4 y V5

Adaptar journal PREPARING/STAGED/VALIDATED/SWITCH_PREPARED/SWITCHED/COMPLETED y recovery. V4.4 AtomicApply y rollback. No reusar ticket SQL13 indistintamente.

Matriz V4.4: `docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md`; reutilización: `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`. No copiar legado ni repetir B7 previo.

## B3 — Soluciones externas

ADAPTAR https://www.sqlite.org/wal.html y https://www.sqlite.org/lang_transaction.html; REFERENCIA backup API https://www.sqlite.org/backup.html. DESCARTAR promover por solo tener stage o contraseña, y volver a secuencia vieja durante rollback.

Sin nuevas dependencias, servicios ni APIs pagas. Ante límites reales, retener control local y pruebas adversariales; los proyectos externos son patrones/referencia, no permisos.

## B4 — Diseño y límites

Nuevo ticket versionado FARO_SQL14_EVIDENCE2_CUTOVER_V1; exigir origen SQL13 seleccionado COMPLETED, FBS2v11 autenticado, cinco stores stage equivalentes, root lease/foreground, fence y verificación doble. Full pointer CAS exactamente una vez. Crash antes de pointer recupera origen; después completa destino; fallo salud revierte a SQL13 con secuencia monotónica. Sin permisos de stock activo productivo.

**Rutas candidatas; reconfirmar tras B7 de dependencia, sin READY anticipado:**

1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household14Evidence2DevJournaledCutover.kt`
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household14Evidence2DevJournaledCutoverTest.kt`
3. `.github/workflows/faro-v5-s4-w05-a2b0c1-journaled-cutover.yml`

No main, V4.4, ni activación de stock productivo. Toda ruta adicional obliga a rehacer solo el delta del permiso.

## B5 — Pruebas negativas propuestas

- T01 Origen SQL13 ticket REAL COMPLETED; destino SQL14 5-store readback.
- T02 Rechazar origen sin selección o v10 como si fuera v11.
- T03 Crash PREPARED y SWITCHED recupera sin duplicar.
- T04 Health fail => rollback seguro con secuencia nueva.
- T05 ABA/ciphertext distinto/lease revocado => STOP.

Exigir guardia 7 bloques documental SUCCESS, evidencia blob SHA, permiso READY individual y preflight de **cada ruta** antes de B6; código `FARO-UNIT:S4_W05_A2B0C1_SQL14_EVIDENCE2_JOURNALED_CUTOVER_DEV` en línea propia, CI del SHA real, XML JUnit original tests>0 y failures=errors=skipped=0, B7 independiente, permiso consumido, writer CAS RELEASED. Para sucesores hay que reconfirmar HEAD y contrato real después del predecesor. Nunca declarar B7 desde este documento.
