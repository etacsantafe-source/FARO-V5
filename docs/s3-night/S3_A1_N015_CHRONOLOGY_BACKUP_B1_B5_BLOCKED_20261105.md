# FARO V5 — S3.A1 / N015 cronología y FBS · B1–B5 auditable (05/11/2026)

**Estado: PREAUDITORÍA DOCUMENTAL COMPLETADA, B5 BLOCKED PARA CÓDIGO.** No es un permiso, una migración ejecutada ni un cierre de S3.A1. HEAD de base `48e4d7fbb00df5cca5dcaf5553c7dad1ff5af761`, S2 8/8 congelada. Unidad tentativa `S3_A1_N015_DURABLE_REALITY_CHRONOLOGY`. Retener el B7 anterior N032 y no repetirlo.

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

## B5 — auditoría adversarial y salida
- **BLOCKED** hasta detallar y demostrar en tests de migración: v0/v1/v2/v3/v4/v5/v6→nuevo esquema, reopen sin pérdida, columnas duplicadas, integrity+FK, sello de generation/pointer, backup de recibos antiguos, FBS1 v1 decode/encode con identidad y FBS1 v2/FBS2 con timestamps.
- Tests de crash/rollback PREPARED sin fecha visible; fecha registrada sólo junto a COMMITTED; backup+restore idempotente y material gate; tamper en timestamp detectado por manifest; límites de entradas; reloj inválido/reloj retrocediendo sin mentir sobre recencia.
- Probar que fuente legacy sin fecha no transforma datos desconocidos en 0 ni los ordena por UUID. ACL revocada, generación cambiada entre páginas, cobertura incompleta y ausencia de federation mantienen `Actividad reciente = No disponible`.
- **Criterio READY posterior:** rutas y tests nuevos acotados, diseño FBS1/FBS2 compatible y admisión segura con B5 plenamente verificado; permiso nuevo de esa unidad, no reusar N032. Si respaldo no queda demostrablemente seguro, STOP antes de B6.
- Pendientes N031 sigue sin Reminder/MonthlyPlan y campos de prioridad/snooze/owner; Guardados +8 aún CANDIDATE NOT APPROVED. No se puede cerrar S3.A1 por este documento.

**Dictamen:** B1/B2 constatados en código; B3 comparó soluciones reales; B4 diseño candidato; B5 **BLOCKED** por compatibilidad y pruebas todavía no autorizadas por permiso de unidad. No se escribió código ni corrió CI para N015. S3 desarrollo ~40% histórico estimado, 1/5 etapas con cierre automatizado, S3.A1 OPEN.
