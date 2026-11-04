# FARO V5 — M5: auditoría independiente de aislamiento e integración, 04/11/2026

**Decisión:** `B7_AUDITED_WITH_RESIDUALS / M5_GLOBAL_PARTIAL / V2_DEV_CORE_READY=false`.
**Propósito humano:** completar los tres pasos hacia producción de desarrollo: (1) aislamiento, (2) auditoría final, (3) elegibilidad para V2_DEV_CORE_READY. **Nunca confundir V2 de desarrollo con V2 productiva**.
**Alcance:** auditoría remota de código, APK firmado y CI existente, más capturas y confirmaciones humanas del Moto G15; **ningún acceso ADB remoto al teléfono** en esta sesión. No instalar, borrar ni cambiar FARO DEV, FARO habitual o V2 DEV.

## B1 — Evidencia de repositorio y ejecución, verificada de forma actual
- Rama `work/s3-night-20261102`, HEAD basal `11670fc1a3392e956b31510d026bd3342a3d8921`; writer `RELEASED` epoch 179, CLOUD `STAGED`, código funcional bajo permiso `BLOCKED`.
- APK código SHA `018f0f5e1cb600a25f5d0d7897def5739a9b06f8`, Actions `#41905632794` COMPLETE SUCCESS. Log de job `126839410424` con: `M5_ASSEMBLE_V2_CORE_DEV_PASS`, paquete `ar.com.faro.alpha.v2coredev`, launcher `FaroV5V2CoreDevActivity`, `M5_APK_BINARY_PERMISSIONS_DENIED_PASS`, firma v2 `M5_APK_PACKAGE_SIGNATURE_PASS`. SHA256 APK `8773f7cea29b7487d955aff2a65eecdb56aa0eb49ba6428b74d95ee277f805c4`, bytes 62131379.
- Artefacto #13360681405: `expired=false` comprobado, caducidad 09/11/2026 20:26:15Z, digest ZIP distinto al APK. No dar por instalado **exactamente ese binario** sin recuperar fingerprint de paquete del dispositivo.
- M2A recuperación `#41890189016` SUCCESS, M4 puertos stale y manifest `#41896913438` SUCCESS; S3 regresión `#41890189058` SUCCESS. **No se reejecutaron.**
- `compare_commits` desde `018f0f5e` hasta `11670fc1`: ahead 6, **solo docs/v5**, ningún cambio de app, manifest, build, CI funcional o test tras APK certificado.
- Capturas: 18:47 launcher, 18:48 Chat, 18:50 un mensaje USER, 18:51 mismo mensaje tras **force-stop confirmado por el usuario**, 19:05 FARO DEV anterior abre con Conversación 59 fijada y recientes 77/78/76/74; FARO DEV muestra `4.3.0-dev` en Android y el usuario confirma que están sus datos. No etiquetar el binario instalado V4.4 sin versión/identidad probada.

## B2 — Comparación no invasiva con V4.4 / FARO anterior
- El núcleo DEV usa `applicationIdSuffix=.v2coredev`, `FaroV5V2CoreDevApplication` vacía (sin inicializador remoto), y root `noBackupFilesDir/faro-v2-core-dev-root`; FARO anterior conserva su paquete y datos visibles.
- Se reutiliza journal, active pointer, replay fence, shared write gate, cinco stores, Workspace y `FaroV5SurfaceHost`; no duplicar framework, importar V4.4, reconectar Groq, Apply, OS ni reescribir el owner previo.
- `FaroV5SurfaceHost.install` se llama **sin** assistantTurnPort, applyRequestPort, searchReadPort o avisosReadPort. Puede renderizar el campo de Buscar general del Surface, pero no debe confundirse con Search shipping autorizado; tratarlo como deuda de UX/S3, no como privilegio implícito.

