# FARO V5 S3.A4 — Reality8 Planning STAGED + FBS2v3 paired backup/restore · B7 PASS_SCOPED
2026-11-06 Argentina. Unit `S3_A4_N031_PLANNING_STAGE8_BACKUP_RESTORE`. Final functional SHA `0b2cee7cc778e6bdac50aa08790cee54d6041ce9`.

## B7 — Auditoría final independiente
**PASS_SCOPED_ISOLATED_REALITY8_FULL_FBS2V3_RECOVERY**, no apertura de V2 productiva y **no S3.A4 CLOSED**.

### Trazabilidad del protocolo
- Preauditoría B1–B5 `docs/s3-night/S3_A4_N031_PLANNING_STAGE8_BACKUP_RESTORE_B1_B5_20261106.md`, blob `7e6fcf2379a98d190a43b8fc07470c7c6a88b9bc`, commit docs-only `b86c1fdc564ca4db5ff83add3a4a59efb09c5057`, permiso READY docs-only `52362471be8fc686869f0d3d57751762d9b7a80b`.
- Primer B6 `c28f61501a17f44f086599e8dcc9d9f2cf2fdec9` (10 paths exactas). Kotlin cross-module SMARTCAST_IMPOSSIBLE, CI FAIL [#42267205180](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42267205180). B7 RED_FIX separado ANTES del siguiente funcional: `docs/s3-night/S3_A4_N031_PLANNING_STAGE8_B7_RED_FIX_20261106.md`, blob `98d859ddd026c29310493b20445551f10784d4b7`, commit `7780ad55ec08011d9d73ac99e6709f1a5bda7893`. El segundo B6 `0b2cee7cc778e6bdac50aa08790cee54d6041ce9` solo modifica los dos paths ya permitidos: local estable del nullable de Planning y fixture sintética con occurrences empty; no cambios de alcance.
- El intento fallido intermedio anterior `c969ecfd...` fue reemplazado solo en rama de desarrollo con CAS force-with-lease al reconstruir historia `c28f615 → 7780ad55 (B7 RED_FIX) → 0b2cee7 (B6 corregido)`. Ningún cambio a main, V4.4 ni S2, ni datos de usuario. Guardia del protocolo verificada en la historia resultante.
- Writer CAS epochs 381–386 usados en B1–B6 y RED_FIX, liberado durante CI; B7 retomado en epoch387. Cierre previsto writer RELEASED epoch388.

### Evidencias CI, mismo SHA, todos SUCCESS
- Guardia B1–B7 [#42268956285](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42268956285): **SUCCESS**.
- CI integrado Reality8 + FBS2v3 [#42268956036](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42268956036): **SUCCESS**, JUnit XML exactamente **48/48**: `RealityPlanningStage8BackupT2Test` 7/7; `RealityG4DomainRestoreT2Test` 5/5; `FaroV5Fbs2V3PlanningStageT2Test` 6/6; `FaroV5Fbs2V3PlanningEnvelopeTest` 6/6; `FaroV5Fbs2G4StagedRestoreT2Test` 7/7; `FaroV5GenerationStoreGraphV2T2Test` 17/17; 0 failed/skipped/errors.
- FBS2v3 compat and legacy export fences [#42268956037](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42268956037): **SUCCESS**, XML **29/29** incl old G4 and v1.
- G4 public restore negative and old replay [#42268956135](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42268956135): **SUCCESS**.
- G4 internal stage and legacy public-fence [#42268956356](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42268956356): **SUCCESS**.
- V2 isolated core [#42268956270](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42268956270): **SUCCESS**; Chat session XML10/10, G5 host1/1, M2 isolated7/7, schema7 generation17/17, M2B4/4 and M1 isolation4/4. No physical G15 test.
- Todas las seis Actions terminaron `completed/success` para SHA `0b2cee7cc778e6bdac50aa08790cee54d6041ce9`, verificadas por GitHub.

### Alcance implementado con persistencia real en STAGED
- RealityStore sigue usando schema7 de forma predeterminada. `planningStage8=true` crea/migra SOLO generación aislada ya verificada. En CI: migraciones sintéticas 5,6,7→8; SQLite schema8 con `reminder_rule`, `reminder_occurrence`, `monthly_plan_item` y revisiones, restricciones y FK; snapshot/persist/reopen, replay idempotente, rechazo nonfresh/foreign, tamaño de sección guardado.
- Nuevo `RealityPlanningBackupSnapshot`, source snapshot paired bajo el mismo Reality lock con legacy y G4; `FaroV5FiveStoreBackupCapture` exige FBS2v3 aun si Planning está vacío en schema8; FBS2v3 AEAD no omitió sección.
- `restoreAuthenticatedPlanningStageOnly` es internal, se autentica y fija identidad de archivo y reintenta secciones legacy/G4/Planning; tests validan cinco stores, crash parcial, substituted archive, public denial, stage G4-only denial, sin cambio de puntero de generación.
- **No certificado:** ningún CRUD/AtomicApply de usuario, ninguna integración de chat, Home/N031 o Activity/N015, ni permisos host para esquema8, ni activación V2 productiva, ni backup/export/restauración públicos v3, ni pruebas físicas X01–X12 G15. Ninguna afirmación de A4 cerrado ni de nuevos datos de usuario. Los FBS2v1/v2 públicos continúan sin poder respaldar Planning. Los owners G4 y la compatibilidad schema7 conservan CI verde.
- S2 CLOSED 8/8, S3 A1/A2 2/5 alcance automatizado; A3 Guardados +8 NO_APPROVED, catálogo172; A4 OPEN; A5 pruebas G15 NOT_RUN; S4 sin abrir, V2 prod/Noche/Auto OFF.

### Próxima unidad
Nuevo B1–B5 y permiso READY propio: **catálogo de operaciones de Planning + bounded write-before-commit bajo WriteCoordinator y trusted ACL/host**, con consumo exacto Reality8/FBS2v3, integración Home/Activity y negativas de autorización. No habilitar V2 activa/backup público ni declarar A4 cerrado antes de probar el circuito end-to-end y comprobar que el presupuesto de PLN1 se impone para TODA escritura incremental. No repetir lo ya certificado.
