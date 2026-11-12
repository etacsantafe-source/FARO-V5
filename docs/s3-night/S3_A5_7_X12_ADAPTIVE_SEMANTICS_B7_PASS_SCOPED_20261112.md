# FARO V5 — X12 Adaptive Accessibility Semantics, B7 PASS scoped
Fecha: 2026-11-12 (Argentina). Unidad `S3_A5_7_X12_ADAPTIVE_ACCESSIBILITY_SEMANTICS_ANDROID`.

## B1–B5 reutilizados, sin repetir pruebas
Evidencia previa: `S3_A5_7_X12_ADAPTIVE_SEMANTICS_B1_B5_20261112.md`; deltas RED_FIX1 geometría, RED_FIX2 touch bounds, RED_FIX3 serialización de consulta en `S3_A5_7_X12_B7_RED_FIX3_QUERY_SERIAL_20261112.md`. Se preservaron asserts de grant, canonical search, revocación, rol/semántica de botones, tamaño táctil y configuraciones. RED_FIX3 reordena el probe independiente ANTES de iniciar UI; no altera código de producción.

## B6 verificado en SHA real
Código exacto `2da58971b2258218964abd4a31454adb2738ba98`.
Android API35 CI run `43165047469`, job `131004870678`: **SUCCESS**; guard documental `43165047475`: **SUCCESS**, ambos mismo SHA.
Instrumentación cruda (artefacto 13919562852) contiene START1/END0 único para `x12ExpandedWindowNativeDrawerAndRestoredSourceIsolation` y `x12LargeFontCanonicalAvisosActionsAndRevocation`; `OK (2 tests)`, `INSTRUMENTATION_CODE: -1`. XML `x12-adaptive-android-junit.xml`: tests=2 failures=0 errors=0 skipped=0. No se lanzó un CI nuevo ni se repitieron suites certificadas.

## Auditoría B7 independiente del ZIP
Se descargó el ZIP original del artifact 13919562852; 18 entradas, CRC íntegros sin entrada defectuosa. SHA-256 ZIP `e94aedbdff002135e37e8dc77107948c7a4ee3845e39ad8132a10f827939b8f1` coincide con digest publicado por GitHub Actions.
`x12-adaptive-source-sha.txt` coincide con SHA del run. APK app `v5/app/build/outputs/apk/v2CoreDev/app-v2CoreDev.apk`: SHA-256 `dc783842ef5bb9309aa918e745edcd5e18bec1f0404b382054c19854c6aba365`. APK androidTest: SHA-256 `6cb3859fae8ec32aa95ab9b894c5442369eac32feaaac56fa96ce3eebba83c96`. Ambos hashes recalculados desde bytes ZIP y coincidentes con el registro incluido.
Manifest/badging: paquete DEV `ar.com.faro.alpha.v2coredev`; launcher `ar.com.faro.v5.app.FaroV5V2CoreDevActivity`; versión `5.0.0-dev-v2coredev` code 66, minSdk 29 targetSdk 36; lista de permisos sin `android.permission.INTERNET`, sin Activity AI aislada X03 U02. Ejecución en emulador offline (wifi/data disabled).
Configuraciones registradas: fontScale=2.0 screenWidthDp=393; expanded fontScale=1.0 screenWidthDp=1600, override físico 1600x1200/density160. Capturas de font200, expanded y Avisos para ambas configuraciones presentes e inspeccionadas: contenido/controles legibles, navegación persistente expandida, fuente canónica de aviso y resumen mostrado; no se observa dato sensible/ID explícito. Las capturas no sustituyen los asserts programáticos de accesibilidad.

## B7 — Dictamen y límites
**PASS_SCOPED_ANDROID_API35_2_OF_2.** Solo prueba X12 adaptive font200 / ventana expandida y semántica correspondiente en V2 CORE DEV aislado, con las dos pruebas dirigidas y material descargado. NO implica TalkBack/SwitchAccess físico, comportamiento de otra pantalla, aplicación productiva ni cierre X01–X12/A5 global.
Moto G15 NOT_RUN, G15 humano separado. No instalar el APK androidTest en móvil, no sobrescribir S2 aislada, no activar V2 productiva, Internet, Groq, S4, robots o Noche. El permiso funcional X12 queda CONSUMED/BLOCKED. Próxima unidad independiente: `S3_A5_7_X06_FOUR_OWNER_SIMULTANEOUS_ANDROID`, B1–B5 preparado, requiere guard documental, permiso READY propio y preflight de dos rutas antes de B6; aceptación integral y campaña final aún OPEN.
