# S3.A2-G1 ACTIVE_CHAT_READ_POINTER_ABA — B7 RED_FIX de descubrimiento JUnit (04/11/2026)

## B7 AUDITORÍA FINAL
**Código inspeccionado:** `212679f7c2dc211445dc7f806479b83aea8780e0` · **unit_id:** `S3_A2_G1_ACTIVE_CHAT_READ_POINTER_ABA` · **dictamen:** **RED_FIX**, no DONE.
- CI obligatorio `#41936856589`: SUCCESS, solo protocolo.
- S3 Home directed `#41936856517`: **FAILURE** en el oráculo `S3_CHAT_POINTER_ABA_XML_FAIL (5, 0, 0, 0)`, aunque el fuente tiene 6 anotaciones `@Test`. Los XML de Home 9/9, Workspace 7/7, bridge 5/5 y Avisos 4/4 sí imprimieron PASS. La suite de Chat ejecutó **5**, no 6.
- Análisis estático del código: `@Test fun durablePointerAbaFailsClosedWithoutAnyListener() = runBlocking { ... assertThrows(...) }` termina con `assertThrows`, que devuelve una excepción. Kotlin infiere tipo de retorno **no Unit** para el método; JUnit Jupiter descarta `@Test` de retorno no void. El otro test nuevo `pointerAbaDuringReadFailsOnPostReadCheck` es de bloque y ejecutó en los cinco contabilizados.
- **Prueba/causa externa verificada:** documentación JUnit `https://docs.junit.org/6.1.0/writing-tests/test-classes-and-methods.html` exige métodos sin retorno; Kotlin `runBlocking<T>` devuelve `T` `https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/run-blocking.html`. No confundir 6 anotaciones con 6 tests ejecutados.
- V2 core `#41936856422` y APK DEV `#41936856486` estaban ejecutándose al detectar esta falla; conservar sus resultados reales posteriormente, no darlos por PASS.
- **Corrección requerida:** misma unidad, solamente hacer que `durablePointerAbaFailsClosedWithoutAnyListener` tenga retorno explícito `: Unit`; no debilitar el oráculo 6/6 ni quitar test. Reemitir B1–B5 y READY documental separado antes de corregir.
- La falla es de cobertura de pruebas, NO evidencia de fuga producida. B7 NO PASS hasta que el nuevo SHA demuestre **6/6** y las regresiones completas. V2 shipping sigue OFF; S3.A2 G1 OPEN, S2 8/8, S3≈40% estimado/0/5 formal.
