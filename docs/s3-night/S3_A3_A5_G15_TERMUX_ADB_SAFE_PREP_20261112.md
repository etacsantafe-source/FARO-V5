# FARO V5 — Termux ADB G15: preparación segura (READ_ONLY + pruebas humanas)
Fecha 2026-11-12, rama `work/s3-night-20261102`.
Estado: **KIT_PREPARED_AND_MOCK_TESTED; ADB_G15_REAL_NOT_CONNECTED; PHYSICAL_NOT_RUN**.
Este documento no autoriza activar V2 productiva, instalar/desinstalar, limpiar datos, ejecutar APK androidTest ni modificar runtime/owner. No solicita permiso funcional; únicamente registra un diagnóstico documental y un kit transferido al usuario.

## B1 — Identidad y fuentes reales reutilizadas
Checkpoint de partida `60fabc7ed87a4a686bf4b1492ea0de1551513184`, guard `43225410571` SUCCESS, writer RELEASED epoch807; X06 cuatro owners Android `43223365784` 1/1, guard `43223365720` y X12 `43165047469` 2/2 ya B7 PASS scoped. No repetir CI.
Archivo descargable real `FARO_V5_CORE_DEV_MOTO_G15_X06_20261112.apk`, SHA-256 recalculado desde bytes `47da61183d4679f8ba5bc6362db63d83fc22e1cfc6dd03578c93f6a190a51fb7`, idéntico al ZIP del artifact `13948280345`; ZIP SHA `3bbe3d99c00be4da9a27bdd937446cdfc46ab56a0905e70939095c8e93e4ec24`, CRC 10 entradas íntegro, XML 1/0/0/0. Paquete `ar.com.faro.alpha.v2coredev` code66, versionName `5.0.0-dev-v2coredev`, minSdk29 targetSdk36, sin permiso INTERNET ni X03 U02. APK AndroidTest nunca en G15.

## B2 — Qué se aprovecha y qué NO se extrapola
Campaña ya preparada: `docs/s3-night/S3_A3_A5_G15_PHYSICAL_CAMPAIGN_20261112.md`, matriz `docs/s3-night/S3_A5_7_ACCEPTANCE_MATRIX_20261112.md`. No repetir pruebas de emulador ni certificar físico por analogía. No borrar Reality8, Guardados, app S2 ni datos DEV existentes. El G15 necesita verificar el paquete instalado y firma antes de intentar actualizar. Firma existente aún **UNKNOWN**, no afirmar compatibilidad hasta ADB físico.

## B3 — Método ADB Termux sin root
Android 11+ permite wireless debugging con código y dos puertos DISTINTOS: emparejamiento y conexión. `127.0.0.1:PUERTO` es un candidato para Termux en el mismo teléfono; si falla usar IP:PUERTO mostrado por Ajustes. Requiere Wi-Fi activo/red aceptada por Android; solo datos móviles no garantizan habilitar depuración inalámbrica. Fuentes: https://developer.android.com/tools/adb y https://android.googlesource.com/platform/packages/modules/adb/+/HEAD/docs/dev/adb_wifi.md. Termux: `pkg install android-tools`; para comparación de firmas `pkg install apksigner` (https://github.com/termux/termux-packages/blob/master/packages/apksigner/build.sh). Usar pantalla dividida: el diálogo PIN puede cerrarse al perder foco. Nunca publicar código PIN/pares de claves ni serial.

## B4 — Kit reproducible entregado al usuario
Artefactos generados fuera del repositorio FARO y adjuntados en la conversación, NO desplegados en G15 ni versionados como código funcional:
- `FARO_G15_TERMUX_ADB_V1.sh`, SHA-256 `580f6a13266cf797f3c330fa37b82bb84079be84a54120fda621d851f3944bba`.
- `FARO_G15_TERMUX_KIT_V1_20261112.zip` (script+guía), SHA-256 `a42a606c6021425f113ffc51a48c01b09df7b34085bfe7cc1a7c336636a25ba8`, CRC 2/2.
- `FARO_G15_TERMUX_GUIA_V1.txt` dentro del ZIP, con pasos exactos.

Flujo humano en el dispositivo (no se ejecutó aquí):
```bash
termux-setup-storage
pkg install android-tools
cp ~/storage/downloads/FARO_G15_TERMUX_ADB_V1.sh ~/faro-g15.sh
bash ~/faro-g15.sh preparar
bash ~/faro-g15.sh verificar-apk
bash ~/faro-g15.sh parear       # puerto de pairing + PIN temporal ingresado en ADB
bash ~/faro-g15.sh conectar     # OTRO puerto de Wi-Fi debugging
bash ~/faro-g15.sh diagnostico
pkg install apksigner
bash ~/faro-g15.sh firma        # copia sólo APK base instalada a Termux privado + cotejo cert
bash ~/faro-g15.sh plan
# SOLO tras compatibilidad y autorización humana de instalación por separado:
bash ~/faro-g15.sh smoke        # apertura, Inicio, retorno, requiere palabra PROBAR
bash ~/faro-g15.sh captura X12-P01
bash ~/faro-g15.sh registrar X12-P01 NOT_RUN "hasta observación real"
bash ~/faro-g15.sh detener
```
El script no contiene `adb install`, `pm clear`, `uninstall`, `am instrument`, comandos de actualización de Android Settings o cambios de densidad/fuente. Capturas y TSV permanecen PRIVADOS en `~/faro-g15-evidencias`, no se suben solos. No interpretar notas del usuario como autorizar borrar.

## B5 — Riesgos y límites
Distinto firmante => STOP, sin desinstalar. Identidad o serial ADB ambiguos => STOP; código alfanumérico de emparejamiento secreto, no guardar. Si el dispositivo muestra sólo datos móviles y no permite Wireless debugging, STOP con alternativa Wi-Fi bajo control humano; no proponer bypass/ROOT por defecto. `smoke` solo verifica que se emita gesto de abrir→Inicio→volver y exige observación humana para PASS; ni persistencia de Guardados ni tokens de ACL se certifican automáticamente. Los tests de cuatro owners en G15 requieren fixtures no presentes en CORE DEV y se anotan BLOCKED_FIXTURE, nunca PASS o FAIL inventado.

## B6 — Pruebas de preparación, NO físicas
`bash -n`: PASS. Comando `verificar-apk`: SHA exacto PASS. ZIP CRC2/2 PASS. Mock local de ADB (NO G15) pasó 6 comandos `preparar`, `diagnostico`, `plan`, `smoke`, `captura`, `registrar`; sin invocaciones destructivas. Séptima prueba simulada `firma` comparó certificados sintéticos iguales y validó control de confirmación y copia de binario. **SIMULATED_ADB_7_OF_7_PASS; REAL_ADB_NOT_RUN**. No afirmar que la firma G15 sea igual; fue un mock.

## B7 — Dictamen documental
**READY_FOR_HUMAN_ADB_PAIR_AND_PREFLIGHT**. No despliegue ni aceptación física. Autoridad user+Termux para emparejar, confirmar paquete instalado y comparar firmas. Si firma distinta o versión incompatible, detenerse sin borrar nada. Luego elaborar campaña humana A3/X01–X12 con la matriz existente; no cerrar A3/A5 ni X03 live por estas pruebas. Main, V4.4, S2, V2 productiva, S4, Auto/Noche intactos. Mantener escritor GLOBAL CAS RELEASED al terminar.
