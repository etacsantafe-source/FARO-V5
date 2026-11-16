# FARO V5 — W01 M0C2B2A · Grafo de cinco stores de Finance9 en STAGED, B1–B5
Fecha AR 2026-11-16; unidad `S4_W01_M0C2B2A_FINANCE9_STAGE_GRAPH_ADMISSION`, base HEAD `08ca2738168eac42da2dc0835a019cf6b7c0559f`. DOC_ONLY; código NO READY hasta permiso independiente.
M0C2B1 B7 PASS scoped 7/7 CI 43979784062, último guard de cierre 43980498964 SUCCESS, writer RELEASED epoch998. No repetir suites M0A/M0B/M0C1/M0C2A/M0C2B1 ya PASS.

## B1 — Fuentes realmente leídas
`RealityStore.kt`: default schema7, opt-in `planningStage8=true` abre solo 8; FBS2 paired `fbs2Backup` combina Reality/G4 y Planning cuando está abierto; `readPlanningSnapshot` exige EXACT schema8; `restoreOrVerifyPlanningStage8` también. `validateRequiredSchema` exige7 y `validatePlanningStage8Tables` exige8. La app productiva sólo conoce rutas previas y está OFF para V2.
`FaroV5GenerationStoreGraph.kt`: `openValidatedV2PlanningStage8` sólo en STAGED no activo, verifica marker y descriptor, abre los cinco stores pero `requireStrictV2StoreSchemas` rechaza9. `openStores` construye RealityStore con planningStage8 actual.
`RealityFinance9SealedReadPort`: verificador de estructura exacta y campos tipados de las OCHO tablas nuevas, read-only, preflight schema9 no activa. `StagedSqlitePreflight` verifica schema/required tables cinco stores, sidecars y FK.
`FaroV5FiveStoreBackupCapture` ya captura cuatro stores + Workspace + G4 + Planning en una sola lease cuando recibe `FaroV5GenerationStoreGraph` válido, pero no captura Finance9 hasta próxima unidad B2B. `FaroV5FiveStoreStagedRestore` legacy rechaza FBS2v4; no modificar esa protección.

## B2 — Reuso controlado
Elegir un nuevo `openValidatedV2Finance9Stage(staged)` INTERNAL para STAGED NO ACTIVE, sobre la misma ruta `openValidatedV2Internal` + los cinco stores; opt-in `financeStage9=true` y `planningStage8=true`. No cambiar valores default ni `SCHEMA_VERSION=7`, `PLANNING_SCHEMA_VERSION=8`, `LEGACY_BACKUP_SCHEMA_VERSION=5`. No agregar código a CompositionRoot, no habilitar V2 activa.
RealityStore `initializeFinanceStage9` sólo ADMIT (no MIGRATE), exige EXACT user_version9, tabla legacy7+Planning8+once Finance9 y FK/integrity, no seed/write ni downgrade. Permitir leer Planning8 snapshot y restore sólo bajo financeStage9 STAGED con exact9; los métodos de Apply/Undo siguen gate EXACT8 y no se abren en9. `fbs2Backup` aporta cuatro-store/G4/Planning8 existentes sin omisiones.

## B3 — Alternativas externas y veto
Elegida apertura stage9 dedicada en graph+Reality con preflight de tablas/columnas y pointer NO ACTIVE. Rechazar subir constantes normales a9, reinterpretar graph active8 como9, raw SQLite para repetir FBS1+G4+PLN1 y activar app. SQLite `https://www.sqlite.org/pragma.html`, `https://www.sqlite.org/wal.html`, `https://www.sqlite.org/lang_transaction.html`; Android offline-first `https://developer.android.com/topic/architecture/data-layer/offline-first`.
El grafo stage9 es una herramienta interna DEV; no concede tokens ni operaciones Finance.

## B4 — Permiso y pruebas exactas
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9StageGraphAdmissionTest.kt`
- `.github/workflows/faro-v5-s4-w01-m0c2b2a-finance9-graph-directed.yml`
- RealityStore constructor añade al FINAL `financeStage9:Boolean=false`; init cuando true llama `initializeFinanceStage9` fail-closed; FBS2 paired Planning read disponible en stage9, pero NO abrir CRUD/app Apply debido a revision gates existentes.
- GenerationStoreGraph añade `internal openValidatedV2Finance9Stage(staged)` con `FileStorageGenerationLayout.requireStaged`, marker V2 y pointer ID != target; `StagedSqlitePreflight` EXACT9 (5 owners) + `RealityFinance9SealedReadPort` columnas exactas ANTES de abrir SQLite read-write, sin sidecars/symlink; luego `openStores` con `financeStage9=true` y verificación Healthy.
- Tests seis: (1) grafo9 real bajo stage no activo con FBS2 G4/Planning; (2) opener7/8 siguen rechazando9; (3) legacy schema8 no se abre como9 ni se migra; (4) marker/descriptor ajeno DENY; (5) target activo/ABA DENY; (6) sidecar/columna adicional corrupta DENY antes de RW.
- CI dirigida `:store:generation:test --tests ar.com.faro.v5.generation.FaroV5Finance9StageGraphAdmissionTest`, XML6/6 y guard exact HEAD; no retestear otras suites salvo delta a API certificado.

## B5 — Adversarial
La apertura9 no crea ACL, no autoriza App, no restaura ni crea FBS2v4. Si un proceso externo cambia pointer o WAL durante apertura (inprocess quiescence no interprocess), deny/quarantine, no dar PASS productivo. Archivo 9 con tablas faltantes/columnas añadidas, schema8/7, symlink, fuente activa, marker alterado => ERROR antes de cualquier RW initializer. Viejos openers nunca admiten9. Full restore/backup con archive pin/replay sigue M0C2B2B; M1 acceso durable, W02 STOP, S2/S3/main/V4.4/prod V2 intactos.

## B6
NOT_RUN. Emitir READY en commit DOC_ONLY luego del guard B1–B5, writer CAS, código+CI en otro SHA.
## B7
PENDING. Exigir CI 6/6 XML exact, guard de código, diff cuatro rutas, writer RELEASED, permiso CONSUMED. No certificar M0C total todavía.
