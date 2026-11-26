# FARO V5 · W05-A2C0C1A · SQL15 five-store full semantic sealed readback B1–B5

2026-11-26, Argentina. Repo `etacsantafe-source/FARO-V5`, branch `work/s3-night-20261102`. Baseline `9a0ee69288001da2bac0067f547a41f2a5630cca`. Unit `S4_W05_A2C0C1A_SQL15_FIVESTORE_SEMANTIC_SEALED_DEV`.

**Preauditoría, NO permiso para B6; no autoriza SQL15 ACTIVE, cutover, stock.consume o restauración productiva.** Divide A2C0C1 en A2C0C1A (igualdad semántica completa de cinco stores en SQL15 STAGED) y A2C0C1B (host DEV ACTIVE estrictamente READ_ONLY). A2C0C2 (cutover journaled) sigue además separado y BLOQUEADO hasta que ambos obtengan B7 independiente.

## B1 — Evidencia vigente y fallo de cobertura

- A2B completo 9/9 B7 PASS; A2C0A schema SQL15, A2C0B1 FBS2v12, A2C0B2 SQL15 rows, A2C0B3 replay 5-store, A2C0C0 precutover READ_ONLY certificados y prohibido repetirlos. A2C0C0 checkpoint `9a0ee69288001da2bac0067f547a41f2a5630cca`, CI 45883689940 SUCCESS original XML 33/33, guard 45884522318 SUCCESS, writer RELEASED epoch 2124.
- `FaroV5Household15PreCutoverReadback.verifyOnly` comprueba archivo FBS2v12 autenticado/pinned y cinco SQLite (version/tables/integrity), igualdad exacta de ledger + historia SQL15 y huellas antes/después. PERO no compara semanticamente Workspace, Auxiliary, Commercial, Web y el resto de Reality con todas las secciones del backup.
- El lector semántico anterior sí existe: `FaroV5Household13V10StagedReplay.observeForVerification/capture` con `FaroV5FiveStoreBackupCapture`, Workspace/Auxiliary/Commercial/Web/Reality FBS2 y comparación v11. Su abertura `FaroV5GenerationStoreGraph.openValidatedHousehold14ArchiveStage` y el `RealityStore` sellado aún exigen SQL14 exacto. El contract `FaroV5Household15Fbs2Snapshot.base.base` es la misma sección v10 de cinco owners. Extender opt-in a **SQL15 STAGED**, sin tocar la ruta SQL14.

## B2 — Reutilización de contratos y comparación

- Reusar cinco owners canónicos, no introducir tablas paralelas/snapshot ad-hoc ni un `RealityStore` write owner nuevo. `FaroV5FiveStoreBackupCapture.captureReceiptProjection` ya obtiene los snapshots exactos con quiescence y valida Workspace, Reality G4/Planning, Evidence2, Web y Auxiliary4; `FaroV5Household13V10StagedReplay.capture` ya reimpone manifiesto fuente de respaldo, Finance9, Needs10 y historia de compra/Undo.
- En modo SQL15 acotado, el objeto `FaroV5GenerationStoreGraph` debe validar EXACTAMENTE SQL15, mantener 5 stores/NOFOLLOW/no sidecars, abrir `RealityStore(householdStage15ReadOnly=true)` con `immutable=1` exclusivamente por ser stage sellado, y no ofrecer ningún `householdActive15DevReadOnly` ni persistencia.
- `RealityFinance9SealedReadPort.readTrustedSealedStage15`, `RealityHousehold11NeedsRowsPort.readTrustedSealedStageFifteen` y `RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen` conservan todo el contrato tipado y forma de datos; viejos 12/13/14 y DEFAULT siguen exactos.
- Comparación V4.4 conceptual: reutilizar su UX de Inventario/Compras en el futuro, pero V4.4 no es un atajo de restauración/autenticación SQL15; el gateway nuevo pertenece a V5. No tocar V4.4 ni políticas.

## B3 — Alternativas y normas de operación

- https://www.sqlite.org/wal.html: `immutable=1` sólo con stage sellado, nunca ACTIVE. https://www.sqlite.org/pragma.html: `quick_check`/FK necesarios pero no suficientes para semántica; `user_version` debe ser SQL15 exacto, no falsificar. https://www.sqlite.org/lang_transaction.html: no iniciar escrituras en una lectura bajo lease exclusiva del origen.
- Rechazado: reconstruir los cuatro stores copiando bytes de un v11 temporal, cambiar SQL15 a SQL14 sin migración certificada, reseal FBS2v12 como v11 (pierde pin), limitarse a hashes del archivo, cambiar puntero antes del host, usar una compra o receta para inferir consumo, invocar `restoreOrVerify` sobre ACTIVE.
- **Opción aprobada:** reutilizar el snapshot semántico v10 de los cinco owners con una nueva ruta exacta `stageFifteen=true` que sólo lee, extrae 1:1 y compara con `expected.base.base` del archivo v12 autenticado. Keep pin/fingerprint/closed DEV lease de C0.

