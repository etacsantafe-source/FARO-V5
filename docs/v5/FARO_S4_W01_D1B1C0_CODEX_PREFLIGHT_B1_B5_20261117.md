# FARO S4 W01 D1B1C0 — B1–B5 para piloto Codex de preflight Finance9 DEV
Fecha: 2026-11-17. Base inicial `f01aa27de4a81ff68a8c5d6e60da2e51b21950f2`. Unidad `S4_W01_D1B1C0_FINANCE9_DEV_CUTOVER_PREFLIGHT`. **DOC_ONLY**; no permiso READY en este commit. W1 OPEN, W2 STOP, V2 productiva OFF.

## B1 — inventario de evidencia y aceptación
- HEAD de partida después del paquete Codex: `f01aa27de4a81ff68a8c5d6e60da2e51b21950f2`; writer verificado RELEASED epoch1175; `AGENTS.md`, pointer y protocolo se leen antes del código. El permiso anterior D1B1B está BLOCKED y consumido.
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9StagedMigration.kt` migra SOLO STAGED8→STAGED9, exige pointer completo y 5 stores, rechaza target activo, sidecars y descriptor foráneo. `FaroV5Finance9StagedMigrationTest.kt` 7/7 M0B ya certificado con CI 43953191957; NO reejecutar por inercia.
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt` líneas de métodos `openValidatedV2Finance9Stage` y `openValidatedV2Internal` mantienen Finance9 estrictamente STAGED; el primero abre un graph staged y NO debe utilizarse para un preflight READ_ONLY de datos financieros.
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9SealedReadPort.kt` es read-only y exige stage9 sellado pero **lee filas**; no hace falta usarlo para un indicador sin datos y expondría más información de la necesaria.
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Planning8DevCutoverOwner.kt` verifica journal VALIDATED, punteros y replay fence: referencia de identidad/recovery; no copiar su promoción schema8 a schema9.
- `v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/GenerationSwitchCoordinator.kt` YA gestiona CAS pointer, journal y rollback. El piloto no lo debe invocar.
- D1B1B código `bf30b5ea37791eda6909175e2508e0c488a5a940` pasó 6/6 JVM + compile W01, CI 44199936375 y guard 44199936427, no Finance9 activo ni G15.
- **Aceptación del piloto:** inspección de mantenimiento DEV con DICTAMEN SOLO INFORMATIVO de identidad+journal+stage9 sellado; 0 cambios de puntero/SQLite, 0 grants, 0 second ACTIVE graph, negativa explícita ante cualquier inconsistencia; tests JVM nuevos y XML/guard dirigidos. No afirmar Finance9 ACTIVE.

## B2 — comparación V4.4 y reutilización
V4.4 aportó AtomicApply, receipts, Undo y startup recovery; NO posee equivalente a Finance9 de esquema9 ni debe modificarse. Reusar `FileActiveGenerationPointer` (incluida `sequence`), `FileStorageGenerationLayout`/descriptor, `FileBootstrapJournal` y `BootstrapPhase`, `ExistingGenerationFormatReader`, `StagedSqlitePreflight` y `MaterialWriteQuiescencePort` existentes. El preflight **NO ES** un cutover ni un owner nuevo, tampoco prueba quiescencia entre procesos: requisito a certificar en la unidad posterior de activación.
Solo se agrega un detector DEV read-only y tests nuevos; si existe equivalente ya suficiente en el HEAD, no agregarlo y reportar delta cero.

## B3 — investigación externa documentada y alternativas
Fuentes primarias verificadas:
- https://www.sqlite.org/wal.html — WAL conserva modificaciones no checkpointed en sidecars: un target no sellado no es una generación portátil autónoma. **REFERENCIA / ADAPTAR** preflight previo a cualquier cutover.
- https://www.sqlite.org/pragma.html — `user_version`, `foreign_key_check`, `quick_check`/`integrity_check`; evitar SQL writable o pragmas que muten estado en un preflight. **USAR** verificación estricta mediante `StagedSqlitePreflight` existente.
- https://www.sqlite.org/foreignkeys.html — FK debe comprobarse explícitamente; versión9 aislada no se deduce por presencia de archivo. **REFERENCIA**.
Alternativa A: reutilizar el `FaroV5Planning8DevCutoverOwner` para activar 9: **DESCARTAR** porque opener/runtime certificado exact8 y se eludiría el rechazo ACTIVE9. Alternativa B: abrir `FaroV5GenerationStoreGraph.openValidatedV2Finance9Stage` para probar salud: **DESCARTAR** para esta unidad READ_ONLY porque abre stores y duplica grafo durante inspección. Alternativa C: preflight pure Kotlin con booleans suministrados por UI: **DESCARTAR**, fácilmente forjable. Alternativa D: verificación propia de disco/journal con primitives existentes y `StagedSqlitePreflight` 5 owners: **ELEGIR**; validar identidad antes/después, sin graph de runtime. Repetir lista de esquemas mínimos únicamente si el helper no los expone con visibilidad segura; documentar exactitud e impedir discrepancia en test.

