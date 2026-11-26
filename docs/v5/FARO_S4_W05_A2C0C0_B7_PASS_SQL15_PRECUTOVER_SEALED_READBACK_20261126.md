# FARO V5 — W05 A2C0C0 — B7 PASS SQL15 precutover sealed read-only, no selection

2026-11-26 Argentina. Repo `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`.
**Unidad:** `S4_W05_A2C0C0_SQL15_PRECUTOVER_SEALED_READBACK_DEV`.
**B7 PASS SCOPED DEV — 33/33 tests**. Esta unidad **no es** A2C0C cutover global, ni autoriza SQL15 ACTIVE/Apply.

## B7 — Evidencia real y alcance cerrado

- Base certificada `1bdb2b528af02d45acfb67f3d8380058482f18c6`, A2B cerrado 9/9 y A2C0A, A2C0B1, A2C0B2, A2C0B3 ya B7 PASS. No repetir esas implementaciones.
- B1–B5 propia `docs/v5/FARO_S4_W05_A2C0C0_SQL15_PRECUTOVER_READBACK_B1_B5_20261126.md`, commit `a0c9c9d1ea39717b44862a46e75a306e7e8ac991`, blob `b9ecc3e135f435744dc50ed6b9fbd09ec38ac2d7`, guard `45883254300` SUCCESS.
- READY exact4 en commit documental `a30ae8127b9a152ea33855a3ef1014a2607547f6`, guard `45883377961` SUCCESS. No se heredó permiso anterior.
- B6 final con `FARO-UNIT:S4_W05_A2C0C0_SQL15_PRECUTOVER_SEALED_READBACK_DEV`, código SHA **`097104fc28960bc1cbff9e3589e1829f3393e96b`**, cuatro rutas: `FaroV5Household15V12StagedReplay.kt` (helper tipado interno, no cambia replay), `FaroV5Household15PreCutoverReadback.kt` (nuevo, READ_ONLY), `FaroV5Household15V12StagedReplayTest.kt` (+5 tests, total 13), y workflow `.github/workflows/faro-v5-s4-w05-a2c0c0-sql15-precutover-readback.yml`.
- Guardia funcional `45883689847`, job `140129003631`, COMPLETED **SUCCESS**. Preflight exact4 dentro CI **PASS**.
- **CI exact SHA** https://github.com/etacsantafe-source/FARO-V5/actions/runs/45883689940, job `140129003335` COMPLETED **SUCCESS**. Tests Generation `BUILD SUCCESSFUL in 4m 7s`; Reality `BUILD SUCCESSFUL in 41s`. Verificación de XML originales `Verify original JUnit XML test vectors` **SUCCESS**.
- Vectores XML originales dentro CI: `FaroV5Household15V12StagedReplayTest` **13/13**, `FaroV5Household14V11StagedReplayTest` **5/5**, `FaroV5Household15Fbs2CodecTest` **8/8**, `RealityHousehold15ConsumptionRowsPortTest` **7/7**; **33/33** sin failures/errors/skips y nombres de testcase únicos.
- Artefacto original `w05-a2c0c0-sql15-precutover-readback-xml`, ID **15148222965**, GitHub digest `sha256:f01f1a5126ab511ee9ae9ad60fcd93192d35be28721626a2d36697e04434eeba`, https://github.com/etacsantafe-source/FARO-V5/actions/runs/45883689940/artifacts/15148222965. La auditoría de XML se ejecutó **dentro de CI**, no descargar ZIP fuera de GitHub ni afirmar verificación independiente.
- C0 verifica exclusivamente: cinco SQLite selladas e inactivas sin sidecars, versiones/tablas claves exactas e integridad y FK, archivo original FBS2v12 autenticado con pin, puntero esperado sin ABA, origen≠target, TODA historia SQL15 receipts/purchases/undo/reservas/consumos exacta, y hash de cinco archivos antes/después para detectar mutación. Rechaza passphrase/archivo v11/corrupto/reseal, filas divergentes, perfiles incorrectos y sidecars. Retorna `EvidenceOnly` (no concede capabilities).
- **No sobreinterpretar:** los cuatro stores no-Reality tienen check estructural/integridad, **no comparación semántica 1:1 con todas las secciones FBS2v12** en esta unidad. No se publicó validador de read host SQL15 ACTIVE ni `GenerationSwitchCoordinator`. Por eso el cutover CAS sigue PROHIBIDO: A2C0C1 semántica completa + host DEV READ_ONLY, A2C0C2 cambio journaled + recuperación son unidades futuras con B1–B7 y READY propios. Si C1 no pasa, C2 STOP.
- Varias workflows anteriores activadas por rutas compartidas pueden marcar FAILURE en preflight antiguo de su unidad: no equivalen a regresión compilada pero tampoco CI global verde; mantenimiento separado. La CI dirigida nueva y la guardia de protocolo sí son SUCCESS.
- **Cierre:** consumir permiso exact4 como BLOCKED y publicar pointer a C0 B7 PASS con writer RELEASED. Esta frase es condición a verificar contra ref remoto y guardia documental, no adelanta certificación.
- V2 productiva, stock real, pagos y Auto/Noche OFF. Main/V4.4 no tocados. G15 físico NOT_RUN; no APIs pagas.