## B4 — Ruta de código, límites y aceptación

- `FaroV5Household13V10StagedReplay.observeForVerification` admite únicamente el opt-in interno `stageFifteen=true` y `capture` selecciona Graph STAGED 15, Finance9SealedReadPort 15, Needs11 15, SQL15 consumption historical, sin mutar el replay v10/v11/v12 certificado.
- `FaroV5GenerationStoreGraph` añadir `openValidatedHousehold15ArchiveStage` y `requireSealedHousehold15ArchiveStage` con opciones particulares para SQL15; cruzar solo los perfiles exactos de `householdStage15` y `RealityStore`. No registrar capability, no exponer ACTIVE15, no enganchar Surface, no agregar Aplicar/Undo.
- `RealityStore` rama STAGED15 READONLY/immutable+NOFOLLOW en constructor e init, valida SQL15 mediante `RealityHousehold15ConsumptionRowsPort.captureConnection`, `validateExistingFinanceLayout(15)`, `isPlanningBackupSchemaMounted` con opt-in explícito. Mantener las protecciones SQL14 y otros modos en presencia de flags incompatibles.
- `FaroV5Household15PreCutoverReadback.verifyOnly` DESPUÉS de checks C0 calcula `expected.base.base` contra el snapshot semántico exacto de todos los owners y verifica que tanto stage como pointer, bytes y pin sigan idénticos. El objeto `EvidenceOnly` no es ticket/grant y no debe incorporar un selector ACTIVE.
- Privacidad/seguridad: semantic snapshots en memoria, no logs de datos ni ciphertext; límites del codec original (bounded) y de los puertos; no API paga, no dinero ni stock real.
- EXACT8 rutas propuestas para B6:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9SealedReadPort.kt`
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold11NeedsRowsPort.kt`
3. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
4. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
5. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household13V10StagedReplay.kt`
6. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household15PreCutoverReadback.kt`
7. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household15V12StagedReplayTest.kt`
8. `.github/workflows/faro-v5-s4-w05-a2c0c1a-sql15-fivestore-semantic-readback.yml`
- C1B independiente tras B7 A: lectura DEV ACTIVE15 WAL-aware, con real pointer y ticket **a futuro**, no intentar cutover C2 hasta C1B B7 y pruebas rollback/ABA.

## B5 — Matriz adversarial B6 + XML

1. Integración de positivo FBS2v12 sellado, stage SQL15, cinco stores semanticamente idénticos; readback repetible sin escribir bytes/crear sidecars ni mover pointer.
2. Mutación EXTRA de `finance_budget_limit` que mantenga SQLite íntegro y SQL15 ledger idéntico debe fallar C1A. Igual mutación de Web/Commercial/Auxiliary/Workspace con SQLite íntegro debe fallar al menos una prueba distinta, probando que C0 no alcanzaba.
3. Pérdida de `Workspace` de respaldo, Evidence2 terms, Finance9/Planning, Auxiliary4 receipts, Web2 evidence: comparación de todo snapshot, nunca éxito parcial.
4. Stage SQL14/incorrect version, FK/quick_check, faltan tablas/triggers y sidecar => no abrir Graph. Pointer ABA, source==target, revocación de `MaterialWriteQuiescencePort`, passphrase errónea, archivo v11/reseal y pin mismatch => rechazo.
5. Suite A2C0C0 expandida (13 previos + regresiones C1A), regresión `FaroV5Household14V11StagedReplayTest` 5/5, `FaroV5Household15Fbs2CodecTest` 8/8 y `RealityHousehold15ConsumptionRowsPortTest` 7/7; original JUnit XML toda test suite sin failures/errors/skipped y unique testcase names.
6. `seven-block-evidence` del SHA functional SUCCESS, CI dirigida real por SHA SUCCESS, XML original inspeccionado en CI; B7 documental PASS o RED_FIX conforme a log. Históricos workflows viejos pueden fallar preflight, anotar separadamente.
7. **NINGÚN** `GenerationSwitchCoordinator`, ticket ni cambio de pointer en esta unidad. V2 productiva, stock real, pagos, Auto/Noche OFF; G15 NOT_RUN, main/V4.4 intactos.

**Gate:** commit sólo preauditoría B1–B5, soltar writer, guard; permiso READY exact8 sólo en docs, soltar writer y guard; código `FARO-UNIT:S4_W05_A2C0C1A_SQL15_FIVESTORE_SEMANTIC_SEALED_DEV`, Gradle, XML, guard; B7 o RED_FIX con evidencia y checkpoint, RELEASED. C1B/C2 no heredan permiso.
