# FARO V5 · W05-A2B0B3 · FBS2v11 autenticado, cinco stores SQL14 STAGED

Unidad: `S4_W05_A2B0B3_FBS2V11_FIVESTORE_SQL14_REPLAY_DEV`. Base c4d1901549005e7fdb990eb5b83aba62099eb01c. Estado **PREAUDITADO CONDICIONAL, NO_READY; requiere B7 predecesor y delta audit**. Evidencia B1–B5 de diseño, NO son resultados de tests. V2 productiva, stock real, pagos, Auto/Noche OFF. Moto G15 NOT_RUN.

## B1 — Evidencia

Entrada: A2B0B2 B7 certificado. FaroV5Household13V10StagedReplay restaura 5-store hasta SQL13, autentica ciphertext y valida pin. Falta integración con migración SQL14 y ledger v11; A2B0B1/2 proveerán codec y filas.

## B2 — V4.4 y V5

Reusar FileRestoreArchiveIdentity, MaterialWriteQuiescencePort, graph cinco stores, Evidence2 y fases de replay9→13. V4.4 restauración y crash recovery, no código legado.

Matriz V4.4: `docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md`; reutilización: `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`. No copiar legado ni repetir B7 previo.

## B3 — Soluciones externas

ADAPTAR SQLite Backup API https://www.sqlite.org/backup.html y WAL https://www.sqlite.org/wal.html; ADAPTAR offline-first Android https://developer.android.com/topic/architecture/data-layer/offline-first; DESCARTAR copy ACTIVE u omitir pin entre fases.

Sin nuevas dependencias, servicios ni APIs pagas. Ante límites reales, retener control local y pruebas adversariales; los proyectos externos son patrones/referencia, no permisos.

## B4 — Diseño y límites

Abrir FBS2v11 auténtico antes de tocar stage; comprobar manifest, origen != target, identidad ciphertext y full pointer. Bajo root lease y quiescence replay v10 base→SQL13, migrar **copia** SQL13→14 e insertar eventos14. Captura/relectura de Reality, Commercial Evidence2, Web Evidence, Auxiliary y Workspace/Finance. Fences antes/después de cada fase; replay mismo archive+operación idempotente; error/crash no promueve ni concede Apply.

**Rutas candidatas; reconfirmar tras B7 de dependencia, sin READY anticipado:**

1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household14V11StagedReplay.kt`
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household13V10StagedReplay.kt`
3. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
4. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold14StagedFilePort.kt`
5. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household14V11StagedReplayTest.kt`
6. `.github/workflows/faro-v5-s4-w05-a2b0b3-fivestore-replay.yml`

No main, V4.4, ni activación de stock productivo. Toda ruta adicional obliga a rehacer solo el delta del permiso.

## B5 — Pruebas negativas propuestas

- T01 5 stores iguales a respaldo autenticado, SQL14 ledger completo.
- T02 V10 import explícito mantiene eventos vacíos sin grants; v11 íntegro preserva releases.
- T03 Crash entre fases conserva stage recuperable, ACTIVE intacto.
- T04 Ciphertext/manifest/identidad/ABA/sidecar inválido denegado.
- T05 Reapertura WAL/readback exacto y límites de tamaño.

Exigir guardia 7 bloques documental SUCCESS, evidencia blob SHA, permiso READY individual y preflight de **cada ruta** antes de B6; código `FARO-UNIT:S4_W05_A2B0B3_FBS2V11_FIVESTORE_SQL14_REPLAY_DEV` en línea propia, CI del SHA real, XML JUnit original tests>0 y failures=errors=skipped=0, B7 independiente, permiso consumido, writer CAS RELEASED. Para sucesores hay que reconfirmar HEAD y contrato real después del predecesor. Nunca declarar B7 desde este documento.
