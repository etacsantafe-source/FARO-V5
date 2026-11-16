# FARO V5 — W01 M0C2B1 · restauración autenticada de sección Finance9 en STAGED · B1–B5
Fecha AR 2026-11-16. Unidad `S4_W01_M0C2B1_FINANCE9_AUTHENTICATED_SECTION_RESTORE`. Baseline HEAD `330ac48efcebbc7adce2f89a2791ae21ed22f97b`. Preauditoría DOC_ONLY, B6 aún NO autorizada.
**Alcance conservador:** restaurar/validar exclusivamente la quinta sección FINANCE9 de un sobre FBS2v4 autenticado, en una nueva generación STAGED9 cuyo resto de datos contables/otros cuatro owners habrá de estar previamente preparado. **NO** declarar todavía backup ni restore completo de cinco stores: integración y journal M0C2B2 pendientes.

## B1 Evidencia real y problema
- M0A esquema9 7/7, M0B generación sellada 7/7, M0C1 FBS2v4 codec/fence 6/6, M0C2A captura SELECT reales 7/7: B7 PASS scoped, últimas guardia `43977323836` SUCCESS, writer RELEASED epoch994. No repetir certificaciones.
- `FaroV5FiveStorePortableBackupCrypto.open` entrega sólo `Opened` tras AES-GCM sobre sobre FAROBAK2+FBS2v4; `FaroV5FiveStoreSemanticCodec` rechaza secciones corruptas, exige G4/Planning y Finance9.
- `FaroV5Finance9BackupCodec` conoce ocho tablas exactas, sin `finance_acl_state/policy/apply_grant`. `RealityFinance9SealedReadPort` devuelve esas ocho tablas de `reality.db`9 sellada, orden canónico. `RealityFinance9Schema` impone FK de opening account, transfer debit/credit y pagos.
- `FaroV5FiveStoreStagedRestore` V1–V3 rechaza FBS2v4 ANTES de archiveIdentity/writes. `FaroV5GenerationStoreGraph` no puede abrir Reality9; por eso restauración Finance9 exige puente exclusivo :store:reality, sin cambiar openers anteriores.
- Restaurar SOLO Finance9 antes de aplicar `fourStore.reality` fallará con FK para accounts/entries referidos; esto es **fail-closed correcto**. Por eso M0C2B1 se limita a sección9 sobre base compatible ya presente; M0C2B2 debe demostrar el orden de todos los owners sin omisiones.

## B2 Reuso, invariantes, no duplicar
Reusar `FaroV5Finance9SealedSnapshotCapture` y `StagedSqlitePreflight`, pin exacto ID+sequence, marker WORKSPACE_V2, `MaterialWriteQuiescencePort` dev (no prueba exclusión cross-process). Reusar codec 8 tablas + `Finance9SectionSnapshot`, no editar sus tests 7/7. El acto de sección es UNA transacción `BEGIN IMMEDIATE` en Reality9 con FK ON; revisión de target fresco o idéntico, nunca sobreescribir Finance9 real de otra fuente. Un crash antes COMMIT revierte ambas tablas de apertura y deudas; receipts de Reality7/8 permanecen intactos. Nunca `ATTACH` otras bases ni convertir preview en gasto.

## B3 Alternativas técnicas
A) Restauración de sección Finance9 primero en staged9, desde FBS2v4 validado con AES-GCM, como nueva herramienta DEV: ADOPTAR, previa verificación de dependencias por FK.
B) Deshabilitar FK, insertar en ACTIVE o copiar DB Finance9 cruda: RECHAZAR.
C) Abrir Reality9 mediante `RealityStore(planningStage8=true)`: RECHAZAR, opener8 debe bloquear 9.
D) Marcar restore total cinco stores cuando sólo ocho tablas se restauraron: RECHAZAR.
Fuentes primarias: `https://www.sqlite.org/lang_transaction.html`, `https://www.sqlite.org/foreignkeys.html`, `https://www.sqlite.org/lang_conflict.html`, `https://developer.android.com/topic/architecture/data-layer/offline-first`.

## B4 Especificación exact-path y aceptación
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9StagedRowsRestorePort.kt`
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9AuthenticatedSectionRestore.kt`
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9AuthenticatedSectionRestoreTest.kt`
- `.github/workflows/faro-v5-s4-w01-m0c2b1-finance9-restore-directed.yml`
- Reality bridge `RealityFinance9StagedRowsRestorePort.restoreOrVerifyTrustedStagedNine(file, snapshot)`: valida ocho tablas/columnas idénticas a esquema 9, longitud, tipo, orden, FKs, esquema 9 exacto, NOFOLLOW/archivo existente, y tras autenticar afuera recibe `RealityFinance9SealedSnapshot`; si destino ya coincide => `VERIFIED`; de lo contrario sólo acepta data tables financieros vacíos salvo opening `LEGACY_UNVERIFIED` y un migration audit nuevo; ACL debe estar vacía, epoch inicial=1 rev0. Dentro de SQL BEGIN IMMEDIATE verifica estado fresco, elimina ONLY opening+audit de staging, inserta en orden de dependencias las ocho tablas tipadas por placeholders SQL, ejecuta FK/integrity, COMMIT y verifica roundtrip; jamás DROP ni Update de ledger antiguo. Si foreign account/entry ausente, ROLLBACK íntegro.
- Owner generación `FaroV5Finance9AuthenticatedSectionRestore.restoreFinanceSectionOnly(operationId, staged, expectedActive, archive, passphrase)`: autentica FAROBAK2 y FBS2v4 completo antes de escribir; verifica origen != target, cinco almacenes sellados, descriptor/marker V2, pointer exacto antes/después, lee snapshot Finance9 antes/después con M0C2A, copia DTO a bridge. No archive pin/activación; NO autorizar API productiva ni external export. Identidad durable/journal para el restore COMPLETO se exige en M0C2B2.
- Siete tests dirigidos NUEVOS en JVM: (T1) v4 autenticado con budget/debt se restaura en stage9 y se lee exactamente; (T2) reintento mismo snapshot es VERIFIED sin duplicados; (T3) password equivocado/tamper rechaza sin cambios; (T4) v3/no Finance9 rechazo; (T5) target no fresh o ACL no vacía DENY; (T6) foreign-key account no presente produce ROLLBACK; (T7) target ACTIVE y ABA pointer/sidecar rechazan. CI exact `:store:generation:test --tests ...` y XML 7/7.
- Result enum `Restored|Verified|Blocked`, sin `StagedVerified` de todos los owners ni permitir cutover. No alterar G4/Planning ni metadata de FAROBAK2.

## B5 Auditoría negativa
Clave o sobre corrupto, sección9 ausente, target source-id igual, pointer ABA, source account no compatible, esquema extraño, ACL/grant previos, destFinance ya no fresco, fallo de segundo INSERT o sidecars WAL: DENY sin pérdida de estado; no reintento de Apply, no restaurar permiso ni credencial de otro teléfono. SQL9 CHECK y triggers validan importes/cuentas; si fallo rollback. Después de éxito la generación continúa **STAGED SIN AUTORIDAD**, requieren M0C2B2/M1 y B7 propios. S2/S3/main/V4.4/catalog180/productive V2/Auto/Noche intactos, W02 STOP.

## B6
NOT_RUN: preauditoría no habilita código. Exigir guard SHA, permiso READY exact cuatro rutas en OTRO commit doc y preflight de HEAD/writer.
## B7
PENDING: CI XML 7/7 y guard exactos, diff cuatro rutas, writer RELEASED, permiso CONSUMED; aún NO cierre M0C2B total.
