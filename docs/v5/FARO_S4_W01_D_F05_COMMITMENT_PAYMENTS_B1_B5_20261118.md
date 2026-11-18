# FARO W01 D · Registro de pagos parciales y totales de compromisos

## B1

Base de inspección del código `615660cc325a3851d87897df9bcfddcdfa5a7d80`. La preauditoría de altas y la CI `44374508754` pertenecen al bloque anterior. Para autorizar pagos, se requiere cerrar ese bloque con evidencia B7 y verificar el guard documental. ARCH-04 y P05 F05/PF08 exigen que el vencimiento nunca genere un gasto ni cambie el compromiso a pagado. En el catálogo 180, `finance.plan_item.execute` (N-027) y `finance.debt.payment.record` (N-025) son mutaciones Finance registradas, todavía sin binding de pagos en esta base. `RealityFinance9Schema.kt` ya contiene `finance_plan_item_payment` con FK a cuota y asiento, asiento único y trigger de tipo/importe/moneda. La llave del recibo proviene de AtomicApply. La unidad es SQL ACTIVE9 DEV. W01-F deberá montar los bindings bajo el Review, sesión y grant existentes, que hoy admiten altas de cuenta y movimientos.

## B2

La auditoría V4.4 P02, L04, L05 y L17, registra `FaroV4FinanceGateway.kt` @d70a7d71, `FaroV4FinanceProductFeatures.kt` @e94c637d y `FaroV4AtomicApplyTransaction.kt` @a43f43cf. L04 y L17 conservan owner único, distinción entre hechos y compromisos y retry idempotente. L05 separa realidad y proyección; un plazo visible no limita persistencia. V5 conserva su catálogo, el ledger de `recordFinanceEntry`, el gate y el receipt existentes. No se toma la ruta histórica alternativa de escritura, cuya atomicidad global no está certificada. Se reusa el enlace/tipo ya cubierto por el formato de backup Finance9.

## B3

- GnuCash v5, software local GPL-2/3, vincula explícitamente factura/obligación y pago, permite cambiar el importe en un pago parcial y mantiene compatibilidad con registros antiguos. Las referencias históricas de lots muestran riesgo de enlaces sobrantes y de interpretar un crédito como pago normal. ADAPTAR el concepto de asignación explícita a un gasto positivo y un solo origen. DESCARTAR el editor contable, las dependencias y el crédito residual en esta unidad. Negativas derivadas: mismo asiento en dos cuotas, ingreso o monto incongruente, reintento tras reapertura. Fuentes: https://gnucash.org/docs/v5/C/gnucash-manual/busnss-ar-payment.html , https://gnucash.org/docs/v5/C/gnucash-manual/busnss-ap-payment.html , https://www.gnucash.org/docs/v5/C/gnucash-manual/tool-lots.html , https://github.com/Gnucash/gnucash/blob/stable/LICENSE .
- ERPNext Payment Entry, suite GPL-3, separa pago de asignación y lee el saldo del ledger. Su documentación explica la falsa liquidación cuando faltan filas y distingue anticipos no asignados. REFERENCIA para validar saldo contra enlaces reales dentro de la misma transacción. DESCARTAR servidor, FX y reparto multiobligación, por coste de integración y alcance; no incorporar librerías ni APIs. Negativas derivadas: pago superior al pendiente, suma anterior obsoleta y discrepancia de moneda. Fuentes: https://docs.frappe.io/erpnext/payment-entry , https://docs.frappe.io/erpnext/payment-entry-outstanding-and-currency-problems , https://github.com/frappe/erpnext-14/blob/develop/license.txt .
- USAR la conexión SQLite y AtomicApply existentes. La frontera de rollback incluye asiento, enlace y recibo. Los SAVEPOINT y transacciones propias por cada efecto no aportan valor aquí. Fuente: https://www.sqlite.org/lang_transaction.html .

## B4

Comando v1 de pago de obligación registrada, con cuota concreta, deuda explícita para `finance.debt.payment.record`, moneda, suma anterior revisada y el `FinanceEntryRecord` positivo de tipo EXPENSE como hecho de pago. El caso directo de una deuda sin calendario admite una cuota manual explícita vinculada; la misma regla conserva la causalidad y evita un segundo tipo de enlace/backup. `finance.plan_item.execute` también admite cuota manual sin deuda. Ambos IDs comparten un puerto SQL y cada revisión aplica solo uno. El límite se calcula con `expected_minor` menos suma de asignaciones válidas. Una diferencia con la suma revisada exige una nueva confirmación. No se presume que las cuotas incluyan solo principal. La fecha civil de vencimiento no restringe que un gasto real ya hecho se registre anticipadamente o tarde; la fecha efectiva del gasto permanece restringida por su fecha de registro. La lectura posterior deriva pendiente/parcial/liquidado de las asignaciones; persiste el estado base PLANNED y preserva CANCELLED. El helper usa el mismo scope y la misma conexión, agrega el vínculo y reutiliza el asiento. El receipt se confirma en el Apply original. Las tablas existen y el formato FBS2v4 ya las incluye.

Rutas para preflight individual:
- `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceOperations.kt`
- `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceCommitmentPayment.kt`
- `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceCommitmentPaymentBinding.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9CommitmentWriter.kt`
- `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9CommitmentPaymentTest.kt`
- `.github/workflows/faro-v5-s4-w01-d-commitment-payments.yml`

## B5

Rechazo dentro del scope antes de mutar para cuota ausente/cancelada, deuda no vinculada, moneda distinta, gasto de especie incorrecta, límite excedido y suma stale. Verificar desbordamiento con aritmética exacta y que una fila restaurada incoherente cierre el acceso al pago. ASIENTO único al repetir intento; demostrar rollback conjunto provocando fallo entre gasto y enlace, y al reabrir consultar vínculo y recibo originales. Dos pagos de 35/65 para una cuota de 100, mostrando un pendiente de 65 tras el primero y 0 sólo tras confirmar el segundo. Negativas ante tercero, repetición, fecha pasada sin pago, cancelación y grafo no ACTIVE9. CI JVM exacta con JUnit; revisión final independiente de SHA, diff y XML. Este permiso no incluye edición de calendario, lectura UI, presupuesto, datos bancarios, ADB, V2 productiva ni W2.
