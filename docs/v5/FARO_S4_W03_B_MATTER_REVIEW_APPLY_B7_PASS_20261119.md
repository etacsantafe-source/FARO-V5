# FARO V5 — W03-B Asuntos Review/Apply · B7 PASS dirigido DEV

Fecha 2026-11-19. Unidad `S4_W03_B_MATTER_REVIEW_APPLY`. Commit funcional `4b959ac90f29548e79a74a0b594f0b2867c2c836`. **PASS_SCOPED_W03_B_DEV_JVM_8_OF_8**; no equivale a W03 GLOBAL, Android de usuario ni V2 productiva.

## B1
- Recuperados `AGENTS.md`, puntero, permiso previo W03-A BLOCKED, handoff W03, preauditoría general y B1–B5 específicos W03-B.
- W03-A ya B7 PASS 33/33, W01/W02 PASS DEV no repetidos.
- Documento W03-B B1–B5 `docs/v5/FARO_S4_W03_B_MATTER_REVIEW_APPLY_B1_B5_20261119.md`, blob `0800c42e8e2c4ea27fb76a91f2f6e9f2a1e91515`, commit `6b16acc7937206c9bd60a371cf379023d0900e6f`, guard `44544546037` SUCCESS.
- READY por exactamente cinco rutas tras guard del commit documental `f446f2b23e814b11def5cf5ee3e96479f14feef1` / `44544971726` SUCCESS. Writer CAS epoch1295 HELD durante B6, RELEASED antes de CI.

## B2
- Traducida la semántica de V4.4 de asuntos, estado y reversión a Matter V5. Reutilizados únicamente como referencia W02 Review/Apply y su grant sesión DEV (sin modificar W02), `MatterPersistencePort` de W03-A y un RealityStore.
- No borrado de historiales o receipts al deshacer, ninguna transferencia de dinero, ninguna operación sobre Reminder/Notice.

## B3
- SQLite existente como única autoridad de edición con revision CAS y BEGIN IMMEDIATE, patrón offline-first Android sin backend; eventos append-only como registros explícitos compensados, sin Event Sourcing externo.
- Alternativas descartadas: upsert desde formulario, reintento sin conciliación de receipt, tabla o SQLite adicionales, borrar historial para Undo, reutilización de código GPL/AGPL, permisos productivos ficticios.

## B4
- **Cinco rutas funcionales exactas, todas NUEVAS:** `MatterOperations.kt`, `MatterReviewBinding.kt`, `S4W03MatterReviewHost.kt`, `S4W03MatterReviewHostTest.kt` y `.github/workflows/faro-v5-s4-w03-b-matter-review.yml`.
- Contratos: N-031 `matter.create/update/lifecycle.set/snooze`; payload binario canónico limitado, intención con revision/timestamp; Create/Edit/Transition/Snooze más Undo compensatorio revisado (`undoOfSequence`) preserva eventos anteriores.
- Preparación en Workspace PENDING → aprobación humana → Apply bajo scope Matter, foreground/epoch/generación y política independiente. El binding revalida la revision del Reality owner en SQL antes del upsert. Apply/receipts y conciliación usan los componentes existentes `ReviewedConversationApplyCoordinator` y `ApplyRetryReceiptGate`, nunca upsert desde UI.
- Alcance únicamente JVM SQLite/DEV; no host productivo, Android G15 ni nuevas operaciones del catálogo.

## B5
- Ocho pruebas ejecutadas en la CI dirigida y XML comprobado: (1) alta/edición/receipts y doble apply, (2) Discard y reconcile-only no mutan, (3) ediciones stale / CAS, (4) denegación política/foreground/chat, (5) ARCHIVED→OPEN + Undo preserva historia, (6) Undo edición compensa creando evento nuevo, (7) payload canónico/trailing bytes, (8) snooze/transición inválida.
- **Límites no ocultos:** el conjunto 8/8 no simula caída real entre COMMIT y receipt ni una carrera entre dos procesos; tampoco valida Android, un Undo CREATE separado o un recorrido completo RESOLVED→OPEN. Esas verificaciones extra corresponden a aceptación integral posterior, no se declaran PASS. Permiso productivo independiente aún no implementado.

## B6
- Commit de código `4b959ac90f29548e79a74a0b594f0b2867c2c836` con `FARO-UNIT:S4_W03_B_MATTER_REVIEW_APPLY` en línea independiente. Únicamente cinco rutas del READY; no cambia W01/W02/W03-A, ni `main`.
- Guardia del código GitHub Actions run `44546591982`, **SUCCESS** mismo SHA.
- Preflight real de cinco rutas en job W03-B `135638680617`: SUCCESS.
- CI W03-B `44546592178` SHA `4b959ac90f29548e79a74a0b594f0b2867c2c836`: workflow y job **completed / success**. Gradle `:app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W03MatterReviewHostTest` SUCCESS, validación XML exacta `(tests=8,failures=0,errors=0,skipped=0)` SUCCESS.
- Artifact `w03-b-matter-review-xml`, id `14541013204`, GitHub digest `sha256:f86ac856505c93b876be970c8d0ac0106ae1c02cf65f7446039dc043ff14e759`, presente/no expirado; logs confirmaron `S4W03MatterReviewHostTest 8/8 PASS`.
- Guardia W03-B PASS no oculta workflow independiente G4 `44546592005` FAILURE **solo** en contador XML heredado: el `MatterModelTest` actual pasó **6/6** y el validador antiguo exige **4/4**. Build Gradle de ese run SUCCESS. Advertencia ya documentada en B7 W03-A, `G4_XML_FAILED matters (6, 0, 0, 0)`. Requiere permiso separado para arreglar workflow S3; no usar W03-B para tocarlo.

## B7
- **PASS_SCOPED_W03_B_DEV_JVM_8_OF_8**, basados en SHA/run/XML/guard/artefacto verificables. Notificación Android, CI global verde total, pruebas físicas y W03-G siguen NOT_RUN. No confundir con certificación global.
- Consumir READY a BLOCKED, registrar `last_code_review=PASS` con evidencia de este B7, siguiente W03-C recordatorio civil; writer se libera tras commit documental. Checkpoint definitivo condicionado a guard documental y writer remoto RELEASED.
