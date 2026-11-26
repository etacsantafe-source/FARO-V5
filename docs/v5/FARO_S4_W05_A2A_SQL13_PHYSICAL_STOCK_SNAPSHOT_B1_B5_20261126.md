# FARO V5 · W05-A2A — Stock físico neto SQL13, lectura DEV · B1–B5

**Unidad:** `S4_W05_A2A_SQL13_PHYSICAL_STOCK_SNAPSHOT_DEV`. **Fecha:** 2026-11-26. **BASELINE** `2315c09b2cc6aad42490900f79dad77453afbf18`. **A1 GLOBAL DEV B7 PASS:** [CI 45743934153](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45743934153) (JVM+Android API35, 2/2 XML originales); [guardia final 45745241899](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45745241899) SUCCESS. Writer observado RELEASED época 2055; adquisición CAS para este documento, sin reutilizar permiso consumido de A1C4.

**ALCANCE EXACTO:** nueva proyección estrictamente READ_ONLY de cantidad física neta respaldada por `RealityHousehold13CompensationRows`, invocando el host SQL13 ACTIVE DEV ya certificado y su `readHistory()` bajo lease, full-pointer, owner admission y gate. **No implementar aún reservas/consumos ni presentar disponible numérico.** No modificar tablas, catálogo, kernel, host certificado o Android existente. A2A usa el único origen SQL13 real que dejó A1; no simula un libro de reservas que todavía no existe.

## B1 — Evidencia comprobada y brecha binaria

- P05 `S04/S05`, `PF21–PF25` y P08 W05 exigen separar compra, pago, recepción, stock físico, reservado, disponible, consumido y Cocina; P07 fija Household como dueño distinto de Finance.
- Catálogo `v5/spec/generated/contract-baseline.tsv` blob `5653a9758f682f683a852dcf286211bae71c41d0`: `stock.snapshot.read` READ_ONLY, `stock.reserve`/`stock.release`/`stock.consume` REALITY_MUTATION, owner household, N-035. Que estén catalogadas NO implica grants/bindings, implementación ni permiso Apply.
- Código vivo A1C4: `FaroV5Household13ActiveDevReadHost.readHistory()` devuelve datos activos autenticados; `RealityHousehold13CompensationRows` contiene `historical.purchases`, `historical.receipts` y `compensations`. Los recibos y compensaciones tienen IDs/revisiones, unidades y purchaseRef; los receipts se vinculan al stockItemRef de compras. `RealityHousehold13CompensationRowsPort.validate()` verifica referencia y cronología de compensación.
- A1C4 B7 acredita compra 10, recepción 7+3, rollback/recuperación, Undo 7, neto 3; **NO acredita consumos ni reservas**. SQL13 no contiene tablas de reserva/consumo. Por eso no se puede afirmar `available = physical`.
- Estado de partida: A1 PASS solo DEV; G15 físico NOT_RUN, V2 productiva/stock productivo/pagos/Auto/Noche OFF. No editar cierres anteriores.

## B2 — Reutilizar V4.4 sin copiarlo

P02 legado `L11–L13` distinguía comprar, recibir, reservar, usar y Cocina; P04 (Grocy/ KitchenOwl) documentó la UX de stock/cocina. Se retiene la separación conceptual de V4.4, **no** código, store, network stack, tablas, servicios ni pruebas legadas. Para A2A se reutilizan los tipos SQL13 y el host DEV existente, su autorización/grant/fence, más los modelos `HouseholdNeedUnit` con seis unidades tipadas. Poner "desconocido" para reservado, consumido y disponible, y no inventar 0. No confundir `pending` de compra con stock disponible.

## B3 — Alternativas revisadas y decisión

- **USAR:** proyección pura derivada del snapshot SQL13 tomado por host autenticado, sumando recibos de compra confirmada y restando compensaciones del mismo SKU y unidad; sin nuevas tablas ni drivers. Es offline y evita nuevo writer/owner.
- **DESCARTAR:** tratar `purchased`, `pending` o gross received como disponible; ignora Undo/reservas/consumos y crea falsos positivos. También descartar convertir KILOGRAM↔GRAM sin regla de conversión aprobada y el stock de Evidence/precio como realidad.
- **NO APLICABLE AHORA:** event sourcing persistente de reservas y consumo (necesitará unidades A2B/A2C con esquema/versionado/Apply). Considerar modelos de Grocy `B03` y KitchenOwl `B04` solo como patrón de interacción, sin importar PHP/backend/AGPL ni APIs pagas.
- **Hipótesis falsable A2A:** para una misma SKU+UNIT, receipts 7+3, Undo 7, `physicalNet=3`; si existen compras en otra unidad conservar series separadas, no combinar; `reserved/consumed/available=UNKNOWN`.

