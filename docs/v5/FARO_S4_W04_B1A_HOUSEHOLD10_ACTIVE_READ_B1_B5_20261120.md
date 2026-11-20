# FARO V5 · W04-B1A · Admisión de sesión ACTIVE10 y lectura de necesidades · B1–B5
Fecha Argentina 2026-11-20. Rama work/s3-night-20261102. Baseline `5cc67fc5a3bc0bcfe1f3246c746b812dfc55a294`. Unidad candidata `S4_W04_B1A_HOUSEHOLD10_ACTIVE_READ_DEV`.
**DOC_ONLY / NO_READY** hasta guard documental y permiso independiente. B6/B7 y pruebas W04-B1A **NOT_RUN**; V2 productiva OFF, main/V4.4 inmutables.

## B1 — Evidencia exacta y criterio de entrada
- `docs/v5/FARO_S4_W04_B0_HOUSEHOLD10_GLOBAL_B7_PASS_20261120.md` @blob `286526d20aebf51d5e90da869bbf6fcc5944aaaa`: B0 completo B7 DEV. Nueve unidades, total 61 pruebas JVM (26 anteriores + 35 finales); no repetir. Último código B0D2 `7381c8767a062a740b4729d1fa353499eb654611`; guard B0 global 44729681979 SUCCESS. Writer inicial RELEASED epoch 1404. El permiso B0D2 BLOCKED / consumido.
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt` @blob `0d8942507ee834f3370b991f35d96f6a23b0e1f2`: parámetro `householdStage10ReadOnly` abre `mode=ro&immutable=1` sobre archivo sellado, sin gate ni transacción normal. `initializeSchema` normal sólo admite su version base7, Finance9 por modos distintos; **NO existe modo ACTIVE10 de operación Household**. El `run` base usa BEGIN IMMEDIATE/COMMIT/ROLLBACK y scope RealityTransactionScope, pero eso NO constituye autorización owner. No habilitar `householdStage10ReadOnly` para mutar.
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt` @blob `53e8f06bc7a2da10d8ad715422c3869f6bad4a31`: `openValidatedHousehold10ArchiveStage`, `requireSealedHousehold10ArchiveStage` y `verifyReopenedHousehold10Dev` verifican y cierran; esta última NO expone una sesión mutable durable, ni es un grant. Los openers ACTIVE actuales son Planning8/Finance9.
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold10SealedRowsPort.kt` @blob `ab4ebc74b053e0f11ba802fddbc257b10b6be11d`: ROWS read protegido para SQL STAGED sellado, versión10 exacta, LIMIT 8192 y 4 MiB, id/revision y FK. No reabrir archivo ACTIVE como immutable con WAL pendiente.
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold10Schema.kt` @blob `0fca56b7c0910a37085d18b4ce1bd59a1d118f33`: tablas ya existen como schema10 STRICT, singleton revision y necesidades. `v5/settings.gradle.kts` @blob `e449f405bec580ae0d0b4c892e0b2fbe45273b46` ya registra `:owner:household`. NO recrear módulos, DDL ni migración de B0.
- GATE B1A: crear una **sesión ACTIVE10 DEV verificada de lectura** con generation+pointer ID/sequence exactos y shared MaterialWriteGate; ninguna mutación Household/Finance/stock durante esta unidad. Snapshot lectura pura, sin abrir segunda base.
- Aceptación: ACTIVE10 ya validado B0D2 puede abrirse y cerrarse bajo un único grafo de cinco stores, sin side-effects; pointer ABA, schema9/8, rutas falsas y grant ausente DENIED. Valores Household previos legibles y el modo ARCHIVE10 sellado intacto.

## B2 — Reutilización V4.4 / V5
- V4.4 `app-v4/src/main/kotlin/ar/com/faro/v4/smartcompra/FaroV4SmartCompraGateway.kt` @blob `856943d8a08aef04240d507ae1ed8daa88f4b510` daba `read` y `readForSearch` sin materializar necesidades. Su composition root inyectaba store real; no importarlo ni reutilizar DB legacy. Conservar lectura humana y la separación de mutación.
- V5 ya contiene `RealityHousehold10Rows` / `Household10RowsSql.checkSchema/readRows`, `FaroV5Household10DevCutoverRuntime` con root lease `finance9-dev-root.lock`, y validaciones de `ActiveSqlitePreflight` / `FaroV5GenerationStoreGraph`. ADAPTAR, no copiar nueva base ni "hacer otro escritor".
- Contratos y recibos de Finance9 no otorgan Household. `RealityStore` debe preservar todos los modos previos (base7, staged8, stage/active9, stage10 read only).

