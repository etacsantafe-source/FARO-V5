# FARO V5 — S4 W01 · Finanzas manual local · B1–B5 y partición segura
Fecha local: 2026-11-15. Unidad global `S4_W01_FINANCE_MANUAL_LOCAL`. Baseline remoto `55578538c33e5d43c5982bb6205f913023479f0a`.
Modo de este commit: **DOC_ONLY**. S4 abierta; W00 arquitectura cerrada; W01 **ABIERTA, sin B6 funcional**.
Permiso de código heredado: `BLOCKED`; catálogo de 180 descriptores, S2/S3, main, V4.4, V2 productiva, Auto/Noche y G15: INTACTOS.
Los bloques siguientes son preauditoría **específica**, no certificación de una app Finanzas funcional.

## B1 — Evidencia de implementación y demandas
Fuentes de producto: `docs/v5/FARO_S4_P05_FUNCTIONAL_PRODUCT_SPEC_20261113.md` (F02–F06, PF03–PF09);
`docs/v5/FARO_S4_P07_PAYMENT_PROMOTIONS_FINAL_DELTA_20261113.md` (dinero exclusivamente Finance);
`docs/v5/FARO_S4_P08_W00_W11_B1_B5_20261115.md` (W01). W00 B7 final en
`docs/v5/FARO_S4_W00_FINAL_B7_ARCHITECTURE_20261115.md`.
HEAD verificado 55578538c33e5d43c5982bb6205f913023479f0a; writer RELEASED epoch 932 y último checkpoint idéntico al HEAD; guard
43779157636 `completed/success` del mismo SHA; permiso `BLOCKED`.
Código real reutilizable a HEAD:
- `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceAccountCreate.kt` (blob 1de8eb82029d29b0dbe42d3f633119793f44e8bf):
  cuenta, nombre, moneda, naturaleza, saldo inicial minor no negativo, con **default 0 inseguro para formulario implícito**.
- `FinanceEntryRecord.kt` (blob bf5af8e3e814a0a392e5acaaa8fd1a4d21744eac):
  importe positivo minor, ingreso/gasto, cuenta, categoría, fecha, concepto y procedencia.
- `FinanceModel.kt` (blob 22f9444f931aff356836bf61bffb10e9975142b7):
  FinanceSnapshot revisionado, monedas por cuenta, saldo con `Math.addExact/subtractExact`, sin conversión FX.
- `FinanceOperations.kt` (blob e4cd61328e22c53795940f376ffd75f27993ef62):
  SOLO `finance.snapshot.read`, `finance.account.create`, `finance.entry.record`; NO contrato de transferencia,
  obligación o presupuesto.
- `FinanceAccountCreateBinding.kt`, `FinanceEntryRecordBinding.kt`, `FinancePersistencePort.kt`
  y RealityStore: base de escritura bajo `RealityTransactionScope`. Sus tests históricos son scoped, no UI manual.
- `FinanceEntryRecordRevalidation` comprueba que exista cuenta; NO demuestra lease revocable ni consentimiento.
- W00 dos owners 30/30 tests scoped: no prueban W01 ni grants de Finance en UI.
No cuenta bancaria conectada, importación ni dinero movido externamente.

## B2 — Reuso y límite V4.4
Reusar el ledger y operaciones Finance existentes, ApplyPlan/fingerprint, Review, scope Reality único,
journal PREPARED/COMMITTED, receipts, recuperación y ACL por owner. P02/P03 ya compararon V4.4:
conservar usabilidad de cuentas, movimientos, vencimientos y presupuestos, NO copiar DB ni estado de pruebas.
Un movimiento REAL solo nace tras revisión y Apply aceptado: un borrador nunca crea saldo.
Evitar duplicar pruebas existentes de FinanceOwnerFoundationTest y FinanceEntryFoundationTest;
probar únicamente los deltas nuevos. Las compras son Household y NO acceden a escribir Finance directamente.

## B3 — Investigación de alternativas y elección
A) Formulario manual local -> **preview canónica pura** -> sesión de Review con grant Finance válido ->
Apply idempotente -> receipt -> lectura Reality; **ELEGIDA como arquitectura por fases**.
B) Registrar entradas directamente desde Compose/DAO salteando Review/Apply: RECHAZADA.
C) Simular una transferencia como dos confirmaciones/commits de ingreso y gasto: RECHAZADA;
debe existir un solo ámbito transaccional con rollback integral.
D) Crear deudas futuras como gastos REAL: RECHAZADA.
Primarias consultadas: SQLite transaction/BEGIN IMMEDIATE `https://www.sqlite.org/lang_transaction.html`;
Android Developers offline-first source-of-truth `https://developer.android.com/topic/architecture/data-layer/offline-first`.
Aplicabilidad: base local única, sin red; **ninguna API paga**, sin adoptar ejecutores externos.
La garantía de revocación cruzada pendiente de W00/W06 NO se obtiene de SQLite por sí sola.

