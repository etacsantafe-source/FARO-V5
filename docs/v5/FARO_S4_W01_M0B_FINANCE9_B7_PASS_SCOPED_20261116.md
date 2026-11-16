# FARO V5 — W01 M0B — B7 PASS SCOPED staged9 generation
Fecha: 2026-11-16 Argentina. Unidad: S4_W01_M0B_FINANCE9_GENERATION_ADMISSION. Código SHA a5cd3dc3798ee5b5f5a580a6044bff63e2ad2f37. Resultado: B7_PASS_SCOPED_DEV_GENERATION_STAGE_ONLY, NO apertura activa/productiva.

## B1
HEAD de código a5cd3dc3798ee5b5f5a580a6044bff63e2ad2f37: diff exacto cuatro rutas nuevas:
- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9StagedFilePort.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9StagedMigration.kt
- v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9StagedMigrationTest.kt
- .github/workflows/faro-v5-s4-w01-m0b-finance9-generation-directed.yml
Parent permiso READY exact4 y B1–B5 con blob 5daaf7b6afebc3c66bb944a1dbf0fd906e437ed6. Commit mensaje FARO-UNIT exacto. No cambios en :app, S2, S3, V4.4, main ni catálogo180.

## B2
Reusa código M0A B7 7/7 sin reejecutarlo. Realidad/Planning histórico 7/8 conservado: el nuevo adaptador Reality abre SQLite existente READWRITE+NOFOLLOW sin CREATE y delega helper interno de Finance9; owner generation comprueba descriptor V2, cinco stores, quiescence y pointer ID+sequence antes/después, sin activar generación.

## B3
SQLite stage-only, FBS2/G4 preservados. Fuentes https://www.sqlite.org/lang_transaction.html y https://www.sqlite.org/wal.html. No hacer cutover sin posterior certificación de journal/backup9; alternativa de cambiar opener histórico rechazada.

## B4
CI GitHub Actions dirigida 43953191957, job 133600796829: completed/SUCCESS con BUILD SUCCESSFUL in 2m 29s. XML verificó 7 tests, 0 failures, 0 errors, 0 skipped y los siete nombres exactos; log S4_W01_M0B_FINANCE9_GENERATION_STAGE_PASS 7/7. Artefacto faro-v5-w01-m0b-generation-junit id 14269056865 disponible (no expirado). Guardia siete bloques 43953191939 completed/SUCCESS del mismo SHA. Diff cuatro rutas revisadas.
B6 implementación confirmada SOLO como herramienta DEV y test de stage.

## B5
Pruebas reales: stage V2 sellado 8→9 y pointer idéntico; target activo rechazado; descriptor/path foráneo rechazado; schema7/desconocido rechazado; sidecar sin checkpoint rechazado; cambio de pointer en ventana aborta, deja stage9 sin activación; segundo intento stage9 rechazado. No son pruebas de migración in-place ACTIVE, backup FBS2v4, política cross-process, corte real de energía, autenticación Android, UI, ni G15. MaterialWriteQuiescencePort sigue siendo in-process. La comprobación de sidecar no certifica TODO escenario de symlink ni de carrera entre procesos.

## B6
PASS del código M0B en alcance DEV stage. Permiso M0B CONSUMED/BLOCKED. M0C requiere auditoría, READY y B7 propios. M1 principal/grants y F02–F06 aún pendientes; W02 STOP. No repetir certificaciones ya aprobadas.

## B7
Este B7 sólo queda confirmado al verificar post-publicación: guard en HEAD de documentación SUCCESS, writer RELEASED epoch986 con last_checkpoint=HEAD y permiso BLOCKED last_code_review=a5cd3dc3798ee5b5f5a580a6044bff63e2ad2f37 PASS; si falta algo se mantiene pendiente, no afirmar cierre global W01.
