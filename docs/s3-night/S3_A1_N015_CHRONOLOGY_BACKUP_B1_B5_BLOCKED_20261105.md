# FARO V5 — S3.A1 / N015 cronología y FBS · B1–B5 auditable (05/11/2026)

**Estado: PREAUDITORÍA B1–B5 ACOTADA COMPLETA PARA STORAGE-ONLY; NO PERMISO READY PUBLICADO, B6/B7 NOT_RUN.** No es un permiso, una migración ejecutada ni un cierre de S3.A1. HEAD de base `48e4d7fbb00df5cca5dcaf5553c7dad1ff5af761`, S2 8/8 congelada. Unidad acotada `S3_A1_N015_RECEIPT_TIME_STORAGE_BACKUP`, distinta del futuro owner federado `history.query`. Retener el B7 anterior N032 y no repetirlo.

## B1 — evidencia concreta de fuentes y respaldos
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`: `SCHEMA_VERSION=6`, tabla `execution_receipt(receipt_id, transaction_id, plan_fingerprint)` sin marca temporal; `commit()` inserta recibo durante `run()`, que COMMIT/ROLLBACK en SQLite. `receiptHistory.queryCommittedReceipts` filtra `COMMITTED` y pagina por `transaction_id` lexicográfico ascendente. Un ID opaco no es fecha.
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityReceiptHistoryPort.kt`: `hasCommitTimestamps=false` y el contrato indica explícitamente que no es `history.query` federado. `financeActivity` ordena por `recordedAtEpochMillis`, pero es **fecha de asiento financiero**, no timestamp duradero de COMMIT ni historial global.
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityBackupPort.kt`: `RealityCommittedReceiptBackup` solo 3 campos, `RealityBackupSnapshot` usa `LEGACY_BACKUP_SCHEMA_VERSION=5` sin hora de recibo. `readBackupSnapshot()/restoreOrVerifyBackup()` no podrían transportar/validar un nuevo reloj sin ampliación explícita. La restauración canónica requiere igualdad de snapshot.
- `v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/SemanticBackupSetCodec.kt`: FBS1 mágico `FBS1`, VERSION=1, serializa 3 campos de recibo. Un cuarto campo agregado sin versionar rompería lectura determinista y restauración antigua. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreSemanticCodec.kt` versión 1 **envuelve** FBS1, de modo que la compatibilidad debe probar FBS1 y FBS2.
- `v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/SemanticBackupRestore.kt`, `BackupManifestBuilder.addReceipt()`: huella canónica incluye `id/transaction/plan`, NO fecha. Sin modificación versionada, una marca nueva podría perderse o quedar sin integridad criptográfica.
- `v5/app/src/main/java/ar/com/faro/v5/app/FaroV5CompositionRoot.java`: `readActiveHomePort()` integra solo N032 Avisos; `history.query` no se enlaza con admisión ACL real, cobertura por dominio ni epoch de generación. Estado correcto para Actividad: UNKNOWN.

## B2 — comparación V4.4 y V5
- V4.4 `FaroV4GlobalHistoryGateway` filtra/pagina recibos, pero eso no demuestra cronología ni autoriza leer registros de otros propietarios. V5 ya tiene atomicidad y receipts canónicos; **ADAPTAR el dueño Reality**, no copiar un servicio V4 mutante al Home ni reconstruir una base paralela.
- En V5, FBS1/FBS2 y el fence de restauración son activos certificados de S2: cualquier cronología nueva necesita preservar ambos, no rebajar su aceptación.

## B3 — referencias externas, alternativas y decisión
- SQLite oficial ALTER TABLE: https://sqlite.org/lang_altertable.html: ADD COLUMN nullable es compatible con filas antiguas; no genera timestamps históricos y no sustituye prueba real de migración. **ADAPTAR** si se elige esquema 7.
- Android Developers migraciones y pruebas: https://developer.android.com/training/data-storage/room/migrating-db-versions: los cambios de persistencia deben conservar datos y probar rutas históricas. **REFERENCIA** de metodología; Reality usa el driver SQLite existente, no incorporar otra capa Room para esta solución.
- SQLite transacciones: https://www.sqlite.org/lang_transaction.html: confirmar que timestamp y `COMMITTED` se escriban en el mismo commit y el rollback los descarte. **ADAPTAR**.
- OWASP MASVS Privacy: https://mas.owasp.org/MASVS/controls/MASVS-PRIVACY-1/: la nueva lectura necesita autorización por dueño y cobertura tipada; no exponer recibos por una consulta Home sin ACL. **ADAPTAR**.
- Opción A **PROVISIONALMENTE PREFERIDA**: timestamp nullable de registro de confirmación en `execution_receipt` + versión durable nueva, fuente nueva cronológica con cobertura explícita de recibos legacy undated; extender backup con codec versionado y huella, sin inferir fechas antiguas.
- Opción B **DESCARTAR**: inferir tiempo de UUID, transactionId, rowid o fecha financiera: no son commit timestamps y la restauración puede alterar el orden.
- Opción C **DESCARTAR**: sidecar/tabla/cache en Auxiliary escrito en una segunda transacción: pierde atomicidad frente a Reality, crash y replay; duplica owner.
No se incorporan dependencias ni código externo, licencias o API pagas.

