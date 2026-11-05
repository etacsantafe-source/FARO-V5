# S3.A2-G1 ACTIVE_CHAT_READ_POINTER_ABA — JUnit RED_FIX B6 WAIT_CI (04/11/2026)

**Unit:** `S3_A2_G1_ACTIVE_CHAT_READ_POINTER_ABA`. **SHA nuevo:** `dec1024439fdddc7367d0c98b993b4e0da207e13`. **Padre READY:** `870c0bb81e01c59e71325c2a9fdd8f19b86d16de`. **Preflight confirmado:** `#41937785131` SUCCESS, `FARO_PROTOCOL_PREFLIGHT_PASS S3_A2_G1_ACTIVE_CHAT_READ_POINTER_ABA`.

## B6 COMMIT Y CI PENDIENTE
- Diff EXACTO de RED_FIX: solo firma `@Test fun durablePointerAbaFailsClosedWithoutAnyListener(): Unit = runBlocking {` en `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5ActiveChatReadSessionT2Test.kt`. No se tocó código del host productivo, XML exigido, esquema, V2 flag ni paquete Android.
- Motivo anterior B7 RED_FIX `212679f7`: oráculo `#41936856517` red porque ejecutó **5/5** de 6 anotaciones `@Test` (método infería retorno Throwable). No reducir el oráculo.
- CI nuevo sobre SHA `dec10244`: `#41937907113` guard **SUCCESS**, `#41937907083` S3 Home + Chat **RUNNING** a este checkpoint. Exigir los XML reales: Home9/9, Workspace7/7, bridge5/5, Avisos4/4, Chat pointer ABA **6/6** sin skips/errores/fallos. No declarar PASS hasta log + job COMPLETED SUCCESS.
- El código de producción de la unidad no cambió en RED_FIX y la CI anterior #41936856422 V2 core y #41936856486 APK DEV fueron SUCCESS a SHA `212679f7`; esos tests **no se repiten** porque el único delta es la firma JVM. La verificación nueva del módulo `app` cubre la corrección.
- B7 independiente deberá certificar: Java/Kotlin correctos, los SEIS métodos efectivamente descubiertos y ejecutados, pointer CAS A→B→A denegado, ninguna activación de V2 productiva. Si rojo, RED_FIX misma unidad; si verde, PASS solo para pointer ABA en lectura de Chat, **NO G1 ACL global**.
- S2 8/8 PASS 100%; S3 desarrollo aproximado ≈40%, cierre 0/5; S3.A2 0/5 gates; S4/V2 productiva/Auto/Noche OFF. APK física no instalada. **Tiempo humano no medido.**
