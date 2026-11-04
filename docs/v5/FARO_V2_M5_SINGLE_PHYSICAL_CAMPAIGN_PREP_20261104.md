# FARO V5 — M5 campaña física única: preparación acotada (04/11/2026)
**Unidad:** M5_DEV_SINGLE_PHYSICAL_ACCEPTANCE. **Estado:** B1–B5 documentados; READY_FOR_SINGLE_PHYSICAL_CAMPAIGN **solo como preparación**. Ejecución Android **NOT_RUN**; M5 global **PARTIAL**; V2_DEV_CORE_READY **false**. No es permiso de activación productiva ni de instalar automáticamente.

## B1 EVIDENCIA (conservada; NO repetir CI)
- HEAD inicial revisado: `445704c5a8d702a872e0d059ce156dd8c9b414f4`, rama `work/s3-night-20261102`; writer CAS epoch 175 RELEASED al recuperar; Auto/Noche OFF, CLOUD STAGED.
- M2A recuperación: B7 PASS JVM `d2af301789db445be764368f0d50528da63fb41d`, Actions #41890189016, Chat/Workspace 4/4, T4 7/7, sin prueba física.
- M4 negativos: B7 PASS `aba2850cea07d19bbddb8813b1592cd641b67d62`, Actions #41896913438, stale ports 6/6; aislamiento de manifest DEV M1/M2B 4/4 cada uno (marcas de manifest, NO JUnit).
- M5 solo paquete APK: B7 PASS `018f0f5e1cb600a25f5d0d7897def5739a9b06f8`, Actions #41905632794 SUCCESS, artefacto ZIP #13360681405. APK `ar.com.faro.alpha.v2coredev`, launcher `ar.com.faro.v5.app.FaroV5V2CoreDevActivity`, cero uses-permissions en APK, firma debug v2, SHA256 APK `8773f7cea29b7487d955aff2a65eecdb56aa0eb49ba6428b74d95ee277f805c4`; ZIP tiene **otro** hash. Caduca 09/11/2026 20:26:15 UTC. No equiparar APK compilado con Android físico certificado.
- Código real: `FaroV5V2CoreDevActivity` comprueba ID distinto, build depurable, flag productivo OFF, requiere tocar botón y usa `noBackupFilesDir/faro-v2-core-dev-root`; `FaroV5V2CoreDevAuthority` abre cinco stores, journal, pointer, replay-fence; puertos Chat locales, SIN gateway de IA/modelo, Apply, Search shipping ni OS delivery.
- Límite preciso: no se observaron proceso Android en Moto G15, force-stop, WAL real tras muerte de proceso, restauración de UI, UID y raíz separados dinámicamente, egress/IPC dinámico ni conservación de FARO habitual después de instalar la variante DEV.

## B2 COMPARACIÓN V4.4 y código heredado (READ_ONLY)
- V4.4 y app habitual incorporan componentes/servicios de producción y almacenes previos: no trasladar su paquete, datos, permisos ni credenciales al host `v2CoreDev`.
- V5 reutiliza el controlador duradero, los cinco owners y el `FaroV5SurfaceHost`; no crear un segundo Chat, no importar la base V4.4, no rehacer M1–M4 que ya pasaron.
- Android aísla almacenamiento interno por UID; `noBackupFilesDir` no prueba por sí solo toda la política de migración Android 12+. El backup efectivo y la ausencia de accesos ajenos deben comprobarse donde sea observable; el APK estático demuestra menos que la ejecución real.

## B3 INVESTIGACIÓN y alternativas
- **USAR** documentación oficial de Android Debug Bridge: https://developer.android.com/tools/adb para `pm`, `am start`, `am force-stop`, `dumpsys` y reingreso controlado.
- **USAR** almacenamiento interno específico de aplicación: https://developer.android.com/training/data-storage/app-specific ; validar con paquete y UID, no con un supuesto path universal.
- **REFERENCIA/ADVERTENCIA**: https://developer.android.com/identity/data/autobackup : `allowBackup=false` protege backup nube pero Android 12+ tiene particularidades de transferencia de dispositivo según fabricante. No afirmar prohibición dinámica absoluta.
- **DESCARTAR ahora** instalar V2 en el paquete habitual, subir permisos, habilitar Internet, hacer reset/clear data, migraciones reales de V4.4, emulator como sustituto de Moto G15, instrumentación nueva sin brecha reproducible o recompilación del mismo SHA por rutina.

