# FARO V5 — M5 APK real y admisión DEV aislada, B1–B5 (04/11/2026)
**Unidad:** `M5_DEV_APK_PACKAGE_GATE`. **HEAD base revisado:** `92060b09366585ba1736bf6bdafc8ba05467405f`, rama `work/s3-night-20261102`. **Estado:** B1–B5 completados como plan técnico acotado, B6 NO EMPEZADO, B7 PENDIENTE, permiso funcional BLOCKED; M5 global y `V2_DEV_CORE_READY` siguen ABIERTOS. No V2 productiva ni campaña física en este checkpoint.

## B1 EVIDENCIA — brecha precisa
- M4 **B7 PASS** `docs/v5/FARO_V2_M4_B7_PASS_20261104.md`, blob `7137494b70d565ca344728c3b26ed17a7fae35f4`: código `aba2850c` no permite `uses-permission*` en el manifest empaquetado v2CoreDev; CI dirigida #41896913438 SUCCESS, XML V2 6/6 JVM + T4 7/7, manifest M1 y M2B 4/4 cada uno; S3 #41896913436 SUCCESS 25/25; guard #41898005020 SUCCESS en HEAD documental `92060b09366585ba1736bf6bdafc8ba05467405f`. No repetir estos PASS si no cambian contratos.
- Workflow actual `.github/workflows/faro-v5-v2-core-directed.yml` compila `:app:compileV2CoreDevKotlin` y `processV2CoreDevManifestForPackage` y verifica manifiestos y JVM, **pero NO ensambla APK instalable** ni inspecciona manifiesto binario del APK resultante.
- `FaroV5V2CoreDevActivity.kt` exige app ID `ar.com.faro.alpha.v2coredev`, build debug, V2 productiva OFF, click humano y root `noBackupFilesDir/faro-v2-core-dev-root`. Sólo conecta Chat local real (read/timeline/selection/composer), sin modelo, Review/Apply, notificaciones ni Search shipping.
- `FaroV5V2CoreDevAuthority.kt` usa journal/pointer/replay fence y cinco SQLite; M2A cerró pruebas recovery de PREPARING/STAGED/VALIDATED en JVM. NO hay evidencia aún de force-stop/restart por Android, proceso kill, health/WAL físico, APK firmado instalable, acceso a root V1 habitual o ausencia de salida dinámica IPC.
- `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json` está BLOCKED tras M4 B7. La unidad M5 requiere permiso propio y run exacto antes de código.

## B2 COMPARACIÓN — V4.4 solo lectura / reuso V5
- Comparación V4.4 ya documentada en `docs/v5/FARO_V2_RETRO_B1_B5_M0_M3_20261104.md`: la V4.4 usa MainActivity productiva, Internet, boot receiver y almacenamiento propio. **NO copiar esos permisos, components ni el almacenamiento real** al UID de desarrollo.
- Reutilizar `v2CoreDev` package / root / launcher, `SingleProcessMaterialWriteGate`, cinco stores, CAS, journal, tests M4 y cadena GitHub CI. No crear otra Activity, owner ni fork funcional de V4.4.
- El salto sin certificar es **APK final y proceso real**: la JVM y el manifest intermediario no son equivalentes a un APK que se pueda instalar. Un artefacto sólo permite preparar prueba física, no equivale a prueba física.

## B3 INVESTIGACIÓN — opciones razonables
- **USAR:** Gradle `:app:assembleV2CoreDev` en GitHub Actions, solo DEV. Android AAPT2 oficial `aapt2 dump packagename|badging|permissions <apk>` permite inspeccionar el manifest **binario del APK final**, no depender solo de un archivo intermediario: https://developer.android.com/tools/aapt2 .
- **USAR:** `actions/upload-artifact@v4` con `retention-days: 5`, `if-no-files-found: error`; la documentación oficial permite conservar el APK generado para una sola prueba posterior: https://docs.github.com/en/actions/tutorials/store-and-share-data .
- **ADAPTAR:** nuevo workflow `faro-v5-v2-core-apk-directed.yml` sin modificar M4 ni shipping; `aapt2` ya incluido en build-tools 36 del SDK CI. Primero build/inspect; sólo si PASS, publicar artefacto. Integración de pruebas físicas se documenta como gate separado.
- **REFERENCIA/POSTERGAR:** instrumentación Android/Emulator en GitHub y pruebas de proceso in-CI; puede consumir minutos y no sustituye el Moto G15 real. Reconsiderar sólo si M5 no alcanza aceptación en campaña física única. No meter nuevos servicios/secretos.
- **DESCARTAR:** declarar `V2_DEV_CORE_READY` por Kotlin compile, habilitar un launcher de producción, mover root de datos, firmar con credenciales productivas o introducir red/IA/Apply para acelerar.

