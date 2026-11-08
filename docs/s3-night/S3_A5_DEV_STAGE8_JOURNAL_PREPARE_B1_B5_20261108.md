# FARO V5 — S3.A5 — DEV Reality8 journal preparation and safe recovery — B1–B5
Fecha 2026-11-08 AR. Unidad exacta `S3_A5_DEV_STAGE8_JOURNAL_PREPARE`.
Base auditada `9df313029c2844dcaf97ece464b8c8cd7634c8d5`; writer manual epoch 591.
V2 productiva OFF, S4/Auto/Noche OFF, G15 reservado para campaña única final.

## B1 — Estado y brecha comprobada
- **CERTIFICADO Y NO REHACER:** A5 ACTIVE8 host opener `d0fc1ad`, 6/6 XML; A5 fresh STAGED8 owner `5c9f3ef`, 5/5 XML + guard; C2A lifecycle interno 17/17. Ambos A5 B7 PASS_SCOPED y A5 sigue abierto.
- El owner `FaroV5WorkspaceV2StageOwner.stageFreshPlanning8AndVerify` crea cinco SQLite Reality8 en generación STAGED aislada, pero **NO crea journal**, marca VALIDATED ni cambia puntero. C2A verifica journal, CAS, rollback y replay fence solo en fixture, sin puente DEV persistente.
- `FaroV5V2CoreDevAuthority` todavía asume `targetId=v2-core-dev-workspace-1` Reality7 con journal de primera activación; ni host de Pendientes Reality8 ni cutover 7→8 se deben suponer conectados. Próxima subunidad DEV cutover + UI separada.
- Esta unidad implementa **preparación journalizada REAL de candidato STAGED8**, transición PREPARING→STAGED→VALIDATED bajo la **misma barrera global de escrituras suministrada por el host**. Recupera STAGED completo, rechaza PREPARING parcial y nunca cambia puntero, restaura, borra ni abre Activity.

## B2 — Comparación y reuso
- V4.4: sin autoridad sobre generación/punteros V5; no copiar bases de prueba ni modificar V4.4. La política modular de S5 se difiere.
- V5 existente: `FileBootstrapJournal` y `BootstrapTransitionPolicy` son el **único** journal; `FileActiveGenerationPointer` el puntero canónico; `MaterialWriteQuiescencePort` bloquea los cinco dueños, `FaroV5WorkspaceV2StageOwner` y `FaroV5GenerationStoreGraph.openValidatedV2PlanningStage8` el único stage verificado. No inventar segundo journal, lock ni constructor SQLite.
- El journal se crea PREPARING antes de tocar STAGED; al completarse los cinco stores y la verificación Reality8, CAS a STAGED y VALIDAD. Caída entre fases: fail-closed. Se mantienen los datos anteriores y el puntero completo (ID+sequence); un cambio ABA no equivale a autorización.

## B3 — Proyectos externos, documentación y alternativas
- SQLite WAL https://www.sqlite.org/wal.html y atomic commit https://www.sqlite.org/atomiccommit.html : los commits/WAL de cada archivo NO vuelven atómica una promoción de cinco bases y puntero de aplicación. **ADAPTAR** journaling superior a SQLite + pointer CAS y no inferir atomicidad cross-store.
- SQLite isolation https://www.sqlite.org/isolation.html : escritor único por SQLite NO reemplaza exclusividad de cinco stores y generación; mantener quiescence global (**USAR**).
- Android official offline-first https://developer.android.com/topic/architecture/data-layer/offline-first : almacén local autoridad, **REFERENCIA** sin Cloud/API.
- Proyecto real Google Now in Android https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md : separa fuentes locales de verdad de UI. **REFERENCIA**; licencia Apache-2.0 del repositorio, no copiar código ni agregar dependencias.
- Android Room migrations https://developer.android.com/training/data-storage/room/migrating-db-versions : fallback destructivo **DESCARTADO**; la STAGED fallida permanece en cuarentena.
- OWASP Authorization Cheat Sheet https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html : un journal VALIDATED NO concede permiso para cutover ni lectura humana. **ADAPTAR** fail-closed.
- Alternativa A: wrapper journal de la etapa8 reusando las clases existentes (**ADOPTAR**). B: migrar la ACTIVE7 in situ (**DESCARTAR**). C: usar directamente C2A fixture como Android runtime (**DESCARTAR**). D: hacer prepare + cutover + UI en un único commit (**DESCARTAR**, demasiado alcance).

## B4 — Plan de implementación mínimo B6
Solo **tres rutas exactas**, sin modificar código previo:
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Planning8DevStageJournalOwner.kt` — owner explicitamente no conectado, exige barrera material del mismo host, pointer completo estable, journal único y candidato fresco Reality8. `prepareFresh`, `recoverValidatedStage` con CAS.
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Planning8DevStageJournalOwnerT2Test.kt` — cinco tests nuevos con SQLite real: PREPARING→STAGED→VALIDATED; no-reuso; caída PREPARING parcial no adoptada; caída tras STAGED recuperable e idempotente; drift de puntero mantiene cuarentena.
3. `.github/workflows/faro-v5-s3-a5-dev-stage8-journal-directed.yml` — JVM dirigido y JUnit XML exacto 5/5, sin G15.
No tocar Activity, manifest, UI, main, V4.4, S2, S4, catálogo172, Apply/Undo ni esquema activo. No repetir 5/5 STAGED8 ni 17/17 C2A salvo regresión atribuible.

## B5 — Adversariales, veredictos y bloqueos
- Falta de host-quiescence real, puntero distinto aun con ID idéntico, journal de otra operación, reuso de ID/carpeta, PREPARING parcial o objeto con esquema diferente → **DENY**, sin switch ni deletion. El plan sólo autoriza un contrato en módulo generation, **no implica que la Activity posea la misma barrera**.
- `recoverValidatedStage` únicamente acepta entrada propia STAGED/VALIDATED, puntero previo completo y descriptor exacto. Preflight Reality8 **exacto** (sin migración de Reality7 durante revalidación) seguido por grafo de cinco SQLite saludable; PREPARING tras crash no se adopta automáticamente.
- Tras VALIDATED, puntero sigue apuntando a ACTIVE7; sólo la unidad siguiente podrá usar el coordinator B7 anterior para SWITCH_PREPARED→SWITCHED→HEALTH_VERIFIED→COMPLETED con replay fence/rollback. A5/DEV no se declara habilitado ni cerrado.
- Si no se obtienen XML y guard sobre SHA funcional exacto, B7=RED; conservar evidencia, NO proclamar PASS. Writer CAS único y liberar con checkpoint seguro.
