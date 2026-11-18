# FARO W01 F1 · Review y Apply integrados de Finanzas DEV

## B1

Base `16f06446fa9b7f8fa4b0bcbe09234c603b8626ed`. P05 PF03–PF09 y ARCH-05/06 requieren revisión explícita, efecto único, recibo real, cierre/reapertura y lectura compartida del mismo grafo. B2D1 y B3B2 ya prueban que alta de cuenta y asiento pasan por Workspace Review aprobado, grant ACTIVE9, AtomicApply y receipt. `S4W01B1FinanceReviewHost`, el lector de alcance aprobado y `finance9ActiveDevGrantIssuer` sólo admiten cuenta y asiento. C0, F05 y F06 tienen puertos de dominio y CI JVM. Se requiere extender la ruta ACTIVE9 para usar dichos puertos antes de montar formularios y lectura Android; ésta es la primera parte de W01-F. Revalidar guard y B7 de calendario antes de READY.

## B2

P02 L04/L17 y P03 R04/R17 preservan el gateway de Finance y la secuencia Review/Apply/recibo. V5 conserva el owner y la autorización del grafo ACTIVE9, que ya almacena el grant y el recibo atómicamente. Se reutiliza la revisión tipada y su persistencia en Workspace. Se conserva la ruta certificada de cuenta y asiento. El lector usado por las rutas STAGED mantiene sus alcances de cuenta y asiento. Los criterios existentes de aislamiento de W01 siguen vigentes.

## B3

- ERPNext separa revisión de efecto contable. La aplicación local puede mostrar cuentas e importe previos al registro. ADAPTAR el dato concreto por operación, sin ampliar al servidor GPL-3, roles de pago, FX ni transferencias bancarias. Negativa: un texto de revisión que indique un monto distinto al payload no debe constituir aprobación. Fuentes: https://docs.frappe.io/erpnext/payment-entry , https://github.com/frappe/erpnext-14/blob/develop/license.txt .
- GnuCash v5 GPL-2/3 presenta efectos y permite asociar una obligación a un pago explícito; su lógica de asignación puede interpretar mal reintegros. REFERENCIA para mostrar cuenta, moneda, monto y cuota en Review, y exigir payload positivo validado por el owner. DESCARTAR front-end contable, importación y dependencias. Negativas: doble confirmación y cuota/gasto de una moneda ajena. Fuentes: https://gnucash.org/docs/v5/C/gnucash-manual/busnss-ap-payment.html , https://github.com/Gnucash/gnucash/blob/stable/LICENSE .
- USAR el plan y recibo persistentes V5 sobre SQLite, manteniendo el paso Android por separado. Restaurar UI no restaura permisos ni vuelve a ejecutar una operación. Fuente: https://developer.android.com/topic/libraries/architecture/views/saving-states-views .

## B4

Agregar una lista explícita de mutaciones desacopladas y admitirlas sólo para ACTIVE9 DEV. La propuesta tipada decodifica el payload canónico, deriva en el host el resumen revisable y conserva un solo nodo con el contrato completo canónico. Cuenta y asiento conservan su preparador previo. El lector compara generación, revisión, fingerprint, emisor, capacidad y catálogo; ampliar el alcance sólo mediante parámetro explícito a la ruta ACTIVE9, sin cambiar la lista STAGED. Derivar scope por ID de transferencia, deuda/calendario/cuota o clave de presupuesto única, sin que la UI provea scope ni descriptor. Someter la transferencia al revalidador existente y las demás a su decoder y precondiciones SQL dentro de la transacción. El host usa el mismo Review, gate, Reality, receipt y recuperación committed-only, admitiendo apertura confirmada sólo en la creación de cuenta. Registrar casos JVM con grafo Finance9 real a través de Workspace, antes del montaje Android separado.

Rutas exactas para preflight:
- `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceOperations.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B1FinanceReviewHost.kt`
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B2FinanceApprovedScope.kt`
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B2D1ActiveFinanceApplyHost.kt`
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01F1FinanceReviewOperations.kt`
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B2D1ActiveFinanceApplyHostTest.kt`
- `.github/workflows/faro-v5-s4-w01-b2d1-active-finance9-review-apply.yml`

## B5

CI JVM con F02/F03 existentes y nuevos casos de transferencia, cuota/pago y presupuesto. Verificar cancelación o pending sin efecto, descriptor o payload alterado, nombre de review derivado, denegación al perder foreground y scope exacto, misma moneda en transferencia, retry/receipt, ausencia de egress, rollback de gasto/asignación/grant y ningún segundo grafo. Cualquier vía de reconciliar una aprobación sin receipt permanece sin Apply. Los tests Android API35 de formularios, navegación, fuente a 200% y accesibilidad pertenecen a la siguiente unidad. W2 STOP y V2 productiva OFF.
