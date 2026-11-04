# S3.A2-G1 — misma unidad ACTIVE_CHAT_READ_POINTER_ABA · RED_FIX B1–B5 (04/11/2026)

**HEAD revisado:** `563cdffca87048a75d347eb9a6d9647f8c6a6b26`. **unit_id sin cambiar:** `S3_A2_G1_ACTIVE_CHAT_READ_POINTER_ABA`. V2 productiva OFF; writer CAS; no otra unidad ni fase abierta.

## B1 EVIDENCIA
- Log de S3 Home `#41936856517` @ `212679f7`: `S3_CHAT_POINTER_ABA_XML_FAIL (5,0,0,0)` pese a seis `@Test` en `FaroV5ActiveChatReadSessionT2Test.kt`. Home 9/9, Workspace 7/7, Search bridge 5/5, Avisos 4/4 conservaron PASS.
- Método sospechoso: `@Test fun durablePointerAbaFailsClosedWithoutAnyListener() = runBlocking { ... assertThrows(...) }` termina en `assertThrows` que devuelve un objeto Throwable. El método JVM no queda void; JUnit omite un método no void. B7 del SHA previo documentado con esa evidencia y **RED_FIX**, nunca PASS.
- Otros workflows de `212679f7` siguen independientes; ningún resultado nuevo se inventa.

## B2 COMPARACIÓN
- V4.4 `FaroV4GlobalSearch.kt` contiene tests de búsqueda/normalización y degradación, pero el problema de retorno de JUnit es de infraestructura de prueba, NO de comportamiento V4.4 ni algoritmo. No modificar V4.4 ni reabrir pruebas de Search DEV `c3f5e5d1` B7 PASS.
- Preservar código de puntero/lectura del primer commit y los cuatro tests anteriores más la segunda negativa nueva; no rehacer la clase del host ni su implementación.

## B3 INVESTIGACIÓN EXTERNA
- JUnit User Guide `https://docs.junit.org/6.1.0/writing-tests/test-classes-and-methods.html`: `@Test` no debe devolver valor. Kotlin `runBlocking<T>` `https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/run-blocking.html`: su resultado es exactamente el valor de la última expresión. **USAR:** forzar `: Unit` en el método.
- OWASP `https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html` y Element X PR #7877 `https://github.com/element-hq/element-x-android/pull/7877`: no debilitar una prueba negativa de revocación solo para conseguir verde; cobertura exacta como evidencia.
- Alternativas: cambiar oráculo a 5/5 **DESCARTADO** (false PASS); eliminar test **DESCARTADO**; pasar todo a `suspend fun` JUnit **DESCARTADO** (impacto mayor y compatibilidad variable); agregar `: Unit` **USAR**, delta mínimo sin dependencia/licencia/costo.

## B4 UNIFICACIÓN
- Cambiar únicamente `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5ActiveChatReadSessionT2Test.kt`: `@Test fun durablePointerAbaFailsClosedWithoutAnyListener(): Unit = runBlocking {`.
- No tocar lógica, setup, payload, tests anteriores, V2 DEV, manifest, gradle ni workflow: oráculo exigirá **6/6** sobre SHA nuevo. El fallo ya se identificó sin ensayo-error.

## B5 AUDITORÍA ADVERSARIAL
- El nuevo método debe compilar a retorno void y figurar ejecutado junto al test A→B→A durante lectura. No dar PASS si JUnit XML es 5/5, 6/6 con skips/failures, o si el test se excluye.
- Mantener `FileActiveGenerationPointer` REAL en el test, sin listeners; siempre denegar ports viejos tras A→B→A. Cero cambios de producción en este RED_FIX.
- Alcance único, mismo `unit_id` por B7 RED_FIX. Nuevo READY en un padre documental, preflight obligatorio, CAS writer, B6, CI exacto y B7 independiente. Sin activar V2 shipping ni cerrar G1 (0/5).
- **B5 READY_FOR_SAME_UNIT_JUNIT_REDFIX_ONLY**.
