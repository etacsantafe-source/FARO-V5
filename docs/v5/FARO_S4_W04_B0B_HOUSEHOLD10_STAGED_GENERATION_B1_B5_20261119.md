# FARO V5 · W04-B0B · Migración de generación STAGED Household10

Unidad `S4_W04_B0B_HOUSEHOLD10_STAGED_GENERATION_DEV`. Base funcional W04-B0A `8dfd37aad5a3bab8306fe901e7989fec962cc091`, B7 documentado en `docs/v5/FARO_S4_W04_B0A_HOUSEHOLD10_STAGED_SCHEMA_B7_PASS_20261119.md`. Requiere guardia documental y READY propio. La unidad migra únicamente una copia ya preparada de cinco stores cerrados y deja el resultado en STAGED.

## B1 — Evidencia y alcance

- W03-G ejercitó la sesión DEV Reality9/Auxiliary4. `FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(..., allowAuxiliary3 = false)` verifica el perfil completo de cinco stores, distinto del perfil Auxiliary3 de la primera migración Finance9.
- `FaroV5Finance9DevStagePreparation` usa lease exclusivo del host cerrado, quiescence, copia separada, fingerprints de archivo y fsync. `FaroV5Finance9StagedMigration` compara el puntero completo antes y después de instalar SQL. `FileStorageGenerationLayout` resuelve descriptor y ruta canónicos. `StagedSqlitePreflight` valida el SQLite sellado en modo immutable, sin exponer un segundo writer.
- La futura activación depende de que B0C certifique formato de respaldo que incluya Household y B0D certifique journal, cutover y reapertura. B0B termina con target no activo y sin operación de cutover registrada.

## B2 — Comparación y reuso

Mantener el comportamiento comercial del legado ya revisado en W04-A y B0/C0. En almacenamiento, tomar del V5 el perfil Finance9/Auxiliary4, la admisión STAGED y el patrón de puerto Reality para operaciones SQLite. W03-A/G y Finance9 conservan sus rutas, contratos y pruebas ya certificadas. No reutilizar el gateway V4.4 ni crear una base Household.

## B3 — Alternativas revisadas

Elegir admitir una copia sellada e independiente, migrar únicamente Reality y comprobar los cuatro archivos restantes contra el origen. El preflight anterior y posterior no abre SQLite para escribir. Aplican WAL y commit atómico de las referencias SQLite de B0/C0. Prohibir copia del ACTIVE con runtime abierto o sidecars presentes. Rechazar restauración parcial de un archivo 10 en un conjunto 9, apertura del target como ACTIVE y escritura in-place. Verificar mismo archivo además de nombre para rechazar hard links al origen.

## B4 — Algoritmo y rutas

1. Con quiescence y afirmación del lease exclusivo del host cerrado, fijar el puntero por igualdad de ID y secuencia. Resolver origen y target con `FileStorageGenerationLayout.requireStaged`, verificar descriptor y ruta, exigir IDs distintos y V2 completo. La copia viene de un caller confiable. La unidad no construye ni publica el stage.
2. Comprobar ausencias de `-wal`, `-shm` y `-journal` también con NOFOLLOW (la presencia de un link roto es rechazo). Ejecutar preflight Finance9+Aux4 exacto en ambos. Verificar que los cinco archivos iniciales del target representan la misma captura sellada pero sean archivos distintos. Leer y comparar SHA-256 por archivo mediante streaming sin seguir symlinks.
3. Revalidar puntero y lease antes de invocar el puerto Reality. El puerto comprueba archivo regular final, usa READWRITE|NOFOLLOW sin CREATE, FK ON, synchronous FULL, invoca B0A y trunca WAL con resultado no ocupado. Cerrar SQLite antes de exigir ausencia de sidecars.
4. Revalidar lease y puntero; validar Reality10 con `StagedSqlitePreflight` y verificar que el origen completo y los cuatro stores no migrados tengan sus hashes originales. Un fallo posterior a SQL deja la copia en cuarentena, sin modificar puntero, catálogo ni journal. Un target 10 no se readmite como copia nueva de v9.
5. La compatibilidad del stage mantiene Auxiliary4 y Workspace4; producción, stock, finanzas compuestas, Android, grants y CRUD quedan fuera de B0B.

Rutas exactas propuestas:

- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold10StagedFilePort.kt`
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10StagedMigration.kt`
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household10StagedMigrationTest.kt`
- `.github/workflows/faro-v5-s4-w04-b0b-household10-stage.yml`

## B5 — Pruebas dirigidas y STOP

- Real SQLite de cinco stores: copia válida mantiene puntero y origen9 idénticos, target10 y otras cuatro bases preservadas incluyendo Auxiliary4.
- Rechazar target activo, ruta/descriptor forjado, copia no equivalente y hard link al origen antes de SQL.
- Rechazar Auxiliary3, SQLite desconocido, symlink y sidecar sin resolver. Mantener versión9 de copia ante estos rechazos.
- Forzar cambio ABA de puntero tras la migración e impedir emisión de un resultado validado. No publicar journal ni activar. Reintento del mismo target10 se rechaza.
- La suite B0B debe pasar en CI JVM con XML descargable, junto a la guardia de su SHA. Validación de respaldo autenticado y recuperación durante cutover se certifican en unidades independientes.
