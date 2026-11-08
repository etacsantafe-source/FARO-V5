# FARO V5 — S3.A5 DEV Reality8 journal-preparation — B7 PASS_SCOPED
Fecha 2026-11-08 AR. Unidad `S3_A5_DEV_STAGE8_JOURNAL_PREPARE`.

## B7 — Auditoría de código independiente
- B1–B5 previos `docs/s3-night/S3_A5_DEV_STAGE8_JOURNAL_PREPARE_B1_B5_20261108.md`, blob `e6490296d17940624ab5f815393933dffc80da72`; permiso `READY` en commit padre `58ee078166fae489db975f740973d6dfa4edb7df`.
- Código exacto [`8e1cdac498c8ec607bf34ee12cb1d978241a5f05`](https://github.com/etacsantafe-source/FARO-V5/commit/8e1cdac498c8ec607bf34ee12cb1d978241a5f05), trailer `FARO-UNIT:S3_A5_DEV_STAGE8_JOURNAL_PREPARE`; `compare 58ee078..8e1cdac` demuestra **únicamente tres archivos nuevos**: owner Kotlin 163 líneas, suite 136 líneas, workflow dirigido 57 líneas. No se cambió un byte del anterior StageOwner ni de coordinador, Apply/Undo, app/Activity/manifest, esquema activo, S2, S4, main ni V4.4.
- CI dirigida [#42530139661](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42530139661), job `128882298659`: COMPLETED SUCCESS en SHA exacto `8e1cdac4`, Gradle `BUILD SUCCESSFUL in 2m 49s`, JUnit XML `tests=5 failures=0 errors=0 skipped=0`; marcador de log **`S3_A5_DEV_STAGE8_JOURNAL_REAL_SQLITE_XML_PASS 5/5`**.
- Guardia B1–B7 [#42530139602](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42530139602), job `128882297164`: COMPLETED SUCCESS mismo código; `FARO_PROTOCOL_GATE_PASS 109 functional units validated`, `FARO_PROTOCOL_PREFLIGHT_PASS S3_A5_DEV_STAGE8_JOURNAL_PREPARE 58ee0781...`. Regresión V2 M1/M2 se evalúa por separado: no confundir con el alcance 5/5.

## Cinco casos SQLite reales nuevos
1. Prepara Reality8 fresca y cinco stores saludables; journal persistente PREPARING→STAGED→VALIDATED, ACTIVE7 y bytes previos intactos, **no pointer switch**.
2. Segundo prepare no reutiliza el ID ni la operación, ni permite una segunda operación concurrente: no sobrescribe.
3. Crash al crear primer SQLite deja `PREPARING` y archivos parciales en cuarentena, recuperación niega adopción.
4. Crash después de `STAGED` permite recuperación a `VALIDATED` exacto, idempotente y sin restage.
5. Cambio de puntero durante materialización se rechaza, journal no pasa de `PREPARING` y el candidato nunca se activa.
- Preflight SQLite STAGED schema8 exacto para Reality antes de abrir grafo; no auto-upgrade en recuperación. Revalidación del puntero completo con secuencia y descriptor canónico bajo `MaterialWriteQuiescencePort`. El host futuro deberá inyectar el **mismo** bloqueo global que gobierna sus cinco owners; este componente no certifica por sí mismo un Android host.

## Límites y próximos pasos
- Dictamen: **PASS_SCOPED_DEV_STAGE8_JOURNAL_REAL_SQLITE_5_OF_5**. Es la **tercera unidad A5 certificada**, no A5 completo. Las anteriores `S3_A5_ACTIVE8_CERTIFIED_HOST_OPEN` 6/6 y `S3_A5_FRESH_PLANNING8_STAGED_OWNER` 5/5 permanecen certificadas sin repetición.
- **A5 sigue OPEN**. Falta enlazar journal VALIDATED con `ValidatedGenerationActivationCoordinator` real bajo la misma barrera global y su recovery, + host V2 DEV Reality8 con revocación de sesiones previas y ACL/Pendientes, + campaña física G15 X01–X12 **solo al final**.
- V2 productiva OFF, no permisos para entregar/Apply/Undo extra, S4/Auto/Noche OFF. No uso Android/G15 ni importación/borrado de datos.
