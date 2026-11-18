# FARO W01 C0 · Transferencia registrada en una transacción Finance9

## B1

Base `b324911839c7d1893d637fea9c80e614bff0584c`, B3B2 prueba de apertura y ledger activo en 44322715808 y 44322715824. `RealityStore.kt` blob `27aadce726e4d3d2dda3746be4e48f1501198057` posee `finance.entry.record` y el scope de Reality SQLite. `FinanceOperations.kt` blob `e4cd61328e22c53795940f376ffd75f27993ef62` aún no expone el ID oficial `finance.transfer.record`; el TSV congelado lo declara N-025, finance, REALITY_MUTATION y BINDABLE. En schema9 están finance_transfer y su trigger de consistencia para dos entries opuestos, sin binding ni persistencia activa. Reusar el diseño ARCH-04 y verificar el salto de DDL a un registro real local con un receipt.

## B2

P02 L04/L17 y P03 R04/R17 demuestran la semántica V4.4 de cuentas, movimientos, idempotencia y atomicidad. Reimplementar sólo el delta transfer en Finance V5. `ApplyTransactionExecutor`, `FinanceEntryRecord`, `RealityStore.recordFinanceEntry`, `finance_transfer` y el receipt de Reality se reutilizan. Catálogo 180 y schema9 vigentes.

## B3

ADAPTAR Actual Budget (local-first, MIT), que enlaza los dos importes invertidos para poder navegar y excluir la transferencia interna de informes: https://actualbudget.org/docs/transactions/transfers/ y https://github.com/actualbudget/actual. USAR la transacción ACID de Reality documentada por https://www.sqlite.org/lang_transaction.html. DESCARTAR dos operaciones de aplicación o dos receipts y posterior reparación, porque una caída puede dejar el débito sin réplica. Tampoco se integra red, FX ni otra contabilidad. La relación de las dos partidas queda explícita en la tabla propietaria.

## B4

Agregar payload canónico para F04 y binding/revalidator según el catálogo existente. Origen y destino distintos, existentes y de igual moneda; amountMinor positivo, fecha y concepto en las dos entradas. Dentro de Reality ACTIVE9 reevaluar saldo y overflow e insertar débito, crédito y enlace en el mismo scope. La Review debe aceptar la advertencia para origen insuficiente o cobertura desconocida, sin presentar un saldo supuesto. Los efectos registran una transferencia ya informada, sin instrucciones bancarias. El host y la UI F04 son unidades posteriores. Alcance exacto:
- `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceOperations.kt`
- `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceTransferRecord.kt`
- `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceTransferRecordBinding.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
- `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TransferApplyTest.kt`
- `.github/workflows/faro-v5-s4-w01-c0-transfer-atomic.yml`

## B5

Prueba SQLite real con executor y receipt: ambos efectos, enlace y receipt tras reabrir; reintento no duplica; fallo forzado en segundo efecto revierte el débito; denegar cuentas/monedas incompatibles, overflow, payload no canónico y cobertura desconocida o saldo insuficiente sin aceptación. Usar identificadores deterministas para los dos efectos, limitar el port a ACTIVE9 DEV y preservar STAGED/legacy. El futuro host aprueba la operación canónica y muestra advertencias; este núcleo no emite permisos ni da acceso Android. Disparadores por ruta JVM y compilación, sin ADB para C0.
