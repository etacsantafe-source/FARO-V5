# FARO V5 W05-A1B3C2B1B — B0 RED_FIX exact2 Kotlin tests and workflow
2026-11-25, rama work/s3-night-20261102, unidad `S4_W05_A1B3C2B1B_EVIDENCE2_SQL11_DURABLE_PRECUTOVER_AUTH_DEV`.

## Evidencia real que activa RED_FIX
Código `07a6719b1e8c7290bd33b1e0c3d049b8e47f283d`, guardia `45521824977` SUCCESS; B1–B5 `docs/v5/FARO_S4_W05_A1B3C2B1B_EVIDENCE2_DURABLE_AUTH_B1_B5_20261125.md`, blob `eba84215d23e557518160e5d5c0b7c1ebed5d17d`, READY original `94e020ebdaa37074ad50531711604cdec3bd312c`.
CI dirigida `45521825103`, job `138895327117`, **FAILURE** en `:store:generation:compileTestKotlin`, Kotlin `UNINITIALIZED_PARAMETER` en test nuevo `FaroV5Household11Evidence2DevCutoverAuthorizationTest.kt:185`: parámetro homónimo `key: CharArray = key`. El código Kotlin principal `:store:generation:compileKotlin` pasó; **no se ejecutó ningún JUnit** y **NO existe XML real** (artefactos 0). Nunca marcar B7 PASS.
Revisión adicional de código-fuente test detectó 2 defectos preventivos en el fixture heredado: `fixture()` devuelve etapa Finance9 aún sin restaurarla a SQL11 (el original C1A sí restauraba durante scenario); un caso negativo usa `sealWithHousehold` v6 para snapshot typed Evidence2, que necesita `sealWithHouseholdEvidence2` v7 para probar degradación sin excepciones del fixture.
No hay evidencia de defecto en código funcional Evidence2, no ampliar fuente `main`.

## B1–B5 scoped RED_FIX
- B1 dato de CI+línea y estatus; fixture inspeccionado en SHA de código y API `FaroV5FiveStorePortableBackupCrypto`.
- B2 reutilizar mismo test C1A exitoso y fixture C2B1A certificado; no reescribir tests históricos ni C2A/C2B0/C2B1A.
- B3 A: corregir explícitamente default `this@FaroV5Household11Evidence2DevCutoverAuthorizationTest.key`, restaurar la etapa dentro de `fixture()` con método existente `restore` y aserción `StagedVerified`, usar `sealWithHouseholdEvidence2` para FBS2v7 negativo. Rechazar: relajar verificación de schema SQL11, quitar aserciones de crypto o modificar owner Kotlin funcional.
- B4 EXACT2 rutas y solo esas:
  1. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11Evidence2DevCutoverAuthorizationTest.kt`.
  2. `.github/workflows/faro-v5-s4-w05-a1b3c2b1b-durable-authorization.yml` (ajustar preflight de tres archivos a **dos** contra padre READY; ruta del test y del workflow ya disparan CI).
- B5 pruebas: compilar código+test, ejecutar **8/8** Evidence2 C2B1B, **8/8** Evidence1 C1A y **6/6** semántica C2B1A, parser XML total **22/22** failures/errors/skips 0, guard real SUCCESS. Si otro fallo aparece, documentar su causa y no forzar PASS.
Writer CAS released entre CI; no nuevos pagos, Android físico, stock Apply ni pointer switch.
**B0 RED_FIX READY para B6 exact2, B7 PENDING.**
