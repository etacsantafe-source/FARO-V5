# FARO V5 · S4-W01 M0A · Finance9 STAGED schema-only · B1–B5
**Fecha:** 2026-11-16, Argentina. **Unidad:** `S4_W01_M0A_FINANCE9_STAGED_SCHEMA`. **Base:** `e8a35a11406762a5689e3b93ca59860e1e7e9c15`. **Estado:** preauditoría DOC_ONLY. **No constituye READY ni autoriza B6**.
**Autoridad arquitectónica:** `docs/v5/FARO_S4_W01_ARCHITECTURE_GLOBAL_B7_PASS_20261116.md`, `FARO_S4_W01_ARCH03_DDL_ARCH06_EXACT_PATHS_20261116.md` y `FARO_S4_W01_ARCH02_ARCH06_MONOTONIC_EPOCH_PATH_FIX_20261116.md` (este último prevalece sobre el epoch de políticas).

## B1 — Fuentes y gap real
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`: `SCHEMA_VERSION=7`, `PLANNING_SCHEMA_VERSION=8`, `LEGACY_BACKUP_SCHEMA_VERSION=5`; la ruta normal rechaza schema8, y Planning8 sólo se crea por `RealityStore(file, planningStage8=true)` en STAGED. No hay Finance9, grants durables, tablas nuevas, contrato de backup9 ni Android host.
- `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityPlanningStage8BackupT2Test.kt` es prueba histórica REAL de 7→8 y backups: **no repetirla**, usarla para preparar fixtures 8 en archivos temporales separados.
- El módulo `v5/store/reality/build.gradle.kts` es `faro.jvm-library`, AndroidX SQLite bundled y JUnit Jupiter. Gradle CI dirigida `:store:reality:test --tests ar.com.faro.v5.store.reality.RealityFinance9MigrationTest`.
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt` y `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5ActiveGenerationGraphOwner.kt` admiten 7/8, **no 9**. Esta unidad NO cambia esos archivos; admisión de generación/pointer/backup es M0B/M0C y continúa STOP.
- `docs/v5/V5_OPERATION_CONTRACT_INVENTORY.md` y baseline generado confirman 180/180 canónicos; ningún catálogo ni código Finance owner se cambia en M0A.
**Criterio de alcance:** ensayar estructura DDL Finance9 sobre conexión SQLite TEMP ya validada como STAGED8, manteniendo intactas las rutas productivas. No ejecutar ninguna migración en generation ACTIVE.

## B2 — Comparación y reutilización
Reusar `RealityStore` para construir un **fixture 8** en directorio temporal, cerrar conexión y usar SQLite bundled para DDL 8→9. Para origen 7, la ruta **futura M0B** prepara copia separada y realiza 7→8 en su staging; M0A comprueba una secuencia 7→8→9 sobre archivo de prueba, no copia/activación real. Conservar el esquema 7, las tablas Planning8, G4, receipts, WAL FULL, UTF-8, FK y las B7 anteriores. V4.4/P02 L04/L05/L17 son referencia funcional, NO compatibilidad DB. Ninguna cuota vencida produce pago y opening heredado sin fila no se declara cero confirmado.

## B3 — Alternativas externas y decisión
- **Elegida:** objeto de creación SQL **sin opener ni API de producción**, que recibe conexión `SQLiteConnection` ya abierta por **harness STAGED confiable**; verifica user_version exactamente 8 y tablas base, aplica BEGIN IMMEDIATE + DDL/FK/indices + `PRAGMA user_version=9` atómicos y vuelve a validar. Una interrupción revierte transacción; la clase no habilita generación ni Compose.
- **Rechazada:** subir `RealityStore.SCHEMA_VERSION` a 9 en el opener habitual; haría migrar ACTIVE o invalidar G4 y Planning8.
- **Rechazada:** DB Finance separada o transacciones ATTACH+WAL, permisos en RAM, ignorar G4/Planning u omitir información legacy en backup.
- **Rechazada:** inventar nuevos OperationIds para transferencias/presupuesto: ya existen en el catálogo 180.
Fuentes: SQLite `https://www.sqlite.org/lang_transaction.html`, `https://www.sqlite.org/lang_altertable.html`, `https://www.sqlite.org/foreignkeys.html`, `https://www.sqlite.org/lang_createtrigger.html`, `https://www.sqlite.org/pragma.html`; AndroidX SQLite bundles según implementación efectiva en HEAD. Referencias P04/Actual Budget y V4.4 se reutilizan sin copiar licencias o runtime.
**Limitación explícita:** un helper con conexión abierta no puede acreditar STAGED por sí solo. No ofrecerlo como opener público. La **barrera exacta de generación+archivo** pertenece M0B con B1–B7 propio.

