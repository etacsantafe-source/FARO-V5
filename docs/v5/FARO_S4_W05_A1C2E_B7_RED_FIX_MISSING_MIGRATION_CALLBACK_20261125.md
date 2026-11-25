# FARO V5 · W05-A1C2E · B7 RED_FIX específico: falta beforeCommit en fixture SQL12

## B7 — RED_FIX verificable

- Unidad: `S4_W05_A1C2E_SQL13_ACTIVE_DEV_READ_ONLY`. Código original `93ab2c38b2430ae6a88ed2e8f533bfdd7d20bfbf`. El preflight de cinco rutas y guardia `45620921416` fueron **SUCCESS** en el mismo SHA.
- CI `45620922804`: **FAILURE** confirmado; job `139228507072`. Gradle llegó a `:store:generation:compileKotlin` satisfactoriamente y se detuvo en `:store:generation:compileTestKotlin` con `NO_VALUE_FOR_PARAMETER`.
- Localización exacta: `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household13ActiveDevReadHostTest.kt:68`. La llamada a `RealityHousehold12StagedFilePort.migrateTrustedStagedEleven(file)` omite el callback obligatorio `beforeCommit: () -> Unit` del puerto existente, definido sin valor predeterminado.
- Corrección mínima prevista, **misma unidad, misma ruta ya autorizada**: pasar `{ }` explícito en dicha llamada. No tocar puertos, migraciones, permisos de stock, grafo, host ni producción. Ningún XML JUnit se produjo; el paso de artefactos falló por ausencia de resultados (no son pruebas fallidas).
- El permiso original sigue `READY`, `B6=READY_TO_IMPLEMENT` y `B7=PENDING_FINAL_AUDIT`, para permitir la reparación exacta. Esta evidencia RED_FIX habilita exclusivamente el siguiente commit funcional de la misma unidad, nunca otra. Rehacer CI y validar XML auténticos del SHA corregido antes de declarar PASS.
- A1C2D B7 PASS y 24/24 previos no se repiten como implementación; V2 productiva, stock físico, pagos, Apply y Auto/Noche permanecen OFF; G15 `NOT_RUN`.
