# FARO V5 — S4 W01-B2-C · Finance Review → Apply → Reality receipt · B1–B5
Fecha local 2026-11-16. Documento PREAUDIT exclusivamente READ_ONLY,
SIN READY ni código. Baseline 8b9257aebc626c135abb2eee44396d84e1b54f7e.
W01-B2-B B7 debe confirmarse por guard documental y writer.
W01 global sigue OPEN. Sin pagos externos/online.

## B1 — Evidencia verificada y brecha
W01-A preview 10/10, B0 canonical proposal 12/12,
B1 Workspace PENDING review 7/7, B2-A approved scope 8/8,
B2-B DEV scoped in-process grant owner 8/8. No repetir.
FaroV5ActiveReviewedApplySession.kt usa catálogo legacy Finance
ContractVersion(1) y fingerprints no idénticos al metadata-v1
generado oficialmente; su port shipping devuelve
Denied(v2_not_certified). NO reutilizarlo a ciegas ni activar V2.
ReviewedConversationApplyCoordinator.kt tiene BeginApply Workspace
CAS, callback afterBeginApply fuera de Reality mutex, preflight,
binding, ApplyTransactionExecutor/receipt, reconcileCommittedOnly;
la continuidad UNKNOWN es solo lookup (nunca replay ciego).
FinanceAccountCreateBinding y FinanceEntryRecordBinding ya escriben
dentro de RealityTransactionScope, no transacciones online.
ApplyTransactionExecutor tiene autorizer por nodo antes y después
de binding, pero no protege por sí solo el intervalo desde último
check hasta COMMIT frente a procesos externos.
W01-B2-B monitor protege solo revocación dentro del MISMO proceso;
no equivale a fuente de política durable ni identidad humana.

## B2 — Conservación/ownership
Conservar los 180 descriptores y usar exclusivamente
BuiltInContractCatalog.current como fuente para Finance F02/F03.
Conservar una sola conexión Reality bajo MaterialWriteGate,
Workspace review/canonical envelope, pinned generation sequence,
owner grants Finance y receipt oficial del motor.
No nueva DB ni receipt artificial de Surface, no transferir
permisos de Household, MonthlyPlan, Chat o modelo.
Reusar pruebas W00 de atomicidad y las cinco subunidades B anteriores
sin duplicarlas. No escribir resultados REAL sin receipt.

## B3 — Alternativas y fuentes primarias
A: host DEV no compuesto en Activity que prueba el flujo
Review APPROVED → verificación descriptor oficial → owner
grant/policyEpoch → Workspace BeginApply CAS →
afterBeginApply promueve scope exacto →
Atomic Reality Apply con autorización in-transaction →
Reality receipt → Workspace COMMITTED/UNKNOWN.
Sólo habilitarlo a modo DEV y bajo permiso scope propio.
B: host V2 productivo con metadata legacy: RECHAZADO.
C: saltarse ACL por callback foreground, IA o UI APPROVED: RECHAZADO.
D: monitor Kotlin como garantía cross-process: RECHAZADO.
E: dos SQL writes fuera de transacción o dos receipts: RECHAZADO.
SQLite https://www.sqlite.org/lang_transaction.html;
Android offline-first https://developer.android.com/topic/architecture/data-layer/offline-first.

## B4 — Plan de partición y puertas no otorgadas
B2-C0: composición DEV Finance con cat oficial, bindings y
revalidadores existentes, SOLO pruebas bajo autoridad simulada y
sin montaje productivo. Necesita audit B1–B5 específica,
READY exact paths, comprobar que el host NO exponga un
ConversationApplyRequestPort a Surface/Chat.
B2-C1: verificar ACL/epoch desde usuario local real,
con host foreground autenticable, en tanto no se pueda
mantener barrera transaccional durable multi-proceso
obligar DENY productivo. El bloqueo de WriteCoordinator
entre procesos y la revalidación de owner/policyEpoch
deben cubrir BEGIN→COMMIT; no Workspace/Policy calls
dentro del mutex Reality por inversión de locks.
B2-C2: recuperación UNKNOWN por lookup,
revisión COMMITTED con recibo existente y doble tap
único; test G15 sólo en campaña física final.
Los caminos concretos de código no quedan autorizados
por este documento. NO nueva migración Reality ni
nuevo contrato por B2-C0 sin otro scope explícito.

## B5 — Pruebas adversariales previstas NOT_RUN
C01 account CREATE revisada COMMITTED y read Finance real.
C02 ENTRY expense/income respeta cuenta/currency/saldo.
C03 APP sin Review APPROVED, sin grant, sin policyEpoch:
DENY y ledger intacto.
C04 descriptor oficial mismatch/metadata legacy DENY.
C05 generación ABA/host closure/revoke antes BeginApply:
DENY sin ledger.
C06 revoke durante Reality binding o antes COMMIT:
rollback o STOP, y epoch no reutilizable.
C07 crash después COMMIT antes entregar Workspace:
recuperar con receipt exacto, NO segundo Apply.
C08 doble tap y idempotencia: mismo receipt;
UNKNOWN incierto NO ejecutar de nuevo.
C09 concurrencia entre procesos cambia policyEpoch:
FAIL-CLOSED (requisito para subir a producción).
C10 source stale/restore schema7/8: STOP sin inventar 0.
Hasta demostrar C09 y cierre físico, NO habilitar
W01 Finance Apply de producto. B2-C tests NOT_RUN,
no READY, no source code ni migraciones.
Mantener V2 productiva/Auto/Noche OFF, main/V4.4/S2/S3,
catálogo180/schema Reality7/8 intactos.
