# FARO V5 — W04-C1b2G · integrar Evidence2 en cinco stores STAGED DEV sin activar · B1–B5

Fecha local 2026-11-20. Código C1b2F validado en `65f075284a7006df24ad1a0e8f612ca8f4212c56`, guard `44824047221` SUCCESS; JVM `44824047295` SUCCESS, FBS1v4 8/8 XML; regresión Guardados `44824047291` SUCCESS. Unidad futura `S4_W04_C1B2G_STAGED_FIVE_STORE_EVIDENCE2_DEV`. **PREAUDIT DOC_ONLY / NO READY / NO B6**. C0A, C0B, C1a, C1b1, C1b2E y C1b2F B7 PASS scoped: no repetir ni ampliar su alcance.

## B1
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt` blob `19da4d2d408b3e8b9289eeb4471e6e6df7085bf5`: `requireStrictV2StoreSchemas` fija CommercialEvidence schema1+tabla `commercial_evidence`, en `openValidatedV2Internal` hace preflight SQLite read-only **antes** de abrir stores y en `openStores` crea `CommercialEvidenceStore` con defaults schema1. `openValidatedV2WithSharedGate` existe para stage schema1; `openActiveV2WithSharedGate` se mantiene fuera de esta unidad y jamás debe admitir schema2.
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreBackupCapture.kt` blob `5c6dd7f2ea2653170015ecb1a49770cdb9e6491f`: captura los cinco owners en un único `MaterialWriteQuiescencePort` e invoca `graph.commercialEvidence.backup`, sin branch de versión. `FaroV5FiveStoreSemanticCodec.kt` blob `2624bbe4c89a5a8a3c9276223190e1d1face8328` envuelve un `SemanticBackupSetCodec.encodeWithin/decode` FBS1 en FBS2, por diseño puede transportar FBS1v4 **sólo si se verifica roundtrip**.
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreBackupCaptureT2Test.kt` blob `058eb78e1f607edefa870a231c22b34bb892ac9e`: 5 tests actuales de captura/lease y G4; `FaroV5FiveStoreSemanticCodecT2Test.kt` blob `cfc4524ade28e9f8b2617facd0a841a48da2b33b` y tests de codec existentes, NO reescribir. `StagedSqlitePreflight` comprueba versiones/tablas, FK, quick_check y ausencia de `-wal/-shm/-journal` antes de abrir.
- `v5/kernel/contracts/src/main/kotlin/ar/com/faro/v5/kernel/contracts/MaterialWriteGate.kt`: `SingleProcessMaterialWriteGate` compartido para admisión/quiescencia; NO sustituye el writer CAS global.
- C1b2F habilitó `CommercialEvidenceStore(... stagedTypedTermsV2=true, stagedSemanticBackupV2=true)` **sólo** con opt-ins explícitos, FBS1v4 lossless + fingerprint de terms; legacy sigue DENY schema2. Aún NO existe graph de cinco owners STAGED que use esos flags. Ningún test integral G ejecutado.

## B2
V4.4 L11/L15 se conserva como UX/comercio contextual; V5 Evidence≠Reality, no alterar datos W1 Finanzas ni la generación ACTIVE. Reutilizar graph, preflight, FBS2 envelope, shared material gate y owner Commercial existente. No nuevo root/store/owner, no Apply/activación, no FX ni promociones inferidas. V1 normal y active openners conservan EXACTAMENTE schema1 y fail-closed ante Evidence2. La migración 1→2 sólo puede realizarse ANTES, bajo construcción STAGED aislada y después de cerrar/selar WAL; un opener de cinco stores no migra schemas al abrir.

## B3
Alternativas comparadas:
- **ADAPTAR** ruta interna de `FaroV5GenerationStoreGraph.openValidatedV2Internal` a una fábrica nueva y *de nombre explícito* `openValidatedV2Evidence2StagedDev` con shared gate; pasa flag SOLO para Commercial, preflight exacto schema2+tablas `commercial_evidence_quote_terms` y `commercial_evidence`, sin habilitar ACTIVE.
- **REFERENCIA** SQLite `https://www.sqlite.org/pragma.html` y `https://www.sqlite.org/wal.html` (versions/WAL/foreign keys); `https://www.sqlite.org/backup.html` (consistencia sin sustituir FBS2), Android Room migration test `https://developer.android.com/training/data-storage/room/migrating-db-versions` (fixtures v1→v2), sin migrar FARO a Room.
- **DESCARTAR** modificar preflight global para aceptar schema1|2 indistintamente, inyectar flags en normal `openActiveV2WithSharedGate`, duplicar Evidence SQLite/graph, activar V2 productiva, alterar C1a, escribir Finance/stock o reducir validaciones de WAL.
La opción elegida conserva la frontera de autoridad y verifica la compatibilidad externa FBS2 en test, sin editar el codec FBS2 si ya realiza roundtrip.

