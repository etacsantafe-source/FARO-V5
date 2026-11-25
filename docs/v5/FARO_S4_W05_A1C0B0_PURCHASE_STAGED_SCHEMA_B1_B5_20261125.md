# FARO V5 · W05-A1C0B0 · Reality12 compra confirmada, esquema STAGED

## B1 — Evidencia y objetivo

Base auditada: 1c134df981bb8fdd4aaea78730b41d758d1f252b, guardia 45535482279 SUCCESS.
A1C0A B7 PASS acotado, CI 45534939547, XML 8/8. El puerto confiable no tiene
implementación. Reality11 guarda recepciones y cantidades ya recibidas, no
cantidades compradas. Los tipos de carrito W04 y presupuesto observado no
expresan compras confirmadas. A1C0B0 prepara el esquema de destino en una copia
STAGED; ningún host abre ni activa Reality12 en esta unidad.

## B2 — V4.4 y reutilización

P02 L11/L12 y P03 R11/R12 separan carrito, pago y recepción. Conservar esa
separación. Reusar checkSchema, checkIntegrity, readRows y validate de
RealityHousehold11ReceiptRowsPort para verificar la fuente, y el patrón
transaccional de RealityHousehold11ReceiptSchema sin abrir ruta nueva de host.
Reality11 permanece con su esquema exacto probado. La autoridad futura pertenece
a Household y la recepción conserva su permiso separado.

## B3 — Investigación y decisiones

SQLite permite asociar una versión explícita de aplicación al esquema mediante
user_version. BEGIN IMMEDIATE evita validar una versión y promover otra tras
una escritura concurrente; la operación se revierte al fallar. La guía de
migraciones de Room exige probar tanto el esquema como la conservación de datos.
El proyecto usa SQLite directo para estas migraciones STAGED; se aplican esos
principios sin incorporar una dependencia.

- https://www.sqlite.org/pragma.html#pragma_user_version
- https://www.sqlite.org/lang_transaction.html
- https://developer.android.com/training/data-storage/room/migrating-db-versions

Se eligió Reality12 y un payload futuro FBS2v9 con compras y recibos de una
misma vista. FBS2v8 queda congelado. Una importación v8 conserva sus recibos y
parte con compras vacías. Descartados campos reutilizados de recibos, Evidence
como fuente de compra, persistencia lateral y DDL bajo SQL11. La unidad de
respaldo debe probar autenticación FAROBAK2 y restauración antes de la apertura
de escritura DEV. El nuevo esquema usa referencia única de línea, artículo,
cantidad entera en milésimos, unidad, principal confirmante, hora de
confirmación, procedencia manual y revisión positiva. No crea hechos por
migración; la persistencia autenticada y la autorización se prueban aparte.

## B4 — Alcance B6 exacto

- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold12PurchaseSchema.kt
- v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold12PurchaseSchemaTest.kt
- .github/workflows/faro-v5-s4-w05-a1c0b0-purchase-schema.yml

Objeto interno sin llamador runtime. Requiere conexión SQLite de una copia DEV
STAGED proporcionada por su llamador de confianza. En una transacción verifica
versión exacta SQL11, esquema de recibos, integridad y contenido canónico.
Crea solamente una tabla STRICT de compras confirmadas y fija user_version=12.
Verifica integridad antes de COMMIT. Rollback ante error. No agrega relación
obligatoria retroactiva para recibos de SQL11, porque la procedencia histórica
se desconoce. La colisión de referencia se rechaza al nivel SQL. Un futuro
writer revalida principal, revisión, consentimiento y puntero en la
transacción. El esquema por sí solo no prueba confirmación humana.

## B5 — Aceptación previa

JUnit exacto 6: migración de copia preserva fuente, necesidad, finanzas y
recibo sin inferir compra; falla antes de COMMIT revierte tabla y versión;
versión incorrecta y reinvocación se rechazan; integridad y esquema de recibos
incorrectos se rechazan; datos inválidos o duplicados se rechazan por SQL;
reapertura conserva un hecho válido de fixture. CI exige XML original 6/6 y
guardia del SHA. Pruebas futuras de A1C0B incluyen FBS2v8→v9, round-trip
autenticado con compras y recibos, acceso exclusivo DEV y alta manual
autorizada. Stock productivo, V2 productiva, pagos y Auto/Noche OFF.
