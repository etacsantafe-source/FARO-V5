# FARO V5 — S4 W01 · ARCH-04 catálogo canónico y F02–F06 (corrección de colisión)
**Fecha:** 2026-11-16 (Argentina). **Unidad:** `S4_W01_ARCH04_CATALOG180_CORRECTIVE_B1_B5`. **Alcance:** DOC_ONLY; B1–B5 de arquitectura; B7 global W01 pendiente, código funcional NO autorizado.
**Autoridad:** HEAD `dbca79b8d47821ada442398d39e28b52a2837344`, `docs/v5/V5_OPERATION_CONTRACT_INVENTORY.md`, `v5/spec/generated/contract-baseline.tsv` generado y `FaroSpecBridge.kt`. El catálogo generado precede a `FinanceOperations.kt`: este último declara **solo las tres operaciones IMPLEMENTADAS** en Finance owner, no el conjunto oficial de contratos.
**Corrigendum:** El documento `FARO_S4_W01_ARCH01_ARCH06_DESIGN_DECISIONS_20261116.md` propuso erróneamente nuevos IDs `finance.transfer.record`, `finance.obligation.upsert`, `finance.obligation.settle` y `finance.budget.upsert`. El primero **ya está registrado** y los otros tres crearían semánticas paralelas a las aprobadas. Quedan DESCARTADOS antes de cualquier B6. No tratar una propuesta histórica sin B7 como autoridad sobre el catálogo exacto.

## B1 · Comprobación primaria con nombres y perfiles reales
El `contract-baseline.tsv` actual consta de **180 operaciones**; por ejemplo líneas 105–133:
| Flujo | Operaciones canónicas existentes | Capability, executor, perfil |
|---|---|---|
| F02 cuentas | `finance.snapshot.read`; `finance.account.create` | N-025; finance; READ_ONLY / REALITY_MUTATION |
| F03 movimientos | `finance.entry.record`; `finance.snapshot.read` | N-025; finance; REALITY_MUTATION / READ_ONLY |
| F04 transferencia LOCAL igual moneda | `finance.transfer.record` | N-025; finance; REALITY_MUTATION |
| FX | `finance.fx_transfer.record` | N-026; finance; REALITY_MUTATION — **FUERA de W01**; no activar conversión |
| F05 obligación/deuda | `finance.debt.create`, `finance.debt.payment.record` | N-025; finance; REALITY_MUTATION |
| F05 recurrencia y cuota concreta | `finance.schedule.create/update/set_active/delete/read`, `finance.plan_item.create/execute/cancel` | N-027; finance; READ_ONLY/REALITY_MUTATION, perfil exacto según TSV |
| F06 presupuesto | `finance.budget.read/set/clear` | N-029; finance; READ_ONLY/REALITY_MUTATION |
| Coordinación mensual | `monthly_plan.read`, `monthly_plan.item.add/cancel` | N-028; planning; **authority separada**; no duplica hechos Finance |
| Horizonte F07 posterior | `finance.horizon.read` | N-027; finance; READ_ONLY — cálculo/presentación W08, no W01 |

Confirmación adicional en fuente `V5_OPERATION_CONTRACT_INVENTORY.md` filas N-025..N-029 y tabla de operaciones detallada; los nombres anteriores existen, NO son placeholders. En `FinanceOperations.kt` sólo están `snapshot.read/account.create/entry.record`, por eso **faltan bindings, DTOs, persistencia y montaje**, no nuevos IDs.

## B2 · Mantener semánticas existentes, delimitación V4.4 y W08
Usar IDs congelados 180/180 y su profile original sin mutarlos; preservar 172 históricos + 8 Guardados, catálogo generado/taxonomía/refreeze. No editar TSV manualmente ni subir 180→184. Los contratos para planes futuros pertenecen a Finance N-027, pero W01 sólo construye su soporte de cuota/deuda/recurrencia real que exige F05; el motor de proyecciones/calculadora `finance.horizon.read` corresponde W08. `monthly_plan` coordina lectura y fechas sin escribir pagos en Finance ni asumir APPPROVED en Finance.

V4.4 P02 L04/L05/L17 y P03 R04/R05/R17 son **referencia de UX y semántica**; no copiar DB, ejecutor ni modificar V4.4. Compras/Household no realiza mutaciones Finance; FARO es registrador local, NUNCA interfaz de banco, orden de transferencia real ni pasarela de pago.

## B3 · Alternativas auditadas y resolución
A (ADOPTAR): implementar bindings nuevos para IDs **ya catalogados**, manteniendo metadata congelada y una transacción Reality por operación; acotar W01 a F02–F06.
B (DESCARTAR): añadir `finance.obligation.upsert/settle`, `finance.budget.upsert` u otro catálogo duplicado y modificar 180→184; rompe identidad/existing grant y aumenta superficie sin necesidad.
C (DESCARTAR): dos `finance.entry.record` independientes para transferencia; produce débito unilateral ante crash.
D (DESCARTAR): marcar cuota pagada por vencimiento o crear gasto desde presupuesto; rompe Reality≠estimación.
Fuentes técnicas externas: SQLite `https://www.sqlite.org/lang_transaction.html`, `https://www.sqlite.org/isolation.html`, `https://www.sqlite.org/wal.html`; Android offline-first `https://developer.android.com/topic/architecture/data-layer/offline-first`. Reutilizar benchmarks P04 Actual Budget como referencia UX, sin copiar runtime/licencias ni exigir red.

