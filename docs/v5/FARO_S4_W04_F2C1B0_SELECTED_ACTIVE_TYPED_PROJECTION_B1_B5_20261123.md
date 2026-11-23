# FARO V5 — S4 W04-F2C1B0 · Proyección seleccionada ACTIVE a contratos SmartCompra · B1–B5
Fecha: 2026-11-23, Argentina. Unidad `S4_W04_F2C1B0_SELECTED_ACTIVE_TYPED_PROJECTION_DEV`. Baseline exacto `911ad4953628785574af63e4f8f0ad03c93f622a`. Estado **B1–B5 documental; NO READY, NO B6** hasta guardia y permiso independiente. Writer inicial RELEASED epoch1680 y permiso F2C1A consumido BLOCKED.

## B1 — Evidencia y brecha binaria
- F2C0 code `ff881ffc41e5a2ca719589a49536cb24792ec694` / CI `45110499993` B7 PASS 6/6: `FaroV5Household10Evidence2DevSelectionReadPort.SelectedRead` trae UN `RealityHousehold10Row`, revisión global `householdRevision`, `ActiveGenerationPointer` exacto y `CommercialEvidenceStagedTypedPortabilitySnapshot` filtrado al producto bajo una única sesión/root lease. Nunca expone otras necesidades ni otros productos, preserva watermark global.
- F2C1A code `4e40dd64fd794ee23b79fc10ca5ebd4da206101b` y B7 checkpoint `911ad4953628785574af63e4f8f0ad03c93f622a`: Android DEV 4/4 real API35 `45116709185` / guard `45116709264` y regresión W04-E `45116709151` SUCCESS. Gate revocable y pantalla en blanco, SIN emisor real.
- F1 `S4W04HistoricalAuthorizedReadCoordinator` y D0–D4 ya existen y exigen `HouseholdNeedsSnapshot`, `CommercialNeedReadFence` y `CommercialEvidenceStagedTypedPortabilitySnapshot` CON grants del host. Todavía no existe traducción F2C0 `SelectedRead` -> esos contratos. No hay fuente tipada ACTIVE autorizada montada en Activity. **Objetivo de esta unidad**: solo convertir ese `SelectedRead` ya autorizado a UN snapshot Household tipado y Evidence2 seleccionado con fence de generación/epoch/principal; no emitir grants, carrito, selección de observación ni precios.
- Importante: F1 exige frescura en cada callback; una proyección puntual no es licencia para cachearla como autorización. F2C1B1 debe integrar reads repetidos, issuer real Android DEV y selector humano antes de emitir D4.

## B2 — V4.4, componentes reutilizados y delta mínimo
- V4.4 SmartCompra mostró selección humana y contexto de compra; es referencia UX, no fuente de autoridad para V5. Conservar Evidence!=Reality, dinero solo Finanzas.
- Reusar `HouseholdNeed`, `HouseholdNeedsSnapshot`, `HouseholdNeedId`, `CommercialNeedReadFence`, `CommercialEvidenceStagedTypedPortabilitySnapshot`, `ActiveGenerationPointer`, y el mismo port F2C0 (sin modificarlo). El mapeo de filas ya existe dentro de `RealityHouseholdNeedPersistence`; esta unidad reproduce solo conversión inmutable de fila de lectura, no un segundo owner.
- La entrada de F2C0 está validada por SQL F2B2C y por el port F2C0; el bridge comprueba de nuevo ID de necesidad/producto, tipo, estado OPEN, rango revisión y datos; copia lista (sin rehashear watermark). La pantalla E y gate A quedan intactos.

## B3 — Primarias externas y alternativas
- Android UI state holders: https://developer.android.com/topic/architecture/ui-layer/stateholders — ADAPTAR pipeline data->immutable UI state, no presentar raw row ni acoplar SQLite al renderer.
- SQLite isolation: https://www.sqlite.org/isolation.html — REFERENCIA a snapshots; NO crear otro opener/lease ni asumir atomicidad de fuentes distintas.
- Android Activity lifecycle: https://developer.android.com/guide/components/activities/activity-lifecycle — ADAPTAR invalidación del estado UI en pause, ya cubierta por F2C1A; no reabrir aquí.
- OWASP MASWE-0024: https://mas.owasp.org/MASWE-0024/ — ADAPTAR ausencia de cache/retención de sensibilidad al terminar sesión.
- Opción A leer directamente cinco bases desde Activity: **DESCARTAR**, rompe generación/lease.
- Opción B reconvertir snapshots comerciales STAGED sin el F2C0 opener: **DESCARTAR**, confunde fuente y ACL.
- Opción C *pure adapter* de SelectedRead F2C0 a contratos existentes, una necesidad/producto y recheck de callback: **ADAPTAR**. Sin nuevas bibliotecas, servicios o API paga.

## B4 — Solución y exactamente tres rutas para B6 futuro
`FARO-UNIT:S4_W04_F2C1B0_SELECTED_ACTIVE_TYPED_PROJECTION_DEV`.
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04SelectedActiveTypedReadProjection.kt` NUEVO: converter interno sin I/O. `project(selected,needId,productKey,principal,hostEpoch,admitted)` produce `Projected` inmutable (snapshot de UNA necesidad, fuente tipada solo un producto, fence ID+sequence+epoch+principal) o null. Fail closed para null, error, no admisión, ID ajeno, estado distinto de OPEN, revisión inconsistente, término legacy desconocido NO convertido, watermark extraño, objeto de otra generación, datos contaminantes u obligación de grant no cumplida. No persiste, no abre store, no selecciona observación, no emite permisos ni se monta en shipping.
2. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04SelectedActiveTypedReadProjectionTest.kt` NUEVO: 6 JVM JUnit5 dirigidos, datos sintéticos in-memory SOLO en pruebas: T01 exact SelectedRead/H10/E2 y fence; T02 mismatch necesidad/producto/contaminación; T03 estado inválido y revisión; T04 denegación inicial y revocación a mitad de lectura; T05 watermark intacto, términos UNKNOWN/null intactos, protección de alias; T06 principal/epoch/identidad inválidos. **No repetir** 6/6 F2C0 ni 4/4 F2C1A.
3. `.github/workflows/faro-v5-s4-w04-f2c1b0-selected-projection.yml` NUEVO: Java21 SDK37 JVM `:app:testDebugUnitTest` --tests T0*, gate READY exact3 previo y verificador XML 6 testcases; subir XML para B7 independiente. No emulador necesario en esta unidad pura.
F2C1B1 posterior habilitará emisor real DEV y la relación select->D0/F1->D4/E; F3 Android E2E posterior.

## B5 — Negativos, aceptación y STOP
- Entradas malformadas/revoked o callbacks que arrojen error => null. No reutilizar READ grants arbitrarios ni inferir permisos a partir de debug/UID, nombre, Intent o pruebas.
- Revisión global Household preservada, aunque solo se expone un elemento; nunca multiplicar fuentes, crear totales de ARS+USD, asignar UNKNOWN a vigente, elegir precio barato automático o suministrar un «monto actualizado».
- Antes de retornar, segundo `admitted()`; si cambia durante conversión, no proyectar. Copia inmutable de records, sin retener reference mutable ajena.
- CI real por exact code SHA, guard SUCCESS, 6/6 XML distintos 0 failed/errored/skipped, artifact independientemente verificado, B7 scoped final y writer RELEASED. Si falla, RED_FIX misma unidad.
- Prohibidos: `main`, V4.4, F2C0/F2C1A/E modificación, Apply, pagos, checkout, stock, red, G15 físico, Auto/Noche, V2 productiva. F2C1B global y F3 quedan OPEN.
