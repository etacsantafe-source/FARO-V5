# FARO V5 — S4 W01 M0B — Stage9 generation admission B1–B5
Fecha AR: 2026-11-16. Unidad: S4_W01_M0B_FINANCE9_GENERATION_ADMISSION. Baseline: a12e282a90f38b04d3d466c2aa006dfcab7ed43c. PREAUDIT DOC_ONLY — no READY ni B6.

## B1 — Evidencia real
- M0A terminada: código 34fc3382d5c5495e81e35f7ab079f59c4acb9e08; CI 43950202913 7/7, guard 43950202231; cierre a12e282a90f38b04d3d466c2aa006dfcab7ed43c guard 43951163958. Permiso M0A consumido BLOCKED. NO repetir los siete tests.
- RealityFinance9Schema.kt vive en :store:reality y es internal. :store:generation no puede importarlo: el plan original omitió esta frontera.
- v5/store/generation/build.gradle.kts declara SQLite bundled sólo en testImplementation; no añadir driver SQL a producción de generación.
- FaroV5GenerationStoreGraph.openValidatedV2PlanningStage8 usa RealityStore(planningStage8=true) y falla para 9. NO reutilizarlo como opener 9.
- StagedSqlitePreflight.kt en :store:reality es READ_ONLY, rechaza symlink/sidecars y comprueba versión exacta, tablas, FK e integridad; reusar antes de cualquier SQL y después del cierre.
- FileStorageGenerationLayout.requireStaged, ExistingGenerationFormatReader.requireAndRead, FileActiveGenerationPointer y MaterialWriteQuiescencePort son la autoridad del stage y del pin ID+sequence. El gate de mantenimiento es INPROCESS; no certifica exclusividad interproceso.
- FaroV5WorkspaceV2StageOwner.stageFreshPlanning8AndVerify materializa cinco stores y Planning8 en una generación nueva; NO activa ni reconfigura el Android host.

## B2 — Reuso y propiedad
Se conserva el código M0A y sus siete PASS. Adaptador nuevo en Reality abre SQLite bundled en archivo EXISTENTE, exige foreign_keys ON y synchronous FULL, delega a M0A internal y cierra; por sí solo NO certifica que la ruta sea STAGED. El owner generation externo revalida descriptor/camino/marker/puntero y cinco stores, y sólo entonces llama al adaptador. Ni B6 ni B7 aquí implican cutover, backup ni migración de ACTIVE.

## B3 — Alternativas
Elegir puente de escritura acotado Reality + guard de generación separado. Rechazar hacer público el SQL interno, agregar SQLite de producción a generation, cambiar versiones de RealityStore histórico, migrar activo, crear segunda base o replicar DDL. Primarias: https://www.sqlite.org/lang_transaction.html, https://www.sqlite.org/wal.html, https://developer.android.com/topic/architecture/data-layer/offline-first. P02/P03 V4.4 y W00 sólo como referencias: ninguna app bancaria.

## B4 — Cuatro rutas exactas futuras
1. v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9StagedFilePort.kt — puente público de archivo, requiere EXISTING regular file sin symlink; abrir, foreign_keys ON, synchronous FULL, invocar installer internal M0A, cerrar; no puntero ni app.
2. v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9StagedMigration.kt — owner stage9, bajo quiescence y expectedActive completo; exige layout.requireStaged, descriptor igual, formato WORKSPACE_V2, target no activo, preflight EXACT Reality8 + otras cuatro versiones; llama puente; preflight EXACT 9 y otros cuatro; pin pointer igual al final. Sin activar ni reabrir con RealityStore de schema8.
3. v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9StagedMigrationTest.kt — 7 casos JUnit nuevos con staged real.
4. .github/workflows/faro-v5-s4-w01-m0b-finance9-generation-directed.yml — Gradle :store:generation:test suite exacta, XML7/7 y artifact.

Casos T01–T07 diseñados NOT_RUN: (1) target 8 sellado upgrade9 con puntero inmutable; (2) target ACTIVE rechaza; (3) descriptor/path ajeno rechaza; (4) schema7/extraño rechaza; (5) WAL/symlink rechaza; (6) ABA en medio cancela promoción; (7) reintento stage9 se bloquea sin mutación.

## B5 — Stop/amenazas
- No abrir ACTIVE ni SQLite writable antes de preflight exacto; no usar immutable=1 en modo escritura.
- Si la migración llegó a 9 y cambia pointer, target permanece STAGED/quarantined: no bajar ni exponer sin futuro journal/recuperación certificado.
- ID+sequence pin exacto para ABA; MatWriteGate sólo inprocess. Pruebas de revocación interproceso y cutover aún NOT_RUN (M1/M0C).
- No tocar catálogo180, Restore FBS2v1-v3, Android shipping, S2/S3, main, V4.4, apps G15 ni implementar W02.
- Cada unidad necesita permiso propio READY en commit DOC_ONLY, writer CAS y CI/B7 por SHA.

## B6
NOT_RUN — el permiso funcional continúa BLOCKED hasta nueva verificación de guardia y READY independiente para estas cuatro rutas.

## B7
NOT_RUN. Requiere 7/7 XML, guard exacto, diff cuatro rutas, writer RELEASED, permiso consumido. W01 global OPEN.
