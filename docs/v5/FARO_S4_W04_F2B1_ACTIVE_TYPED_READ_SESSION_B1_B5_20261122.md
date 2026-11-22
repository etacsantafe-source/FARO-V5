# FARO V5 — W04-F2B1 · Lector ACTIVE Evidence2 bajo mismo lease Household10 · B1–B5

Fecha 2026-11-22; unidad `S4_W04_F2B1_HOUSEHOLD10_TYPED_ACTIVE_READ_SESSION_DEV`. **PREAUDIT_DOC_ONLY**, NO READY hasta nueva guardia y permiso exacto. Sin cutover/restore/Apply, V2 productiva ni Android.

## B1 — Evidencia y problema
F2A schema1→2 en copia STAGED se certificó B7: código `e459ce49f48a2f0a7f36cd8ee9069f26aba28ad4`, guard `45056165869`, CI `45056165906` 8/8 JUnit XML independiente, artefacto `14767409067`, cierre `adbd89cd4015e13f9529118697bab76b99199b3d` guard `45056657309` SUCCESS y writer RELEASED epoch1582. Queda sin resolver cómo abrir una generación **ya seleccionada** cuyo Commercial Evidence usa schema2 bajo Household10 SAME-GENERATION sin abrir otro SQLite ni exponer grants.
`FaroV5GenerationStoreGraph` posee opener ACTIVE Household10 de un único lease, exige Evidence1 en `requireStrictV2StoreSchemas`, y limita `commercialEvidence2StagedDev` a `!activeGeneration`. F2B1 tendrá una **segunda ruta DEV opt-in** de apertura de schema2 estricta, sin cambiar factories legacy ni producir selección/puntero. Para exponer el snapshot H1 tipado al host de confianza, validar grants Household y Comercial independientes y lease/puntero antes y después de lectura. El caller debe validar cada necesidad/producto y el render Android exige nuevo gate foreground; no resolver aquí UI.
**Prohibido** tomar una copia STAGED y fingir que está ACTIVE: tests pueden construir puntero sintético explícito en `@TempDir` sólo para demostrar el opener, pero no representa un cutover recuperable, ni habilita uso de datos reales.

## B2 — Reuso sin contaminar
Reusar `FaroV5GenerationStoreGraph.openActiveHousehold10DevForTrustedHost` / `openActiveHousehold10Dev` (FileLock único root, pin exacto, MaterialWriteGate, Reality10/Finance9/Aux4, prevención hardlink, preflight WAL y Foreign keys). `CommercialEvidenceStore.stagedTypedPortability.snapshot()` es sólo una lectura de schema2 ya materializada, sin record/write; no reabrir store ni copy SQL. `FaroV5CommercialEvidence2StagedUpgrade` de F2A prepara copias DEV. Conservar V4.4 sólo UX chat-first; F1 es coordinador de lectura, E es renderer, ninguno debe recibir permisos por defecto.
Alternativa abrir otra DB o permitir schema2 automáticamente en legacy ACTIVE, DESCARTADA; sólo opener nuevo opt-in DEV con preflight exacto2 para Evidence y schema10/4/4/2 restantes. Rechazar legacy1 y no migrar en ACTIVE.

## B3 — Referencias y riesgos auditados
SQLite WAL https://www.sqlite.org/wal.html: preflight active WAL consistente, un solo lease, no seleccionar un archivo STAGED de otra generación.
SQLite isolation https://www.sqlite.org/isolation.html: puntero y acceso pueden cambiar; repetir fence antes/después y fail-closed.
SQLite pragma foreign_key_check https://www.sqlite.org/pragma.html: integrity_check no cubre violaciones FK, exigir ambas.
SQLite ATTACH https://www.sqlite.org/lang_attach.html: transacciones de múltiples DB en WAL no son atómicas como conjunto; no usar ATTACH para simular cutover.
Android foreground/lifecycle https://developer.android.com/topic/libraries/architecture/lifecycle: host real F2C deberá invalidar pantalla en pausa, no responsabilidad de graph.

## B4 — Contrato EXACT3 y fase
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt` solo delta:
  * Nuevo `openActiveHousehold10Evidence2DevForTrustedHost(root,staged,expected,gate,ownerAdmission)` interno, mismo opener, único lease y `householdActive10Dev=true` más flag nuevo `commercialEvidence2ActiveDev=true`.
  * Flag dev solo para combinación `activeGeneration && householdActive10Dev && financeStage9 && auxiliaryReceiptFence != null && materialWriteGate != null`; nunca habilitar `commercialEvidence2StagedDev` en ACTIVE, ni mezclar ambos flags.
  * Preflight exacto de cinco schemas: Evidence2 schema2 con tablas legacy+terms, Reality10, Auxiliary4, WebEvidence2, Workspace4. Legacy ACTIVE conserva Evidence1 exacto. `CommercialEvidenceStore` tipado sólo nuevo opener, no migrar en ACTIVE; activación de constructor prohibida si DB no es schema2 preadmitida.
  * Sesión read-only `Household10DevReadSession.authorizedCommercialTypedSnapshot(householdAdmission, commercialAdmission)`: mismo graph/lease/gate y validador `pointerFence`, grants independientes antes/después, snapshot H1 inmutable bajo control de host DEV. No publicar como API productiva. No permitir el snapshot sin política específica de necesidad/producto; host sólo puede inyectar callbacks evaluados desde selection. Nada de descuentos vigentes o escritura.
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household10Evidence2ActiveReadSessionTest.kt` exactamente seis JUnit5 de stores reales @TempDir: T01 lectura de schema2 ya seleccionado y legados sin terms, cinco owners y snapshot H1; T02 legacy activo rechaza opener tipado y Evidence2 activo rechaza legacy; T03 deniega grants/revocación antes/después/puntero ABA; T04 rechaza schema2 corrupto/trigger/foreign; T05 lease único impide simultánea apertura y cierra correctamente; T06 staged no activa por sí solo, origen preservado, invalidación por ownerClosed. La selección sintética test-only no certifica cutover.
3. `.github/workflows/faro-v5-s4-w04-f2b1-active-typed-read.yml` CI Java21/SDK37, `:store:generation:test --tests ...FaroV5Household10Evidence2ActiveReadSessionTest`, seis XML distintos cero fallos/errors/skips, preflight parent READY EXACT3, artifact + guard. No ejecutar G15.
F2B2 posterior deberá crear **cutover/recovery real** y respaldo/restauración tipado (FBS2 versionado) con CAS/journal/replay y prueba crash; F2C montará host Android y navegación sólo después. F2B1 PASS queda acotado a opener/lectura DEV en una generación ya seleccionada.

## B5 — Adversariales y PASS exigido
T01–T06 **NOT_RUN**. Comprobar que la source schema2 abierta por nuevo opt-in no expone mutadores, que los grants no son implicados por un `productKey`, que no hay fallback a schema1, y que puntero ABAs/revocaciones cierran evidencia. En ausencia de guard exact SHA + XML 6/6 + permiso READY consumido+writer RELEASED => no B7 PASS. RED_FIX solo con evidencia y disposición formal. Los escenarios de transición/crash de F2B2 NO se reclaman por T01. V2 productiva OFF, no Finance, Apply, pagos, compras, stock, red, main ni V4.4. Exact3 rutas.
