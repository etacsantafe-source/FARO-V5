# FARO V5 · W04-B0C2 · Archivo autenticado FBS2v6

Unidad `S4_W04_B0C2_HOUSEHOLD10_AUTHENTICATED_ARCHIVE_DEV`. Base B0C1 `52961b19f6db6c1a37dfb3d0576776ff28c9a22c` y B7 en `docs/v5/FARO_S4_W04_B0C1_HOUSEHOLD10_BACKUP_SECTION_B7_PASS_20261119.md`. Alcance semántico y autenticación del archivo en memoria. La captura y la restauración SQLite sobre Reality10 requieren B0C3.

## B1 — Evidencia

- B0C1 aporta un encoder/decoder canónico y acotado para `household_need_state` y `household_need`. B0B conserva Finance9 y Auxiliary4 al migrar la copia. El producto todavía no crea ni carga archivos Household.
- `FaroV5Auxiliary4Fbs2Snapshot` contiene el set obligatorio de cinco stores, Finance9 y el ledger de recibos de Auxiliary4. `FaroV5Auxiliary4Fbs2Codec` comprueba relaciones, versiones, tamaños y digests para v5. `FaroV5FiveStorePortableBackupCrypto` tiene primitivas privadas genéricas `sealPayload` y `openPayload`, con AES-GCM, cabecera autenticada, KDF, límites y limpieza. Reutilizar ambas como caja cerrada. El máximo portátil es 16 MiB de plaintext.
- `FaroV5Auxiliary4ArchiveCoordinator` revela el punto de integración posterior. Sus puertos Reality9 verifican v9 estricta y el archivo v5 reproduce recibos propios de Auxiliary4. B0C2 no debe ampliar la admisión de esos caminos.

## B2 — Compatibilidad y reutilización

Componer bytes canónicos FBS2v5 como una sección base y H10S como una segunda sección obligatoria. Reutilizar el codec v5 completo para preservar Finance9, datos existentes y Avisos, incluido el ledger. Mantener las funciones públicas que ya abren generaciones antiguas y probar que un archivo antiguo se puede abrir por su punto de entrada original. El alcance V4.4 del análisis W04-A se conserva. Sin lectura de su base o formato en B0C2.

## B3 — Diseño elegido

Crear FBS2 versión 6 con cabecera y presupuesto del mismo formato portátil, sin inventar criptografía o KDF. Los dos digests SHA-256 de secciones tienen función de integridad estructural. AEAD del FAROBAK2 autentica la cabecera y todo el plaintext. Se descarta añadir bytes opcionales al v5, omitir Household ante una versión desconocida o abrir un archivo v5 por la entrada v6 con defaults silenciosos.

## B4 — Flujo y rutas

La cabecera v6 expresa magic, versión, tamaño de base v5, tamaño de Household y los dos digests. Exigir longitudes estrictamente positivas, suma con Long y coincidencia exacta con el tamaño del archivo, límite 16 MiB y sección Household dentro de su límite. El codec B0C1 valida los bytes H10S y el v5 valida la base. Los arrays intermedios se limpian en finally al igual que en v5. El tipo de snapshot exige las dos secciones, incluso si la tabla de necesidades está vacía.

Agregar métodos internos específicos en `FaroV5FiveStorePortableBackupCrypto` que deleguen a `sealPayload` y `openPayload` con el codec v6. No modificar primitivas existentes, parámetros, header FAROBAK2, tamaños o contratos de errores. La entrada v6 debe rechazar v5 y la v5 debe rechazar v6. La migración explícita de respaldo v5 a un stage10 con Household vacío queda en B0C3. B0C2 no monta host ni escribe archivos de usuario.

Rutas exactas propuestas:

- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10Fbs2Codec.kt`
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStorePortableBackupCrypto.kt`
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household10Fbs2CodecTest.kt`
- `.github/workflows/faro-v5-s4-w04-b0c2-household10-fbs2.yml`

B0C3 necesita construir la proyección de cinco stores sobre un Reality10 sellado, con la misma validación de filas Finance existente y admisión explícita de archivo 10. Requiere restaurar Household en el mismo Reality STAGED bajo lease, sin promover grants ni revivir un intento Apply. El target parcial se mantiene en cuarentena hasta completar validación y podrá rechazarse sin tocar ACTIVE. La recuperación y el cutover se prueban en B0D.

## B5 — Pruebas y cierre

- Round trip v6 de cinco stores con proyección Finance9, al menos un recibo real o validado de Auxiliary4 y Household con revisión y una fila. Reemisión byte a byte del plaintext.
- Un Household vacío explícito conserva su revisión. Sección faltante, lengths no válidas, digests corruptos, truncamiento o trailing bytes se rechazan. El codec antiguo debe rechazar v6, el nuevo debe rechazar v5.
- Probar el archivo autenticado con clave correcta, clave incorrecta y alteración de ciphertext. Abrir un v5 autenticado solo por el método antiguo y v6 por el nuevo.
- CI JVM y guardia verdes en el mismo SHA, con verificación directa del XML. El archivo en memoria no certifica que se capturen o restauren filas SQLite reales.
