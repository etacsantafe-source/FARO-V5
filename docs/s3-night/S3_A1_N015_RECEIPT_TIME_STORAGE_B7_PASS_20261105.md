# FARO V5 — S3.A1 N015: Reality receipt time + FBS storage · B7 independiente (05/11/2026)

## B7 AUDITORÍA FINAL INDEPENDIENTE

**Dictamen: PASS_SCOPED** exclusivamente para `S3_A1_N015_RECEIPT_TIME_STORAGE_BACKUP`. **No** significa cierre de `history.query` federado ni de S3.A1. HEAD funcional `55ef7edd9373a020309df427de16e17ddae9eb0c`; baseline B1–B5 `docs/s3-night/S3_A1_N015_CHRONOLOGY_BACKUP_B1_B5_BLOCKED_20261105.md`, B7 RED_FIX en `S3_A1_N015_RECEIPT_TIME_B7_RED_FIX_20261105.md` y `S3_A1_N015_SCHEMA7_G4_B7_RED_FIX_20261105.md`.

### Código inspeccionado y autoridad
- RealityStore migra esquema 0–6 a **7** con `commit_recorded_at_epoch_millis` nullable; filas históricas quedan NULL, jamás se infiere hora desde transactionId/UUID/rowid. El nuevo valor se observa mediante reloj del owner dentro de la misma transacción SQLite de recibo COMMITTED; rollback se descarta. No es reloj monotónico y no promete hora de commit global a través de dominios.
- `receiptHistory` original con cursor lexicográfico se conserva por compatibilidad. Nuevo `timedReceiptHistory` es **Reality-only**, pagina por `recordedAtEpochMillis DESC, transactionId ASC`, informa `legacyUndated` y `globallyFederated=false`. **NO** se enlaza a Home ni se presenta como `history.query` multi-owner; no inventa ACL.
- `RealityCommittedReceiptBackup` admite fecha nullable. FBS1 preserva versión 1 para recibos sin fecha y usa versión 2 solo cuando hay marcas durables; decode admite ambas. La huella canónica agrega fecha cuando existe sin cambiar fingerprints legacy. FBS2 encapsula el FBS1 interno versionado y conserva roundtrip byte-identical para cada payload probado. `RealityBackupSnapshot` sigue legacy schemaVersion **5**; `RealityStore` y snapshot de dominios ahora **7**.
- Los únicos cambios funcionales de esta subunidad se limitaron a RealityStore, historial Reality, contrato de backup, FBS1/manifest y pruebas/controles específicos. No se alteraron S2 histórica, datos del teléfono, catálogo, main/V4.4, egress, V2 productiva o S4.

### Evidencia exacta de CI
1. Código inicial `78a5e9be`: guard [42106005370](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42106005370) SUCCESS; compilación inicial de tests roja por import `assertTrue` faltante, disposición B7 RED_FIX, no ocultada.
2. Código de corrección `18bd27b2`: guard [42106829624](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42106829624) SUCCESS; [42106829637](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42106829637) SUCCESS: `S3_REALITY_RECEIPT_XML_PASS 3/3`, `S3_FINANCE_ACTIVITY_XML_PASS 3/3`, `S3_FBS1_RESTORE_XML_PASS 4/4`, `S3_FBS2_SEMANTIC_XML_PASS 7/7`. G4 rojo solo por expectativas de esquema 6 obsoletas, B7 RED_FIX documentado.
3. HEAD `55ef7edd9373a020309df427de16e17ddae9eb0c`: tests G4 ajustados a esquema 7 y caso `realSchemaSixReceiptsMigrateWithoutInventingCommitTimes` (schema6 sin columna→7 con fecha NULL); workflow G4 exige **9/9**, sin tolerar skipped/fallos. Guard [42107761450](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42107761450) SUCCESS.
4. G4 [42107761539](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42107761539) **SUCCESS** exact SHA: `G4_REALITY_DOMAIN_XML_PASS 9/9`, `G4_LEGACY_XML_PASS RealityBackupFilteringT2Test 3`, `RealityStoreFinanceEntryT2Test 5`, `RealityStoreFinanceT2Test 6`; no failures/errors/skipped. Ejercitó persistencia y migración en SQLite de prueba.
5. N015 [42107761716](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42107761716) **SUCCESS** exact SHA: `S3_REALITY_RECEIPT_XML_PASS 3/3`, `S3_FINANCE_ACTIVITY_XML_PASS 3/3`, `S3_FBS1_RESTORE_XML_PASS 4/4`, `S3_FBS2_SEMANTIC_XML_PASS 7/7`. Se conserva el gate previo de V2 solo para su SHA histórico, sin atribuirlo al HEAD si no se volvió a ejecutar.

### Límites adversariales y siguiente unidad
- **No** garantiza time ordering correcto ante clock skew/reinicio ni un orden causal global entre dueños; registra tiempo observado de cada recibo Reality. No restaura una fecha real de receipts históricos sin metadata.
- **No** acredita ACL del historial, federation Reality/Workspace/Auxiliary, invalidaciones de permisos, navegación a referencia, ni presentación de Actividad; Home debe mostrar `No disponible` hasta bind genuino. Esas rutas necesitan preauditoría nueva y permiso separado.
- Pendientes N031: Matter persistente parcial pero sin prioridad/snooze ni owner combinado Reminder+MonthlyPlan con ACL; no publicar un conteo parcial. Guardados: +8 candidatos solo revisados, NO APROBADOS, catálogo 172.
- Pruebas Android Moto G15 y X01–X12 siguen NOT_RUN hasta S3.A5. S2 CLOSED 8/8, S3.A2 CLOSED_AUTOMATED_SCOPE 5/5, S3.A1 OPEN, S4/Noche/Auto/V2 productiva OFF. Sin migración real en teléfono.

**Veredicto:** `B7 PASS_SCOPED` N015 storage, nuevo código **BLOCKED** hasta nueva B1–B5 y permiso por unidad. Orden productivo: N031 owner combinado y/o integración autorizada N015 history.query, luego Guardados sin expandir catálogo y cierre S3.A1. Writer CAS liberable tras checkpoint; reportar estado y epoch real.
