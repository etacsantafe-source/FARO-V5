# FARO V5 — W01 B3B1B1B0 · B7 PASS acotado DEV, doble observación de Finance9
Fecha AR: 2026-11-17. Commit funcional EXACTO `3216747249fec29f79ce6ba35b3ba0b6c793282f`; unidad `S4_W01_B3B1B1B0_FINANCE9_STAGED_READ_SESSION_FENCE`. Desarrollo STAGED9, no shipping.

## B1 — Evidencia vigente, sin repetir
Preauditoría `docs/v5/FARO_S4_W01_B3B1B1B0_CROSS_PROCESS_READ_FENCE_B1_B5_20261117.md`, blob `7e2364b16c1aaa7c83d852d26218671961440f24`, guard `44094780996` SUCCESS. B5 separado, permiso READY exact3 `docs/v5/FARO_S4_W01_B3B1B1B0_FENCE_INDEPENDENT_B5_READY_20261117.md`, blob `a57ad2f87ff89681f950a0162aa5328093ae6222`, guard `44095247004` SUCCESS, preflight `FARO_PROTOCOL_PREFLIGHT_PASS S4_W01_B3B1B1B0_FINANCE9_STAGED_READ_SESSION_FENCE 8be319210fc2d021d286bdb1f80936d0a543413c`. B3B1B0 y B3B1B1A previos B7 PASS y no repetidos.

## B2 — Diff contra READY
Código `3216747249fec29f79ce6ba35b3ba0b6c793282f`, marker `FARO-UNIT:S4_W01_B3B1B1B0_FINANCE9_STAGED_READ_SESSION_FENCE` confirmado, 3 rutas EXACTAS:
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B3B1B1B0ObservedReadFence.kt` — componente internal, DEV, no port de UI.
2. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B3B1B1B0ObservedReadFenceTest.kt` — 8 tests JVM.
3. `.github/workflows/faro-v5-s4-w01-b3b1b1b0-fence-directed.yml` — pruebas y compilación DEV.
Sin cambios en RealityStore, schema7/8/9, políticas o issuer, FBS2v4, Apply, receipts, Activity, Compose, manifest, Shell, CompositionRoot, main, V4.4, S2/S3 ni W2. Writer RELEASED epoch1088 antes de CI.

## B3 — Qué sí queda implementado
`observedForDevOnly()` examina full pointer sequence+generation y host epoch+readiness, compara principal sintético estable, ejecuta DOS lecturas completas del port compuesto B3B1B0, toma tiempo fresco en ambas y exige igualdad exacta de policy epoch, global revision, source revision y cuentas. Ante null/exception/change/ABA/close, retorna null, invalida definitivamente la instancia y no persiste cache. El valor devuelto es SOLO `RealityFinance9ObservedSourceRead` informativo, NO permiso real, UI model ni derecho a ver saldos.

## B4 — Pruebas exactas en GitHub
Run dirigido `44095814499`, job `134082521967`, COMPLETED SUCCESS en el código `3216747249fec29f79ce6ba35b3ba0b6c793282f`. `:app:testDebugUnitTest` SUCCESS y `:app:compileV2CoreDevKotlin` SUCCESS. Verificación de XML nominal F01–F08: **8/8 tests**, 0 failures, 0 errors, 0 skipped, log exacto `W01_B3B1B1B0_INERT_READ_FENCE_8_OF_8`. Artefacto `faro-v5-w01-b3b1b1b0-fence-junit` id `14332869999`, SHA coincidente.
Las 5 acciones del mismo SHA todas COMPLETED SUCCESS:
- Guardia obligatoria `44095814073`.
- S3 Home regresión `44095814189`.
- M5 APK DEV aislada `44095814120`.
- V2 Core M1/M2 `44095814419`.
- Fence dirigido `44095814499`.
No reejecuciones manuales ni resultados fabricados. No prueba física del Moto G15 en esta unidad.

## B5 — Límites reales y pendientes
Dos observaciones no dan validez absoluta después de la segunda SELECT; un otro proceso puede revocar luego y antes de que una UI pinte. Callback de principalHash y expectedEpoch son forjables en DEV, las pruebas usan fuentes sintéticas: no certifican AndroidKeyStore real, UID, aprobación humana, consentimiento lector ni UI. No se creó una fuente interproceso confiable de invalidaciones ni un `finance.snapshot.read` issuer firmado, y no existe binding del `FaroV5FinanceReadPort` productivo. FileObserver y flujo inyectable no bastan para autorizar. La próxima unidad B3B1B1B1 requiere autoridad Android real + revocación/lifecycle, su propia B1–B5 y READY. B3B2/G15 después; W1 B4/C/D/E/F OPEN, W2 STOP, V2 productiva OFF, Auto/Noche OFF.
## B6
IMPLEMENTED `3216747249fec29f79ce6ba35b3ba0b6c793282f`.
## B7
**PASS_SCOPED_DEV_DOUBLE_OBSERVED_READ_FENCE_8_OF_8_AND_5_OF_5_CI**. Consumir permiso a BLOCKED, checkpoint y liberar writer, confirmar guard documental antes de dar cierre final.
