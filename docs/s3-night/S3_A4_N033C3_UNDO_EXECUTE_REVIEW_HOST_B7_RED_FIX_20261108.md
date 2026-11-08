# FARO V5 — N033C3 Undo NEW Review host B7 RED_FIX
Fecha AR 2026-11-08. Unidad `S3_A4_N033C3_UNDO_EXECUTE_REVIEW_HOST`.
## B7 — Auditoría independiente RED_FIX
- Código inicial `0fec033d21ec167926d29f1e00fa888bc1b07f60` publicado bajo B1–B5 `docs/s3-night/S3_A4_N033C3_UNDO_EXECUTE_REVIEW_HOST_B1_B5_20261108.md`, permiso cuatro paths, guard `#42498342049` SUCCESS.
- CI REAL SQLite new reviewed Undo host `#42498342148`, job `#128785456305`, **FAILURE** en `:app:compileDebugUnitTestKotlin`, antes de ejecutar JUnit: `UNRESOLVED_IMPORT` en `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5Planning8UndoReviewedApplyHostT2Test.kt:15`, `SingleProcessMaterialWriteGate` incorrectamente importado desde `ar.com.faro.v5.kernel.apply`.
- Fuente probada de la clase: `v5/kernel/contracts/src/main/kotlin/ar/com/faro/v5/kernel/contracts/MaterialWriteGate.kt`, package `ar.com.faro.v5.kernel.contracts`; el test YA tiene `import ar.com.faro.v5.kernel.contracts.*`. Cambio mínimo necesario: **eliminar el import del paquete erróneo**. No requiere cambiar host, gate, Kotlin API, Reality8, codec ni SQL.
- No existe XML 6/6 del código 0fec, ni B7 PASS. Las otras CI se están ejecutando o terminarán según su propia conclusión. Los 4 B7 anteriores permanecen certificados por su SHA propio y no se repiten manualmente.
- Disposición **RED_FIX_SAME_UNIT**; bloquear nueva unidad hasta corregir import y volver a verificar host 6/6, 12/12 existente, kernel38/38, guard y regresiones aplicables. No justificar habilitación de Undo desde UI o S3.A4 CLOSED.
