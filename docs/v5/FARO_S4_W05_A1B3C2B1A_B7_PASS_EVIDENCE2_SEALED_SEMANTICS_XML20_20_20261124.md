# FARO V5 — W05-A1B3C2B1A · B7 PASS scoped · sealed Evidence2 semantic readback XML20/20

Unidad `S4_W05_A1B3C2B1A_EVIDENCE2_SEALED_TYPED_SEMANTIC_READBACK_DEV`. Rama `work/s3-night-20261102`. Fecha local de trabajo: 2026-11-24/25 (Argentina). Solo DEV STAGED READ_ONLY; ningún cutover activo.

## B1–B5 certificados
Fuente B1–B5 `docs/v5/FARO_S4_W05_A1B3C2B1A_EVIDENCE2_SEALED_TYPED_SEMANTIC_READBACK_B1_B5_20261124.md`, blob `ebf7a0775cf75414116db5ecf488cc3bff58ecf6`, commit `2d2b927bf479106654b80d433174cc43a6de26b6`, guardia `45519116263` COMPLETED SUCCESS. Permiso exact4 `8afbd12ced2488ae15e3604326542016ff719093`, guard `45519272702` SUCCESS. C2A y C2B0 ya tienen B7 PASS, sin repetición.

## B6 — implementación exact4
Código `5c080ef54d0dda5dedb005c8ef1397daaa293dfa` con padre READY exact4:
1. `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/SealedCommercialEvidence2SnapshotReader.kt` — lectura de archivo STAGED schema2 en SQLite `mode=ro&immutable=1`, NOFOLLOW, sello WAL sin sidecars, nlink1, sin triggers, integrity+FK; LEFT JOIN de registros y términos. Reconstruye `CommercialEvidenceStagedTypedBackupSnapshot`, respeta términos ABSENT (null), UNKNOWN explícito y KNOWN con fingerprint verificable.
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11Evidence2SemanticPreflight.kt` — invoca preflight C2B0 Evidence2 dentro de la misma quiescencia, autentica el FBS2v8 original, compara la totalidad del snapshot comercial typed de la etapa con el archivo, revalida tras adversarial seam sin mutación material.
3. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11Evidence2SemanticPreflightTest.kt` — seis casos, incluyendo alteración de términos con fingerprint recalculado válido, UNKNOWN vs ABSENT, fingerprint roto, nonce/passphrase, host revocado y pointer ABA.
4. `.github/workflows/faro-v5-s4-w05-a1b3c2b1a-semantic-readback.yml` — preflight exact4 contra padre READY, Java21, SDK37, tres suites y verificación de XML JUnit original.
Writer CAS código adquirido y liberado, epoch 1918. No se abrieron owners productivos ni se alteraron C1A/B/C ni C2A/B0.

## B7 — evidencia CI/guard verificada
- Guardia código `45519525935`: COMPLETED **SUCCESS**, SHA exacto `5c080ef54d0dda5dedb005c8ef1397daaa293dfa`.
- CI dirigida `45519526104`, job `138887563839`, intento1, **COMPLETED SUCCESS**. Preflight parent READY **4/4 PASS**, Gradle `BUILD SUCCESSFUL in 4m 14s`.
- XML JUnit original: C2B1A **6/6**; regresión C2B0 **6/6**; C0 Evidence1 **8/8**; total **20/20**, `failures=0,errors=0,skipped=0`, marca real de log `FARO_W05_C2B1A_SEALED_TYPED_SEMANTIC_XML_PASS_20_20`.
- Artefacto GitHub original ZIP id `14978859436`, `w05-a1b3c2b1a-sealed-evidence2-semantic-xml`, 2378 bytes; digest publicado `sha256:532b3b77881d685d4c3b4678e856d434343281301e11088ecc2e8adfd0ffdc96`. No se afirma verificación independiente del digest descargando ZIP; se verificó el parser JUnit dentro del job.
- Ninguna suite nueva registra FAIL; no se atribuye CI global verde a otras rutas no revisadas.

## Disposición y siguiente unidad
**B7 PASS SCOPED W05-A1B3C2B1A Evidence2 semantic READ_ONLY XML20/20**. Permiso consumido. La igualdad completa de términos comerciales tipados ya se verificó únicamente en DEV STAGED, sin ticket durable, journal, pointer switch, recuperación ACTIVE, stock Apply ni pagos.
Siguiente: **C2B1B** autorización PRE-CUTOVER durable Evidence2 con ticket versionado/confirmación foreground y recovery read-only (nueva B1–B5, READY y tests). Luego C2C switch/reopen/rollback Evidence2 en DEV, y cierre posterior A1C. No reutilizar v1 Evidence1 ticket de C1A ni afirmar compatibilidad.
**A1 GLOBAL OPEN. Stock real, pagos, V2 productiva, Auto/Noche OFF.** No hubo prueba física Moto G15.
