# FARO W01 F2 · Finanzas en la sesión Android DEV

## B1

Base de integración `0ad128358157b9411d7eabb7a18296de7acee200`. P05 PF03–PF09 y ARCH-05/06 requieren navegación desde Chat, cuentas, movimientos, transferencias, compromisos y presupuestos con lectura real, revisión, confirmación, recibos y recuperación. D1B1B y D1B1C2D ya montan la Activity nativa y una única sesión Finance9 DEV con handoff in-process de un solo uso. `FaroV5W01FinanceReadDevActivity` aún muestra cuenta informativa usando el préstamo de lectura. F1 conecta Review/Apply y sus bindings a Reality ACTIVE9 en la app y debe tener B7 JVM antes de READY. La evidencia Android final de F02–F06 debe corresponder al nuevo SHA.

## B2

Reutilizar P02 L04/L05/L17 y P03 R04/R05/R17 para mantener navegación directa, moneda y fecha visibles, revisión explícita y receipt. Conservar handoff, Activity nativa, insets, sesión, autenticidad del proceso, guardianes de generación, Workspace y consumo atómico de grant. La app aislada usa su grafo ya abierto por la sesión. Planning8 conserva la lectura informativa existente.

## B3

- Actual Budget (MIT, local-first) muestra detalle de movimientos por categoría desde el presupuesto móvil. ADAPTAR la lectura separada de gastos y compromisos y las entradas directas de formularios. DESCARTAR overlay web, backend y sincronización adicional. Negativa: el detalle y la revisión deben mantener la moneda y categoría de origen. Fuentes: https://actualbudget.org/docs/releases/ , https://actualbudget.org/docs/budgeting/ , https://github.com/actualbudget/actual .
- Android Views, framework ya usado y guía oficial, recomienda áreas táctiles de 48 dp, etiquetas conectadas a campos y prueba con fuente de hasta 200%. ADOPTAR controles nativos con textos reales, encabezados, desplazamiento y labels. Android puede restaurar estado serializado tras recreación. DESCARTAR guardar campos de datos financieros, tokens o aprobaciones en Bundle. Negativa: volver desde Home o recrear no reaplica ni revive la autorización vieja. Fuentes: https://developer.android.com/guide/topics/ui/accessibility/views/apps-views , https://developer.android.com/about/versions/14/features#non-linear-font-scaling , https://developer.android.com/topic/libraries/architecture/views/saving-states-views .

## B4

Montar en w01FinanceDev pantalla nativa y acciones para cuenta, ingreso/gasto, transferencia, calendario/deuda/cuota, pagos parciales y presupuesto; mostrar datos bajo el préstamo de sesión ya válido. El revisor existente recibe payload tipado; cada botón de aplicar opera después de estado APPROVED y confirma con grant atómico. Cancelar en Workspace y limpiar los controles. La restauración y reanudación consultan receipts persistidos y nunca reintentan automáticamente. Reusar una conversación activa de Workspace y crear una local sólo si falta. El callback de la sesión valida UID/aplicación esperados, foreground, identidad de préstamo y puntero. Las lecturas Finance9 usan puertos del owner admitido, preservan ARS/USD, apertura verificada, vencimiento y estimación desconocida. El emulador API35 debe ejecutar la navegación y formularios reales. Mantener V2 productiva y seguridad opcional OFF. Acceso físico prohibido.

Rutas exactas para preflight:
- `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthority.kt`
- `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/S4W01F2FinanceSessionActions.kt`
- `v5/app/src/w01FinanceDev/kotlin/ar/com/faro/v5/app/FaroV5W01FinanceDevActivities.kt`
- `v5/app/src/w01FinanceDev/kotlin/ar/com/faro/v5/app/FaroV5W01FinanceForms.kt`
- `v5/app/src/w01AndroidTest/kotlin/ar/com/faro/v5/app/S4W01FinanceIntegratedAndroidTest.kt`
- `.github/workflows/faro-v5-s4-w01-f2-finance-integration-android.yml`

## B5

Verificar cuentas e ingresos/gastos, transferencia con ambas partidas, deuda/calendario/cuota y pago parcial, límite y meses/monedas. Comparar lo presentado con owner y receipt. Cancelación, confirmación repetida, cierre/reapertura con recuperación de receipt, fecha efectiva, negativa de permisos y abandono del préstamo, aumento de fuente y nombres accesibles. Verificar variante DEV sin egress y sin cambios de main, V2 productiva, W2 o scopes STAGED. La certificación W1-F requiere CI/XML exactos y auditoría final del alcance completo.
