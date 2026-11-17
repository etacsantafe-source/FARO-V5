# FARO V5 — W01 B3B1B1B1A · B7 PASS acotado emisor SQL READ Finance9
Fecha Argentina: 2026-11-17. Unidad S4_W01_B3B1B1B1A_FINANCE9_READ_ONLY_POLICY_ISSUER_DEV. Código EXACTO 8a4d79ca3a27e5d312345df0a1c50503749487e0. No shipping, no autentica Android real.

## B1 — Evidencia y preflight sin repetir
Base B1–B5 docs/v5/FARO_S4_W01_B3B1B1B1A_SEPARATE_READ_POLICY_ISSUER_B1_B5_20261117.md blob 22ba3a1875d7baf8ae75f66c7cfb3193a652bcb8, guard 44109546378 SUCCESS. Auditoría B5 independiente + READY exact4 docs/v5/FARO_S4_W01_B3B1B1B1A_READ_POLICY_INDEPENDENT_B5_READY_20261117.md blob bfe9c98671d5d6295ef9f4d43db39848621c2358, guard 44109988029 SUCCESS y preflight FARO_PROTOCOL_PREFLIGHT_PASS S4_W01_B3B1B1B1A_FINANCE9_READ_ONLY_POLICY_ISSUER_DEV 19400505f5c61ea5cab0e50f6e29979be256e6bb. No repetir el código y tests del mapper B3B1B1A, observed B3B1B0 ni fence B3B1B1B0 (todos B7 PASS previos).

## B2 — Alcance B6 exacto
Commit 8a4d79ca3a27e5d312345df0a1c50503749487e0, marker FARO-UNIT:S4_W01_B3B1B1B1A_FINANCE9_READ_ONLY_POLICY_ISSUER_DEV comprobado; diffs funcionales ÚNICAMENTE cuatro rutas:
- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedReadPolicyIssuerPort.kt
- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt
- v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedReadPolicyIssuerTest.kt
- .github/workflows/faro-v5-s4-w01-b3b1b1b1a-read-issuer-directed.yml
Writer RELEASED epoch1096 antes de las pruebas. Código no toca Android manifest, Activity, Chat, Compose, CompositionRoot, Surface, schema DDL, FBS2v4, V4.4/main/S2/S3, Apply/receipts/product V2.

## B3 — Comportamiento implementado
RealityStore.finace9 typo avoided: la función REAL se llama finance9TrustedReadPolicyIssuerForDevOnly y está limitada a STAGED9 con owner, lock, activeTransactionId vacío y withMaterialAdmission. Emite SOLO fila SQL ACL para operationId finance.snapshot.read y objectScope finance:all con TTL máximo 30 segundos; avanza policy_epoch monotónico, no genera finance_apply_grant ni execution_receipt y no hace pagos. La request dev tiene principalHash y timestamp suministrados por caller y ES FALSIFICABLE, incluso cuando SQL resulta coherente. El record no es grant de lectura/producto ni puede autorizar saldos.

## B4 — CI real y resultado final
CI dirigida 44110607488, job 134134835286, COMPLETED SUCCESS, SHA exacto 8a4d79ca3a27e5d312345df0a1c50503749487e0; 8/8 JUnit nuevos, más 8/8 regresiones de RealityFinance9ObservedSourceReadTest y 8/8 de RealityFinance9ReadPolicyObservationTest (24 casos exactos XML). Log W01_B3B1B1B1A_FIXED_READ_SQL_POLICY_8_OF_8_PASS; W01_B3B1B1B1A_B3B1A_8_AND_B3B1B0_8_REGRESSIONS_PASS. Artefacto faro-v5-w01-b3b1b1b1a-read-issuer-junit ID 14340118142.
Guard código 44110607547 SUCCESS. **23/23 workflows exact SHA COMPLETED SUCCESS**. La última ejecución histórica 44110607195 antes IN_PROGRESS cerró COMPLETED SUCCESS job 134134834312; log W01_B3B1A_OBSERVED_ONLY_SQL_POLICY_8_OF_8, archivo JUnit artefacto 14343735015. Duró 24 minutos y 5 segundos; no fue fallo ni requirió reintento/cambio de código. GitHub Actions 0 failed/unfinished; sin repetir certificaciones.

## B5 — Auditoría de seguridad final
Sí: separación SQL READ vs write, scope fijo, TTL<=30s, epochs monotónicos, rechazo de missing/foreign/revoked/expired, esquema9 staged, excepción/cierre, cero grants/receipts, tests SQL reales, regresiones correspondientes.
No: Android UID/Keystore real, BiometricPrompt, consentimiento humano explícito, revocación interproceso notificada, lifecycle Compose ni pantalla activa. Un DTO policy READ no es una llave. SQLite WAL snapshot no garantiza que la UI siga autorizada después de una revocación posterior. B3B1B1B1B host Android y publisher siguen STOP hasta B5 propio; B3B2/G15 NOT_RUN. W1 sigue OPEN, W2 STOP, V2 productiva OFF. Preauditoría B1–B5 del siguiente host DOC_ONLY en docs/v5/FARO_S4_W01_B3B1B1B1B_TRUSTED_ANDROID_READ_HOST_B1_B5_20261117.md (commit 432e08b29c884fc7ab9f84277c4a87cc5b65268f); no READY.

## B6
IMPLEMENTED 8a4d79ca3a27e5d312345df0a1c50503749487e0 y CI 8+16 JUnit + 23/23 workflows SUCCESS.
## B7
**PASS_SCOPED_DEV_SQL_READ_POLICY_ONLY_8_NEW_16_AFFECTED_23_CI**. Consumir el permiso a BLOCKED y registrar last_code_review. Verificar guard de documento, checkpoint final y writer RELEASED antes de cerrar turno.
