# FARO V5 — W05-A2D0B · B1–B5 para Work Turbo (27/11/2026)

**Unidad:** `S4_W05_A2D0B_SQL15_ACTIVE_STOCK_READ_HOST_DEV`. **Objetivo:** Host lectura SQL15 ACTIVE con lease/grant y frescura.
**Base fuente:** `29216d6d403e9d5e590551a92f0b68073350118e`. **Dependencia:** A2D0A B7 PASS.
**Estado al publicar:** preauditoría B1–B5 documental para guardia; **NO código, NO CI nuevas, NO B7**, permisos por unidad pendientes salvo D0A si READY independiente posterior. Los paths son scope PREVISTO; no son privilegio de escritura por sí mismos.

## B1 — Evidencia real, hueco exacto y aceptación

Repo etacsantafe-source/FARO-V5, rama work/s3-night-20261102, HEAD inicial `29216d6d403e9d5e590551a92f0b68073350118e`. A2C CLOSED DEV por B7 XML 91/91 y APK aislada; código `b064960b4443f56776781744bc30279648da01cb`, B7 checkpoint `29216d6d403e9d5e590551a92f0b68073350118e`, guardia final 46046431091 SUCCESS. A2A físico SQL13 PASS; A2B reserva SQL14 PASS; A2C almacenamiento/consumo/Undo SQL15 PASS. `docs/v5/FARO_S4_W05_A2_STOCK_RESERVATIONS_CONSUMPTION_ROADMAP_20261126.md` exige A2D lectura integral/UX ANTES de A2E. El permiso heredado de A2C es BLOCKED y no autoriza ninguna fuente A2D. Writer global CAS, AGENTS.md y protocolo B1–B7 prevalecen.

Evidencia de archivos reales inspeccionados en `29216d6d403e9d5e590551a92f0b68073350118e`: `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt` (blob 72a5dc9853e417b4a18886590f531c095936d278) ofrece `inspectActiveHousehold15DevReadOnly` sobre SQL15, cinco stores, pointer, guard/lease y callback de selección; `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold15ConsumptionRowsPort.kt` (4af7a385640977f6e984d5a49878926b7ad60287) define `RealityHousehold15ConsumptionRows`, historia SQL14/13/12/11, eventos CONSUME/CONSUME_UNDO, orden y restricciones. `FaroV5Household13DevStockSnapshot.kt` (03cd385c5804e5c89195bf9da8ce299a0515377a) ya calcula físico SQL13 con reservado/consumido/disponible UNKNOWN, NO se debe modificar ni atribuirle disponibilidad. `FaroV5Household14ActiveDevReadHost.kt` (88ac609f112b459427abc1b19cb89c4e48ee03c7) solo cubre SQL14, no sustituye lectura SQL15.

`FaroV5GenerationStoreGraph.inspectActiveHousehold15DevReadOnly(root, selected, gate, ownerAdmission, completedDevSelection)` ya prueba SQL15 ACTIVE WAL, perfil 5 stores y rechazo por root lease/pointer ABA en `FaroV5Household15ActiveDevReadTest.kt` (blob 2c5e381fc5455523b2012182acb06d0005e73a04). Su callback de fixture es inyectable para DEV tests, NO licencia para que una UI diga `{true}`. Falta host público seguro que pinne selección real COMMITTED y transporte proyección tipada A2D0A sin salida de `RealityStore` ni conexiones SQL.

## B2 — Comparación V4.4, V5 y reutilización

Reusar inspección certificada, `FaroV5Household15Evidence2DevJournaledCutover.hasCompletedSelection(root,selected)` real, `FileActiveGenerationPointer` completo (id+sequence) y `MaterialWriteGate`; patrón del host SQL14 `FaroV5Household14ActiveDevReadHost.kt` solo referencia de lease y owner, no volver a usarlo en SQL15. El SQL13 físico `FaroV5Household13DevStockSnapshot` NO demuestra disponible.

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

Nuevo único host generación DEV de solo lectura que recibe root, pointer, principal autenticado y `ownerAdmission` verdadero (no delegado a UI). Preflight root NOFOLLOW, estado de selección SQL15 COMPLETED real, perfil 5 stores, ACL, epoch/pointer/full revision, root lease antes y después, read transaction ACTIVE WAL-aware y salida DTO D0A inmutable. Releer/revalidar pointer+lease+ACL justo antes de devolver y al reanudar foreground; revocación/puntero distinto=>`DENIED/STALE` sin números. No exponer `RealityStore`, `materialWrite` o Apply al caller. Errores/swap/recovery UNKNOWN => `UNAVAILABLE`, no stock 0. No reutilizar el callback de fixture `completedDevSelection={true}` en producción. Si imports/internal impiden API pública, realizar rediseño mínimo dentro de estas rutas o STOP y reauditar permiso por delta.

**Rutas candidatas (3; fuente funcional nueva/ediciones solo DESPUÉS de READY por esta unidad):**
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household15DevStockReadHost.kt`
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household15DevStockReadHostTest.kt`
3. `.github/workflows/faro-v5-s4-w05-a2d0b-stock-read-host.yml`

Escrituras funcionales nunca se incluyen en commit de permiso; B6 comienza con `python3 scripts/quality/faro-protocol-gate.py --mode preflight --head HEAD^ --unit S4_W05_A2D0B_SQL15_ACTIVE_STOCK_READ_HOST_DEV` y **todos** los `--path` modificados (o `--head` HEAD actual según modo; respetar contrato local). En RED_FIX solo delta documentado, mismo unit y B7/guard previo. En cualquier mismatch => STOP, B1–B5 delta, nueva autorización exacta; no reemplazar rutas por comodines.

## B5 — Auditoría adversarial antes de código

Pruebas: SQL15 ACTIVE WAL con consumo no checkpointed; SQL14 ACTIVE ante host SQL15 => denegado; archive STAGED/immutable no admitido; ticket COMPLETED ausente, corrupto o ABA; lease ocupado; symlink WAL/SHM; ACL revocado antes/durante/después de lectura; readout solo de principal correcto; cambio FULL pointer ida-vuelta ABA; restart tras journal COMMITTED y tras restore; click foreground/stale; ningún método mutador exportado; mismo snapshot repetido idempotente. No modificar C0C2 journal ni credenciales. Pruebas con archivos temporales aislados, no data user.

Puertas globales obligatorias: `main`, V4.4, S2 y A2A/B/C certificados intactos; inventario productivo, pagos, V2 productiva, FARO Auto/Noche OFF; sin Internet ni APIs pagas; no importación de legacy. `stock.consume` y `undo.execute` son mutaciones con grants distintos y no las concede ningún botón de lectura; snapshot = Evidence de lectura de Reality **solo** con procedencia y pointer completo, nunca se convierte en Apply. Todo B6 exige writer CAS nuevo, permiso READY individual con rutas exactas, guardia documental y de permiso SUCCESS y preflight local sobre el parent. Los tests/CI/XML deben verificarse por SHA, cada B7 debe consumir permiso y writer RELEASED antes de pasar a la siguiente unidad.

**Gate de salida del pretrabajo:** B1–B5 guardia documental SUCCESS, fuente HEAD revisada y preflight/READY propios; B6 y B7 reservados a Work. El PASS de B1–B5 no es el PASS de producto.
