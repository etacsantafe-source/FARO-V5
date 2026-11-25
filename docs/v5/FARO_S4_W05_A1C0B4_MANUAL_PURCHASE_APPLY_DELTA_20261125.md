# FARO V5 · W05-A1C0B4 · Compra manual N-036 DEV

## B1 — Delta de evidencia

Aplica la preauditoría `FARO_S4_W05_A1C0B4_MANUAL_PURCHASE_APPLY_B1_B5_PREAUDIT_20261125.md`. A1C0B2 certificó la portabilidad v9 de las compras SQL12. A1C0B3A certificó el grafo ACTIVE SQL12 read-only. A1C0B3B tiene B7 PASS en c0fcabc006d021f498c30480e31ee9041f919f8e, CI 45599209365, guardia 45599209368 y XML 16/16. Certificó ticket, journal completado, fence y puntero activo. El catálogo vigente conserva `shopping.purchase.record`, N-036, `household`, REALITY_MUTATION, BINDABLE, MERGE. La compra se registra por acción manual explícita y no inicia un pago ni una recepción.

## B2 — Reutilización

Usar el flujo de `S4W04HouseholdNeedApplyHost` y `ReviewedConversationApplyCoordinator` con Review, identidad revalidada, plan canónico, binding, ejecución, recibo COMMITTED y conciliación. `RealityStore.run` ya asigna la identidad y el scope exacto de SQLite. Mantener los grants de necesidades y stock independientes. No copiar V4.4. Usar referencias históricas P02 y P03 de la preauditoría.

## B3 — Delta de diseño

Abrir únicamente un modo explícito SQL12 Purchase DEV con el lease de root existente y comprobación del ticket SQL12 COMPLETED y del fence del restore antes y después de la apertura. Conservar el grafo read-only y proteger sus transacciones. En modo compra, validar scope y puntero antes de mutar y antes de COMMIT. El binding recibe una aprobación foreground de un Review específico y verifica en el owner un payload con referencia, artículo, cantidad, unidad, principal, epoch y puntero. El alta inmutable conserva MANUAL_EXPLICIT y usa el reloj confiable de la confirmación. La escritura de fila y receipt comparte la transacción AtomicApply, sin cambiar SQL12 ni introducir sus propias tablas de intent. La identidad del intento y fingerprint de plan determinan si corresponde conciliación. Una referencia contradictoria produce conflicto sin reemplazo. Descartar el opener SQL10, convertir carrito o presupuesto en compra, y ampliar stock por backup. [SQLite transacciones](https://www.sqlite.org/lang_transaction.html), [SQLite WAL](https://sqlite.org/wal.html).

## B4 — Rutas exactas

1. `v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdManualPurchaseReview.kt`
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold12PurchaseApplyPort.kt`
3. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
4. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
5. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household12Evidence2DevJournaledCutover.kt`
6. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W05ManualPurchaseDevApplyHost.kt`
7. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W05ManualPurchaseDevApplyHostTest.kt`
8. `.github/workflows/faro-v5-s4-w05-a1c0b4-manual-purchase-apply.yml`

Conservar el API de reapertura read-only. El cambio en cutover solo permite validar el ticket final para el host nuevo. La integración de la UI queda en A1C3.

## B5 — Aceptación

Compra manual 10 con fila única, origen y recibo COMMITTED. Reintento del mismo intent sin duplicar ni volver a consentir un COMMITTED. Referencia contradictoria deja una única fila. Review viejo, pérdida de foreground, puntero ABA, identidad inconsistente, acción fuera de N-036 y apertura previa al journal COMPLETED rechazan. Rollback previo y conciliación después de COMMIT. Comprobar lectura tras volver a abrir. CI dirigida, artefacto XML original, exacto SHA y guardia. N-035 y Undo quedan fuera.
