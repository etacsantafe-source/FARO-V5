# FARO V5 · W05-A2C0C1B · SQL15 ACTIVE WAL-aware DEV read-only B1–B5

2026-11-26 · Repositorio `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`.
Baseline verificable antes de la publicación: `bd4a0eb10d47efe899dfb658061fab17917936b5`; protocol guard push `45888220227` SUCCESS.
Unidad independiente: `S4_W05_A2C0C1B_SQL15_ACTIVE_WAL_READONLY_DEV`.

**PREAUDITORÍA DOCUMENTAL: B6 NO EJECUTADO.** Esta evidencia NO equivale a permiso READY, prueba de aplicación, certificación de SQL15 ACTIVE, cutover, restauración, stock.consume o validación física Moto G15. El permiso anterior de C1A está BLOCKED/consumido. No cambiarlo junto con código.

## B1 — Estado real y brecha concreta

- W05-A2B 9/9 B7 PASS y W05-A2C0A 7/7, C0B1 32/32, C0B2 20/20, C0B3 28/28, C0C0 33/33, C0C1A 35/35 certificados en DEV; no repetir B6/B7 ni refactorizarlos. Último código C1A `b327656a14eea360b148122250f31929df7b7ffc`, CI dirigida `45887407597` SUCCESS, último documento `bd4a0eb10d47efe899dfb658061fab17917936b5`, guard `45888220227` SUCCESS, writer RELEASED época 2128 al inicio de esta preauditoría.
- C1A verifica archivo FBS2v12 autenticado, puntero + pin, SQL15 **STAGED sellado sin sidecars**, integridad y equivalencia semántica exacta de cinco owners. `FaroV5Household15PreCutoverReadback.verifyOnly` entrega `EvidenceOnly`, **no** ticket para seleccionar ACTIVE ni permiso para operar stock.
- `FaroV5GenerationStoreGraph.openValidatedHousehold15ArchiveStage` y `RealityStore(householdStage15ReadOnly=true)` admiten sólo STAGED con `mode=ro&immutable=1`. `openValidatedV2Internal` rechaza de forma explícita `householdStage15 && activeGeneration`. No hay factoría interna propia para leer SQL15 ACTIVE y verificar un corte completado.
- Infraestructura reutilizable: `ActiveSqlitePreflight.requireAll` abre archivos SQLite existentes con `SQLITE_OPEN_READONLY | SQLITE_OPEN_NOFOLLOW`, sin `immutable`, acepta sidecars normales y verifica `user_version`, tablas, `quick_check` y `foreign_key_check`; `StagedSqlitePreflight.Schema.requiredTables` rechaza versiones fuera del perfil indicado. `FileActiveGenerationPointer` fija ID+secuencia. `FaroV5GenerationStoreGraph.openActiveHousehold14DevForReservationHost` muestra lease exclusiva y fence journaled+pointer; NO se reutiliza como permiso SQL15.
- `RealityStore.openHouseholdActiveRead` ya abre WAL-aware para 12/13/14. Falta un modo distinto SQL15 ACTIVE **read-only** con snapshot `RealityHousehold15ConsumptionRowsPort.captureConnection`, `validateExistingFinanceLayout(15)`, rechazo de todas las rutas de mutación y revocación antes/después.
- Los cinco stores ACTIVE pueden tener `-wal` y `-shm`; prohibirlos (regla STAGED) o abrir `immutable=1` daría falsos resultados. La lectura de una generación ACTIVE puede observar commits que aún viven exclusivamente en WAL. Requiere una transacción de lectura consistente y cierre del host ante cambio de lease, permiso o puntero.
- **Dependencia futura**: A2C0C2 aún no implementa la selección journaled de SQL15. C1B sólo certificará la mecánica de lectura contra fixtures DEV explícitos de selección existente; NO puede inventar una prueba de cutover real ni declarar que el sistema tiene SQL15 ACTIVE productivo.

## B2 — Reuso, retrocomparación y límites

