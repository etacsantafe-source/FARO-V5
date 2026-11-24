# FARO V5 · W05-A1B0 · Reality11 en copia generacional sellada (B1–B5)

Fecha: 2026-11-24. Unidad `S4_W05_A1B0_REALITY11_SEALED_STAGE_DEV`. HEAD de revisión `24f90f1da69bc27327dfea0e17babfd88b696b2c`. Rama `work/s3-night-20261102`.
**Alcance exacto:** migración 10→11 de Reality *solamente* en una generación STAGED (copia sellada de los cinco stores), bajo gate de quiescence + lease host cerrado y pin de puntero. No activar, no cutover, no backup/restore v11, no stocks reales ni recibos físicos, no Finance mutación, V2 productiva OFF.

## B1 — Evidencia y problema acotado

- W05-A0 B7 PASS preview puro 10/10 (CI `45387220139`); W05-A1A B7 PASS staged schema `2e9c9bdf9cebc4cafcf0a0b66798c520022bb9a9`, CI `45389909485` SUCCESS 10/10. S3 XML counter redfix B7 PASS 21/21 CI `45391329067`, final guard `45392159773` SUCCESS. HEAD `24f90f1da69bc27327dfea0e17babfd88b696b2c`, writer RELEASED epoch 1800, permiso BLOCKED.
- `RealityHousehold11ReceiptSchema.installOnStagedTenConnection` instala una transacción SQLite atómica. No decide si la SQLite es origen ACTIVE, copia falsa o target con links. Falta la verificación de generación sellada.
- `FaroV5Household10StagedMigration` demuestra patrón 5 stores, root pinned, lease, quiescence, SHA-256 before/after, hardlink y symlink reject, DDL aislado, fsync, postflight 10. `FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage` exige Reality10 + Auxiliary4 sin sidecars/WAL pendiente y tablas base. `RealityHousehold10StagedFilePort` usa READWRITE|NOFOLLOW + synchronous=FULL + checkpoint TRUNCATE. Reutilizar, NO alterar W04 certificado.
- Contrato del usuario: recepción 7/10 ≠ pago 10/10; un stage nuevo no es una recepción materializada.

## B2 — Comparación V4.4 y reuso sin duplicar trabajo

- V4.4 SmartCompra/Stock L11–15 diferenciaba comprado/pagado/recibido; no importar su runtime.
- Reusar únicamente contratos V5 existentes: `GenerationStoreFiles.WORKSPACE_V2` (5), `FileStorageGenerationLayout`, `FileActiveGenerationPointer`, `MaterialWriteQuiescencePort`, `StagedSqlitePreflight`, `RealityHousehold11ReceiptSchema`. No nuevo owner ni SQLite activo v11, no modificar generación fuente ni puntero. El caller es dueño de `assertExclusiveClosedDevHost`; este adaptador nunca adquiere lease.

## B3 — Alternativas externas y riesgos

- SQLite `https://www.sqlite.org/lang_transaction.html`, `https://www.sqlite.org/wal.html`, `https://www.sqlite.org/pragma.html#pragma_wal_checkpoint`: transacción de schema, WAL/checkpoint y sync explícitos son compatibles con stage offline; adaptar, sin dependencias.
- Android `https://developer.android.com/topic/libraries/architecture/room`: migración SQLite real exige estrategia de versiones y validación de schema; **no** registrar Room autoMigration 10→11 en host activo antes de backup y recovery.
- Opción A elegida: migrar copia generacional completa sellada bajo fence, dejando root original y puntero sin mutación. Opción B rechazada: abrir ACTIVE Reality10 y subir user_version allí. C rechazada: copiar solamente `reality.db` ignorando los otros cuatro stores/sidecars. C no demuestra consistencia del snapshot.

## B4 — Diseño y rutas congeladas

**Exactamente 4 rutas nuevas B6:**
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold11StagedFilePort.kt`: READWRITE|NOFOLLOW, regular final path, FK ON, FULL, ejecutar installer A1A, `wal_checkpoint(TRUNCATE)` sin busy.
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11StagedMigration.kt`: usuario pasa `StagedStorageGeneration` canonizada de origen/copias, `expectedActive` pin, `MaintenanceOperationId`+host lease externo, evita alias hardlinks, comprueba hashes de cinco stores, contra `requireSealedHousehold10ArchiveStage`; migra sólo reality en target; force reality/directorio; comprueba original/otros4 intactos y Reality11 con nuevas tablas; no mueve pointer, no journal.
3. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11StagedMigrationTest.kt`: al menos 7 pruebas de cinco stores, negativo, pointer ABA y lease; no repetir tests específicos SQL ya certificados en A1A.
4. `.github/workflows/faro-v5-s4-w05-a1b0-reality11-sealed-stage.yml`: preflight 4 rutas parent READY, Java21 Android37, Gradle dirigido, comprobar XML exacto y preservar archivo.

Los materiales de entrada son Reality10 + Auxiliary4 en origen y target, con stage creado como copia completa y bytes iguales. **No implica** que el ACTIVE host soporte v11. En la salida `requireAll` Reality11 con `household_need`, `household_need_state`, `household_stock_receipt`, `household_stock_receipt_state` y ambas tablas nuevas vacías.

## B5 — Preauditoría adversarial

Pruebas B6 previstas (aún NOT_RUN):
1. Migración 5 stores sin tocar origen, cuatro stores secundarios, puntero o journal; stock SQL11 cero.
2. Rechazo target=source o ruta de target forjada.
3. Rechazo copia divergente/hardlink.
4. Rechazo versión de origen distinta (Reality9, 99), auxiliar incompatible.
5. Rechazo symlink de Reality en target, sidecar huérfano y enlaces extraños.
6. Rechazo cambio de pointer ABA durante etapa SQL, target queda sin activar (cuarentena).
7. Revocación de host lease antes de SQL deja stage v10.
8. Falla inyectada después de SQL no cambia origen ni puntero; target v11 STAGED pero **no se considera activo**.

**Límites negativos sin cobertura B0:** restore/backup Reality11 FBS2, cutover y reopen activo, WAL de base activa, idempotencia CAS/Apply, Undo, receipt físico, G15, UI. Son A1B1+ y A1C, con B1–B7 independientes; NO derivar PASS de este plan. Permiso READY sólo después de guardia documental y auditar estas rutas; no activar V2 productiva, Pago ni Auto/Noche.
