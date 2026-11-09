# FARO V5 — S3.A5.7 Compose Chat B7 RED_FIX3 selector de colección
Fecha AR 2026-11-09. Unidad S3_A5_7_COMPOSE_CHAT_REAL_ANDROID. GLOBAL A5 OPEN.

## B1 — Evidencia exacta
- B1–B5 originales docs/s3-night/S3_A5_7_COMPOSE_CHAT_REAL_ANDROID_B1_B5_20261109.md (blob 320d7895b4998f0e988266005bab9a647a169374).
- Primer código d0c61074 CI #42592348610 FAILURE: Kotlin unresolved import assertExists. Segundo código 4186e0b2 CI #42593488175 FAILURE: Kotlin unresolved import onAllNodes; revisión B2/B3/B5 y B7 RED_FIX2 documentada docs/s3-night/S3_A5_7_COMPOSE_CHAT_B7_REDFIX2_IMPORTS_AUDIT_20261109.md.
- Tercer código 9ddd585162bc1fb6e7f3522f3149d797968f9d86 CI https://github.com/etacsantafe-source/FARO-V5/actions/runs/42594626211, job 129084156396, COMPLETED FAILURE en compileV2CoreDevAndroidTestKotlin: Kotlin UNRESOLVED_REFERENCE onLast on SemanticsNodeInteractionCollection, archivo FaroV5S3A57ComposeChatUiTest.kt línea86. BUILD FAILED 4m50s. Android emulator tests NOT_RUN. Guard https://github.com/etacsantafe-source/FARO-V5/actions/runs/42594626180 COMPLETED SUCCESS en mismo SHA.

## B2 — Comparación y diagnóstico
- Tests Compose FARO previos usan onAllNodes(hasSetTextAction()).fetchSemanticsNodes(). Los imports inválidos previos ya están eliminados; la tercera falla es selector no resuelto por Kotlin, NO una regresión de host DEV.
- La API Compose documenta onLast como extensión que requeriría import y la colección ofrece operador miembro get(index), disponible desde 1.0.0. Evitar más ensayo-error con imports de extensiones.

## B3 — Investigación externa
- Referencia oficial AndroidX https://developer.android.com/reference/kotlin/androidx/compose/ui/test/SemanticsNodeInteractionCollection: operador miembro get(index: Int) validado, onLast extensión.
- https://developer.android.com/develop/ui/compose/testing: uso de onAllNodes y semántica Compose.
- Alternativa A USAR: editors[editors.fetchSemanticsNodes().lastIndex] selecciona el último editor mediante get sin import nuevo. B DESCARTAR: agregar import onLast dependiente de versión, innecesario. C DESCARTAR: testTag en producción para un test, fuera de autorización.

## B4 — Modificación mínima
- Solo fichero v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57ComposeChatUiTest.kt.
- Sustituir expresión editors.onLast().performTextInput(body) por editors[editors.fetchSemanticsNodes().lastIndex].performTextInput(body); conservar todos los asserts y los dos tests.
- No tocar workflow, producción, Android manifest, stores, Gradle, S2/S4, catálogo172, A3, V2 productiva, Auto/Noche ni Moto G15.

## B5 — Auditoría adversarial y criterio STOP
- Ya existe assert de tamaño mínimo dos editores y waitForIdle luego de abrir Activity. Acceso indexado invoca member API estable; si no hay campo válido, la prueba falla, no falsifica dato.
- Próximo CI debe producir dos instrumentaciones Android API35 reales con stdout OK (2 tests), INSTRUMENTATION_CODE: -1 y guard B1–B7 del mismo SHA. Si otra falla, volver a B2/B3/B5 sobre causa real y no debilitar asserts.
- Mantener seis subunidades 33/33, X07/X11 4/4 y X12 native 2/2 intactas. X01/X02 preparado B1–B5 DOC_ONLY, no nuevo código hasta dictamen Compose B7.

## B7 — Veredicto del SHA anterior
RED_FIX, NO PASS. Código 9ddd585 falló al compilar test Android por onLast sin resolver; ninguna instrumentación ejecutada. Única corrección autorizada B4, misma unidad y permiso READY de padre tras documentar este B7. GLOBAL A5 OPEN; G15 una campaña física final.
