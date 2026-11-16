# FARO V5 W01-M0C2A — Finance9 sealed data capture B1–B5
Fecha AR 2026-11-16. Unidad S4_W01_M0C2A_FINANCE9_SEALED_CAPTURE. HEAD ecf3bdbe821e36f54389d9458c3d5fec831ecdee. DOC_ONLY, B6 NO READY.
M0A B7 7/7, M0B B7 7/7, M0C1 B7 6/6: certificados DEV acotados; no volver a probar mismos suites.
## B1
M0C1 FBS2v4 codec/legacy restore fence B7 PASS scoped documentado en docs/v5/FARO_S4_W01_M0C1_FBS2V4_B7_PASS_SCOPED_20261116.md; guard final 43963866167 SUCCESS, writer RELEASED epoch990.
v5/store/generation/.../FaroV5Finance9BackupCodec.kt implementa sección Finance9 de exactamente OCHO tablas canónicas con columnas/clave PK y presupuesto4MiB, sin ACL. No existe fuente real de esas filas.
v5/store/reality/.../RealityFinance9StagedFilePort.kt escribe sólo en STAGED8→9; ya B7 M0B PASS. v5/store/reality/.../StagedSqlitePreflight.kt verifica stage sellado read-only, no WAL/SHM/symlink, versiones y FK; la generación no debe importar SQLite de producción.
FaroV5GenerationStoreGraph.openValidatedV2PlanningStage8 falla para 9; no modificar ni activar. RealityStore 7/8 y FBS2v1–v3 permanecen intactos. Para capturar FINANCE9 real desde stage9, mantener SQL exclusivamente en :store:reality, como se hizo M0B.
## B2
Reusar preflight exacto y marker del ExistingGenerationFormatReader, descriptor de FileStorageGenerationLayout, pin de FileActiveGenerationPointer con ID+sequence, global MaterialWriteQuiescencePort del host DEV. NO reabrir stage9 con RealityStore 8.
Primera parte de M0C2 captura SOLAMENTE sección Finance9 real y preserva unknown/null/moneda en tabla, no exportar archivo parcial como un respaldo completo. M0C2B deberá integrar las cinco secciones, FBS2v4 FAROBAK2 y restore stage9 sin ACL, con pruebas propias.
## B3
Elegido: lectura SQLite immutable=1 read-only con SQLITE_OPEN_NOFOLLOW y perfil exacto de tablas/columnas, bajo lease sellada y pin pointer. Listar whitelist ocho tablas y convertir INT64 mediante Long.toString, NULL como NULL y texto UTF8; no SQL dinámico de entradas de usuario.
Rechazados: SELECT * sobre tablas arbitrarias, exportar .db raw, adjuntar SQLite WAL caliente, capturar ACL/grants, usar RealityStore normal 7/8 contra9, exposición a UI antes de grant. Fuentes primarias https://www.sqlite.org/pragma.html, https://www.sqlite.org/lang_select.html, https://www.sqlite.org/wal.html. Reuso V4.4 solo semántico, sin código.
## B4
Cuatro rutas exactas futuro READY:
- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9SealedReadPort.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9SealedSnapshotCapture.kt
- v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9SealedCaptureTest.kt
- .github/workflows/faro-v5-s4-w01-m0c2a-finance9-capture-directed.yml
Port RealityFinance9SealedReadPort.readTrustedSealedStage9(file) devuelve RealityFinance9PortableRows(tables) con ocho perfiles de columnas SQL explícitas, PRAGMA user_version=9, integrity_check, FK check, límite 20k filas/4MiB, orden BINARY y ninguna escritura.
Owner generation FaroV5Finance9SealedSnapshotCapture captura stage no ACTIVE bajo quiescence, descriptor/marker V2/pointer full/5-store exact preflight ANTES y DESPUÉS, convierte a Finance9SectionSnapshot validado por codec M0C1, no devuelve nada ante pointer ABA. No cambia puntero ni activa.
Tests nuevos siete: fuente stage9 real y ocho secciones; presupuesto/deuda reales roundtrip; opening NULL heredado; target activo denegado; descriptor/sidecar denegados; ABA entre lectura+retorno denegado; versión ajena/columna inesperada denegada. CI dirigida :store:generation:test 7/7 XML.
## B5
No confundir lectura etapa con export usuario, ni cubrir autenticidad: FAROBAK2 etapa M0C2B. Quiescence inprocess no certifica exclusión otros procesos M1. Snapshot Finance9 no podrá restaurarse hasta capture/replay de legacy/G4/Planning/Workspace y ACL reset posterior, M0C2B.
No touch main/V4.4/S2/S3/apps G15/productive V2/Auto/Noche, W02 STOP; catálogo180 intacto. Si CI falla, RED_FIX misma unidad tras evidencia. Handoff sin falsa afirmación de M0C completado.
## B6
NOT_RUN; sólo tras guard de preauditoría y READY de las cuatro rutas en otro commit DOC_ONLY, preflight, writer CAS.
## B7
PENDING; exige CI7/7 XML, guard exacto, scope cuatro archivos, writer RELEASED y permiso consumido; M0C2B pendiente.
