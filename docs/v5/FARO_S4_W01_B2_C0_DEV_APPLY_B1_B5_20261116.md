# FARO V5 — S4 W01-B2-C0 · Apply Finance aislado en TEST DEV — B1–B5
Fecha AR 2026-11-16. Estado DOC_ONLY; ninguna ruta de código READY.
Baseline f0d90c8f25e9cebe2be0db624afb73993a7f1cf5. Subunidad individual: S4_W01_B2_C0_DEV_FINANCE_REVIEWED_APPLY_TEST_HOST.
Deriva de docs/v5/FARO_S4_W01_B2_C_FINANCE_REVIEWED_APPLY_B1_B5_20261116.md.
No confundir C0 con C1 permiso humano/ACL cross-process o C2 recuperación final.

## B1 — Evidencia y diferencia de fuentes
- W01-A 10/10, B0 12/12, B1 7/7, B2-A 8/8, B2-B 8/8 B7 PASS scoped,
  última guard documental 43872652753 SUCCESS, writer epoch960 RELEASED.
  Esas certificaciones NO se repiten.
- FaroV5ActiveReviewedApplySession usa descriptors Finance hardcoded ContractVersion(1)
  y fingerprint legacy; W01-B0 propone metadata-v1 desde BuiltInContractCatalog.
  Reusar ese mapa causaría denegación o aceptación de metadata no canónica.
- ReviewedConversationApplyCoordinator y ApplyTransactionExecutor, FinanceAccountCreateBinding,
  FinanceEntryRecordBinding, FinanceRevalidation, RealityStore.financePersistence
  y Workspace canonical Review soportan transacción SQLite + receipt sin pagos externos.
- S4W01B2FinanceApprovedScopeReader compara APPROVED exacto y puntero con sequence.
  S4W01B2FinanceObjectGrantOwner DEV mantiene nonce/epoch y lease en proceso;
  no valida permisos cross-process ni usuario humano real.

## B2 — Reutilización y fuente de verdad
- Usar solo el descriptor actual de BuiltInContractCatalog, nunca VERSION(1) legacy.
- Reusar FinanceManualReviewedProposal con stamps de Reality Finance en tests,
  Workspace Review durable APPROVED y CAS BeginApply, realidad SQLite TEMP.
- Efecto y receipt solo por ApplyTransactionExecutor; no escribir DAO desde UI;
  NO introducir tabla nueva ni migration schema7/8 ni contrato en catálogo180.
- Host DEV interno no referenciado desde Activities/CompositionRoot ni expuesto
  como ConversationApplyRequestPort. Caso con política simulada SOLO en JUnit.

## B3 — Alternativas y límites
A: host DEV aislado, composición existing coordinator+official catalog+Finance bindings,
   admitir únicamente scope exacto de Review canonical y epoch/nonce de owner;
   ejecutar toda Reality transacción bajo lease mismo proceso; ELEGIDA para C0.
B: activar V2 productiva/host legacy para probar rápido: RECHAZADA.
C: confiar en variable de UI/IA como autorización: RECHAZADA.
D: declarar cross-process garantizado por synchronized JVM: RECHAZADA.
E: usar un receipt simulado o segunda inserción SQL: RECHAZADA.
C0 solo experimenta mecanismos en DB temporal test, NO constituye
autenticación humana, ACL durable, autorización para monto real o pago.

## B4 — Plan exacto propuesto, sin permiso todavía
Tres rutas nuevas separadas, únicas candidatas:
- v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B2C0FinanceDevApplyHost.kt
- v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B2C0FinanceDevApplyHostTest.kt
- .github/workflows/faro-v5-s4-w01-b2c0-apply-dev-directed.yml
El host debe recibir Workspace/Reality/catalog/pointer/owner de test como dependencias,
sin crear grants a partir de texto y sin montar app. Entrada explicitamente
DEV_TEST_ONLY, con persona NO autenticada; si falta scope, host, grant, epoch,
revisión o descriptor exacto, DENY antes de tocar Reality. Lock order:
MaterialWriteGate → Finance grant owner → Workspace CAS →
Reality BEGIN/COMMIT; afterBeginApply relee Workspace FUERA Reality.run.
Dentro Reality.run comprobar identidad inmutable, transacción/plan/alcance y
puntero host, NUNCA invocar Workspace o policyEpoch dentro mutex SQLite.
El lease protege revocaciones en el MISMO owner solamente.
Una carrera cross-process o política externa NO certificada sigue STOP productivo.

## B5 — Pruebas adversariales dirigidas; NO EJECUTADAS
C0-01 Account CREATE APR. canónico → COMMITTED, saldo/receipt SQLite TEMP.
C0-02 Entry expense en cuenta creada con revisión nueva → receipt y saldo.
C0-03 PENDING/DISCARDED → DENY sin Reality write/receipt.
C0-04 Ausencia de owner policyEpoch/hostReady → DENY sin efecto.
C0-05 Scope/descriptor legacy corrupto y catalog inconsistente → DENY.
C0-06 ABA puntero/hostEpoch/owner revoke previo → DENY.
C0-07 doble gesto sobre COMMITTED → no segundo Apply ni segundo receipt.
CI dirigida EXACTA JUnit 7/7, guard mismo SHA, diff exactamente tres rutas,
XML con 7 métodos, B7 posterior doc-only y permiso consumido.
Crash UNKNOWN, atomicidad de políticas inter-proceso, G15, UI productiva
y consumo de autorizaciones humanas NO certifican aquí; preparar C1/C2
con permisos distintos. S2/S3, main/V4.4, Auto/Noche, V2 productiva OFF.
