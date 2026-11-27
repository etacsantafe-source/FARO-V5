# FARO V5 — W05-A2D2 · B1–B5 para Work Turbo (27/11/2026)

**Unidad:** `S4_W05_A2D2_GLOBAL_STOCK_READ_ACCEPTANCE_DEV`. **Objetivo:** Aceptación integral A2D DEV y enlace a A2E.
**Base fuente:** `29216d6d403e9d5e590551a92f0b68073350118e`. **Dependencia:** A2D1B B7 PASS.
**Estado al publicar:** preauditoría B1–B5 documental para guardia; **NO código, NO CI nuevas, NO B7**, permisos por unidad pendientes salvo D0A si READY independiente posterior. Los paths son scope PREVISTO; no son privilegio de escritura por sí mismos.

## B1 — Evidencia real, hueco exacto y aceptación

Repo etacsantafe-source/FARO-V5, rama work/s3-night-20261102, HEAD inicial `29216d6d403e9d5e590551a92f0b68073350118e`. A2C CLOSED DEV por B7 XML 91/91 y APK aislada; código `b064960b4443f56776781744bc30279648da01cb`, B7 checkpoint `29216d6d403e9d5e590551a92f0b68073350118e`, guardia final 46046431091 SUCCESS. A2A físico SQL13 PASS; A2B reserva SQL14 PASS; A2C almacenamiento/consumo/Undo SQL15 PASS. `docs/v5/FARO_S4_W05_A2_STOCK_RESERVATIONS_CONSUMPTION_ROADMAP_20261126.md` exige A2D lectura integral/UX ANTES de A2E. El permiso heredado de A2C es BLOCKED y no autoriza ninguna fuente A2D. Writer global CAS, AGENTS.md y protocolo B1–B7 prevalecen.

Evidencia de archivos reales inspeccionados en `29216d6d403e9d5e590551a92f0b68073350118e`: `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt` (blob 72a5dc9853e417b4a18886590f531c095936d278) ofrece `inspectActiveHousehold15DevReadOnly` sobre SQL15, cinco stores, pointer, guard/lease y callback de selección; `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15ConsumptionRowsPort.kt` (4af7a385640977f6e984d5a49878926b7ad60287) define `RealityHousehold15ConsumptionRows`, historia SQL14/13/12/11, eventos CONSUME/CONSUME_UNDO, orden y restricciones. `FaroV5Household13DevStockSnapshot.kt` (03cd385c5804e5c89195bf9da8ce299a0515377a) ya calcula físico SQL13 con reservado/consumido/disponible UNKNOWN, NO se debe modificar ni atribuirle disponibilidad. `FaroV5Household14ActiveDevReadHost.kt` (88ac609f112b459427abc1b19cb89c4e48ee03c7) solo cubre SQL14, no sustituye lectura SQL15.

A2D0A/B y A2D1A/B estarán B7 solo tras Work; aun así no prueban en mismo SHA los casos integrados compra10/recepción7/reserva/consumo/Undo, readback SQL15 REALITY, restauración cinco stores, presentación SmartCompra y APK. A2C global previo 91/91 PASS solo certifica ledger/Apply sin UX de stock. A2D2 no existe y debe ser aceptación global nueva; A2E inventario completo es posterior.

## B2 — Comparación V4.4, V5 y reutilización

Reusar XML original/fixtures certificados A2A/A2B/A2C por SHA y nuevas pruebas A2D; no reescribir cálculo ni el host. Baseline obligado `docs/v5/FARO_S4_W05_A2C_GLOBAL_DEV_ACCEPTANCE_B7_PASS_XML91_APK_20261127.md` y plan A2; jamás contabilizar suma de runs antiguos como una sola aceptación.

Conforme a P03 `FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`, P05 S04, P06 UX26, P07 decisión Household y a `FARO_S4_P08_W00_W11_B1_B5_20261115.md`. Se reutilizan patrones/conceptos, nunca runtime V4.4 ni FBS2/SQL15 reimplementados.

## B3 — Investigación externa, licencia, alternativas y fallos

Fuentes externas revisadas al preparar el paquete:
- SQLite snapshot isolation WAL: https://www.sqlite.org/isolation.html y https://www.sqlite.org/wal.html (USAR concepto de snapshot coherente, no código); evita leer fuente ACTIVE con `immutable=1` y evita mezclar generaciones durante un render.
- Android offline-first/lifecycle: https://developer.android.com/topic/architecture/data-layer/offline-first y https://developer.android.com/topic/libraries/architecture/lifecycle (ADAPTAR al local owner ya existente; no introducir red, sync, WorkManager ni servicio).
- Compose semántica/escala: https://developer.android.com/develop/ui/compose/accessibility/semantics y https://developer.android.com/develop/ui/compose/accessibility/scalable-content (ADAPTAR títulos, semántica explícita y fontScale 200%; no depender solo del color).
- Android adaptive window sizes: https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes (REFERENCIA, solo si librería existente; no añadir dependencia por inercia).
- Grocy, https://github.com/grocy/grocy (MIT; REFERENCIA para separar existencia física de consumo, no importar PHP/servidor ni su UX offline); KitchenOwl https://github.com/TomBursch/kitchenowl (AGPL-3.0; SOLO REFERENCIA de listas/recetas, sin copiar código ni lógica de sincronización).

