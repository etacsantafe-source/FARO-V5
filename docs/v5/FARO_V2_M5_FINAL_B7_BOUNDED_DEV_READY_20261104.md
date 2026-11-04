# FARO V5 M5 — B7: aceptación física acotada del núcleo V2 DEV aislado (04/11/2026)

**Decisión:** `M5_GLOBAL = PASS_BOUNDED_DEV_ISOLATED`; `V2_DEV_CORE_READY = true` **solo para reanudar desarrollo S3 sobre núcleo local aislado**. No equivale a activación de V2 productiva, a habilitar IA/Apply/red, ni a cerrar S3/S4. La evaluación conserva límites expresos; esta decisión no declara medición de tráfico, integridad forense SQLite/WAL ni garantía de actualización del APK.

## B1 Evidencia, sin repetir
- GitHub Actions del APK M5 `#41905632794`: SUCCESS; APK `ar.com.faro.alpha.v2coredev`, `FaroV5V2CoreDevActivity`, cero `uses-permission*`, firmado v2; sha256 `8773f7cea29b7487d955aff2a65eecdb56aa0eb49ba6428b74d95ee277f805c4`.
- El usuario ejecutó en Moto G15 `pm path` y `sha256sum base.apk`. **F-00 PASS:** hash del APK instalado coincide byte a byte con el binario certificado.
- `pm list packages -U`: V2 CORE DEV uid **10372**, V4 DEV uid **10344**, app base uid **10347**, V5 DEV uid **10361**. **F-01 PASS:** identidad aislada por Android.
- `dumpsys package` mostró `versionName=5.0.0-dev-v2coredev`, permiso **declarado** `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` de protección `signature`, sin permisos solicitados/concedidos visibles en salida filtrada. No equivale a permiso `INTERNET` solicitado.
- El último `dumpsys package` filtrado muestra **un único componente FARO launcher**: `ar.com.faro.v5.app.FaroV5V2CoreDevActivity`. La lista de paquetes del sistema entre corchetes no es una lista de componentes instalados de FARO.
- F-03 pantalla Chat abrió, F-04 un mensaje único enviado, F-05 el mismo mensaje tras **force-stop** confirmado por el usuario: **PASS_VISUAL_PARTIAL** dentro de aceptación de comportamiento de usuario; no hay logcat ni captura forense.
- `run-as` y `find` del sandbox exclusivo DEV: V1 con cuatro archivos SQLite y V2 con **cinco SQLite** (`workspace.db`, `reality.db`, `auxiliary.db`, `commercial-evidence.db`, `web-evidence.db`), los cinco con `-wal`/`-shm`; journal, active pointer, replay-fence, descriptor y marcador presentes. **F-06 PASS_DEVICE_FILE_STRUCTURE** sin inspección de integridad interna.
- FARO DEV previa abrió, mostrando conversaciones previas y el usuario confirmó persistencia de sus datos: **F-07 PASS_VISUAL_PARTIAL**, sin auditoría exhaustiva de contenido anterior.
- JVM M2A y M4 ya PASS: `#41890189016`, `#41896913438`; no se reejecutaron.

## B2 Comparación no invasiva
V4 DEV y V5 DEV conservan otros application IDs/UIDs. El paquete aislado usa su propio `noBackupFilesDir`, `FaroV5V2CoreDevApplication` vacía y root DEV independiente. No importó datos de V4, no activa V2 productiva, no modifica shipping ni V4.4.

## B3 Investigación / alternativa conservadora
El APK instalado, comprobado por hash, tiene cero `uses-permission*`: **no puede usar el permiso INTERNET del proceso para abrir conexiones de red directas**. La actividad DEV `FaroV5V2CoreDevActivity` crea solo sesiones de Chat Workspace local: `FaroV5SurfaceHost.install` sin `assistantTurnPort`, `applyRequestPort`, `searchReadPort` ni puertos OS. El manifiesto V2 DEV elimina providers/receivers/services y la actividad habitual; actividad única launcher exportada deliberadamente para poder abrir. Otros tipos de IPC de Android no pueden descartarse por ausencia de INTERNET; no afirmar aislamiento de todas las IPC imaginables.