## B4 · Contratos de dominio decididos para implementar MÁS ADELANTE
**F02:** `finance.account.create` v1 conserva codificación histórica; formulario debe requerir `openingBalanceMinor` presente incluso cuando 0. La implementación Reality7 almacena un opening positivo sólo si >0 y usa `COALESCE(...,0)`: migración futura deberá añadir estado de procedencia **CONFIRMADO / LEGACY_SIN_CONFIRMAR**, no reinterpretar ausencia como certificado. Datos legacy no confirmados se muestran NO_VERIFICADO hasta revisión humana. No reescribir historia/recibos.
**F03:** `finance.entry.record` v1 conserva signos por INCOME/EXPENSE, importe minor positivo, concepto, cuenta, categoría, fecha efectiva≤fecha registro, moneda de cuenta; corrección de asiento ya committed requiere nuevo hecho compensatorio con causalidad y receipt, no `UPDATE` silencioso del ledger.
**F04:** `finance.transfer.record` tendrá payload canónico `transferId, fromAccountId, toAccountId, currency, amountMinor>0, effectiveAt, recordedAt, concept, humanIntentKey`. Las cuentas han de existir, diferir y ser de igual moneda; prohibir FX. Un único ApplyPlan con un nodo / un `BEGIN IMMEDIATE`, **dos entries** INCOME/EXPENSE + un `finance_transfer` de enlace en el mismo Reality y un solo receipt. Overflow/fallo segundo INSERT→ROLLBACK. No autoriza descubierto bancario: saldo negativo manual se permite exclusivamente tras preview + confirmación explícita de advertencia. Dos confirmaciones no deben equivaler a dos intents.
**F05:** `finance.debt.create` es identidad de la obligación monetaria; `finance.schedule.*` es template de recurrencia/cuotas; `finance.plan_item.create` es ocurrencia materializada deliberadamente por acción/revisión, con identidad estable `scheduleId+version+period+ordinal`. `finance.debt.payment.record` registra hecho de pago vinculado a asiento y receipt; `finance.plan_item.execute` enlaza item con esa causalidad, una sola mutación revisada. Compatibilidad parcial: importes abonados acumulados derivan de entries/receipts confirmados; deuda y cuota futuras se mantienen `COMMITTED/PLANNED/DUE` sin producir un gasto REAL por pasar tiempo. Al modificar schedule, no alterar cuotas ya liquidadas; duplicados por 36 meses, borrado de calendario y cambio de generación se rechazan según revision+identity. Estados propuestos `PLANNED, PARTIAL, PAID_CONFIRMED, CANCELLED` no crean nuevos OperationIds. El `MonthlyPlan` existente en schema8 tiene su propia reconciliación/coordination, nunca obtiene Finance grant por referencia.
**F06:** `finance.budget.set` escribe límite por `(YearMonth,Category,Currency)` con revisión, idempotencia, sin entry/receipt de gasto; `finance.budget.read` devuelve límite y consumo derivado de `finance.entry` (sólo gastos reales autorizados), más compromiso por fuente explícita separado. `finance.budget.clear` permite cese sin borrar ledger. Cuenta/saldo/mes de fuente DENIED/UNKNOWN produce estado PARCIAL/NO_ACCESS, NO cero. ARS/USD no se suman sin fuente FX real autorizada. Un presupuesto jamás mueve dinero fuera de FARO.
**Payloads/versiones:** cada binding nuevo tendrá `CanonicalPayload` versionado, límites de bytes, UTF-8 canónico, overflow-safe Long minor, orden estable, no trailing bytes, validación de igualdad del descriptor exacto con catálogo 180, revision/epoch/generation y fingerprint; revalidar inmediatamente antes del COMMIT. Las rutas Kotlin reales se nombrarán en preflight del B1–B5 de cada unidad tras B7 arquitectónico. No hay code READY en este documento.

## B5 · Matriz adversarial obligatoria y clasificación
- F02 opening ausente → NO_CONFIRMADO, no 0 automático; duplicado/moneda inválida/overflow DENY.
- F03 doble tap/crash después de receipt, revisión cancelada, cuenta revocada, fingerprint distinto → 1 o 0 asientos, sin re-Apply incierto.
- F04 cuenta igual, distinta moneda, segundo efecto falla, saldo negativo con aviso rechazado, doble commit, receipt UNKNOWN, ABA restore → dos o cero efectos inseparables.
- F05 `M+7/M+12/M+36` persisten; fecha vencida no crea entry; pago parcial no declara PAID; repetición schedule/occurrence y restore no dupliquen; modificación de versión no altera cuota pagada.
- F06 Límite actualizado ≠ gasto; presupuesto por ARS y USD segregado; lectura parcial/denegada no cero; presupuesto revocado no ejecuta.
- Cualquier caso sin actor autenticado/consentimiento o política durable con revoke entre check y SQL COMMIT: STOP hasta ARCH-02/03 implementados y suites de dos procesos.

## B6/B7 documental y estado
B6: este checkpoint corrige únicamente autoridad de diseño/handoff. No Kotlin, tabla SQL, Gradle, workflow funcional ni Android.
B7 de **esta corrección** requiere diff documental, SHA de fuentes oficiales y guard del commit en verde con writer RELEASED. `ARCH-04` queda **DECISION_DEFINED_PENDING_INDEPENDENT_B7**; ARCH-01/02/03/05/06 y global W01 permanecen OPEN. Un B7 documental no convierte tests diseñados en tests ejecutados.
