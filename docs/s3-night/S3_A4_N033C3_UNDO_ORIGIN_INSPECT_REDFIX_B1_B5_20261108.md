# FARO V5 — N033C3 Undo origin inspection RED_FIX · B1–B5
Fecha AR 2026-11-08. Misma unidad `S3_A4_N033C3_UNDO_ORIGIN_INSPECT`. Base funcional RED `e5b97e69c4569ed559ce89eae32f005ad4b0d7e0`. Red anterior `docs/s3-night/S3_A4_N033C3_UNDO_ORIGIN_INSPECT_B7_RED_FIX_20261108.md`.
## B1 — Evidencia de fallo y aceptación
- La ejecución #42491100997 mostró 10/12 tests PASS, fallas exactas de ambas vistas previas COMMITTED; kernel SKIPPED. El puerto `TrustedConversationApplyReviewPort.load` nunca carga COMMITTED intencionalmente. Objetivo acotado: leer envelope histórico únicamente desde nuevo puerto separado de SOLO lectura cuando Review COMMITTED exacta, no cambiar criterios del puerto Approved.
- Aceptación: con Reality8 SQLite y Workspace4 reales, 12/12 host PASS incluyendo 2 previews y 1 negativa; Kernel Apply >=13 y resultado observado (baseline 38/38), guard y otras CI exact SHA; ninguna nueva capacidad de ejecución/Undo/cancel ni cambios a V2 productiva.
## B2 — Comparación V4.4 y reuso
- V4.4 `SqliteComplexReversalPort` usa transacción compensatoria nueva, conserva movement_voids y niega repetición: REFERENCIA sin reutilizar código histórico. V5 usa `readPlanReviewInternal`, `planEnvelopeVerifier`, revisión CAS, Workspace4 y Reality8. **Mantener** `TrustedConversationApplyReviewPort.load` como puerta de aprobación, no ampliar a COMMITTED. Usar tipo sellado diferente para dato inerte.
- Conserva pruebas 9/9 previas; 3 test nuevos del SHA e5b sin duplicarlos.
## B3 — Investigación alternativas
- Martin Fowler CQRS https://martinfowler.com/bliki/CQRS.html: separar proyecciones de lectura de comandos, REFERENCIA (sin implantar motor CQRS); contrato read-only distinto reduce riesgo de otorgar permisos por accidente.
- OWASP https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html: separar verificación de objeto de autorización de comando, deny-by-default; ADAPTAR.
- SQLite https://www.sqlite.org/transactional.html y https://www.sqlite.org/wal.html: lectura del envelope en Workspace y lectura Reality8 son snapshots separados, no un commit multi-DB; ADAPTAR con doble fence y revalidación futura en Undo SQL.
- Alternativas: relajar `load` para COMMITTED DESCARTAR (mezcla autoridad), query raw SQL desde host DESCARTAR (bypass Workspace), duplicar envelope en Reality DESCARTAR (nuevo estado no canónico), reusar original receipt como autorización DESCARTAR.
## B4 — Unificación, delta mínimo, tests
- `v5/kernel/contracts/src/main/kotlin/ar/com/faro/v5/kernel/contracts/ConversationReviewPort.kt`: nuevo objeto de bytes defensivos `TrustedCommittedConversationPlan` y `TrustedCommittedConversationPlanReadPort.loadCommitted(context)`, interfaz exclusiva de lectura sin `advance`.
- `v5/workspace/src/main/kotlin/ar/com/faro/v5/workspace/ConversationWorkspaceStore.kt`: implementar nuevo puerto con exact context, COMMITTED, envelope verificado, bytes copiados y control de mutex. Dejar `TrustedConversationApplyReviewPort.load` **sin modificaciones semánticas**.
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5Planning8ReviewedApplyHost.kt`: inyectar puerto committed separado por constructor host dormido y cambiar su método `inspectCommittedUndoOriginForTrustedHost` de `workspace.load` a `committedPlans.loadCommitted`.
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5Planning8ReviewedApplyHostT2Test.kt`: pasar `committedPlans = workspace` SOLO en host JVM, asegurar en positivos `workspace.load(committed.context())==null` y `workspace.loadCommitted(...)` aceptado, mantener 12 tests; NO reejecutar pruebas certificadas fuera del workflow.
- `v5/app/src/main/kotlin...` no se monta en production. Workflow dirigido ya exige 12/12, SIN cambio a workflow. Cuatro paths EXACTOS; commit funcional `FARO-UNIT:S3_A4_N033C3_UNDO_ORIGIN_INSPECT` por RED_FIX. Guard exacto, CI automática, B7 posterior.
## B5 — Auditoría previa adversarial
- Un `APPROVED` no pasa nuevo puerto committed y un `COMMITTED` no pasa puerto approved. Ambos validan `context` por igualdad, el plan fingerprint, SHA256 y canonical verifier; un caller de Chat no obtiene el puerto histórico.
- Preview en sí no concede permiso ni escritura, no altera SQL/receipts/owner revision; aplica deny en missing fingerprint, tx o mismatches. No Workspace reads dentro de Reality SQL lock.
- Parent code RED e5b exige documento B7 RED_FIX anterior y permiso READY de **misma unidad**, no otra. Si CI roja no seguir a Undo ejecución; writer global CAS y release durante CI.
- **READY_SCOPED_RED_FIX** solo cuatro archivos B4, no autoriza N033C3 cancel ni cierre A4.
