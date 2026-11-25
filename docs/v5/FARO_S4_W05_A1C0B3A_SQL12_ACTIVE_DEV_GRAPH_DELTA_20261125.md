# FARO V5 · W05-A1C0B3A · Grafo ACTIVE DEV read-only SQL12

## B1 — Delta de evidencia

La preauditoría A1C0B3 permanece vigente. A1C0B2A y A1C0B2B tienen B7 PASS. El código `7af8bda34e9aa05b3dad0964e41e58d9e62f8d97` y CI 45548015333 demostraron la etapa STAGED SQL12 sellada, con captura de los cinco owners y compras más recibos. El grafo nuevo de ese bloque excluye explícitamente la generación seleccionada. El grafo ACTIVE existente accede a Household10 exacto, de modo que SQL12 requiere una nueva admisión read-only. El corte A1C0B3A prueba esa admisión sin hacerse cargo del journal, y A1C0B3B queda para ticket, cutover y recuperación.

## B2 — Reutilización

Reutilizar `ActiveSqlitePreflight`, el lease exclusivo vigente `finance9-dev-root.lock`, `MaterialWriteGate`, el uso exacto del puntero, el cierre y la salud de los cinco owners del grafo DEV. Preservar el runtime Household10 y los openers SQL12 STAGED. Usar de referencia los hosts SQL11 Evidence2 B7 C2C0/C2C1 y las matrices P02/P03, sin importar o copiar V4.4. El código de compra y recepción queda para sus permisos propios.

## B3 — Decisión revisada

Abrir Reality12 ACTIVE en modo no CREATE y read-only sobre WAL real. La conexión entra únicamente por un modo explícito SQL12 DEV, con chequeos del lease y puntero completo antes y después de cada lectura. Reutilizar la validación de filas y esquema SQL12 sobre la conexión admitida. El puerto STAGED usa `immutable=1` y no es válido para observar una conexión ACTIVE con WAL. Descartar apuntar el perfil Household10 a un SQL12 y descartar dar escritura por presencia de tabla o estado de puntero. SQLite reconoce los sidecars legítimos del WAL vivo y aplica su vista transaccional de lectura. [SQLite WAL read-only](https://sqlite.org/wal.html#read_only_databases), [formato de WAL e índice](https://sqlite.org/fileformat.html#wal_index_format), [transacciones SQLite](https://www.sqlite.org/lang_transaction.html).

## B4 — Rutas exactas

1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household12ActiveDevReadHost.kt`
3. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
4. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold12PurchaseRowsPort.kt`
5. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household12ActiveDevGraphTest.kt`
6. `.github/workflows/faro-v5-s4-w05-a1c0b3a-sql12-active-dev-graph.yml`

El grafo se ejercita con selección explícita del fixture DEV. El ticket, la promoción y el journal corresponden a A1C0B3B, que depende de este B7. La app queda fuera de estas seis rutas.

## B5 — Matriz adversarial

Verificar lectura de compra y recibos reales en SQL12 Evidence2, presencia de cinco almacenes sanos, rechazo de versión 10 y 11, STAGED sin puntero seleccionado, Evidence equivocada, alias y lease ocupado. Revocar admisión y provocar ABA entre dos lecturas. Comprobar reapertura de SQLite/WAL y cierre del lease sin habilitar compras, stock ni Apply. Correr CI por SHA con XML original y guardia. Solo después del B7 se congela el permiso de journal y cutover.