- Reusar `ActiveSqlitePreflight` y los perfiles de schema exactos 4/15/4/2/2 de Workspace/Reality/Auxiliary/Commercial Evidence/Web (según lo ya certificado), comprobados contra el descriptor canónico y el puntero completo. Mantener el root lease y `MaterialWriteGate` que existen en el host DEV, sin segundo writer.
- Reusar `RealityHousehold15ConsumptionRowsPort.captureConnection` y su proyección tipada; no duplicar el ledger, no sumar tablas auxiliares ni iniciar transacciones Apply.
- Tomar el patrón 13/14 ACTIVE de graph+RealityStore: `SQLITE_OPEN_READONLY | SQLITE_OPEN_NOFOLLOW` con fence por lectura y apertura/cierre. En C1B **no** ofrecer un `RealityStore` o `FaroV5GenerationStoreGraph` mutables al llamador; exponer sólo evidencia y lecturas tipadas bajo control del host.
- V4.4 sólo aporta referencia de experiencia de compra/inventario y sencillez visual, no un protocolo para verificar SQL15, WAL, receipts o punteros; no trasplantar su almacenamiento ni modificar V4.4. El contrato de generación de V5 prevalece.
- Evidencia y realidad continúan separadas. El resultado C1B no otorga grants, no certifica stock disponible ni ejecuta consumo. Mantener guardias previas idénticas; sólo sumar rutas opt-in.

## B3 — Investigación externa y alternativas

Referencias técnicas primarias verificadas:
1. SQLite WAL: https://www.sqlite.org/wal.html — los lectores fijan un snapshot WAL con end mark; el lector de una base WAL puede necesitar `-wal`/`-shm`, y el archivo principal no incluye necesariamente el commit más reciente.
2. SQLite URI: https://www.sqlite.org/uri.html — `mode=ro` abre lectura; `immutable=1` supone que nadie modifica la base y desactiva locking/detección de cambios. Rechazado en ACTIVE.
3. SQLite C open: https://sqlite.org/c3ref/open.html — opción `SQLITE_OPEN_READONLY` y advertencia contra `nolock`; **nunca** desactivar locking en un ACTIVE concurrente.
4. SQLite PRAGMA: https://www.sqlite.org/pragma.html — `query_only` puede ser defensa adicional, no sustituye apertura read-only ni autorización de owner.

SQLite es un componente real reutilizado por el proyecto mediante AndroidX BundledSQLiteDriver, sin compra de API ni dependencia de servicio de nube. Las referencias se citan como especificaciones públicas, no se incorpora código externo.

Alternativas descartadas:
- `immutable=1` en ACTIVE: puede omitir páginas comprometidas presentes sólo en WAL y desactiva control de cambios.
- Tratar ACTIVE como STAGED sellado y rechazar cualquier sidecar: falsas denegaciones y/o pérdida de lecturas.
- Copiar o hacer checkpoint WAL en C1B: modificaría material y traspasaría el alcance read-only.
- Abrir el graph normal de cinco owners sin auditoría de sus inicializadores: puede crear/migrar SQLite y exponer puertos de escritura.
- Reusar el certificado de selección SQL14 o inventar `hasCompletedSelection=true`: falsa autorización SQL15.
- Usar sólo `quick_check` o hashes de archivos: no prueba datos semánticos ni instantánea WAL.
- Habilitar Apply, stock.consume, UI o automatizaciones con un resultado booleano de salud.

**Elección**: factoría interna SQL15 ACTIVE READ-ONLY para una generación ya seleccionada, bajo lease/ACL/pointer/journal exactos inyectados por el trusted DEV host, con preflight WAL-aware de cinco stores y snapshot tipado en una sola conexión Reality; no toca selección ni genera ticket. Hasta que A2C0C2 provea la autoridad journaled real, C1B sólo prueba fixtures y deniega uso productivo.

## B4 — Contrato propuesto para B6 separado, sin permiso aún

1. En `FaroV5GenerationStoreGraph`, una operación **internal** `inspectActiveHousehold15ForHeldDevHost` (nombre orientativo): exige root real, descriptor exacto, `ActiveGenerationPointer(sequence,id)`, lease exclusivo vivo, `MaterialWriteGate`, decisión de host y prueba externa de selección completada no falsificable. Verifica la misma selección/lease antes, durante y después. No cambia puntero, no publica nueva autoridad ni devuelve graph mutable.
2. Ejecutar `ActiveSqlitePreflight.requireAll` sobre los cinco stores con perfiles **exactos** y final-file/sidecar NOFOLLOW. Permitir WAL real, sin `immutable`, `wal_checkpoint`, writes de esquema ni resellado. La prueba de SQL15 exacto se hace antes de abrir el owner.
3. En `RealityStore`, un opt-in mutuamente excluyente `householdActive15DevReadOnly` con fence obligatorio, `SQLITE_OPEN_READONLY | SQLITE_OPEN_NOFOLLOW`, `foreign_keys=ON`, snapshot tipado SQL15, `validateExistingFinanceLayout(15)`, y `BEGIN DEFERRED` + `ROLLBACK` para snapshot consistente. No permitir `BEGIN IMMEDIATE`, esquema, migración, owner writer, ni `householdStockConsumptionWritableDev`.
4. No abrir cinco owners de dominio mediante el constructor por defecto si pudieran mutar: preflight SQLite cinco stores y sólo el lector `RealityStore` en este corte. Todo intento de devolver el `graph`, `ApplyCommitPort`, `AtomicApplyTransactionPort` o sesión stock falla. No usar `mapper.restoreOrVerify` sobre ACTIVE.
5. Usar fixtures de prueba DEV con un SQL15 previo explícito y selección verificable sin publicar un C2. Donde no pueda demostrarse origen journaled real, resultado `DENY` en la ruta del host; no certificar selección. C2 implementará recuperación/corte/rollback luego, bajo permiso propio.

