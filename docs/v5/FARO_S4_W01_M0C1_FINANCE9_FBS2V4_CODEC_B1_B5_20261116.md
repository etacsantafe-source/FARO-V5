# FARO V5 — S4 W01 M0C1 · FBS2v4 Finance9 portable section + legacy restore fence · B1–B5
Fecha AR 2026-11-16. Unidad S4_W01_M0C1_FINANCE9_FBS2V4_CODEC_FENCE. Baseline HEAD 63900989d0f8af2d49f0c81569780052958becf5. PREAUDIT DOC_ONLY, no READY ni B6.
M0A B7 scoped PASS JVM 7/7 y M0B B7 scoped PASS JVM 7/7; sus pruebas no se repiten. Cierre M0B guard 43954020504 SUCCESS, writer RELEASED epoch986.

## B1 — Hallazgo sobre fuentes REALES
- FaroV5FiveStoreSemanticCodec.kt implementa FBS2 versiones 1(legacy+workspace),2(+G4),3(+Planning8); data class FaroV5FiveStoreSemanticSet carece de Finance9. El formato usa hashes de integridad SHA256 por sección, presupuesto máximo 16MiB, que NO son autenticación.
- FaroV5FiveStorePortableBackupCrypto.kt usa FAROBAK2 AES-GCM y deriva clave por PBKDF2; decodifica exclusivamente FaroV5FiveStoreSemanticCodec.decode. Debe transportar v4 sin alterar versión de envelope.
- FaroV5FiveStoreBackupCapture.kt usa grafo Reality7/8 y no puede capturar 9; FaroV5FiveStoreStagedRestore.kt restaura v1/v2/v3 con sus puertas, pero sin reconocer Finance9. La omisión de sección v4 sería pérdida irreversible. No abrir RealityStore 8 para 9.
- RealityFinance9Schema.kt M0A crea tablas: finance_opening_confirmation, finance_acl_state, finance_acl_policy, finance_apply_grant, finance_transfer, finance_debt, finance_schedule, finance_plan_item, finance_plan_item_payment, finance_budget_limit, finance_schema_migration_audit. ACL state/policy/grants contienen privilegios y NO se exportan; otras ocho tablas son la sección Finance9, incluido historial de migración. La base Finance anterior sigue en snapshot FBS1.
- No existe captura/restore Finance9; M0C1 es codec/puerta de rechazo, M0C2 será capturador/restaurador stage-only. NO afirmar backup restaurable por M0C1.

## B2 — Reutilización y límites
Reusar FBS2v3 para cuatro secciones canónicas, añadir sección cinco Finance9; mantener bytes de v1/v2/v3 idénticos y rutas anteriores. No editar Kotlin legacy owners ni catálogo180. Conservar workspace/g4/planning incluso vacíos, sin inferir ausencia como vacío. ACL/export NO por defecto. FBS2v4 nuevo exige los otros cuatro snapshots no nulos y esquema9. Captura quiesced y source provenance se integrarán en M0C2.

## B3 — Alternativas primarias
Elegida: nueva sección binaria Finance9 con ocho tablas whitelist, columnas y orden canónico, máximos de bytes/filas y SHA256 de integridad; FBS2v4 cinco secciones + FAROBAK2 AEAD existente. Codificar snapshot de datos sin SQL ejecutable. Rechazadas: raw sqlite.db, SQL DDL en archivo, serializar ACL/grants/Keystore, usar v3 y omitir Finance9, recrear G4/Planning como ausentes, alterar FAROBAK1/FBS1. Fuentes: https://www.sqlite.org/backup.html, https://www.sqlite.org/lang_transaction.html, https://developer.android.com/privacy-and-security/keystore ; referencias actuales codec FBS2v3 y WSB2.

## B4 — Unidad exacta, tabla whitelist y pruebas
Rutas únicas autorizables por permiso futuro:
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9BackupCodec.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreSemanticCodec.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreStagedRestore.kt
- v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9Fbs2V4CodecFenceTest.kt
- .github/workflows/faro-v5-s4-w01-m0c1-finance9-codec-directed.yml
- FaroV5Finance9BackupCodec.kt declara Finance9SectionSnapshot(schemaVersion=9, tables), ocho nombres en orden fijo, columnas tipadas como texto portable canónico y nullable, conteo columnas fijo, filas PK ordenadas por bytes UTF8, longitud campo<=4096, máximo sección 4MiB, sin SQL ejecutable; código binario v1 F9S1. No se permite exportar tablas de policy ni grants; migrationAudit se conserva como provenance no grant.
- FaroV5FiveStoreSemanticCodec.kt amplía FaroV5FiveStoreSemanticSet con finance9 nullable última propiedad default null; encode Finance9 exige G4+Planning+set Base válido; FBS2v4 con cinco longitudes, cinco SHA256 y cinco segmentos; decode v1-v3 no cambia. Un set Finance9 sin Planning se rechaza, nunca se hace downgrade silencioso.
- FaroV5FiveStoreStagedRestore.kt añade enum UNSUPPORTED_FINANCE9_ARCHIVE y rechaza Finance9 inmediatamente después del descifrado pero ANTES de archiveIdentity.pinOrVerify, de escribir SQLite o de permitir un restore antiguo. Sólo M0C2 añadirá ruta 9 certificada.
- Tests dirigidos NUEVOS (6): codec canonical y whitelist; v4 cinco secciones y roundtrip; v1-v3 no se reinterpreta como v4; versiones/truncación/huella/longitud denegadas; FAROBAK2 AEAD niega tamper; restore público y stage-only legado rechaza v4 antes de modificar destino o pin.
- Workflow dirige :store:generation:test clase exacta, JUnit XML 6/6, artifact. M0C1 NO garantiza restore Finance9 ni capture real.

## B5 — Auditoría adversarial
Omitir Finance9 convierte dinero, cuotas o presupuesto en pérdida de datos -> STOP. ACL/consent tokens exportados -> STOP. Snapshot no canónico/duplicado/externo/UTF8 sobrelongitud => DENY. FBS2v4 corrupto/versión futura/no AEAD => DENY. Viejo restaurador v1/v2/v3 recibe v4 => UNSUPPORTED_FINANCE9 antes de journal+receipt. Quiescence/copia five-store no están certificados en 9; sólo M0C2 los cerrará. Nadie activa V2 productiva, S2/S3/main/V4.4 intactos, W02 STOP.

## B6
NOT_RUN. Exigir guard de preauditoría, permiso READY separado en commit docs y preflight exact five-path antes de código.
## B7
NOT_RUN. Exigir seis XML PASS, guard del commit B6 SUCCESS, writer RELEASED, auditoría diff cinco rutas, permiso consumido. M0C2 pendiente.
