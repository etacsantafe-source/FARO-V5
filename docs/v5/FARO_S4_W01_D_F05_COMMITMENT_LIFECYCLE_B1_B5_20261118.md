# FARO W01 D · Calendario, vencimiento y ciclo de vida de cuotas

## B1

Base revisada `9d25a7920fe40537de979dab63300b1986e5936e`. Cambios F06 preservan el esquema y el writer de compromisos. P05 F05/PF07/PF08 y ARCH-04 requieren observar pendientes, vencidos, parciales y pagos con evidencia, mantener cuotas M+7/M+12/M+36 y preservar historial cuando cambia el calendario. El catálogo N-027 ya registra `finance.schedule.read/update/set_active/delete` y `finance.plan_item.cancel`. El esquema Finance9 separa `finance_schedule.version/active`, `finance_plan_item.state/period/due_local_date` y vínculos de pago a asientos. Tras altas y pagos, faltan bindings de edición/cancelación y lectura. Revalidar este inventario por SHA antes de READY.

## B2

P02 L04/L05 identifica `FaroV4FinanceProductFeatures.kt` @e94c637d y su modelo visible a 6 meses. Mantener fecha civil, separación de realidad y previsto y persistencia de las cuotas posteriores al horizonte. R04/R05 de P03 preservan Finance como autoridad de cuentas y obligaciones, y MonthlyPlan como lectura y coordinación. Usar los mismos comandos y almacenamiento de altas/pagos de W01; la lectura del calendario permanece en el dueño Finance, sin replicar filas con otro owner.

## B3

- Actual Budget (MIT, local-first) separa definición, ejecuciones y opciones de omitir/completar. El arreglo de conservación de fechas omitidas #9173 exhibe un fallo concreto al editar una plantilla. ADAPTAR preservación de las ocurrencias/cancelaciones previas y versionado. DESCARTAR posting automático y deducción de periodicidad. Negativas: una edición no elimina ni duplica un período ocupado y no reintroduce por sí sola una cuota cancelada. Fuentes: https://actualbudget.org/docs/schedules/ , https://actualbudget.org/docs/upcoming-release-notes/ , https://github.com/actualbudget/actual/pull/9173 , https://github.com/actualbudget/actual .
- GnuCash v5 (GPL-2/3 local) diferencia activar, quitar y editar plantilla y muestra el calendario futuro. REFERENCIA para separar la plantilla y la ocurrencia con fecha. DESCARTAR ejecución por asistente, FX, plazo de UI fijo y dependencias. Negativas: desactivar calendario impide nuevas ocurrencias de esa definición pero las cuotas ya abonadas siguen visibles; borrar no destruye historial. Fuentes: https://gnucash.org/docs/v5/C/gnucash-manual/trans-sched.html , https://gnucash.org/docs/v5/C/gnucash-manual/sched-editor.html , https://github.com/Gnucash/gnucash/blob/stable/LICENSE .
- USAR transacción SQLite existente y YearMonth/LocalDate. Derivar estado sin calendario de background ni escrituras por leer fechas. Fuente: https://www.sqlite.org/lang_transaction.html .

## B4

Editar con versión esperada y definición aprobada; fijar una versión nueva preservando toda cuota materializada. Desactivar/reactivar con versión esperada y emitir otra versión para invalidar propuestas viejas. Retirar únicamente una definición sin cuotas dependientes. Conservar su identidad como fila inactiva con versión reservada Long.MAX_VALUE para impedir reuso de ID y ABA después del retiro; ocultarla de las lecturas. Las demás versiones se incrementan de forma exacta y no pueden alcanzar la versión reservada. Las cuotas materializadas preservan su FK e historial. Cancelar solamente una cuota PLANNED sin pago registrado. La generación deliberada comprueba versión, activo, período y ocupación no cancelada para evitar dos cuotas del mismo calendario/mes al recrear. Las ocurrencias canceladas se conservan en historial y sólo una nueva acción explícita puede generar una nueva ocurrencia tras editar versión. Fecha de vencimiento se compara con fecha civil elegida en el host. El lector usa una sola instantánea SQLite y un rango explícito acotado que permite M+36. Devuelve deudas y calendarios disponibles para selección, además de cuotas del rango y moneda solicitados. Devuelve recibos por la vía de Apply y pendientes calculados a partir de gastos enlazados. Estado visible PLANNED/DUE/PARTIAL/PAID_CONFIRMED/CANCELLED, con fecha y monto restante separados. No cambia filas durante la lectura. El host que emita `finance:all` integra esa lectura bajo admisión antes/después y cancela si el fence cambia.

Rutas exactas para preflight:
- `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceOperations.kt`
- `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceCommitmentLifecycle.kt`
- `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceCommitmentLifecycleBinding.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9CommitmentWriter.kt`
- `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9CommitmentLifecycleTest.kt`
- `.github/workflows/faro-v5-s4-w01-d-commitment-lifecycle.yml`

## B5

Versiones viejas y cambios concurrentes quedan rechazados por CAS SQL dentro de AtomicApply. Validar versión desbordada y tiempo de revisión. Desactivar o editar no toca pagos. Cubre declaración distante, fecha de vencimiento sin gasto, dos versiones intentando cubrir el mismo período, cancelar cuota abonada parcial/total, cancelar sin efecto en ledger ni backup y retirar calendario sin cuotas, impedir retiro con dependencias, no volver a crear calendarios retirados. Negar lecturas obsoletas o fuera de rango; ausencia autorizada distingue vacío de parcial. Mantener presupuesto, W08, V2 productiva, integraciones productivas y W2 fuera de esta unidad. Permiso acotado y guard documental previo.
