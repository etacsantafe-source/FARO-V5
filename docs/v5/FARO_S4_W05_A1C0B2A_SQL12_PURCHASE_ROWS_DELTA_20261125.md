# FARO V5 · W05-A1C0B2A · Puerto transaccional Reality12 STAGED

## B1 — Evidencia y alcance

Se reutiliza `FARO_S4_W05_A1C0B2_SQL12_CAPTURE_RESTORE_B1_B5_PREAUDIT_20261125.md`. A1C0B0 certificó la migración de esquema y la conservación de datos previos. El delta de A1C0B1 incorpora `Household12PurchaseBackupRow` y `FaroV5Household12Fbs2Snapshot`, dos tipos de generation. El puerto Reality debe leer compras, estado de recepciones y recibos dentro de una única transacción y restaurarlos juntos. Su prueba usa SQLite real de AndroidX. Generation aún no dispone de un puerto SQL12 de producción ni de un replay de los cinco almacenes autenticado por el cifrado original de v9.

## B2 — Reutilización

Se mantienen la semántica histórica P02/P03 y el contrato de archivo sellado del puerto SQL11 ya certificado. Reutilizar `Household11ReceiptRowsSql` para validación estructural, integridad, lectura y escritura de los recibos heredados. Extraer los chequeos de tablas y el helper DML de recibos conservando la comprobación de versión exacta en los entry points SQL11. Compartir el DDL de compra desde `RealityHousehold12PurchaseSchema` para validarlo en el puerto nuevo y conservar sus bytes y SQL12. El puerto nuevo entrega un modelo de Reality que A1C0B2B podrá mapear al modelo FBS2v9 sin llevar SQLite a generation.

## B3 — Delta técnico

Usar una conexión read-only sobre archivo STAGED sellado, con BEGIN y una vista para ambas familias de filas. Usar BEGIN IMMEDIATE con foreign_keys y synchronous FULL para el replay, readback dentro de la transacción y comprobación tras cierre y checkpoint. Dos SELECT con autocommit independiente podrían observar estados distintos. El URI immutable solo corresponde a la etapa cerrada sin sidecars que ya exige checkFile. Verificar tablas STRICT, DDL y ausencia de triggers o claves externas incidentes que alteren el replay. Comparar el orden JVM de referencias de compras antes de mapear al v9, incluso para referencias suplementarias que SQLite BINARY ordena por UTF-8. Tratar las cantidades SQLite LONG antes de convertir a Int y sumar en Long.

Fuentes primarias consultadas en este delta. [Transacciones SQLite](https://www.sqlite.org/lang_transaction.html), [URI y modo de solo lectura](https://www.sqlite.org/uri.html), [PRAGMA e integridad](https://www.sqlite.org/pragma.html), [cadenas con NUL y claves](https://www.sqlite.org/quirks.html).

## B4 — Alcance exacto del puerto

1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold12PurchaseRowsPort.kt`
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold12PurchaseSchema.kt`
3. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold11ReceiptRowsPort.kt`
4. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold12PurchaseRowsPortTest.kt`
5. `.github/workflows/faro-v5-s4-w05-a1c0b2a-sql12-purchase-rows.yml`

A1C0B2B encadena la identidad completa, lease DEV, pin del ciphertext original y el replay autenticado de cinco owners. El replay SQL11 actual acepta `AuthenticatedHousehold11RestoreArchive` y pinnea bytes v8. Pasarle una nueva cifra v8 obtenida a partir de v9 generaría otro nonce y otro pin al reintentar. El coordinador posterior requiere una proyección tipada de base autenticada que conserve la identidad del archivo original y mantenga la entrada histórica v8 exacta, o una orquestación equivalente probada. A1C0B2 queda abierto hasta ese B7. Los openers ACTIVE quedan en A1C0B3.

## B5 — Pruebas obligatorias

1. Compra 10 y recibos 7+3 dentro del mismo snapshot, restore, reapertura persistida y replay idéntico.
2. Recibos heredados sin compra preservados y compra vacía explícita.
3. Destino divergente denegado sin modificar sus filas.
4. Excepción antes de COMMIT revierte compras, receipts y revisión.
5. Item, unidad, procedencia, referencia, duplicado, over-receive, cantidad fuera de rango y límites rechazados.
6. SQL11 rechazado por lectura SQL12 y SQL12 por lectura SQL11. Alteraciones de DDL, trigger o integridad rechazadas.
7. Sidecar y enlace rechazados. Orden canónico incluidas referencias suplementarias.
8. Suites dirigidas históricas de migración y puerto SQL11, con XML originales en artefacto para el SHA exacto y guardia SUCCESS.

La identidad STAGED, ABA de generación, evidencia y los cinco owners se certifican en A1C0B2B. Este permiso de Reality no alcanza efectos ACTIVE, Apply, stock real ni montajes Android.
