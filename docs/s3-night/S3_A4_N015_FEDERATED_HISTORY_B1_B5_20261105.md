# FARO V5 — S3.A4 N015 Reality+Workspace federación temporal · B1–B5 (05/11/2026)

**Unidad:** `S3_A4_N015_TWO_OWNER_FEDERATED_HISTORY`. Baseline `1a5effa19132b32a2615eb50259e8f9e1a5840d9`, permiso `BLOCKED` heredado del B7 anterior. **B1–B5 VERIFICADOS SOLO PARA UN AGREGADOR DE LECTURA DE DOS DUEÑOS**, B6/B7 NOT_RUN. No es el cierre de N015 completo (Finance/Additional owners/host UI) ni de A4; sin activación V2 productiva ni API paga.

## B1 — fuentes reales y brecha de ejecución
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityReceiptHistoryPort.kt`: `RealityTimedReceiptHistoryReadPort` consulta commits canónicos con `recordedAtEpochMillis`, cursor `RealityTimedReceiptCursor` e indicador `legacyUndated`; origen `reality.execution_receipt`. El timestamp se registra dentro de la transacción SQLite de Reality esquema 7; no inventar fecha de legacy.
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5RealityTimedActivityReadSource.kt`: wrapper de Reality solo con generación/epoch/admisión real pre/post, pero **sin Workspace, mezcla ni conexión Home**. B7 independiente 6/6 aprobada en `S3_A1_N015_REALITY_ACTIVITY_ADMISSION_B7_PASS_20261105.md`.
- `v5/workspace/src/main/kotlin/ar/com/faro/v5/workspace/ConversationWorkspaceStore.kt`: ahora tiene `readHistoryPage(WorkspaceHistoryQuery)` verdadero SQL filtrado (B7 exacto `84f2040773e23520262da9595019b20b94532980`, 6/6 `#42148422395`). `created_ms` es **fecha declarada del mensaje**, no fecha de COMMIT/fin de respuesta; cursor con revisión de Workspace, `globallyComplete=false`, `hostAdmissionRequired=true`.
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5WorkspaceSearchReadSource.kt` y `FaroV5ActiveWorkspaceSearchGrantOwner.kt`: mecanismo de generación/puntero/epoch y grants de host ya ensayado para Search. **REUSAR CONCEPTO**, pero un permiso de Search no se reutiliza automáticamente para history.read; nunca inventar grants desde UI.
- **Brecha exacta:** no hay un readport que combine recibos Reality y eventos Workspace con orden global y deniegue la página completa si se revoca cualquier dueño durante la consulta. No basta sumar conteos (no equivale a historial).

## B2 — comparación con V4.4 y activos preservados
- La comparación V4.4 `docs/s3-night/S3_P00_CONTRACT_REVIEW_20261102.md` muestra gateway de historial de recibos y problemas de ocultar cobertura de fuentes; no demuestra una fusión multi-owner segura ni un reloj causal global. **NO importar V4** ni su UI directamente.
- En V5, ambos readports son reales y mantienen autoridad separada. No escribir eventos en store paralelo ni formar nuevos receipts al leer; no transformar `workspace.message.created_ms` en `Reality.commitRecordedAt`. La intercalación es solo un orden de presentación, no garantía causal.
- `FaroV5ActiveWorkspaceSearchGrantOwner` asegura epochs únicas tras revocación/regrant; el futuro host de N015 debe emitir permisos independientes y comparar puntero activo A→B→A. El agregador solo recibe callbacks del host y falla cerrado en ausencia de ellos.

## B3 — investigación externa y alternativas contrastadas
1. ActivityWatch, proyecto real y multi-bucket event timeline https://github.com/ActivityWatch/activitywatch y https://github.com/ActivityWatch/docs/blob/master/src/buckets-and-events.rst — eventos mantienen bucket/origen; **ADAPTAR** provenance tipada, **DESCARTAR** copia de servidor/collector por dependencia, API y licencias ajenas.
2. SQLite official keyset pagination https://www.sqlite.org/rowvalue.html — origen con cursores propios y tie-break estable, evita OFFSET; **ADAPTAR** cursor por dueño y merge por timestamp+source+ID en memoria; cursor nunca se rellena desde un ID aleatorio.
3. Android Room invalidation tracker https://developer.android.com/reference/androidx/room/InvalidationTracker — eventos Flow no constituyen permiso ni fence de generación; **REFERENCIA**, **DESCARTAR** como reemplazo de revalidación antes/después.
4. OWASP MASVS-PRIVACY-1 https://mas.owasp.org/MASVS/controls/MASVS-PRIVACY-1/ — minimización y acceso a datos de cada dueño; **USAR** solo IDs opacos/tipos/sellos de tiempo, sin texto, sin red y con grants separadas.
- A: producir datos ficticios de UI para una fuente ausente (DESCARTAR).
- B: tomar snapshots sin epoch y filtrar después (DESCARTAR TOCTOU, regrant ABA).
- C: un nuevo historial persistente (DESCARTAR: duplicaría Truth y backup).
- D: leer dos fuentes canónicas con grants y puntero **del HOST**, hacer merge determinista bounded con cursores por fuente y revocación pre/post (**USAR/ADAPTAR**).

## B4 — delta acotado y contrato de entrega
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5FederatedHistoryReadSource.kt` nuevo readport integrado **SIN UI**: recibe read-port Reality y Workspace de dueños reales, expected/current generation, `pinnedPointerCurrent`, grants independientes Reality y Workspace y sus epochs; NO genera grants. Si un dueño falta/revoca/cambia puntero o acl, devuelve `null` antes de leer o descarta la página completa después de leer.
2. Resultado solo `kind/source/opaqueId/atEpochMillis/timeProvenance`, orden estable `atEpochMillis DESC`, desempate tipo de fuente y ID. Paginación por **dos** cursores de owner y su epoch, tope 1..100, `hasMore` explícito; `legacyUndated`, `fullyFederated=false`, `completeWithinSources` diferenciado de cobertura global. Sin texto Workspace ni detalles de plan, salida View Only. No fecha causal única: etiquetas de provenance Reality y Workspace distintas. Fecha filtro y navegación/host quedan en siguiente integración; esta unidad solo valida intercalación/seguridad.
3. `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5FederatedHistoryReadSourceTest.kt`: tests negativos por grant ausente/ephemeral ABA, revocación a mitad, cursor stale, lecturas fallidas, parcial/legacy y orden/ties/paginación inter-source; sin usar V2 productiva.
4. `.github/workflows/faro-v5-s3-federated-history-directed.yml`: CI de app en rama S3, solo tests N015 6/6 XML exacto, compilar. Guard seven-block y CI regresiones existentes se verifican por SHA; no nuevos secretos/permisos ni workflows irrelevantes.

