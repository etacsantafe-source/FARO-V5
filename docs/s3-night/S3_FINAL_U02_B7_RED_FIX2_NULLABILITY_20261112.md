# S3 final U02 — B7 RED_FIX2: Android nullable metadata test
Fecha AR2026-11-12. Misma unidad S3_FINAL_U02_DISTINCT_IA_APP, SHA43740e810db13174eb60dccfbe847efe2a3bdd48.
## B1
Run43343128100 job131615991432 FAILURE: compileX03AiDevKotlin y assembleX03AiDev SUCCESS; compileX03AiDevAndroidTestKotlin falla UNSAFE_CALL sobre Array<ActivityInfo>? test línea46. El defecto sourceSet anterior se resolvió; esta es otra causa del test. Android no ejecutado, sin XML ni PASS. Artefacto14000563293 NO ENTREGAR. Guard43343128317 SUCCESS.
## B2
La suite nueva usa PackageInfo de Android37 con anotación nullable, unlike colecciones internas V5. El test no debe asumir campos no nulos por GET_ACTIVITIES. Revisados todos sus accesos: activities y requestedPermissions necesitan requireNotNull; providers/services/receivers usan isNullOrEmpty, sharedUserId se espera null. No modificar API ni host ni S2. Se preserva Kotlin sourceSet corregido.
## B3
Fuente primaria Android SDK PackageInfo https://developer.android.com/reference/android/content/pm/PackageInfo (activities/requestedPermissions pueden faltar); Kotlin https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/require-not-null.html : exigir presencia o fallar. Alternativa elegida requireNotNull fail-closed; DESCARTAR orEmpty para esconder metadatos ausentes, suprimir nullability o eliminar aserción de componentes. Sin dependencias. Se reaudita causa de compilación completa en lugar de rerun idéntico.
## B4
Una ruta exacta v5/app/src/x03AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3FinalDistinctAppUiTest.kt. Introducir val activities=requireNotNull(info.activities), usarla en las dos aserciones; requireNotNull(info.requestedPermissions).toSet(). El resto de prueba y workflow sin cambios. La misma suite nueva2/2 verifica runtime/manifest/UID/consent; no duplicar X01–X12.
## B5
Metadatos ausentes deben FAIL con mensaje, no hacerse conjunto vacío. Exigir exactamente ambas Activities y permisos previos. No relajación de launcher/exports/UID ni política de Free. Es el segundo fallo de compilación de la unidad pero una nueva causa: fuente Activity resuelta y nulabilidad SDK identificada; B2/B3/B5 reabiertos aquí antes de permiso. Si vuelve la MISMA causa, STOP para revisar mecanismo, nunca rerun ciego. Ningún test ejecutado hasta ahora ni APK certificada.
## B7
RED_FIX del SHA43740e810db13174eb60dccfbe847efe2a3bdd48, no PASS. Cambios previos auditable: fuenteSet un-path, preflight y permiso guard43343019346; source guard actual SUCCESS. Requiere nuevo READY doc separado y preflight exacto. Writer liberado durante CI. S3 global OPEN, U01 política humana/U03 dependencias/U04 humano no cambian.