## B4 UNIFICACIÓN: matriz de la única campaña (TODOS inicialmente NOT_RUN)
| ID | Acción segura solo con participación del usuario | Evidencia esperada y condición binaria |
| --- | --- | --- |
| F-00 | Conservar listado/versión/estado de FARO habitual ANTES. Verificar hash de APK extraído frente a SHA256 anterior. | APK exacto y FARO habitual identificada; sin reutilizar su ID |
| F-01 | Instalar únicamente APK DEV, sin desinstalar/actualizar/limpiar datos habituales. | Coexisten ambos paquetes; package ID DEV exacto, UID distinto, launcher solo DEV. STOP ante colisión |
| F-02 | Inspeccionar el paquete realmente instalado: permisos solicitados y `allowBackup`/componentes visibles. | Cero permisos OS, ninguna actividad de producto habitual ni proveedor/receptor/servicio heredado; cualquier divergencia STOP |
| F-03 | Abrir DEV, confirmar pantalla `FARO V2 — Núcleo local aislado`, pulsar explícitamente «Abrir Chat local de desarrollo». | Sin crash; cinco stores reales admitidos, Chat local visible y no Search/IA/Apply/OS |
| F-04 | Crear/seleccionar Chat, escribir y enviar **un texto de prueba sin datos privados**. | Un único USER/ACCEPTED persistente; ninguna respuesta FARO ficticia ni ejecución externa |
| F-05 | Ir a Inicio/Recientes, volver, repetir apertura sin pulsaciones repetidas, después `am force-stop` **solo al paquete DEV** y reabrir. | Mismo Chat/selección/texto, sin duplicados; un crash o pérdida => FAIL reproducible |
| F-06 | Si la app es debuggable, inspeccionar **solo el sandbox DEV** con `run-as`; no tocar/alterar datos de FARO habitual. | Root privado, journal, pointer y cinco archivos SQLite presentes; fallos de readback registran LIMIT, no se destruyen bases para “arreglar” |
| F-07 | Comprobar que FARO habitual abre y conserva datos/estado propios. No desinstalar ninguna de las dos. | Comparación antes/después sin cambios involuntarios; ante duda, STOP |
| F-08 | Observar ausencia de permisos/red/notificaciones/Apply/OS durante prueba; registrar límites de observabilidad. | No afirmar “cero egress dinámico” sin evidencia de medición; ausencia de permisos manifest no prueba ausencia total de IPC |
| F-09 | Capturar crash/ANR/estado de `logcat` solo si hay fallo, filtrar tokens/PII antes de compartir. | Un reporte mínimo por gate; no publicar secretos ni contenido de conversaciones |

### Secuencia ADB de referencia, NO EJECUTADA
Preparar el APK desde el artefacto verificado; comprobar SHA256 local **antes** de instalar. En un host ADB autorizado por el usuario:
```bash
DEV=ar.com.faro.alpha.v2coredev
adb shell pm list packages | grep -F "$DEV"      # estado inicial
adb install -r /ruta/VERIFICADA/archivo.apk    # instala SOLO DEV; no usar -d, pm clear ni uninstall
adb shell pm path "$DEV"
adb shell dumpsys package "$DEV"                 # confirmar UID, permisos y componentes
adb shell am start -n "$DEV/ar.com.faro.v5.app.FaroV5V2CoreDevActivity"
# Realizar manualmente F-03 y F-04 y documentar resultados.
adb shell am force-stop "$DEV"                  # SOLO tras verificar package ID
adb shell am start -n "$DEV/ar.com.faro.v5.app.FaroV5V2CoreDevActivity"
# Confirmar manualmente F-05 y F-07; no borrar datos.
```
No ejecutar comandos en nombre del usuario desde tareas programadas. No presuponer que un `dumpsys` por sí solo certifica UID/storage; correlacionar con resultados de la app.

## B5 AUDITORÍA adversarial y salidas
1. **Aislamiento:** impedir sustitución del ID por V4.4, sobreescritura de FARO habitual, uso de su UID, restauración de backup ajena, apertura productiva V2 o migración de sus datos. Si paquete/hash no coinciden, STOP ANTES de `adb install`.
2. **Durabilidad:** no borrar WAL, journal, pointer, receipts o SQLite para “desbloquear”. Reingreso con proceso muerto debe conservar mensaje USER único; la prueba JVM no equivale a WAL físico.
3. **Permisos/egress:** cero permisos OS en APK es condición necesaria, no suficiente para negar todos los efectos dinámicos. No habilitar red/modelo/Groq/Apply/OS ni servicio.
4. **Certificación:** resultado por fila `PASS/FAIL/BLOCKED/NOT_RUN` + evidencia verificable, fecha, firmware/Android/package, logs filtrados y SHA del APK. Faltante `NOT_RUN` nunca es PASS.
5. **Decisión:** solo si F-00..F-09 relevantes pasan y no hay brecha de seguridad se evalúa `V2_DEV_CORE_READY`. Eso **no** habilita V2 productiva, no cierra S3 (0/5), ni abre S4 formalmente. Si hay fallo, corregir mínimo con nuevos B1–B5, permiso READY, preflight, CI y B7 independiente.
6. **Reglas:** código y tests M1–M4 y paquete M5 B7 ya demostrados no se repiten sin cambio. Writer CAS único; permiso de código `BLOCKED`; Auto/Noche OFF; cola STAGED; catálogo 172; sin APIs pagas; S2 congelada.

