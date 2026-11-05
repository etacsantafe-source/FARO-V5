# FARO V5 — S3.A2-G1 · same-unit JVM suspend RED_FIX B1–B5 (04/11/2026 AR)

**Unidad original (NO abrir otra):** `S3_A2_G1_POLICY_RESTORE_READMISSION`. **Código auditado:** `dc3eee9de857a00b07a808c3ed19f6b60acff3d6`; permiso original `39fde93dfc34294bf447a0717aa258a53147d1d2`.
**B7 actual:** `RED_FIX`, NO PASS, a pesar del protocolo `#41955987110 SUCCESS`. **S2 8/8 CLOSED; S3.A2-G1 OPEN; V2 productiva, Auto, Noche y S4 OFF.**

## B7 AUDITORÍA FINAL DEL CÓDIGO INICIAL — RED_FIX, NO DONE
El código productivo `FaroV5CompositionRoot.java` y `FaroV5ActiveWorkspaceSearchGrantOwner.kt` **compilaron en Debug**. Pero la suite S3 dirigida `#41955986988` terminó **FAILURE** en `:app:compileDebugUnitTestKotlin`: `ILLEGAL_SUSPEND_FUNCTION_CALL`, `FaroV5ActiveWorkspaceSearchGrantOwnerTest.kt:303`. El test `processRestartKeepsWorkspaceButNeverRestoresGrant` invoca el `suspend fun createAndSelect` fuera de corrutina. El run V2 core `#41955987005` reproduce `ILLEGAL_SUSPEND_FUNCTION_CALL` en `:app:compileV2CoreDevUnitTestKotlin`. NO se corrieron los XML de 10/10 ni el nuevo oráculo del manifest por ese bloqueo. La APK DEV puede tener estado independiente; jamás confundir con aprobación de test. La revisión de protocolo verde no sustituye Kotlin compile.

## B1 EVIDENCIA
- Logs CI SHA `dc3eee9d`: Kotlin compiler, error exacto línea 303 (llamada suspend a `createAndSelect` en método `@Test fun processRestartKeepsWorkspaceButNeverRestoresGrant(): Unit`). Los otros 9 tests nuevos y previos ni siquiera llegaron a ejecutarse por el error de compilación.
- La fuente `ConversationWorkspaceStore.createAndSelect` tiene contrato suspend y los tests de Workspace existentes usan `runBlocking`. El método del test puede quedar `: Unit = runBlocking { ... }`, manteniendo la prueba real de persistencia y grant no restaurado.
- Toda la comparación y evidencia de arquitectura de G1 ya están en `docs/s3-night/S3_A2_G1_FINAL_POLICY_RESTORE_READMISSION_B1_B5_20261104.md`. No repetir V4.4 ni volver a cambiar cuatro archivos.

## B2 COMPARACIÓN Y REUTILIZACIÓN
- V4.4 GlobalSearch y el contrato durable de V5 no cambian. El fallo está en infraestructura de **test** Kotlin, no en algoritmo Search, owner o DB. No importar código V4.4, no tocar FBS2/SQLite schema, Manifest, Flutter ni wiring productivo.

## B3 INVESTIGACIÓN EXTERNA Y ALTERNATIVAS
- Kotlin `runBlocking` (https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/run-blocking.html) permite llamar APIs suspend desde test JVM no suspend y conserva los fallos del test. JUnit @Test en Kotlin debe devolver `Unit`; el método debe declarar explícitamente `: Unit = runBlocking` para no repetir el bug histórico de métodos no-void omitidos por JUnit.
- **USAR:** hacer suspendible únicamente el cuerpo de ese test con `runBlocking`; no convertir al store en sincrónico. **DESCARTAR:** eliminar test de restart/restore, sustituir la llamada por mock, rebajar XML 10/10 a 9/9, modificar una dependencia o abrir otro `FARO-UNIT`.

## B4 DELTA MÍNIMO, MISMA UNIDAD
**Una sola ruta funcional:** `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5ActiveWorkspaceSearchGrantOwnerTest.kt`.
Cambio literal esperado:
```
-    @Test fun processRestartKeepsWorkspaceButNeverRestoresGrant(): Unit {
+    @Test fun processRestartKeepsWorkspaceButNeverRestoresGrant(): Unit = runBlocking {
```
Sin tocar cuerpo de la prueba, red, permisos ni catálogo. El workflow existente exige **10/10** de G1, **42/42** S3 dirigidos en total y `S3_G1_SINGLE_PROCESS_MERGED_MANIFEST_PASS`.

## B5 AUDITORÍA ADVERSARIAL ANTES DE APLICAR
- Fallar si el método Kotlin no se compila o se omite en XML. Verificar test real file-backed Workspace close/reopen con grant nuevo y epoch anterior no reutilizado, además de A→B→A sin listener, readmisión idempotente y proceso único.
- Preservar `FaroV5CompositionRoot` completo, ni otra línea de producción. Reaprovechar PASS del protocolo del commit inicial y exigir nuevo preflight en HEAD documental distinto.
- Tras `READY` separado, writer CAS, B6 y cuatro CI sobre nuevo SHA; B7 final independiente. No marcar G1 CLOSED si CI/manifest rojo. G2 UI y V2 productiva siguen OFF. V2 de la app nunca se activa para un test.
- **B5:** `READY_FOR_SAME_UNIT_ONE_FILE_REDFIX_ONLY`. Tiempo humano medido: NO_REGISTRADO. 