## B4 — contrato acotado, rutas exactas y CI
`FaroV5Finance9DevCutoverPreflight` debe ser **internal / solo generación JVM DEV** y devolver dictamen tipado `OBSERVED_SEALED_STAGED9_ONLY` o `DENIED` con motivos, **nunca** `ACTIVATABLE` / `AUTH_GRANTED`. Parámetros provenientes de disco y maintenance: storageRoot, operationId, descriptor/staged, expectedActive ID+sequence y `MaterialWriteQuiescencePort`; no depender de permisos de UI ni de parámetros booleans de confianza. Leer y volver a leer pointer, descriptor, formato V2 marker, journal de una sola operación RESTORE en fase VALIDATED sin `materialWritesAccepted`, sin mantenimiento competidor; comprobar target distinto de ACTIVE y 5 stores exactos, schema9 Reality y versiones 1/2/3/4 restantes con checks FK/integrity y ausencia de sidecars. La quiescencia in-process NO equivale a cross-process; por ello el resultado JAMÁS autoriza promoción.
Tres rutas funcionales **exactas** propuestas:
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9DevCutoverPreflight.kt` nuevo; no modificar clase de opener o switch.
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9DevCutoverPreflightTest.kt` nuevo: casos positivos solo informativos (stage9 sellado + journal) y negativos: apuntador stale y ABA, target ACTIVE, descriptor/path falsos, marcador V2 inválido, schema7/8/unknown, sidecars, journal falta/fase/operación extraña, interrupción sin cambios de DB; casos nuevos no duplicar M0B 7.
3. `.github/workflows/faro-v5-s4-w01-d1b1c0-finance9-preflight.yml` nuevo: preflight protocolar del parent READY EXACT3; JDK21, `cd v5 && ./gradlew :store:generation:test --tests ar.com.faro.v5.generation.FaroV5Finance9DevCutoverPreflightTest`; verificar JUnit XML exacto y resultado de guard; evitar SDK Android (puro JVM) y suites históricas.
Cero cambios `main`, V4.4, M0B, UI W01, `FaroV5GenerationStoreGraph.kt`, `FaroV5Planning8DevCutoverOwner.kt`, `RealityStore.kt`, aplicación, manifests, seguridad opcional OFF.
Si una de estas rutas no alcanza por un requisito técnico REAL, STOP y registrar re-auditoría antes de ampliar permiso; NO improvisar cambios fuera de exact3.

## B5 — auditoría adversarial antes de código
Ver también `docs/v5/FARO_S4_W01_D1B1C0_CODEX_PREFLIGHT_ADVERSARIAL_B5_20261117.md`. Punto central: **un chequeo read-only puede ser observado y dejar de ser cierto un instante después**, así que su output no es una capacidad segura para activar ni un sustituto de las vallas en COMMIT real. Revalidar ante cualquier operación futura; probar ABA mismo generationId diferente `sequence`, quiescence ajena no certificada, journal conflictivo, SQLite WAL no sellado, 5 store/marker inconsistente, candidate source, staged symlink/path. Si no pueden verificarse, resultado DENIED. No usar credenciales, huella, red ni saldos reales.
Dictamen previo limitado: se puede considerar READY **solo para exact3, preflight informativo DEV** tras verificación del guard documental e independencia B5, nunca para corte Finance9, SQL writable o W01.

## B6
NOT_RUN_PRE_READY. Codex todavía NO ejecutado.

## B7
PENDING_INDEPENDENT_POST_CI. No confundir un test del preflight con evidencia de activación real.
