# FARO V5 · W05-A1C2A · Evolución STAGED SQL13 para compensaciones

## B1 — Delta de evidencia

Continúa la preauditoría FARO_S4_W05_A1C2_STOCK_UNDO_RECOVERY_B1_B5_PREAUDIT_20261125.md. A1C1 tiene B7 PASS en 0335c7657bc81f3d89a0709508c562f5e1099aac, CI 45605547164, guardia 45605547137 y XML 8/8. El ledger de SQL12 solo admite recepciones positivas y FBS2v9 no tiene sección de compensaciones. El catálogo180 contiene undo.execute N-016, executor undo-coordinator y perfil REALITY_MUTATION. El contrato no autoriza acciones de stock por sí solo. Esta dependencia se certifica antes de asociar N-016 con compensaciones reales.

## B2 — Reutilización

Reusar migraciones SQL11→SQL12 en copia cerrada STAGED, controles canónicos SQL12, validación de integridad y checkpoint WAL. La proyección conserva compras y recepciones originales byte a byte lógicamente. Conserva referencias históricas P02/P03. [SQLite transacciones](https://www.sqlite.org/lang_transaction.html), [SQLite WAL](https://sqlite.org/wal.html).

## B3 — Delta de diseño

Agregar una tabla STRICT de compensaciones append-only en SQL13. La relación única apunta a un receipt físico original e identifica intent, compra, cantidad, unidad, principal, revisión y fecha de registro. Validar enlace, procedencia y coherencia con la compra histórica. Una compensación revierte íntegramente una recepción seleccionada. El estado revisionado del ledger aumenta cuando se aplica la compensación, sin alterar filas previas. Los lectores SQL12 conservan su fence exacto; habilitar un verificador SQL13 separado. El alta N-016 y su grant quedan para una unidad separada después de la nueva versión autenticada y el restore. Mantener el formato SQL12 y FBS2v9 históricos.

## B4 — Rutas exactas

1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold13CompensationSchema.kt`
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold13StagedFilePort.kt`
3. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold12PurchaseRowsPort.kt`
4. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold13CompensationSchemaTest.kt`
5. `.github/workflows/faro-v5-s4-w05-a1c2a-sql13-compensation-schema.yml`

La ruta compartida SQL12 permite reutilizar inspección y lectura de registros dentro de una transacción ya abierta; el método SQL12 verifica exactamente la versión 12. V10/Capture/Restore/ACTIVE/Apply reciben permisos propios.

## B5 — Aceptación

Preservar filas y revisión histórica tras migrar 12→13 en copia STAGED. Rollback de DDL y user_version ante interrupción. Rechazar versión ajena, esquema divergente, enlace incorrecto, usuario incorrecto, unidad y cantidad inválidas, intent duplicado, 2º Undo de un mismo recibo y UPDATE/DELETE de compensación. Comprobar reapertura de fixture y que el puerto SQL12 sigue rechazando SQL13. CI y guardia por SHA, artefactos JUnit originales. Sin cambios productivos.
