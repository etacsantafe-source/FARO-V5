# FARO V5 — S3.A5 ingreso de host a Reality8 activa — B1–B5
Fecha 2026-11-08 AR. Base de revisión `aec816c9e1b7f8f57e8641f7d572b22e1cd86088`, writer epoch 587. Unidad `S3_A5_ACTIVE8_CERTIFIED_HOST_OPEN`. Solo capa de apertura y tests JVM; **NO cutover, Android click ni habilitación V2 productiva**.

## B1
- S3.A4 `CLOSED_AUTOMATED_SCOPE_READ_ONLY_DEV` (6/6 formatter, APK M5 y guard); A5 queda OPEN. N031 Pendientes exige Reality8, pero actual `FaroV5ActiveGenerationGraphOwner.open()` siempre llama `openActiveV2WithSharedGate`, cuyo admission solo acepta schema7. `FaroV5V2CoreDevAuthority.open()` además exige target de bootstrap antiguo schema7: estas dos barreras permanecen reales y no se eluden.
- N033C1 B7 14/14 y N033C2A B7 17/17 ya validaron reopen interno `FaroV5GenerationStoreGraph.reopenVerifiedActivePlanning8`, identidad de descriptor y secuencia de puntero, WAL-aware exact-schema preflight, crash y rollback. N033C2B2 B7 readiness prueba booleano pero **no devuelve grafo**; A5 necesita una apertura acotada de un Reality8 **YA activo**, sin volver a abrir sqlite como schema7 y sin migración activa.
- Aceptación unidad: método explícito con certificado host obligatorio y puntero completo (generation ID y sequence), no disponible en shipping normal ni inicialización automática, sin API de Apply/ACL o switch. Camino legacy `open()` no se altera. El host local sólo puede usarlo cuando real cutover Reality8 haya sido validado por separado.

## B2
- Reusar 100% `reopenVerifiedActivePlanning8` (N033C1, antes B7), `FileActiveGenerationPointer`, `FileStorageGenerationLayout.requireStaged`, `ExistingGenerationFormatReader`, `CanonicalPreparedPlanEnvelopeVerifier`, `MaterialWriteGate`, health y close. **No reimplementar esquema8, SQLite ni coordinador**. No cambiar `FaroV5PreSwitchHostAdmission` ni `V2_ACTIVE_ENABLED`.
- Un puente público del módulo generation `openActivePlanning8ForCertifiedHost` sólo delega al método interno después de exigir `certifiedHost=true` (sin grant autónomo; el booleano expresa aprobación del host confiable, no identidad de usuario). En app, `internal FaroV5ActiveGenerationGraphOwner.openExistingPlanning8ForCertifiedHost` exige primero `v2HostCertified=true`, expected ID y expected sequence y lectura completa del puntero; luego llama al puente, verifica Reality8 y cinco owners, revalida puntero y cierra grafo en error. Ninguna Activity/CompositionRoot lo llama en esta unidad.
- El grafo es una entidad de lectura/operación interna; la apertura no confiere por sí sola un permiso de escritura ni una policy de lectura humana. No representar este método como read-only si el objeto contiene stores mutables.

## B3
- SQLite WAL https://www.sqlite.org/wal.html y SQLite isolation https://www.sqlite.org/isolation.html : **USAR** preflight WAL-aware del abridor N033C1 y comprobar el puntero otra vez después de abrir; evitar abrir active sqlite con semántica de copia STAGED. Coste de licencias: SQLite dominio público; no se agrega dependencia.
- Android offline-first https://developer.android.com/topic/architecture/data-layer/offline-first : fuente de verdad local canónica. **ADAPTAR**, grafo ya activo es fuente única; no crear DB paralela. Docs Android, sin dependencia/costo.
- Proyecto externo real Google Now in Android https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md : repositorios encapsulan verdad local; **REFERENCIA** de boundary, no copiar código ni adoptar network/Room. Licencia no relevante porque no se copia código.
- OWASP Authorization https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html : default DENY. Un host-certified booleano no equivale a login ni autorización por dominio/objeto; policies N031/N015 permanecen distintas.
- Alternativa A adoptada: puente muy delgado hacia opener ya probado, con guardas host, preflight, secuencia y propietario. B rechazada: cambiar `GraphOwner.open` para que admita Reality8 en shipping silenciosamente. C rechazada: `RealityStore(...planningStage8=true)` sobre ACTIVE7 o reset automático. D pospuesta a siguiente A5: cutover real STAGED Reality8 en instalación DEV aislada.

## B4
- **Rutas funcionales exactas**:
  1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt` (añadir entrada acotada; no modificar viejo N033C1).
  2. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5ActiveGenerationGraphOwner.kt` (añadir método internal diferenciado; `open` V1/V2 existente intacto).
  3. `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5ActiveGenerationGraphOwnerPlanning8OpenT2Test.kt` (6 negativos/positivos sobre cinco SQLite reales).
  4. `.github/workflows/faro-v5-s3-a5-active8-open-directed.yml` (testDebugUnitTest solo clase nueva, JUnit XML 6/6).
- Cinco-vías test: caso activo8 válido, certificado ausente y no-SQL, schema7 intacta, generation ID equivocado, secuencia equivocada, ABA A→B→A sin revivir sesión. No duplicar N033C1/C2A anteriores; ni pruebas G15 ni emulador.

## B5
- El nuevo método se prohíbe antes de certificado, sin existencia de pointer, o si ID/sequence no coinciden; no puede migrar schema7 activa a 8 por el preflight anterior. No usarse para admitir esquema8 sin journal/replay fence del responsable de cutover DEV; este método no verifica por sí solo el permiso de usuario ni instala esos registros.
- Revalidar exacto pointer+host health después de abrir; cerrar stores abiertos si falla. No permitir fallback a schema7 bajo nombre Planning8. Verificar negativas con bytes SQLite intactos.
- **No cambiar** app shipping, Surface, BootstrapActivity, V2CoreDevAuthority, cat.172, RealityStore SQL, Workspace, Apply, Undo, S2/S4/V4.4/main, Auto/Noche, paquetes Android o permisos. Requiere B7 CI dirigida y guard por exact SHA antes de declarar avance; previous A4 B7 `715d1069` PASS documented, permit anterior BLOCKED. Físicas una sola campaña en S3.A5 posterior.
- B1–B5 **READY para la unidad acotada**, todavía sin código hasta permiso separado y guard. Resultado esperado: apertura segura de graph existente en pruebas JVM, NO funcionamiento completo de Pendientes en DEV.
