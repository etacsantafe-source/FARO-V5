# FARO V5 · S4 W01-B0: propuesta canónica Finance B1–B5
Fecha local 2026-11-15. Baseline 3090416219ba7ff8b551e239a11cdbeadfd5291c. Unidad S4_W01_B0_FINANCE_CANONICAL_PREPARED_OPERATION. DOC_ONLY, sin READY.

## B1
W01-A ya B7 PASS 10/10, guard 43782356841, permiso BLOCKED y writer RELEASED epoch940.
Fuentes: FinanceManualDraftPreview.kt (Account/Entry inertes + sourceRevision), FinanceAccountCreatePayload,
FinanceEntryRecordPayload, FinanceOperations (ACCOUNT_CREATE/ENTRY_RECORD), FinanceSnapshot y stamps,
PreparedOperation/CanonicalPayload, OperationContractLookupPort y contract-baseline.tsv.
Ninguno garantiza identidad humana ni permiso de ejecutar. W01-B general B1–B5 publicado.

## B2
Reusar payloads y stamps tipados sin encoder paralelo, idempotencia del motor y catálogo oficial;
conservar V4.4 como referencia UX de P02/P03, no su runtime. No repetir W00 30/30 ni W01-A 10/10.

## B3
A ELEGIDA: preparador puro que transforma preview válido y snapshot fresco en PreparedOperation inerte;
B RECHAZADA: Compose/DAO escribiendo o generando grants; C RECHAZADA: descriptor inventado
por LLM; D RECHAZADA: llamar APPROVED a una propuesta. Fuentes primarias:
https://developer.android.com/topic/architecture/data-layer/offline-first y
https://www.sqlite.org/lang_transaction.html. No incorporar dependencias externas.

## B4
Funciones prepareAccount/prepareEntry. Revalidan preview ante snapshot tomado por un callback,
mismas revisiones antes/después, catálogo oficial current(operationId) dos veces,
capability N-025, executor finance, profile REALITY_MUTATION, BINDABLE y operación exacta.
Cuenta: stamp finance:accounts por revisión. Asiento: cuenta/currency/stamp + revision capturada;
misma intención estable => mismo PreparedOperation e IdempotencyKey.
La clave de intención viene del host confiable y aquí no es autorización.
Solo estas 3 rutas NUEVAS candidatas, requiere permiso independiente antes de B6:
- v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceManualReviewedProposal.kt
- v5/owner/finance/src/test/kotlin/ar/com/faro/v5/owner/finance/FinanceManualReviewedProposalTest.kt
- .github/workflows/faro-v5-s4-w01-b0-proposal-directed.yml
CI dirigida owner:finance:test con XML exacto, sin UI, Reality, Workspace, Apply, receipt,
contrato nuevo, catálogo180, datos externos o Android físico.

## B5
12 tests DISEÑADOS NO_EJECUTADOS: T01 cuenta válida y metadata; T02 asiento válido;
T03 catálogo ausente; T04 descriptor spoof; T05 INTERNAL_ONLY; T06 cambio revisión
snapshot; T07 cuenta duplicada/preview alterado; T08 asiento cuenta ausente; T09
moneda de preview alterada; T10 clave inválida/control; T11 monto/apertura falsificados
o overflow; T12 repetición estable e inerte. Denegar por cualquier excepción.
Próximo paso: guard documental + nuevo READY específico, preflight tres rutas, CI exacta y B7.
