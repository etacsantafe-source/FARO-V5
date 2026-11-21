# FARO V5 — W04-D0 · Carrito comercial efímero de necesidades vinculadas — B1–B5

Fecha local 2026-11-20; baseline `171940caa83aa11ccb0e2e792a5b1f28cacacdfc`, última guardia `44852615787` SUCCESS, writer observado RELEASED epoch 1485. Unidad `S4_W04_D0_CART_DRAFT_DEV`. **DOC_ONLY**; NO implementar B6 hasta guardia del presente documento y permiso READY exact3 posterior. No repetir W1/W2/W3, W04-A/B0/B1/C certificados B7.

## B1 — Evidencia fuente y brecha puntual
- `v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdNeedModel.kt` blob `224b4aac04238b2241adb046161761c931389bdc`: `HouseholdNeed` incluye `id`, `quantity` con milésimos/unidad, estado OPEN/COMPLETED y `revision`. `HouseholdNeedPersistencePort.kt` blob `f48e6528ffcdf4986274dc5111ca0b8a10255c00`: `HouseholdNeedsSnapshot(revision,needs)`; no hay carrito persistente en este contrato.
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialNeedProductSelection.kt` blob `b2f5c63ea25d01ef092606a588c1df74fca6b047` (C0A B7 PASS) ofrece `CommercialNeedProductCandidate(need,householdRevision,productKey,readFence)` creado solo tras elección humana y método `revalidate(candidate,currentSnapshot,currentFence,isOwnerAdmitted)`. La comparación por nombre no está autorizada; `readFence` tiene generación/pointer/epoch/principal. No fabricar un candidato saltándose C0A.
- `CommercialProductKey` pertenece a Evidence. `v5/app/build.gradle.kts` blob `4dfa72fe4850f603bf8ec7ce58305a43f4997790`: dependencias presentes `:evidence`, `:store:generation`, `:kernel:contracts`; tests app JUnit5. No agregar módulos/dependencias.
- `FARO_S4_W04_C_SCOPED_DEV_B7_REVIEW_20261120.md` blob `f2df934db19d04c334fe757e6deddf5a7fb8fe2c`: núcleo W04-C B7 DEV, no host Evidence2 ACTIVE ni cotización confirmada. D0 no debe consultar, copiar o inferir precios.
- GAP: no existe `S4W04CommercialCartDraft.kt` ni su test ni workflow. La preauditoría D general `FARO_S4_W04_D_CART_BUDGET_PREAUDIT_20261120.md` blob `c85659bdee978142dd60b98435c30cb6ab85fcfe` está DESIGN_ONLY.

## B2 — Reutilización y contraste con V4.4
P02 L11/L14/L15 `FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md` blob `9a6388ae6be298eb6536a3dc81e1266bf4d42385`: V4.4 incluía necesidades y carrito comercial como flujo UX. V5 mantiene elección explícita y source-of-truth Household. Una necesidad de 1000 GRAM no equivale silenciosamente a dos paquetes comerciales de 500g: unidad de necesidad y cantidad de packs seleccionada son datos distintos. Tampoco elegir un producto afirma vendedor, moneda, stock ni precio. Reutilizar C0A para controlar OPEN/revisión/generación, no copiar gateways ni SQLite V4.4.

## B3 — Alternativas, decisión y costo
- **USAR** `CommercialNeedProductCandidate` + `S4W04CommercialNeedProductSelection.revalidate` (C0A certificado) y `HouseholdNeedsSnapshot`; genera borrador efímero e inmutable. Elección ganadora: tres rutas puras app+test+CI, sin SQL, red, permisos nuevos ni Finance. JVM rápido y aislado.
- **ADAPTAR** fingerprint `CanonicalHashBuilder` de kernel (mismo patrón C0A/C1a), dominio exclusivo `w04.d0.cart-draft.v1` con snapshotRevision, fence y campos tipados/ordenados; no autoriza Apply ni se usa como permiso.
- **REFERENCIA** P02 UX carrito; D1/D2 futuros gestionan cobertura, moneda, local, promo y presupuesto. No copiar lib/stack externo.
- **DESCARTAR** checkout/product catalog automático, fuzzy need/product, SQL de carrito duplicado, builder de precios con FX, wallet/Finance, ventas, stock o pagos, escribir sourceRef, activar V2 productiva, APIs pagas.

## B4 — Contrato exacto y fronteras
Crear una sola unidad pure read-only `S4W04CommercialCartDraft` en app, sin I/O y sin mutation:
1. Entrada al builder: snapshot Household admitido **no nulo**, readFence confiable, lista no vacía de `CommercialNeedProductCandidate` y número entero POSITIVO de packs **elegido explícitamente** por línea, callback de admisión Household. El llamado debe revalidar **cada** candidate por `S4W04CommercialNeedProductSelection.revalidate`. El conjunto de líneas comparte la misma revision global/fence y no admite el mismo `needId` duplicado; no inferir una segunda opción.
2. Salida: draft con copias inmutables de `needId`, `needRevision`, `needName`, `needQuantityThousandths`, `needUnit`, `productKey`, `requestedPackCount`, revision global, generation/host fence y fingerprint determinístico. Rango: 1–200 líneas; packCount 1–10000 cada una; total calculado como cantidad de entradas, NO presupuesto. Orden canonical por NeedId y ProductKey; fingerprint incluye todos los valores y delimitación; no incluye datos personales/evidence/price.
3. Denegación tipada: `NOT_AUTHORIZED`, `STALE_CANDIDATE`, `INVALID_PACK_COUNT`, `EMPTY_OR_TOO_MANY_LINES`, `DUPLICATE_NEED`, `INCONSISTENT_READ_FENCE`. No draft parcial si alguna línea falla; reject antes de construir fingerprint. No persistencia/reload/receipt ni `ApplyPlan`; sin cap registry nuevos.
4. `revalidate(draft,currentSnapshot,currentFence,grant)` contra todos los candidatos originales antes de usarlo más adelante. Si se modifica la necesidad, el estado, su unidad, cantidad, revisión, el snapshot/revision, el fence o el grant, invalida TODO el draft; no rebase automático. No permite producir orden de compra desde el borrador.
5. Workflow dirigido exact3 `:app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04CommercialCartDraftTest`, Java21, Android platform37 config ya usada en C0A, JUnit XML con test count 12, fail/error/skip 0 y artefacto. Reutilizar patrón C0A sin tocar su workflow ni código.

**RUTAS EXACTAS propuestas (todavía NO READY):**
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialCartDraft.kt` (nueva)
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04CommercialCartDraftTest.kt` (nueva)
- `.github/workflows/faro-v5-s4-w04-d0-cart-draft.yml` (nueva)

No modificar código histórico ni `main`; no añadir dependencia ni migrar datos. C1B2/C2/C3 no se reabren.

## B5 — Matriz adversarial binaria (NOT_RUN)
1. T01 sin grant, snapshot null o readFence null: NOT_AUTHORIZED, draft ausente.
2. T02 candidate de need no existente/completada o borrada: STALE_CANDIDATE.
3. T03 cambia revisión global, nombre, quantity/unit, needRevision o dueDate: draft no reusable.
4. T04 pointer ABA/hostEpoch/generation/principal cambian: INCONSISTENT_READ_FENCE o STALE_CANDIDATE, sin líneas publicadas.
5. T05 selección explícita de C0A válida pero no hay precio/evidence: D0 crea únicamente draft sin precio/stock/conversión.
6. T06 packCount 0, negativo, >10000: INVALID_PACK_COUNT y cero mutación.
7. T07 dos líneas para mismo needId (aunque productKey diferente): DUPLICATE_NEED, no merge silencioso.
8. T08 mezcla de candidatos de dos generaciones/epochs/principals: rechazo de draft completo.
9. T09 orden de entrada distinto con mismas líneas: fingerprint y líneas canonical idénticos.
10. T10 lista 0 o 201 líneas => rechazo, cap 200; no overflow de pack.
11. T11 snapshot y pack original independientes de una mutación posterior de las listas de entrada; la salida no es un permiso de ejecución ni pago.
12. T12 fuente/prodKey opaco no ejecuta URI, y `revalidate` tras revocación falla sin SQL/recibo/Finance/stock.
13. B7 DEV comprobar XML real exacto 12/12, guard código SUCCESS, writer RELEASED. Emulador Android / Moto G15 quedan NOT_RUN.

## Gate de autorización
**B1_B5_PREAUDITED_DOC_ONLY**. Tras publicar este documento y verificar SHA+guard documental SUCCESS: emitir permiso READY EXCLUSIVO a esta unidad y a las tres rutas literales en commit de solo documentación; emitir guard READY preflight y sólo entonces delegar B6/B7 a Work. No autorizar D1/D2/D3/D4, ACTIVE Evidence2, pagos, Finance ni stock. Si falla CI, RED_FIX exclusivo y documentado (no repetir C).
