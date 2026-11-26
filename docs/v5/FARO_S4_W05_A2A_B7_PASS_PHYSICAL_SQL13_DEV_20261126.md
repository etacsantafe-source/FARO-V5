# FARO V5 · W05-A2A — Stock físico neto SQL13 DEV, B7 PASS

Fecha: 2026-11-26. Unidad `S4_W05_A2A_SQL13_PHYSICAL_STOCK_SNAPSHOT_DEV`. Código `ce30bb6f5498a52b70b74decb15a490171923fe1`. HEAD de código B6 con exactamente las tres rutas autorizadas por permiso READY exact3 y B1–B5 en `docs/v5/FARO_S4_W05_A2A_SQL13_PHYSICAL_STOCK_SNAPSHOT_B1_B5_20261126.md`. No hay cambios de A1, Android, runtime productivo ni esquema SQLite.

## B7 — PASS scoped DEV (JVM + SQLite13 real)

- [Guardia código 45746937952](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45746937952) **COMPLETED SUCCESS** sobre `ce30bb6f5498a52b70b74decb15a490171923fe1`. Incluye 14 casos sintéticos del protocolo y preflight READY exact3 en el padre del commit funcional.
- [CI dirigida 45746937979](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45746937979) **COMPLETED SUCCESS** sobre `ce30bb6f5498a52b70b74decb15a490171923fe1`, `:store:generation:test --tests ar.com.faro.v5.generation.FaroV5Household13DevStockSnapshotTest`. Verificación estricta de XML original SUCCESS.
- [Artefacto 15083439673](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45746937979/artifacts/15083439673), ZIP original descargado. **SHA-256 ZIP** `f2374ce12fafcfea04c5840b4c8c9c03abcc44ed47e2fc7a57f0ef3571b57ade` y SHA-256 XML `69400e9c0aec49c2eb2e02b536d08ce87f0c3da993c1bf804bcce1b546347788`; digest ZIP coincide exactamente con GitHub.
- XML `TEST-ar.com.faro.v5.generation.FaroV5Household13DevStockSnapshotTest.xml`: **5/5 PASS, failures=0, errors=0, skipped=0**. Se verificaron nombres de los cinco casos originales, no un resumen de log inventado.

### Matriz observada 5/5

1. `realSql13ReceiptAndCompensationYieldPhysicalThreeWithAvailabilityUnknown()`: generación SQLite13 STAGED → ACTIVE DEV real, compra 10 UNIT, receipts 7+3 y compensación 7 → físico neto **3 UNIT**; reservado/consumido/disponible **NULL/UNKNOWN**. Reapertura devuelve la misma proyección; puntero no cambia.
2. `multiplePurchasesSameSkuAggregateButUnitsRemainSeparate()`: agrupación por SKU+unidad; compras múltiples UNIT suman correctamente, GRAM separado y sin conversiones tácitas.
3. `invalidLinkDuplicateReceiptAndOverReceiveFailClosed()`: fuente sin receipt, ID duplicado y recepción sobre cantidad comprada → proyección rechazada; no stock ficticio.
4. `revokedGrantFullPointerAbaAndExclusiveLeaseFailClosed()`: tras revocación de ownerAdmission y full-pointer ABA, proyección NULL; rechazo de lease concurrente.
5. `emptyAndFullyCompensatedReceiptsNeverInventAvailability()`: libro vacío no crea stock; receipt totalmente compensado = físico 0 verificado, disponible continúa UNKNOWN.

### Auditoría de alcance y cierre

PASS de **A2A** significa una **proyección read-only** del saldo de recepciones físicas netas por SKU+unidad, derivada desde el host SQL13 DEV certificado y sin mutaciones. **NO es stock disponible**, ni ledger de consumo, ni reserva, ni compra pagada. No hay cambios de schema, catálogo, grants, recibos, kernel, Reality, backup/restore, Android, V2 productiva o Finance. La ausencia de reservas/consumos no se interpreta como 0; los tres campos son NULL.

No se ejecutó una campaña física G15; no hay APK nuevo requerido para este alcance JVM. A1 permanece B7 cerrado DEV. **W05-A2 y W05 completo siguen OPEN.** Próxima unidad **A2B0** esquema de reservas y backup, con B1–B5/permiso propios, antes de `stock.reserve` o `stock.release` reales. No heredar permiso A2A. V2 productiva, stock productivo, pagos, Auto/Noche OFF; main y V4.4 intactos.

Permiso A2A consumido/BLOCKED después de B7. Writer CAS debe volver a RELEASED, época 2059, checkpoint durable y guardia final sobre su SHA.
