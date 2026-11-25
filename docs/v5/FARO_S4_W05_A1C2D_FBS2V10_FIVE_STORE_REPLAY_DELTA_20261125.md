# FARO V5 · W05-A1C2D · Replay autenticado FBS2v10 de cinco almacenes en SQL13 STAGED

## B1 — Delta de evidencia

Reusa la preauditoría FARO_S4_W05_A1C2_STOCK_UNDO_RECOVERY_B1_B5_PREAUDIT_20261125.md. A1C2A y A1C2B certificaron el esquema y el codec autenticado. A1C2C tiene B7 PASS en 3dd0f1e26f0d4b929bb43a954dbaf4f58b03578a, CI 45612551164, guardia 45612551079 y XML 6/6. Componer ambos con el replay base de cinco owners. El formato v10 no modifica SQL12/v9 y conserva los recibos y las compensaciones.

## B2 — Reutilización

Reusar FAROBAK2, el replay tipado del v8 y su validación de cinco stores, archivo de identidad original, pin de puntero completo, apertura sellada por perfil, staging SQL9→10→11→12→13, retención de Finance9 y needs. Mantener referencias históricas P02/P03 y documentación de SQLite transacciones/WAL citadas en unidades anteriores.

## B3 — Delta de diseño

Abrir perfil independiente SQL13 STAGED de solo lectura con verificación exacta de los cinco archivos y su realidad histórica. Abrir v10 autenticado como tipo de replay antes de cualquier escritura, fijar el ciphertext original y reusar el restore base bajo una vista quiesced. Migrar según versión verificada, conservar compras/recibos/compensaciones en una transacción de Reality, validar los cinco owners antes y después. Importar un v9 autenticado explícitamente como SQL13 con compensaciones vacías. Readback compara el snapshot completo, incluida Evidence2 y base histórica. Retener el pin y fences tras errores y reintentos. Puntero ACTIVE, autorizaciones y Undo quedan para unidades futuras.

## B4 — Rutas exactas

1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household13V10StagedReplay.kt`
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11V8BaseReplay.kt`
3. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
4. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
5. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9SealedReadPort.kt`
6. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold11NeedsRowsPort.kt`
7. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household13V10StagedReplayTest.kt`
8. `.github/workflows/faro-v5-s4-w05-a1c2d-fbs2v10-five-store-replay.yml`

## B5 — Aceptación

Ronda de backup autenticado y stage separado que conserva los cinco owners, historial, compras, recibos y compensaciones. Restore v9 explícito deja cero compensaciones; evidencia legacy y productos previos preservados. Rechazar otro ciphertext o versión, corrupción, puntero ABA, aliases, sidecars, target ACTIVE o divergente. Interrupción antes del commit mantiene la recuperación y reintento exacto. CI de alcance, XML original y guardia por código.
