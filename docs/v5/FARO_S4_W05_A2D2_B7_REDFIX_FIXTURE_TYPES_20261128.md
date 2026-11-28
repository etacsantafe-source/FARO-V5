# FARO V5 · W05-A2D2 · B7 RED_FIX de tipos del fixture

## B7 · Evidencia y delta

- SHA `5a873bcea56f920b59e88909100cecbe1db3e4a7`, guardia 46118498033 SUCCESS. CI 46118498085, job 140897548890: app JVM 18/19; la suite nueva falla antes de restaurar. ZIP original 15252927272, XML `S4W05StockReadGlobalDevAcceptanceTest`: `NoClassDefFoundError: error/NonExistentClass` en la llamada del helper tipado `captureBase` (línea 49).
- El tipo interno de snapshot no debe quedar en la firma del helper entre módulos. Reutilizar el patrón probado de `S4W05StockConsumptionDevApplyHostTest`: helper captura bytes, desencriptado y tipos internos inferidos dentro del caso. Mantener las filas internas del fixture en variables locales. Conservar las mismas comprobaciones de secuencia, autenticación, idempotencia y revocación.
- Corrección exacta de `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W05StockReadGlobalDevAcceptanceTest.kt`. Sin cambio de producto, API pública ni dependencia. B7 pendiente de CI global y Android API35 del nuevo SHA.
