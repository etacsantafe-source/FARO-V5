# FARO V5 · S3.A4 N015 — Workspace history owner · B7 final independiente (05/11/2026)

## B7 AUDITORÍA FINAL INDEPENDIENTE

**Dictamen: PASS_SCOPED** únicamente para `S3_A4_N015_WORKSPACE_HISTORY_OWNER`, commit funcional `84f2040773e23520262da9595019b20b94532980`. **NO** acredita `history.query` federado, Home Actividad, ACL del host ni S3.A4 CLOSED. Esta subunidad elimina la dependencia de lectura cronológica real de Workspace, con fuente de tiempo explícita y sin SQL duplicada.

### Cadena auditada antes del código
- B1–B5 preauditoría independiente: `docs/s3-night/S3_A4_N015_WORKSPACE_HISTORY_B1_B5_20261105.md` blob `60b273764aa01f9ae6d0093f3b9dd72a6f06a5e4`, commit solo de documentación `9acd5a1e9ab1171e0a723e88f6983fb974f9e2bf`.
- Permiso `READY` limitado a **tres** rutas, commit documental `44495e9f54c12a71899a709b1f211dab807a037d`; guard [42147777302](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42147777302) SUCCESS. El commit código tiene `FARO-UNIT:S3_A4_N015_WORKSPACE_HISTORY_OWNER` en mensaje, sin archivo de permiso/estado mezclado.

### Delta exacto verificado
1. `v5/workspace/src/main/kotlin/ar/com/faro/v5/workspace/ConversationWorkspaceStore.kt`: nueva lectura `readHistoryPage`, datos desde **la tabla original** `workspace_message`, sin cambiar esquema ni crear store. Lee solo mensajes `USER ACCEPTED` y `FARO COMPLETED`; descarta `PROCESSING`, `FAILED`, `OFFLINE`, `PARTIAL` y drafts. Proyecta `messageId`, `conversationId`, evento y `created_ms` sin cuerpo del texto.
2. Filtros por fecha inclusiva, conversación y tipo; orden determinista `created_ms DESC, id ASC`, consulta SQL con bindings seguros, límite 1–100 y lookahead `limit+1`; `WorkspaceHistoryCursor` incluye revisión del dueño y rechaza cursor stale. `complete` expresa cobertura de esa página/owner; `globallyComplete=false` y `hostAdmissionRequired=true` nunca sugieren federación ni read ACL.
3. `v5/workspace/src/test/kotlin/ar/com/faro/v5/workspace/WorkspaceHistoryReadT2Test.kt`: 6 pruebas SQLite nuevas (vacío real; orden/tie/keyset; filtros; excluidos; cursores stale/inputs inválidos; archived+reopen/read-only).
4. `.github/workflows/faro-v5-s3-workspace-history-directed.yml`: job de test Workspace+JUnit XML exacto, scope de paths y solo `contents:read`, sin secrets. No se alteró `main`, V4.4, catálogo 172, V2, backup, schemas ni V2 shipping.

### Resultado de CI exacta por SHA
- [42148422350](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42148422350) protocolo seven-block **SUCCESS**, SHA `84f2040773e23520262da9595019b20b94532980`.
- [42148422395](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42148422395) CI N015 Workspace owner **SUCCESS**, SHA `84f2040773e23520262da9595019b20b94532980`. Log real: `BUILD SUCCESSFUL in 2m 44s`, `S3_N015_WORKSPACE_HISTORY_XML_PASS 6/6`, `failures=0 errors=0 skipped=0`.
- **Android físico NOT_RUN.** No certificar carga grande ni eficiencia con alta cardinalidad sin benchmark porque no se agregó índice por fecha. Tests JVM validan SQLite real (no emulador/G15). No se ejecutó regresión V2-core adicional para este SHA; no atribuirle su PASS histórico.

### Negativas y bloqueos residuales
- `created_ms` es **hora declarada del mensaje**, no hora real de COMMIT ni fecha de respuesta completa. Esta lectura no certifica el instante de Apply; `RealityStore` sí conserva un receipt time distinto, incluido `legacyUndated`.
- El store canónico **no** emite una credencial de read ACL ni prueba un ABA durable por sí solo: la UI no debe recibir el puerto directamente. El paso siguiente `S3_A4_N015_FEDERATED_HISTORY` debe conectar los propietarios **REALES** Workspace y Reality al host admitido, con generación/puntero/epoch por dueño, invalidaciones, orden de fuentes, paginación federada y cobertura incompleta. No presentar cero falso ni suprimir fuente faltante silenciosamente.
- N031 Pendientes todavía carece de owner combinado Matter+Reminder+MonthlyPlan con prioridades, fechas y admisión. A3 Guardados revisión acotada sin aprobación (+8 candidatas); A5 X01–X12 y campaña física pendientes.

**Estado tras B7:** S2 CLOSED 8/8 congelado. S3 A1/A2 CLOSED_AUTOMATED_SCOPE (2/5), entrega final integrada 0/5, A4 IN_PROGRESS, V2 productiva OFF, S4 OFF, Noche/Auto OFF, catálogo 172. **Pasar permiso a BLOCKED**, registrar último SHA y evidencia B7, actualizar `RUN_STATE.json` y puntero canónico en el mismo commit documental. Liberar escritor epoch 331 y validar guard. La próxima unidad requiere B1–B5 y READY independientes.
