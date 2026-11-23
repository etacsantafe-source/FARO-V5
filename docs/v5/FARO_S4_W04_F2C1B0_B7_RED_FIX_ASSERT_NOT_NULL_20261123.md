# FARO V5 — W04 F2C1B0 · RED_FIX de compilación del test JVM · 2026-11-23

Unidad `S4_W04_F2C1B0_SELECTED_ACTIVE_TYPED_PROJECTION_DEV`. Código en fallo `169a9ddaf5191aae6c434dd4ba80cd982ff5ea7f`. Ramas de producción y V4.4 NO modificadas. Writer preaudit RELEASED epoch1686.

## B1 — Evidencia real (compilador)
- Guardia del commit `45118646553` SUCCESS; permisos del código exact3 conformes.
- La regresión S3 Home de ese SHA, run `45118646536`, job `137507137313`, terminó FAILURE en `:app:compileDebugUnitTestKotlin` antes de ejecutar casos. Log Kotlin: `UNRESOLVED_REFERENCE`, ubicación `S4W04SelectedActiveTypedReadProjectionTest.kt:68`, `Unresolved reference 'household' on receiver of type 'Unit'`. No error comprobado en la fuente funcional nueva.
- Causa exacta: `org.junit.jupiter.api.Assertions.assertNotNull` devuelve Java `void` / Kotlin `Unit`, NO el valor comprobado. Dos `val data = assertNotNull(project())` en T01 y T05 no guardan `Projected` (segunda ocurrencia predecible tras el primer error). Usar `requireNotNull(project())` retorna `Projected` o falla el test, sin cast ni contrato nuevo.
- CI dirigida F2C1B0 `45118646616` todavía puede estar en curso: no confundir resultado pendiente con PASS.

## B2 — Comparación y reuse
- V4.4/SmartCompra y F2C0/F2C1A E/F1 no participan en esta falla. Reusar implementación completa del adaptador sin tocarla; único delta test de la misma unidad.
- No repetir 6 pruebas SQLite F2C0 ni Android 4 F2C1A; tampoco resetear estado SAFE por aparente fallo de producción.

## B3 — Patrón y alternativa
- JUnit5 Assertions `assertNotNull`: https://junit.org/junit5/docs/current/api/org.junit.jupiter.api/org/junit/jupiter/api/Assertions.html (void). Kotlin `requireNotNull`: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/require-not-null.html (T & Any).
- Alternativa A: `assertNotNull(project()); val data = project()!!`: DESCARTAR por leer dos veces con semántica de revocación.
- Alternativa B: `val data = requireNotNull(project())`: USAR, un solo read, falla ante null sin crear autorización.
- Alternativa C: `val data = project(); assertNotNull(data); data!!`: válido pero redundante. Preferir B.

## B4 — Delta exacto
Solo `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04SelectedActiveTypedReadProjectionTest.kt`: sustituir ambas asignaciones `val data = assertNotNull(project(...))` por `val data = requireNotNull(project(...))`, retirar import unused de JUnit `assertNotNull`. Mantener métodos T01–T06, datos sintéticos, granteo negativo, comprobación de watermark/UNKNOWN y snapshot exacto. Permiso original READY exact3 sigue vigente, misma unidad. Ningún código fuente ni workflow modificado.

## B5 — Verificación de corrección
El cambio nuevo exige guardia GitHub SUCCESS, directed JVM `45118646616` (anterior) no reutilizable, **CI nueva** con SHA de fix + XML 6/6 exactos (0 failures, errors, skipped) y regresión Home si el workflow se dispara. B7 independiente sobre ZIP/XML real del SHA corregido. Si otro error concreto aparece, reauditar solo su causa, no adivinar correcciones.

## B7 — RED_FIX (NO PASS)
La salida real observada es `RED_FIX_TEST_COMPILER_ASSERT_NOT_NULL_UNIT` sobre código `169a9ddaf5191aae6c434dd4ba80cd982ff5ea7f`. Este documento autoriza reparar solo las dos expresiones de prueba bajo el permiso previo de `S4_W04_F2C1B0_SELECTED_ACTIVE_TYPED_PROJECTION_DEV`, sin declarar B6/B7 terminado ni activar V2 productiva. Writer se liberará tras publicar la documentación; jamás permanecer HELD durante CI.
