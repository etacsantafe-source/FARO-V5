# FARO V5 — S3.A4 N033C2B4 kernel transactional auth hook · B7 PASS_SCOPED
Fecha local 07/11/2026 AR. Código exacto 66d581ffded00534745f59845afbab035867fee1. Unidad S3_A4_N033C2B4_TXN_AUTH_HOOK. Este B7 NO certifica ACL Planning8 ni cierra A4.

## B7 — Auditoría final adversarial y alcance
- B1–B5 previos: docs/s3-night/S3_A4_N033C2B4_TXN_AUTH_HOOK_B1_B5_20261107.md, preaudit READY base 9830c957 y commit de documentación 67c7a597. Guard de documentación #42427431816 SUCCESS.
- B6: commit 66d581ffded00534745f59845afbab035867fee1 modifica solo v5/kernel/apply/src/main/kotlin/ar/com/faro/v5/kernel/apply/ApplyTransactionExecutor.kt, v5/kernel/apply/src/test/kotlin/ar/com/faro/v5/kernel/apply/ApplyTransactionExecutorTest.kt, workflow .github/workflows/faro-v5-s3-a4-n033c2b4-txn-hook-directed.yml; mensaje FARO-UNIT coincide con permiso previo. GitHub seven-block guard run #42427565595 SUCCESS exact codeSHA.
- CI dirigida run #42427565628, job #128569031625, conclusion SUCCESS exact codeSHA. Logs: Gradle BUILD SUCCESSFUL in 2m58s; verificación JUnit XML explicitamente A4_TXN_HOOK_XML_PASS 13/13 (13 tests, 0 failed, 0 errors, 0 skipped). No confundir con toda la app Android.
- Tests nuevos: false antes de primer binding -> rollback 0 commit/receipt; policy revoke durante segundo binding -> rechaza en recheck precommit; allow con 4 verificaciones a/b a/b -> success/receipt; excepción policy -> RecoveryRequired y rollback. Nueve pruebas históricas del mismo executor siguen ejecutadas como parte de 13/13; ninguna prueba física.
- Firma de hook opt-in no cambia llamadas históricas ni su resultado; no instala hook en ningún host ni registra operaciones Planning8. La validación se ejecuta dentro del scope del AtomicApplyTransactionPort y antes de commit; no inventa owner, principal ni permisos.
- Límite crítico: revocación tras último recheck y antes de SQL COMMIT NO queda serializada por el hook; tampoco los estados de Workspace/ACL entre procesos/restarts. La documentación de código lo advierte expresamente. No proclamar autorización de producción ni no-TOCTOU global.
- No se activó V2, S4, Auto/Noche, catálogo, G15, main, V4.4 ni refactor S5. Writer fue liberado epoch 476 antes de CI. Cola continúa STAGED. El nuevo estado de CI se valida por SHA y XML, no por estado agregado en UI.

## Dictamen / handoff
PASS_SCOPED para infraestructura kernel de autorización y rollback/recheck dentro de transacción. No hay falla de esta unidad que requiera RED_FIX; bloquear siguiente código hasta nuevo B1–B5+permiso independiente.
NEXT: S3_A4_N033C2B4_TRANSACTIONAL_PLANNING_ACL B1–B5 (fente real de Review+policy con proceso/restart y pruebas Reality8+Workspace); luego N033C3 Undo/retry y cierre S3.A4. S3 desarrollo ~40% histórico estimativo sin nueva ponderación; S3 formal 0/5; S2 8/8; S4/V2 shipping OFF; prueba Android G15 A5 NOT_RUN.
