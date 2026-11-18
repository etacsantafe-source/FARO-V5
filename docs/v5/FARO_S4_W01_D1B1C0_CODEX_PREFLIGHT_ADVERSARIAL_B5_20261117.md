# FARO W01 D1B1C0 — examen adversarial B5 separado antes del código
Fecha 2026-11-17. Unidad `S4_W01_D1B1C0_FINANCE9_DEV_CUTOVER_PREFLIGHT`. Base `f01aa27de4a81ff68a8c5d6e60da2e51b21950f2`. Revisión de diseño del auditor, separada del implementador Codex; **no** revisión de código final ni certificación de producción.

## B5
1. **TOCTOU entre inspección y activación**: NO permitir que el resultado `OBSERVED_SEALED_STAGED9_ONLY` autorice `GenerationSwitchCoordinator` ni un graph ACTIVE; la futura operación tendrá que repetir pointer+sequence/journal/ACL/quiescence bajo exclusividad real. Si se pretende emitir `READY_TO_ACTIVATE`, RECHAZAR el diseño.
2. **ABA**: comparar `ActiveGenerationPointer` entero (ID y sequence) antes, durante y después de la inspección, no sólo generationId. Cambios al mismo ID con otra sequence DENIED.
3. **Journal y replay**: sólo una operación exacta RESTORE previamente VALIDATED, materialWritesAccepted=false; no tolerar PREPARING/STAGED/SWITCH_PREPARED por inferencia ni journal ajeno/pending múltiple. El preflight NO genera ni modifica journal, receipt ni replay fence.
4. **STAGED≠ACTIVE**: target distinto a activo; descriptor y path canónicos, V2 marker válido, 5 archivos existentes, Reality9 exacto y owners complementarios exactos; evitar abrir schema9 con opener histórico 7/8 y jamás promover porque `openValidatedV2Finance9Stage` devuelve graph.
5. **Readonly real y sidecars**: `StagedSqlitePreflight` evita SQLite writable; rechazar symlink, WAL/SHM/journal, schema desconocido, tablas ausentes, FK/integridad roja y señales inconsistentes. Si el helper existente no cubre uno, agregar test negativo o negar, no degradar checks.
6. **Quiescencia falsa/cross-process**: el puerto `MaterialWriteQuiescencePort` es in-process; este piloto no lo extiende a otros procesos ni certifica contención del host Android. Un resultado positivo de preflight es exclusivamente informativo; no equivale a sesión segura ni a permiso de Apply.
7. **Scope y costo**: Kotlin nuevo y tests nuevos en `:store:generation`, un workflow nuevo, EXACT3; sin Gradle global, sin app Android, sin biometría ni APIs pagas. Confirmar `AGENTS.md`, permiso READY y writer CAS; CI dirigida/XML del nuevo test y guard SHA real antes de B7. No reejecutar M0B o D1B1B para aparentar progreso.
8. **Interrupción**: forzar excepción en inspección o pointer cambio y comprobar no cambian activo, datos, journal ni stage en disco; sin rollback ficticio (no hubo mutación).

Dictamen de esta revisión: **GO a PREPARAR permiso scoped EXACT3 una vez que B1–B4 y este B5 queden en HEAD y pase guard documental; STOP a cualquier activación Finance9 o escritura SQLite**. No se afirma que un tercero externo haya hecho la auditoría ni que existan tests nuevos PASS.
