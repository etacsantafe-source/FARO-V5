# FARO V5 · W05-A1B0 · B7 PASS · migración Reality11 de generación sellada

Fecha: 2026-11-24 (Argentina). Unidad `S4_W05_A1B0_REALITY11_SEALED_STAGE_DEV`. Rama `work/s3-night-20261102`.
Código B6 `f5a34e1d1aebd070cf1ccba14d281bad77c3f392`; READY padre `c2419c725ecc5741d253ff914371a6f24abeacc0`.

## B1–B5 — Admisión y revisión

Preauditoría propia `docs/v5/FARO_S4_W05_A1B0_REALITY11_SEALED_GENERATION_B1_B5_20261124.md`, blob `cdc4c91865d8446a2841b9dd78c12cbd298e56fc`; guardia `45414925915` **SUCCESS**. Permiso exact4 READY en `c2419c725ecc5741d253ff914371a6f24abeacc0` y guardia `45415115337` **SUCCESS**.
Reuso del adaptador generacional Household10, StagedSqlitePreflight y schema11 ya certificado en W05-A1A. No se reabrió W04 ni se alteró Finance, main o V4.4. SQLite WAL/FULL/NOFOLLOW y generación de cinco stores se basan en componentes locales existentes, sin dependencia externa.

## B6 — Código y prueba real CI

Commit `f5a34e1d1aebd070cf1ccba14d281bad77c3f392`: exactamente cuatro rutas, sin modificar archivos certificados:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold11StagedFilePort.kt`
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11StagedMigration.kt`
3. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11StagedMigrationTest.kt`
4. `.github/workflows/faro-v5-s4-w05-a1b0-reality11-sealed-stage.yml`

Guardia código `45415512256` **SUCCESS**. CI dirigida `45415512280`, job `138520515004`, SHA exacto `f5a34e1d1aebd070cf1ccba14d281bad77c3f392`, **COMPLETED SUCCESS**. Preflight parent exact4 PASS; Gradle `BUILD SUCCESSFUL in 3m 25s`. XML original de la clase dirigida: `tests=8, failures=0, errors=0, skipped=0`; salida `FARO_W05_A1B0_SEALED_GENERATION_XML_PASS_8_8`.
Artefacto `14932248222`, `w05-a1b0-reality11-sealed-generation-xml`, 800 bytes; digest reportado `sha256:820291fe7e9bbc68d704376aff2068ca01036107f548273339684ab443b929e4`. No afirmar rehash independiente del ZIP.
Regresión dirigida G4 `45415512129` **SUCCESS**. CI V2 core `45415512155` y S3 historial `45415512153` estaban RUNNING en la lectura inmediatamente posterior al CI; su estatus final no forma parte del PASS acotado hasta verificarlo.

## B7 — Auditoría adversarial y límites

**PASS_SCOPED_W05_A1B0_REALITY11_SEALED_STAGE_XML_8_8**.

Acreditado por ocho pruebas: instalación Reality11 en copia sellada de cinco stores, Reality10 fuente y otros cuatro stores sin cambios byte a byte, puntero activo inmutable y journal vacío; receipts en blanco/revisión cero; rechazo de source ACTIVE, target forjado, stage distinto, hardlink, sidecar, symlink, Auxiliary3, versión inesperada, host lease revocado. Crash simulado y ABA después de migrar dejan target v11 en cuarentena, sin activar; no se emite un recibo de compra y no se toca Finance.

**NO cubierto**: backup/restore portable v11 (W05-A1B1/A1B2), cutover/reopen de Reality11 (W05-A1B3), receipt y Apply/CAS/Undo real (W05-A1C), reserva/consumo (A2), Cocina (A3), Android físico G15, V2 productiva. No transformar SQL11 stage en READY para operación de inventario ni usar permisos de W04/este B0 para esas etapas. La generación antigua sigue siendo la única autorizada en producción.

Decisión: consumir READY a BLOCKED; siguiente W05-A1B1, **sección de backup tipada propia de recibos Reality11**, con B1–B5 y guard, seguido de contenedor/versiones/restore y cutover independientes. Writer debe quedar RELEASED tras checkpoint.