## B5 — auditoría adversarial pre-código
- **Integridad y privacidad:** no se autorizan escritura de notices, Reality, Workspace ni modificación de planes. `ExecutionReceipt` COMMITTED solo proviene de Reality; `WorkspaceHistoryEvent` solo de mensajes aceptados/completados. Jamás enviar texto/ID a IA, ni marcar avisos vistos ni inventar atención.
- **Autoridad:** all-or-nothing cuando falta Reality o Workspace; `null` no significa cero. Ambos epochs NO vacíos, generación esperada y puntero durable actual; revalidar antes de acceder, después de cada lectura y antes del retorno. A→B→A con ID igual invalida lectura. Si falla un dueño a mitad, descartado todo, sin mezcla.
- **Cursor:** ligado a las dos épocas/identidad de generación. Cada fuente adelanta hasta su **último evento realmente consumido** en la página fusionada; no perder los del otro dueño cuando el límite corta la mezcla. Revisiones stale Workspace deben hacer STOP, no volver a página1 sin aviso. Empates ordenados por `source` y `opaqueId`.
- **Cobertura:** `completeWithinSources` describe solo estas dos fuentes; `fullyFederated` queda **false** aunque estén vacías, y `legacyUndated` se conserva. No montar contador Inicio ni decir que Finance/Additional owners están integrados.
- **Host/recursos:** aunque este adaptador recibe callbacks de host, **NO monta ni emite permisos** ni activa V2. Probar comportamiento con grants simuladas no certifica fuente de permisos real; falta admisión del host verdadero y UI en unidad posterior, además de performance en dispositivo.
- **B5 dictamen:** APTO SOLO PARA EMITIR READY EN RUTAS EXACTAS y ejecutar CI/B7 de **federador de dos fuentes**. No N015 full, A4 CLOSED, S3 FULL ni pruebas físicas. Si falla seguridad/cursor/compilación, B7 RED_FIX y reparar la misma unidad.

**Salida:** B1–B5 PREAUDIT_VERIFIED_SCOPED, permiso actual BLOCKED hasta un nuevo commit documental `READY`; ningún B6 antes de green guard.