## B4 Consolidación por gate
| Gate | Resultado acotado | Evidencia |
|---|---|---|
| F-00 APK exacto | **PASS** | SHA256 instalado = CI |
| F-01 identidad / UID | **PASS** | V2 10372; V4 DEV 10344; V5 DEV 10361 |
| F-02 permisos/componentes | **PASS_STATIC_AND_INSTALLED_CONSISTENT** | cero permisos solicitados en APK idéntico; `dumpsys` permiso definido signature, launcher previsto; sin componente extra confirmado |
| F-03 apertura Chat | **PASS_VISUAL_PARTIAL** | UI real Moto G15 |
| F-04 mensaje único | **PASS_VISUAL_PARTIAL** | UI real Moto G15 |
| F-05 reinicio proceso | **PASS_VISUAL_PARTIAL** | usuario confirmó force-stop y reapertura |
| F-06 sandbox / cinco owners | **PASS_DEVICE_FILE_STRUCTURE** | `run-as` root DEV: cinco SQLite+WAL/SHM, pointer, journal, fence |
| F-07 FARO anterior | **PASS_VISUAL_PARTIAL** | app V4 DEV visible y datos confirmados |
| F-08 egress/IPC/efectos | **PASS_BOUNDED_DENIAL_GATE** | sin permiso INTERNET/OS, sin servicios/receptores/proveedores heredados, sin assistant/apply/os ports; no respuesta IA ni efectos externos observados por usuario; **NO medimos tráfico ni auditamos toda IPC** |
| F-09 fallos | **NOT_TRIGGERED / N/A** | sin crash/ANR reportado; logcat solo si surge incidente |

## B5 Auditoría adversarial: límites aceptados, no ocultos
1. **No** se capturaron paquetes ni estadísticas de red; el alcance PASS de F-08 es la negación de capacidades por manifiesto Android+code-port y ausencia de efectos visibles durante la campaña, **no certificación de cero egress de cualquier tipo**.
2. F-06 comprueba estructura real, no `PRAGMA integrity_check`, WAL forense, checkpoint o journal decodificado. Las pruebas JVM + Chat persistente son independientes y complementarias.
3. El manifest declara y Android muestra el permiso propio `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` de firma, compatible con cero `uses-permission*`. Declarado no significa concedido.
4. **Riesgo de actualización**: el APK fue firmado con debug key de runner; la estabilidad de esa firma entre futuras compilaciones NO está demostrada. **STOP para `adb install -r`/actualizaciones hasta resolver identidad de firma sin pérdida de datos**. No desinstalar, no borrar sandbox, no sobreescribir FARO DEV ni otras instalaciones.
5. No introducir gateway de IA, red, envío OS, notificaciones ni Apply en esta variante por efecto lateral de `V2_DEV_CORE_READY`. Cambios funcionales posteriores requieren nuevo work permit B1–B7, writer CAS, CI exacta. V2 productiva OFF.

## B6 Verificaciones usadas
Se reaprovecharon CI exactas, hash del APK instalado, inspección PackageManager UID y componentes, `run-as` DEV y evidencia UI ya aportada, además de lectura del manifiesto/Activity/Authority/Surface. **No se ejecutó nuevo código ni se modificó el teléfono**.

## B7 Dictamen independiente
**PASS_BOUNDED_DEV_ISOLATED:** el núcleo V2 local aislado está suficientemente verificado **para desbloquear el trabajo de implementación de S3 en GitHub** bajo todas las restricciones vigentes. Se considera completada M5 como `DEV_READY` con caveats explícitos; esto **NO** certifica producción Android de V2 ni autoriza actualización del APK existente con nueva clave debug.

**Próxima unidad autorizable:** desarrollo S3 sujeto al protocolo completo (B1 evidencia, B2 comparar V4.4, B3 investigar, B4 unificar, B5 auditoría, B6 escribir/CI bajo permiso READY, B7 auditoría final). El permiso actual de código permanece BLOCKED hasta abrir una unidad concreta. Desarrollar primero lo mínimo útil, sin repetir M1–M5.
**Estado:** S2 CLOSED 8/8, S3 ~40% estimado / 0 de 5 cierres formales, S4 no iniciado, V2 productiva OFF, Auto/Noche OFF, cola CLOUD STAGED. Sin APIs pagas.