Propuesta **EXACT5** de rutas para evaluación preflight de una futura unidad B6 (puede reducirse si la auditoría encuentra un camino más simple; cualquier cambio exige nueva preauditoría):
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household15ActiveDevReadTest.kt` (nueva)
- `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15ActiveDevReadTest.kt` (nueva)
- `.github/workflows/faro-v5-s4-w05-a2c0c1b-sql15-active-wal-readonly.yml` (nueva)

No alterar `ActiveSqlitePreflight.kt` salvo nuevo delta y permiso separado: la clase actual ya cumple el contrato WAL-aware de lectura. En particular, comprobar que las pruebas ejerciten su `Schema.requiredTables` exacto sin versiones legacy.

## B5 — Auditoría adversarial y criterios de aceptación

**Casos obligatorios** (JUnit original en CI dirigida, nombres únicos, errors/failures/skips=0):
1. Positivo fixture SQL15 ACTIVE, descriptor canónico, puntero exacto, lease y selección DEV completada comprobable, cinco DBs 4/15/4/2/2: snapshot tipado consistente y sin aplicar ninguna operación.
2. Commit SQL15 consumido únicamente en `reality.db-wal` (checkpoint aún no realizado, writer externo manteniendo contexto): READONLY observa el último commit, no la versión del archivo principal. Prueba separada que `immutable` no es la vía de la factoría.
3. `-wal`/`-shm` regulares válidos admitidos; `-wal`/`-shm`/db symlink, path alias y descriptor alterado denegados.
4. SQLite falta, versión 14/16 de Reality o versiones erróneas de cualquiera de los otros cuatro, tabla requerida ausente, quick_check/FK fallida: DENY antes de snapshot.
5. Puntero con mismo ID pero secuencia ABA, cambio entre preflight y lectura, cambio mientras se lee, generación no seleccionada, root lease ocupado/invalidado: DENY, cerrar sin fuga.
6. Owner permission revocada, falso `hasCompletedSelection`, journal incompleto, fuente/target confundidos: DENY y ninguna exposición de API Apply. Hasta C2 real no afirmar selección productiva.
7. Un intento de DML/`BEGIN IMMEDIATE` desde el lector falla; snapshot no crea recibos, compensaciones, reservas, consumos ni cambia contador de Finance/Household.
8. Conexión de lectura cerrada y liberada en throw/revocación; una falla parcial no deja graph vivo ni reinicia migración.
9. Mantener regresiones SQL14 ACTIVE y SQL15 STAGED C1A; no modificar A2B o A2C0A–C1A certificados.
10. Guard de siete bloques para el SHA funcional, workflow dirigido real y verificación del XML original. Publicar B7 PASS o RED_FIX con evidence, nunca por intención, y actualizar puntero/checkpoint. Guard histórico puede mostrar CI antigua rota; reportarlo sin esconder.

**B6 permitido sólo después de**: publicar esta B1–B5 con writer CAS, liberar writer, guard documental SUCCESS, validar blob+SHA+evidencia de los cinco bloques, emitir **permiso READY exact5** mediante commit sólo docs y guard SUCCESS, volver a verificar preflight para cada ruta. Luego adquirir writer nuevo para B6. Si alguno falla, STOP y permanecer READ_ONLY.

**Límites invariables**: V2 productiva OFF, stock real OFF, pagos OFF, Auto/Noche OFF, S4 DEV aislado, `main` y V4.4 intactos, Moto G15 NOT_RUN, sin APIs pagas. A2C0C2 journaled cutover, A2C1 stock.consume, A2D UI, A2E aceptación global continúan independientes y OPEN.
