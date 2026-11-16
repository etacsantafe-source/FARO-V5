# FARO V5 — S4 W01-B2 · Finance F02/F03, autoridad Review/Apply DEV
Fecha local 2026-11-16. Baseline funcional a7c670c7af7d8c7f41877fcbc0cdeb25e0e54ad5. Solo DOC B1–B5.
W01-B1 necesita B7 documental verificado. B2 no tiene READY.
Transferencias C, compromisos D y presupuestos E autorizados de alcance,
sin permiso de código ni de migración.

## B1 — Evidencia del código del HEAD
W01-B0 genera PreparedOperation inerte, descriptor oficial
OperationContractLookupPort.current / metadata-v1. W01-B1 solo guarda
Review PENDING en Workspace, no grants ni Reality.
FaroV5ActiveReviewedApplySession.kt, blob
3c07c9b0d46b1cdb728396dd8e7067e2f94e225c, incluye Finance
ACCOUNT_CREATE y ENTRY_RECORD pero sus descriptors hardcodeados son
ContractVersion("1") y fingerprints finance-...-v1: NO IGUALAN
metadatos oficiales usados por W01-B0. Shipping port retorna
Denied(v2_not_certified). No debe activarse V2 ni ocultar discrepancia.
ReviewedConversationApplyCoordinator implementa Workspace APPROVED ->
preflight -> BeginApply CAS -> afterBeginApply -> Reality transaction ->
receipt -> ObserveCommitted; UNKNOWN requiere lookup/solo reconcileCommittedOnly.
ApplyTransactionExecutor ejecuta autorización antes/después de binding,
pero no cubre solo la carrera final de revocación cross-process hasta SQL COMMIT.
Planning8ReviewedApplyHost, ObjectGrantOwner, LocalUserRequestPolicy y
ReviewGrantLeaseBridge muestran patrón DEV con monitor y lease; son
Reminders/MonthlyPlan, NO certifican autoridad Finance. Sandbox Android
solo acredita un principal técnico, no usuario humano autenticado.

## B2 — Reutilización y aislamiento
Reusar FinanceAccountCreateBinding/FinanceEntryRecordBinding, RealityStore
con RealityTransactionScope y receipts, PreparedPlanEnvelopeCodec,
Workspace canonical reviews, ApplyPreflightGate, ApplyRetryReceiptGate,
MaterialWriteGate y la autoridad de generación. Conservar W00 30/30,
W01-A 10/10, B0 12/12 y B1 7/7 sin repetir.
El único writer y la sesión DEV no reemplazan grants owner. Ninguna
Review PENDING ni una salida de IA concede permisos.
Evitar hardcode de un descriptor que difiera del catálogo oficial.

## B3 — Alternativas y fuentes
A preferida: autoridad Finance DEV aislada, descriptor oficial, ACL
generation/policyEpoch desde host, owner grant lease que cubra toda
Reality BEGIN→COMMIT, recibo verificado, sin puerto UI productivo.
B usar host antiguo con metadata no idéntica: RECHAZADA.
C un botón APPROVED o texto LLM como permiso de COMMIT: RECHAZADA.
D ACL antes pero no durante COMMIT: RECHAZADA.
E pagos reales externos: FUERA DE ALCANCE.
Primarias SQLite https://www.sqlite.org/lang_transaction.html y
Android https://developer.android.com/topic/architecture/data-layer/offline-first.

## B4 — Particiones con permisos independientes
B2-A: lector puro de alcance Finance aprobado canónico, DEV/DORMANT.
Leer TrustedConversationApplyReviewPort.load(context), verificar estado
APPROVED/identidad exacta, decodificar el envelope actual,
planFingerprint y un nodo sin dependencias, operationId ACCOUNT_CREATE
o ENTRY_RECORD, capability N-025, executor finance,
REALITY_MUTATION/BINDABLE, descriptor identico a catalog.current,
payload tipado válido, generation/hostEpoch y segunda lectura
contra stale/ABA. Devuelve SOLO scope inerte, NO grant/Apply/receipt.
Rutas candidatas NUEVAS sin READY:
- v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B2FinanceApprovedScope.kt
- v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B2FinanceApprovedScopeTest.kt
- .github/workflows/faro-v5-s4-w01-b2a-scope-directed.yml
B2-B: dueño Finance de grant/policyEpoch verificado contra principal
y evento real de host; tests revocación/ABA.
B2-C: lease exclusivo en Workspace→Reality COMMIT, callback
afterBeginApply, autorización dentro Reality y recibo único.
B2-D: CI DEV cruce de SQLite + UNKNOWN/recovery, Android físico final.
Cada bloque requiere B1–B5, READY y B7 propios. B2-A test JVM 8/8
dirigido, sin implicar B2 final.

## B5 — Auditoría adversarial
B2A01 APPROVED cuenta scope tipado; 02 APPROVED entrada.
03 PENDING/DISCARDED/COMMITTED denegados.
04 metadata oficial spoof denegada.
05 multi nodo, dependencias externas denegadas.
06 payload falso/monto/currency denegados.
07 generation/host epoch/ABA denegados.
08 review/envelope cambiado entre lecturas denegado.
TODAS 8 NOT_RUN.
B2B/C: revocación durante binding o SQL COMMIT sin TOCTOU,
cierre/grant/host lifecycle y proceso concurrente; dos taps un
receipt; UNKNOWN NO reintentar ciegamente; saldo REAL solo tras receipt;
sin credencial humana verificada, productivo siempre OFF.
No tocar main, V4.4, S2/S3, Auto/Noche, V2 productiva,
catálogo180, schema Reality7/8, datos G15 ni pagos reales externos.
No otorgar READY por esta preauditoría.
