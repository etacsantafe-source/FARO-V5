# FARO V5 — W05-A2D1A · B1–B5 para Work Turbo (27/11/2026)

**Unidad:** `S4_W05_A2D1A_STOCK_ACCESSIBLE_PRESENTATION_DEV`. **Objetivo:** Panel Compose accesible y estado de stock, sin montar productividad.
**Base fuente:** `29216d6d403e9d5e590551a92f0b68073350118e`. **Dependencia:** A2D0B B7 PASS.
**Estado al publicar:** preauditoría B1–B5 documental para guardia; **NO código, NO CI nuevas, NO B7**, permisos por unidad pendientes salvo D0A si READY independiente posterior. Los paths son scope PREVISTO; no son privilegio de escritura por sí mismos.

## B1 — Evidencia real, hueco exacto y aceptación

Repo etacsantafe-source/FARO-V5, rama work/s3-night-20261102, HEAD inicial `29216d6d403e9d5e590551a92f0b68073350118e`. A2C CLOSED DEV por B7 XML 91/91 y APK aislada; código `b064960b4443f56776781744bc30279648da01cb`, B7 checkpoint `29216d6d403e9d5e590551a92f0b68073350118e`, guardia final 46046431091 SUCCESS. A2A físico SQL13 PASS; A2B reserva SQL14 PASS; A2C almacenamiento/consumo/Undo SQL15 PASS. `docs/v5/FARO_S4_W05_A2_STOCK_RESERVATIONS_CONSUMPTION_ROADMAP_20261126.md` exige A2D lectura integral/UX ANTES de A2E. El permiso heredado de A2C es BLOCKED y no autoriza ninguna fuente A2D. Writer global CAS, AGENTS.md y protocolo B1–B7 prevalecen.

Evidencia de archivos reales inspeccionados en `29216d6d403e9d5e590551a92f0b68073350118e`: `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt` (blob 72a5dc9853e417b4a18886590f531c095936d278) ofrece `inspectActiveHousehold15DevReadOnly` sobre SQL15, cinco stores, pointer, guard/lease y callback de selección; `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15ConsumptionRowsPort.kt` (4af7a385640977f6e984d5a49878926b7ad60287) define `RealityHousehold15ConsumptionRows`, historia SQL14/13/12/11, eventos CONSUME/CONSUME_UNDO, orden y restricciones. `FaroV5Household13DevStockSnapshot.kt` (03cd385c5804e5c89195bf9da8ce299a0515377a) ya calcula físico SQL13 con reservado/consumido/disponible UNKNOWN, NO se debe modificar ni atribuirle disponibilidad. `FaroV5Household14ActiveDevReadHost.kt` (88ac609f112b459427abc1b19cb89c4e48ee03c7) solo cubre SQL14, no sustituye lectura SQL15.

P06 UX `docs/v5/FARO_S4_P06_FINAL_UX_SPEC_20261113.md`: fuente 200%, destinos dentro de SmartCompra, targets >=48dp, estados negativos NO_AUTORIZADO/STALE/UNKNOWN no traducidos a cero, Cocina solo subsección. El shell V5 existente `FaroV5ShellNavigation.kt` (blob e40ac8fcbf65014149cfd97a797166e55f40004f) contiene SMART_COMPRA como raíz y no tiene Inventario raíz. No hay aún `FaroV5StockReadSurface` ni pantalla A2D.

## B2 — Comparación V4.4, V5 y reutilización

V4.4 L12 y P03 R12 apuntan a lectura de stock separada; adaptar lenguaje sencillo y visible, no copiar el código V4.4 ni montar nueva sección. Reusar patrón `FaroV5FinanceReadSurface.kt` de cobertura READY/PARTIAL/DENIED/STale y `FaroV5ShellNavigation`; no editar su shell en esta unidad.

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

Crear componentes Compose independientes y modelo de UI de solo lectura que reciba snapshots autenticados ya mapeados; no acepta ID/epoch técnicos introducidos por usuario. Dentro de SmartCompra, bloque “Stock”: tres valores **Físico, Reservado, Disponible** y detallado historial fuente/fecha/operación/Undo. Unidades visibles en cada fila, sin conversiones silenciosas. Falta de libro/respaldo/grant/lease => mensaje explícito y ocultar números. Vacío confirmado => “Todavía no registraste stock”; no presentar “0” como evidencia cuando fuente desconocida. Lista y detalle accesibles; agrupación SKU+unidad, sin precio ni saldo Finance; scroll vertical en 200%, ancho compacto/expandido, orientación/split, dark/light, semántica heading y contentDescription de cantidad/unidad/estado; foco TalkBack secuencial; 48dp mínimos. No agregar Cocina raíz ni operaciones de Apply. Sin dependencias nuevas salvo justificadas.

**Rutas candidatas (3; fuente funcional nueva/ediciones solo DESPUÉS de READY por esta unidad):**
1. `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5StockReadSurface.kt`
2. `v5/surface/src/test/kotlin/ar/com/faro/v5/surface/FaroV5StockReadSurfaceContractTest.kt`
3. `.github/workflows/faro-v5-s4-w05-a2d1a-stock-presentation.yml`

Escrituras funcionales nunca se incluyen en commit de permiso; B6 comienza con `python3 scripts/quality/faro-protocol-gate.py --mode preflight --head HEAD^ --unit S4_W05_A2D1A_STOCK_ACCESSIBLE_PRESENTATION_DEV` y **todos** los `--path` modificados (o `--head` HEAD actual según modo; respetar contrato local). En RED_FIX solo delta documentado, mismo unit y B7/guard previo. En cualquier mismatch => STOP, B1–B5 delta, nueva autorización exacta; no reemplazar rutas por comodines.

## B5 — Auditoría adversarial antes de código

Tests API Compose / JVM según suite existente: estados READY/EMPTY/DENIED/STALE/UNKNOWN exhiben textos correctos; cero solo con libro íntegro; 2 SKU iguales con distinta unidad se ven separados; fuente 200% sin recorte ni scroll perdido; TalkBack anuncia nombre+cantidad+unidad+estado y botón vuelve; solo lectura (sin botones que muten); ventana compacta/landscape/split, rotación restaura pantalla sin monto stale; no mostrar precio/reintegro; historial Undo legible separado. Pruebas de instrumentación visual/emulador se completarán en A2D1B; este B7 no afirma G15.

Puertas globales obligatorias: `main`, V4.4, S2 y A2A/B/C certificados intactos; inventario productivo, pagos, V2 productiva, FARO Auto/Noche OFF; sin Internet ni APIs pagas; no importación de legacy. `stock.consume` y `undo.execute` son mutaciones con grants distintos y no las concede ningún botón de lectura; snapshot = Evidence de lectura de Reality **solo** con procedencia y pointer completo, nunca se convierte en Apply. Todo B6 exige writer CAS nuevo, permiso READY individual con rutas exactas, guardia documental y de permiso SUCCESS y preflight local sobre el parent. Los tests/CI/XML deben verificarse por SHA, cada B7 debe consumir permiso y writer RELEASED antes de pasar a la siguiente unidad.

**Gate de salida del pretrabajo:** B1–B5 guardia documental SUCCESS, fuente HEAD revisada y preflight/READY propios; B6 y B7 reservados a Work. El PASS de B1–B5 no es el PASS de producto.
