# FARO V5 · W05-A1C0B3B · Ticket, journal y cutover SQL12 Evidence2 DEV

## B1 — Evidencia nueva

Aplica la preauditoría A1C0B3. A1C0B2 certificó FBS2v9 y replay íntegro SQL12 STAGED. A1C0B3A aporta la apertura ACTIVE read-only con WAL, grafo Evidence2 y fence que liga puntero completo al lease. A1C0B3A tiene B7 PASS en 15271b3b22e78f799418d68adcd2d8259491cca3, CI 45595910320, guardia 45595910224 y XML 19/19 verificado.

## B2 — Reutilización

Reusar la separación entre autorización, journal y activación de los cutovers SQL11 Evidence2 B7 C2B1B/C2C0. Usar `FileRestoreArchiveIdentity`, `FileBootstrapJournal`, `GenerationSwitchCoordinator`, `FileGenerationReplayFence` y `ValidatedGenerationActivationCoordinator`. Conservar V4.4 y las rutas históricas. Aplicar P02/P03 según el alcance existente.

## B3 — Delta de diseño

El ticket tiene formato propio SQL12, identidad de operación y generación, origen completo, destino canónico, hash del ciphertext original y huellas de los cinco archivos sellados. Preparar ticket bajo confirmación foreground, comparar semánticamente el STAGED SQL12 contra v9 autenticado con Evidence2 exacto y conservar el mismo pin del restore. El journal VALIDATED espera la confirmación de cambio de puntero. Recuperación automática local solo desde un SWITCH_PREPARED ya autorizado. Rechazar ticket SQL11/v8, cambio de SHA, UUID de generación o secuencia ABA. No ejecutar restore sobre ACTIVE ni usar el lector STAGED immutable sobre el archivo vivo. Validar salud sobre el grafo ACTIVE SQL12 de A1C0B3A con el lease existente y ofrecer reapertura posterior únicamente después de journal COMPLETED y fence de RESTORE. El lease de cutover usa la misma ruta DEV que el host. Tras fallo de salud, verificar el origen bajo puntero rollback de secuencia origin+2. El estado genérico de journal no concede escrituras de compras ni stock. [SQLite WAL](https://sqlite.org/wal.html), [SQLite transacciones](https://www.sqlite.org/lang_transaction.html).

## B4 — Rutas exactas

1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household12Evidence2DevJournaledCutover.kt`
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household12V9StagedReplay.kt`
3. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household12Evidence2DevJournaledCutoverTest.kt`
4. `.github/workflows/faro-v5-s4-w05-a1c0b3b-sql12-dev-cutover.yml`

La segunda ruta solo expone inspección del snapshot contra el ciphertext fijado, sin mutar datos. El ticket y la fábrica de reapertura validada permanecen dentro del nuevo cutover. Las cuatro rutas constituyen el alcance de esta unidad.

## B5 — Banco adversarial

Probar que VALIDATED espera foreground y que autorización revocada aborta. Probar retomar después de SWITCH_PREPARED y después del CAS sin reejecutar efectos, ticket ajeno o corrupto, evidencia cambiada, ABA, caída de salud con rollback comprobado, y reapertura real con compras/recibos preservados. Verificar el fence, los cinco owners, 0 fallos u omitidas, CI/XML por SHA y guardia. NO concede compra confirmada ni recepción. El alcance sigue DEV.
