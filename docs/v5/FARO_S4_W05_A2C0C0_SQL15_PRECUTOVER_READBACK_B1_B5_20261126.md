# FARO V5 — W05-A2C0C0 · SQL15 pre-cutover sealed READ_ONLY evidence · B1–B5

Fecha: 2026-11-26 (Argentina). Rama: `work/s3-night-20261102`, baseline `1bdb2b528af02d45acfb67f3d8380058482f18c6`.
Unidad acotada: `S4_W05_A2C0C0_SQL15_PRECUTOVER_SEALED_READBACK_DEV`. **Este documento NO es permiso B6, ticket de cutover, ni una habilitación de SQL15 ACTIVE.** A2C0C global se divide para no saltar la precondición de lectura SQL15.

## B1 — Hechos reales y brecha no certificada

- A2B CLOSED 9/9; A2C0A SQL15 schema, A2C0B1 codec v12, A2C0B2 SQL15 rows y A2C0B3 replay cinco stores **B7 PASS** acotados, último checkpoint `1bdb2b528af02d45acfb67f3d8380058482f18c6`. No repetir B6/B7 de esas unidades.
- `FaroV5Household15V12StagedReplay` deja SQL15 **STAGED** y prueba readback del libro; `RealityHousehold15ConsumptionRowsPort` valida cronología/reservas y detiene sobreconsumo. Ninguna activa stock.
- `FaroV5Household14Evidence2DevJournaledCutover` tiene tickets/recovery que exigen fuente SQL13 y destino SQL14; **no** admitir SQL15 cambiando 14 a 15 mecánicamente. El grafo `FaroV5GenerationStoreGraph`, el `RealityStore` ACTIVE dev y su perfil estricto aún reconocen **hasta SQL14**, no existe read host SQL15 ACTIVE validado.
- La brecha real es doble: (1) **readback pre-cutover SQL15 sin escrituras** que ate stage, bytes FBS2v12 originales, filas/historia y cinco SQLite; (2) ulterior validador semántico completo para todos los owners en SQL15 y sesión de lectura ACTIVE antes de CAS. La unidad C0 resuelve sólo (1); (2) se mantiene BLOCKED hasta otra preauditoría y evidencia.
- Prohibido saltar la fase de certificación y seleccionar un SQL15 donde ningún host de lectura saludable existe; riesgo de pérdida de acceso y rollback inseguro.

## B2 — Reutilización y frontera

- **Reutilizar** `FileRestoreArchiveIdentity.requireExistingPinnedArchive`, `FaroV5Household12PurchaseSql12Port.requireSealedStage` (5 SQLite canonical/no sidecars/pointer/grant), `FaroV5FiveStorePortableBackupCrypto.openWithHouseholdConsumption15` autenticado, `RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen`, y `StagedSqlitePreflight.requireAll` (read-only immutable solamente en stage inactivo).
- En `FaroV5Household15V12StagedReplay.kt` exportar helper interno **sólo de transformación tipada** `expectedSqlFifteenRows(snapshot)`, reutilizando exactamente su mapper histórico (purchases, receipts, compensations, reservations, consumptions) sin copiarlo ni cambiar replay. La admisión debe rechazar desajuste de **toda** la historia, no sólo cola de consumos.
- Archivo nuevo `FaroV5Household15PreCutoverReadback.kt`: función `verifyOnly(...): Evidence` con quiescence/closed DEV host/identity pin/version 15 exacto/5-store integrity/readback SQL15. `Evidence` es **diagnóstico sin permisos**, no fuente autorizada para conmutar puntero, no ticket, no escritura en base.
- Tests en fixture aislado ya existente `FaroV5Household15V12StagedReplayTest.kt`; no duplicar fixtures de A2C0B3. Un workflow nuevo dirigido verifica ruta exact4, JUnit XML y regresiones.
- No integrar todavía `FaroV5GenerationStoreGraph`, `RealityStore` ACTIVE, cutover ni UI. No agregar botones/operaciones públicas.

## B3 — Investigación concreta / decisiones independientes

