# FARO V5 — W05-A2C0A · B7 PASS scoped DEV SQL15 STAGED

Fecha: 2026-11-26 (Argentina). Repositorio `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`.
Unidad `S4_W05_A2C0A_SQL15_CONSUMPTION_STAGED_SCHEMA_DEV`. Esta certificación corresponde **sólo a esquema SQL15 de consumo y migración sobre STAGED inactivo**; NO certifica consumo de stock real, backup FBS2v12, A2C completo ni prueba física G15.

## B7 — Auditoría final de implementación y evidencia

- Antecesor A2B DEV **9/9 B7 PASS** sin repetición: `docs/v5/FARO_S4_W05_A2B1C_GLOBAL_B7_PASS_A2B_CLOSED_20261126.md`, checkpoint `3fff7848b2824b066f365b0a399376c8263dcd42`.
- Preauditoría diferencial B1–B5: `docs/v5/FARO_S4_W05_A2C0A_SQL15_CONSUMPTION_STAGED_SCHEMA_B1_B5_20261126.md` blob `51fa254a5c3eb9837964e21381a02f09b38200c1`; commit documental `0d50f1f656b91d8459a74d1032cd0c78a32f8184`; guardia protocolo **45872148641 SUCCESS**.
- Permiso independiente **READY exact4** sobre `ac037c55621c4694a6251d0e2c8ac7238a77b6ec`, guardia documental **45872338661 SUCCESS**. Preflight exact4 `FARO_PROTOCOL_PREFLIGHT_PASS` comprobado en job dirigido.
- B6 funcional implementado **exactamente cuatro rutas** en `dbe04c32023b9f31ca2bcf94d10d16945b675568`:
  1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15ConsumptionSchema.kt`
  2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15StagedFilePort.kt`
  3. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15ConsumptionSchemaTest.kt`
  4. `.github/workflows/faro-v5-s4-w05-a2c0a-sql15-consumption-schema.yml`
  Sin mezcla de documentación/permisos con código en el mismo commit. La guardia del commit funcional **45872779443 SUCCESS**.
- **CI dirigida del SHA exacto:** https://github.com/etacsantafe-source/FARO-V5/actions/runs/45872779600 — ID **45872779600**, **SUCCESS**, job **140092173042 SUCCESS**. Logs: `FARO_PROTOCOL_PREFLIGHT_PASS`; `BUILD SUCCESSFUL in 3m 17s`; tarea `Verify original JUnit XML` SUCCESS.
- El paso de CI verifica en el reporte JUnit original `TEST-ar.com.faro.v5.store.reality.RealityHousehold15ConsumptionSchemaTest.xml` el vector **tests=7, failures=0, errors=0, skipped=0**, y siete casos `testcase`. XML preservado como artefacto **15144155911**, nombre `w05-a2c0a-sql15-consumption-schema-xml`, ZIP digest GitHub `sha256:83facf0068b3d498978427ad5d52cd1260468ead2971b9b000f418f19fe7fd04` (https://github.com/etacsantafe-source/FARO-V5/actions/runs/45872779600/artifacts/15144155911). **La verificación del XML se efectuó en CI; no se afirma descarga/auditoría independiente del contenido ZIP en este chat.**
- Pruebas dirigidas 7/7 cubren: preservación SQL14 y otros owners en migración STAGED; rollback por lease revocado; versiones/rutas no autorizadas/corrupción; rechazo STRICT de eventos malformados, duplicados y UPDATE/DELETE; Undo sólo con origen/mismos atributos y cantidades dentro del saldo; checkpoint ocupado con estado COMMITTED recuperable; validación DDL de reapertura y stage vacío. Estas pruebas son sobre fixture DEV, NO sobre stock físico real.
- Alcance: tabla append-only `household_stock_consumption_event`, `user_version=15` sólo en archivo STAGED, historia SQL14 y reservas preexistentes verificadas en fixture. No se abrió una ruta de `stock.consume` Apply ni SQL15 ACTIVE, no se generó ningún consumo real. Las garantías de balance disponible, reservas y consumo todavía dependen de siguientes unidades.
- Restricciones: **V2 productiva OFF, stock real OFF, pagos OFF, Auto/Noche OFF, G15 NOT_RUN**, sin APIs pagas; main, V4.4, los esquemas SQL13/SQL14 certificados, Finance y crypto intactos.
- **Cierre binario:** A2C0A **B7 PASS SCOPED DEV 7/7**; permiso READY exact4 **CONSUMIDO/BLOCKED** en este checkpoint. A2B permanece CLOSED 9/9. A2C0B, A2C1, A2D, A2E continúan OPEN/NOT_RUN; no confundir cierre de A2C0A con A2C global.

## Próximo paso sin repetición

Preparar B1–B5 y permiso nuevo para **A2C0B1 — FBS2v12 consumo tipado y autenticado**, preservando compatibilidad FBS2v11 y evitando restaurar/activar SQL15 hasta completar las unidades de filas, replay y cutover. Sólo después considerar Review/Apply de consumo A2C1.

**Nota de protocolo:** la liberación del writer y guardia del commit de documentación que contiene este cierre deben verificarse luego de publicarlo; la certificación de pruebas es del SHA funcional `dbe04c32023b9f31ca2bcf94d10d16945b675568`.
