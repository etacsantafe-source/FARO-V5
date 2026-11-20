# FARO V5 — W04 B0D2 · Activación, rollback y recuperación DEV

Fecha local 2026-11-20. Unidad `S4_W04_B0D2_HOUSEHOLD10_ACTIVATE_RECOVERY_DEV`. Delta al documento B0D. El scope de ejecución se limita a un root DEV cerrado e aislado y a la restauración autenticada verificada.

## B1 — Evidencia y brecha

- D1 aporta preflight de STAGED10 exacto y reapertura de verificación con puntero ID+secuencia, cinco stores y Household. C3B2 aporta restore real y pin durable por operación, target y cifrado.
- `GenerationSwitchCoordinator`, `ValidatedGenerationActivationCoordinator`, `FileBootstrapJournal` y `FileGenerationReplayFence` ya implementan transiciones crash-safe de puntero, journal, salud, fence y rollback. El owner Finance9 DEV usa esos componentes con una lease de root.
- El modo Household10 requiere un intent durable propio que vincule origen completo, destino, cifrado autenticado y huellas de los DB sellados.

## B2 — Comparación y reutilización

- P02 L15 registra V4.4 SmartCompra y sus límites de atomicidad. La fuente de verdad en V5 permanece en los cinco stores bajo coordinación de generación.
- Reutilizar el pin de archive del restore, el mismo archivo de exclusión DEV del root Finance9, la secuencia de puntero y el journal genérico. El owner de W04 no modifica el runtime W01 ni brinda autoridad de negocio.

## B3 — Alternativas

- Elegido seguir el protocolo de switch probado con un adaptador de admisión Household10 y ticket durable sin contraseña. En reingreso se verifican identidad, journal, full pointer y fingerprints de archivos.
- Quedan fuera la migración sobre ACTIVE, la recuperación basada en sólo ID de generación, el borrado de intent/journal al interrumpirse y la adopción de un archive o destino ajenos.

## B4 — Contrato y rutas

- Lease exclusiva de host DEV cerrado, confirmación explícita, journal PREPARING → STAGED, restauración exacta del archivo v6 sobre el target, intent de autorización durable, VALIDATED y switch con comprobación exacta de origen y target. Válido únicamente en continuación del mismo operation ID y archive SHA-256.
- Recuperación respeta transiciones previas al fence, después de pointer CAS y luego del fence. Verificar byte a byte las huellas de origen y destino cuando corresponda, y reabrir target10 por D1 bajo lease antes de aceptar salud. Tras fence, rechazar salud degradada sin rollback automático.
- Rutas exactas:
  - `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10DevCutoverOwner.kt`
  - `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10DevCutoverRuntime.kt`
  - `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household10DevCutoverRuntimeTest.kt`
  - `.github/workflows/faro-v5-s4-w04-b0d2-household10-cutover.yml`

## B5 — Preauditoría adversarial

- Integrar archivo FBS2v6 con Household, Finance9 y recibo Auxiliary4, activar DEV, comparar puntero y journal/fence y reabrir. Confirmar conservación del origen. Repetir operación sin segunda publicación.
- Inyectar interrupción antes de puntero, después de CAS, después de fence y fallo de salud que cause rollback antes del fence. Verificar remedio tras reinicio con nuevas instancias.
- Rechazar ausencia de grant humano, lease concurrente, sustitución de archivo, puntero ABA y cambios del target. Cierre B0 con XML de esta suite y evidencias B7 previas.
