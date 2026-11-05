# FARO V5 — S3.A1 / N015 schema7 existing generation 17-test CI · B7 independiente, 05/11/2026

## B7 AUDITORÍA FINAL INDEPENDIENTE

**Dictamen: PASS_SCOPED (CI coverage, no source data modification)**. Unidad `S3_A1_N015_SCHEMA7_GENERATION_XML_CI`, fuente funcional SHA exacta `088c0e51b478d726bfd884493d5f68173ce0250d`. No equivale a S3.A1 full product, recuperación física o validación completa de toda generación histórica.

### Evidencia B1–B6
Preauditoría B1–B5 `docs/s3-night/S3_A1_N015_SCHEMA7_GENERATION_XML_B1_B5_20261105.md`, blob `ee41fcaffe4ffcdb8d8543e404e10753756bfb81`. Permiso previo `1e7acf88e13de0f9ae181619afe4233c93db81e4` READY, ruta única `.github/workflows/faro-v5-v2-core-directed.yml`; cambio funcional agregado `FARO-UNIT:S3_A1_N015_SCHEMA7_GENERATION_XML_CI` sin tocar SQLite, propietarios, tablas, UI ni el catálogo 172.

CI para SHA `088c0e51`: [GitHub Actions #42115722898](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42115722898) **SUCCESS**. Logs del job `v2-core-isolation` demuestran `S3_A1_SCHEMA7_GENERATION_XML_PASS 17/17`, XML exacto `FaroV5GenerationStoreGraphV2T2Test`: tests 17, failures=0, errors=0, skipped=0, incluyendo `exactRealityPreflightAllowsOnlyFiveSixSeven` y `existingSchemaSixRealityIsAdmittedAndUpgradedToSeven`. Mismo run: `V2_CORE_DEV_SEARCH_SESSION_XML_PASS 10/10`, `G5_REAL_HOST_FOUR_OWNER_XML_PASS 1/1`, `M2_DEBUG_T4_RELOCATED_XML_PASS 7/7`, `V2_CORE_M2B_DEV_APP_ISOLATED_PASS 4/4`, `V2_CORE_M1_ISOLATION_PASS 4/4`.
Guard siete bloques [#42115723125](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42115723125) **SUCCESS** para el código SHA exacto. El checkpoint documental posterior `554cab80d4622bdbd0fa1800ff851389394c2361` dio [#42115856391](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42115856391) FAILURE solo de preflight `previous code change has no B7 disposition` mientras esperaba esta disposición. Este B7 junto a la actualización separada del permiso lo corrigen; no reetiquetar el run fallido como éxito.

### Alcance preciso y límites
- **PASS verificado por fixtures:** aceptación de perfiles explícitos 5/6/7, rechazo 4/8, preflight/symlink/WAL/fallo de integridad, migración materializada 6→7 y nueva columna de recibo; V2 DEV nueva abre cinco stores sin fallos; no se cambió versión/producto ni fuente de verdad.
- **No certificado por este test:** restaurar *todos* los datasets reales previos de esquema 5 (solo el perfil de tablas es verificado), migración física del G15, multihost o V2 productiva. Mantener backup y restauración S2 existentes; no afirmar efectos en datos de usuario.
- **S3.A1 diferente:** implementación actual de Inicio UI, cuatro indicadores, respaldo N032 Avisos real; N015 `history.query` multi-owner, N031 planificación y A3 bookmarks continúan como dependencias de funciones respectivas, no pueden contarse como datos completos de la UI.

**Acción:** `last_code_review.sha=088c0e51...`, `result=PASS`, B7 PASS_SCOPED, permiso funcional de nuevo BLOCKED. Writer liberable después del checkpoint. El cierre de A1 debe evaluarse por los criterios de **S3-A1 del backlog original**, con límites explicitados y sin adelantar A3/A4/A5.
