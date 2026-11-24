# FARO V5 — F2C1B1B1B1B B7 RED_FIX2 (historial de un solo comercio)
2026-11-23. Estado **RED_FIX**, no PASS. Unidad S4_W04_F2C1B1B1B1B_NATIVE_EXPLICIT_HISTORY_D4E_DEV.

## B7 — Evidencia real del SHA 01f6009d41f95617d0425042dc6f02270c3f1a1b
- Guardia `45228286436`: SUCCESS; B6 corrección W_selected y W_full publicada en `01f6009d41f95617d0425042dc6f02270c3f1a1b`.
- CI Android API35 `45228286538`, job `137880629740`: FAILURE. Build OK, 3/3 métodos instrumentados, **t05 PASS; t04 FAIL line 344; t06 FAIL line 393**; ambos llegan al tap y fallan porque D4/E no se muestra. No equivale a PASS 1/3 global.
- Diagnóstico acotado a código real: fuente tiene una sola observación comercial `sku-typed` de comercio local; D1 la clasifica `HISTORICAL_PARTIAL` por ausencia de segundo comerciante. F1 `S4W04CommercialLiveReadRecheck.recheck` requiere `COMPARABLE_OBSERVED_ONLY` (dos comerciantes) antes de comparar candidatos, y por eso niega aunque la observación individual aún sea válida.
- Nuevo B1–B5 específico: `docs/v5/FARO_S4_W04_F2C1B1B1B1B_SINGLE_MERCHANT_REAUDIT_B1_B5_20261123.md`. Corregir compuerta F1 sin forzar datos ni relajar verificación por observación. Nuevo READY exact5; en B6 tocar solo F1 + filtro de workflow (2 rutas). Ejecutar **solo** Android t04/t05/t06, revisar XML, APK offline y guard. Si nueva CI no pasa, permanecer RED_FIX.
- Mantener previos certificados, W_full original/seleccionado, no precios vigentes, no compra/Finance/Apply ni V2 productiva.
