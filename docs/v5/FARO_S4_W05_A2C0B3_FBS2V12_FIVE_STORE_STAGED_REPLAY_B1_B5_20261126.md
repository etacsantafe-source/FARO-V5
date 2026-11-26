# FARO V5 — W05-A2C0B3 FBS2v12 five-store SQL15 STAGED replay · B1–B5

Fecha: 2026-11-26 Argentina. Rama `work/s3-night-20261102`, base `d49cd2d0d5ad4cf8514b58aab279cad8f14ba3ae`. Unidad independiente `S4_W05_A2C0B3_AUTH_FBS2V12_FIVESTORE_SQL15_STAGED_REPLAY_DEV`. **Este documento es preauditoría, no permiso de B6 ni certificación de cutover.**

## B1 — Evidencia, contratos previos y brecha

- W05 A2B CLOSED DEV 9/9; A2C0A SQL15 schema STAGED B7 7/7; A2C0B1 FBS2v12 tipado y autenticado 32/32; A2C0B2 SQL15 rows STAGED 20/20, checkpoint `d49cd2d0d5ad4cf8514b58aab279cad8f14ba3ae`, guardia 45879903993 SUCCESS y writer RELEASED 2114. No rehacer estas unidades.
- El codec `FaroV5Household15Fbs2Codec` empaqueta la sección FBS2v11 idéntica y eventos de consumo. `FaroV5FiveStorePortableBackupCrypto.openWithHouseholdConsumption15` verifica autenticación FAROBAK2. `RealityHousehold15ConsumptionRowsPort.restoreOrVerifyTrustedSealedStageFifteen` aplica filas únicamente a fichero SQL15 STAGED sellado y readback; SQL15 no está ACTIVE.
- Ya existe `FaroV5Household14V11StagedReplay` para reconstruir cinco stores en SQL14 usando `AuthenticatedHousehold13RestoreArchive` + pinning del ciphertext. **Brecha**: v11 rechaza versiones superiores y no puede abrir ciphertext v12. No re-cifrar el archivo v12 ni falsificar la versión: reutilizar el replay v11 tomando un **proyector interno tipado autenticado v12** con los mismos bytes de archivo original para el pin.
- `FaroV5Household12PurchaseSql12Port.requireSealedStage` garantiza generación STAGED, pointer original, identidad de ruta, WAL-format, nlink=1, ausencia de sidecars y DEV host exclusivo. Nunca crear ACTIVE nuevo.

## B2 — Reutilización vs alternativas

- **REUTILIZAR** todas las capas FBS2v8→v11, SQL9→14, manifiesto cinco stores, archive identity con pin SHA, quiescence, control de fuente≠destino, y readback. No recrear esos owners, migraciones o cifrado.
- **AMPLIAR ACOTADAMENTE** un factory `AuthenticatedHousehold13RestoreArchive.openV12Projection` tras `openWithHouseholdConsumption15`, que devuelve `AuthenticatedHousehold13RestoreArchive(ciphertextV12, v12.base.base)` + snapshot v12. Rechaza archives mal autenticados, v11/v10 cruzados, tamaños. Gestiona borrado de copia cifrada cuando hay rechazo.
- **AMPLIAR ACOTADAMENTE** replay v11 con método `restoreVerifiedV12BaseToFourteen`: recibe la proyección ya autenticada y snapshot v14, reutiliza exactamente `restoreVerified` actual bajo la misma exclusividad, la misma identidad de archivo y los mismos bytes cifrados. No cambia el flujo público v11.
- **NUEVO** replay v12 orquestador SQL9–14→SQL14 verificado→SQL15 schema→event rows; en SQL15 retries verifica pin, historia 1:1 y filas. SQL15 no se acepta como base para volver a ejecutar replay v11; se reanuda sólo el paso SQL15 ya preparado. Sin nuevo selector global.
- Revisión de garantías: la revalidación de cinco stores completa antes de SQL15 la hace v11; el paso SQL15 únicamente cambia Reality y fuerza archivo/directorio; la **aceptación ACTIVE / restauración global con revalidación de todos los stores en SQL15** queda explícitamente reservada a A2C0C. No confundir B7 STAGED con certificado de cutover.

## B3 — Fuentes y decisiones externas

- SQLite transactions https://www.sqlite.org/lang_transaction.html: `BEGIN IMMEDIATE` bloquea escritores concurrentes; preferir lease exterior y recuperación idempotente.
- SQLite WAL https://www.sqlite.org/wal.html: persistir FULL, checkpoint TRUNCATE y verificación antes de admitir stage sellado; si checkpoint falla tras COMMIT, la recuperación consulta estado confirmado, no deshace un commit.
- SQLite STRICT https://www.sqlite.org/stricttables.html: tabla de eventos tipada; los saldos deben verificarse fuera del esquema; no se confía en una simple constraint.
- SQLite FK https://www.sqlite.org/foreignkeys.html: activar FK por conexión y validar referencias lógicas con validadores FARO. NO nuevas dependencias ni APIs, no copiar Grocy/ERP.
- **RECHAZAR** cambiar SQL15 a SQL14 temporalmente, re-sellar v12 como v11 para sortear la autenticación/pin, restaurar en origen o ACTIVE, cambiar puntero de generación aquí, activar stock por evento de carrito o pagar dinero real.