Alternativas discriminadas para esta unidad: (1) sumar checks viejos y dejar pantalla/owner desconectado: DESCARTAR, distinto SHA y sin aceptación integral; (2) renovar owner/DB/SQLite o copiar servidor Grocy: DESCARTAR, redundante y ajeno a Android local; (3) contrato mínimo descrito en B4, reusando SQL15, APIs de owner/Compose ya presentes: ELEGIR sujeto a B5 y permiso; (4) cache/memoización de cifras entre restauraciones o UI sin pointer/grant: DESCARTAR por fuga de datos stale.

## B4 — Diseño único, delta mínimo y rutas exactas CANDIDATAS

Crear test global de integración mínimo y workflow dedicado, disparado solo por nuevas rutas exact2. Misma ejecución/HEAD: SQL15 5 stores ACTIVE, adquisición confiable, compra10→recepción7→3 no recibidos; reserva2, consumo1 no reservado, Undo1, release2; confirmar físico/reservado/disponible, historial y error/guard en UI. Reinicio, FBS2v12 backup/restore con source+target autenticados, pointer ABA y journal UNKNOWN, red OFF, 200% fuente/semánticas en emulador/API35 cuando admisible, build APK V2CoreDev aislada. Sumar CI original XML de D0A/B/D1A/D1B, restauración y regresión A2C acotada con conteos reales detectados (no inventar números); verificar clases, casos únicos, errors/failures/skipped=0, APK bytes SHA256, artifact. A2E conserva su aceptación global de inventario **después** de A2D, no confundir cierres.

**Rutas candidatas (2; fuente funcional nueva/ediciones solo DESPUÉS de READY por esta unidad):**
1. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W05StockReadGlobalDevAcceptanceTest.kt`
2. `.github/workflows/faro-v5-s4-w05-a2d2-global-read-acceptance.yml`

Escrituras funcionales nunca se incluyen en commit de permiso; B6 comienza con `python3 scripts/quality/faro-protocol-gate.py --mode preflight --head HEAD^ --unit S4_W05_A2D2_GLOBAL_STOCK_READ_ACCEPTANCE_DEV` y **todos** los `--path` modificados (o `--head` HEAD actual según modo; respetar contrato local). En RED_FIX solo delta documentado, mismo unit y B7/guard previo. En cualquier mismatch => STOP, B1–B5 delta, nueva autorización exacta; no reemplazar rutas por comodines.

## B5 — Auditoría adversarial antes de código

Rechazar: A2C1 B7 usado como certificado de UI; historial SQL14 leído como 15; compra pagada tratada como físico; reserva consumida automáticamente; Undo repetido; estado UNKNOWN como 0 o COMMITTED; ACL revocada; cambio root lease, WAL al momento de render, nombre/unidad erróneos; screen fontScale=2 en emulador; artefacto XML stale de run anterior; Android instrumentación no ejecutada descrita PASS; G15 no probado descrito PASS. B7 solo con CI+guard exactos, XML originales y APK; permiso consumido, checkpoint A2D CLOSED DEV, writer RELEASED y guardia documental final SUCCESS.

Puertas globales obligatorias: `main`, V4.4, S2 y A2A/B/C certificados intactos; inventario productivo, pagos, V2 productiva, FARO Auto/Noche OFF; sin Internet ni APIs pagas; no importación de legacy. `stock.consume` y `undo.execute` son mutaciones con grants distintos y no las concede ningún botón de lectura; snapshot = Evidence de lectura de Reality **solo** con procedencia y pointer completo, nunca se convierte en Apply. Todo B6 exige writer CAS nuevo, permiso READY individual con rutas exactas, guardia documental y de permiso SUCCESS y preflight local sobre el parent. Los tests/CI/XML deben verificarse por SHA, cada B7 debe consumir permiso y writer RELEASED antes de pasar a la siguiente unidad.

**Gate de salida del pretrabajo:** B1–B5 guardia documental SUCCESS, fuente HEAD revisada y preflight/READY propios; B6 y B7 reservados a Work. El PASS de B1–B5 no es el PASS de producto.
