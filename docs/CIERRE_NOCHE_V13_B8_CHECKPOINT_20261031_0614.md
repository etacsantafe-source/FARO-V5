# FARO V5 — V1.3 Noche B, B8 / turno 06:14 — 31/10/2026

**Estado:** CHECKPOINT DURABLE / S2.A6 OPEN 3/8 / V2 OFF / S3-S4 CLOSED / T4 NOT_RUN.
**Repositorio privado:** etacsantafe-source/FARO-V5. **Rama única:** work/cierre-noche-v13-20261030.
**HEAD de código:** 2bf2e65ce61827a62a2e79f34db3c2e4177f5624.
**Writer:** epoch 33, owner NOCHE_B_V13_TURN6_20261031_0614; lease adquirido mediante CAS desde 5ee4ad1cf15e29089e19985227cbaeb5d5271018. Este checkpoint acompaña el commit de liberación; verificar GitHub HEAD y writer NONE después del CAS.

## Trabajo funcional F3

Commit **2bf2e65ce61827a62a2e79f34db3c2e4177f5624**: el host Android de consentimiento SAF mantiene registro por identidad de cada CharArray de contraseña temporal, incluso cuando un trabajo todavía está encolado. Al destruirse Activity, cancela trabajo y limpia todos los secretos registrados; evita que shutdownNow descarte un Runnable sin ejecutar su finally de limpieza. Cada salida normal, error o cancelación también elimina el secreto del registro y lo sobrescribe.

Archivos:
- v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5FiveStoreSafConsentActivity.kt
- v5/app/src/androidTest/kotlin/ar/com/faro/v5/app/FaroV5F3SafConsentInstrumentationTest.kt
- .github/workflows/faro-v5-v13-f1-functional.yml

Nueva regresión Android: destroyWipesQueuedOrInFlightBackupPassphrase; oráculo exacto actualizado de 10 a 11 casos. Revisión estática dirigida: **11 nombres de test presentes, 11 nombres en oráculo, sin ausentes ni extras**, rutas de limpieza y registro inspeccionadas. **No se ejecutó compilación ni instrumentación**: no confundir revisión estática con PASS.

## CI y bloqueo

GitHub Actions [#41188045885](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41188045885), SHA 2bf2e65ce61827a62a2e79f34db3c2e4177f5624: FAILURE 2026-10-31 09:16:50–09:16:53 UTC, **3 segundos wall**, los 3 jobs runner_id=0, pasos=0, XML=NOT_RUN. Auto-disparada por push, no reintento manual. Patrón previo #41177654138, #41179478414, #41181395040, #41183601361 y #41185851309: fallo antes de asignación; causa de elegibilidad/cupo no confirmada. No cambiar facturación ni relanzar a ciegas. No Actions no terminales observadas en el listado reciente.

## Criterios S2.A6 congelados

1 FIVE_OWNERS CERTIFIED; 2 FBS2_ISOLATED_RESTORE CERTIFIED; 3 JOURNAL_REPLAY_WAL CERTIFIED; 4 F1_ANDROID_CHAT_E2E OPEN; 5 F2_ANDROID_ACTIVATION_RECOVERY OPEN; 6 F3_FIVE_STORE_BACKUP_SAF_CONSENT OPEN; 7 F4_INTEGRAL_REGRESSIONS OPEN; 8 T4_PHYSICAL NOT_RUN. **3/8**. Android emulador no es físico. V2 OFF, sin cutover, S3/S4 no abiertos.

## Continuidad

- Verificar runner elegible/Actions disponibles por medios READ_ONLY y la causa del fallo pre-runner antes de lanzar más CI. Cuando haya runner, compilar y ejecutar oráculo Android 11/11 sobre SHA exacto, además de pruebas de proveedor SAF real; no reclamar PASS mientras no exista XML.
- F1 sigue requiriendo E2E de proveedor remoto real consentido/Keystore; no insertar secretos sintéticos como evidencia real. F2 recuperación, F3 restore con journal y picker, F4 regresiones y T4 físico pendientes.
- Noche A se observó deshabilitada y sin ejecución activa; Noche B es la ejecución actual. Informe READ_ONLY de las 07:00 se conserva.
- Tiempo observado hasta checkpoint: **137 segundos**; no se simuló turno de 30 minutos.
- No se tocaron main, repo histórico, V4.4, FARO Auto, secretos, visibilidad, pagos, merges ni publicaciones.
