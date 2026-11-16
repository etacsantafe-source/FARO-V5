# FARO V5 — W01-M0A · B7 PASS acotado: Finance9 staged schema
**Fecha AR:** 2026-11-16. **Unidad:** `S4_W01_M0A_FINANCE9_STAGED_SCHEMA`. **Código SHA exacto:** `34fc3382d5c5495e81e35f7ab079f59c4acb9e08`. **Cierre:** `B7_PASS_SCOPED_JVM_SQL_STAGE_ONLY`.
No confundir PASS de esquema con READY de Finance Apply, schema9 activo, Backup9, credencial Android, revocación entre procesos, operaciones F02–F06, producción o G15.

## B1 — Evidencia y diffs
Código en exactamente tres rutas:
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9Schema.kt` (nuevo installer `internal`, esquema sólo sobre `SQLiteConnection` de un stage8; NO abre archivos ni modifica puntero).
- `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9MigrationTest.kt` (7 tests nuevos JVM, fuentes temporales).
- `.github/workflows/faro-v5-s4-w01-m0a-finance9-directed.yml` (CI dirigida y XML exacto).
`AGENTS.md`, `FARO_PROTOCOL_WORK_PERMIT.json` en parent `de933df78a26a2d1441d9aa3302bbf191856ff00`: READY tres rutas y cinco evidencias B1–B5 blob `2abfa5215c57266f1a60438af426e4ccb497ca48`. El code commit tiene `FARO-UNIT:S4_W01_M0A_FINANCE9_STAGED_SCHEMA`. Sin mezcla de permiso/código en mismo commit.

## B2 — Reutilización y regresiones
`RealityStore` 7/Planning8 intacto, G4 y FBS2v1/v2/v3 intactos, `FinanceOperations.kt` y 180 contratos intactos. El helper empieza desde una conexión preparada por caller: M0B debe demostrar el fence de generación/archivo aislado antes de invocarlo. Ninguna nueva cuenta/saldo ni operación real se expone.

## B3 — Investigación y alternativa elegida
SQLite bundled real, FK ON, `BEGIN IMMEDIATE`, CREATE TABLE/CHECK/UNIQUE/índices, `PRAGMA user_version=9` dentro del mismo COMMIT, rollback ante fault injection. Fuentes `https://www.sqlite.org/lang_transaction.html`, `https://www.sqlite.org/foreignkeys.html`. No banco/API paga, ni dos DB, ni ALTER de ACTIVE. V4.4 sigue únicamente como referencia semántica y UX.

## B4 — Ejecutado y verificado por GitHub Actions
- Run dirigido **43950202913** en SHA `34fc3382d5c5495e81e35f7ab079f59c4acb9e08`: job **133590355809** `completed/success`.
- Log del job: `BUILD SUCCESSFUL in 2m 27s`, `S4_W01_M0A_FINANCE9_STAGED_SCHEMA_PASS 7/7` y mensaje `NOT_CERTIFIED: generation admission, cross-process ACL, backup9, Android, G15, shipping`.
- XML dirigido verificado por workflow: conteos exactos **(tests=7,failures=0,errors=0,skipped=0)** y nombres siete testcases concordantes. Artefacto `faro-v5-w01-m0a-finance9-junit`, id **14267971393** (presente, no caducado).
- Guardia siete bloques en SHA exacto **43950202231** `completed/success`.
- Inspección diff del commit reporta **las tres rutas y sólo las tres rutas**.
Resultado: **M0A B6/B7 PASS únicamente stage-only SQL**; no se ejecutan pruebas físicas.

## B5 — Verificaciones negativas y límites
Los siete casos corrieron de verdad: fixture7 independiente→stage8→9 conservando otra fuente7; 8→9 conserva tablas Planning; opening LEGACY_UNVERIFIED/NULL; DDL fault rollback íntegro incluyendo user_version; FK/trigger rechaza efectos huérfanos; state epoch persiste al reabrir; versión inesperada o tabla faltante DENY. Ninguno demuestra migración de generación sellada real, backup9, serialización de revocación, resistencia a corte de luz, host Android o recibo financiero real. La prueba de `finance_acl_state` no demuestra `revoke/regrant` transaccional: M1 pendiente.

## B6 — Disposición
Implementación acotada terminada, permiso M0A **CONSUMED / BLOCKED**. La próxima unidad M0B tiene obligación B1–B5, nuevo READY exact-path, preflight, writer CAS, CI propia y B7. No re-ejecutar 7/7 M0A salvo cambio de su fuente bajo delta justificado. W01 global continúa OPEN y W02 STOP.

## B7 — Condición de cierre
Este B7 se considera confirmado únicamente al verificar después de publicarlo: nueva guardia docs por SHA SUCCESS, writer `RELEASED` epoch 982 con last_checkpoint igual al HEAD, nuevo permiso BLOCKED con last_code_review `34fc3382d5c5495e81e35f7ab079f59c4acb9e08`, `main` y Android shipping sin cambios. Si falla se considera B7 pendiente, no PASS.
