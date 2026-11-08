# FARO V5 — S3.A4 N033C3 Undo execute: canonical NEW intent · B1–B5
Fecha AR: 2026-11-08. Unidad `S3_A4_N033C3_UNDO_EXECUTE_INTENT_CODEC`. HEAD base `be2453252ab739237f02cc1e580ff0f5f253bfc7`.

## B1 — Evidencia, brecha y aceptación binaria
- Dos unidades **YA B7 PASS** y NO reabrir: N033C3 `UNDO_ORIGIN_INSPECT` code `52455e3d`, 12/12 Workspace+Reality y 38/38 Kernel; N033C3 `UNDO_OWNER_CANCEL_SQL` code `61b16c94`, 6/6 SQLite + 14/14 Actions SUCCESS. Las cancelaciones son internas y no montadas en Android/Chat.
- El catálogo D-229 (172 IDs) YA contiene `undo.execute` (N-016, REALITY_MUTATION, undo-coordinator). El kernel tiene `ConversationPlanReviewContext`, `ExecutionReceipt`, `CanonicalPayload`, PlanFingerprints, ApplyPlan. **Todavía no hay payload canónico de Undo** que fije la procedencia exacta del COMMITTED original y el destino pretendido bajo una NEW human Review.
- Esta unidad entrega SOLO objeto de intención inerte `UndoExecuteIntent` + codec `UndoExecutePayload`: referencia a source Review exacta, source Receipt exacto, source OperationId, original Create payload bytes inmutables, ID destino exacto y tiempo pretendido de cancelación. Código kernel puro, sin escritura, permisos o nuevo catalog ID. El host futuro DEBE verificar las referencias contra Workspace/Reality; la presencia del payload NO demuestra autoridad.
- Aceptación B7: JUnit JVM dirigido exacto **8/8** (copia defensiva, determinismo, roundtrip, malformaciones, origen alterado, Unicode, límites, sin filtración de tokens), SHA y guard SUCCESS. No afirmar Undo ejecutable.

## B2 — Comparación y reuso
- V4.4 `SqliteComplexReversalPort` conserva historial y niega doble void: REFERENCIA. V5 ya tiene transaction receipt y ACTIVE generation, por lo que Undo debe ser NUEVA transacción, no rollback del COMMITTED.
- Reusar tipos de `:kernel:contracts` sin dependencias Android/Workspace/owner. Patrón de codificación puro de `ReminderCreatePayload`/`MonthlyPlanItemAddPayload`: magic/version, campo UTF-8 estricto length-prefixed, copia de bytes, rechazo de trailing bytes y no-canonical encoding.
- No introducir segundo store/journal ni generador de catálogo, no cambiar el ID D-229; el codec no se instala en `Surface`.

## B3 — Investigación y elección material
- `RFC 8785` (JSON canonicalization) es referencia para fingerprint del ApplyPlan ya implementado; **no** sustituir el codec binario existente ni duplicar la serialización del plan.
- `OWASP Authorization Cheat Sheet` https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html: `deny by default` y permiso por objeto/operación; **ADAPTAR** en unidad posterior HOST, nunca hacer que el payload sea una autorización.
- `SQLite transactions` https://www.sqlite.org/lang_transaction.html: recibo y efecto futuro comparten Reality8 SQL; **REFERENCIA**, no implementar SQL desde el codec.
- Alternativas rechazadas: (a) receipt original como único target — no tiene object ID; (b) reusar original Review APPROVED — ya está COMMITTED; (c) codec en owners individualmente — duplicaría la operación `undo.execute`; (d) JSON ad hoc sin verificador de canonicidad — riesgo de distinto fingerprint por bytes ambiguos.

## B4 — Delta exacto y pruebas
1. `v5/kernel/contracts/src/main/kotlin/ar/com/faro/v5/kernel/contracts/UndoExecuteIntent.kt` (nuevo): tipo `UndoExecuteIntent` con `sourceReview`, `sourceReceipt`, `sourceOperationId`, `targetObjectId`, `originalCreatePayload` y `cancelledAtEpochMillis`. El init comprueba coherencia estructural, no autoridad; el `toString()` no divulga token ni payload.
2. `v5/kernel/contracts/src/test/kotlin/ar/com/faro/v5/kernel/contracts/UndoExecuteIntentTest.kt` (nuevo): 8 tests JVM exactos, sin tests de Android.
3. `.github/workflows/faro-v5-s3-a4-n033c3-undo-intent-codec-directed.yml` (nuevo): sólo clase 8/8 `:kernel:contracts:test`, XML strict no skip, no Android UI; guard global separado.
4. B6 sólo con permiso READY exacto de estos tres paths y writer CAS; commit funcional con `FARO-UNIT:S3_A4_N033C3_UNDO_EXECUTE_INTENT_CODEC`. B7 independiente exact SHA, luego bloquear permiso.

## B5 — Auditoría adversarial
- Validar source receipt fingerprint == source Review fingerprint, IDs y payload <= límite, UTF8 estricto, flag/version/magic, ninguna lectura de bases/descifrado, no usar `TransactionId` viejo para nuevo Apply.
- Validación de semántica ausente por diseño: TODO el comando es NO CONFIABLE hasta que `TrustedCommittedConversationPlanReadPort.loadCommitted(sourceReview)` coincida con receipt/journal Reality8, source CREATE plan exacto, object ID, role, estado actual, new human Review y catalog `undo.execute`; lectura histórica NO otorga permiso. Owner SQL debe revalidar CAS en nueva Reality8 transacción.
- Rechazar si `sourceReceipt.planFingerprint != sourceReview.planFingerprint`; no asumir que no hubo una compensación previa. Nunca tocar `main`, V4.4, shipping, G15, S4, V2 productiva, AUTO o Noche.
- Alcance **READY_SCOPED_CODEC_ONLY**: no `undo.execute` binding, no Review nuevo todavía, no S3.A4 CLOSED. Si CI falla, B7 RED_FIX misma unidad; no avanzar a host antes de corrección B7.
