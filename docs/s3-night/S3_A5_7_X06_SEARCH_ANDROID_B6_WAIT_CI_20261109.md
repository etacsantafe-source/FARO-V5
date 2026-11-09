# FARO V5 — S3.A5.7 X06 Search real Android B6 WAIT_CI
Fecha AR 2026-11-09; UNIT `S3_A5_7_X06_SEARCH_ANDROID_SCOPED`. Global A5 OPEN.

## B6 — Evidencia y estado exacto
- B1–B5 `docs/s3-night/S3_A5_7_X06_X08_FEDERATED_SEARCH_ANDROID_B1_B5_20261109.md` blob 1e40a270e110cd3fee4f50ee60bdfa317bae2c7a. Alcance exacto B4/B5 `docs/s3-night/S3_A5_7_X06_SEARCH_REAL_ANDROID_B4_B5_SCOPE_20261109.md` blob 75ee31e8cfb19ace741724d6b5623786440cddff. Permiso READY previo en padre `a388a590dc9dd36b4310532e3ba613d0b15e4616`.
- Código exacto `e857e57af3fb89c1f50edbf3605bd1c25cc31d19`, dos archivos NUEVOS test/workflow: `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57SearchComposeUiTest.kt` y `.github/workflows/faro-v5-s3-a5-7-search-compose-android-directed.yml`, NO ediciones productivas.
- Test 1: crear USER real por Compose, navegar Buscar, consulta con Workspace federado cuatro owners, hit de fuente Workspace, click con revalidación, exigir vuelta a Chat con texto persistido; no Apply.
- Test 2: consulta UUID inexistente, verificar un único resultado final explícito COMPLETE sin hits, PARTIAL sin hits o UNAVAILABLE sin hits, sin estados contradictorios. **No fuerza** rama X08 partial/owner revocado; no reclamar X08 global PASS.
- CI dirigida https://github.com/etacsantafe-source/FARO-V5/actions/runs/42625768734 status inicial IN_PROGRESS, conclusión UNVERIFIED; guard https://github.com/etacsantafe-source/FARO-V5/actions/runs/42625768539 status COMPLETED, conclusión success. La admisión B7 exige `OK (2 tests)`, `INSTRUMENTATION_CODE: -1`, guard SUCCESS exacto mismo SHA, job y prueba real Android API35.
- 44 tests previamente certificados (seis A5 33/33, ownerrev4/4, Native2/2, ComposeChat2/2, ActivityAvisos3/3) preservados sin rerun. A3 +8 Guardados NO APROBADO, catálogo172 intacto, V2 prod/S4/Auto/Noche OFF, G15 físico NOT_RUN final campaign.
## B7 pendiente
Si CI falla, inspeccionar salida exacta y documentar B7 RED_FIX, no inferir causa. Si pasa, B7 PASS_SCOPED X06 Workspace UI y X08 wording only; global A5 sigue OPEN.