## B4 — Diseño, fases y permiso futuro

- Nuevo `FaroV5Household15V12StagedReplay`: `restoreAuthenticatedV12(operation,stage,origin,archive,passphrase)`, `Household15StagePhase` para inyección de interrupciones, MaterialWriteQuiescencePort alrededor de toda la operación, resultado `FiveStoreStagedRestoreResult.StagedVerified` o `Blocked`. Fuente manifiesto diferente de stage. Archivo auténtico v12, no dos archivos con el mismo nombre.
- Apertura con factory autenticado (ciphertext copia original, sin re-sell); invocar replay v11 sólo cuando target SQL9..14. Verificar stage completo SQL14; en SQL15 reintento, exigir mismo pin y comprobar snapshot histórico SQL14 codificado a `RealityHousehold14ReservationRows`, consumos a `RealityHousehold15ConsumptionRows`. Versiones fuera de [9,15] -> STOP.
- `RealityHousehold15StagedFilePort.migrateTrustedStagedFourteen` con fence de logical stage y archivo original, fuerza file+directory; luego rows.restore/verify, fuerza y recaptura. Nunca reabrir versión 15 con API de migración que sólo admite ledger vacío en retry; usar `RealityHousehold15ConsumptionRowsPort` para validar esquema e idempotencia.
- Interrupciones entre replay base, upgrade SQL15, y commit de ledger: archive pin original, no nuevo Apply, recuperación con el mismo operation id y bytes; segundo ciphertext diferente con mismo contenido debe ser rechazado por pin.
- Conversión tipada **completa** de v12 base: receipts + purchases + receiveUndos + reservations + consume rows a DTO de SQL15 para comparación exacta. Verificar historia no sólo consumos; una base SQL15 con otros valores no se sobreescribe.
- Dev host exclusivo, pointer unchanged, no archivos nuevos fuera stage ni writes de Finance productivo. No exponer método cutover ni ajustar `GenerationStoreGraph` ACTIVE.
- B6 propuesto: **exact5 rutas**:
  1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household13V10StagedReplay.kt`
  2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household14V11StagedReplay.kt`
  3. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household15V12StagedReplay.kt`
  4. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household15V12StagedReplayTest.kt`
  5. `.github/workflows/faro-v5-s4-w05-a2c0b3-fbs2v12-staged-replay.yml`
- Siguientes etapas separadas A2C0C SQL15 journaled cutover, A2C1 Review/Apply de stock.consume, A2D UX, A2E aceptación global W05-A2; cero herencia de permisos.

## B5 — Adversarial y JUnit exigidos

1. Archivo autenticado FBS2v12 y evidencia2: cinco stores de origen, restaura base SQL14 y ledger SQL15 únicamente en stage independiente; conserva otros owners/manifest y puntero.
2. Snapshot con compra10000, recibido10000, deshacer recibido7000, reserva2000/libera1000, consume1000 y CONSUME_UNDO500; balance 2500 reservado1000, retry misma identidad `StagedVerified` sin filas duplicadas.
3. Wrong passphrase, ciphertext corrupto, archivo v11/v10 utilizado como v12 => `INVALID_ARCHIVE`, etapa y pointer intactos.
4. ID de archivo FBS2v12 distinto para el mismo snapshot durante retry => `ARCHIVE_IDENTITY_CONFLICT`, sin segundo replay.
5. Prohibir source=target; pointer ABA y stage extraña, sidecars y lease revocada => bloqueo, nunca escrituras fuera de STAGED.
6. Interrupción después de base SQL14, después de migración SQL15, antes del commit de consume: mismo ciphertext reanuda; cambio de ciphertext rechazado.
7. Desajuste de historia SQL15 y ledger distinto no vacío => STOP sin borrar ni modificar, sólo ALREADY_IDENTICAL en mismo stage.
8. Invalid Undo, exceso de consumo/reserva, UTF-8 inválido o duplicated revision => ya bloqueados por codec+SQL15 RowsPort B7, añadir regresión puntual v12 para cadena.
9. Regresión dirigida `FaroV5Household14V11StagedReplayTest` 6/6 y nueva `FaroV5Household15V12StagedReplayTest`, además del codec v12 8/8 y rows SQL15 7/7 si el módulo lo permite; ORIGINAL XML failures=errors=skipped=0; guard seven-block-evidence SUCCESS.
10. A2C0B3 termina en stage SQL15 inactivo. Sin puntero nuevo, sin ACTIVE, `stock.consume`, pagos, V2 productiva, Auto/Noche, APIs pagas, ni instalación G15.

**GATE SEPARADO:** publicar B1–B5 documentales con CAS writer, liberar writer, verificar guard documental SUCCESS, emitir READY exact5 en commit exclusivamente `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json` con blob proof de cinco secciones, obtener guard/preflight PASS; sólo entonces B6 commit código `FARO-UNIT:S4_W05_A2C0B3_AUTH_FBS2V12_FIVESTORE_SQL15_STAGED_REPLAY_DEV`, CI real del SHA/gradle/XML; B7 y consumo permiso, checkpoint, writer RELEASED. Si RED, RED_FIX sólo unidad propia, sin violar gate ni declarar falso PASS.
