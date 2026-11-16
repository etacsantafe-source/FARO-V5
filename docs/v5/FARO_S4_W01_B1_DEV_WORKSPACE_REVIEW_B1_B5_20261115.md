# FARO V5 — S4 W01-B1 · creación DEV de Review PENDING en Workspace
Fecha AR 2026-11-15. Baseline 7d478433471c515b70b7836fcb7a573202eadf87. Unidad S4_W01_B1_DEV_CANONICAL_FINANCE_REVIEW_CREATION.
PREAUDIT B1–B5 exclusivamente documental, NO READY hasta guard y permiso acotado.
W01 global OPEN, V2 productiva OFF, Finance solo datos locales sin pagos externos.

## B1
W01-A B7 PASS 10/10, W01-B0 B7 PASS 12/12, último guard 43787903423
SUCCESS, writer RELEASED epoch946.
W01-B0 FinanceManualReviewedProposal prepara PreparedOperation con payload
humano confirmado independientemente, snapshot/revision y descriptor oficial.
WorkspacePlanReviewCreateRequest y ConversationWorkspaceStore.createPlanReview
persisten exclusivamente PENDING en un SQLite local, verifican canonical envelope,
Workspace authorityToken, expectedWorkspaceRevision, CAS, idempotencia, chat y
estado archived. CanonicalPreparedPlanEnvelopeVerifier valida la envoltura.
FaroV5ModelReviewBridge es un ejemplo de composición con MaterialWriteGate,
pero sirve SOLO Profile/modelo; nunca da permiso Finance. FaroV5HostCertification
V2_ACTIVE_ENABLED=false. Ningún host de gesto humano Finance está certificado.

## B2
Reusar Kernel PreparedPlan.of/ApplyPlanNode/PreparedPlanEnvelopeCodec,
CanonicalPreparedPlanEnvelopeVerifier, FinanceManualDraftPreviews,
FinanceManualReviewedProposal, ConversationWorkspaceStore y
MaterialWriteGate sin duplicar stores ni tests de W01-A/B0.
No copiar V4.4, no duplicar F1 modelo, no cambiar 180.
La Review PENDING no es Apply ni grant, jamás recibo.

## B3
A: DEV host aislado que, ante una señal de foreground humano suministrada
por un host confiable, revalida Finance/Workspace/generación antes/después,
prepara plan tipado y delega UNA creación de Review canónica en Workspace;
ELEGIDA provisionalmente con scope TEST/DEV, NO shipping.
B: UI/AI entrega PreparedOperation directo o genera APPROVED: RECHAZADA.
C: activar V2 productiva para usar la sesión F1: RECHAZADA.
D: persistir review fuera del Workspace canónico: RECHAZADA.
Fuentes técnicas: https://developer.android.com/topic/architecture/data-layer/offline-first,
https://www.sqlite.org/lang_transaction.html. El callback foreground es
solo test seam hasta enlazar el evento de Android; NO autentica un usuario.

## B4
Host W01B1 sin conectar a Activity/CompositionRoot: constructor recibe
Workspace store con canonical verifier, FinanceSnapshotReadApi, catálogo,
MaterialWriteGate, generation pin, host lifecycle callbacks.
Request ACCOUNT/ENTRY requiere payload humano independiente, apertura explícita
para cuentas, idempotencyKey estable, conversación ACTIVA, callback humano
foreground verificado por host futuro; re-deriva preview + operación B0,
verifica revisión Finance y Workspace y genera Review PENDING con fingerprint
del plan, no estado APPROVED. Replay exacto devuelve mismo review PENDING
por reviewId estable; el mismo ID con payload diferente -> DENY.
No invoca Reality Apply, no entrega receipt/lease, y no crea un host general
de operaciones arbitrarias.
Exactas 3 rutas NUEVAS candidatas:
- v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B1FinanceReviewHost.kt
- v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B1FinanceReviewHostTest.kt
- .github/workflows/faro-v5-s4-w01-b1-review-directed.yml
CI dirigida app:testDebugUnitTest --tests S4W01B1FinanceReviewHostTest
y verificador XML exactamente 7 métodos. READY individual requerido tras
esta auditoría y guard documental.

## B5
T01 foreground confirmado crea PENDING durable, 0 Apply/receipt;
T02 foreground falso DENY sin writes;
T03 generation/host epoch cambia DENY;
T04 snapshot Finance cambia DENY, sin Review;
T05 mismo intent replay no duplica;
T06 mismo intent/diferente monto DENY;
T07 Workspace revision/selección cambia DENY.
Las 7 están DISEÑADAS/NOT_RUN; no inferir éxito de S3 F1 ni W01-B0.
No autenticación humana ni fencing cross-process certificados aquí:
B2 requiere permisos reales hasta COMMIT. Scope DEV-only,
no nuevo módulo Finance productivo, ni edición de catálogo/esquema,
ni V2 productiva/Auto/Noche, S2/S3, main/V4.4, pagos externos o G15.
