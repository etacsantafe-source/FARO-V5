# FARO V5 — Undo NEW Review host regresión dirigida B1–B5
Fecha AR 2026-11-08. Misma unidad `S3_A4_N033C3_UNDO_EXECUTE_REVIEW_HOST`. Baseline funcional `ac9f00f56eb8b280fd1a4e321a32fafce7b26f03`. Evidencia `docs/s3-night/S3_A4_N033C3_UNDO_EXECUTE_REVIEW_HOST_B7_REDFIX_REGRESSION_20261108.md`.
## B1 — Hallazgo/aceptación
- JUnit 6/6 NEW Undo host verde exact SHA, pero B7 carece regresión de clase histórica host12 y kernel38 en ese SHA. Aceptación única: nuevo commit workflow-only que ejecute 6/6 + 12/12 host juntos y 38/38 kernel, XML exacto y 0 skipped/error/failure.
## B2 — Reuso y comparación
- Reusar la clase histórica `FaroV5Planning8ReviewedApplyHostT2Test` (12 tests) y la suite de `:kernel:apply:test` (38 tests). V4.4 no aplica a selección CI; no volver a ejecutar tests de módulos no modificados de forma manual. No duplicar workflow host antiguo. Editar solo el nuevo workflow generado.
## B3 — Investigación y elección
- Gradle `--tests` filters múltiples en una única task: incluir ambas clases en el MISMO invocation para no perder el XML del primer filtro. Para kernel usar `:kernel:apply:test` en un paso separado, evitando que filtros exclusivos de app bloqueen tests kernel. Leer XML después de ambos pasos, fail closed si faltan o skipped.
- DESCARTAR rerun del antiguo run rojo `0fec` (inmutable SHA con import incorrecto), copiar XML histórico de otro SHA o sumar CI sin trigger.
## B4 — Delta exacto
- Único path funcional `.github/workflows/faro-v5-s3-a4-n033c3-undo-execute-review-host-directed.yml`. Mantener job y SDK prep; en test app ejecutar `:app:testDebugUnitTest --tests <UndoHostT2Test> --tests <Planning8ReviewedApplyHostT2Test>`; agregar nuevo paso `:kernel:apply:test`; verificador XML exija exactamente 6/6, 12/12 y kernel38/38 sin skipped, no datos inventados.
- Commit `FARO-UNIT:S3_A4_N033C3_UNDO_EXECUTE_REVIEW_HOST` misma unidad RED_FIX, con writer global CAS y permiso exacto; CI y guard B7 después.
## B5 — Auditoría adversarial
- Workflow-only no cambia ejecutores, owners, Workspace ni Android. No asumir valor de kernel38 por haber aprobado en un SHA anterior; exigir XML nuevo. No relajar criterios si CI roja, no cancelar otra Actions mientras escribe; si aparece regresión real tras tests nuevos, analizar causa antes de corregir.
- V2 productiva/S4/FARO Auto/Noche OFF; usuario físico G15 solo A5, A4 no CLOSED hasta N015/N031 y e2e. **READY_SCOPED_CI_RED_FIX_ONLY**.
