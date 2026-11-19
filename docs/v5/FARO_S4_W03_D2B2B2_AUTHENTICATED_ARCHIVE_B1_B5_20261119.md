# FARO V5 — W03-D2B2B2 · Archivo autenticado de cinco owners con Auxiliary4

Unidad S4_W03_D2B2B2_AUTHENTICATED_ARCHIVE_DEV. Base funcional 0b1efa6b958276b7137287faaa98b3ce3c1b09d3. STAGED DEV aislado. La activación, journal de switch y host tienen gates propios.

## B1 — Evidencia
D2B2B1 FBS2v5 (código 71c174a7e8f534fe946d071c6c67bc28d6069850, CI 44600744259 y XML 5/5) y D2B2B2 AUX4 STAGED BACKUP (código 0b1efa6b958276b7137287faaa98b3ce3c1b09d3, CI 44602951476) resuelven representación y owner. FaroV5FiveStorePortableBackupCrypto cifra/abre solo FBS2v1–4, FaroV5FiveStoreBackupCapture llama el backup standard de Aux3 y FaroV5FiveStoreStagedRestore lo restaura; ese owner debe rechazar v4 sin ledger. Debt/Plan Finance9 se materializan por RealityFinance9StagedRowsRestorePort con grafo cerrado. Falta conectar un snapshot/replay de los cinco owners con FBS2v5 y el mismo archivo autenticado. Aceptación: exactitud tras reapertura, recuperación al reintentar mismo archivo e identidad bloqueada ante archivo distinto.

## B2 — Comparación y reutilización
V4.4 FaroV4FollowUpSurface.kt @ bfa0ee71f5b9cd104baaadd71f1e77a61f203939 conserva recordatorios cuando no hay permisos de notificación. Mantener esa semántica y excluir DeliveryIntent del backup. Reutilizar FAROBAK2 AEAD, codecs, ArchiveIdentity, writer quiescence y la restauración semanticada de los cinco stores. Mantener la admisión pública FBS2 legacy y ruta Finance9v4 con las mismas restricciones.

## B3 — Investigación
USAR AES-GCM con AAD para cabecera y autenticación del payload existente (https://csrc.nist.gov/pubs/sp/800/38/d/final y https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html). Reutilizar configuración de FAROBAK2, PBKDF2, nonce y limpieza de memoria sin migrar criptografía. Referencia pública NIST y especificación API, sin copiar implementaciones ni agregar dependencia. SHA-256 interno para integridad/canonicalización y pin local de identidad; DESCARTAR digest sin AEAD o volver a cifrar la base v4 del archivo para restaurarla, porque rompe la identidad exacta entre reinicios.

## B4 — Unificación y rutas
Agregar opt-in interno al codec FAROBAK2 sin admitir v5 en open legacy. Un coordinador STAGED usa lease único y puntero completo, el mismo grafo de cinco owners y Auxiliary4 combinado. Patrón Finance9: sealed snapshot/restore tras cerrar grafo, verificación completa al reabrir. Autenticar bytes, comprobar fuente/destino, pin del ciphertext exacto, y recién después migrar la copia STAGED a Auxiliary4. La ruta privada reusa el restore común con la proyección de Auxiliary4 y su ledger; los lectores públicos siguen bloqueados para v5. Reintentar admite v4 del owner ya migrado y verifica datos existentes; jamás activa. Preflight exacto de las rutas:
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Auxiliary4ArchiveCoordinator.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStorePortableBackupCrypto.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreBackupCapture.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreStagedRestore.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt
- v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Auxiliary4ArchiveCoordinatorTest.kt
- .github/workflows/faro-v5-s4-w03-d2b2b2-authenticated-archive.yml

## B5 — Auditoría previa
Negativas con contraseña incorrecta, ciphertext alterado, pin previo para archivo distinto, destino ACTIVE o descriptor no canónico, puntero ABA y crash parcial con reintento exacto. Comprobar que la admisión y pin se ejecuten antes de mutar schema/owner; lease y fence exactos hasta sellado o relectura. Probar que guardados, asuntos, recordatorios, recibos, Workspace y Finance9 sobrevivan al round-trip. Cerrar conexiones antes del acceso SQL9 sellado. Ningún grant, OS DeliveryIntent ni operación ACTIVE cruza el archivo. El switch autoritativo D2B2B3 es posterior.
