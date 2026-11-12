# FARO V5 — Emulator isolated G15 UI smoke inconclusive, B1–B5 delta y B7 RED_FIX
Fecha 2026-11-12 AR. MISMA unidad `S3_G15_X06_ISOLATED_BOOT_IDENTITY_FIX`. Scope funcional exclusivo: `.github/workflows/faro-v5-g15-isolated-x06-apk-directed.yml`.

## B1
SHA funcional `8bed315b8c8ff041ee1737caccb10c3ae0b1bf25`, guard `43268564507` SUCCESS. CI `43268564709` FAILURE en `Verify isolated DEV first launch and visible UI offline on Android API35` (job `131358627150`): Gradle completado y checks package/manifest/signed app PASS; emulador booted, `adb install` = Success, `adb shell am start -W` mostró `Status: ok`, `LaunchState: COLD`, Activity package exacto. Tras 10 iteraciones (46 s), `G15_ISOLATED_FIRST_LAUNCH_UI_FAIL`: checker exige simultáneamente proceso `pidof` y texto `Abrir Chat local de desarrollo` leído del XML UIAutomator; salida de ambos se había suprimido, por lo tanto **se desconoce** cuál falló. El filtro AndroidRuntime:E al final no mostró excepción fatal en log. NO concluir que Activity ya funcionó ni que cerró en esta CI. G15 físico sigue FAIL con APK anterior.
Primera CI `43266734360` fue RED_FIX del shell separado, documentado y solucionado por script único en SHA8bed. No repetir X06 cuatro owners ni otras suites históricas.

## B2
Conservar Activity package allowlist literal (2 IDs) y 7 Authority checks de SHA146aafb5, sin otras modificaciones funcionales; su compilación PASS. Reusar instalador emulator, script único Bash y `am start -W`. Solo agregar evidencia de depuración del estado real de PID, Activity, UI XML y captura de pantalla de emulador sin datos privados.

## B3
Alternativas A DESCARTAR hacer PASS solo con `am start -W Status ok` (puede devolver status mientras la app falla segundos después). B USAR diagnóstico fail-closed en loop: guardar `pidof`/dump activity/resultado UIAutomator/primeiro XML y screenshot, imprimir valores de fuente al fallar, conservar salida del emulador en artifact diagnóstico incluso con CI roja. C DESCARTAR desactivar check UI, simular pantalla, introducir fixture o instalar androidTest en G15. Android testing reference https://developer.android.com/training/testing/other-components/ui-automator and adb diagnostics https://developer.android.com/tools/adb, no dependencies nuevas.

## B4
ÚNICA ruta `.github/workflows/faro-v5-g15-isolated-x06-apk-directed.yml`: en script efímero ya existente guardar PID result last + stdout/stderr uiautomator (sin tragarse errores), al final guardar dumpsys activity, `adb exec-out screencap -p` a PNG, dumps XML y stdout de launch; si condiciones PASS (PID no vacío y botón visible) upload artifact normal APK+diag; si fallan, step continúa FAILURE pero `actions/upload-artifact if: failure()` SOLO artefactos diagnósticos de emulador y log, nunca la APK no aceptada. Cada intento bounded; NO convertir FAIL en PASS. Ruta contenedora/archivo de salida debe existir para upload. No re-run de tests ya certificados.

## B5
No publicar secretos ni contenido del celular: emulador API35 recién creado con app vacía, sin permiso Internet, sin datos usuario. No volcar logs personales de host. `adb exec-out screencap` captura pantalla sintética inicial (únicamente entorno CI). Si PID y UI no aparecen, registrar resultado como RED_FIX con causa concreta. No alterar aplicación source, salvo nueva auditoría si emulador demuestra otro crash. Requisito de G15: no borrar/desinstalar, firma local constante y nueva APK solo tras artifact certificado/usuario.

## B7
`8bed315b8c8ff041ee1737caccb10c3ae0b1bf25` **RED_FIX**: compilación y lanzamiento reportado por am = PASS parciales, pero smoke de texto+PID = FAIL; causa específica no expuesta por script. No aceptar paquete físico aún. Corrección permitida solo de instrumentación diagnóstica del mismo workflow, no debilitar validación ni reclamar B7 PASS.
