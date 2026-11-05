# FARO V5 — S3.A2 G5 · B7 RED_FIX Android emulator runner (05/11/2026)

## B7 AUDITORÍA FINAL INDEPENDIENTE
**Unidad:** `S3_A2_G5_ANDROID_EMULATOR_DIRECTED`. Último código `13911eacf677b0ec968fd1202b521d764bc73a1c`. **Resultado: RED_FIX**, CI [#42069366649](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42069366649) FAILURE en paso `Android API 35 real OS G5 host and security`. El binario principal y el de instrumentación sí compilaron: `BUILD SUCCESSFUL`, `G5_ANDROID_INSTRUMENTATION_COMPILED`. El emulador sí inició: `Emulator booted.`; falló justo al evaluar `test -n "$APP" && test -n "$TEST"`. No se instaló ni ejecutó la instrumentación y no hay resultado G5 positivo/negativo. Los pasos de regresión exactos del SHA: protocolo [#42069373871](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42069373871) PASS, V2 core [#42069366769](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42069366769) PASS, APK aislado [#42069366835](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42069366835) PASS, Home notice [#42069366842](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42069366842) PASS.

### B1 EVIDENCIA
Las líneas del runner aparecen como ejecuciones independientes `/usr/bin/sh -c APP=...`, `/usr/bin/sh -c TEST=...` y `/usr/bin/sh -c test -n "$APP" && test -n "$TEST"`. El proceso shell de una línea no exporta las variables creadas en otro proceso. Las rutas construidas sí existen según aserciones previas del ensamblado. Es un error de orquestación shell, no de contenido de los owners.

### B2 COMPARACIÓN
Descartamos volver a tocar variantes Gradle, dependencias A5, heap o fuentes test: las dos APK compilaron. No correr tests independientes que no ejecuten el caso G5 ni dar por válido solo que abra la app. Emplear un único proceso `bash -c` con verificación de stdout y código de resultado.

### B3 INVESTIGACIÓN EXTERNA
`reactivecircus/android-emulator-runner@v2` ejecuta el `script` suministrado como comandos separados; el registro del job demuestra el comportamiento real de esta versión. En documentación del proyecto del runner se usa `script` para instalar y ejecutar comandos `adb`. El fix basado en evidencia es una sola llamada shell con `bash -euo pipefail -c '...'` envuelta en scalar YAML folded `>-`, de modo que la acción reciba una sola línea, con todo el ciclo dentro de ese mismo shell. No agregar terceros ni infraestructura.

### B4 UNIFICACIÓN
Solo `.github/workflows/faro-v5-g5-android-directed.yml`, bloque `script`: generar un único comando `bash`, localizar APP+TEST, verificar ambas rutas, instalar, ejecutar `am instrument`, imprimir la salida tanto en fallo como éxito y exigir `OK (1 test)` e `INSTRUMENTATION_CODE: -1` antes de `G5_ANDROID_REAL_OS_FOUR_OWNER_PASS 1/1`.

### B5 AUDITORÍA ADVERSARIAL
Sin tocar datos reales, firmware Moto, registros productivos, Android manifest, permisos, ACL, V2 shipping ni otras etapas. El script debe fallar cerrado si no aparece una APK, si `adb` falla, si el caso no existe o si no hay los dos marcadores del resultado real. Debe preservar output de fallos para diagnóstico posterior. Se mantiene `G5 OPEN` hasta evidencia Android real y criterio físico residual.

**Permiso de red_fix acotado:** `S3_A2_G5_ANDROID_EMULATOR_DIRECTED`, modificar exclusivamente el workflow de la prueba. Antes de otro cambio funcional, exigir nueva auditoría B7 de ese cambio y resultado exacto. Escritura manual con writer exclusivo; Auto/Noche OFF.
