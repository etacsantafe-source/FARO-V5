# FARO V5 · W04-B0A · Esquema Household en copia STAGED de Reality9

Unidad `S4_W04_B0A_HOUSEHOLD10_STAGED_SCHEMA_DEV`. Base funcional W04-A `2f345d5e46a5c7d15cfa6e7b4a530f741dca0fbf`. Documento de preauditoría; requiere guardia y permiso propios antes de B6.

## B1 — Evidencia y criterio de entrada

- W04-A fijó `HouseholdNeed`: id opaco hasta 128, nombre humano hasta 160, cantidad en milésimas positiva hasta 2147483647, seis unidades enumeradas, prioridad LOW/NORMAL/HIGH, estado OPEN/COMPLETED, revisión no negativa y fecha civil opcional con año 1..9999. El futuro mapper validará el dato íntegro con los tipos de Household al leer y antes de escribir.
- `RealityFinance9Schema.installOnStagedEightConnection` instala DDL y `user_version` en una transacción SQLite; `RealityFinance9StagedFilePort` separa la apertura de archivo de esa lógica. `FaroV5Finance9StagedMigration` valida identidad y puntero antes y después de SQL; su matriz auxiliar es v3. La sesión W03-G ya llega a **Reality9 + Auxiliary4 + Workspace4 + Commercial Evidence1 + Web Evidence2**. La futura admisión de generación debe conservar Auxiliary4 y `notice_attention_receipt`, siguiendo `FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage` y el archivo v5.
- `FaroV5Auxiliary4ArchiveCoordinator` sella y restaura el conjunto de cinco stores con Finance9 y recibos Auxiliary4; su codec de Finance es exacto y no captura tablas Household. Una tabla añadida a v9 sin versionar podría perder datos al respaldar. `RealityStore` mantiene un único `BEGIN IMMEDIATE` para operaciones habilitadas; ninguna operación Household lo usa todavía.
- B0A se limita a probar DDL transaccional sobre una **copia de prueba aislada** de Reality9. Su aceptación no habilita un puerto de archivos, cutover, restauración o escritura de necesidades.

## B2 — Reutilización y comparación

- La comparación V4.4 de W04-A y la revisión B0/C0 siguen vigentes. El legado incorporaba necesidades en su SmartCompraStore; adaptar la semántica comercial sin incorporar el gateway ni un SQLite adicional.
- W01-M0A provee un patrón de DDL, integridad y rollback; reutilizar su instalador en la preparación de fixtures Reality9 y su patrón CI sin modificar su código ni repetir su suite. El instalador Household permanece interno al módulo Reality sin registro en el grafo de aplicación.
- Para unidades siguientes, reutilizar quiescence, journal y pin del puntero por generación y secuencia de cutover DEV, además del archivo autenticado Auxiliary4 y la transacción de Apply con recibo. Evitar puertos directos para mutar fuera de esa transacción.

## B3 — Alternativas

- Elegida una versión Reality10 con tablas Household aditivas, sobre archivo de stage. SQLite admite DDL en transacción y `PRAGMA user_version` como entero del formato de aplicación. Configurar foreign keys antes de BEGIN y comprobar integridad/FK dentro del límite transaccional. Referencias verificadas en B0/C0: https://www.sqlite.org/atomiccommit.html y https://www.sqlite.org/pragma.html.
- Descartado ampliar Reality9 en ACTIVE porque sus validadores y codec de respaldo esperan formato 9. Descartada otra base porque introduce un escritor y protocolo de backup/receipts adicionales. El stage copiado preserva origen9 y permite abortar antes de una eventual activación.

## B4 — Diseño y rutas exactas

`household_need_state`: singleton con revisión agregada 0 desde la migración. `household_need`: PK de texto opaca, nombre, cantidad en milésimas, unidad, prioridad, estado, revisión y `due_epoch_day` nullable usando `LocalDate.toEpochDay()`. Límite civil replicado con el intervalo inclusivo para 0001-01-01 a 9999-12-31. DDL usa tipos SQLite STRICT, NOT NULL explícitos y CHECK para rangos y enums. La validación Unicode del nombre e ID sigue en el modelo Kotlin; los checks SQL son defensa adicional.

El instalador recibe una conexión abierta por un futuro dueño confiable, valida versión exactamente 9, FK activas, tablas Reality9 requeridas y ausencia de objetos Household, dentro de `BEGIN IMMEDIATE`. Crea las dos tablas, inicializa singleton, pone versión 10 y comprueba integridad/FK antes de COMMIT. Un fallo intermedio revierte estado, DDL y versión. Las pruebas usan el patrón `RealityStore` → stage8 → instalador Finance9 y una copia de origen cerrado con WAL truncado. El enlace al dueño de generación queda sin implementar en esta unidad.

Rutas propuestas para READY B0A tras guardia propia:

- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold10Schema.kt`
- `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold10MigrationTest.kt`
- `.github/workflows/faro-v5-s4-w04-b0a-household10-schema.yml`

B0B exigirá stage canónico de cinco archivos, pin exacto y quiescence, identificación de cada versión incluyendo Auxiliary4, rechazo de links y sidecars no sellados, preservación del origen9 y fallo cerrado si cambia el puntero. B0C exigirá formato FBS2 posterior autenticado que cubra ambas tablas Household y preserve Finance9 + recibos Auxiliary4; rechazo de downgrade o secciones ausentes y restauración 9→10 explícita con Household vacío. El respaldo anterior conserva semántica 9. B0D deberá certificar journal/cutover reversible y reapertura de un único grafo DEV antes de B1 CRUD. B1 deberá definir grants locales, CAS de fila y snapshot, Review/Apply, recibo/Undo compensatorio, idempotencia y recuperación al reabrir.

## B5 — Matriz negativa y condiciones de cierre

1. Copia stage: 9→10 inicializa singleton, mantiene origen en 9 y preserva registros Finance heredados y su provenance. Verificar reabrir e integridad.
2. Fault después de aplicar DDL y `user_version`, antes de COMMIT: vuelve a 9 sin tablas Household, preserva contenido y permite reintento.
3. Versión incorrecta, schema9 incompleto o segunda migración se rechazan sin efectos.
4. FK desactivada o inconsistente se rechaza sin instalar DDL.
5. Constraints rechazan milésimas 0/desborde, enum inválido, fecha fuera de rango, review negativa y duplicado de PK. Aceptan mínimos, máximos y fecha nula.
6. Abrir stage10 con el camino activo Finance9 existente falla cerrado. No hay activación ni conversión silenciosa de ACTIVE.

CI B0A verificará suite JVM directa :store:reality:test, XML sin errores/failures/skips y guardia del SHA exacto. B0A no certifica aislamiento de una generación completa, backup, permisos, Apply, recovery ni Android. Integración productiva y pruebas físicas siguen fuera de esta unidad.
