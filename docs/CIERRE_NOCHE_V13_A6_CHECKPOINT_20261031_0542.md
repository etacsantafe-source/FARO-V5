# FARO V5 — V1.3 Noche A, turno A6 — 31/10/2026 05:42 Argentina

**Estado:** CHECKPOINT / S2.A6 OPEN 3/8 / V2 OFF / S3-S4 CLOSED / T4 NOT_RUN.
**Repositorio privado:** etacsantafe-source/FARO-V5. **Rama exclusiva:** work/cierre-noche-v13-20261030.
**HEAD de código:** 17f6061558df86e72a93d1ff07acf47e4b3238b1.
**Writer:** epoch32 owner NOCHE_A_V13_TURN6_20261031_0542; adquirido por CAS desde f082be26640b974e7c1c7925834a63da9fb9e24f; liberación CAS pendiente de verificar al crear este checkpoint.

## Avance funcional y alcance

- Commit 17f6061558df86e72a93d1ff07acf47e4b3238b1: nueva instrumentación Android de recorrido cifrado de cinco almacenes reales en generación V2 aislada, usando el ContentResolver Android, creación de Intent ACTION_CREATE_DOCUMENT, escritura verificada de archivo FAROBAK2, reingreso privado, autenticación y comparación de los cinco snapshots con origen. V1 se protege con sentinel, V2 permanece OFF. **La prueba todavía no se ejecutó**; usa URI local, no un proveedor SAF real ni el picker visible.
- El mismo commit corrige la eliminación de un archivo de ingreso cifrado no verificado en AndroidBackupSafAdapter: no conservar staging inválido sin pin de journal.
- Oráculo Android dirigido actualizado de 9 a 10 casos para incluir la prueba nueva; no se cambió el alcance de los ocho criterios ni el gate de exportación de producción.
- Archivos: v5/app/src/androidTest/kotlin/ar/com/faro/v5/app/FaroV5F3QuarantinedSafRoundTripInstrumentationTest.kt; v5/platform/android/src/main/java/ar/com/faro/v5/platform/android/backup/AndroidBackupSafAdapter.java; .github/workflows/faro-v5-v13-f1-functional.yml.
- Cambio de estrategia tras fallos de runners: ruta de certificación en cuarentena separada del bloqueo de exportación activa, sin rebajar V2_ACTIVE_ENABLED.

## CI y pruebas exactas

- Actions #41185851309 SHA 17f6061558df86e72a93d1ff07acf47e4b3238b1: **FAILURE pre-runner**, 3/3 jobs terminaron sin pasos, sin XML, ~10 segundos wall (08:44:56–08:45:06 UTC). Auto-disparada por push; no repetición manual.
- Fallos anteriores #41177654138, #41179478414, #41181395040, #41183601361: mismo patrón de asignación de runners, sin XML.
- Android 10/10: **NOT_RUN**. Kotlin compile: **NOT_RUN**. No declarar PASS de esta modificación. T4 físico: NOT_RUN.
- No Actions pendientes observadas al checkpoint; no se lanzaron reintentos ni se cambiaron pagos, secretos, visibilidad o repositorios.

## Ocho criterios congelados

1 FIVE_OWNERS CERTIFIED; 2 FBS2_ISOLATED_RESTORE CERTIFIED; 3 JOURNAL_REPLAY_WAL CERTIFIED;
4 F1_ANDROID_CHAT_E2E OPEN; 5 F2_ANDROID_ACTIVATION_RECOVERY OPEN;
6 F3_FIVE_STORE_BACKUP_SAF_CONSENT OPEN; 7 F4_INTEGRAL_REGRESSIONS OPEN;
8 T4_PHYSICAL NOT_RUN. **Total 3/8.**

## Hipótesis, límites y siguiente acción

- Runner: no se asigna ninguno de los tres jobs. Causa exacta no demostrada; verificar elegibilidad/cupo de Actions sin modificar facturación. No repetir suites del mismo SHA sin nueva evidencia.
- F3: comprobar primero compilación del SHA 17f6061558df86e72a93d1ff07acf47e4b3238b1 y oráculo 10/10 cuando haya runner; resolver cualquier rojo real. Esta prueba es URI local y no sustituye prueba SAF con picker/document provider, ni restauración con journal/consentimiento real.
- F1: completar flujo de proveedor remoto consentido con credencial real sólo mediante decisión/entrada humana segura. F2 y F4 siguen pendientes; no abrir S3 antes de 7/8 con sólo T4 pendiente.
- Revalidar writer NONE y lease liberado mediante lectura GitHub posterior. Duración observada al checkpoint 144s, no se simula una sesión de 30 minutos.
