# FARO V5 — S3.A5.7 X08 canonical Workspace PARTIAL real — B6 WAIT_CI
Fecha AR 2026-11-09. Unit `S3_A5_7_X08_CANONICAL_PARTIAL_ANDROID_SCOPED`. Global A5 OPEN.

## B6 — Cambio y ejecución observables
- B1–B5 `docs/s3-night/S3_A5_7_X06_X08_FEDERATED_SEARCH_ANDROID_B1_B5_20261109.md` (blob 1e40a270e110cd3fee4f50ee60bdfa317bae2c7a), B4/B5 causal `docs/s3-night/S3_A5_7_X08_CANONICAL_TRUNCATION_PARTIAL_B4_B5_20261109.md` (blob f8095eb62eb73a76fa1896d9ab75dccaaa6958d5); READY permit en padre SHA `a49a0fe32446cbe58bee2ef608fd36af9a67b1c0`.
- Código funcional exacto `f63acfe3518e3a91b1d16a2361a6c2c5db6a1d6f` con solo dos archivos NUEVOS, test de instrumentación `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57SearchPartialCanonicalUiTest.kt` y workflow `.github/workflows/faro-v5-s3-a5-7-search-partial-canonical-android-directed.yml`. Cero fuentes de app/host/runtime/contratos modificados.
- Un caso nuevo sobre Android API35 aislado: clic real Chat, UI compone USER de >5000 caracteres y token único detrás del carácter4096, envío persiste, Buscar UI consulta ese token, criterio estricto `Cobertura parcial: pueden existir más resultados.` sin `No se encontraron resultados...`, ausencia Apply.
- CI https://github.com/etacsantafe-source/FARO-V5/actions/runs/42633273852 observado inicialmente `IN_PROGRESS` conclusión UNVERIFIED, guard https://github.com/etacsantafe-source/FARO-V5/actions/runs/42633273765 `COMPLETED` conclusión success. NO declarar PASS hasta ver Android emulator stdout `OK (1 test)`, `INSTRUMENTATION_CODE: -1` y guard SUCCESS SHA idéntico.
- Se conservaron las 46 pruebas de alcances anteriores (33 seis A5, 4 ownerrevision, 2 native Android, 2 Compose Chat, 3 Activity/Avisos, 2 X06 Search) sin rerun.
- X08 global ACL revocada/owner unavailable en carrera NO certificada aunque truncación PASS; A3 Guardados X04/X05 no autorizado, 172 catálogo intacto, V2 productiva/S4/Auto/Noche OFF, G15 solo una prueba física final.
## B7 PENDING
Verificar logs CI y guard exactos, documentar B7 PASS_SCOPED o B7 RED_FIX con causa real. Sin nuevos commits funcionales antes del dictamen.