## B4 UNIFICACIÓN — diseño sin duplicar
- Alcance funcional propuesto de B6: **crear únicamente** `.github/workflows/faro-v5-v2-core-apk-directed.yml`, separado del workflow M4 ya PASS. No editar aplicación ni manifests salvo que CI descubra falla específica, lo que obligará a nueva auditoría causal.
- Workflow con `on: push` rama `work/s3-night-20261102` filtrado a sí mismo, `v5/app/src/v2CoreDev/**`, `v5/app/build.gradle.kts` y `v5/app/src/main/**`, además de `workflow_dispatch`; permisos `contents: read`, checkout sin credenciales, JDK21, SDK compile37 con Android-37.0, build tools36, sin cancel-in-progress.
- Ejecutar `./gradlew --no-daemon --max-workers=2 --no-parallel --no-configuration-cache :app:assembleV2CoreDev`. Un único APK en `v5/app/build/outputs/apk/v2CoreDev/`; cero o múltiples => STOP. Validar APK mediante `$ANDROID_HOME/build-tools/36.0.0/aapt2 dump packagename`, `permissions` y `badging`: ID exacto `ar.com.faro.alpha.v2coredev`, **sin permisos declarados**, Activity launcher exclusiva; volver a comprobar `debuggable` si la salida de AAPT2 expone dato inequívoco. No aceptar rutas por ordinal ni regex permisiva.
- Publicar sólo después del chequeo exacto un artefacto ZIP Actions con único APK, nombre que indique `V2-CORE-DEV`, SHA del commit y retención 5 días; no generar APK de app habitual ni mezclar otros paquetes. Registrar SHA/run/manifest/result/artefacto para entrega futura.
- La campaña física **separada** en Moto G15 requiere decisión/participación explícita para instalar paquete DEV independiente, abrir Chat, aceptar mensaje USER real, forzar detención/reabrir, revisar persistencia y verificar que no aparezcan permisos/egress/OS/Apply, sin tocar FARO habitual. `V2_DEV_CORE_READY` sólo tras integración y recovery aceptadas, no por artifact upload.

## B5 AUDITORÍA ADVERSARIAL PREVIA
1. Identidad: comparar paquete del APK **binario**, no solo intermedio. ID distinto de app habitual y V4.4; launcher exacto; no componente extra, backup OFF. Nunca publicar artefacto si mismatch.
2. Egress: `aapt2 dump permissions` **vacío** salvo encabezado, cero INTERNET/WAKElOCK/BIOMETRIC/BOOT, y comparar con M4 manifest empaquetado aprobado. Ante cambio de dependencias, STOP. Permisos ausentes no prueban cero IPC dinámico; ese residual queda explícito.
3. Seguridad de artefacto: exigir 1 archivo APK, archivo no vacío, hash SHA256 registrado en logs, artefacto generado sólo luego de todas las negativas PASS; CI no contiene secretos, no firma release.
4. Recovery: nunca borrar WAL, journal, generation ni pointer para simplificar. La campaña física debe probar cierre/reapertura y force-stop sin destrucción de datos y sin doble creación de mensajes; los tests JVM M2/M3/M4 se reutilizan, no se duplican.
5. Writer: GitHub CAS exclusivo, HEAD previo, checkpoint y release temprano. Un fallo CI implica B7 RED_FIX o STOP con evidencia; no saltarse B1–B5 ni ampliar permiso a archivos no inspeccionados.
6. Alcance humano: no instalar nada automáticamente en el dispositivo; no activar V2 productiva, Noche, Auto ni APIs pagas. S2 CLOSED 8/8, S3 ≈40% desarrollo / 0 de 5 formal, S4 OFF, catálogo 172.
7. Aceptación B6/B7: workflow nuevo compila APK y verifica paquete/permisos/launcher con log exacto, publica artefacto de SHA verificado; B7 independiente decide **M5_APK_GATE_PASS**, no `V2_DEV_CORE_READY` si falta la sesión física.

## B6 IMPLEMENTACIÓN Y CI
**PENDING.** Este documento no emite permiso READY ni certifica APK.

## B7 AUDITORÍA FINAL
**PENDING.** Tras B6 dirigida se exige SHA/logs/manifiesto/artefacto y, por separado, una campaña física única. M5 global abierto hasta cerrar todos los criterios.
