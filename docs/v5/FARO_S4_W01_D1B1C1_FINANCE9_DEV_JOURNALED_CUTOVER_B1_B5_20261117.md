# FARO W01 D1B1C1 — cutover Finance9 DEV controlado en JVM

Fecha local 2026-11-17. Auditoría del delta `S4_W01_D1B1C1_FINANCE9_DEV_JOURNALED_CUTOVER` sobre `c0b1928109742b3068fd17038b6f6fa8916f2e3e`. W1 OPEN, W2 STOP, V2 productiva OFF.

## B1 — Evidencia y brecha
- D1B1C0 cerrado con guard 44223168557 y CI 44223168620, XML 12/12; el guard del cierre 44223787837 pasó. Se conserva su contrato de observación.
- `FaroV5GenerationStoreGraph` bloquea el schema9 ACTIVE. Su health y verificación WAL-aware ya existen para Planning8. `RealityStore` monta exacto schema9 como STAGED y concede puertos de staging bajo esa bandera. Reutilizar el verificador exacto 9, mantener no disponibles esos puertos durante el ACTIVE nuevo.
- `ValidatedGenerationActivationCoordinator` ya publica el puntero, reconcilia el journal y hace rollback antes de admisión de escritura. Los callbacks deben verificar ID+sequence con el journal exacto. `FaroV5Planning8DevCutoverOwner` verifica el mismo protocolo en 8, pero su predecesor es schema7 y no se puede usar para 9.
- Alcance verificable: activación y recuperación en raíz DEV de fixture, con etapa exacta9 preparada y journal validado; test positivo de promesa y recuperación y adversariales para host negado, ABA, stage inválido y rollback. La app W01 todavía necesitará origen duradero, cierre de sesión previa, exclusión de apertura y entrega al mismo grafo. No usar una etapa vacía para reemplazar datos de W01.

## B2 — Reutilización
Se conservan AtomicApply, receipts, Undo y recuperación de V4.4. V4.4 no tiene generación Finance9. Reusar el gate material del proceso, el coordinador, el replay fence, el descriptor y el lector de puntero completo. Abrir y atender únicamente el grafo de health del destino después de que el puntero lo identifica. Para rollback cerrar ese grafo y abrir el predecesor Planning8 en forma secuencial. Un grafo nunca se devuelve como resultado de mantenimiento.

## B3 — Alternativas
- ADAPTAR el coordinador existente con admisión exacta. Evita duplicar persistencia y transición de journal.
- DESCARTAR activar mediante el opener de staging, persistir una decisión de UI como autorización técnica o habilitar Finance9 globalmente.
- USAR `ActiveSqlitePreflight` para ACTIVE y `StagedSqlitePreflight` para STAGED bajo quiescencia. [SQLite WAL](https://www.sqlite.org/wal.html) explica por qué el activo necesita observar el log. [FileLock](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/nio/channels/FileLock.html) distingue locks de proceso y contención entre hilos. Esta unidad utiliza una raíz controlada con un solo proceso y gate material compartido. La futura integración del host tendrá que acreditar exclusión de otras aperturas.

## B4 — Implementación y rutas
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`: modo exacto9 activo explícito y acotado, con gate compartido, sin inicialización de tablas y conservando el rechazo de puertos STAGED. Aperturas históricas intactas.
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`: reusar la admisión canónica de puntero completo y el profile exacto9; opener interno DEV, chequeos WAL-aware y cierre ante error o drift. Exponer validación interna del stage con el mismo perfil sin abrir otro grafo.
3. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9DevCutoverOwner.kt`: activar y recuperar únicamente operación RESTORE autorizada explícitamente por el host de mantenimiento DEV, con origen ID+sequence, descriptor exacto y único journal; retorno de estado sin grafo. Comprobar el preflight D1B1C0 al iniciar y revalidar junto con health bajo el gate real del coordinator.
4. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9DevCutoverOwnerTest.kt`: fixtures aisladas con documentos/rows de origen, success y negativos, rollback no destructivo, recovery tras pointer CAS sin journal y rechazo de puertos de staging en ACTIVE.
5. `.github/workflows/faro-v5-s4-w01-d1b1c1-finance9-cutover.yml`: solo esa suite JVM en JDK21, reporte XML exacto, guard por SHA. Sin UI, manifest, shipping, biometría ni otro grafo de runtime.

## B5 — Verificación previa adversarial
- Tomar el gate antes de comparar el puntero y mantenerlo hasta el resultado. Full ID+sequence para origen, destino (+1) y retorno (+2), incluida recuperación tras interrupción. No aceptar journal competidor, operación ajena, directorio de otro root ni apuntador ABA.
- El dictamen informativo D1B1C0 no concede admisión. La fase SWITCH_PREPARED requiere releer descriptor, puntero, SQLite sellado y journal bajo exclusión. Si el puntero ya se publicó, usar checks ACTIVE y recuperar la operación original.
- El stage debe ser canónico, sellado y exact9 con datos de propietario coherentes. La herramienta de activación no construye ni borra etapas. No usar un stage nuevo vacío como actualización de un workspace activo.
- El modo activo no debe entregar ports STAGED de restauración/grant/revocación. Se revisan health/replay y rollback antes de aceptar escrituras de material. Mantener el origen anterior intacto y verificar su salud para rollback. No retroceder automáticamente con escrituras aceptadas ni eliminar un replay fence previo.
- Se requiere host DEV explícito, raíz privada de prueba, una sola autoridad y comprobación de cierre secuencial. Se prueban denegación previa a escritura y pérdida de proceso entre puntero/journal. Sin autorización de foreground recuperada ni kit Android en este delta; el host futuro debe acreditar procedencia duradera del target, exclusividad y adaptación del lector de sesión.
- GO limitado: tras guard documental y nuevo permiso EXACT5. No sellar B7 hasta leer el XML real. Siguiente D1B1C2: montaje específico W01 sobre sesión existente, con procedencia/recuperación duradera.
