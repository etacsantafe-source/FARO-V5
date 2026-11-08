# FARO V5 · S3.A4 N031 DEV Pendientes · RED_FIX B1–B5
2026-11-08 AR. Unidad EXACTA `S3_A4_N031_DEV_PENDING_UI_MOUNT`. Base anterior `77d5bfa19bf9bc191d44b364a45955e03672f5e2`; código no certificado, B7 RED_FIX. Retiene del original `docs/s3-night/S3_A4_N031_DEV_PENDING_UI_MOUNT_B1_B5_20261108.md` todos los límites de seguridad.

## B1
- El nuevo test dirigido #42521187552 falló EXCLUSIVAMENTE al seleccionar una tarea Gradle ausente `testV2CoreDevUnitTest`, antes de test ni compilación del target de test. Alcance vigente: la pantalla DEV aislada y renderizador bounded, no schema8 migration ni shipping. La guardia #42521187437 pasó, no prueba unidad 6/6.
- Ningún resultado del test se puede declarar en verde hasta XML exacto. M5 APK final se observa por separado. Se conserva Reality7 => unavailable, sin 0 falso.

## B2
- Reusar los 5 archivos del último commit; cambiar solo ubicación física del renderizador (y prueba) conservando código y API: desde `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevPendingPresentation.kt` a `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevPendingPresentation.kt` (sin caller shipping), desde `v5/app/src/testV2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevPendingPresentationT2Test.kt` a `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevPendingPresentationT2Test.kt`. Los callbacks del launcher DEV quedan intactos.
- Exigir ausencia de class duplication y guard unchanged. No cambio a bases/owners de búsqueda.

## B3
- Gradle Android variants https://developer.android.com/build/build-variants y Gradle testing https://docs.gradle.org/current/userguide/java_testing.html: `testDebugUnitTest` está comprobada en B7 anteriores del MISMO repo; no extrapolar tarea custom por nombre.
- OWASP default DENY https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html permanece obligatorio. Alternativa descartada: pasar test as skipped o reetiquetar APK como test. La prueba nueva debe ejecutarse de verdad en defaultDebug sobre la lógica pura del display.
- No se requiere reauditar source SQLite ya certificado; el diff no afecta read binding.

## B4
- Cambiar/borrar exactamente cinco rutas: las dos rutas antiguas que se borran, las dos nuevas main/test y `.github/workflows/faro-v5-s3-a4-n031-dev-pending-ui-directed.yml`. Workflow pasa a `:app:testDebugUnitTest --tests ar.com.faro.v5.app.FaroV5V2CoreDevPendingPresentationT2Test` y comprueba XML 6/6 en `testDebugUnitTest`. M5 se dispara por main y paths existentes.
- Sin tocar `FaroV5V2CoreDevActivity`, `FaroV5V2CoreDevAuthority`, `RealityStore`, `GraphOwner`, Manifest, Apply ni política V2 OFF.

## B5
- Gate antes de B6 exige nuevo permiso READY, previous last_code_review RED_FIX ligado al SHA `77d5bfa...` con evidencia exacta de B7 RED_FIX.
- CI nuevo y M5 APK deben dar PASS exact SHA para B7 final. Si falla algo, STOP y nueva RED_FIX; no pasar a A4 formal. Solo cerrar alcance automatizado de A4 si criterios originales quedan satisfechos y limitaciones sin esconder en A5.
- No efectuar prueba física G15 en esta unidad.