## B4 — Diseño y exactamente tres rutas B6

Contrato DEV: `FaroV5Household13DevStockSnapshot.read(host)` retorna `null` si `host.readHistory()` deniega, puntero ABA cambia, lease no es válido o la fuente no puede validarse. Devuelve registros agrupados por `(stockItemRef, unit)`, orden determinista, `physicalNetThousandths` no negativo Long, `reservedThousandths=null`, `consumedThousandths=null`, `availableThousandths=null`, fuente `SQL13_CONFIRMED_RECEIPTS_LESS_UNDO`. Revisiones monotónicas, filas de compra provienen de `MANUAL_EXPLICIT`. Cero sólo cuando la fuente certificada está realmente vacía para ese SKU; preferir omitir SKU sin receipts en lugar de inventar inventario inicial. Errores/inconsistencia deniegan toda la vista.

**Rutas funcionales B6 exactamente:**
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household13DevStockSnapshot.kt` — proyector interno read-only + derivación tras host.
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household13DevStockSnapshotTest.kt` — pruebas de datos SQL13 reales y negativos (ver B5), sin reejecutar A1.
3. `.github/workflows/faro-v5-s4-w05-a2a-physical-stock-snapshot-dev.yml` — CI dirigida con JUnit XML original, validación estricta y artefacto.

No modificar `FaroV5Household13ActiveDevReadHost.kt` ni RealityStore: se utiliza `readHistory()` público interno existente. No tocar `v5/app`, UI, catálogo o permisos; si el diseño exige cuarta ruta, STOP, nueva revisión B1–B5 y permiso independiente.

**Secuencia B6/B7:** primero guardar este B1–B5 y guardia SUCCESS; después permitir A2A **READY exact3**, en commit documental separado con evidencia blob SHA. B6 cambio funcional separado con `FARO-UNIT:S4_W05_A2A_SQL13_PHYSICAL_STOCK_SNAPSHOT_DEV`; writer CAS; CI+XML original; B7 PASS/RED_FIX documentado, permiso consumido y writer RELEASED.

## B5 — Matriz de pruebas negativas y aceptación

| ID | Caso | Esperado |
|---|---|---|
| T01 | SQL13 real: compra 10, receipt 7+3, compensation 7 | physicalNet=3 UNIT, sin doble conteo; reservas/consumo/disponible UNKNOWN |
| T02 | Dos compras de misma SKU+unidad; compensación parcial de una | Neto suma sólo receipts reales y resta compensación una vez por receiptId; sin mezclar purchase pending |
| T03 | Un SKU con UNIT y otro con GRAM; otra compra con igual nombre diferente unidad | Series independientes, ninguna conversión silenciosa |
| T04 | Reversión de recepción completa | 0 físico solo si evidencia válida, sin inventar stock negativo |
| T05 | Grant revocado durante host y pointer full ABA/lease cerrado | `null`/STOP, jamás snapshot viejo visible; no abrir DB sin host |
| T06 | SQL12 o Evidence1 o SQLite corrupta; referencias de receipt sin compra confirmada | Denegación; no fallback a mock, no resolver por prefijo |
| T07 | Replay/reopen SQL13 con misma generación | Proyección igual y determinista; sin writes, tablas nuevas, recibos, Apply o cambios de puntero |
| T08 | Valores extremos, sum overflow Long o compensación de receipt no coincidente, inconsistencias | Rechazo íntegro; no mostrar positivo falso |
| T09 | Sin inventario recibido y compras todavía pendientes | No inventar existencias físicas ni stock disponible |
| T10 | Incompatibilidad de unidades/identidades/stock provenance | STOP, no cross-unit aggregation, no leak a otro principal |

CI debe invocar las pruebas A2A por selector exacto, guardar XML original y verificar `tests>0`, `failures=errors=skipped=0`. No compilar Android si solo se modifica proyector JVM; A1C4 Android ya B7 PASS, no repetir. Guardia protocolo SUCCESS en SHA exacto. **A2A B7 no es A2 completo; A2B/C y aceptación final quedan pendientes.**

## Estado al publicar B1–B5

B1–B5 = DOCUMENTADO / sujeto a guard documental y revisión READY exact3. **B6 NOT_RUN; B7 NOT_RUN.** Writer liberar al acabar checkpoint; no habilita automáticamente operaciones mutantes ni stock disponible.
