# FARO V5 · W01-M0A Finance9 STAGED schema JVM — READY estricto
**Fecha AR:** 2026-11-16. **Unidad:** `S4_W01_M0A_FINANCE9_STAGED_SCHEMA`.
**HEAD B1–B5:** `5ea02d77e5f9c65352acbdc650de1dce5d52ef9e`, guard exacto `43909101705` completed/SUCCESS y writer RELEASED epoch 979 con last_checkpoint idéntico. B1–B5 firmados como evidencia documental blob `2abfa5215c57266f1a60438af426e4ccb497ca48` en `docs/v5/FARO_S4_W01_M0A_FINANCE9_PREAUDIT_B1_B5_20261116.md`; no heredar READY B2C2.

## B1
Se verifican fuentes RealityStore 7/8, staged-only RealityPlanningStage8BackupT2Test, módulo JVM y compatibilidad G4/FBS2, sin cambiar esos activos. Evidencia en preaudit exacto.
## B2
Reutilizar baseline y JUnit existentes. No volver a implementar apertura7/8 ni reejecutar subunidades B7 PASS de W01.
## B3
SQLite oficial transaction/FK/rollback y AndroidX SQLite ya existente, sin API externa; se rechaza migrar ACTIVE o elevar SCHEMA_VERSION default.
## B4
READY sólo para estas **tres rutas**:
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9Schema.kt`
- `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9MigrationTest.kt`
- `.github/workflows/faro-v5-s4-w01-m0a-finance9-directed.yml`
Fijar metodología: Kotlin internal stage8 connection, 7 tests XML exactos; no archivo de persistencia productiva Android.
## B5
Ninguna producción/cutover/backup/grant/host; no cuenta sin opening interpretada como 0. Rechazar schema errado y truncamiento de DDL; ForeignKey/check/epoch. W01-A/B históricos no se modifican.

## B6
READY_TO_IMPLEMENT; **solo** en las tres rutas después de ejecutar preflight local exacto por ruta y writer CAS contra HEAD. Código y permiso NUNCA en el mismo commit.
## B7
PENDING_FINAL_AUDIT; exigir JVM test XML 7/7, guard por SHA, revisión de tres rutas, rollback, source intacto, writer RELEASED, permiso consumido en checkpoint de documentación posterior.