## B6 / B7 — reserva explícita
**B6 físico:** NOT_RUN, depende del dispositivo y participación humana; este documento no ejecuta ADB ni instala APK. **B7 de M5 global:** NOT_VERIFIED, pendiente de evaluación posterior de la evidencia real. Próxima acción: iniciar la única campaña física cuando el usuario tenga el dispositivo disponible y el artefacto verificado, sin alterar FARO habitual.

## Evidencia física observacional — 04/11/2026 18:47–18:50 Argentina (suministrada por usuario)

**Alcance exclusivamente visual de capturas desde el Moto G15.** No se proporcionaron a este checkpoint ADB, `dumpsys`, hash binario extraído, logcat, ni inspección de SQLite. No copiar capturas a repositorio ni presumir permisos por una pantalla.

- **F-03 UI: PASS_VISUAL_PARTIAL.** Captura a las **18:47**: pantalla `FARO V2 — Núcleo local aislado`, texto explícito de no conexión/IA y botón `ABRIR CHAT LOCAL DE DESARROLLO`. Captura a las **18:48**: tras tocar el botón, pantalla Chat visible con entrada de texto, área de conversación y control `Enviar`. No hay crash visible. Esto demuestra apertura visual, **no** inspección directa de cinco archivos, journal/WAL ni estabilidad tras kill.
- **F-04 UI: PASS_VISUAL_PARTIAL.** Captura a las **18:50**: aparece exactamente una burbuja con `Prueba FARO V2 - 04/11/2026`, y composer vacío. No aparece mensaje de respuesta FARO artificial. Esto acredita envío/renderizado observado, **no prueba aún persistencia tras proceso muerto** ni estado DB/receipts por inspección.
- **F-05: NOT_RUN** al escribir este checkpoint; la siguiente comprobación es forzar detención **solo de FARO V2 CORE DEV** desde Ajustes → Aplicaciones, reabrir y verificar que permanezca exactamente una burbuja y la selección. No borrar datos, desinstalar, actualizar ni limpiar almacenamiento.
- **F-00/F-01/F-02/F-06/F-07/F-08/F-09:** NOT_VERIFIED por capturas; instalación DEV al menos mostró un launcher funcional, pero identidad/UID/permiso/aislamiento dinámico de app habitual aún requiere evidencia específica.
- **Seguridad inmediata:** batería mostrada en captura 18:50 **7%**. Conectar a cargador antes de force-stop/reinicio para evitar interrupción no controlada.
- **Estado:** M5 física **IN_PROGRESS_USER_SCREENSHOTS_ONLY**; B7 M5 global sigue **OPEN**; `V2_DEV_CORE_READY=false`, V2 productiva OFF. No nuevos cambios funcionales, tests, APK ni CI dirigida. Permiso funcional `BLOCKED`.

### Próxima lectura
Solicitar la captura tras forzar detención del paquete DEV y reabrir Chat. Un mensaje igual y único permite **F-05 persistence PASS_VISUAL_PARTIAL**; evidencia física de otras dimensiones no inferida. Si falla, detener acciones y documentar síntoma, sin borrar el sandbox.

## Evidencia adicional F-05 — confirmación humana 04/11/2026, 18:51 AR
- El usuario confirmó explícitamente: **«Sí. Después de forzar detención»** en respuesta a si la captura de las 18:51 era posterior a utilizar la acción Android **Forzar detención**, volver a abrir FARO V2 y ver el mensaje original.
- La captura 18:51 presenta una única burbuja `Prueba FARO V2 - 04/11/2026`, idéntica a la captura 18:50, Chat visible y composer vacío, sin error observable.
- **F-05 = PASS_VISUAL_PARTIAL (confirmación humana + captura)** para *persistencia del mensaje después de force-stop/reapertura*. No se dispone de traza ADB/logcat que demuestre la transición exacta del proceso, ni inspección interna SQLite/WAL/journal/UID. No elevar a integración física global PASS.
- F-03 y F-04 permanecen PASS_VISUAL_PARTIAL; **F-00/F-01/F-02/F-06/F-07/F-08/F-09 sin certificación completa**. M5 GLOBAL PARTIAL; `V2_DEV_CORE_READY=false`; producción V2/Auto/Noche OFF; sin cambio de código/CI/APK.
- **Siguiente control útil y no destructivo:** comprobar visualmente que FARO habitual (no la DEV) sigue abriendo y conserva datos de prueba previos; no borrar almacenamiento ni instalar otro paquete. Si batería sigue al 7%, cargar antes.
