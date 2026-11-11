# FARO V5 — S3.A5.7 X04/X05 — B7 RED_FIX4 compilación de gesto Compose

Unidad: `S3_A5_7_X04_X05_ANDROID_GUARDADOS`. Commit funcional auditado `10be3a4f780a9ddd9f7da8d64923099eec9510f6`.
Run Android `43081207104` / job `130734208838`: compilación Kotlin AndroidTest FAILED. Guard protocol `43081207163`: SUCCESS. Home notice `43081207019`: SUCCESS.

## B7 — RED_FIX

- El código productivo y APK DEV compilaron. `:app:compileV2CoreDevAndroidTestKotlin` rechazó la referencia de test `swipeUp` en `FaroV5S3A57BookmarksUiTest.kt:129`: `UNRESOLVED_REFERENCE`.
- Causa concreta: se añadió `performTouchInput { swipeUp() }`, pero faltó importar la extensión oficial `androidx.compose.ui.test.swipeUp`. La documentación Android de `SemanticsNodeInteraction.performTouchInput` muestra explícitamente el import: https://developer.android.com/reference/kotlin/androidx/compose/ui/test/SemanticsNodeInteraction .
- No se ejecutaron X04 ni X05 en el emulador para este SHA: **0/2 NOT_RUN**. Ningún `OK (2 tests)`, XML o artifact aprobado.
- Corrección limitada y verificable: insertar una línea `import androidx.compose.ui.test.swipeUp` al test Android existente, sin modificar métodos, workflows, dependencias ni producción.
- Mantener B1–B5 y alcance previo; permisos READY mismo `FARO-UNIT`, writer CAS, preflight, CI real API35 2/2, guard, XML/APKs, B7 independiente. Si ejecución revela problemas reales de autorización fuera de rutas, `NEEDS_PREP`, no corregir código ajeno.
