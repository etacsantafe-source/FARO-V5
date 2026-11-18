# FARO V5 — Codex Cloud / piloto W01 D1B1C0 (preparación, NO ejecución)
Fecha: 2026-11-17 (Argentina). Documento DOC_ONLY. **No concede READY ni autoriza código.**
Repositorio: `etacsantafe-source/FARO-V5`; rama funcional única: `work/s3-night-20261102`.
Baseline al preparar: `d7e582b4083b103fab8af3c440ba1b7ec90befea`; último código B7: `bf30b5ea37791eda6909175e2508e0c488a5a940`.
Unidad piloto propuesta: `S4_W01_D1B1C0_FINANCE9_DEV_CUTOVER_PREFLIGHT`. W1 OPEN; W2 STOP; V2 productiva OFF; biometría opcional OFF por defecto.

## 1. Evidencia de entrada y reutilización
- **NO repetir** D0, D1A, D1B0, D1B1A o D1B1B. El último B7 de D1B1B está en `docs/v5/FARO_S4_W01_D1B1B_SINGLE_SESSION_FINANCE_UI_B7_PASS_SCOPED_20261117.md`: 6/6 JVM y `:app:compileW01FinanceDevKotlin` PASS, run 44199936375, guard 44199936427; no prueba física ni Finance9 ACTIVE.
- El `FaroV5Finance9StagedMigration.kt` ya migra Planning8 STAGED a Finance9 STAGED con pointer exacto y preflight cinco stores; `FaroV5Finance9StagedMigrationTest.kt` tiene 7 escenarios certificados previamente. **No reejecutarlos por inercia.**
- `FaroV5Planning8DevCutoverOwner.kt` tiene el patrón existente de journal/rollback/replay fence para Planning8; **referencia de reutilización, NO portar sin revisión**.
- `FaroV5GenerationStoreGraph.kt` rechaza Finance9 ACTIVE y permite Finance9 STAGED únicamente. `FaroV5ActiveGenerationGraphOwner.kt` admite Planning8 ACTIVE bajo host certificado. No puentear ninguno.
- D1B1B lee FinanceSnapshot ACTIVE de la misma Session Android con una sola conexión/grafo; esa fuente **no certifica** saldo Finance9. No mezclar ARS/USD, no transformar ausencia de saldo en cero.
- La fuente V4.4 no tiene cutover Finance9: conservar AtomicApply, recibos, Undo y recovery; no escribir V4.4 ni main.

## 2. Misión piloto sugerida
**Objetivo pequeño y falsable:** producir un **preflight de elegibilidad de cutover Finance9 exclusivamente DEV y fail-closed**, reutilizando los primitives existentes, como paso previo a un futuro D1B1C de activación. El piloto **no activa** una generación, no cambia punteros, no abre graph de aplicación, no migra datos, no muta saldos, no habilita shipping y no emite permisos.
Antes de implementar, definir con evidencia si este preflight necesita código nuevo o si el chequeo puede reutilizarse sin duplicación; si ya existe la misma función, STOP y reportar delta cero.

Criterios iniciales para delimitar una unidad B1–B5 **nueva**:
1. Entrada con identidad de generación fuente/pointer **ID y secuencia**, descriptor canónico del target Finance9 STAGED, journal de operación y evidencia de quiescencia; sin confiar en booleans de una UI.
2. Rechazar pointer stale/ABA, stage igual a ACTIVE, descriptor/path foráneo, versión7/8/unknown del target, V2 marker/5 stores incompletos, sidecars WAL no sellados, journal ausente o fase incorrecta, operation/receipt ajenos, host no autorizado y cualquier read inconsistente.
3. Ninguna autoridad financiera ni autorización de lectura puede escapar de esta función. Retornar un dictamen tipado **READ_ONLY** y motivos concretos, sin convertirlo en permiso de activación.
4. Comparar alternativas: (A) estirar Planning8 cutover owner; (B) solo inspección read-only con reutilización de preflight Stage9; preferir el delta mínimo y evitar duplicar coordinador, stores, writers y SQL. Si el contrato no puede quedar acotado, STOP antes de código.
5. Probar solo escenarios **nuevos** de admisión negativa y uno de stage canónico, con fixtures aisladas, sin alterar/certificar nuevamente M0B. CI JVM dirigida con XML exacto y guardia de protocolo por SHA.

Rutas **candidatas, NO autorizadas todavía**: nuevo helper en `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/`, nuevo test en `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/` y nuevo workflow dirigido en `.github/workflows/`. B4 debe fijar sus **rutas exactas** antes de READY; no se permite tocar `FaroV5Planning8DevCutoverOwner.kt`, `FaroV5GenerationStoreGraph.kt`, `FaroV5ActiveGenerationGraphOwner.kt`, `RealityStore.kt`, Activity W01, main ni manifest por comodidad.