- SQLite `BEGIN IMMEDIATE`: https://www.sqlite.org/lang_transaction.html — los controles de escritor/lease pertenecen a generación material, mientras la admisión de C0 sólo lee etapa quiesced.
- SQLite WAL y checkpoint: https://www.sqlite.org/wal.html — `immutable=1` exclusivamente para stage sellado sin WAL; un ACTIVE debe leerse mediante conexión WAL ordinaria después de la fase de selección y recuperación (NO en C0).
- SQLite PRAGMA `quick_check` y `foreign_key_check`: https://www.sqlite.org/pragma.html — verificar las cinco bases y schema version/tables; no reemplaza validación de contrato tipado.
- Se descartó re-sellar v12 como v11, cambiar versión SQL, inventar el read host ACTIVE, fingir un ticket SQL14 válido para SQL15 o utilizar checksums aislados como demostración de identidad semántica cinco-store.

## B4 — Contrato acotado C0 y secuencia A2C0C

- En exclusiva DEV y `MaterialWriteQuiescencePort`, exigir `pointer.read()==origin`, stage real distinto de origen, cinco ficheros regulares, ausencia sidecars, archivo original que ya estuvo PINNED para la MISMA operación, manifest source distinto de target.
- Autenticar FAROBAK2 + codec v12. Exigir Reality user_version=15 exacto, tablas SQL15 y tabla claves de los cuatro stores restantes: Workspace4, Auxiliary4, Web2, CommercialEvidence2. `StagedSqlitePreflight.requireAll` read-only con `quick_check` y FK. `RealityHousehold15ConsumptionRowsPort` relee SQL15 y comparará con **toda** la historia y consumos tipados desde backup. Exigir que el pointer permanezca idéntico después; verificar fingerprint de archivos antes/después si es viable en DEV test.
- Devolver sólo `Evidence` con source ID, stage ID, hash del ciphertext; **NO** devolver ticket autorizado ni habilitar CAS, `Apply` o Android. No usar la evidencia C0 como sustituto de autenticación del cutover posterior.
- **C1 separado**: certificación de **semántica completa de los cinco stores en SQL15**, perfil y lectura ACTIVE SQL15 de solo lectura, sin escrituras; fixtures negativos. **C2 separado**: sólo después, journaled cutover SQL14→15 con ticket durable, foreground doble, source SQL14 completed, fault injection, health/reopen del target y rollback/ABA. Si C1 no está probado, C2 STOP.
- Ruta B6 exact4:
  1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household15V12StagedReplay.kt` (helper interno sin cambio semántico)
  2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household15PreCutoverReadback.kt` (nuevo, sólo lectura)
  3. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household15V12StagedReplayTest.kt` (regresiones negativas/positivas C0 en fixtures)
  4. `.github/workflows/faro-v5-s4-w05-a2c0c0-sql15-precutover-readback.yml` (preflight/Gradle/XML)
- Workflows históricos auto-disparados por archivos compartidos pueden fallar en su preflight de permiso antiguo: registrar separadamente; CI dirigida nueva y `seven-block-evidence` obligatorios. No modificar CI histórica fuera de su scope.

## B5 — Casos de ataque que deben pasar antes de B7

1. SQL15 en stage de cinco stores después de replay auténtico v12; leer sin cambiar byte alguno, sin sidecars y sin mover puntero. Manifiesto fuente!=target, pin original.
2. Wrong passphrase, archive corrupto/v11 cruzado y ciphertext v12 re-sellado con el mismo contenido -> rechazo sin efectos.
3. SQL14, versión15 con schema/tabla faltante, pointer ABA, target==source, sidecars `-wal`, symlink -> rechazo.
4. Alterar consumo o reserva después del replay -> rechazo exacto de las filas, sin reescribir.
5. Comprobar que `verifyOnly` no retorna token de activación y no utiliza `GenerationSwitchCoordinator`, `FileBootstrapJournal` o `RealmStore` ACTIVE.
6. Ejecutar suite vieja FBS2v12 replay + nueva C0 + v11, y dirigida SQL15 rows si posible. Tests JUnit XML original, sin skips/failures/errors, Gradle SUCCESS; CI guard `seven-block-evidence` SUCCESS.
7. **NO declarar C0 como cutover** aunque PASS: A2C0C1/C2 y A2C1/A2D/A2E global siguen OPEN. G15 NOT_RUN, V2 productiva/stock real/pagos/Auto/Noche OFF, main y V4.4 intactas, cero APIs pagas.

**Secuencia gate:** B1–B5 docs (este documento) -> writer release -> guard SUCCESS -> permiso independiente READY exact4 sólo docs -> release y guard SUCCESS -> B6 código exact4 y CI dirigida -> auditoría B7 PASS/RED_FIX sin falso verde -> checkpoint y writer RELEASED. Ningún C1/C2 puede heredar este permiso.
