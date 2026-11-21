# FARO V5 — W04-D1 · Cobertura de cotizaciones observadas del carrito — B1–B5

Fecha local 2026-11-20. Baseline W04-D0 B7 `32934177735ac3557195a67815452dc6a48284bd`. Unidad futura `S4_W04_D1_CART_OBSERVATION_COVERAGE_DEV`. **B1–B5 PREAUDIT DOC_ONLY, no B6 ni READY hasta guard y permiso específico.**

## B1 — Evidencia de código (no repetir W04-D0 ni W04-C)
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialCartDraft.kt` blob `eb5b528bf87311e053751f822b0cfe50466a30ef`: carrito efímero inmutable de 1..200 líneas, `Line(needId,needRevision,needQuantityThousandths,needUnit,productKey,requestedPackCount)`, `fingerprint`, fence y `revalidate(draft,currentSnapshot,currentFence,isOwnerAdmitted)`. D0 B7 `docs/v5/FARO_S4_W04_D0_CART_DRAFT_B7_PASS_20261120.md`, código `7ba9c5c97dda3b8e228680954b997d9bc5d46c05`, CI `44853703822` 12/12, guard `44853703753` SUCCESS. El número de packs no da derecho a convertir need quantity a presentación.
- `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/EvidencePortabilityPort.kt` blob `16cf4709ee533fb5cfedd08c4e44942b3b41f7ef`: `CommercialEvidenceStagedTypedPortabilitySnapshot(2,sourceWatermark,records)`, H1 STAGED only, null terms distinto de UNKNOWN. Cap 100000. El puerto no incluye permisos de usuario, precios vigentes ni ejecución de URL.
- `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialTypedPriceComparison.kt` blob `904e072b12d92e29a79056bc9b95881110fe2032`: `CommercialTypedPriceComparison.project(snapshot,productKey,asOf,maxAge,limit)` verifica watermark H1, orden canónico y fuente; observa rangos de locality/currency/presentation/pack/payment/eligibility/promo/vigencia; retorna `history`, `groups`, `coverage`, `truncated`, `notice=OBSERVED_HISTORY_ONLY_NOT_CURRENT_QUOTE_OR_STOCK`. C2 B7 CI `44850685841` 14/14.
- `S4W04CommercialCartDraft.revalidate` y `S4W04CommercialNeedProductSelection.revalidate` C0A ya aseguran need OPEN/revisión/fence bajo grants de Household. NO reemplazar estos guards. En C3, Evidence2 STAGED es append-only y no activa API productive.
- `v5/app/build.gradle.kts` blob `4dfa72fe4850f603bf8ec7ce58305a43f4997790` ya depende de `:evidence` y `:owner:household` transitivamente, JUnit5; no agregar dependencia. FALTANTE: no existe read-model carrito×quotes/Coverage en la app.

## B2 — V4.4 y regresión de intención
En P02 V4.4 L11/L14/L15 se presentan necesidad, precio, carrito y estimación; V5 no puede ascender un precio observado a garantía actual ni afirmar que 2 packs cubren 1000g si presentation del comercio no cuadra. D1 **no calcula presupuesto**: registra cobertura y muestra qué dato falta. Un comprador puede ver ARS/USD en cotizaciones aisladas, nunca total cruzado. `sourceRef` de observación es texto opaco no URL ejecutable. D1 no genera Apply, recibo, egress, permisos, SQL, stock, gasto, saldo ni venta.

## B3 — Alternativas
ELEGIDA: nueva proyección read-only en `:app` reutilizando **exactamente** `CommercialTypedPriceComparison.project` por `productKey` de cada línea, manteniendo fuente H1 original, cutoff asOf explícito y `sourceWatermark`. ADAPTAR: los grupos tipados C2, sin inferir presentación ni discount, con `coverage` distinto de `quote confirmed`. DESCARTADAS: consultar red/autorefrescar, copiar histórico a tabla carrito, recalcular promociones, cambiar factura o futuro presupuesto D2, usar `acquiredAt` como observedAt, mezclar moneda o usar `lowestObserved` como oferta disponible. REVISION: grants Household + Commercial read callbacks independientes, relectura del snapshot/fence por host antes de divulgar resultados; D1 no puede otorgar grants ni abrir otro store.

## B4 — Contrato exacto
Unidad D1 **STAGED DEV read-only**, archivo nuevo `S4W04CommercialCartCoverage.kt`:
1. `project(draft,currentHouseholdSnapshot,currentFence,evidenceSnapshot,asOfEpochMillis,maxAgeMillis,householdAdmission,commercialReadAdmission)` → `Observed` o `Rejected`. Los dos grants se revisan antes de cada producto, durante la lectura y otra vez antes de publicar; `S4W04CommercialCartDraft.revalidate` obligatorio al inicio y final. No host nuevo ni confiar en JWT/productKey como capacidad.
2. D1 rechaza enteramente si `currentFence` stale, snapshot nulo, usuario sin ambos grants, `asOf` inválido, `maxAge` inválido, watermark corrupto, o cambio de evidencia detectado al releer un snapshot suministrado por callback `currentCommercialSnapshot` luego de proyectar. Para scope mínimo se admite H1 STAGED in-memory con snapshots reobtenidos desde caller confiable, NOACTIVE.
3. Por línea devolver `LineCoverage(needId,productKey,requestedPackCount,observedRecordsCount,groups,coverageStatus,truncated)`; estados `NO_DATA`, `HISTORICAL_PARTIAL`, `COMPARABLE_OBSERVED_ONLY`, **ninguno `CONFIRMED`**, nunca total/pago/stock. Valor de quote `observedMinor` sólo en `CommercialObservedHistoryEntry` con moneda+merchant+conditions completos; no `confirmedQuote`.
4. Rechazar si el Evidence STAGED cambia antes de publicar (watermark mismatch entre before/after), si un grant revoca o si D0 queda stale. Exigir mismo snapshot2 y ningún rebase del carrito. Guardar en proyección sólo `cartFingerprint`, `sourceWatermark`, `asOf`, `lines` inmutables y advertencia global `OBSERVED_ONLY_NOT_A_CURRENT_QUOTE_OR_BUDGET`.
5. El orden de salida sigue `draft.lines`; nunca suma precios, mezcla ARS/USD, multiplica packs, valida disponibilidad ni realiza conversiones. Máx 200 ítems/cart. Limitar C2 cada producto a 200 registros y reportar truncamiento; si hay más de 200, no prometer mínimo.
6. JUnit5 dirigido 12 tests en app con H1 snapshot sintético tomado preferentemente de STAGED API para hashes correctos; si fixture synthetic, usar `EvidencePortabilityProjection` vía owner existente sin abrir red ni user data. Probar invalid sourceWatermark. Workflow copia patrón C0A Java21, API37, `:app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04CommercialCartCoverageTest`, XML exacto `(12,0,0,0)`, artefacto, preflight EXACT3 y `FARO-UNIT`.

**RUTAS EXACTAS propuestas para D1 (no READY hasta guard):**
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialCartCoverage.kt` (nueva)
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04CommercialCartCoverageTest.kt` (nueva)
- `.github/workflows/faro-v5-s4-w04-d1-cart-coverage.yml` (nueva).

## B5 — Matriz adversarial
T01 sin cart, permisos Household y Commercial independientes; uno false ⇒ NOT_AUTHORIZED y ningún precio publicado.
T02 draft stale (needCompleted/revision/ABA/epoch/fence) ⇒ rechazo total.
T03 H1 vacío, producto sin historial ⇒ NO_DATA, sin precio inventado.
T04 observación null observedAt / acquiredAt reciente no se interpreta como cotización actual.
T05 ARS/USD, localidad, pack/qty/unit, payment distinto se conservan separados en grupos C2; no total.
T06 validez UNKNOWN/expirada, stale/futuro/issued inconsistente, promoción condicional => HISTORICAL_PARTIAL.
T07 dos comercios condiciones comparables ⇒ COMPARABLE_OBSERVED_ONLY con notice obligatorio, no CONFIRMED.
T08 snapshot H1 watermark corrupto, términos modificados o sourceRef alterado => REJECTED_SOURCE_UNVERIFIED.
T09 cambiar H1 entre preview y publicación => REJECTED_SOURCE_CHANGED, sin publicación parcial.
T10 revocación tardía de grants/revisión de need entre pre/post ⇒ rechazo completo.
T11 cart 2 lines, sólo una con observaciones => salida completa con coverage individual; jamás agrupar por nombre de necesidad.
T12 snapshot/lectura no escriben ni ejecutan fuente hostil, no hay SQL nuevo, receipts, gasto, stock ni conversión de packs.
Todas las pruebas D1 **NOT_RUN** hasta CI. Android/G15 NOT_RUN.

## Gate
`B1_B5_PREAUDIT_DOC_ONLY`. Revalidar HEAD/lock/permit/última CI D0, commit DOC_ONLY con B1–B5 específicos, guard SUCCESS por SHA, luego READY exact3 commit aparte y guard/preflight SUCCESS; sólo después B6. D2, D3, D4 NO READY. V2 prod OFF.
