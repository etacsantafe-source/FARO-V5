# FARO V5 — G4 mandatory V2 Core runtime coverage after Reality v6 · B1–B5

**Unidad:** `S3_A2_G4_V2_CORE_CI_COVERAGE`, HEAD revisado `40ceb0c818ac76ebe0feee8f20224e1256d51809`, 05/11/2026. Scope: añadir disparadores a **un** workflow ya existente, ejecutar todos los tests de lifecycle exactos y cerrar G4 sólo si PASS.

## B1 EVIDENCIA
- El commit `05de1c6` integró cuatro owners con navegación tipada; G4 JVM 26/27 rojo por fixture y V2 Core `FaroV5V2CoreDevAuthorityTest` rojo por Reality v5 hardcoded. El RED_FIX `40ceb0c818ac76ebe0feee8f20224e1256d51809` corrigió ambas causas (código y versión): G4 27/27 CI #42016712209 SUCCESS, Reality 21/21 CI #42016712086 SUCCESS, protocol #42016712033 SUCCESS, APK previo #42015087367 SUCCESS.
- `.github/workflows/faro-v5-v2-core-directed.yml` actualmente sólo hace push para `v5/app/build.gradle.kts`, `v5/app/src/v2CoreDev/**`, `v5/app/src/main/**`, `v5/surface/**`, `v5/app/src/testV2CoreDev/**` y el propio workflow. `v5/store/generation/src/main/**` y `v5/store/reality/src/main/**` NO disparan esos 10 tests automáticamente.
- No hay ejecución V2CoreDev runtime contra el HEAD corregido `40ceb0c818ac76ebe0feee8f20224e1256d51809`. El CI de federación compila `:app:compileV2CoreDevKotlin`, pero esto NO comprueba apertura tras restart, pointer ABA, recovery fence ni manifest.
- Writer y permiso deben revalidarse. G4 global permanece OPEN hasta ejecución de runtime exacta.

## B2 COMPARACIÓN
- La corrección SQLite 5→6 es responsabilidad de RealityStore y GenerationStoreGraph; conservar barreras exactas y mismas pruebas de V2CoreDev, sin repetir tests ya verdes ni cambiar el código del host.
- GitHub Actions usa `on.push.paths` para evitar CI ajena; ampliar únicamente las rutas de cambio **capaces de alterar la apertura V2**, no convertirlo en workflow que se lance sobre documentación/README/main.
- GitHub ofrece `workflow_dispatch`, pero esta sesión no tiene una herramienta de disparo manual verificable; la ruta cloud es actualizar el trigger ya existente y dejar ejecución automática auditable en un commit propio. El cambio del workflow se incluye entre rutas que ya se disparan.

## B3 INVESTIGACIÓN DE ALTERNATIVAS
- GitHub Actions workflow syntax: https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax — `paths` en eventos push, glob sobre archivos mutados. ADOPTAR rutas explícitas de generacion/Reality; ningún secreto extra.
- Android migrations: https://developer.android.com/training/data-storage/room/migrating-db-versions — la verificación de migración real exige test de apertura e integridad, no basta compilar. ADOPTAR runtime tests existentes.
- Opción A ELEGIDA: ampliar filtros del mismo V2 Core workflow (sin tocar jobs, contenido de tests ni certificación) para abarcar `v5/store/generation/src/main/**` y `v5/store/reality/src/main/**`, además del propio workflow; un push lo dispara en el SHA nuevo y permite verificar los diez tests contra el runtime actualizado.
- Opción B DESCARTADA: relanzar CI FALLIDA anterior 42015087300 por botón: usa SHA antiguo, no comprueba fix. Opción C DESCARTADA: inferir PASS desde compilación. Opción D DESCARTADA: cambios falsos en código para provocar CI.
- Sin dependencias, servicios, permisos, APIs pagas ni manipulación de versiones.

## B4 UNIFICACIÓN
- Ruta EXACTA ÚNICA: `.github/workflows/faro-v5-v2-core-directed.yml`.
- Añadir dos globs a `on.push.paths`: `v5/store/generation/src/main/**` y `v5/store/reality/src/main/**`. No tocar otros filtros, tests, scripts, catálogo ni branch. No activar `workflow_dispatch`.
- Los jobs existentes deberán ejecutar `:app:processDebugManifestForPackage`, `:app:processV2CoreDevManifestForPackage`, `:app:compileV2CoreDevKotlin`, `:app:testV2CoreDevUnitTest --tests FaroV5V2CoreDevAuthorityTest` (10/10), tests T4 debug 7/7 y manifest de aislamiento. Registrar run ID, SHA, logs/XML sin duplicarlos.
- Verificar el código de G4 `40ceb0c818ac76ebe0feee8f20224e1256d51809` permanece base y la CI del workflow de reparación es PASS. El propio push del workflow ejecutará los tests, no otras tareas manuales.

## B5 AUDITORÍA ADVERSARIAL
- No modificar protección G1–G3, SQLite FBS1, datos/restore, bibliotecas, Compose, permisos o V2 productiva; un workflow verde sin XML exacto NO basta. Fallo de tests de legacy version/ABA/recovery => G4 OPEN y nueva hipótesis por RED_FIX.
- Mantener `cancel-in-progress:false`, permisos `contents: read`, misma rama `work/s3-night-20261102`; no tocar main ni V4.4. Cinco-store runtime en DEV aislado no equivale a pruebas físicas G5.
- Aceptación: [CI exacta] V2 Core 10/10 + T4 7/7 + manifests pass, G4 federation 27/27 reutilizado por SHA base y patch solo CI, Reality 21/21; guard del protocolo SUCCESS, escritor liberado y puntero actualizado.
- Dictamen `READY_BOUNDED_CI_COVERAGE_ONLY`; G4 global NOT_CLOSED antes de CI de runtime y B7 final.
