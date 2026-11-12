# FARO V5 — X01 Activity Receipt B7 RED_FIX — XML instrumentation start/end cardinality
Fecha AR 2026-11-11. Unidad `S3_A5_7_X01_REALITY_COMMITTED_ACTIVITY_ANDROID_SCOPED`. Código auditado `cc112b1074bab80e75d0e4c75bff43b460f19c11`, sólo dos rutas permitidas. **B7 RED_FIX**, NO PASS CI global.

## B1 — Evidencia exacta, sin repetir histórico
- CI Android API35 [run 43133141939](https://github.com/etacsantafe-source/FARO-V5/actions/runs/43133141939), job `130901920802`, estado `COMPLETED FAILURE`, source SHA exacto `cc112b1074bab80e75d0e4c75bff43b460f19c11`. Guardia [run 43133141835](https://github.com/etacsantafe-source/FARO-V5/actions/runs/43133141835) SUCCESS mismo SHA.
- Pasaron compilación V2 DEV + AndroidTest, identidad/manifiesto sin INTERNET y firmas APK, emulador Android35 sin datos/red. **Paso Android real PASS**: `OK (1 test)`, `INSTRUMENTATION_CODE: -1`, `S3_A5_7_X01_REAL_RECEIPT_ACTIVITY_PASS 1/1` y `INSTRUMENTATION_STATUS_CODE: 0` exactamente una vez. Test `x01CanonicalRealityReceiptVisibleOnlyAfterForegroundReadAndRevokedOnPause` ejerció Reality COMMITTED sintético real, lectura Activity con gesto, procedencia y revocación; eso NO convierte la CI global FAILURE en PASS.
- Falló únicamente el paso `Verify actual instrumentation events and produce traceable JUnit XML`: `AssertionError` en Python `<stdin>` línea 8. Inspección independiente del artifact ZIP `13906008980`, digest recalculado `df73eaf69cb163563d620dc2eefd962ca69ae9589689b21dddbb7adc1393d8cf` igual al reportado por runner; log contiene **dos** eventos `INSTRUMENTATION_STATUS: test=x01CanonicalRealityReceiptVisibleOnlyAfterForegroundReadAndRevokedOnPause` (uno inicio, uno fin) y **un** `INSTRUMENTATION_STATUS_CODE: 0` (resultado). El workflow asume erróneamente `re.findall(...) == [expected]` y falla con dos reportes del mismo nombre.
- ZIP 9 archivos, APK DEV SHA256 `aed3bde5abd1c5a1cfe20cd76f80d26fbfa337aa072422262b21a73350d1b22b`; APK test `a6f77c7989c7df83577cb905b3ffb8c638c7b60de67415f075b699d913f99ecb`. Hashes coinciden con el manifiesto SHA del ZIP. XML NO producido en este primer run. No repetir pruebas X01/X02 3/3, X06 2/2, X08 1/1, X12 previas.

## B2 — Comparación y causa
- Referente V4.4: recibo durable y auditoría de efecto real; no hay nueva falla en receipts/Reality ni en la UI, y no se reescribe legado.
- El workflow X10 previo usa `set(observed) == expected` y cuenta separadamente `INSTRUMENTATION_STATUS_CODE: 0`. Su log real tiene dos eventos de nombre por test y sólo uno de terminación; este comportamiento no es fallo Android. El nuevo workflow X01 introdujo regresión de **parser de CI**, no regresión productiva.
- Diferencia causal: nombres repetidos por par start/end. Se debe verificar conjunto de nombres exactos y cantidad de finalizaciones, no número de mensajes de inicio+fin.

## B3 — Alternativas investigadas
- USAR el patrón CI X10 ya certificado: `set(observed) == {expected}`, `len(completed)==1`, nombre esperado presente, `OK (1 test)` y `INSTRUMENTATION_CODE:-1`; la salida de instrumentación Android tiene inicio y fin de un caso.
- REFERENCIA fuente oficial AndroidX `https://developer.android.com/guide/components/activities/testing` y Compose `https://developer.android.com/develop/ui/compose/testing/interoperability` para pruebas híbridas View/Compose; ninguno justifica ignorar un test fallido.
- DESCARTADO eliminar la validación XML o aceptar solo `OK (1 test)` sin eventos; descarta verificar recibos físicos nuevos. DESCARTADO editar Kotlin, Kotlin Gradle, stores, permisos o apps: el gesto real y el receipt ya PASS.
- No introducir librerías ni red.

## B4 — Delta mínimo
Modificar **solo** `.github/workflows/faro-v5-s3-a5-7-x01-receipt-activity-android-directed.yml` en `Verify actual instrumentation events`, sustituyendo
`assert re.findall(..., result, re.M) == [expected]`
por
`assert set(re.findall(..., result, re.M)) == {expected}`,
manteniendo `assert len(re.findall(... STATUS_CODE:0 ...)) == 1` y el resto intacto.
Una única CI dirigida en nuevo SHA; generar JUnit XML 1/0/0/0, APK verificables, guard SUCCESS, B7 independiente. No tocar test Kotlin ni repetir suites históricas.

## B5 — Adversarial de corrección
- No rebajar el requisito: extra test distinto debe hacer fallar el conjunto, cero/2 finalizaciones deben fallar, `OK (1 test)` y `INSTRUMENTATION_CODE:-1` siguen obligatorios.
- App V2 DEV sin INTERNET, principal/grants/red sin cambio; no alteración del catálogo de 172, datos, commits productivos, main, V4.4, V2 productiva, S4 ni pruebas G15.
- Un único archivo YAML READY para la misma unidad y permiso restringido a esa ruta; nada de reutilizar permiso para otra X. La nueva prueba de Android se rerun **por causa verificable de infraestructura de certificación** en la misma unidad, no por repetición mecánica de los tests históricos.

## B7 — Dictamen y continuidad
**RED_FIX_CAUSAL_ONE_YAML_XML_EVENT_CARDINALITY**, `cc112b1074bab80e75d0e4c75bff43b460f19c11`; Android real UI 1/1 PASS, CI global FAILURE, JUnit XML NOT_CREATED. Guard mismo SHA SUCCESS. El código test-only original queda intacto; permitir corrección un YAML de igual unidad solo tras actualizar `last_code_review` con resultado `RED_FIX`, permiso individual limitado, guard documental SUCCESS y writer CAS. Luego obtener CI global SUCCESS, verificar artifact/recibo y publicar B7 PASS scoped independiente. No proclamar cierre hasta entonces.
