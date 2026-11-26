# FARO V5 — W05-A2C0B2 · B7 PASS SCOPED DEV SQL15 STAGED rows

Fecha 2026-11-26 (Argentina). Repositorio `etacsantafe-source/FARO-V5`; rama `work/s3-night-20261102`. Unidad `S4_W05_A2C0B2_SQL15_SEALED_CONSUMPTION_ROWS_DEV`.

## B7 — Evidencia final verificada

- **Límite claro:** esta unidad únicamente captura y restaura filas de consumo SQL15 en fichero sellado **STAGED inactivo** con lease exclusivo y readback; **no** autoriza SQL15 ACTIVE, consumo físico, `stock.consume`, Apply, backup cinco stores, cutover, UI, prueba G15, dinero ni pagos. A2C0B3/A2C0C/A2C1/A2D/A2E siguen abiertos.
- Antecesores sin repetición: W05-A2B 9/9 B7 PASS DEV; A2C0A SQL15 staged schema 7/7 PASS; A2C0B1 FBS2v12 tipado autenticado 32/32 PASS, checkpoint `ae365172a667ed62eebd92e462256e3ca8b90a74`. Permiso previo consumido/BLOCKED.
- B1–B5 preaudit específico `docs/v5/FARO_S4_W05_A2C0B2_SQL15_CONSUMPTION_ROWS_B1_B5_20261126.md`, blob `5143602fe08a5b52d9c3f128134103aaa0312abc`; commit `f46f957df1f2213e0da0792978a99ba347a164da`; guardia de documentación **45878843457 SUCCESS**.
- Permiso **READY exact7** independiente en commit documental `03c03451b676b3cc1eaf08e38d82fa69279b6760`, guardia documental **45878952665 SUCCESS**, paso de preflight exact7 verificado en job de código.
- B6 código funcional SHA **`fb9e559702e3ba50b83a19951794888252d740a8`**, FARO-UNIT correcto y exclusivamente siete rutas:
  1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold12PurchaseRowsPort.kt`
  2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold13CompensationRowsPort.kt`
  3. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold14ReservationSchema.kt`
  4. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold14ReservationRowsPort.kt`
  5. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15ConsumptionRowsPort.kt`
  6. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15ConsumptionRowsPortTest.kt`
  7. `.github/workflows/faro-v5-s4-w05-a2c0b2-sql15-consumption-rows.yml`
- Code guard `seven-block-evidence` **45879399953 SUCCESS**, preflight `FARO_PROTOCOL_PREFLIGHT_PASS S4_W05_A2C0B2_SQL15_SEALED_CONSUMPTION_ROWS_DEV` comprobado en la CI del SHA exacto.
- **CI dirigida SHA exacto:** https://github.com/etacsantafe-source/FARO-V5/actions/runs/45879400210 — job **140114602223** COMPLETED **SUCCESS**; `BUILD SUCCESSFUL in 2m 31s`. Paso `Verify original JUnit XML` SUCCESS, inspección del XML original dentro de la CI.
- **Vector XML original verificado por workflow:** `RealityHousehold15ConsumptionRowsPortTest` **7/7**, `RealityHousehold14ReservationRowsPortTest` **6/6**, `RealityHousehold15ConsumptionSchemaTest` **7/7**, total **20/20**, failures=0, errors=0, skipped=0 en cada suite, siete+seis+siete casos únicos. Artefacto `w05-a2c0b2-sql15-rows-regressions-xml`, ID **15145923073**, digest GitHub `sha256:115a9c6076c4428835400d1e71561cf38b8d4515f8a566314b36fd592762160d`; https://github.com/etacsantafe-source/FARO-V5/actions/runs/45879400210/artifacts/15145923073. **La verificación de XML se ejecutó en CI; no se reclama descarga independiente del ZIP en este chat.**
- Evidencia de prueba: preserva base SQL14, finanzas y needs; retorno/restauración de eventos CONSUME+CONSUME_UNDO; retry idéntico sin doble inscripción; rechazo de históricos divergentes, sobreconsumo/reservas, origen Undo no coincidente, colisiones, referencias inválidas, lease revocado, DDL/versiones/alias inválidos, y recuperación de estado COMMITTED ante checkpoint ocupado. Pruebas sólo en ficheros DEV y fixtures, no stock real.
- **Negativas y límites de interoperabilidad:** la ruta SQL15 de historia exige explícitamente expectedVersion 15, SQL12–14 conservan su API de versión exacta; el codec FBS2v12 existente no se modifica. La disponibilidad aún no se expone en host ni UI.
- Otros workflows históricos activados por modificaciones de archivos compartidos pueden aparecer como FAILURE en **preflight de sus unidades antiguas** (p. ej. SQL14 Rows o SQL12 rows), antes de ejecutar sus pruebas; esto no es un CI global verde, pero no sustituye el **PASS dirigido** ni la guardia de protocolo para esta unidad. Mantenimiento futuro separado de triggers históricos, con permiso propio; no alterar sin autorización.
- **Cierre:** A2C0B2 B7 **PASS SCOPED DEV, 20/20**. Consumir permiso exact7 a BLOCKED, anotar código `fb9e559702e3ba50b83a19951794888252d740a8`, checkpoint final, writer RELEASED. A2C0B3 siguiente unidad con B1–B5 y permiso nuevo; no heredar autorizaciones.

**Seguridad invariable:** V2 productiva, stock real, pagos, Auto/Noche OFF. Main/V4.4 intactos; G15 físico NOT_RUN. No APIs pagas.