## B3 — Alternativas y decisión contrastable
1. **ADOPTAR** opener DEV ACTIVE10 propio, separado del modo de archivo STAGED10, reusando validación de pointer/generation, gate y SQLite compartida de RealityStore, sin dar acceso público irrestricto.
2. **RECHAZAR** conectar UI a `RealityHousehold10SealedRowsPort` sobre ACTIVE o usar `immutable=1` con WAL: no es lectura fiable de la generación mutable.
3. **RECHAZAR** escribir directamente a archivo Reality10 desde otro SQLiteDriver/store, o reusar `financeActive9` cambiando version por fuerza: rompe writer/ACL/separación de dominio.
4. Fuente externa oficial: SQLite `https://www.sqlite.org/lang_transaction.html` advierte que BEGIN IMMEDIATE reserva escritura; `https://www.sqlite.org/wal.html` describe lectores/single-writer y checkpoint. AndroidX `https://developer.android.com/reference/kotlin/androidx/sqlite/driver/bundled/BundledSQLiteDriver` referencia para flags. No se introduce dependencia ni API pagas.
Hipótesis: admisión estricta ACTIVE10 devuelve snapshot tipado solo bajo ownerAdmission de host DEV; un opener heredado Finance9 sigue rechazando schema10.

## B4 — Arquitectura de la primera unidad y rutas EXACTAS propuestas
Acción limitada B1A: habilitar lectura desde la **misma conexión RealityStore de generación activa10**. Añadir modo `householdActive10Dev` guardado por `materialWriteGate` y requisito de `planningStage8` + pointer verificado por grafo. No activar el modo con el constructor normal ni con `householdStage10ReadOnly`. Antes de un open RW validar esquema10 completo/FK, companions y pointer, WAL/sidecars compatibles con ACTIVE; **nunca correr inicialización/migración sobre 10**. Exponer solo `household10ActiveDevReadForTrustedHost(ownerAdmission: () -> Boolean)` limitado por generación/puntero/foreground y sin superficie de escritura. Si acceso read requiere granteo nuevo, este bloque debe permanecer con entrada interna trusted, sin registro de operación productiva.
Rutas candidatas B1A, sujetas a guard y READY exactos:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
3. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold10ActiveDevReadTest.kt`
4. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household10ActiveGraphTest.kt`
5. `.github/workflows/faro-v5-s4-w04-b1a-household10-active-read.yml`
Si se detecta necesidad inevitable de otra ruta, detener y preauditar delta; no escribir fuera del READY. Work debe respetar método de instancia, gate compartido, sin leak de conexión SQLite, sin callback que otorgue licencia al comprador.

## B5 — Auditoría adversarial y pruebas NO EJECUTADAS
A01 ACTIVE10 con dos needs y revision > 0, app read pure con ID+secuencia exactos.
A02 ACTIVE10 con cero needs y revisión no cero: sin normalizar/sobrescribir.
A03 root schema9/8, compañero Auxiliary3, tabla faltante, trigger/FK corrupta: fallo cerrado antes de writable open.
A04 changed pointer (ID/sequence) al abrir, revocación de lease/foreground/gate: denegación; no fuga de datos.
A05 dos sesiones concurrentes u owner distinto: no segundo writer, no apertura con lease ajeno.
A06 acceso sin ownerAdmission o grants: no nombres ni cantidades de Household.
A07 ARCHIVE10 STAGED immutable sigue leyendo lo mismo; Finance9 no abre ni migra 10; Finance9 productivo no se altera.
A08 reabrir tras cierre y un checkpoint desde STAGED10 preserva snapshots y receipts; consulta no crea rows ni aplica cambios.
A09 entrada de texto malformado/bytes inválidos deja DB estable.
A10 CI exacta JVM + JUnit XML + guard; sin Android, UI, esquema11, Finance mutation ni V2 productiva.

**B5 disposición: PREAUDITED / NO_READY.** B0 B7 antecedente PASS, B1A B6/B7 NOT_RUN. Después de publicar este documento, verificar guard del SHA documental; emitir READY en commit exclusivamente documental con cinco rutas + sha blob B1–B5 y preflight; recién entonces B6/CI/B7. No heredar permiso B0D2.