## 3. Protocolo FARO (obligatorio)
- Leer primero `AGENTS.md`, `docs/v5/FARO_CONTINUITY_POINTER.json`, `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json`, `docs/v5/FARO_V2_PROTOCOL_RESTITUTION_AND_RETRO_AUDIT_20261104.md` **en HEAD real**, no en un checkpoint pegado.
- La autorización vigente tras D1B1B está **BLOCKED / consumida**. Hasta generar B1–B5 verificados, auditoría B5 adversarial separada, guardia documental verde y nuevo permiso READY scoped por rutas en commit **solo documental**, Codex **NO puede escribir código funcional**, aunque el prompt lo sugiera.
- Antes de cualquier escritura a rama, usar writer global CAS `faro-v5-writer-lock:writer.json` con HEAD exacto; no tomar un writer ajeno HELD/UNKNOWN; liberar tras checkpoint y antes de esperar CI. No trabajar en paralelo sobre mismo writer.
- B1 evidencia → B2 comparar/reusar → B3 fuentes externas reales/alternativas → B4 delta → B5 adversarial → READY separado → B6 código+CI → B7 independiente. En cada commit funcional `FARO-UNIT:S4_W01_D1B1C0_FINANCE9_DEV_CUTOVER_PREFLIGHT` y gate preflight para TODAS las rutas exactas; no combinar READY y código.
- Si CI roja, corregir solo delta del piloto y registrar RED_FIX; si entorno no soporta Android no simular compilación: para este piloto usar JVM `:store:generation:test`. No dar PASS sin logs XML SHA-run ni guardia.
- La rama `main` y V4.4 son intocables; W2 STOP; no APIs pagas, banco/transferencia real, credenciales, servicios en red del app, biometría obligatoria, second SQLite graph, Android físico ni V2 productiva.

## 4. Entorno Codex Cloud (publicación manual necesaria)
Desde **ChatGPT web o escritorio**, crear y publicar un entorno Codex Cloud:
- GitHub conectado y repo `etacsantafe-source/FARO-V5` incluido. Seleccionar explícitamente rama `work/s3-night-20261102` (el default del repo es **main**, no usarlo para cambiar código).
- JDK **21**; wrapper `v5/gradle/wrapper/gradle-wrapper.properties` fija **Gradle 9.8.2**. `cd v5 && ./gradlew --version` para sanity (puede requerir descargar distribución).
- La primera misión del piloto apunta a JVM `:store:generation:test`; **no instalar Android SDK37** solo para este preflight. Para futuros compiles de `:app`, SDK Android compile 37 y build-tools 36 cuando corresponda. Red de entorno limitada a repositorios Gradle/Maven/Google necesarios; sin secretos ni clave Groq/OpenAI.
- Instalar dependencias/compilar en setup solo si sirve para reutilización. **Evitar correr suites históricas amplias** en setup. Guardar y **Publicar** entorno para reutilizar desde Moto G15.
- No activar ejecuciones automáticas ni tareas nocturnas. Codex Cloud se inicia manualmente desde selector Codex en móvil después de publicar entorno en web/escritorio.

## 5. Prompt listo para Codex (pegar al iniciar piloto)
```text
FARO V5 / PILOTO CODEX W01 D1B1C0.
Usar repo etacsantafe-source/FARO-V5; cambiar a work/s3-night-20261102 (NUNCA main).
Leer primero docs/v5/FARO_CODEX_W01_D1B1C0_PILOT_HANDOFF_20261117.md y AGENTS.md.
Revalidar HEAD, continuidad, permiso, writer y CI. No asumir checkpoint estático.
Objetivo: preparar/implementar SOLO el preflight fail-closed read-only previo al cutover Finance9 DEV, unidad S4_W01_D1B1C0_FINANCE9_DEV_CUTOVER_PREFLIGHT.
NO activar Finance9, NO tocar UI/manifest/shipping, NO crear graph SQLite nuevo, NO biometría, NO W2 ni repetir D0/D1A/D1B0/D1B1A/D1B1B/M0B.
IMPORTANTE: permiso actual BLOCKED. Ejecutar primero B1–B5 para esta unidad con evidencia, alternativas externas y auditoría adversarial de verdad. Registrar paths EXACTOS y obtener nuevo READY documental + guardia verificada ANTES de B6. Si no puede verificar guard/writer/READY, STOP sin cambios funcionales y reportar qué falta.
Si READY y writer CAS válidos: hacer delta mínimo de Kotlin/tests/CI acotados, pasar JVM dirigida con JUnit XML exacto, guard protocolar, B7; dejar writer RELEASED. No repetir certificaciones antiguas. Reportar commit SHA, archivos, tests, run y coste/uso consumido si visible. Si el preflight ya está implementado, STOP con evidencia: no escribir por escribir.
```

## 6. Medición y cierre del piloto
Medir saldo antes/después desde UI del producto si está visible, duración y uso aproximado, archivos cambiados, tests nuevos PASS, CI + guard, y utilidad real para D1B1C. **Éxito**: 1 delta revisado + pruebas nuevas + writer RELEASED + progreso hacia cutover, sin mover V2 productiva. Si solo se completa preauditoría, reportar PREAUDIT_READY/BLOCKED según evidencia, **no** venderlo como código entregado.
Estado de este documento: **PREPARED_DOC_ONLY**, Codex **NOT_STARTED**, ambiente Cloud **NOT_VERIFIED/PUBLISH_HUMAN_REQUIRED**, permiso D1B1C0 **NOT_READY**, CI nueva **NOT_RUN**.
