# FARO V5 — S3.A1 N015 schema 7 · B7 RED_FIX de G4 (05/11/2026)

## B7 AUDITORÍA RED_FIX

HEAD funcional `18bd27b210347e9355628eb1836aa36211435d54`. Guardia #42106829624 SUCCESS. CI dirigida N015 [42106829637](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42106829637) SUCCESS exact SHA; logs `S3_REALITY_RECEIPT_XML_PASS 3/3`, `S3_FINANCE_ACTIVITY_XML_PASS 3/3`, `S3_FBS1_RESTORE_XML_PASS 4/4`, `S3_FBS2_SEMANTIC_XML_PASS 7/7`. No errores ni skipped en esos XML. CI G4 [42106829781](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42106829781) FAILURE: **3 assertions obsoletas** en `RealityDomainOwnersT2Test.kt` líneas 39, 149, 180 que esperan `store.schemaVersion() == 6` tras introducir esquema 7. Al corregir la primera, línea 45 también requiere `domainBackup.snapshot().schemaVersion == 7`; `backup.snapshot().schemaVersion == 5` debe **permanecer 5**. No modificar versiones de FBS1 legacy a ciegas.

## B5 RED_FIX — alcance, evidencia y pruebas adversariales
Unidad **misma** `S3_A1_N015_RECEIPT_TIME_STORAGE_BACKUP`, no nuevo owner, ni History/Home. Modificar solo `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityDomainOwnersT2Test.kt` para expectativas de esquema **7** y añadir test real de v6→v7:
1. Abrir/sembrar un RealityStore fresco en un archivo de prueba y restaurar un receipt COMMITTED sin fecha (snapshot legacy v5).
2. Simular esquema 6 persistido retirando la columna nueva con SQLite `ALTER TABLE execution_receipt DROP COLUMN commit_recorded_at_epoch_millis` y `PRAGMA user_version=6` fuera de la conexión de Reality. No tocar otros campos ni FKs.
3. Reabrir `RealityStore`: nueva migración agrega columna nullable, mantiene receipt/plan/fingerprint, `legacyUndated=true`, nuevos campos no reciben fecha ficticia; `schemaVersion==7`, `backup.schemaVersion==5` y integrity OK.
4. El test preexistente de 5→7 conserva perfil; 0→7, restore legacy y FBS1/FBS2 siguen validados por suites existentes.
5. Exigir CI exacta G4 y N015, sin cambiar expectativas del workflow para ocultar fallos. Si `DROP COLUMN` no estuviera disponible en SQLite bundled, corregir fixture sin perjudicar fuente ni bajar gate.

**Dictamen:** B7 `RED_FIX` para commit `18bd27b210347e9355628eb1836aa36211435d54`; la subunidad no queda PASS mientras G4 esté roja. Permiso de siguiente B6 restringido al mismo N015 + archivo test; B7 independiente posterior. S3.A1 todavía OPEN; N031/Guardados no completos; catálogo 172 y S2 congelado.
