# FARO V5 — S3.A5 post-cutover DEV integración B6 WAIT_CI
Fecha: 2026-11-08 AR. Unidad `S3_A5_POST_CUTOVER_DEV_INTEGRATION`; B1–B5 `docs/s3-night/S3_A5_POST_CUTOVER_DEV_INTEGRATION_B1_B5_20261108.md`, permiso READY previo, writer epoch598.
- Funcional commit `03f5af2ae9e1382c7a2f0ff0b9d51049868e374d`, dos rutas: `FaroV5S3A5PostCutoverIntegrationTest.kt` (5 escenarios NUEVOS de SQLite real, Chat, cuatro owners, old7 leak, corrupción8, doble cutover, ABA); workflow `faro-v5-s3-a5-post-cutover-dev-integration-directed.yml`.
- CI nueva dirigida `#42536498612`, al checkpoint QUEUED; gate exacto `tests=5 failures=0 errors=0 skipped=0`; V2 core histórico se auto-disparó `#42536498488`, no duplicar ni contar como criterio nuevo de esta unidad. Guard obligatorio por comprobar.
- B6=`WAIT_CI`, B7 pendiente. No considerar aprobada suite sin resultado terminal+JUnit XML. Si rojo: leer Kotlin/SQLite XML/log, aplicar corrección mínima con nuevo permiso RED_FIX; no relanzar suites anteriores.
- NO X01-X12 finales, NO G15, NO shipping V2. S3.A5 OPEN, S4/Auto/Noche OFF. No APIs pagas ni main/V4.4.
