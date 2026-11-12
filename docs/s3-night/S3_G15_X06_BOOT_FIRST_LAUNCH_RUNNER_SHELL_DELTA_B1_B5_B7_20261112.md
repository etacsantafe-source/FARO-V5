# FARO V5 — G15 isolated boot first launch runner script, B1–B5 delta y B7 RED_FIX
Fecha 2026-11-12. MISMA unidad `S3_G15_X06_ISOLATED_BOOT_IDENTITY_FIX`. No cambios de runtime adicional, solo workflow.

## B1
Commit funcional `146aafb50793d46e86df05bd1d027f65c2e7b34e` implementó política exacta de dos IDs DEV, reemplazó 1 Activity check y 7 Authority checks, y agregó smoke Android35. Guardia `43266734155` SUCCESS. CI dirigida `43266734360` FAILURE job `131352176113`: Gradle `:app:assembleV2CoreDev` terminó correctamente y verificador estático de APK/manifest pasó; emulador API35 **arrancó satisfactoriamente**. Falló script ANTES de `adb install` y de `am start`: runner `reactivecircus/android-emulator-runner@v2` procesó cada línea de `script: |` como un comando shell separado `/usr/bin/sh -c`, por lo cual `APP=...` no persistió; `test -s "$APP"` salió 1. No existe evidencia de fallo de Activity en esa CI, ni de PASS de apertura. La app en G15 físico anterior seguía FAIL y sin manipulación. B1–B5 original `docs/s3-night/S3_G15_X06_ISOLATED_BOOT_IDENTITY_FIX_B1_B5_20261112.md` ya cubre controles de identidad; reutilizar. Corrección posterior de B1: hay 7 comparaciones en Authority más una check en Activity (8 en total), no 8 de Authority.

## B2
Comparar con patrón de runner utilizado en `.github/workflows/faro-v5-s3-a5-7-x06-four-owner-search-android-directed.yml`: pasa un único shell `bash -euo pipefail -c '...'` como argumento script. Reutilizar la lógica smoke ya publicada, sin repetir tests X06/X12; no cambiar el APK compilado ni permisos, owners, root, V4.4, S2, catálogos o tests históricos.

## B3
Alternativas: A `script: >- bash -euo pipefail -c '...'` con quoting complejo (ADAPTAR); B **USAR** un paso `run: |` previo que escriba `faro-g15-isolated-smoke.sh` efímero con heredoc literal y un único `script: bash ./faro-g15-isolated-smoke.sh`; así el shell preserva variables/estado y `set -euo pipefail`. C DESCARTAR reconfigurar ambiente Android, desactivar chequeos, usar `adb install` en G15 o usar `|| true` para false PASS. El action no garantiza single-shell para varias líneas bajo `script: |`; logs son evidencia primaria.

## B4
Única ruta funcional a cambiar: `.github/workflows/faro-v5-g15-isolated-x06-apk-directed.yml`. Antes del paso `reactivecircus`, crear script temporal con shebang bash, `set -euo pipefail`, pasos previos de APP=find/adb install/offline/am start y bucle bounded UI XML+pid. Runner ejecuta solo `bash ./faro-g15-isolated-smoke.sh`; upload del APK únicamente después de PASS. No cambiar Kotlin, Gradle, signatures, manifest, packageName. CI exact SHA y guard del mismo SHA.

## B5
Nunca false green por `am start` que puede retornar success antes de excepción; exigir PID y texto real `Abrir Chat local de desarrollo` del window dump. No usar androidTest con fixtures ni conectar G15. No usar Bash expansions no escapadas en YAML. Registrar salida de emulador/logcat si falla, conservar todo el flujo de permiso propio AndroidX signature, sin internet/OS. Android35 emulador efímero, sin user data. Si falla en onCreate u otros motivos, B7 RED_FIX concreto. Writer CAS único, permiso individual SAME UNIT y ONE PATH antes de código.

## B7
**RED_FIX** del commit `146aafb50793d46e86df05bd1d027f65c2e7b34e` porque smoke API35 nunca llegó a lanzar Activity. Comprobación independiente: logs reales CI `43266734360` job `131352176113`, fallo test `-s "$APP"` tras cambio de shell, sin `adb install`. Guardia de protocolo del SHA SUCCESS, no equivale a aceptación física ni B7. Puede autorizarse únicamente corrección de shell para misma unidad; B1–B5 delta documentados, CI y B7 tras cambio.
