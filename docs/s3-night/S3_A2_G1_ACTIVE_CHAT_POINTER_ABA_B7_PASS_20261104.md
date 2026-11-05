# FARO V5 — S3.A2-G1 · Chat read pointer ABA · B7 PASS limitado (04/11/2026)

## B7 AUDITORÍA FINAL
**Unidad:** `S3_A2_G1_ACTIVE_CHAT_READ_POINTER_ABA` · **Código funcional:** `212679f7c2dc211445dc7f806479b83aea8780e0` · **Corrección única de test:** `dec1024439fdddc7367d0c98b993b4e0da207e13`. **Dictamen final: PASS SOLO de protección A→B→A de lecturas de Chat del host activo**. NO certifica ACL de Search ni activación V2.

### Contraste de protocolo, SHA y delta
- B1–B5 previo y permiso en padre `e1cc4b979e39bef1e2cd78384c549829adab4164` (preflight CI `#41936541907` SUCCESS); primer cambio de 4 rutas: `FaroV5ActiveChatReadSession.kt` exige `hostReadAuthorityStillCurrent` antes/después, `FaroV5CompositionRoot.java` fija `ActiveGenerationPointer` completo desde el owner durable y lo relee, test real con CAS A→B→A sin listener y durante lectura, oráculo 6/6 en `.github/workflows/faro-v5-s3-home-notice-directed.yml`. No root/schema/manifest/flag productivo/infra de permisos nuevo.
- B7 intermedio `RED_FIX` @ `212679f7` en `docs/s3-night/S3_A2_G1_ACTIVE_CHAT_POINTER_ABA_INITIAL_B7_REDFIX_20261104.md`: XML `#41936856517` reportó `S3_CHAT_POINTER_ABA_XML_FAIL (5,0,0,0)`, ya que una función `@Test` retornaba Throwable inferido por `runBlocking` y JUnit **no la descubría**. No fue una falla de los cinco tests ejecutados.
- Segunda auditoría B1–B5 en `docs/s3-night/S3_A2_G1_ACTIVE_CHAT_POINTER_ABA_JUNIT_REDFIX_B1_B5_20261104.md` y permiso independiente `870c0bb81e01c59e71325c2a9fdd8f19b86d16de`, preflight CI `#41937785131` SUCCESS. Cambio único de `dec10244`: `: Unit` explícito en test `durablePointerAbaFailsClosedWithoutAnyListener`. Ninguna modificación de código productivo ni relajación de oráculo.

### Evidencia exacta de CI
- `#41937907113`: mandatory protocol guard **SUCCESS** @ `dec10244`.
- `#41937907083`: S3 Home+Chat **SUCCESS** @ `dec10244`, log de JUnit XML real: `S3_CHAT_POINTER_ABA_XML_PASS 6/6`, `S3_HOME_NOTICE_XML_PASS 9/9`, `S3_WORKSPACE_SEARCH_XML_PASS 7/7`, `S3_SEARCH_BRIDGE_XML_PASS 5/5`, `S3_AVISOS_XML_PASS 4/4`. **31/31 dirigidas**, 0 fallos, 0 errores, 0 saltadas en esas suites.
- `#41936856422`: V2 core **SUCCESS** @ `212679f7`, DEV Search `10/10`, regresión T4 debug `7/7`, aislamiento manifest `4/4`.
- `#41936856486`: APK DEV **SUCCESS** @ `212679f7` con permisos binarios denegados y firma válida, SHA256 `d8d1101398894d9ddf2ec90d3d5d67489db8f7a1e726ca4aed5410d5e2817bcc`. Como `dec10244` solo modifica test JVM, no se reensambla APK por rutina ni se instala en el teléfono.
- `#41938092958`: guard de checkpoint documental previo **SUCCESS**. Verificar guard final del commit de B7 por separado.

### Riesgos residuales y salidas
- PASS: antes/después de Chat Workspace y timeline el host activo exige la MISMA identidad duradera `ActiveGenerationPointer(sequence,generation)`, por lo que una generación A→B→A no reactiva un puerto de lectura viejo aunque ninguna UI haya observado eventos. Los tests ejercitan `FileActiveGenerationPointer` real.
- **No certificado:** propietario ACL de grant/revoke Search genuino y observable (S3.A2-G1 sigue OPEN), invalidación cross-process, navegación exacta al mensaje, ni tests Android físicos. Otras sesiones activas del host (Composer, selección, Review y Apply) aún precisan auditoría específica de su propio control de secuencia; no extender el PASS de lectura a escrituras.
- V2 productiva, S4, Auto y Noche OFF; S2 8/8 CLOSED y congelada; S3≈40% desarrollo estimado, **0/5 etapas formalmente cerradas**, S3.A2 **0/5 gates**. Sin nuevas licencias, APIs pagas, cambios de cinco stores, importaciones V4.4 ni firma física.
- **Próxima unidad:** exigir B1–B5 con investigación externa, inspeccionar primero si shared material write gate cubre reversión de puntero/ABA en Composer/Selection/Review/Apply; luego seleccionar el delta mínimo antes de G1 propietario ACL real. Si ese problema ya está mitigado, no repetir; continuar owner ACL, revocación e invalidación observables. V4.4 y Element X #7877 referencias READ_ONLY.
- **Tiempo de desarrollo real:** NO MEDIDO, no confundir con tiempos de GitHub Actions.

**B7 PASS para esta unidad y SHA corregido; permiso siguiente BLOCKED hasta nueva B1–B5.**
