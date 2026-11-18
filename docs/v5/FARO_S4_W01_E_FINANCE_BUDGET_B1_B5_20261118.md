# FARO W01 E · Límites mensuales y lectura presupuestaria desde Reality ACTIVE9

## B1

Base de inspección `44cd027abce43b2cd2a9be06b7afd25e109d4f14`. P05 F06/PF09 y ARCH-04/05 requieren límite, gastos, compromisos y disponible con cobertura explícita. Los tres contratos N-029 `finance.budget.set`, `finance.budget.clear`, `finance.budget.read` existen en el catálogo 180 y carecen de implementación. Reality ACTIVE9 ya almacena `finance_budget_limit` con clave (período, categoría, moneda), revisión y procedencia. El ledger registra cantidad, categoría, moneda y fecha efectiva. La tabla de transferencias identifica ambos asientos. Las cuotas Finance9 tienen período, estado, moneda y monto esperado, pero su tabla y el formato FBS2v4 carecen de categoría de presupuesto. El saldo pendiente se obtiene de asignaciones registradas al gasto en la unidad F05 de pagos. La preauditoría no sustituye al cierre de esa unidad para READY.

## B2

P02 L04 y L05 identifican la interfaz y gateway de Finanzas V4.4, `FaroV4FinanceProductFeatures.kt` @e94c637d y `FaroV4FinanceIntegrationTest.kt` @29c6ac90. Se conserva lectura por mes/moneda y la distinción REAL frente a previsto. P03 R04 conserva owner Finance y lectura tipada. Se usa Reality ACTIVE9 con el patrón de una transacción de lectura ya existente en `finance9ActiveDevOpeningProvenanceRead`. Se conserva entrada explícita y contabilidad separada de movimientos, backup, UI y horizonte W08.

## B3

- Actual Budget (MIT, local-first) organiza el presupuesto por mes y categoría. Sus transferencias internas al presupuesto no consumen una categoría; una operación hacia fuera sí se trata de otra forma. ADAPTAR la exclusión usando el enlace `finance_transfer` del propio ledger, sin heurística textual ni importar el modelo envelope. Negativa: un débito enlazado no incrementa el consumo, mientras un gasto con la misma etiqueta sí lo hace. Fuentes: https://actualbudget.org/docs/budgeting/ , https://actualbudget.org/docs/transactions/transfers/ , https://github.com/actualbudget/actual .
- GnuCash v5 (GPL-2/3, local) muestra presupuesto y real en columnas independientes por período, con informes de predicción separados. REFERENCIA para presentar límite, gasto verificado y disponible estimado con procedencia distinta. DESCARTAR árbol contable, dependencias, FX y predicción general. Negativas: cambiar el tope no crea gasto y dos monedas con igual etiqueta no se agregan. Fuentes: https://www.gnucash.org/docs/v5/C/gnucash-guide/budget_reporting1.html , https://github.com/Gnucash/gnucash/blob/stable/LICENSE .
- USAR las APIs existentes de fecha civil y SQLite. Una lectura pinneada toma un solo snapshot y devuelve valores desconocidos ante falta de cobertura, revocación o inconsistencia, sin fallback a un acumulador en memoria. Fuente: https://www.sqlite.org/lang_transaction.html .

## B4

Set y clear requieren revisión explícita con la revisión global de Finance observada. La mutación incrementa la revisión global y el set asigna esa revisión creciente a la fila, para impedir que clear seguido de recreación revalide un intento anterior. Clear solo borra un límite existente. Un límite explícito cero se distingue de límite ausente. La escritura reusa la transacción AtomicApply y emite su receipt, sin alterar `finance_entry`. El lector ACTIVE9 usa una consulta pinneada por período civil, categoría y moneda. Toma fecha efectiva en la zona elegida por el host, excluye transacciones enlazadas en `finance_transfer`, usa cantidades enteras exactas y calcula gasto autorizado y compromisos pendientes por moneda. Una cuota de período/moneda con pendiente y sin atribución no se asigna por heurística. Se informa el total sin categorizar y la estimación por categoría permanece desconocida para ese mes/moneda. Con cobertura de fuente completa y ausencia de pendiente sin atribución, el disponible estimado es tope menos gasto real, admite negativo. Si la cuota se liquida, el gasto vinculado aparece según su categoría real y el residual desaparece. La atribución persistente de compromisos requeriría un contrato o evolución de esquema/backup propios y no es parte de este permiso. Sin admisión `finance:all` vigente el lector devuelve estado desconocido/denegado sin datos. Integrar host, formularios, fuentes y presentación en W01-F.

Rutas del permiso:
- `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceOperations.kt`
- `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceBudget.kt`
- `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceBudgetBinding.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9Budget.kt`
- `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9BudgetTest.kt`
- `.github/workflows/faro-v5-s4-w01-e-finance-budget.yml`

## B5

Pruebas: mismo período y categoría en ARS/USD, período civil alrededor de medianoche UTC, topes cero/ausente, clear/recreación y revisión stale, reintento con receipt original, ledger intacto, transferencia interna excluida por enlace, gasto real con etiqueta similar, previsto parcialmente asignado a pagos y disponibilidad desconocida con compromiso sin categoría. Denegar lectura o invalidar al perder admisión en mitad de la operación; overflow y entidad incoherente no devuelven cero. Los totales nunca mezclan moneda. AtomicApply realiza commit o rollback de límite y receipt; el lector no escribe transacciones materiales. Scope DEV SQLite/JVM. W2 STOP, V2 productiva OFF, sin ADB.
