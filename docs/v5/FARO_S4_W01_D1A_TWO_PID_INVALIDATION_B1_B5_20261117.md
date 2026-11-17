# FARO V5 — S4 W01 D1A · aviso ContentResolver entre dos PID reales (B1–B5)
Fecha: 17/11/2026 (Argentina). Estado: **PREAUDITADO Y CORREGIDO DOC_ONLY / SIN READY / SIN B6 / SIN B7**.
Base de análisis: HEAD de código `014372cc071ff0eddf704b82066f93bb86ec480e`. **D0 B7 PASS acotado confirmado; NO comenzar D1A funcional hasta READY propio posterior a B5 independiente.**

## B1 — Evidencia verificable y brecha única
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadPrivateInvalidation.kt` blob `ff022304228e77a9c0ed741f4163456b6a933d85`: observer registra en `content://<applicationId>.finance9read/invalidated`, invalida caché y entrega callback en Main Looper. `notifyChange` sólo ocurre cuando un publisher cooperante lo llama. B7 histórico DEV 8/8 JVM certificó la gate, NO dos PID.
2. `S4W01FinanceReadPostCommitPublisher.kt` blob `1e4dce3a37ed62455189fda5c9843beb4669e47b`: adaptador SQL committed -> cambio opcional, devuelve `notified=false` si falla la entrega. B7 de C `e68549b1b1ee2155aea37e27d4f8861661db1316` y Action `44160015632` SUCCESS 16 JVM; no host W01 conectado.
3. `v5/app/src/w01FinanceDev/AndroidManifest.xml` blob `ef4b2e16d82cd9535fb4c1645fa9599341f739f1`: único provider privado `exported=false`, W01 separado, sin Internet, sin servicio remoto. `v2CoreDev` cero provider, no tocar.
4. `v5/app/build.gradle.kts` blob `06efa77d0b8bb09911b0a74fb200e8d883fe1464`: contiene buildType `w01FinanceDev`, instrumentación histórica G5/X03/S3 elegida por propiedades explícitas. Todavía NO hay test source W01 separado. **No activar pruebas simultáneas para dos buildTypes.**
5. D0 código `1f05ed3a28db2f66a86fcb8c394c83dcfe035323`, RED_FIX `014372cc071ff0eddf704b82066f93bb86ec480e`; guard `44165550515` SUCCESS, run dirigida `44165550511` SUCCESS 8/8 JUnit + Android compilación, B7 scoped documentado en `docs/v5/FARO_S4_W01_D0_DEV_LAUNCH_B7_PASS_SCOPED_20261117.md` blob `aa4c6f35247dea93ef10b7a42731d2ad9e12676f`; guard B7 `44167039799` SUCCESS. No repetir. NO prueba de arranque G15.

**Aceptación limitada de D1A:** test de Android API35 ejecutado en emulador con dos PID de Linux distintos dentro de app W01 aislada, suscriptor ContentObserver en proceso primario, aviso vacío `notifyChange` desde servicio remoto y callback recibido. Caso negativo tras unregister (sin callback), `onDestroy/unbind`, provider privado, y nada de saldos/autenticación. El evento **no autoriza** lecturas. El probe NO demuestra que un commit de SQL haya ocurrido, ni que la Activity de Finanzas esté vinculada.

## B2 — Comparación / reutilización
- V4.4 mantiene AtomicApply/receipts/recovery y su fuente de verdad SQL; no existe paridad de ContentResolver IPC para Finanzas en V4.4. No introducir un segundo reality owner ni un bus de eventos productivo.
- Reutilizar `S4W01FinanceReadPrivateInvalidation`, `S4W01FinanceReadPrivateInvalidationProvider`, el URI fijo y la suite JVM ya certificada. `S4W01FinanceReadForegroundReconciliation` conserva el catch-up SQL `ON_RESUME` y se conectará al dueño real en una **unidad posterior D1B** con su B1–B5 y permiso individual.
- No reejecutar las 16 JVM de C ni las 8 JVM anteriores por rutina; esta unidad sólo prueba la brecha *real Android dos PID*.

## B3 — Investigación externa primaria y decisión
- Android `ContentResolver`: https://developer.android.com/reference/android/content/ContentResolver — desde API26 las notificaciones requieren autoridad respaldada por provider; se observa mediante `registerContentObserver`, con callbacks asíncronos. **USAR**.
- Android `ContentObserver`: https://developer.android.com/reference/android/database/ContentObserver — notificación no es un dato persistido, ni garantía de entrega si el proceso está muerto. **USAR**.
- Android servicio ligado / Messenger: https://developer.android.com/develop/background-work/services/bound-services — permite enviar comandos entre procesos del mismo APK mediante framework `Messenger` y `replyTo`, sin AIDL adicional. **ADAPTAR** para sonda DEV temporal.
- Android Multiprocess Espresso: https://developer.android.com/training/testing/espresso/multiprocess — alternativa razonable para UI cross-process, descartada aquí para un evento aislado sin UI; mayor complejidad y dependencia que un test instrumental con servicio remoto.
- Android instrumentación: https://source.android.com/docs/core/tests/development/instrumentation — test usa proceso real Android, no resolver simulado. **USAR**.
- AOSP `ContentResolver.java`: https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/content/ContentResolver.java — inspección de referencia del puente OS.
- **Elegida**: `Service` no exportado en `:w01ReadProbe`, comunicación con `Messenger` (Binder del sistema); emite sólo un `notifyChange` vacío; responde PID y confirmación sin finanzas. En proceso primario test registra observer y valida `pid1 != pid2` y llegada del callback. No hay transferencia de secretos ni estado SQL.
- Rechazar tests JVM de fake resolver como sustituto, `ContentProvider.call` habilitado al exterior, IPC por archivos/polling, broadcast exportado, servicio en package productivo, extraer información financiera de URI, afirmar entrega al 100%.