## B3 — Investigación de comportamiento Android / alternativas
- **USAR** la documentación oficial: https://developer.android.com/tools/adb (ADB consultas `pm`/`dumpsys`, `am force-stop`); https://developer.android.com/training/data-storage/app-specific (interno privado por app/UID); https://developer.android.com/guide/topics/manifest/application-element (backup Android 12+); https://developer.android.com/google/play/app-updates (certificados para actualizar); https://developer.android.com/studio/publish/app-signing (debug.keystore automático).
- **REUTILIZAR** CI estático/manifest/APK, pruebas JVM y capturas existentes; no repetir M1–M4.
- **RIESGO NUEVO, DE ITERACIÓN:** el build usa firma debug y el workflow corre en runner efímero. No consta una clave persistente verificada para actualizaciones futuras del paquete DEV. **No afirmar que un nuevo APK pueda actualizar esta instalación sin pérdida de datos.** Un APK nuevo con certificado distinto exige desinstalación; prohibido hacerlo sin plan de preservación explícito. Solución posterior deberá elegir firma DEV estable sin secretos publicados y verificar correspondencia con la instalación, o nuevo paquete aislado sin sobrescribir el existente.
- **DESCARTAR** inferir ausencia de todo IPC/egress porque el manifest no declara INTERNET; asumir integridad WAL interna desde una burbuja visible; usar “clear data” para probar restart; activar V2 productiva.

## B4 — Unificación y matriz de aceptación sin duplicación
| Gate | Evidencia válida | Decisión exacta |
| --- | --- | --- |
| F-00 APK exacto | CI APK SHA256 PASS, artefacto vivo; **hash del APK instalado no observado** | `CI_PASS / INSTALLED_ID_UNVERIFIED` |
| F-01 identidad/UID | `applicationId` distinto PASS en binario y `onCreate` valida packageName; UI real abre. **UID Android comparativo no observado** | `PACKAGE_PASS_CODE_AND_UI / UID_PENDING` |
| F-02 permisos/componentes | AAPT2 del APK firmado: cero uses-permission, launcher exclusivo; manifest DEV elimina providers/receivers/services, `allowBackup=false` heredado del main | `APK_STATIC_PASS / DEVICE_RUNTIME_PENDING` |
| F-03 Chat abierto | Foto 18:48 del Chat posterior al botón | `PASS_VISUAL_PARTIAL` |
| F-04 escribir/enviar | Foto 18:50 muestra una única burbuja del mensaje de prueba | `PASS_VISUAL_PARTIAL` |
| F-05 process restart | Usuario confirmó expresamente `Forzar detención`, reapertura, mensaje único visible a las 18:51 | `PASS_VISUAL_PARTIAL` |
| F-06 owner/5 stores | Apertura DEV solo retorna tras graph saludable con Workspace + journal/fence, y M2A JVM; **no se inspeccionaron los cinco SQLite ni WAL en teléfono** | `CODE_CI_PASS / DEVICE_FILES_PENDING` |
| F-07 datos FARO previo | Foto 19:05 de FARO DEV con conversaciones previas; usuario afirma conservar datos | `PASS_VISUAL_PARTIAL` |
| F-08 sin efectos externos | APK cero permisos y host sin ports externos; **no hay observación dinámica de IPC/egress** | `STATIC_DENIAL_PASS / DYNAMIC_PENDING` |
| F-09 crash | No se reportó fallo ni error; logcat solo si falla un gate | `NOT_TRIGGERED` |

## B5 — Auditoría adversarial, decisiones de STOP
1. Exigir UID del paquete aislado distinto de las apps anteriores y mostrar permisos instalados: si coincide o aparece permiso inesperado, **STOP**.
2. Los cinco SQLite deben localizarse SOLO bajo root DEV, sin consultar ni mutar archivos de FARO DEV previo. Probar read-only, nunca `pm clear`, `uninstall`, `rm`, borrar WAL, pointer, journal o volver a instalar.
3. Captura visual de mensaje + force-stop satisface UX/durabilidad observable, pero no un chequeo SQLite de integridad ni crash/WAL bajo corte brusco. Si Android `run-as` no funciona, registrar `BLOCKED`, no inventar acceso.
4. Contrato fail-closed: no habilitar permisos, red/Groq/IA, Apply, actos OS, searchReadPort auténtico ni otra generación por mero resultado parcial.
5. Riesgo de firma: **no recompilar y actualizar DEV a ciegas**, la firma del runner puede no ser estable; preservar el APK actual y datos de la prueba.
6. Reglas de protocolo: el permiso de código se mantiene BLOCKED. Cualquier corrección funcional necesita nuevos B1–B5/READY, writer CAS y CI exacta; documentación read-only no autoriza despliegue.

