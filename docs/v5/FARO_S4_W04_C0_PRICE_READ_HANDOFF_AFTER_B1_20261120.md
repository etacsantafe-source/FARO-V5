# FARO V5 — Relevo W04-C0 · Lectura local de precios

Fecha local 2026-11-20. B1 global B7 PASS en `docs/v5/FARO_S4_W04_B1_HOUSEHOLD10_GLOBAL_B7_PASS_20261120.md`. Base funcional `52db8e5d7c5c4d4c8f013af5d2d81fd3f29724d8`. Preparación documental de C0. Permiso C0 pendiente.

- Se revalidó `FARO_S4_W04_C0_PRICE_READ_ADVANCE_20261120.md`. B1 ahora aporta snapshot tipado con revisión de necesidades. El mapping verificado `HouseholdNeedId` a `CommercialProductKey` sigue siendo la primera dependencia de C0. Una clave ausente o ambigua requiere aclaración. La selección debe quedar vinculada a la revisión del need.
- Reutilizar `CommercialPriceHistoryReadExecutor` y `CommercialEvidenceStore.priceHistory(productKey, limit)` en `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidence.kt`. El executor ya exige el descriptor oficial de lectura `commercial.price_history.read`, y el store limita a 1..200 observaciones. Este es el lado CommercialEvidence. El lado Household consume su snapshot admitido.
- Presentar precio observado por moneda, comercio y fuente, y mostrar fecha observada y adquisición como datos separados. Cuando falte fecha de observación, localidad o vigencia, mantener esa ausencia en la proyección. La consulta local es de solo lectura.
- Próxima acción exacta: verificar el mapping explícito y el punto de admisión de lectura por owner en el HEAD vigente. Congelar rutas, preflight individual C0 y CI JVM dirigida solo después de completar B1–B5 delta y emitir READY. Mantener C0 fuera del Apply Household y de mutaciones de Evidence, Finanzas o stock.
