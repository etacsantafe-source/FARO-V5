# FARO V5 S3.A4 — Planning Reality schema8 STAGED-ONLY + FBS2v3 full paired capture/restore · B1–B5
Fecha 2026-11-06 AR. Baseline `ed4889c96831bb47333bd2a88a1b3dbd38835a4f`. Unidad `S3_A4_N031_PLANNING_STAGE8_BACKUP_RESTORE`.
**Scope test-only, no Android host, no user CRUD, no active-generation schema8 writes, no v3 public export/restore.** Permitir escritura persistente de datos sintéticos SOLO dentro de una generación STAGED, tras garantizar el mismo owner en la captura/restore interna.

## B1 — Evidencia concreta / condiciones binarias
- Protocolo B1–B7 en AGENTS y permiso `BLOCKED` tras `S3_A4_REALITY8_FOUR_VERSION_PREFLIGHT`, código `c6ec477a` dirigido 23/23, guard CI PASS, writer RELEASED epoch380. S2 CLOSED 8/8, S3 A4 OPEN, V2 productiva/Noche/Auto/S4 OFF, Guardados +8 no aprobado catálogo172.
- `RealityStore` esquema7 predeterminado, `initializeSchema` ya migra 5/6→7 con WAL y transacción; `domainBackup` snapshot tipo G4 exige wire schema7. `RealityFbs2BackupSnapshot(legacy,g4)` hoy mismo mutex Reality; `FaroV5FiveStoreBackupCapture` no tiene Planning. `FaroV5FiveStoreStagedRestore` G4 privada, planning pública e interna rechazadas; FBS2v3 encode/decode y FAROBAK2 AEAD funcionan desde B7 anterior.
- Concretar UNA ruta STAGED para abrir Reality8 opt-in verificando formato, descriptor, pointer no activo, precarga READ_ONLY de versiones 5,6,7,8 y cinco stores, sin cambiar la puerta `openValidatedV2` schema7 ni la app.
- Crear dos owners de almacenamiento (`reminder_rule`/`reminder_occurrence`, `monthly_plan_item`) + revisiones separadas. Esquema8 incluye nuevos records y estados, FOREIGNS, checks; construir `RealityPlanningBackupSnapshot` desde esos SQL reales, capturar paired 3 subdomains (legacy+G4+Planning) bajo el mismo mutex y lease global cinco stores. Modelo de transporte `PlanningDomainSectionSnapshot` se transforma explícitamente en el boundary generation para evitar ciclo de dependencias.
- Añadir restauración Planning idempotente y transaccional; FBS2v3 solo en método interno `restoreAuthenticatedPlanningStageOnly`, archive AEAD y pin antes de primer cambio. Public restore/export quedan negados. Si stage tiene una combinación ajena/no fresh STOP antes de pisar. Recuperación tras crash en mitad de las secciones mantiene pointer, repite bajo mismo pin.
- **Aceptación:** stage8 con datos sintéticos persistidos + FBS2v3 roundtrip de 5 stores y 4 Reality owners en CI; v1/v2 y schema7 iguales; source/target collision, auth tamper, replay sustituto, foreign owner y crash parcial bloqueados. Stage8 no es permiso user-facing, no N031 counts ni Android físico.

## B2 — Reutilización V4.4 / V5
- V4.4 separaba Rule vs Occurrence y Planning por mes civil; código V4.4 solo referencial (G4 previos) sin copiar DB, UIDs, jobs/scheduler. La verdad no deriva de Notificaciones ni Finance ni fecha calculada.
- Reutilizar tipos puros `owner:reminders` y `owner:monthlyplan`, `RealityStore` / WriteGate / mutex, schemaTransaction y existing G4/legacy import, `StagedSqlitePreflight.Schema` perfiles 8/7/6/5, `FBS2v3` four-section transport, `FaroV5FiveStorePortableBackupCrypto`, `FileRestoreArchiveIdentity` y tests G4. No sexta base, no cambios de catálogo ni nuevos permisos.
- No modificar `SCHEMA_VERSION=7` del Reality default: stage8 con constructor flag cerrado y opener explícito. G4 wire schemaVersion7 sigue aunque Reality SQL sea8. Acoplar el version del G4 al SQL destruiría compatibilidad v2; DESCARTAR.
- Alternativa A schema8 global automática en constructor predeterminado: DESCARTAR (V1/V2 productiva y legacy). B crear DB Planning aparte: DESCARTAR (rompe único Reality owner y lease). C opt-in STAGED y respaldo v3 completo bajo mutex, sin host: ELEGIR.