## B6 — Trabajo de verificación ejecutado
- **Ejecutado sin cambios funcionales**: consulta en vivo de branch/writer/queue/permit; CI jobs #41890189016, #41896913438, #41905632794, #41922341797; log exacto del APK y SHA256; artefacto no vencido; comparación GitHub commit a commit; revisión de Activity, Authority, manifest y build V2; revisión de capturas de usuario.
- **No ejecutado:** ADB en Moto G15 (no hay dispositivo conectado ni acceso remoto a `adb` en esta sesión); por ende no instalar ni extraer APK ni `run-as`, no comparar UID, no ejecutar análisis dinámico de IPC. No se crean pruebas simuladas falsamente.
- **Guía de solo lectura para una única sesión ADB cuando haya un host autorizado y dispositivo disponible** (NO EJECUTADA):
```bash
DEV=ar.com.faro.alpha.v2coredev
adb devices -l
adb shell pm path "$DEV"
adb shell dumpsys package "$DEV" | grep -E 'userId=|versionCode=|versionName=|requested permissions:|android.permission.'
adb shell pm list packages | grep -i 'faro'
adb shell run-as "$DEV" sh -c 'find no_backup/faro-v2-core-dev-root -type f' 
```
Los comandos no borran ni modifican datos, pero listan rutas privadas del DEV: **revisar y omitir cualquier contenido sensible antes de compartir logs**. Confirmar `run-as` y el UID del teléfono sin inferencias. Para comparar hash binario instalado, usar ruta `pm path` + `sha256sum` de archivo de APK en host autorizado, distinguiendo splits. Ningún `adb install`/upgrade.

## B7 — Auditoría final independiente: decisión responsable
- `M1/M2A/M2B/M3/M4`: conservar evidencia PASS de alcance JVM/manifest previo, sin reabrir.
- `M5_APK_PACKAGE_GATE`: PASS B7 del APK exacto.
- `M5_GLOBAL`: **PARTIAL / OPEN — NO APPROVAL** por F-00 exactitud del binario instalado, F-01 UID, F-02 componentes/permisos on-device, F-06 verificación del sandbox y F-08 observación dinámica pendientes.
- `V2_DEV_CORE_READY`: **false** hasta evidencias físicas pendientes y auditoría final sin rojos. **V2 productiva: OFF**. FARO Auto/Noche OFF, CLOUD STAGED, S2 8/8 cerrado, S3 ~40% desarrollo estimado y 0/5 cierres, S4 no abierto.
- Próxima unidad MINIMA: una sesión física de **solo lectura** en paquete `ar.com.faro.alpha.v2coredev`, no otra ronda de desarrollo; consolidar evidencia de identidad/UID/permisos, cinco stores y egress observable. F-03/04/05/07 no se repiten. Luego reauditar solo los rojos; si todo PASS, emitir B7 final y cambiar el estado `V2_DEV_CORE_READY` con evidencia. No declarar fecha prometida.

## Adenda de evidencia física del Moto G15 — F-01 y F-06
La sesión ADB del usuario confirmó `ar.com.faro.alpha.v2coredev` con **UID 10372**, distinto de `ar.com.faro.alpha.v4dev` (10344), la aplicación base (10347) y V5 DEV (10361). **F-01 UID: PASS**.

Una consulta `run-as` de solo lectura al almacenamiento privado DEV mostró dos generaciones. La generación inicial contiene cuatro SQLite; la generación V2 contiene **reality.db, commercial-evidence.db, web-evidence.db, auxiliary.db y workspace.db**, cada una con archivos -wal y -shm. También se observaron los registros del puntero activo, journal, replay fence, descriptor y marcador de formato. **F-06: PASS para existencia y estructura de cinco bases en el dispositivo**, no para integridad interna de SQLite/WAL o semántica de los registros.

