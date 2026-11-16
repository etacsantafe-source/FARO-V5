# FARO V5 — S4 W01 M0C2B1: B7 scoped PASS authenticated Finance9 section restore
Fecha AR: 2026-11-16. Unidad S4_W01_M0C2B1_FINANCE9_AUTHENTICATED_SECTION_RESTORE. Código SHA `003de78e8ca97db5e11d42e90654f008e337ff33`. Resultado **B7_PASS_SCOPED_FINANCE9_AUTHENTICATED_SECTION_ONLY**, NO cinco-store restore, NO cutover.

## B1
Diff commit SHA 003de78e8ca97db5e11d42e90654f008e337ff33: exactamente cuatro archivos, todos dentro del permiso READY previo:
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9StagedRowsRestorePort.kt`.
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9AuthenticatedSectionRestore.kt`.
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9AuthenticatedSectionRestoreTest.kt`.
- `.github/workflows/faro-v5-s4-w01-m0c2b1-finance9-restore-directed.yml`.
Parent permit HEAD `2964e0ca3c3cfc5185680046a7aaf4d03dc7e189` tenía READY exact4, preaudit B1–B5 `docs/v5/FARO_S4_W01_M0C2B1_AUTHENTICATED_FINANCE9_SECTION_RESTORE_B1_B5_20261116.md` blob `b8c488e99482a31a8854ef90a0e240753918ae8a`. No hay modificación de S2/S3, main, V4.4, catálogo 180, productivo o G15.

## B2
Reusa M0A M0B M0C1 M0C2A cerradas, sin reejecutar sus suites. Transacción única SQLite9 con BEGIN IMMEDIATE, FK, CHECK/trigger e integridad, ocho tablas F9S1 exclusivamente. No exporta ni restituye ACL; no toca ledger antiguo, otros cuatro stores ni puntero. Target datos vacíos o legado + una fila audit, o replay idéntico; un destino ya con dinero distinto se DENY.

## B3
FAROBAK2 AES-GCM autentica y decodifica v4 antes de cualquier SQL; FBS2v1–3 no se aceptan ni reinterpretan. SQLite official `https://www.sqlite.org/lang_transaction.html`, `https://www.sqlite.org/foreignkeys.html`. Alternativas ATTACH/writes ACTIVE/reutilizar opener8 se rechazan. Apertura sólo staged9, inprocess quiescence insuficiente para productivo.

## B4
CI dirigida **43979784062** en SHA `003de78e8ca97db5e11d42e90654f008e337ff33`, job **133693002621** completed/SUCCESS. Log **BUILD SUCCESSFUL in 2m 14s**, `S4_W01_M0C2B1_AUTH_FINANCE9_SECTION_RESTORE_PASS 7/7`. Verificador XML exactamente siete testcases, failures/errors/skipped=0, artefacto `faro-v5-w01-m0c2b1-finance9-stage-restore-junit` ID **14283525206**, presente y no caducado. Guardia obligatoria protocolo `43979783881` completed/SUCCESS misma SHA. GitHub diff verificado sólo cuatro rutas.
Pruebas ejecutadas: (T1) v4 válido con budget/debt restore; (T2) replay idéntico; (T3) password inválido/tamper; (T4) v3/missing Finance9 DENY; (T5) target nofresh/ACL DENY; (T6) FK cuenta histórica ausente rollback íntegro; (T7) ACTIVE/ABA/sidecar bloqueados.

## B5
**NO CERTIFICADO**: full legacy ledger/G4/Planning/Workspace replay, archive pin y recovery fences, copia simultánea 5 almacenes, cross-process ACL/credenciales, activar schema9, pantalla Compose, Android15/G15 ni W01 F02–F06. Finances no mueve dinero. `MaterialWriteQuiescencePort` de test es fake inprocess; no habilitar App. M0C2B2 integra cinco stores con journal y prueba B7 propio antes de M1. W02 STOP.

## B6
PASS sólo sección Finance9 staged9, permiso **CONSUMED/BLOCKED**. No repetir 7/7 salvo delta nuevo.

## B7
Confirmación condicionada al checkpoint de este commit documental: guard SUCCESS en SHA exacta, writer RELEASED epoch998 con last_checkpoint igual y permiso BLOCKED last_code_review=003de78e8ca97db5e11d42e90654f008e337ff33 PASS. Si falta, B7 queda pendiente.