## B4 — delta mínimo candidate, NO implementación
1. Migración **6→7** en Reality, incluidas rutas históricas 0–5, manteniendo timestamp nullable para filas anteriores y `COMMITTED`+tiempo escritos atómicamente para nuevas.
2. Contrato temporal exacto: definir `commitRecordedAtEpochMillis` como instante observado dentro del commit atómico, no post-facto ni hora de creación. Distinguir reloj de pared y orden ante clock skew; no afirmar cronología perfecta sin prueba.
3. Mantener `receiptHistory` lexical para compatibilidad; añadir read-port de cronología **Reality-only** que señalice `legacyUndated`/cobertura parcial y no active Home. `history.query` final requerirá federación/ACL independiente.
4. Extender `RealityCommittedReceiptBackup` y codec FBS1 versión 2, con decodificación de versión 1, huella de recibo incluida para metadata nueva sin cambiar fingerprint histórico de datos v1; FBS2 contenedor y restore deben preservar bytes/versiones. No permitir timestamp forjado desde la UI.
5. Mínimos paths: `RealityStore.kt`, `RealityBackupPort.kt`, `RealityReceiptHistoryPort.kt`, `SemanticBackupSetCodec.kt`, `SemanticBackupRestore.kt`, pruebas de Reality + Backup/Restore/FBS2. Sin S4, V2 productiva ni catálogo.

## B5 — auditoría adversarial previa y salida sin bucle

**B5 PREAUDITORÍA DE DISEÑO: VERIFIED para la subunidad `S3_A1_N015_RECEIPT_TIME_STORAGE_BACKUP`**, con alcance **únicamente de persistencia Reality + backup versionado**; nunca equivale al cierre de `history.query` ni de A1. Es lícito abrir un permiso READY separado después de verificar HEAD, rutas y evidencia; **no** es lícito ejecutar B6 sin ese permiso. Las pruebas siguientes se ejecutan en **B6**, no son precondición circular de B5.

- B6-N015-01: esquema nuevo con `committedAtEpochMillis` nullable; migraciones reales de esquemas legados 0..6 y reapertura; versiones antiguas mantienen `null` explícito, no fecha inventada. Verificar integridad, claves foráneas y conservación de receipts.
- B6-N015-02: capturar el reloj observado **dentro de la misma transacción COMMITTED**; rollback, PREPARED, crash antes de COMMIT y reopen no exponen metadata falsa. Reloj inválido se rechaza; empates/retrocesos usan orden y cobertura tipados, sin prometer monotonicidad de pared no comprobada.
- B6-N015-03: backup `RealityCommittedReceiptBackup` transporta timestamp nullable con codec **FBS1 versión 2 para datos con tiempo**, y sigue decodificando FBS1 versión 1. Probar roundtrip, identidad exacta de bytes legacy, codec v2, restauración idempotente y manifest nuevo con tiempo vinculado a huella.
- B6-N015-04: FBS2 aloja FBS1 interno versionado sin cambiar su contenedor; probar FBS2 roundtrip con timestamp, truncamientos, version mismatch, tamper/timestamp modificado, límite de registros, exact restore.
- B6-N015-05: vista de recibos cronológicos **Reality-only** sin mezclar cursor lexicográfico histórico, con `legacyUndated=true` si hay registros anteriores; paginación estable con desempate. No montar Home ni afirmar cobertura global, sin nuevo ACL propietario.
- B6-N015-06: regresiones dirigidas de S2/S3, G5 y FBS2. Si falla una hipótesis, B7 RED_FIX específica y corregir sin repetir lo aprobado. No tocar V4.4, S4, V2 productiva, catálogo 172 ni datos reales.

**Diferencia obligatoria de alcance:** el futuro `history.query` federado, la admisión ACL del usuario, invalidaciones de permisos, cobertura entre dominios y los registros sin fecha requieren otra unidad B1–B5. No convertir a Home `Actividad reciente` desde la subunidad de storage. N031 continúa sin owner combinado y Guardados +8 sigue sin aprobación.

**Salida:** `B1–B5 VERIFIED_SCOPED_STORAGE_ONLY`, `B6 NOT_RUN`, `B7 NOT_RUN`, permiso actual `BLOCKED` **hasta emitir READY de esta unidad por rutas y SHA**. Este estado no exige ejecutar pruebas antes de autorizar B6; exige correrlas y verificarlas **para** B7. S3.A1 sigue OPEN y porcentajes sin cambio.
