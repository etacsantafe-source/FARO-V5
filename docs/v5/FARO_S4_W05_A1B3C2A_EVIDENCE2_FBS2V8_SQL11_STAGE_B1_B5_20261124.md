# FARO V5 — W05-A1B3C2A · Evidence2 FBS2v8 → SQL11 STAGED · B1–B5

Fecha 2026-11-24 (Argentina). Rama work/s3-night-20261102; unidad S4_W05_A1B3C2A_FBS2V8_EVIDENCE2_SQL11_STAGED_REPLAY_DEV. No stock real, Apply, pagos, V2 productiva, Auto/Noche, main ni V4.4.

## B1 — Evidencia verificable y alcance

HEAD de entrada 5f400d96c6ef9e16c0faa67b3726f41e0464fa6a; writer remoto RELEASED epoch1903, permiso C1C BLOCKED y guardia final 45510331131 SUCCESS. C1C tiene B7 PASS acotado de startup/reopen Evidence1 (código 8ffc0fa5, CI 45509361871 XML18/18). No repetir C1C, C1B, W1–W4 ni B3B0/B3B1.

FBS2v8 autenticado YA acepta commercialEvidence.schemaVersion==2 y stagedTyped (FaroV5Household11Fbs2Codec, FaroV5FiveStorePortableBackupCrypto y FaroV5Household11V8RestoreAdmission). FaroV5Household11V8BaseReplay YA permite Evidence2 en las cinco bases, incluyendo comparación semántica después del restore. Sin embargo FaroV5Household11V8FullStageReplay rechaza expresamente todo perfil distinto de Evidence1 y usa inspectores Finance9/H10 de perfil Evidence1. C1A/C1B y el host C1C certificado siguen Evidence1-only.

Resultado C2A: restaurar archivo ORIGINAL cifrado FBS2v8 Evidence2 desde Finance9 STAGED hasta Reality11 STAGED, H10 y H11R con restauración idempotente, esquema Evidence2 tipado intacto, integridad cinco stores y ACTIVE pointer sin cambiar. No ticket, cutover ni adopción activa.

## B2 — Comparación V4.4 y reutilización certificada

Matrices V4.4 P02 y P03 (docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md y FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md): backup/recovery históricos no demuestran compatibilidad de términos Evidence2; no importar V4.4.

Reusar sin duplicar FBS2v8 A1B2A, BaseReplay A1B3B0, FullStageReplay A1B3B1 (Evidence1 B7 PASS), W04 F2 typed Evidence2 y los contratos tipados del grafo generation: requireFinance9ReceiptEvidence2ArchiveStage, requireSealedHousehold10Evidence2ArchiveStage. V8BaseReplay hace B0 Evidence2 de forma certificada. C1B y C1C quedan intactos.

## B3 — Alternativas contrastadas

A ADOPTAR: adaptar condicionalmente el FullStageReplay EXISTENTE usando únicamente el perfil del FBS2v8 autenticado. Evidence1 conserva la misma ruta; Evidence2 cambia al perfil Finance9/H10 tipado y comprueba comercial schema2, tablas commercial_evidence y commercial_evidence_quote_terms, conservación de hashes después de B0 y readback estricto del H10/H11R dentro SQL11.
B RECHAZAR: inferir Evidence2 por una tabla física sin contrastar el archivo original autenticado (TOCTOU/foreign target).
C RECHAZAR: degradar Evidence2 a Evidence1; pierde localidad, vigencia, promociones y provenance.
D POSPONER: ampliar C1A/C1B para publicación activa en esta misma unidad; rompe límites de autoridad.

Fuentes primarias de contexto: SQLite WAL https://sqlite.org/wal.html ; SQLite PRAGMA https://sqlite.org/pragma.html ; Room migrations https://developer.android.com/training/data-storage/room/migrating-db-versions . La inspección STAGED es sellada y sin sidecars; no usar immutable=1 en ACTIVE.

## B4 — Scope congelado de tres rutas EXACTAS

1. v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11V8FullStageReplay.kt : ramificación según evidence schema autenticado en {1,2}; FiveStore B0 existente; inspectores Evidence2 adecuados en Finance9/H10 y SQL11; comprobar schema2 + tablas y hash de Commercial Evidence sin cambiar otras bases; conservar pin exacto, idempotencia y gate.
2. v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11V8Evidence2FullStageReplayTest.kt : fixture typed Evidence2 con términos KNOWN, UNKNOWN y legado ABSENT; FBS2v8 original con H10/H11R; SQL9→10→11 y reinicios, rechazo de falsificaciones y regresión Evidence1.
3. .github/workflows/faro-v5-s4-w05-a1b3c2a-evidence2-stage.yml : preflight exact3 parent READY, Java21/Gradle, JUnit dirigido más regresión B3B1, XML real y upload de artefacto.

No modificar ni activar C1A, C1B, C1C, Activity, shipping V2, manifest ni W1–W4. Si un fallo demostrara necesidad de otra ruta, detenerse y tramitar RED_FIX del permiso antes de expandir.

## B5 — Adversariales y criterios de PASS

T01 Evidence2 autenticado pasa STAGED9→11 conservando cinco stores, Reality11 H10/H11R, comercial Evidence2 typed sin downgrades, cuatro stores no-Reality idénticos después de B0.
T02 ABSENT legacy sigue ABSENT; UNKNOWN y KNOWN conservan condiciones, localidad, vigencia y presentación; no inventar stock.
T03 Retry idéntico después de cortes en base/migración9→10/H10/DDL10→11/H11R; sin duplicar receipts ni reescribir propietarios.
T04 Mismo plaintext con ciphertext/nonce distinto, contraseña incorrecta, MAC corrupta y perfil sin typed terms → bloqueo.
T05 Pointer ABA, revocación host, stage diferente, hardlink/symlink, SQLite sidecar, esquema/trigger adulterado → bloqueo sin publicar pointer.
T06 Evidence1 suite B3B1 sigue PASS sin cambiar pruebas, luego regresión dirigida CI/XML.
T07 Ningún ticket, journal de cutover, fence material-write, Apply, compra/pago o V2 productiva resulta habilitado.

GO SOLO para B6 STAGED Evidence2 tras guardia B1–B5 SUCCESS, permiso nuevo READY exact3, writer CAS y preflight parent. Este documento NO certifica B6 ni B7. C2B será preflight/autorización Evidence2; C2C corte/recovery/reopen Evidence2; A1C stock Review/Apply/Undo después con permiso propio. A1 GLOBAL permanece OPEN.
