# FARO V5 — W05-A2C0B3 FBS2v12 SQL15 staged replay — B7 RED_FIX, NO PASS

Fecha 2026-11-26 (Argentina). Unidad `S4_W05_A2C0B3_AUTH_FBS2V12_FIVESTORE_SQL15_STAGED_REPLAY_DEV`; rama `work/s3-night-20261102`.
**Código auditado:** `0c19acc82071e0dcffddf18fcb01eb196c0cebe3`, exact5 rutas admitidas, preauditoría B1–B5 publicada en `3770a2af3b84a0a8982c84a478e30dbb603cf1c0`, permiso READY `d29b83fce93688838e70161aab97526a410d4e62`.

## B7 — RED_FIX

- Guardia 7 bloques del código **45881273251 SUCCESS**, preflight exact5 de la CI dirigido PASS.
- CI dirigida https://github.com/etacsantafe-source/FARO-V5/actions/runs/45881273340, job `140120919975`, **FAILURE antes de ejecutar tests**. Original XML nuevo no existe / no acreditado; suite SQL15 regresión y verificación XML fueron SKIPPED. No afirmar B7 PASS.
- Diagnóstico REAL del log GitHub Actions: `:store:generation:compileKotlin` `NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY`, línea 141 de `FaroV5Household15V12StagedReplay.kt`; el método `restoreVerified` con body `{ }` construye `FiveStoreStagedRestoreResult.StagedVerified(...)` al final del `try` sin expresión `return`.
- **Solución mínima** sin cambiar contratos: añadir `return` al resultado `FiveStoreStagedRestoreResult.StagedVerified(stage.descriptor.generationId.value)` dentro de `try`, de modo que el `finally` continúe borrando `originalArchive`.
- Segundo cambio exclusivamente instrumental: el workflow en el commit correctivo debe admitir subconjunto NO VACÍO de la lista exact5 del permiso padre (archivo Kotlin corregido + workflow), y ejecutar el preflight de esas rutas. No modificar el permiso/otras rutas para simular el exact5 original.
- Pruebas esperadas nueva ronda: `FaroV5Household15V12StagedReplayTest` 8, `FaroV5Household14V11StagedReplayTest` **5** (corrección de la estimación de la preauditoría), `FaroV5Household15Fbs2CodecTest` 8, `RealityHousehold15ConsumptionRowsPortTest` 7. Total candidato **28/28** original XML, aún NO EJECUTADO. El workflow corregido debe verificar los cuatro XML con cero failures/errors/skipped.
- `last_code_review.result=RED_FIX` únicamente para este SHA y unidad; conserva `status=READY` y el mismo `paths` exact5. Guardia posterior del documento y del permiso obligatoria ANTES del siguiente commit funcional de la misma unidad.
- Sin modificación de ARCHIVE v12, base v11, compatibilidad históricas, ni permisos SQL15 ACTIVE/cutover; V2 productiva, stock real, pagos, Auto/Noche OFF; main/V4.4/G15 físico intactos.
- Si falla nuevamente: otra RED_FIX de la **misma unidad**, con error real/log/XML, sin abrir A2C0C ni declarar cierre global.