## B4 — Partición y contratos de entrega W01
- `W01-A` F02/F03 **preparación de formulario/draft canónico READ_ONLY**. Nuevo helper de owner Finance;
  jamás Apply, transacción, grant, UI, ni mensaje de registro exitoso. Rutas exactas candidatas:
  `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceManualDraftPreview.kt`,
  `v5/owner/finance/src/test/kotlin/ar/com/faro/v5/owner/finance/FinanceManualDraftPreviewTest.kt`,
  `.github/workflows/faro-v5-s4-w01-a-manual-preview-directed.yml`. Código **sin READY** hasta guard documental y permiso nuevo.
- `W01-B` F02/F03 entrada humana, Review, Apply, receipt, lectura y UX offline; requiere autorización y
  pruebas independientes (IDs nuevos no asumidos). Una cuenta sin opening explícito es UNKNOWN,
  no cuenta con saldo cero por omisión.
- `W01-C` F04 transferencia interna: solo una transacción de dos efectos sobre cuentas de igual moneda,
  con recibo/idempotencia, error del segundo efecto => rollback completo. La API actual NO contiene
  descriptor ni binding atómico de transferencia: se requiere decisión de contrato y autorización específica
  antes de ampliar el catálogo 180 o escribir. No sustituirlo por doble `entry.record` sin host compuesto probado.
- `W01-D` F05 compromisos/cuotas: modelar `COMPROMETIDO` separado de `REAL`, fecha de vencimiento
  no significa `PAID_CONFIRMED`, y cronogramas más allá de M+5 se retienen. Contrato/esquema/ACL aún no aprobados.
- `W01-E` F06 presupuestos por mes/categoría/moneda, sin cash movement, provenientes de lectura Finance y
  MonthlyPlan autorizados; no sumar ARS+USD ni mostrar 0 si falta fuente; requires schema/owner review.
- `W01-F` B7 integración, pruebas android y físico solo al final permitido; no afirmar W01 cerrado
  sin F02–F06, recibos, ACL/revocación/restore y CI por SHA.
Para W01-A: `FinanceAccountCreate` solo con `openingBalanceMinor` aportado explícitamente;
validar nombre/ID/currency/tipo, duplicados y valor >=0. Para entrada: monto >0, cuenta existente,
fecha no futura respecto de registro, concepto y categoría, currency de la cuenta, provenance y
snapshot revisionado. Resultado solo `Preview`, **sin capacidad de escritura**; no hacer fallback a cero.
Las unidades B–F NO heredan READY de A.

## B5 — Auditoría adversarial y matrices de STOP
W01-A negativos nuevos:
A01 opening ausente -> DENY, nunca 0 supuesto;
A02 opening negativo -> DENY;
A03 account ID duplicado -> DENY;
A04 moneda inválida -> DENY;
A05 monto cero/negativo -> DENY;
A06 cuenta ausente -> DENY;
A07 fecha efectiva posterior al registro -> DENY;
A08 entrada duplicada -> DENY;
A09 sobreflujo de ledger -> DENY/EXPLICIT_ERROR, no wrap;
A10 preview -> cero escrituras/recibos, no autorización.
W01-B: doble toque idempotente, grant Finance revocado, plan stale/restore ABA, UNKNOWN reconciliado con
receipt, cancelación sin mutación y cuenta no autorizada no mostrada como cero.
W01-C: débito aprobado/crédito fallido -> rollback; reconciliación después de crash; FX sin fuente -> STOP.
W01-D/E: compromiso no pago real, saldo desconocido != 0, dinero de Household nunca autorizado en Finance.
W01-A puede salir **solo con permiso separado READY** tras evidencia verificable, preflight exacto
de tres rutas, writer CAS y CI/XML exactos. Pruebas A01–A10 aquí **DISEÑADAS, NOT_RUN**.
W01 global permanece **OPEN** hasta W01-B–F; W06 real y W07 siguen bloqueados, V2 productiva OFF.

## B6/B7 — puerta pendiente
Este commit publica solo esta auditoría, handoff y pointer; no código ni CI Android nueva.
Verificar diff exacto docs, guard del HEAD, writer release con last_checkpoint exacto y permiso BLOCKED
antes de declarar PREAUDIT_PUBLICADA. W01-A requiere un siguiente commit documental de READY; luego B6 y B7 propios.
**NO declarar W01 COMPLETA por esta preauditoría.**
