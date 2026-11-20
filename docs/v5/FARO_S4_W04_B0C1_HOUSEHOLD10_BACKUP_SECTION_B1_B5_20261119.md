# FARO V5 · W04-B0C1 · Sección portable Household10

Unidad `S4_W04_B0C1_HOUSEHOLD10_BACKUP_SECTION_DEV`. Una sección tipada y verificable para las dos tablas Household10. Base B0B `7ed40534e20bcca21e2888282a333848591f71e0` y B7 en `docs/v5/FARO_S4_W04_B0B_HOUSEHOLD10_STAGED_GENERATION_B7_PASS_20261119.md`. Los lectores y escritores de archivos existentes conservan sus perfiles actuales hasta una integración explícita.

## B1 — Evidencia

- W04-A define tipos Household. B0A fija `household_need_state` y `household_need`, con fecha nullable expresada en epoch day. B0B migra el archivo Reality solo dentro de una copia de cinco stores bajo puntero fijado.
- `FaroV5Auxiliary4Fbs2Codec` ya representa Finance9 y Auxiliary4 con versión y dos secciones obligatorias. `FaroV5FiveStorePortableBackupCrypto` reutiliza el envelope autenticado FAROBAK2 con límites y rechazo de contenido inválido. `RealityFinance9SealedReadPort` valida versión exacta 9 y proyecta ocho tablas Finance. Su versión de proyección no puede confundirse con la versión física del archivo Reality que la origina.
- B0C1 cubre únicamente bytes de sección Household en memoria. La aceptación requiere round trip fiel y fallo cerrado ante entradas fuera de contrato.

## B2 — Reutilización

- Usar los tipos W04-A para validar identidad, nombre, cantidad, estado, prioridad, revisión y fecha. La sección pertenece al módulo de generación y consume `:owner:household` con dependencia JVM explícita.
- Mantener el enfoque de las secciones F9S1 y del ledger Auxiliary4: cabecera explícita, límites, orden canónico y rechazo de trailing bytes. La comparación V4.4 de W04-A/B0 se mantiene. El respaldo V5 conserva datos tipados y sus revisiones, sin copiar la base V4.4.

## B3 — Formato elegido

Usar campos binarios explícitos, UTF-8 estricto con longitud acotada, valores enteros y enums por nombre estable. Evitar ordinales, Java Serialization y volcado SQL genérico. Limitar tamaño antes de asignar y validar todas las filas usando sus constructores. El digest y la autenticación son responsabilidades del envoltorio FBS2/FAROBAK2 posterior.

La alternativa de enviar Household como FBS2v5 sin sección se descarta por omisión de los datos. Un campo adicional en el snapshot de Finance9 mezclaría propietarios y dificultaría probar que un archivo antiguo conserva la misma lectura.

## B4 — Diseño y rutas

Formato local `H10S` versión 1, schema físico 10, revisión agregada no negativa, número de necesidades, seguido por campos fijos en orden tipado. Ordenar o exigir orden estricto por `HouseholdNeedId.value` con el mismo comparador Kotlin en captura, emisión y lectura. No delegar la canonicalización de Unicode al orden SQL BINARY, que puede diferir. Rechazar duplicados, entidad con revisión por encima de la revisión agregada y codificaciones UTF-8 no representables sin sustitución. Fecha con marcador binario explícito de presencia y `LocalDate.toEpochDay`. Sección vacía explícita con revisión válida incluso después de un borrado. Hasta 8192 filas y 4 MiB por sección, con valores de texto acotados por modelo y tamaño en bytes. El coordinador futuro debe fallar completo al superar los límites, sin truncar datos.

Rutas exactas propuestas:

- `v5/store/generation/build.gradle.kts`
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10BackupSectionCodec.kt`
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household10BackupSectionCodecTest.kt`
- `.github/workflows/faro-v5-s4-w04-b0c1-household10-backup-section.yml`

B0C2 unirá base exacta Finance9/Auxiliary4 más Household con sección obligatoria en FBS2v6 y los métodos autenticados del mismo FAROBAK2. B0C3 conectará captura SQLite de perfil 10 a proyecciones tipadas y restauración en copia 10 bajo lease. Añadir un camino explícito de lectura de proyección Finance9 sobre Reality10 verificado, preservando la admisión exacta de lectores v9. Restauración v5→10 requiere migración explícita con Household vacío, mientras un v6 con sección ausente o inválida se rechaza. Antes de cutover, probar el archivo completo y la recuperación por journal con Finance y recibos preservados.

## B5 — Pruebas dirigidas

- Round trip con caracteres no ASCII, cantidades límite, estado y prioridad, fechas opcionales y revisiones. La reemisión es byte a byte idéntica.
- Snapshot vacío preserva revisión agregada. Rechazar duplicados, orden no canónico y revisión de fila posterior a la agregada.
- Rechazar magic/versión/longitudes ilegales, UTF-8 inválido, enum desconocido, truncamiento, trailing bytes y valores fuera de rango. Probar el límite de filas y bytes sin aplicar ni abrir bases.
- CI JVM dirigida y guardia por SHA con XML descargable. La unidad no acredita una copia de seguridad portable autenticada, un restore ni cutover DEV.
