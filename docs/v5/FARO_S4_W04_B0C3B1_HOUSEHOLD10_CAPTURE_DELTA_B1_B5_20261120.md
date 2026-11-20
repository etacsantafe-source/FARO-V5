# FARO V5 — W04 B0C3B1 · Captura autenticada desde Household10 STAGED

Fecha local 2026-11-20. Unidad `S4_W04_B0C3B1_HOUSEHOLD10_ARCHIVE_CAPTURE_DEV`. Complementa `FARO_S4_W04_B0C3B_HOUSEHOLD10_COMPLETE_ARCHIVE_B1_B5_20261120.md`. Alcance B0C3B1 limitado a admisión strict STAGED10 y captura real FBS2v6. La restauración queda para B0C3B2.

## B1 — Evidencia y brecha

- HEAD funcional B0C3A `e46917aa8ef8c70588226af9e4ac1576dca72d5a` aporta `RealityHousehold10SealedRowsPort` y `RealityHousehold10StagedRowsRestorePort`. La prueba SQL de estas dos tablas termina con su propio B7.
- `FaroV5GenerationStoreGraph.openValidatedV2Finance9Auxiliary4Stage` fija Reality9 y Auxiliary4. `RealityStore.validateExistingFinance9` y `RealityFinance9SealedReadPort` fijan la versión 9. La captura desde Reality10 necesita dos rutas adicionales en Reality.
- `FaroV5FiveStoreBackupCapture.captureReceiptProjection` captura la base de cinco stores, Planning, G4 y recibos. `FaroV5Household10Fbs2Codec` y `sealWithHousehold` autentican esa base y H10S.

## B2 — Comparación y reutilización

- P02 L15 audita los datos reales de SmartCompra V4.4. El respaldo V5 conserva el esquema Household10 y el conjunto de cinco stores con sus recibos. No importa el runtime histórico.
- Reutilizar el perfil SQLite exacto, la captura tipada, el límite F9S1, la lectura sellada H10 y la envoltura FBS2v6 existente. El bridge Finance9 sobre Reality10 mantiene la versión semántica de sección Finance9, aunque exija un archivo físico versión 10.

## B3 — Alternativas

- Elegida la proyección de captura desde un stage10 canónico, con Reality abierto read-only e immutable y el graph validado bajo la misma lease. Usar la API de captura de cinco stores para conservar semántica y recibos.
- Se descarta rebajar `user_version`, usar un graph9 contra 10, recrear tablas con SQL de respaldo y abrir la generación activa durante la captura. Se descarta mezclar la restauración con esta unidad porque la repetición durable y el pin del cifrado v6 requieren un seam adicional en el restaurador.

## B4 — Contrato y rutas

- Verificar lease exclusiva de host DEV cerrado, puntero ID+secuencia, descriptor y marcador canónicos, no aliases, ausencia de sidecars, versiones de cinco archivos y FK. Mantener la comprobación antes y después de captura y autenticación.
- Exponer una proyección interna del graph stage10. Reality10 read-only se admite de forma explícita y exige Planning8, Finance9, las tablas Household10 y la revalidación del lease. El resto de aperturas conserva su versión y contrato. No crear tablas ni habilitar el host activo desde aquí.
- Capturar con el graph bajo el mismo quiescence. Corroborar conservación de los cinco archivos sellados y exactitud de la conversión de filas Household10.
- Usar FBS2v6 con su base FBS2v5 y el snapshot Household10 obligatorio. Evitar políticas o grants en la proyección.
- Rutas exactas candidatas para el permiso propio:
  - `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
  - `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9SealedReadPort.kt`
  - `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
  - `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10ArchiveCoordinator.kt`
  - `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household10ArchiveCoordinatorTest.kt`
  - `.github/workflows/faro-v5-s4-w04-b0c3b1-household10-capture.yml`

## B5 — Preauditoría adversarial

- Captura real de Finance9, cinco stores, Auxiliary4 con recibo y Household10 con necesidades y revisión. Comparar el resultado autenticado y la huella de los cinco archivos antes y después.
- Comparar snapshot Household vacío con revisión distinta de cero. Probar rechazo de la generación activa, alteración del puntero, schema9, aliases, sidecars y companion incorrecto. Mantener el rechazo de esquema10 en el opener anterior y de FBS2v6 en la API v5.
- Riesgo de los openers de companions existentes: la admisión exacta previa impide migraciones. Probar que la captura deja los cinco archivos sellados sin cambios.
- El B7 de captura requerirá XML y guardia del SHA real. C3B2 deberá autenticar una sola vez, identificar el cifrado original durablemente, restaurar la base en STAGED9, migrar a STAGED10 y verificar base y Household con reapertura. Nunca cambiar el puntero en C3B.
