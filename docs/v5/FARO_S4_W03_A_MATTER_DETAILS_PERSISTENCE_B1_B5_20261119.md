# FARO V5 — W03-A · Asuntos tipados / persistencia / backup · B1–B5

Fecha 2026-11-19. Unidad `S4_W03_A_MATTER_DETAILS_PERSISTENCE`. Base auditada `200d30cbf1fe4076af1340b683c81c559e634809`. **Solo documentación, NO READY, B6/B7 NOT_RUN.** Diferencia de alcance sobre la preauditoría general W03: ocho rutas exactas incluidas generación/backup y workflow. W01/W02 B7 PASS DEV no se repiten.

## B1
- `AGENTS.md`, protocolo B1–B7, retroaudit, puntero, permiso actual BLOCKED de W02 y guardia 44509600669 SUCCESS revisados. Writer CAS solo para documentos.
- `MatterModel.kt` blob `c3470f8a051c91193f33652f617f1fe051d85719`: id/title/detail/status/dueAt/updatedAt, sin prioridad/reviewDate/historial. `MatterModelTest.kt` blob `29c2fc35fa9dd64afc7620f53d53d5bcb78a473a`.
- `RealityStore.kt` blob `c1d6e45ac3619f98e10582e4bb2b1cc38ef0ad2d`: `readMatters()` + `upsertMatter()`, revisión del owner, BEGIN/COMMIT/ROLLBACK y G4 restore. `matter_record` tiene seis columnas. Reality base schema7/Planning8/Finance9 activos por admisiones separadas; NO subir user_version por adición opcional ni abrir otra DB.
- `RealityObjectiveDetails.kt` blob `928b6985ce601ef82b82d7b1b4ba89d950a6a696` y `RealityObjectiveDetailsTest.kt` blob `61362b053ed0b7f0c2824a2855a73d70cce543d5` son patrón reutilizable ya B7 W02-A, con tablas lazy transaccionales y preservación de prefijo histórico.
- `FaroV5G4DomainSemanticCodec.kt` blob `3da4ce6377676ad80514d50b5ce18d2d115cb0ba` v1/v2/v3/v4, sí serializa ObjectiveDetails pero Matter solo campos base. La exportación-restauración FBS2/G4 perdería nuevos datos sin v5 propio; modificar este codec y probar v1..v4. `FaroV5ObjectiveDetailsBackupTest.kt` blob `89f559b04feb0da394ddb4ade06edf96873efe49`.
- Aceptación A01..A06: Matter legado no debe adquirir eventos ficticios, migración idempotente, stale timestamp/revision protegida, backup/restore extenso, historial append-only, fallo de transacción revierte datos/tablas/revisión. CI JVM exacta por SHA; Android/G15 no corresponden.

## B2
- V4.4 `FaroV4FollowUpSurface.kt` en `etacsantafe-source/FARO@bfa0ee71f5b9cd104baaadd71f1e77a61f203939`, prioridad, fechas y filtros. `FaroV4FollowUpReversalContractTest.kt` evidencia reversibilidad y registros frente a Undo. **Adaptar UX/semántica; NO copiar runtime ni DB**, mantener estados V5.
- Reusar W02-A Objetivos: detalles opcionales/default legacy, tablas de extensión lazy en Reality transaction, prueba de rollback y versión semántica G4 creciente sin reescribir secciones anteriores. No reabrir W02 ni Finance9 ni catálogo.

