# FARO V5 · W01-B2-C2 · Reality Finance receipt-only recovery B1–B5
Fecha AR 2026-11-16. READ_ONLY PREAUDIT, SIN READY ni código.
Iniciar solo después de C0 B7 documental exacto verificado.

## B1 — Fuentes reales
ReviewedConversationApplyCoordinator.reconcileCommittedOnly:
para Workspace APPLYING o RECOVERING_UNKNOWN lee plan/fingerprint,
transactionId, Reality applyAttempt COMMITTED y matching receipt,
y avanza Workspace ObserveCommitted con CAS; no llama resolve de
intento nuevo, bindings ni executor. Fallar si PREPARED/UNKNOWN.
FaroV5ReviewedApplyHostIntegrationTest ya cubre Profile recovery,
pero NO test Finance con metadata-v1. W01-B2-C0 7/7 prueban flujo
normal Finance REAL en DB TEMP, no crash between COMMIT and Observe.
W01-B2-C1 cross-process sigue NO_GO.

## B2 — Reutilización
No escribir una nueva implementación de recovery. Consumir el API
reconcileCommittedOnly sobre Workspace/Reality existentes y
FinanceAccountCreateBinding con catálogo oficial; sólo test XML
nuevo y workflow dirigido. No repetir Profile F1 ni C0 7 casos.
No exponer recovery como permiso Apply ni reintentar UNKNOWN.

## B3 — Elección
A: prueba dirigida de crash sintético entre SQLite COMMIT y
Workspace ObserveCommitted, reabrir ambos stores y reconciliar
solo desde journal+receipt. ELEGIDA.
B: llamar a coordinator.apply para "reintentar": RECHAZADA.
C: interpretar saldo como receipt: RECHAZADA.
D: fabricar COMMITTED desde texto/UI: RECHAZADA.

## B4 — Dos rutas candidatas, todavía NO READY
- v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B2C2FinanceReceiptRecoveryTest.kt
- .github/workflows/faro-v5-s4-w01-b2c2-receipt-recovery-directed.yml
No source runtime; el API existe. 5 tests propios, no repetir C0:
R01 caída simulada después Finance account Reality COMMITTED
      antes Workspace, reabre y observa receipt único.
R02 sin intento no crea PREPARED ni recibo.
R03 PREPARED sin COMMITTED deja RECOVERY_REQUIRED.
R04 contexto erróneo/fingerprint alterado DENY.
R05 segunda observación no reejecuta, ledger y revision estables.
Solo SQLite local temporal de JUnit; sin Android físico.

## B5 — Auditoría adversarial y límites
Ningún execution callback ni binding debe invocarse en
reconcileCommittedOnly. Reapertura debe reconstruir receipt
exacto por TransactionId/PlanFingerprint, sin segunda mutación.
UNKNOWN no debe transformarse en saldo cero, ni autorizar
segundo Apply. C1 policy cross-process y consentimiento humano
persisten STOP; el éxito C2 no habilita V2 productiva.
Todos los 5 tests NOT_RUN. C2 requiere su propio READY, guard,
7 bloques, CI XML y B7. No cambiar catálogo, migrations ni S2/S3.
