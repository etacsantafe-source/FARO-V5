# FARO V5 · W05-A1B3C0 · FBS2v8 SQL11 cutover preflight READ_ONLY · B1–B5

Fecha 2026-11-24 Argentina. Rama `work/s3-night-20261102`; HEAD inicial verificado `bf6c56b97c0e94777ad584435cf0af6c1f4542f0`, writer RELEASED epoch1862, permiso anterior BLOCKED y B7 PASS.

## B1 — Estado y evidencia existentes

B7 A1B3B1: `docs/v5/FARO_S4_W05_A1B3B1_FBS2V8_H10_H11R_FULL_STAGE_B7_PASS_20261124.md`, blob `0b4e06563193761a13365cbd6312761e5f93c388`, SQL9→10→11 STAGED Evidence1, pin FAROBAK2 del ciphertext original, SQL11 H10/H11R y fallos de recuperación JUnit XML 10/10, CI 45445804222 SUCCESS, guard final 45446850068 SUCCESS. No repetir A1B3A/B0/B1 ni unidades previas. V2 productiva, pagos, Auto/Noche y stock real OFF.

Código real revisado:
- `FaroV5Household10DevCutoverRuntime.kt` blob `803ad8b94e4dd30df6b0776b4c9f2a47e3563a4f`: exclusivo root lock, ticket durable/journaled, activación/recovery, **solo target SQL10**.
- `FaroV5Household10DevCutoverOwner.kt` blob `edd6835ad47a6950df946352d8d50c87776fafdf`: reutiliza `ValidatedGenerationActivationCoordinator`, fence, rollback y `fingerprint` exacto de cinco stores, pero exige schema10 y reopen SQL10.
- `FaroV5Household10DevCutoverPreflight.kt` blob `93e8d08ac2ebf9fcb1fda7e41504736710448b1d`: journal VALIDATED, preflight de SQL10. No servir para Reality11.
- `FileBootstrapJournal.kt` (maintenance): `read(op)`, `pending()`, compare-and-set durable; `FileGenerationReplayFence` con `isInstalled(RESTORE/MIGRATION,target)`.
- `FileRestoreArchiveIdentity.kt`: verificación inmutable del ciphertext original ligando operationId+target; **no generar pin de cero al inspeccionar**.
- `RealityHousehold11NeedsRowsPort.readTrustedSealedStageEleven`, `RealityHousehold11ReceiptRowsPort.readTrustedSealedStageEleven` validados por SQLite read-only/immutable, STRICT y check integrity/fk; `FaroV5FiveStorePortableBackupCrypto.openWithHouseholdReceipts11` autentica v8; `FaroV5Household10DevCutoverOwner.fingerprint` digiere cinco stores.

Problema: el cutover existente de SQL10 no es un cutover SQL11; usarlo volvería a exigir schema10 al recuperar/abrir y podría dejar puntero seleccionado sin runtime compatible. No aplicar cutover hasta implementar salud/reopen SQL11.

## B2 — Reuso

La nueva prevalidación solo lectura reutiliza el root+active pointer exacto, descriptor canónico, cinco stores single-link/sin sidecars/formato WAL, identidad de archivo pin existente, journal pending y replay fence. Compara H10 y H11R leídos realmente de SQL11 con **FBS2v8 auténtico**. Registra fingerprints de origen/destino para permitir después un ticket duradero específico y detectar drift, sin crear ese ticket. La fase C0 **no tiene API que escriba puntero o journal**.

## B3 — Investigación/alternativas

SQLite WAL: https://www.sqlite.org/wal.html; transacciones: https://www.sqlite.org/lang_transaction.html; integridad/FK: https://sqlite.org/pragma.html. Opciones:
A, **elegida**: C0 preflight de solo lectura con contrato de checksum de origen y target SQL11, sin autoridad de activación; C1 implementará journaled cutover/reopen SQL11 por código aparte con rollback/fault injection.
B descartada: usar `FaroV5Household10DevCutoverRuntime` con un SQL11 y saltar validadores; rompe fail-close y replay.
C descartada: activar antes de conservar ticket durable y verificadores de runtime, expone crash no recuperable.

## B4 — Rutas exactas y entrega

Tres archivos **nuevos**:
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11DevCutoverPreflight.kt`: `inspect` bajo quiescence + exclusive closed dev host, autentica FBS2v8 Evidence1, valida pin existente, puntero completo, origin Finance9/Auxiliary4, target SQL11 H10/H11R exactos, fingerprint estable cinco stores y ausencia de journal/fence/ticket conflictivos, revalida después de seam adversarial, devuelve inspección descriptiva NO capability. No writes.
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11DevCutoverPreflightTest.kt`: ocho pruebas JVM dirigidas de aceptaciones/rechazos, pin, host, ABA, journal/fences y cambio de contenido.
3. `.github/workflows/faro-v5-s4-w05-a1b3c0-cutover-preflight.yml`: preflight parent READY exact3, Java21, SDK37, Gradle test, XML original 8/8 artifact.

## B5 — Auditoría/seguridad y límites

T01: cinco stores SQL11 restaurados del FBS2v8, H10 y H11R no vacíos; activo sigue en generación origen; inspección retorna hashes exactos.
T02: H10/H11R revisiones positivas con cero filas, sin stock inventado.
T03: archivo distinto (ciphertext nuevo mismo snapshot), incorrect pass, tag alterado, versión FBS2v6 -> rechazo; pin durable intacto.
T04: hardlink o sidecar o symlink -> rechazar sin journal ni pointer changes.
T05: H10 o H11R cambiados después de B1 -> inspección rechaza aunque snapshot sea autenticado.
T06: pointer ABA / host revocado en seam -> rechazar después de lectura y antes de devolver inspección.
T07: journal no resuelto o replay fence ya instalado -> rechazo, **sin** intentar recuperación automática.
T08: stage Reality10 o Evidence2 -> fail-closed; no reclasificar el resultado como soporte SQL11 productivo.

C0 B7 scoped read-only NO permite cutover, reopen, crash recovery ni rollback: futuro C1/C2 con guard/READY/CI propios. No modificar GRAPH ni owner SQL10 certificado, main intacta. V2 productiva/Auto/Noche/pagos/real stock OFF.