## B3 — Investigación externa / criterios negativos
- SQLite https://www.sqlite.org/lang_createtable.html : integridad de FK y CHECK en tablas; ADAPTAR triggers/constraints transparentes y transacción única.
- SQLite https://www.sqlite.org/lang_transaction.html : BEGIN IMMEDIATE, rollback al fallo; ADAPTAR restore atómico de owner. Distintos stores no pueden ser una única transacción SQL; usar archive pin, idempotencia y global quiescence existentes.
- SQLite WAL https://www.sqlite.org/wal.html: nunca hacer preflight STAGED immutable sobre WAL caliente, ACTIVE read-only no immutable. REUTILIZAR los dos existentes sin relajarlos.
- Android https://developer.android.com/training/data-storage/room/migrating-db-versions : probar rutas y no usar migración destructiva; REFERENCIA, código actual driver SQLite manual sin Room.
- Otras opciones: export de v2 sin Planning (descartar silenciosamente) RECHAZAR; derivar Reminder desde Auxiliary/Notice RECHAZAR; cambiar clase FBS2 en mismo magic sin versión RECHAZAR. Ninguna dependencia paga ni código externo reutilizado.

## B4 — Diseño exacto, archivos de B6 y pruebas
1. `v5/store/reality/build.gradle.kts`: API de dos contratos owner existentes, sin librería extra.
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityPlanningBackupPort.kt`: modelo Planning schema8, snapshot read y restore stage-only port, revisiones y canonical/budget.
3. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityDomainBackupPort.kt`: añadir `planningDomains` nullable con default null al paired backup, null NO es empty certificado.
4. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`: flag constructor opt-in stage8 al final; esquema 8 solo opt-in, migration 5/6/7→8 por transacción, tablas+FK, validación y snapshot/restore idempotente; `domainBackup` wire sigue7. Nada de user CRUD ni scheduler; backup legado niega dominios no vacíos.
5. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`: `openValidatedV2PlanningStage8` (stage descriptor canonical + pointer distinta), preflight 8/7/6/5 sin afectar los openers previos, RealityStore stage8 opt-in.
6. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreBackupCapture.kt`: paired Planning desde el MISMO `reality.fbs2Backup.snapshot()` y v3 obligatorio aún si revisiones=0 en stage8.
7. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreStagedRestore.kt`: interno v3 stage8 bajo pin+lease y replay exacto, antes de permitir G4 v2 / legacy; métodos públicos siguen negando.
8. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityPlanningStage8BackupT2Test.kt`: fresh/7→8 y legacy 5/6 fixtures, durable reopen, snapshot real, idempotencia, foreign, bounds y default schema7 sin cambio.
9. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Fbs2V3PlanningStageT2Test.kt`: capture+AEAD+internal stage restore, source/G4/Planning four-owner data and revisions, partial-crash retry, public/G4 fence, substituting archive and pointer guard.
10. `.github/workflows/faro-v5-s3-a4-planning-stage8-directed.yml`: gradle JVM exact tests+v1/v2/G4 regressions, XML and V2 core integration. CI same SHA.
- Negativas en stage migrations: schema8 sin tablas, corrupt, legacy schema4 no admitido, foreign records, bigger-than-FBS2 Planning; no data loss de Object/Matter/receipts, no user records.
- Datos sintéticos inyectados únicamente por internal stage restore port en test; más tarde implementar user CRUD/AtomicApply con validación de cuota antes de commit + ACL. `global_pending_count=UNAVAILABLE`.

## B5 — Auditoría adversarial / condición READY
- **Scope estrictamente aislado**: ninguna producción de schema8 ni activación V2, ningún usuario crea Rule/MonthlyPlan por ahora. SQLite stage8 opt-in sólo sobre generation no activa y descriptor validado; no debería admitir in-place cambio de generación activa ni WAL en staged. Reversión de migration stage8 no destructiva (rollback y checkpoint en stage, no pointer).
- Protección de datos: FBS2v3 decode completo antes de pin/cambio; stage8 paired snapshot bajo Reality lock, stage replay legacy→G4→Planning con frescura/replay exacto. `FBS1` y FBS2v1/2 deben seguir imposibilitados de respaldar Planning real y no pueden ser restaurados dentro de destino stage8 como si Planning vacío certificado. Reject antes de pin.
- Cotas: <=20k filas por lista, <=4MiB de sección Planning; validar antes de commit del restore. Preservar orden UTF8 BINARY y FK occurrence.rule; no parseo de aviso como fuente. Private restore no debe sobrescribir owners ajenos; revisiones no se reinician.
- Crash: ident archive pin durable, parar después de Reality, reintento misma archive sólo; no cambiar pointer. Compilación Android/host aislado CI necesario, G15 NOT_RUN. CI roja => RED_FIX del mismo unit o STOP, no B7 PASS falsos.
- **READY limitado SOLO a estas 10 rutas y stage-only; NO aprobar user CRUD, app V2 productiva, export/restore públicos, A4 cierre, N031 Home ni nuevas operaciones catálogo**. Condición B7: directed XML sin fallos + guard + regresión de V2. Si no, B7 RED_FIX/WAIT_CI.
