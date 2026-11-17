# FARO V5 — S4 W01 B3B1B1B1B1A SQL READ revocation owner, B7 PASS acotado
Fecha AR: 2026-11-17. Unidad `S4_W01_B3B1B1B1B1A_SQL_READ_REVOCATION_OWNER_DEV`.

## B1 — Evidencia, preauditoría y permiso
- Preauditoría B1–B5 original `docs/v5/FARO_S4_W01_B3B1B1B1B1_CROSS_PROCESS_REVOKE_LIFECYCLE_B1_B5_20261117.md`; independiente `docs/v5/FARO_S4_W01_B3B1B1B1B1A_READ_REVOKER_INDEPENDENT_B5_READY_20261117.md`, blob `ff9339c0acef8a91de2e39688468c7d629498cc8`.
- Permiso individual READY EXACT4 aprobado en `c36ad6795bf28f3f612391b2f39b997729ed93c1`, pointer PRECODE `69fd46d1a00b9395dfdbba488609d67347b7eca7`.
- Preflight oficial ejecutado en Actions sobre worktree de `HEAD^` con las cuatro rutas `FARO_PROTOCOL_PREFLIGHT_PASS S4_W01_B3B1B1B1B1A_SQL_READ_REVOCATION_OWNER_DEV 69fd46d1a00b9395dfdbba488609d67347b7eca7`. Contenedor local sin DNS GitHub no invalida el preflight remoto.

## B2 — Código exacto y no repetición
- Commit funcional único `80e46887d8b46f7030fbbe4fc9c5bc93a93cb6c9`, marcador independiente `FARO-UNIT:S4_W01_B3B1B1B1B1A_SQL_READ_REVOCATION_OWNER_DEV` y diff GitHub compare EXACTAMENTE 4 rutas:
  1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedReadPolicyRevocationPort.kt`
  2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
  3. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedReadPolicyRevocationTest.kt`
  4. `.github/workflows/faro-v5-s4-w01-b3b1b1b1b1a-read-revoke-directed.yml`
- Reutiliza `RealityFinance9Acl.revoke`, WAL y epoch existente. No tocó schema, Apply grant, receipts, FBS2v4, Compose/Manifest/UI, main/V4.4/S2/S3, W2 ni V2 productiva. No repetir suite de dos-PID Apply histórica.

## B3 — Comportamiento limitado
DEV/STAGED9: puerto READ fijo `finance.snapshot.read/finance:all` sólo para principal SHA256 hex64, generación válida y pointerSequence positiva; fábrica `RealityStore.finance9TrustedReadPolicyRevokerForDevOnly` bajo lock material y no Apply activa. `revoke` con BEGIN IMMEDIATE / COMMIT o rollback; return null en ausencia/fallo. Repetida retorna el mismo epoch revocado: es observación, **NO aviso de nueva transición**. Sin permisos humanos/biométricos ni lectura productiva.

## B4 — CI y evidencia real
- Guard siete bloques `44125813602` job `seven-block-evidence` COMPLETED SUCCESS, incluyendo preflight del checkout exacto.
- Run dirigido `44125813960`, job `134189149515`, SHA exacto `80e46887d8b46f7030fbbe4fc9c5bc93a93cb6c9`: COMPLETED SUCCESS. Preflight parent, `:store:reality:test` y verificador de XML COMPLETED SUCCESS. Log `BUILD SUCCESSFUL in 3m 22s`.
- XML verificado por el workflow: 8/8 nuevos R01–R08, 8/8 issuer B3B1B1B1A y 8/8 observation B3B1A; total **24/24 JUnit tests exactos, 0 failures/errors/skips**. El verificador comparó nombres exactos, presencia de suites y ausencia de mount shipping, y emitió:
  - `W01_B3B1B1B1B1A_DEV_SQL_READ_REVOKE_8_OF_8_PASS`
  - `W01_B3B1B1B1B1A_ISSUER_8_OBSERVATION_8_REGRESSIONS_PASS`
  - `TWO_PID_READ_SCOPE_CASE_R07_PASS_NO_ANDROID_UI_CLAIMS`.
- Artefacto XML `faro-v5-w01-b3b1b1b1b1a-read-revoke-junit` ID `14346584362` existe, ZIP 2475 bytes; no atribuir inspección de ZIP separada. En la lectura del 17/11 se observaron 17/24 workflows SHA completados SUCCESS, 7 continuaban ejecutándose; NO afirmar 24/24 global ni cierre de campañas ajenas.

## B5 — Auditoría final adversarial independiente
- PASS scope nuevo: READ fijo, absent/idempotent sin counter nuevo, foreign principal/generation/pointer y Apply no se pisan, epoch ABA, expiración, worker **dos PIDs OS reales** sobre scope READ, rollback de trigger SQL, malformados, closed y active Apply FAIL CLOSED, ningún grant/receipt/account generado.
- NO PASS: esta unidad no avisa a la UI de cambios remotos; no garantiza que saldos visibles desaparezcan al revocar (ventana SELECT/render); no hay ContentProvider notifyChange, lifecycle STOP/RESUME, hardware BiometricPrompt G15, acreditación humana o producción. Para eso B3B1B1B1B1B y B3B2 con auditoría propia. No se certifica V2 productiva, pagos ni automatización.
- Verificación CI acotada equivale sólo a B7 de esta unidad y SHA exacto. Cualquier rojo posterior de workflows externos se trata según alcance, sin transformar B7 acotado en PASS global.

## B6
IMPLEMENTED `80e46887d8b46f7030fbbe4fc9c5bc93a93cb6c9`, preflight oficial + 24 tests XML + CI dirigida SUCCESS; writer RELEASED epoch1113 antes de CI. Conector GitHub validado como alternativa al DNS defectuoso del contenedor; detalle `docs/v5/FARO_GITHUB_CONNECTOR_DNS_FALLBACK_AND_W01_B6_CI_20261117.md`.

## B7
**PASS_SCOPED_DEV_SQL_READ_REVOCATION_8_NEW_16_AFFECTED_TWO_PID_DIRECTED_CI**. Consumir el permiso READY a BLOCKED en documento de control aparte, registrar last_code_review exacto, checkpoint final y writer RELEASED. W1 OPEN, W2 STOP, V2 productiva OFF. S3 DEV 5/5 cerrado según pointer anterior, H07 user exception no debe contarse como PASS.
