# FARO V5 — S3.A2 G5 · B7 Android real OS / emulador PASS (05/11/2026)

## B7 AUDITORÍA FINAL INDEPENDIENTE
**Unidad:** `S3_A2_G5_ANDROID_EMULATOR_DIRECTED`. Código auditado `bcb38e9ca36da7ae9df387d80bb2e911cbb71533`. **B7: PASS dentro del alcance automatizado Android emulado**, [Actions #42071443306](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42071443306) `completed/success` en job `g5-android-four-owner` (`127400136716`). El cambio fue exclusivamente el script de `.github/workflows/faro-v5-g5-android-directed.yml`, en un único `bash -euo pipefail -c` suministrado a `reactivecircus/android-emulator-runner@v2` mediante scalar YAML `>-`. No hay cambios en dominio ni permisos Android.

### Evidencia positiva exacta
- `BUILD SUCCESSFUL in 4m 36s` y `G5_ANDROID_INSTRUMENTATION_COMPILED`: app V2 DEV aislada + test APK reales.
- Emulador Android API 35 inició; la acción ejecutó el comando único `/usr/bin/sh -c bash -euo pipefail -c ...` sin perder variables de APK.
- Instrumentación reportó `INSTRUMENTATION_STATUS: numtests=1`, clase `FaroV5G5AndroidHostIntegrationTest`, método `fourRealOwnersSearchNavigateAndRevokeOnAndroidWithoutEgress`.
- Salida literal `OK (1 test)`, `INSTRUMENTATION_CODE: -1`, `G5_ANDROID_REAL_OS_FOUR_OWNER_PASS 1/1`; job y workflow en `success`.
- Caso Android inspeccionado: app paquete `ar.com.faro.alpha.v2coredev`, manifest expone solo launcher y cero permisos/servicios/providers/receivers; fixture Reality temporal en `cacheDir`, escrito por transacción del propietario canónico, nunca SQLite manual en datos de usuario; Finance/Objective/Matter 3 resultados y navegación tipada a cada uno; sin fuga de monto/ID en resumen; vacío sin coincidencias; el puerto obsoleto deniega apertura tras cierre/reinicio; segunda sesión tiene hits válidos. Se comprueba el alcance de asserts del test, no una certificación más amplia de egress de red.
- Guard de protocolo para código corregido [#42071443729](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42071443729) `success`. Guarda historial de corrección RED_FIX en `S3_A2_G5_ANDROID_EMULATOR_RUNNER_REDFIX_B7_20261105.md`.

### Límites explícitos y dictamen
`G5 Android emulado PASS 1/1` = **automatizado validado**; **NO** es test físico del Moto G15, ni una prueba completa de comportamiento UI táctil de todos los flujos, ni certificación integral IPC/egress fuera de las aserciones del test. El teléfono aún tiene Finanzas/Objetivos/Asuntos sin registros en snapshot: positivos por dominio y adversarial físico pendientes. Mantener `G5 OPEN_PHYSICAL_PENDING` y `S3.A2 4/5` hasta cumplir criterio físico o decisión formal separada. No habilitar V2 productiva, S4 ni Noche/Auto; S2 congelado. No solicitar ADB manual repetitivo ni rehacer G1–G4.

**Siguiente paso:** campaña física final única cuando proceda, con casos guiados y sin accesos al teléfono desde GitHub; no repetir CI que ya pasó. Protocolo B7 PASS registrado antes de nuevo código funcional, writer CAS liberable.
