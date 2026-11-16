# FARO V5 — S4 W01-B · Finanzas F02/F03, Review/Apply/receipt/UX · B1–B5
Fecha local: 2026-11-15. Baseline `3d62a39489b8007e651b5470991d7d2992eb6d6d`. Scope: **DOC_ONLY**, sin permiso nuevo.
Esta es una partición de W01 ya abierta, no creación de un sexto owner. W01-A
`3d62a39489b8007e651b5470991d7d2992eb6d6d` es RED_FIX de fixture
con CI W01-A reejecutándose; no trasladar PASS hasta su verificación.

## B1 — Fuente real y diferencias
Contrato F02 (cuentas) y F03 (ingreso/gasto) en
`docs/v5/FARO_S4_P05_FUNCTIONAL_PRODUCT_SPEC_20261113.md`;
W01 alcance en `docs/v5/FARO_S4_P08_W00_W11_B1_B5_20261115.md`;
preauditoría W01 global `docs/v5/FARO_S4_W01_FINANCE_PREAUDIT_B1_B5_20261115.md`.
Real Finance en `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/`:
`FinanceOperations.kt` tiene `finance.snapshot.read`, `finance.account.create`,
`finance.entry.record`; `FinanceAccountCreate.kt`, `FinanceEntryRecord.kt`,
`FinanceModel.kt`, `FinanceAccountCreateBinding.kt`, `FinanceEntryRecordBinding.kt`
y `FinancePersistencePort.kt` proporcionan modelo, payload, lectura y mutación con transacción.
`v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5ActiveReviewedApplySession.kt`
(blob `3c07c9b0d46b1cdb728396dd8e7067e2f94e225c`): el coordinador
Review/Apply reconoce ambas operaciones Finance con revalidadores y bindings
pero su port se bloquea por `FaroV5HostCertification.V2_ACTIVE_ENABLED=false`.
`FaroV5ModelReviewBridge.kt` acepta solamente `profile.update_declared`;
NO preparar Finanzas a partir de JSON del modelo ni reinterpretar su salida como consentimiento.
`FaroV5ShellNavigation.kt` tiene destino FINANZAS y fallback `FaroV5UnavailableDestination`,
pero NO demuestra pantalla financiera funcional. `FaroV5FinanceSearchReadSource.kt`
(blob `47fdbb88c7a6f2352bf21d14d5f0a2d15a2b46f0`) no monta datos financieros
de producto ni habilita Apply; solo búsqueda sensible a generation+ACL.
`FaroV5PlanReview.kt` presenta revisión de Workspace, pero UI no es autorización.
No hay host de Finance con autenticación humana y grants end-to-end actualmente probado.

## B2 — Reutilización
Reusar las operaciones Finance existentes sin catalog180 nuevo para F02/F03;
`ApplyTransactionExecutor` + único `RealityTransactionScope`, Verify/Retry/Receipt,
Review persistente, idempotencyKey del intento humano y recuperación de UNKNOWN.
No duplicar tests Foundation ni W00. V4.4 referencias L04/L17 de P02/P03
son guía de UX, NO datos ni código importado. Conservar moneda segregada.
Separar preview READ_ONLY, preparar propuesta, pedir Review, validar autoridad,
ejecutar Apply y mostrar receipt. W01-A no concede permiso a W01-B.

## B3 — Fuentes externas y alternativas
- Google Android Developers `https://developer.android.com/topic/architecture/data-layer/offline-first`:
  adoptar repositorio offline-first con SQLite local fuente canónica y UI que lee estado
  autorizado; nunca transformar un placeholder o falta de grant en cuenta vacía.
- SQLite transacciones `https://www.sqlite.org/lang_transaction.html`:
  conservar transacción local única; usar receipt oficial del motor, no una segunda
  inserción de UI. El ejemplo de transferencia bancario online de Android NO
  se aplica: FARO registra SOLO datos locales y no envía pagos.
- A: formularios locales -> propuesta canónica -> Review Workspace -> Apply Finance
  autorizada -> recibo -> lectura Reality: ruta futura elegida.
- B: `Composable` escribiendo `DAO` directamente: RECHAZADO.
- C: levantar V2 productiva para ahorrar host de Finanzas: RECHAZADO.
- D: permitir JSON IA generar grants, IDs, fingerprints o montos sin confirmación:
  RECHAZADO.

## B4 — Desglose operativo, sin READY aún
**W01-B0**. Preparador puro de propuesta Finance: toma únicamente
`FinanceManualDraftPreview` positivo y `FinanceSnapshot` con revision,
relee contrato oficial `OperationContractLookupPort.current`, genera
`PreparedOperation` con payload canónico, dependencias Finance, idempotencia
derivada de una intención humana estable y nunca concedida por IA.
Sin Workspace, DB, React/Compose, Apply, grants, credenciales ni receipts;
propuesta `PENDING` inerte. Rutas nuevas **candidatas**, no aprobadas:
`v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceManualReviewedProposal.kt`;
`v5/owner/finance/src/test/kotlin/ar/com/faro/v5/owner/finance/FinanceManualReviewedProposalTest.kt`;
`.github/workflows/faro-v5-s4-w01-b0-proposal-directed.yml`.
Necesita W01-A B7 real, B1–B5 de B0, permiso propio READY y writer nuevo.
**W01-B1**. Host de creación de Review canónica, única transacción Workspace,
revisión identificable, tentativas humanas explícitas; ningún permiso se deriva
de texto `APPROVED` o de la propuesta.
**W01-B2**. Apply DEV Finance con port dueño de ACL/generation/policyEpoch y
prueba de revocación/ABA hasta COMMIT; recepción de receipt, recuperación
`UNKNOWN` sólo por lookup; V2 productiva permanece OFF.
**W01-B3**. Pantalla Compose Finans F02/F03 con cuenta, moneda, apertura
EXPLÍCITA, concepto/importe/categoría/fecha, estados PENDING/COMMITTED,
display de saldo con fuente y cobertura (no 0 predeterminado).
**W01-B4**. Integración & pruebas en Android/CI de todos los casos F02/F03,
físico Moto G15 sólo campaña final autorizada.
Los pasos B1–B4 no heredan permiso entre sí, W01 global sigue OPEN.

## B5 — Auditoría adversarial
B0-T01..T12 diseñados **NOT_RUN**: catálogo ausente/alterado DENY;
opening null DENY y explicit zero aceptado; entry account inexistente DENY;
currency y binding official mismatch DENY; snapshot revision mutable DENY;
monto 0/negativo DENY; intentKey vacío o duplicado no crea segundo Apply;
propuesta con contrato no bindable DENY; ledger overflow DENY;
no mutación ni grant al construir.
B1/B2: Review literal de un solo user session; generación restaurada ABA;
revocar Finance durante lectura o entre review/commit => STOP;
cancelación sin efecto, doble tap único receipt, crash UNKNOWN reconcile
sin reintento ciego. El sandbox Android no equivale a identidad autenticada.
B3/B4: ACL ausente indica FUENTE_NO_AUTORIZADA, no saldo $0,
ni UI que aparente una transacción cuando el Apply es DENIED.
NO modificar catálogo180, V2 productiva/Auto/Noche, S2/S3, main/V4.4;
ningún pago bancario externo.

## B6/B7 — Estado
Solo este documento B1–B5, handoff y pointer. B0–B4 sin código ni tests
en esta unidad documental. B7 documental exige guard del HEAD publicado,
diff solo docs, writer RELEASED y permiso existente sin READY nuevo.
No declarar W01-B ni W01 completos.
