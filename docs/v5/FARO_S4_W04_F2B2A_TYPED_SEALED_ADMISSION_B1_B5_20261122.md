# FARO V5 — W04-F2B2A · Admisión sellada de generación Household10/Evidence2 DEV · B1–B5

Fecha local 2026-11-22. Unidad `S4_W04_F2B2A_HOUSEHOLD10_TYPED_SEALED_STAGE_ADMISSION_DEV`. Estado **B1–B5 DOC_ONLY; NO READY NI B6** antes de guardia documental. Baseline `b738c499355a70fa45a7197f83d0fdc4298314bf`. Sin Work ni acción productiva.

## B1 — Evidencia y alcance delimitado

W04-F2A probó copia Household10+Evidence1 → STAGED Evidence2 mediante 8/8 JVM SQLite reales, CI `45056165906`, código `e459ce49f48a2f0a7f36cd8ee9069f26aba28ad4`, cierre `adbd89cd4015e13f9529118697bab76b99199b3d` guard SUCCESS `45056657309`. W04-F2B1 probó lector DEV de schema2 ya ACTIVE mediante 6/6 JVM, CI `45057977049`, código `1f40d2956e9b70a8f0b86c27fd7826a770ae990e`, cierre `b738c499355a70fa45a7197f83d0fdc4298314bf` guard `45058561125` SUCCESS; writer RELEASED epoch1589. Ambos B7 acotados, permisos consumidos/BLOCKED, no repetir.

Brecha exacta: `FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage` verifica cinco bases pero obliga Evidence1; el cutover durable `FaroV5Household10DevCutoverOwner`, `FaroV5Household10ArchiveCoordinator` y FBS2v6 siguen esquema 1. No existe aun admisión sellada de formato Household10+Evidence2 schema2 lista para *futura* autorización de cutover. Sin esa puerta, F2B2B archive tipado y F2B2C activación con journal/recovery no pueden comenzar.

F2B2A **SOLO** agrega preflight de generación STAGED sellada: mismas validaciones de Household10 (cinco SQLite WAL sellados, realidad10, auxiliar4, workspace4, web2, real path, hardlink nlink==1, no -wal/-shm/-journal, integridad/FKs) salvo diferencia **explícita** Commercial Evidence schema2 con tablas legacy+terms. No hace apertura ACTIVE, no elige generación ni asigna permisos, no serializa backup ni simula cutover.

## B2 — Reutilización y compatibilidad
V4.4 SmartCompra comparaba compras sin prometer cotización vigente; conservar su semántica UX, no su código. Reusar el preflight sellado Household10 histórico sin duplicar su política de archivos, y `StagedSqlitePreflight` exacto ya existente: la modalidad Evidence2 debe ser separada sin modificar ninguna llamada legacy. El lector tipado F2B1 sigue opt-in e independiente. No cambiar tablas ni migrador F2A, no modificar F1/D/E, Finance ni W1–W3, no escribir la base activa.

## B3 — Riesgos, alternativas y referencias
- https://www.sqlite.org/wal.html: requerir stores STAGED sellados sin sidecar; prohibido tratar una transacción ATTACH WAL como atomicidad inter-base.
- https://www.sqlite.org/pragma.html: `integrity_check` y `foreign_key_check` son controles distintos; ambos requeridos por preflight existente.
- https://www.sqlite.org/isolation.html: no confundir snapshot cerrado con sesión ACTIVE; la validación de puntero/lease y el journal son obligaciones de futura puerta de cutover, no de esta función pura.
- Alternativas rechazadas: desactivar la verificación schema1 en el opener legacy, llamar F2B1 sobre archivo STAGED, abrir otro graph SQLite, usar un backup antiguo v6 que silencie términos, modificar el puntero desde el preflight. La nueva modalidad es una función internal explícita y nunca reemplaza automáticamente el recorrido viejo.

## B4 — Contrato EXACT3

1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household10Evidence2SealedStageAdmissionTest.kt`
3. `.github/workflows/faro-v5-s4-w04-f2b2a-sealed-typed-stage.yml`

Implementar en `FaroV5GenerationStoreGraph` el wrapper nuevo internal `requireSealedHousehold10Evidence2ArchiveStage(staged)` reutilizando por delegación la validación física existente `requireSealedHousehold10ArchiveStage` con parámetro privado `typedEvidence2`. El wrapper legacy llama false; el typed llama true y exige via `requireStrictV2StoreSchemas` `commercialEvidence2StagedDev=true`, `activeGeneration=false`, `financeStage9=true`, `householdStage10=true`, `auxiliary4=true`, y no modifica sus métodos open/restore. El wrapper no ejecuta SQL mutable.

Nuevo test JUnit5 con seis casos de cinco SQLite físicos @TempDir: T01 acepta F2A STAGED schema2 y mantiene source schema1, hash y puntero iguales; T02 niega schema1 bajo typed y schema2 bajo legacy; T03 niega tabla de terms eliminada/user_version99; T04 niega triggers/foreign corruption antes de confianza; T05 niega -wal/-shm/symlink/hardlink; T06 niega REALITY schema distinto/auxiliary4 inconsistente, no publica puntero. El test sólo usa selección explícita de source como fixture sintético y nunca invoca cutover.

Workflow Java21/SDK37, `:store:generation:test --tests ...FaroV5Household10Evidence2SealedStageAdmissionTest`, preflight READY parent exact3, 6 casos diferentes XML cero fail/error/skipped y guardia real de protocolo. No relanzar B1A/B0/D0–D4.

## B5 — Puertas y limitaciones
Adversariales T01–T06 **NOT_RUN** hasta B6. Permiso anterior F2B1 está consumido; emitir READY de F2B2A sólo tras guardia de B1–B5, con rutas EXACT3 y SHA de esta preauditoría. Para B7 exigir CI/guard SUCCESS en SHA exacto + XML descargado 6/6 + CAS writer RELEASED. Si hay fallo, RED_FIX documental, sin saltar etapas.

**F2B2A no completa F2B2 ni F2:** F2B2B aún debe versionar archive autenticado schema2 (no cambiar v6; términos/identidad íntegros) y F2B2C diseñar activación con CAS+journal+replay-fence+rollback y fallos inyectados, seguida por F2C host Android/shell. Ningún dato de usuario ni G15, V2 productiva OFF, pagos, Apply, stock, Internet, root personal, V4.4/main fuera de alcance. Jamás inferir COMPLETE de un preflight sellado.
