# FARO V5 — W01 M0C1 · B7 PASS acotado FBS2v4 Finance9 codec + legacy restore fence
Fecha AR 2026-11-16. Unidad S4_W01_M0C1_FINANCE9_FBS2V4_CODEC_FENCE. Código exacto c4d5cce1a434f74fa085f58a1f1c3078fffdb223.
**Veredicto:** B7_PASS_SCOPED_FBS2V4_SEMANTIC_AND_LEGACY_DENY. NO certifica todavía backup/restore real de Finance9 (M0C2).

## B1
Diff SHA c4d5cce1a434f74fa085f58a1f1c3078fffdb223 cotejado: exactamente cinco rutas del permiso READY anterior y no otros cambios:
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9BackupCodec.kt (F9S1, ocho tablas whitelist, sin ACL).
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreSemanticCodec.kt (FBS2v4 cinco secciones obligatorias).
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreStagedRestore.kt (UNSUPPORTED_FINANCE9_ARCHIVE).
- v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9Fbs2V4CodecFenceTest.kt.
- .github/workflows/faro-v5-s4-w01-m0c1-finance9-codec-directed.yml.
El parent 3bfe3ea6d8f87c74d85849513840d1e505b5d16e tenía READY para exact five paths y B1-B5 verificados en blob 8df15dd9604435bea69e9cf04ae7d454c2ef6bcb. El mensaje de código tiene FARO-UNIT exacto.

## B2
Se conserva FBS2v1/v2/v3, WSB2, G4/Planning8 y FAROBAK2 versión2. El codec v4 exige esas cuatro secciones previas y Finance9. La tabla blanca de ocho no incluye finance_acl_state, finance_acl_policy, finance_apply_grant ni credenciales. No se modifica RealityStore/opener ni M0A/M0B B7 PASS.

## B3
F9S1 canónico y acotado (4MiB, UTF8, orden key, índices numéricos); FBS2v4 con 5 hashes SHA256 por integridad; FAROBAK2 AES-GCM autentica el envelope. Alternativa v3 sin sección 9 rechazada por riesgo de pérdida de Finanzas. Referencias SQLite backup/transaction y Android Keystore registradas en B1-B5, sin APIs pagas.

## B4
GitHub Actions CI dirigida **43962889585**, job **133634669804**, completed/SUCCESS para SHA c4d5cce1a434f74fa085f58a1f1c3078fffdb223; log BUILD SUCCESSFUL in 3m 54s y S4_W01_M0C1_FINANCE9_FBS2V4_CODEC_FENCE_XML_PASS 6/6. XML exactamente 6 testcases, failures/errors/skipped=0; artifact faro-v5-w01-m0c1-finance9-fbs2v4-junit ID **14275173380**, existe no expirado. Guardia obligatoria siete bloques **43962889482** completed/SUCCESS del mismo SHA. El diff remoto coincide con cinco rutas.
Pruebas ejecutadas de verdad: 1) ocho tablas F9S1 sin grants; 2) v4 cinco secciones roundtrip y exigencia Planning; 3) compat y no reinterpretación v1/2/3; 4) corrupt/tamper/longitud/clave duplicada deniega; 5) FAROBAK2 AEAD; 6) restauradores legacy públicos e internos rechazan v4 antes de restaurar.

## B5
**No afirmar:** captura real de SQLite9, admisión/cutover de generación Finance9 productiva, restore de ocho tablas, WAL corte real, política cross-process, principal Android, F02–F06, G15, backups exportables al usuario. Sólo codec y fence. M0C2 debe añadir fuente Finance9 auténtica/canonizada, captura quiesced con cinco stores y restore stage9 sin ACL, y B7 independiente. W02 STOP. No repetir 6/6 sin delta nuevo.

## B6
Implementado y certificado sólo para código M0C1 codec/fence. Permiso M0C1 ahora CONSUMED/BLOCKED; siguiente M0C2 requiere B1-B5 y READY propio.

## B7
Cierre confirmado únicamente cuando este documento esté en SHA guard SUCCESS exacto y writer RELEASED epoch990 con last_checkpoint==HEAD, permiso BLOCKED con last_code_review=c4d5cce1a434f74fa085f58a1f1c3078fffdb223 result PASS y no cambios funcionales en este commit. Si falta alguna condición, B7 pendiente.