## B4
Contrato DEV de generación acotado:
1. Factory nueva **solo STAGED** `internal fun openValidatedV2Evidence2StagedDev(staged,materialWriteGate)`, llama el único `openValidatedV2Internal` con un nuevo booleano `commercialEvidence2StagedDev=true`. Fail closed si se intenta combinación con `activeGeneration`, `auxiliaryReceiptFence`, `householdActive10Dev`, `financeStage9` o `planningStage8` fuera del perfil base; no puede enlazar host productivo ni G15.
2. Preflight `requireStrictV2StoreSchemas` recibe flag solo desde esta fábrica, selecciona Commercial schema2 tablas exactas `commercial_evidence` + `commercial_evidence_quote_terms`; otra ruta conserva schema1. Preflight read-only antes de cualquier writable constructor. Version1 en fábrica v2 no se migra ni se acepta accidentalmente; active opener rechaza schema2.
3. `openStores` propaga flag únicamente a `CommercialEvidenceStore`, con `stagedTypedTermsV2=true` y `stagedSemanticBackupV2=true` para capturar FBS1v4. Mismos materialWriteGate/quiescence para cinco stores. FBS2 codec inalterado si test de ida y vuelta certifica; no modificar otro owner.
4. Fixture: crear cinco SQLite schema V2 existentes con métodos certificados, migrar **aisladamente** Commercial schema1→2 con `CommercialEvidenceStore` opt-in, insertar observación MANUAL y terms conocidos, cerrar para sellar WAL; abrir factory nueva stage; captura cinco owners bajo lease única y comprobar FBS2 encode/decode y manifest hash v2. Comprobar que opener legacy rechaza la misma STAGED schema2, que schema1 factory nueva rechaza, que ninguna lectura/backup crea segundo registro ni cambia pointer, y que quiescence rechazada no exporta.
5. Fuente comercial local literal, ARS/USD sin FX, UNKNOWN legacy se conserva y no es vigente; no se promete restauración completa activa a una nueva generación, aún faltarían pipeline de cutover y portabilidad.

**Rutas exactas candidatas congeladas (TRES) y solo después de guard propia READY:**
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreBackupCaptureT2Test.kt`
3. `.github/workflows/faro-v5-s4-w04-c1b2g-staged-five-store.yml` (nueva)
NO cambiar `FaroV5FiveStoreSemanticCodec.kt`, `FaroV5FiveStoreBackupCapture.kt`, `Evidence`, `Finance`, Android o cualquier ruta fuera del freeze sin nuevo permiso. Code/CI aislado, no campaña física.

## B5
- T01 graph DEV nuevo admite stage Commercial schema2 con dos tablas correctas, otros cuatro stores íntegros y material gate compartido. FBS2/FBS1v4 roundtrip conserva términos, old evidenceId, sourceRef, times y firma del manifiesto; capture quiesced 1/1.
- T02 graph V2 normal/active debe rechazar Commercial schema2 sin abrir owners writable; flag stage2 rechaza schema1, no schema upgrade implícito ni root nuevo. No activar pointer.
- T03 after-close staged sealed WAL: preflight rechazará -wal, -shm y -journal no checkpointed, tabla ausente, schema incorrecto o corrupción de FK/fingerprint; fail closed.
- T04 rollback/kill de quiescence rechazada no produce paquete parcial; recuperación end-to-end de generación/cutover queda PENDIENTE para unidad posterior. No false PASS.
- T05 regresiones automatizadas de captura FBS2 existente (G4, Workspace, quiescence), no rehacer suites C0/C1a ni paquetes físicos; permiso exact3 y guard parent.
- T06 JVM `:store:generation:test` y XML de clase `FaroV5FiveStoreBackupCaptureT2Test`, sin errores ni skips; guard, B7 por SHA y writer RELEASED. Cualquier fallo -> B7 RED_FIX same-unit.
**Todas las pruebas G NOT_RUN** al congelar rutas.

## Gate
Estado **B1_B5_PREAUDIT_ONLY_NO_READY**. Debe verificarse guard documental sobre SHA y emitir permiso READY independiente exact3, antes de escribir código. B7F PASS scoped no da autorización G. C1 global OPEN, C1b2H portabilidad y cutover siguen BLOQUEADOS.