`dumpsys` confirmó `versionName=5.0.0-dev-v2coredev`, pero la salida filtrada de permisos no es una prueba exhaustiva de ausencia de permisos. Se preserva la certificación del APK firmado en CI; F-02 permisos instalados sigue parcial. F-00 hash del APK instalado y F-08 validación dinámica de egress/IPC también pendientes.

No se repiten Chat, force-stop, comparación FARO anterior ni CI. `V2_DEV_CORE_READY=false`, V2 productiva OFF, permiso de código BLOCKED.

## Adenda F-00 — huella exacta del APK instalado (04/11/2026)
El usuario ejecutó en Termux/ADB `adb shell pm path ar.com.faro.alpha.v2coredev`, que identificó `base.apk` dentro del directorio de paquetes de `ar.com.faro.alpha.v2coredev`. Acto seguido ejecutó `adb shell sha256sum` sobre la ruta devuelta; **SHA256 observado en el Moto G15: `8773f7cea29b7487d955aff2a65eecdb56aa0eb49ba6428b74d95ee277f805c4`**, coincidencia exacta con el binario APK aprobado en GitHub Actions #41905632794.

**F-00 = PASS_DEVICE_INSTALLED_APK_HASH**: se certifica identidad byte a byte del archivo `base.apk` instalado con el APK registrado; no se reinstaló ni reescribió la app. Esta identidad permite aplicar los resultados AAPT2 del APK firmado (paquete y cero `uses-permission`) a este binario específico. No sustituye revisión de permisos otorgados/configuración efectiva del teléfono o componentes vivos (F-02), ni inspección dinámica del egress/IPC (F-08). F-01 UID y F-06 estructura de cinco bases ya tienen evidencia física y no deben repetirse. **M5 GLOBAL todavía OPEN y `V2_DEV_CORE_READY=false`.**

## F-02 evidencia de permisos del paquete instalado (04/11/2026)
Salida textual proporcionada desde Termux ADB: `dumpsys package ar.com.faro.alpha.v2coredev | grep -iE 'permission|granted=|userId=|versionName='` muestra `versionName=5.0.0-dev-v2coredev`, encabezado `Permissions:`, un permiso **declarado** `ar.com.faro.alpha.v2coredev.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION: prot=signature`, dos encabezados `runtime permissions:`, sin `granted=true`, `android.permission.INTERNET` o permisos solicitados en ese filtro. AndroidX Core usa ese identificador de permiso de firma para la protección de receptores dinámicos no exportados; su definición por sí sola **no implica permiso solicitado ni concedido** (refs: https://android.googlesource.com/platform/frameworks/support/+/b827e60cec1f802a9fa2dbfbe6a0ddd7576bafca/core/core/src/main/java/androidx/core/content/ContextCompat.java , https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/permission/Permissions.md ).

**F-02 = PASS_APK_USES_PERMISSIONS_ZERO + DEVICE_DUMPSYS_DECLARATION_REVIEWED / COMPONENT_RUNTIME_PENDING.** La igualdad de SHA256 F-00 demuestra que AAPT2 inspeccionó el mismo APK instalado. Se comprobó que el único permiso observado es **declarado** con protección `signature`; no elevar un filtro `grep` a auditoría exhaustiva de la tabla del dispositivo. Sin indicador de falla ni necesidad de modificar APK o activar permisos.

**Pendiente mínimo:** consultar solo metadata de componentes/permiso efectivo del paquete en Android, y cerrar evaluación F-08 con evidencia estática de bloqueo de Internet (APK no lo solicita), UID privado y ausencia de componentes externos no autorizados; no afirmar monitoreo de tráfico que no se haya hecho. No repetir hash, prueba UI o bases. `V2_DEV_CORE_READY=false`, M5 global OPEN.
