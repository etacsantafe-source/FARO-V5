# FARO W01 D · Alta de deuda, calendario y cuota concreta

## B1

Base de código `aef656804ed5f559f19181e9e50b00f407a89783`. C0 prueba el vínculo con el recibo en SQLite activo, CI 44325818697 5/5. ARCH-04 y ARCH-05/06 definen F05. El catálogo 180 registra `finance.debt.create` N-025 y `finance.schedule.create`, `finance.plan_item.create` N-027. Los tres tienen executor finance y REALITY_MUTATION BINDABLE en el TSV. `RealityFinance9Schema.kt` blob `f278a26dd16f7f8760aa8ceff93a669bf21e4ea7` ya instala las tablas finance_debt, finance_schedule y finance_plan_item, y el código activo carece de sus bindings. Falta materializar declaraciones sin asientos. El caso de aceptación conserva cuotas M+7, M+12 y M+36 en el grafo activo y con recibos reales.

## B2

Reusar el comportamiento V4.4 L04/L05 y los deltas R04/R05 para distinguir obligación, regla y ocurrencia. R17 conserva AtomicApply y receipt. Reusar el esquema Finance9, catálogo, Scope y owner de Reality. Los recibos prueban el alta de un compromiso. Los pagos tienen una operación explícita posterior. MonthlyPlan conserva su propia autoridad y el cálculo del horizonte permanece en W08.

## B3

Actual Budget ofrece reglas finitas/indefinidas y registro automático o aprobación manual. ADAPTAR la separación de plantilla y ocurrencia, DESCARTAR el registro automático para FARO. GnuCash separa recordatorios de plantillas y cálculo de préstamos. REFERENCIA la vinculación explícita, sin estimar interés ni crear asientos. USAR YearMonth/LocalDate y SQLite para guardar período, ordinal y fecha civil sin un servicio externo. Fuentes https://actualbudget.org/docs/tour/schedules/ , https://www.gnucash.org/docs/v5/C/gnucash-manual/trans-sched.html y https://www.sqlite.org/lang_transaction.html .

## B4

Crear tres comandos, sus payloads v1 canónicos y bindings de los IDs registrados. Los tres escriben exclusivamente dentro de Reality ACTIVE9. Debt tiene título, acreedor, moneda, principal y timestamp. Schedule puede vincular deuda de igual moneda, define período inicial, importe, recurrencia ONCE o MONTHLY y total positivo o indefinido. Plan item manual puede declarar su identidad. El ligado a schedule usa identidad derivada de schedule, versión, período y ordinal. Para un calendario MONTHLY el período corresponde a inicio + ordinal - 1; ONCE sólo admite el ordinal 1. Validar compatibilidad con la moneda, importe, deuda, versión, actividad y fecha del calendario. Las operaciones futuras de edición, pago, lectura para Android y presupuesto conservan su alcance posterior.

El permiso comprende estas rutas.
- `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceOperations.kt`
- `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceCommitmentCreate.kt`
- `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceCommitmentCreateBinding.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9CommitmentWriter.kt`
- `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9CommitmentApplyTest.kt`
- `.github/workflows/faro-v5-s4-w01-d-commitment-declarations.yml`

## B5

Verificar persistencia tras reabrir y retry con un receipt por declaración. Denegar deuda inexistente o en moneda distinta, versión obsoleta, cuota fuera de rango y repetición de identidad. Un vencimiento futuro o pasado deja previsto el compromiso y no crea entries ni pago. Probar M+7/M+12/M+36, payload no canónico y ausencia de otro grafo. Scope ACTIVO DEV y CI JVM; no habilita ADB, app productiva, Auto/Noche, pagos ni W2.
