# FARO V5 · W05-A1C0B2B · Coordinación de backup FBS2v9 y SQL12 STAGED

## B1 — Delta de evidencia

La preauditoría A1C0B2 sigue vigente. A1C0B2A B7 PASS en 80f5d08db0dcda9882d3f5e15432ab8650a55e90, CI 45545699935, guardia 45545699795 y XML 23/23 verificado. El puerto SQL12 expone compras, recibos y revisión en una sola vista SQLite. Su entrada de replay exige destino sellado SQL12 idéntico o con ambos conjuntos vacíos. El puerto verifica DDL, integridad, claves externas y límites. La generación debe conferir admisión STAGED bajo lease y puntero completo, orquestar los cinco owners y pinnear el contenido original sellado.

## B2 — Reutilización y límites observados

`FaroV5Household11V8BaseReplay` admite explícitamente sólo `AuthenticatedHousehold11RestoreArchive`. Pinnea el ciphertext original y restaura los cinco almacenes y Finance9. `FaroV5FiveStoreStagedRestore` consume esa proyección autenticada, sin aplicar H10 ni H11R. Los puertos de Finance9 y necesidades están parametrizados por versión física concreta. `FaroV5GenerationStoreGraph` y `RealityStore` tienen una rama STAGED de lectura Household10; su verificación y proyección Planning están limitadas a SQL10. Sus entradas deben continuar exactas. Reutilizar P02/P03, el guard de etapa y los coordinadores de portabilidad históricos de V5.

## B3 — Diseño de coordinación

Proyectar la base interna v8 mediante un tipo sellado producido exclusivamente por el abridor autenticado original de cada versión. Así el replay base puede reutilizar su trayectoria con bytes FBS2v9 originales para identidad, incluyendo restauración y verificación Finance9. Mantener entrada directa v8 y su admisión particular sin aceptar v9 a través de ella. Rechazar recifrar el v8 interior para replay porque el nonce cambiaría la identidad de reintentos y un archivo nuevo duplicaría material. Rechazar lecturas por un opener SQL10 de un archivo SQL12. Una variante STAGED exacta 12 debe reutilizar únicamente proyecciones de lectura, con modo de apertura que falle antes de crear o migrar cualquier SQLite. Los SQLite sellados se verifican tras cerrar el grafo y se cotejan con la proyección semántica de los cinco owners, incluyendo Finance9, Planning y Evidence2.

Verificar el lease exclusivo y el puntero con ID y secuencia antes de abrir, antes de cada efecto y al final. Usar archivo fijado por identidad y operación para reintentar. Desde un SQL9 no activo, reutilizar replay base, migrar con las transiciones certificadas, restaurar necesidades y dejar H11R vacío hasta el commit SQL12 de compras y recibos. El camino v8 explícito puede instalar SQL12 sobre el SQL11 STAGED verificado con compras vacías, conservando recibos anteriores. El replay v9 debe detectar base divergente también si reabre en versión intermedia o si se reintenta tras SQL12. No intercambiar las políticas de legado y v9.

Fuentes consultadas. [Transacciones SQLite](https://www.sqlite.org/lang_transaction.html), [URI y modo de solo lectura](https://www.sqlite.org/uri.html), [PRAGMA e integridad](https://www.sqlite.org/pragma.html).

## B4 — Rutas exactas del coordinador STAGED

1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household12V9StagedReplay.kt`
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household12PurchaseSql12Port.kt`
3. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11V8BaseReplay.kt`
4. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreStagedRestore.kt`
5. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
6. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
7. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9SealedReadPort.kt`
8. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold11NeedsRowsPort.kt`
9. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold12StagedFilePort.kt`
10. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household12V9StagedReplayTest.kt`
11. `.github/workflows/faro-v5-s4-w05-a1c0b2b-fbs2v9-five-store-replay.yml`

El abridor autenticado v9 y su proyección pueden vivir en el mismo archivo del coordinador. La variante SQL12 debe mantener aislada la apertura STAGED exacta y excluir toda entrada ACTIVE.

## B5 — Aceptación de coordinación

Abarcar cinco owners, compra 10 y recibos 7+3 preservados, Evidence1 y Evidence2 según archive, v8 explícito con compras vacías, H11R anterior intacto, dos reintentos sin efectos duplicados, divergencias heredadas y post-migración, corrupción, archivo cambiado, lease revocado y puntero ABA. CI dirigida con JDK21 y SQLite real. Verificar XML originales, SHA y guardia, además de las regresiones v8 de base y replay Evidence2 que se reutilicen. Antes de habilitar un cutover, cerrar B7 de esta unidad. Esta nota no emite READY. W05-A1 DEV sigue abierto.
