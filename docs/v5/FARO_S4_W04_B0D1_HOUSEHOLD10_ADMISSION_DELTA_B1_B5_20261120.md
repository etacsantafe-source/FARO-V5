# FARO V5 — W04 B0D1 · Admisión y reapertura DEV de Household10

Fecha local 2026-11-20. Unidad `S4_W04_B0D1_HOUSEHOLD10_PREFLIGHT_ACTIVE_GRAPH_DEV`. Delta contra `FARO_S4_W04_B0D_HOUSEHOLD10_CUTOVER_RECOVERY_B1_B5_20261120.md`.

## B1 — Evidencia y brecha

- La captura y la restauración con archivo autenticado FBS2v6 usan el graph exacto de cinco stores con Reality10 y Auxiliary4, aportado por C3B1 y C3B2. El modo nuevo de Reality es una proyección SQL read-only.
- La ruta C3B exige que el candidato esté fuera del puntero. La reapertura de un target DEV ya seleccionado necesita admisión independiente por lease de host cerrado, puntero exacto, journal y generación, seguida de verificación de salud.

## B2 — Comparación y reutilización

- Conservar el modelo V5 de puntero creciente, lease y journal. P02 L15 registra las limitaciones de atomicidad de SmartCompra V4.4.
- Reutilizar la proyección read-only Reality10 y la admisión sellada de cinco stores, el patrón Finance9 DEV y los lectores de journal/replay-fence. `RealityStore.kt` queda fuera del permiso D1 porque ya contiene la proyección.

## B3 — Alternativas

- Elegida función interna de verificación de reapertura con puntero exacto y proyección cerrada dentro de la lease. Las aperturas públicas siguen independientes.
- Se descarta abrir el archivo activo con el puente de backups sin prueba de root cerrado, usar el opener Finance9 o cambiar el puntero durante preflight.

## B4 — Contrato y rutas

- Preflight solo lectura del stage canónico y distinto del activo. Verificar descriptor, marcador, los cinco archivos sellados sin aliases ni sidecars, versión Reality10 exacta, Finance9 y Auxiliary4, journal RESTORE VALIDATED único y ausencia de replay-fence del target.
- Reapertura DEV desde puntero ya publicado solo bajo comprobación de la lease de mantenimiento. Confirmar ID+secuencia antes y después de abrir graph y evaluar salud. No entregar graph ni puertos de escritura desde el verificador.
- Rutas exactas candidatas:
  - `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10DevCutoverPreflight.kt`
  - `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
  - `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household10DevCutoverPreflightTest.kt`
  - `.github/workflows/faro-v5-s4-w04-b0d1-household10-preflight.yml`

## B5 — Preauditoría adversarial

- Stage10 válido, rechazo de Stage9, companion incorrecto, pointer ABA, journal ajeno, replay-fence, aliases, sidecars y lease perdida.
- Verificar opener histórico rechazando 10, reapertura DEV leída con puntero exacto y fallo previo a apertura cuando cambia el puntero.
- D2 queda a cargo de activar, verificar pin del archivo, transicionar journal y demostrar rollback/recovery con pruebas JVM/XML propias.
