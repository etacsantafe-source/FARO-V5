# FARO W01 B3B2 · Apertura confirmada y fuente activa Finance9

## B1

Base de código `58d70ffc4d6610cdc854b9cdcdb69e1cf314c02a`. B2D1 JVM 5/5 run 44320103402; guard 44320103204 y regresiones SUCCESS. `RealityStore.kt` blob `087b0ff1f70f75fe78d25155302d6462aa40e23b` omite fila de apertura física para cero y sólo expone la lectura de procedencia STAGED9. El esquema `f278a26dd16f7f8760aa8ceff93a669bf21e4ea7` ya distingue CONFIRMED de LEGACY_UNVERIFIED. El host aprobado `54dbf617f79d86ca02d89bbe75dd83bfeec20c9b` aún no aporta la apertura al grant. F02 exige apertura explícita, origen y ledger con cobertura según P05. Completar el delta de escritura/lectura para revisión F02, manteniendo desconocidos en null.

## B2

Reusar L04/R04 de Finanzas V4.4 respecto de apertura y movimientos y R17 para ligarlos al receipt. Conservar la ruta de balance por owner de V5 y la evidencia B3B0/B3B1B0 de un snapshot WAL. El owner conserva el registro heredado sin inferir apertura confirmada de filas financieras anteriores a Finance9.

## B3

REFERENCIA GnuCash registra el saldo inicial como dato de alta con fecha y procedencia, en https://gnucash.org/docs/v5/C/gnucash-manual/acct-create.html. No adoptar un segundo esquema contable ni una cuenta de contrapartida ficticia. USAR la misma transacción SQLite documentada en https://www.sqlite.org/lang_transaction.html y el snapshot consistente de https://www.sqlite.org/isolation.html. DESCARTAR confirmar desde la UI o escribir un marcador en transacción posterior; los fallos dejarían una apertura sin evidencia o duplicada. Usar exclusivamente recursos existentes y ejecución local.

## B4

Enviar la apertura explícita de la operación F02 canónica al issuer. El grant confirma en la misma transacción del receipt únicamente si cuenta y apertura física coinciden con lo declarado; la ausencia física sólo es compatible con una declaración explícita de cero. La fila guarda el origen del receipt en la columna de procedencia existente. F03 no puede confirmar apertura. Exponer una lectura ACTIVE9 acotada con fence del owner, usando el mismo snapshot SQL y conservando el contrato STAGED. Rutas exactas:
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9ActiveDevGrantPort.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9OpeningProvenanceReadPort.kt`
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B2D1ActiveFinanceApplyHost.kt`
- `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9ActiveDevGrantPortTest.kt`
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B2D1ActiveFinanceApplyHostTest.kt`
- `.github/workflows/faro-v5-s4-w01-b2d0-active-finance9-grant.yml`

## B5

Verificar saldo cero declarado y apertura positiva en el flujo real, persistencia al reabrir, pérdida de admisión y diferencia entre declaración y fila física. Un desacuerdo debe retrotraer cuenta, receipt y consumo. Conservar los negativos STAGED/legacy; sólo el port ACTIVE9 con admisión viva puede observar estas pruebas. Las aperturas migradas y faltantes mantienen null. El callback del owner no consulta Workspace ni abre otro grafo. CI JVM dirigida (B2D0 y B2D1 reutilizadas), sin ADB ni cambio productivo. La Activity y sus confirmaciones siguen fuera de esta unidad.