## B4 — Delta funcional propuesto, cinco rutas exactas
1. **MODIFICAR** `v5/app/build.gradle.kts`: propiedad opt-in única `faroW01AndroidTest=true` (mutuamente excluyente con G5/X03/S3); `testBuildType="w01FinanceDev"` y `sourceSets.androidTest.setRoot("src/w01AndroidTest")`. Sin cambios de variantes productivas.
2. **MODIFICAR** `v5/app/src/w01FinanceDev/AndroidManifest.xml`: añadir sólo `Service` privado `S4W01FinanceReadNoticeProbeService` con `android:process=":w01ReadProbe"`, `exported=false`, bind explícito. Mantener provider y Activity originales. Cero permisos nuevos.
3. **CREAR** `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadNoticeProbeService.kt`: `Service` sin lecturas, `Messenger` para mensaje de test. Sólo cuando el package exacto es `ar.com.faro.alpha.w01financedev` **NO** debe llamar a `S4W01FinanceReadPrivateInvalidation.publishAfterVerifiedCommitForDevOnly`, porque no existe COMMIT SQL. Para prueba estrictamente de transporte debe construir URI vacío fijo `content://<applicationId>.finance9read/invalidated` y llamar directamente a `ContentResolver.notifyChange` desde el servicio probe aislado, con marcador explícito `SYNTHETIC_IPC_ONLY`, sin usar el adapter de SQL ni montar Activity. Mensaje/ACK incluye PID y boolean de señal; no data financiera. No exponer endpoint exportado.
4. **CREAR** `v5/app/src/w01AndroidTest/kotlin/ar/com/faro/v5/app/S4W01FinanceTwoPidNoticeAndroidTest.kt`: usar instrumentation AndroidJUnit4, contar callbacks reales, afirmar PID distintos, URI app privado, signal observer antes/después de unregister, detener/unbind. Timeout acotado, sin sleep arbitrarios, sin datos reales.
5. **CREAR** `.github/workflows/faro-v5-s4-w01-d1a-two-pid-notice.yml`: gate parent EXACT5, preparar SDK37, `-PfaroW01AndroidTest=true`, assemble W01 app y W01 test, ejecutar emulador API35 con `reactivecircus/android-emulator-runner@v2`, verificar `INSTRUMENTATION_CODE: -1` + caso positivo y negativo y package manifest (provider export false, service export false; v2Core 0 providers/services), no UI productiva.

Nota: este mecanismo de *sonda* verifica el **transporte Android**, no un segundo proceso SQL mutando Finance9. La ruta sintética se separa explícitamente del método productivo post-COMMIT y nunca debe llamarlo. D1B conectará el sender cooperante real y el dueño de lecturas/Activity; hacerlo aquí mezclaría identidad humana y lifecycle no certificados. W1 sigue OPEN.

## B5 — Preauditoría adversarial (condiciones STOP)
- R01: el PID del servicio debe ser distinto del proceso test, en Android real; prueba in-process es RED.
- R02: `ContentObserver` callback asíncrono únicamente tras registro. Tras unregister o terminación del owner, no hay nuevos callbacks. Observador late callback no puede renovar acceso.
- R03: mensaje sintético marcado `SYNTHETIC_IPC_ONLY`, NO usar publisher `publishAfterVerifiedCommitForDevOnly` sin COMMIT verificado; `notifyChange` sólo señal **sin** saldo, nombre, identificador de cuenta, principal, epoch, SQL DTO o credenciales. ACK sólo PID + bool. No permisos INTERNET/POST_NOTIFICATIONS.
- R04: el probe `:w01ReadProbe` no se registra en shipping, V2 Core, X03 ni S3. `android:exported=false`, no grantUriPermissions, no intents implícitos.
- R05: no iniciar operación financiera, no autenticación sintética, no false PASS de commit SQL, no FBS2v4, ledger, grants, Apply ni UI financiera.
- R06: emulador API35 real y logs instrumentados con PID/markers y salida `OK (N tests)`; JVM no suple físico/emulador.
- R07: no confundir pérdida de notice con consistencia; `ON_RESUME` SQL sigue obligatorio. No afirmar una garantía de entrega en crash/race.
- R08: D0 debe estar B7 PASS scoped antes del permiso D1A, y B5 independiente/guard docs deben aprobar. Si D0 RED_FIX, trabajar sólo D0.

## B6 y B7
NO RUN. Esta preauditoría no emite READY ni autoriza código. D0 ya cerrado B7 scoped, siguiente B5 independiente de D1A y emitir READY exact5 sólo tras guardia verificada. D1B host read lifecycle separado; W2 STOP, V2 productiva OFF.
