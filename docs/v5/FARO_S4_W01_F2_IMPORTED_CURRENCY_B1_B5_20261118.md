# FARO W01 F2 · Importes de cuentas importadas

## B1

En `447afb3cc941f2726ac722a0226c296d72952f1f`, el formulario Android de W1 admite altas nuevas en ARS y USD. Finance9 puede reabrir cuentas de otros códigos de moneda existentes. `FaroV5W01FinanceForms` aplica dos decimales indiscriminadamente al mostrar saldos e importes, y permite elegir esas cuentas para ingresar montos decimales. Falta respetar los valores heredados cuando la escala elegida sólo se conoce para ARS y USD. Android API35 44385933626 certificó el flujo W1 con ARS y no cubrió este caso.

## B2

P02 L04 y P03 R04 conservan el ledger y las monedas. La superficie existente `FaroV5FinanceReadSurface.kt` exige una escala conocida para mostrar un saldo en forma decimal. La vista de Review F1 ya conserva unidades menores para otros códigos. Reutilizar esa convención en el nuevo formulario nativo sin tocar el ledger, migraciones, respaldos ni la autorización.

## B3

REFERENCIA Android Java NumberFormat. Sus formateadores monetarios aplican convenciones del locale y sirven cuando la escala monetaria ya está definida por el dominio. DESCARTAR inferir desde locale, símbolo o configuración externa la escala de datos persistidos. USAR la escala explícita de dos decimales para ARS/USD y el entero con etiqueta de unidades menores en cuentas importadas de otras monedas. Se comparó también aplicar una tabla automática de escalas en todas las mutaciones. Se DESCARTA por ampliar el contrato y transformar importes sin prueba de migración. Fuente oficial https://developer.android.com/reference/java/text/NumberFormat .

## B4

Mostrar las cuentas importadas en la vista general respetando moneda y unidades menores. El editor decimal sólo ofrece cuentas en ARS/USD y explica el alcance si no hay ninguna elegible. No convertir ni recalcular saldos, moneda, asiento o archivo. Conservar el flujo de Android. El test instrumental agrega comprobaciones explícitas de formato ARS, USD y código importado, y de la distinción entre monedas admitidas y no configuradas.

Rutas exactas:
- `v5/app/src/w01FinanceDev/kotlin/ar/com/faro/v5/app/FaroV5W01FinanceForms.kt`
- `v5/app/src/w01AndroidTest/kotlin/ar/com/faro/v5/app/S4W01FinanceIntegratedAndroidTest.kt`

## B5

Verificar que 125 unid. bajo un código sin escala local no aparezca como 1.25. Mostrar ARS/USD como antes y evitar que un editor decimal llegue a una cuenta excluida por escala. La mutación queda bajo el mismo Review/Apply. Probar el escenario existente API35 y el caso monetario añadido. Mantener S3, W2 y V2 productiva fuera de escritura.