## B4 — Unificación de contrato, rutas exactas y pruebas
Permiso futuro **sólo tres rutas nuevas**:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9Schema.kt`: `internal object RealityFinance9Schema` y `installOnStagedEightConnection(connection: SQLiteConnection,...)`, NOT mounted, stage-only. Validación exacta user_version=8 y tablas Finance/Planning antes de BEGIN; DDL y `PRAGMA user_version=9` en una transacción; rollback en excepción; opcional hook de fault injection **internal** sólo pruebas. Sin acceso directo por Android shipping. `finance_acl_state` empieza en epoch1 y no se recrea al abrir.
2. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9MigrationTest.kt`: tests JVM TEMP bunded exactos de schema9, dos fuentes 7/8 preparadas como staging TEMP, integridad, provenance LEGACY_UNVERIFIED, fallas DDL transaccional, constraint FK, epoch; afirmar que `RealityStore` normal rechaza 9.
3. `.github/workflows/faro-v5-s4-w01-m0a-finance9-directed.yml`: GitHub Actions Linux Java21 con Gradle JVM `:store:reality:test`, JUnit XML 7/7 verificando los nombres exactos, build logs/artifact; siempre conserva XML incluso si rojo.
**DDL de referencia:** `FARO_S4_W01_ARCH03_DDL_ARCH06_EXACT_PATHS_20261116.md`; incorporar corrección `finance_acl_state` de `FARO_S4_W01_ARCH02_ARCH06_MONOTONIC_EPOCH_PATH_FIX_20261116.md`. Las tablas de transferencias/deudas/cuotas/presupuesto se crean VACÍAS, sin Apply/ledger real hasta unidades C/D/E.
**Tests propuestos (todos NOT_RUN aquí):**
- M0A-01 migración fixture7→8→9 aislada con source7 separada, keeps source7 intact.
- M0A-02 8→9 conserva tablas Planning8, y opener7/8 rechaza schema9.
- M0A-03 opening histórico sin confirmación persiste `LEGACY_UNVERIFIED/NULL`, no cero confirmado.
- M0A-04 fault injection en mitad de DDL revierte completo `user_version` y tablas, source8 intacta.
- M0A-05 FK, CHECK, trigger de transfer rechazan fila desvinculada o segundo efecto inválido.
- M0A-06 inicio ACL epoch único y reinicio raw no recrea contador; grant viejo queda stale tras incremento.
- M0A-07 esquema ajeno/versión inesperada y tabla ausente fallan cerrados antes de escribir.
**Nota:** los tests son un diseño de aceptación, todavía no ejecutados. M0A no prueba dos procesos, autenticación real, AES backup, cutover, pagos, G15 ni accesibilidad. Esto corresponde M0B/M0C/M1 y W01-F.

## B5 — Auditoría adversarial
- Empeorar versión ACTIVE → STOP, sin método con nombre de apertura productiva ni fallback 8 en `RealityStore`. El caller futuro M0B debe probar generación NO ACTIVE y puntero/lease interproceso ANTES de conectar al helper.
- Objeto SQL no se conecta a `FaroV5CompositionRoot`, no usa INTERNET, no añade permiso Android ni habilita V2.
- `finance_apply_grant` es estructura, NO permite Apply; grant sin validación de principal/epoch/nonce bajo mismo COMMIT está prohibido. `finance_acl_state` debe ser monotónico a través de revoke/regrant (prueba runtime M1).
- `finance_transfer` debe exigir dos entry IDs, doble FK, mismo importe y moneda, INCOME/EXPENSE opuestos; fallo segundo movimiento rollback se certifica en W01-C, no confundir solo CHECK con atomicidad final.
- `finance_plan_item_payment` no certifica PAID por fecha ni por vínculo no certificado; `finance_budget_limit` no escribe finance_entry. No sumar monedas ni crear saldos ante ausencia de permisos.
- Protecciones: S2/S3/main/V4.4, apps G15, catálogo180, shipping V2, Auto/Noche quedan sin cambios. Writer CAS/global, preflight rutas, B6 distinto commit de READY, CI XML por SHA y B7 independiente. Cualquier error da RED_FIX scoped, no autoriza otras unidades.

## B6
`NOT_RUN`, STOP hasta permiso READY validado en commit DOC_ONLY después de esta preauditoría y guard SUCCESS.

## B7
`PENDING`, exige tests 7/7 JVM XML, guard exacto, scope 3 rutas, schema7/8 históricos sin modificación, writer RELEASED y checkpoint SHA final. No afirmar W01 completa por M0A.