## B3
- [SQLite CREATE TABLE](https://www.sqlite.org/lang_createtable.html), [SQLite transactions](https://www.sqlite.org/lang_transaction.html) y [FK](https://www.sqlite.org/foreignkeys.html): **USAR** extensión de tablas `matter_details` + `matter_history` on-demand dentro de la misma conexión/transacción, FK a `matter_record`, filas sin extensión = valores legacy explícitos; event insert append-only. Frente a ALTER TABLE del registro base, **DESCARTAR** por cambios destructivos de schema7/8/9 y mayor invasión sin resolver historial. Ante error, SQLite revierte DDL/DML por la transacción, probarlo.
- [Android/Room migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions): **ADAPTAR** idea de ensayar reapertura y migración no destructiva, no imponer Room nuevo ni recreación. Reality actual usa androidx.sqlite driver y no schemas Room exportados.
- [Tasks.org](https://github.com/tasks/tasks) (GPL-3.0) **REFERENCIA** para prioridad/historial local, no copiar código ni añadir sync, servidor, nuevos servicios o dependencia GPL. Alternativas investigadas: (A) extensión tipada local reversible y wire versionado, **ELEGIR**; (B) columnas base + overwrite del historial, **DESCARTAR**; (C) store separado/servicio externo, **DESCARTAR**.

## B4
- Nuevo `MatterPriority LOW/NORMAL/HIGH`, `MatterDetails(priority=NORMAL, reviewDate: LocalDate?, history=[])`, evento tipado ordenado `MatterHistoryEntry(sequence, timestamp, kind, resultingStatus, reason)`, con validación de secuencia, timestamps, longitud de reason y último status igual al registro, sin fabricar historia para filas viejas.
- `MatterRecord.details` tiene default estable para callers históricos. El reloj de timestamp de Matter sólo avanza por mutación; replay idéntico no incrementa revisión. La escritura de nuevas tablas, filas e incremento del `matter_state` pertenece a la misma transacción activa del `RealityStore` (un único writer). No mutar al consultar.
- Tablas lazy `matter_details(matter_id,review_epoch_day,priority)` y `matter_history(matter_id,sequence,recorded_at_ms,kind,resulting_status,reason)`; validar 0/2 tablas, orfandad, secuencia y append-only al escribir; sin recodificar el registro antiguo. Null date sigue siendo no declarada; usar fecha civil UTC-independent `LocalDate`.
- Sección G4 conserva versiones 1..4 byte a byte en datos viejos, introduce v5 **solo** si Matter extendido. En v5 se codifican también las secciones de Objetivos como v4; requerir v5 canónica con al menos un MatterDetails no-default, rechazar truncado, trailing bytes y campos inválidos. FBS2/Finance9 siguen usando sección G4, por lo que su restore incluye MatterDetail.
- Rutas **exactas candidatas** sujetas a guardia/READY/preflight:
  1. `v5/owner/matters/src/main/kotlin/ar/com/faro/v5/owner/matters/api/MatterModel.kt`
  2. `v5/owner/matters/src/test/kotlin/ar/com/faro/v5/owner/matters/MatterModelTest.kt`
  3. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
  4. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityMatterDetails.kt` (nuevo)
  5. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/S4W03MatterDetailsTest.kt` (nuevo)
  6. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5G4DomainSemanticCodec.kt`
  7. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5MatterDetailsBackupTest.kt` (nuevo)
  8. `.github/workflows/faro-v5-s4-w03-a-matter-persistence.yml` (nuevo; disparador de rutas W03 y XML)
- Ninguna ruta de W01/W02/Auxiliary o Android. Modelo y SQLite tipado NO crean grant ni UI, ni autorizan Apply.

## B5
- **T-A01:** Legacy Matter read/reopen default NORMAL/null/empty; G4 v1/v2/v3/v4 exacto sin historia ficticia.
- **T-A02:** Upsert extendido sobre base 7, Planning8/Finance9 sin alterar `user_version`; creación lazy/idempotente de tablas y reapertura; evitar incompatibilidad en migración.
- **T-A03:** Two updates mismo id con timestamp obsoleto fallan; replay igual no sube revisión; snapshot no acepta duplicados.
- **T-A04:** G4 v5/FBS2v4 preserve priority/reviewDate/history con roundtrip, restauración idempotente y generación; los datos antiguos no cambian de wire.
- **T-A05:** Rechazar reescritura o borrado del prefijo, duplicado de sequence, timestamps no crecientes; motivo malformado; failure sin borrar eventos.
- **T-A06:** Forzar rollback de la primera escritura extendida con `AtomicApplyTransactionDecision.Rollback`: no quedan nuevas tablas/filas/revisión. Si falla la escritura de extensión, no quedan cambios parciales.
- ACL y generación en app **NO son parte de esta unidad**: los hosts W03-B/E las validarán. Comprobar que no se montan grants ni OS delivery, no se tocan main/V4.4/Finance ni W01/W02, ni V2 productiva ni robot. Si surge delta de FBS2 fuera del codec autorizado, STOP y ampliar auditoría/rutas ANTES de código. B6/B7 NOT_RUN.

## Puerta B6
Publicar esta auditoría documental, verificar HEAD/diff/guardia SUCCESS y writer libre; recién entonces emitir permiso READY con estas ocho rutas y evidence blob SHA de este documento para B1–B5 en commit DOC-ONLY independiente. Preflight exacto por ruta y código `FARO-UNIT:S4_W03_A_MATTER_DETAILS_PERSISTENCE`, luego CI JVM/XML y B7 independiente. Ninguna inferencia de PASS por planificación.
